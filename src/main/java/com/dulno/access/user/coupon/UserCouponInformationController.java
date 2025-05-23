package com.dulno.access.user.coupon;

import com.dulno.access.user.UserController;
import com.dulno.core.coupon.Coupon;
import com.dulno.core.coupon.CouponDatabaseTable;
import com.dulno.core.coupon.logo.CouponLogoDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.partner.Partner;
import com.dulno.core.partner.PartnerDatabaseTable;
import com.dulno.core.redeemable.Redeemable;
import com.dulno.core.redeemable.RedeemableDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.redeemable.UserRedeemableDatabaseTable;
import com.google.api.client.util.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserCouponInformationController extends UserController {
  private final UserRedeemableDatabaseTable userRedeemableDatabaseTable;
  private final RedeemableDatabaseTable redeemableDatabaseTable;
  private final CouponDatabaseTable couponDatabaseTable;
  private final CouponLogoDatabaseTable couponLogoDatabaseTable;
  private final PartnerDatabaseTable partnerDatabaseTable;

  private UserCouponInformationController(
    @Qualifier("appKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    UserRedeemableDatabaseTable userRedeemableDatabaseTable,
    RedeemableDatabaseTable redeemableDatabaseTable,
    CouponDatabaseTable couponDatabaseTable,
    CouponLogoDatabaseTable couponLogoDatabaseTable,
    PartnerDatabaseTable partnerDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.userRedeemableDatabaseTable = userRedeemableDatabaseTable;
    this.redeemableDatabaseTable = redeemableDatabaseTable;
    this.couponDatabaseTable = couponDatabaseTable;
    this.couponLogoDatabaseTable = couponLogoDatabaseTable;
    this.partnerDatabaseTable = partnerDatabaseTable;
  }

  @RequestMapping(path = "/user/coupons/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findUserCoupons(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    if (!hasAuthorization(request)) {
      return findUnknownUserCoupons(body);
    }
    return findUser(request, response)
      .thenCompose(this::findAuthorizedUserCoupons);
  }

  private CompletableFuture<Map<String, Object>> findAuthorizedUserCoupons(
    User user
  ) {
    if (user == null) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return userRedeemableDatabaseTable.findAllUserRedeemable(user.id())
      .thenCompose(redeemable -> AsyncIterator.execute(redeemable,
        redeemableDatabaseTable::findRedeemable))
      .thenCompose(this::collectRedeemableListInformation);
  }

  private CompletableFuture<Map<String, Object>> findUnknownUserCoupons(
    DulnoRequestBody body
  ) {
    if (!body.has("coupons")) {
      return CompletableFuture.completedFuture(Map.of("coupons",
        Lists.newArrayList()));
    }
    var redeemableIds = body.<String>getList("coupons")
      .stream().map(UUID::fromString).toList();
    return AsyncIterator.execute(redeemableIds, this::findUnclaimedRedeemable)
      .thenApply(entries -> entries.stream().filter(Objects::nonNull).toList())
      .thenCompose(this::collectRedeemableListInformation);
  }

  private CompletableFuture<Redeemable> findUnclaimedRedeemable(
    UUID redeemableId
  ) {
    return redeemableDatabaseTable.redeemableExists(redeemableId)
      .thenCompose(redeemableExists -> userRedeemableDatabaseTable
        .userRedeemableExists(redeemableId)
        .thenCompose(isClaimed -> findUnclaimedRedeemable(redeemableId,
          redeemableExists, isClaimed)));
  }

  private CompletableFuture<Redeemable> findUnclaimedRedeemable(
    UUID redeemableId, boolean redeemableExists, boolean isClaimed
  ) {
    if (!redeemableExists || isClaimed) {
      return CompletableFuture.completedFuture(null);
    }
    return redeemableDatabaseTable.findRedeemable(redeemableId);
  }

  private CompletableFuture<Map<String, Object>> collectRedeemableListInformation(
    List<Redeemable> redeemableList
  ) {
    return AsyncIterator.execute(redeemableList, this::collectRedeemableInformation)
      .thenApply(result -> result.stream().filter(Objects::nonNull).toList())
      .thenApply(result -> Map.of("coupons", result));
  }

  public CompletableFuture<Map<String, Object>> collectRedeemableInformation(
    Redeemable redeemable
  ) {
    return couponDatabaseTable.couponExists(redeemable.couponId())
      .thenCompose(couponExists -> collectRedeemableInformation(redeemable,
        couponExists));
  }

  private CompletableFuture<Map<String, Object>> collectRedeemableInformation(
    Redeemable redeemable, boolean couponExists
  ) {
    if (!couponExists) {
      return CompletableFuture.completedFuture(null);
    }
    return couponDatabaseTable.findCoupon(redeemable.couponId())
      .thenCompose(coupon -> partnerDatabaseTable.findPartner(coupon.partnerId())
        .thenCompose(partner -> couponLogoDatabaseTable.findCouponLogoId(coupon.id())
          .thenApply(logoId -> assemblyRedeemableInformation(redeemable,
            coupon, partner, logoId))));
  }

  private Map<String, Object> assemblyRedeemableInformation(
    Redeemable redeemable, Coupon coupon, Partner partner, UUID logoId
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("redeemableId", redeemable.redeemableId());
    information.put("couponId", coupon.id());
    information.put("couponBackgroundColor", coupon.backgroundColor());
    information.put("couponForegroundColor", coupon.foregroundColor());
    information.put("couponReward", coupon.reward());
    information.put("couponDescription", coupon.description());
    information.put("lastUpdate", redeemable.lastUpdate());
    information.put("expiration", redeemable.expiration());
    information.put("logoId", logoId);
    information.put("partnerId", partner.id());
    information.put("partnerName", partner.name());
    information.put("partnerDescription", partner.description());
    return information;
  }

  @RequestMapping(path = "/user/coupon/logo/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findCouponLogo(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var couponId = body.getUUID("coupon");
    return couponDatabaseTable.couponExists(couponId)
      .thenCompose(exists -> findCouponLogo(couponId, exists));
  }

  private CompletableFuture<Map<String, Object>> findCouponLogo(
    UUID couponId, boolean couponExists
  ) {
    if (!couponExists) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return couponLogoDatabaseTable.findCouponLogo(couponId)
      .thenApply(logo -> Map.of("logoId", logo.logoId(), "logo",
        Base64.getEncoder().encodeToString(logo.content())));
  }
}
