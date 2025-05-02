package com.dulno.access.portal.stripe;

import com.dulno.core.accessory.AccessoryDatabaseTable;
import com.dulno.core.accessory.AccessoryPresetRepository;
import com.dulno.core.bundle.Bundle;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.bundle.BundleRuntime;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.locale.Translation;
import com.dulno.core.mail.Mail;
import com.dulno.core.mail.MailAttachment;
import com.dulno.core.member.Member;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.offer.Offer;
import com.dulno.core.offer.OfferDatabaseTable;
import com.dulno.core.offer.OfferStatus;
import com.dulno.core.partner.PartnerDatabaseTable;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import com.dulno.core.stripe.*;
import com.google.common.collect.Lists;
import com.stripe.StripeClient;
import com.stripe.model.LineItem;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.InvoicePaymentListParams;
import com.stripe.param.RefundCreateParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.security.Key;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StripeCheckoutResponseController extends StripeController {
  private final StripeClient stripeClient;
  private final StripeCompletionDatabaseTable stripeCompletionDatabaseTable;
  private final TerminationDatabaseTable terminationDatabaseTable;
  private final AccessoryPresetRepository accessoryPresetRepository;
  private final AccessoryDatabaseTable accessoryDatabaseTable;
  private final Mail orderMail;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final OfferDatabaseTable offerDatabaseTable;
  private final Translation translation;
  private final ErrorRepository errorRepository;
  private final DulnoEnvironment environment;

  private StripeCheckoutResponseController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    StripeConfiguration stripeConfiguration,
    StripeDatabaseTable stripeDatabaseTable, StripeClient stripeClient,
    PartnerDatabaseTable partnerDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    StripeCompletionDatabaseTable stripeCompletionDatabaseTable,
    TerminationDatabaseTable terminationDatabaseTable,
    AccessoryPresetRepository accessoryPresetRepository,
    AccessoryDatabaseTable accessoryDatabaseTable,
    @Qualifier("orderMail") Mail orderMail,
    BundleDatabaseTable bundleDatabaseTable,
    OfferDatabaseTable offerDatabaseTable, Translation translation,
    ErrorRepository errorRepository, DulnoEnvironment environment
  ) {
    super(secretKey, memberDatabaseTable, stripeConfiguration, stripeDatabaseTable,
      partnerDatabaseTable, partnerMemberDatabaseTable);
    this.stripeClient = stripeClient;
    this.stripeCompletionDatabaseTable = stripeCompletionDatabaseTable;
    this.terminationDatabaseTable = terminationDatabaseTable;
    this.accessoryPresetRepository = accessoryPresetRepository;
    this.accessoryDatabaseTable = accessoryDatabaseTable;
    this.orderMail = orderMail;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.offerDatabaseTable = offerDatabaseTable;
    this.translation = translation;
    this.errorRepository = errorRepository;
    this.environment = environment;
  }

  @RequestMapping(path = "/stripe/checkout/", method = RequestMethod.POST)
  public CompletableFuture<Void> processStripeRequest(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var event = findStripeEvent(request, payload,
      stripeConfiguration().checkoutWebhookSecret());
    if (event.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return CompletableFuture.completedFuture(null);
    }
    var object = findStripeObject(event.get());
    if (object.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return CompletableFuture.completedFuture(null);
    }
    if (event.get().getType().equals("checkout.session.completed")) {
      var futureResponse = new CompletableFuture<Void>();
      new Thread(() -> processCheckoutSessionCompletion((Session) object.get())
        .thenAccept(futureResponse::complete)).start();
      return futureResponse;
    }
    return CompletableFuture.completedFuture(null);
  }

  private CompletableFuture<Void> processCheckoutSessionCompletion(Session session) {
    try {
      var customerEmail = stripeClient.customers()
        .retrieve(session.getCustomer()).getEmail();
      var subscription = stripeClient.subscriptions()
        .retrieve(session.getSubscription());
      var subscriptionPriceId = subscription.getItems().getData().get(0)
        .getPrice().getId();
      var items = stripeClient.checkout().sessions().lineItems()
        .list(session.getId()).getData();
      return memberDatabaseTable().findMember(customerEmail)
        .thenCompose(member -> findBundle(member, subscriptionPriceId)
          .thenCompose(bundle -> storePurchase(session, subscription, items,
            member, bundle)));
    } catch (Exception exception) {
      errorRepository.processError(exception);
      return CompletableFuture.completedFuture(null);
    }
  }

  private CompletableFuture<Void> storePurchase(
    Session session, Subscription subscription, List<LineItem> items,
    Member member, Bundle bundle
  ) {
    try {
      storeAccessories(items, bundle.partnerId());
      return processPreviousSubscriptions(session, member)
        .thenComposeAsync(value -> findStripeAccount(session, subscription,
          member, bundle.partnerId())
          .thenCompose(account -> applySubscription(session, subscription,
            member, bundle.partnerId(), bundle)));
    } catch (Exception exception) {
      errorRepository.processError(exception);
      return CompletableFuture.completedFuture(null);
    }
  }

  private CompletableFuture<Bundle> findBundle(
    Member member, String subscriptionPriceId
  ) {
    try {
      var bundlePreset = stripeConfiguration().findBundlePreset(subscriptionPriceId);
      if (bundlePreset.isEmpty()) {
        var futureOffer = offerDatabaseTable.findOffersByPriceId(subscriptionPriceId);
        futureOffer.thenAccept(offer ->
          offerDatabaseTable.updateOfferStatus(offer, OfferStatus.ACCEPTED));
        return futureOffer.thenApply(Offer::toBundle);
      }
      var bundleRuntime = findBundleRuntime(subscriptionPriceId);
      return partnerMemberDatabaseTable().findMemberPartner(member.id())
        .thenApply(partnerId -> Bundle.of(partnerId, bundlePreset.get(), bundleRuntime));
    } catch (Exception exception) {
      errorRepository.processError(exception);
      return CompletableFuture.completedFuture(null);
    }
  }

  private CompletableFuture<Void> processPreviousSubscriptions(
    Session session, Member member
  ) {
    return stripeDatabaseTable().stripeAccountExists(session.getCustomer())
      .thenCompose(exists -> processPreviousSubscriptions(member,
        session.getCustomer(), exists));
  }

  private CompletableFuture<Void> processPreviousSubscriptions(
    Member member, String accountId, boolean previouslyExisted
  ) {
    if (!previouslyExisted) {
      return CompletableFuture.completedFuture(null);
    }
    return stripeDatabaseTable().findStripeAccount(accountId)
      .thenAcceptAsync(account -> terminatePreviousSubscriptions(member,
        account.subscriptionId()));
  }

  private void terminatePreviousSubscriptions(Member member, String subscriptionId) {
    try {
      if (subscriptionId.isEmpty()) {
        return;
      }
      var subscription = stripeClient.subscriptions().retrieve(subscriptionId);
      refundLastPayment(member, subscription);
      subscription.cancel();
    } catch (Exception exception) {
      errorRepository.processError(exception);
    }
  }

  private void refundLastPayment(Member member, Subscription subscription) {
    try {
      var payment = findSubscriptionLatestPayment(subscription);
      var amount = calculatePaymentRefundAmount(subscription, payment);
      stripeClient.refunds().create(RefundCreateParams.builder()
        .setPaymentIntent(payment.getId())
        .setAmount(amount)
        .build());
      var title = translation.translateMember(member, "upgrade.email.title");
      var body = String.format(translation.translateMember(member, "upgrade.email.body"),
        member.name(), amount / 100D);
      orderMail.send(member, title, body);
    } catch (Exception exception) {
      errorRepository.processError(exception);
    }
  }

  private PaymentIntent findSubscriptionLatestPayment(
    Subscription subscription
  ) throws Exception {
    var params = InvoicePaymentListParams.builder()
      .setInvoice(subscription.getLatestInvoice()).setLimit(1L).build();
    var payments = stripeClient.invoicePayments().list(params);
    if (!payments.getData().isEmpty()) {
      var invoicePayment = payments.getData().get(0);
      return stripeClient.paymentIntents()
        .retrieve(invoicePayment.getPayment().getPaymentIntent());
    }
    throw new IllegalStateException("No payment found for subscription " +
      subscription.getId());
  }

  private long calculatePaymentRefundAmount(
    Subscription subscription, PaymentIntent payment
  ) {
    var item = subscription.getItems().getData().get(0);
    var periodStart = item.getCurrentPeriodStart();
    var periodEnd = item.getCurrentPeriodEnd();
    if (periodStart == null || periodEnd == null) {
      return 0;
    }
    long currentTime = System.currentTimeMillis() / 1000L;
    double totalPeriod = periodEnd - periodStart;
    double timeRemaining = periodEnd - currentTime;
    double percentageRemaining = timeRemaining / totalPeriod;
    percentageRemaining = Math.max(0, Math.min(percentageRemaining, 1));
    return Math.round(percentageRemaining * payment.getAmount());
  }

  private CompletableFuture<StripeAccount> findStripeAccount(
    Session session, Subscription subscription, Member member, UUID partnerId
  ) {
    var accountId = session.getCustomer();
    return stripeDatabaseTable().stripeAccountExists(accountId).thenCompose(
      exists -> storeCustomer(accountId, subscription, member, partnerId, exists)
        .thenCompose(customer -> stripeDatabaseTable().findStripeAccount(accountId)));
  }

  private CompletableFuture<Void> storeCustomer(
    String accountId, Subscription subscription, Member member, UUID partnerId,
    boolean exists
  ) {
    if (exists) {
      return stripeDatabaseTable().updateStripeAccount(accountId, partnerId,
        member.id(), subscription.getId());
    }
    return stripeDatabaseTable().insertStripeAccount(accountId, partnerId,
      member.id(), subscription.getId());
  }

  private CompletableFuture<Void> applySubscription(
    Session session, Subscription subscription, Member member, UUID partnerId,
    Bundle bundle
  ) {
    bundleDatabaseTable.bundleExists(partnerId).thenAccept(exists ->
      applyBundle(bundle, exists));
    terminationDatabaseTable.deleteTermination(partnerId);
    try {
      sendPaymentEmail(member, subscription);
    } catch (Exception exception) {
      errorRepository.processError(exception);
    }
    return stripeCompletionDatabaseTable.confirmStripeCompletion(member.id(),
      session.getSuccessUrl().replace("https://portal." + environment.domain() +
        "/payment/complete/", "").replace("/", ""));
  }

  private void applyBundle(Bundle bundle, boolean bundleExists) {
    if (bundleExists) {
      bundleDatabaseTable.updateBundle(bundle);
      return;
    }
    bundleDatabaseTable.insertBundle(bundle);
  }

  private BundleRuntime findBundleRuntime(String subscriptionPriceId) {
    if (stripeConfiguration().findPriceIdsOfRuntime(BundleRuntime.MONTHLY)
      .contains(subscriptionPriceId)
    ) {
      return BundleRuntime.MONTHLY;
    }
    return BundleRuntime.YEARLY;
  }

  private void storeAccessories(List<LineItem> items, UUID partnerId) {
    for (var item : items) {
      var presetOptional = accessoryPresetRepository.findPresetByPriceId(
        item.getPrice().getId());
      if (presetOptional.isEmpty()) {
        continue;
      }
      var preset = presetOptional.get();
      for (var i = 0; i < item.getQuantity(); i++) {
        accessoryDatabaseTable.generateAvailableAccessoryId()
          .thenAccept(id -> accessoryDatabaseTable.insertAccessory(id, partnerId,
            preset.id(), preset.price(), System.currentTimeMillis()));
      }
    }
  }

  private void sendPaymentEmail(
    Member member, Subscription subscription
  ) throws Exception {
    var invoice = stripeClient.invoices().retrieve(subscription.getLatestInvoice());
    var invoiceFile = new File(System.getProperty("member.dir") + "/invoices/" +
      invoice.getId() + ".pdf");
    invoiceFile.getParentFile().mkdirs();
    invoiceFile.createNewFile();
    downloadInvoice(invoice.getInvoicePdf(), invoiceFile.getAbsoluteFile());
    var title = translation.translateMember(member, "payment.email.title");
    var body = String.format(translation.translateMember(member, "payment.email.body"),
      member.name());
    orderMail.send(member, title, body,
        Lists.newArrayList(MailAttachment.create("Invoice.pdf", invoiceFile)))
      .thenAccept(value -> invoiceFile.delete());
  }

  private void downloadInvoice(String url, File file) throws Exception {
    var inputStream = new BufferedInputStream(new URL(url).openStream());
    var fileOutputStream = new FileOutputStream(file);
    byte[] dataBuffer = new byte[1024];
    int bytesRead;
    while ((bytesRead = inputStream.read(dataBuffer, 0, 1024)) != -1) {
      fileOutputStream.write(dataBuffer, 0, bytesRead);
    }
  }
}

