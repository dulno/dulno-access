package com.dulno.access.user.coupon;

import com.dulno.access.user.UserController;
import com.dulno.core.coupon.Coupon;
import com.dulno.core.coupon.CouponDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserCouponCollectionController extends UserController {
  private final CouponDatabaseTable couponDatabaseTable;
  private final UserRedeemableDatabaseTable userRedeemableDatabaseTable;
  private final RedeemableDatabaseTable redeemableDatabaseTable;

  private UserCouponCollectionController(
    @Qualifier("appKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    CouponDatabaseTable couponDatabaseTable,
    UserRedeemableDatabaseTable userRedeemableDatabaseTable,
    RedeemableDatabaseTable redeemableDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.couponDatabaseTable = couponDatabaseTable;
    this.userRedeemableDatabaseTable = userRedeemableDatabaseTable;
    this.redeemableDatabaseTable = redeemableDatabaseTable;
  }

  @RequestMapping(path = "/user/coupon/collect/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> collectCoupon(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var couponId = body.getUUID("coupon");
    if (!hasAuthorization(request)) {
      return collectCoupon(body, null, couponId);
    }
    return findUser(request, response)
      .thenCompose(user -> checkUserAuthorization(body, user, couponId));
  }

  private CompletableFuture<Map<String, Object>> checkUserAuthorization(
    DulnoRequestBody body, User user, UUID couponId
  ) {
    if (user == null) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return collectCoupon(body, user, couponId);
  }

  private CompletableFuture<Map<String, Object>> collectCoupon(
    DulnoRequestBody body, User user, UUID couponId
  ) {
    return couponDatabaseTable.couponExists(couponId)
      .thenCompose(couponExists -> checkCouponCollectionExistence(body,
        couponId, user, couponExists));
  }

  private CompletableFuture<Map<String, Object>> checkCouponCollectionExistence(
    DulnoRequestBody body, UUID couponId, User user, boolean couponExists
  ) {
    if (!couponExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    return hasCouponAlreadyCollected(body, couponId, user)
        .thenCompose(alreadyCollected -> couponDatabaseTable.findCoupon(couponId)
          .thenCompose(coupon -> checkCouponCollectionAvailability(coupon,
            user, alreadyCollected)));
  }

  private CompletableFuture<Boolean> hasCouponAlreadyCollected(
    DulnoRequestBody body, UUID couponId, User user
  ) {
    if (user == null) {
      return hasCouponAlreadyCollectedUnauthorized(body, couponId);
    }
    return hasCouponAlreadyCollectedAuthorized(couponId, user);
  }

  private CompletableFuture<Boolean> hasCouponAlreadyCollectedUnauthorized(
    DulnoRequestBody body, UUID couponId
  ) {
    if (!body.has("coupons")) {
      return CompletableFuture.completedFuture(true);
    }
    var couponIds = body.<String>getList("coupons")
      .stream().map(UUID::fromString).toList();
    return AsyncIterator.execute(couponIds, this::findUnclaimedRedeemable)
      .thenApply(entries -> entries.stream().filter(Objects::nonNull)
        .anyMatch(entry -> entry.couponId().equals(couponId)));
  }

  private CompletableFuture<Redeemable> findUnclaimedRedeemable(UUID redeemableId) {
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

  private CompletableFuture<Boolean> hasCouponAlreadyCollectedAuthorized(
    UUID couponId, User user
  ) {
    return userRedeemableDatabaseTable.findAllUserRedeemable(user.id())
      .thenCompose(redeemableList -> AsyncIterator.execute(redeemableList,
          redeemableDatabaseTable::findRedeemable)
        .thenApply(entries -> entries.stream()
          .anyMatch(entry -> entry.couponId().equals(couponId))));
  }

  private CompletableFuture<Map<String, Object>> checkCouponCollectionAvailability(
    Coupon coupon, User user, boolean alreadyCollected
  ) {
    if (coupon.limitation() == 0) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
    if (alreadyCollected) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1002));
    }
    return redeemableDatabaseTable.generateAvailableRedeemableId()
      .thenCompose(redeemableId -> performCouponCollection(coupon, user,
        redeemableId));
  }

  private CompletableFuture<Map<String, Object>> performCouponCollection(
    Coupon coupon, User user, UUID redeemableId
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    var redeemable = Redeemable.create(redeemableId, coupon.partnerId(),
      coupon.id(), System.currentTimeMillis() + coupon.expiration(),
      System.currentTimeMillis());
    processes.add(redeemableDatabaseTable.insertRedeemable(redeemable));
    if (user != null) {
      processes.add(userRedeemableDatabaseTable.insertUserRedeemable(user.id(),
        redeemableId));
    }
    if (coupon.limitation() != -1) {
      coupon.changeLimitation(coupon.limitation() - 1);
      processes.add(couponDatabaseTable.updateCoupon(coupon));
    }
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> Map.of("success", true));
  }
}

