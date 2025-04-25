package com.dulno.access.portal.offer;

import com.dulno.access.portal.security.home.HomeEndpoint;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.member.Member;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.offer.Offer;
import com.dulno.core.offer.OfferDatabaseTable;
import com.dulno.core.offer.OfferStatus;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.stripe.StripeAccount;
import com.dulno.core.stripe.StripeCompletionDatabaseTable;
import com.dulno.core.stripe.StripeDatabaseTable;
import com.stripe.StripeClient;
import com.stripe.param.PriceUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class OfferModificationController extends OfferController {
  private final StripeDatabaseTable stripeDatabaseTable;
  private final StripeCompletionDatabaseTable stripeCompletionDatabaseTable;
  private final StripeClient stripeClient;
  private final ErrorRepository errorRepository;
  private final DulnoEnvironment environment;

  private OfferModificationController(
    @Qualifier("homeKey") Key secretKey, MemberDatabaseTable memberDatabaseTable,
    OfferDatabaseTable offerDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    StripeDatabaseTable stripeDatabaseTable,
    StripeCompletionDatabaseTable stripeCompletionDatabaseTable,
    StripeClient stripeClient, ErrorRepository errorRepository,
    DulnoEnvironment environment
  ) {
    super(secretKey, memberDatabaseTable, offerDatabaseTable,
      partnerMemberDatabaseTable);
    this.stripeDatabaseTable = stripeDatabaseTable;
    this.stripeCompletionDatabaseTable = stripeCompletionDatabaseTable;
    this.stripeClient = stripeClient;
    this.errorRepository = errorRepository;
    this.environment = environment;
  }

  @HomeEndpoint
  @RequestMapping(path = "/offer/accept/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> acceptOffer(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findMember(request).thenAccept(member ->
      performOfferOperation(member.id(), body.getUUID("offer"),
        offer -> partnerMemberDatabaseTable().findMemberPartner(member.id())
          .thenCompose(partnerId -> findExistingAccount(partnerId)
            .thenAcceptAsync(account -> futureResponse.complete(
              acceptOffer(offer, member, account)))),
        () -> futureResponse.complete(Map.of("success", false))));
    return futureResponse;
  }

  private static final String SUCCESS_URL_FORMAT = "https://portal.%s/payment/complete/%s/";
  private static final String CANCEL_URL_FORMAT = "https://portal.%s/offer/%s/";

  public Map<String, Object> acceptOffer(Offer offer, Member member, String accountId) {
    try {
      var token = UUID.randomUUID().toString();
      stripeCompletionDatabaseTable.insertStripeCompletion(member.id(), token);
      var sessionBuilder = SessionCreateParams.builder()
        .addLineItem(SessionCreateParams.LineItem.builder()
          .setPrice(offer.priceId())
          .setQuantity(1L)
          .build())
        .setSuccessUrl(String.format(SUCCESS_URL_FORMAT, environment.domain(), token))
        .setCancelUrl(String.format(CANCEL_URL_FORMAT, environment.domain(),
          offer.id().toString()))
        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
        .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED);
      if (!accountId.isEmpty()) {
        sessionBuilder.setCustomer(accountId);
      } else {
        sessionBuilder.setCustomerEmail(member.email());
      }
      var checkout = stripeClient.checkout().sessions()
        .create(sessionBuilder.build());
      return Map.of("success", true, "link", checkout.getUrl());
    } catch (Exception exception) {
      errorRepository.processError(exception);
      return Map.of("success", false);
    }
  }

  private CompletableFuture<String> findExistingAccount(UUID partnerId) {
    return stripeDatabaseTable.stripeAccountExistsByPartner(partnerId)
      .thenCompose(exists -> exists ?
        stripeDatabaseTable.findStripeAccountByPartner(partnerId)
          .thenApply(StripeAccount::accountId) :
        CompletableFuture.completedFuture(""));
  }

  @HomeEndpoint
  @RequestMapping(path = "/offer/decline/", method = RequestMethod.POST)
  public void declineOffer(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    performOfferOperation(findMemberId(request), body.getUUID("offer"),
      this::declineOffer, () -> {});
  }

  private void declineOffer(Offer offer) {
    offerDatabaseTable().updateOfferStatus(offer, OfferStatus.DECLINED);
    try {
      stripeClient.prices().update(offer.priceId(),
        PriceUpdateParams.builder().setActive(false).build());
    } catch (Exception exception) {
      errorRepository.processError(exception);
    }
  }
}
