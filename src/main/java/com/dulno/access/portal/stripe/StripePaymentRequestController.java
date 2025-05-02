package com.dulno.access.portal.stripe;

import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.PartnerDatabaseTable;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import com.dulno.core.stripe.StripeConfiguration;
import com.dulno.core.stripe.StripeDatabaseTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.stripe.StripeClient;
import com.stripe.model.Invoice;
import com.stripe.model.InvoicePayment;
import com.stripe.model.PaymentIntent;
import com.stripe.param.InvoicePaymentListParams;
import com.stripe.param.PaymentIntentListParams;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StripePaymentRequestController extends StripeController {
  private final StripeClient stripeClient;
  private final ErrorRepository errorRepository;

  private StripePaymentRequestController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    StripeConfiguration stripeConfiguration,
    StripeDatabaseTable stripeDatabaseTable, StripeClient stripeClient,
    PartnerDatabaseTable partnerDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    ErrorRepository errorRepository
  ) {
    super(secretKey, memberDatabaseTable, stripeConfiguration, stripeDatabaseTable,
      partnerDatabaseTable, partnerMemberDatabaseTable);
    this.stripeClient = stripeClient;
    this.errorRepository = errorRepository;
  }

  @PortalEndpoint
  @RequestMapping(path = "/payments/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findPayments(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performStripeOperation(findMemberId(request), account -> new Thread(() ->
        futureResponse.complete(findPayments(account.accountId()))).start(),
      () -> futureResponse.complete(Map.of("payments", Lists.newArrayList())));
    return futureResponse;
  }

  private Map<String, Object> findPayments(String accountId) {
    try {
      var parameters = PaymentIntentListParams.builder()
        .setCustomer(accountId).build();
      var payments = stripeClient.paymentIntents().list(parameters);
      var information = Lists.newArrayList();
      for (var payment : payments.getData()) {
        var invoice = findPaymentInvoice(payment);
        information.add(assemblyPaymentInformation(payment, invoice));
      }
      return Map.of("payments", information);
    } catch (Exception exception) {
      errorRepository.processError(exception);
      return Maps.newHashMap();
    }
  }

  private Invoice findPaymentInvoice(
    PaymentIntent paymentIntent
  ) throws Exception {
    var paymentParams = InvoicePaymentListParams.Payment.builder()
      .setType(InvoicePaymentListParams.Payment.Type.PAYMENT_INTENT)
      .setPaymentIntent(paymentIntent.getId()).build();
    var listParams = InvoicePaymentListParams.builder()
      .setPayment(paymentParams).build();
    var invoicePayments = stripeClient.invoicePayments().list(listParams);
    if (invoicePayments.getData().isEmpty()) {
      throw new IllegalStateException("No invoice found for PaymentIntent " +
        paymentIntent.getId());
    }
    return stripeClient.invoices().retrieve(
      invoicePayments.getData().get(0).getInvoice());
  }

  private Map<String, Object> assemblyPaymentInformation(
    PaymentIntent payment, Invoice invoice
  ) throws Exception {
    var information = Maps.<String, Object>newHashMap();
    information.put("time", formatTime(payment.getCreated() * 1000));
    information.put("amount", payment.getAmount() / 100D);
    information.put("method", stripeClient.paymentMethods()
      .retrieve(payment.getPaymentMethod()).getType());
    information.put("status", payment.getStatus());
    information.put("invoice", invoice.getInvoicePdf());
    return information;
  }

  private String formatTime(long time) {
    var calendar = Calendar.getInstance();
    calendar.setTimeInMillis(time);
    return new SimpleDateFormat("dd.MM.yyyy").format(calendar.getTime());
  }
}
