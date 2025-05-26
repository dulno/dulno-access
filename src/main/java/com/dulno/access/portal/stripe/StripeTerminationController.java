package com.dulno.access.portal.stripe;

import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.bundle.Bundle;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.PartnerDatabaseTable;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import com.dulno.core.stripe.StripeAccount;
import com.dulno.core.stripe.StripeConfiguration;
import com.dulno.core.stripe.StripeDatabaseTable;
import com.dulno.core.stripe.TerminationDatabaseTable;
import com.google.common.collect.Maps;
import com.stripe.StripeClient;
import com.stripe.model.Subscription;
import com.stripe.param.SubscriptionListParams;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StripeTerminationController extends StripeController {
  private final StripeClient stripeClient;
  private final TerminationDatabaseTable terminationDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final ErrorRepository errorRepository;

  private StripeTerminationController(
    @Qualifier("portalKey") Key secretKey, MemberDatabaseTable memberDatabaseTable,
    StripeConfiguration stripeConfiguration,
    StripeDatabaseTable stripeDatabaseTable, StripeClient stripeClient,
    PartnerDatabaseTable partnerDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    TerminationDatabaseTable terminationDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable, ErrorRepository errorRepository
  ) {
    super(secretKey, memberDatabaseTable, stripeConfiguration, stripeDatabaseTable,
      partnerDatabaseTable, partnerMemberDatabaseTable);
    this.stripeClient = stripeClient;
    this.terminationDatabaseTable = terminationDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.errorRepository = errorRepository;
  }

  @PortalEndpoint
  @RequestMapping(path = "/termination/status/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findTerminationStatus(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performStripeOperation(findMemberId(request), account ->
        bundleDatabaseTable.findBundle(account.partnerId())
          .thenComposeAsync(bundle -> findTerminationStatus(account.partnerId(),
            bundle, account.accountId()).thenAccept(futureResponse::complete)),
      () -> futureResponse.complete(Map.of("terminable", false,
        "terminated", false)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> findTerminationStatus(
    UUID partnerId, Bundle bundle, String stripeAccountId
  ) {
    if (bundle.bundleRuntime().isUnbound()) {
      return CompletableFuture.completedFuture(Map.of("terminable", false,
        "terminated", false));
    }
    try {
      var subscriptions = stripeClient.subscriptions()
        .list(SubscriptionListParams.builder().setCustomer(stripeAccountId).build())
        .getData();
      if (subscriptions.isEmpty()) {
        return CompletableFuture.completedFuture(Map.of("terminable", true,
          "terminated", true));
      }
      return terminationDatabaseTable.terminationExists(partnerId)
        .thenApply(exists -> findTerminationStatus(bundle, exists));
    } catch (Exception exception) {
      errorRepository.processError(exception);
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
  }

  private Map<String, Object> findTerminationStatus(
    Bundle bundle, boolean terminationExists
  ) {
    if (!terminationExists) {
      return Map.of("terminable", true, "terminated", false, "directlyEffective",
        isDirectlyTerminable(bundle));
    }
    return Map.of("terminable", true, "terminated", true);
  }

  @PortalEndpoint
  @RequestMapping(path = "/terminate/", method = RequestMethod.GET)
  public void terminate(
    HttpServletRequest request
  ) {
    performStripeOperation(findMemberId(request), account ->
      bundleDatabaseTable.findBundle(account.partnerId())
        .thenAcceptAsync(bundle -> terminate(account.partnerId(), bundle, account)),
      () -> {});
  }

  public CompletableFuture<Void> terminate(UUID partnerId) {
    return stripeDatabaseTable().stripeAccountExistsByPartner(partnerId)
      .thenCompose(exists -> exists ?
        stripeDatabaseTable().findStripeAccountByPartner(partnerId)
          .thenCompose(account -> bundleDatabaseTable.findBundle(account.partnerId())
            .thenComposeAsync(bundle -> terminate(account.partnerId(), bundle, account))) :
        CompletableFuture.completedFuture(null));
  }

  private CompletableFuture<Void> terminate(
    UUID partnerId, Bundle bundle, StripeAccount account
  ) {
    if (bundle.bundleRuntime().isMonthly()) {
      return cancelSubscription(account);
    } else if (bundle.bundleRuntime().isYearly()) {
      return terminateYearly(partnerId, bundle, account);
    }
    return CompletableFuture.completedFuture(null);
  }

  private CompletableFuture<Void> terminateYearly(
    UUID partnerId, Bundle bundle, StripeAccount account
  ) {
    if (isDirectlyTerminable(bundle)) {
      return cancelSubscription(account);
    }
    return terminationDatabaseTable.insertTermination(partnerId);
  }

  public CompletableFuture<Void> cancelSubscription(Subscription subscription) {
    return stripeDatabaseTable().findStripeAccount(subscription.getCustomer())
      .thenComposeAsync(account -> cancelSubscription(subscription, account));
  }

  private CompletableFuture<Void> cancelSubscription(StripeAccount account) {
    try {
      var subscriptions = stripeClient.subscriptions()
        .list(SubscriptionListParams.builder().setCustomer(account.accountId()).build())
        .getData();
      if (subscriptions.isEmpty()) {
        return CompletableFuture.completedFuture(null);
      }
      return cancelSubscription(subscriptions.get(0), account);
    } catch (Exception exception) {
      errorRepository.processError(exception);
      return CompletableFuture.completedFuture(null);
    }
  }

  private CompletableFuture<Void> cancelSubscription(
    Subscription subscription, StripeAccount account
  ) {
    try {
      subscription.cancel();
      return stripeDatabaseTable().updateStripeAccount(account.accountId(),
        account.partnerId(), account.memberId(), "");
    } catch (Exception exception) {
      errorRepository.processError(exception);
      return CompletableFuture.completedFuture(null);
    }
  }

  private boolean isDirectlyTerminable(Bundle bundle) {
    var current = ZonedDateTime.ofInstant(Instant.ofEpochMilli(bundle.expiration()),
      ZoneId.systemDefault());
    var next = current.minusMonths(1);
    if (next.getDayOfMonth() != current.getDayOfMonth()) {
      next = next.withDayOfMonth(next.getMonth().length(
        next.toLocalDate().isLeapYear()));
    }
    return System.currentTimeMillis() > next.toInstant().toEpochMilli();
  }
}
