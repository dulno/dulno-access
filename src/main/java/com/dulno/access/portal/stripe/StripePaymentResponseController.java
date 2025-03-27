package com.dulno.access.portal.stripe;

import com.dulno.core.bundle.Bundle;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.PartnerDatabaseTable;
import com.dulno.core.partner.PartnerMemberDatabaseTable;
import com.dulno.core.stripe.StripeConfiguration;
import com.dulno.core.stripe.StripeDatabaseTable;
import com.dulno.core.stripe.TerminationDatabaseTable;
import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Subscription;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StripePaymentResponseController extends StripeController {
  private final StripeClient stripeClient;
  private final TerminationDatabaseTable terminationDatabaseTable;
  private final StripeTerminationController stripeTerminationController;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final ErrorRepository errorRepository;

  private StripePaymentResponseController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    StripeConfiguration stripeConfiguration,
    StripeDatabaseTable stripeDatabaseTable, StripeClient stripeClient,
    PartnerDatabaseTable partnerDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    TerminationDatabaseTable terminationDatabaseTable,
    StripeTerminationController stripeTerminationController,
    BundleDatabaseTable bundleDatabaseTable,
    ErrorRepository errorRepository
  ) {
    super(secretKey, memberDatabaseTable, stripeConfiguration, stripeDatabaseTable,
      partnerDatabaseTable, partnerMemberDatabaseTable);
    this.stripeClient = stripeClient;
    this.terminationDatabaseTable = terminationDatabaseTable;
    this.stripeTerminationController = stripeTerminationController;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.errorRepository = errorRepository;
  }

  @RequestMapping(path = "/stripe/payment/", method = RequestMethod.POST)
  public void processStripeRequest(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var event = findStripeEvent(request, payload,
      stripeConfiguration().paymentWebhookSecret());
    if (event.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    var object = findStripeObject(event.get());
    if (object.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    if (event.get().getType().equals("payment_intent.created")) {
      new Thread(() -> processPaymentCreation((PaymentIntent) object.get())).start();
    }
    if (event.get().getType().equals("payment_intent.succeeded")) {
      new Thread(() -> processPaymentSuccess((PaymentIntent) object.get())).start();
    }
  }

  private void processPaymentCreation(PaymentIntent paymentIntent) {
    try {
      var invoice = stripeClient.invoices().retrieve(paymentIntent.getInvoice());
      var subscription = stripeClient.subscriptions().retrieve(
        invoice.getSubscription());
      findBundle(subscription).thenAccept(this::checkPackageExtension);
    } catch (Exception exception) {
      errorRepository.processError(exception);
    }
  }

  private void checkPackageExtension(Bundle bundle) {
    if (bundle == null) {
      return;
    }
    var timeDifference = Math.abs(System.currentTimeMillis() - bundle.expiration());
    if (timeDifference > 1000L * 60 * 60 * 24) {
      return;
    }
    bundle.extend();
    bundleDatabaseTable.updateBundle(bundle);
  }

  private void processPaymentSuccess(PaymentIntent paymentIntent) {
    try {
      var invoice = stripeClient.invoices().retrieve(paymentIntent.getInvoice());
      var subscription = stripeClient.subscriptions().retrieve(
        invoice.getSubscription());
      findBundle(subscription).thenAccept(bundle ->
        processPaymentSuccess(subscription, bundle));
    } catch (Exception exception) {
      errorRepository.processError(exception);
    }
  }

  private void processPaymentSuccess(Subscription subscription, Bundle bundle) {
    if (bundle == null) {
      return;
    }
    terminationDatabaseTable.terminationExists(bundle.partnerId())
      .thenAccept(terminationExists -> checkPackageTermination(subscription,
        bundle, terminationExists));
  }

  private void checkPackageTermination(
    Subscription subscription, Bundle bundle, boolean terminationExists
  ) {
    if (!terminationExists) {
      return;
    }
    var periodEnd = subscription.getCurrentPeriodEnd();
    if (periodEnd == null) {
      return;
    }
    var timeDifference = Math.abs(periodEnd - bundle.expiration());
    if (timeDifference > 1000L * 60 * 60 * 24) {
      return;
    }
    terminationDatabaseTable.deleteTermination(bundle.partnerId());
    stripeTerminationController.cancelSubscription(subscription);
  }

  private CompletableFuture<Bundle> findBundle(Subscription subscription) {
    try {
      var customer = stripeClient.customers().retrieve(subscription.getCustomer());
      return memberDatabaseTable().findMember(customer.getEmail())
        .thenCompose(member -> partnerMemberDatabaseTable().findMemberPartner(member.id())
          .thenCompose(this::findBundle));
    } catch (Exception exception) {
      errorRepository.processError(exception);
      return CompletableFuture.completedFuture(null);
    }
  }

  private CompletableFuture<Bundle> findBundle(UUID partnerId) {
    return bundleDatabaseTable.bundleExists(partnerId)
      .thenCompose(bundleExists -> findBundle(partnerId, bundleExists));
  }

  private CompletableFuture<Bundle> findBundle(
    UUID partnerId, boolean bundleExists
  ) {
    if (!bundleExists) {
      return CompletableFuture.completedFuture(null);
    }
    return bundleDatabaseTable.findBundle(partnerId);
  }
}
