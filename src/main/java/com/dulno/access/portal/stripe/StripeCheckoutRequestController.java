package com.dulno.access.portal.stripe;

import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.HomeRestController;
import com.dulno.core.accessory.AccessoryPresetRepository;
import com.dulno.core.bundle.Bundle;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.bundle.BundleRuntime;
import com.dulno.core.bundle.BundleType;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.member.Member;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.PartnerMemberDatabaseTable;
import com.dulno.core.stripe.StripeAccount;
import com.dulno.core.stripe.StripeCompletionDatabaseTable;
import com.dulno.core.stripe.StripeConfiguration;
import com.dulno.core.stripe.StripeDatabaseTable;
import com.stripe.StripeClient;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StripeCheckoutRequestController extends HomeRestController {
  private final StripeDatabaseTable stripeDatabaseTable;
  private final StripeCompletionDatabaseTable stripeCompletionDatabaseTable;
  private final StripeConfiguration stripeConfiguration;
  private final StripeClient stripeClient;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final AccessoryPresetRepository accessoryPresetRepository;
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;
  private final ErrorRepository errorRepository;
  private final DulnoEnvironment environment;

  private StripeCheckoutRequestController(
    @Qualifier("homeKey") Key secretKey, MemberDatabaseTable memberDatabaseTable,
    StripeDatabaseTable stripeDatabaseTable,
    StripeCompletionDatabaseTable stripeCompletionDatabaseTable,
    StripeConfiguration stripeConfiguration, StripeClient stripeClient,
    BundleDatabaseTable bundleDatabaseTable,
    AccessoryPresetRepository accessoryPresetRepository,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    ErrorRepository errorRepository, DulnoEnvironment environment
  ) {
    super(secretKey, memberDatabaseTable);
    this.stripeDatabaseTable = stripeDatabaseTable;
    this.stripeCompletionDatabaseTable = stripeCompletionDatabaseTable;
    this.stripeConfiguration = stripeConfiguration;
    this.stripeClient = stripeClient;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.accessoryPresetRepository = accessoryPresetRepository;
    this.partnerMemberDatabaseTable = partnerMemberDatabaseTable;
    this.errorRepository = errorRepository;
    this.environment = environment;
  }

  @RequestMapping(path = "/checkout/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> checkout(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var bundleType = BundleType.valueOf(body.getString("bundleType"));
    var bundleRuntime = BundleRuntime.valueOf(body.getString("bundleRuntime"));
    return findMember(request).thenCompose(member -> checkout(member, bundleType,
      bundleRuntime, body.getObjectList("accessories")));
  }

  private CompletableFuture<Map<String, Object>> checkout(
    Member member, BundleType bundleType, BundleRuntime bundleRuntime,
    List<DulnoRequestBody> accessories
  ) {
    if (bundleType.isEnterprise()) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return partnerMemberDatabaseTable.findMemberPartner(member.id())
      .thenCompose(partnerId -> checkBundleUsability(partnerId, bundleType)
        .thenCompose(bundleUsability -> checkout(member, partnerId, bundleType,
          bundleRuntime, accessories, bundleUsability)));
  }

  private CompletableFuture<Map<String, Object>> checkout(
    Member member, UUID partnerId, BundleType bundleType,
    BundleRuntime bundleRuntime, List<DulnoRequestBody> accessories,
    boolean bundleUsability
  ) {
    if (!bundleUsability) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return findExistingAccount(partnerId).thenApplyAsync(accountId ->
      checkout(member, bundleType, bundleRuntime, accessories, accountId));
  }

  private CompletableFuture<String> findExistingAccount(UUID partnerId) {
    return stripeDatabaseTable.stripeAccountExistsByPartner(partnerId)
      .thenCompose(exists -> exists ?
        stripeDatabaseTable.findStripeAccountByPartner(partnerId)
          .thenApply(StripeAccount::accountId) :
        CompletableFuture.completedFuture(""));
  }

  private static final String SUCCESS_URL_FORMAT = "https://%s/payment/complete/%s/";
  private static final String CANCEL_URL_FORMAT = "https://%s/payment/";

  private Map<String, Object> checkout(
    Member member, BundleType bundleType, BundleRuntime bundleRuntime,
    List<DulnoRequestBody> accessories, String accountId
  ) {
    try {
      var token = UUID.randomUUID().toString();
      stripeCompletionDatabaseTable.insertStripeCompletion(member.id(), token);
      var sessionBuilder = SessionCreateParams.builder()
        .addLineItem(createItem(stripeConfiguration.findPriceId(bundleType,
          bundleRuntime), 1L))
        .setSuccessUrl(String.format(SUCCESS_URL_FORMAT, environment.domain(), token))
        .setCancelUrl(String.format(CANCEL_URL_FORMAT, environment.domain()))
        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
        .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED);
      for (var accessory : accessories) {
        sessionBuilder.addLineItem(createItem(accessoryPresetRepository.findPreset(
          accessory.getString("id")).get().priceId(), accessory.getInt("amount")));
      }
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

  private SessionCreateParams.LineItem createItem(String priceId, long quantity) {
    return SessionCreateParams.LineItem.builder().setPrice(priceId)
      .setQuantity(quantity).build();
  }

  private CompletableFuture<Boolean> checkBundleUsability(
    UUID partnerId, BundleType bundleType
  ) {
    return bundleDatabaseTable.bundleExists(partnerId)
      .thenCompose(exists -> checkBundleUsability(partnerId, bundleType, exists));
  }

  private CompletableFuture<Boolean> checkBundleUsability(
    UUID partnerId, BundleType bundleType, boolean bundleExists
  ) {
    if (!bundleExists) {
      return CompletableFuture.completedFuture(true);
    }
    return bundleDatabaseTable.findBundle(partnerId)
      .thenApply(bundle -> checkBundleUsability(bundle, bundleType));
  }

  private boolean checkBundleUsability(Bundle bundle, BundleType bundleType) {
    return bundle.bundleType().value() < bundleType.value() ||
      (bundle.bundleType().value() == bundleType.value() &&
        System.currentTimeMillis() > bundle.expiration());
  }
}
