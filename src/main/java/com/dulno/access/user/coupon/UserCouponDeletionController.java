package com.dulno.access.user.coupon;

import com.dulno.access.user.UserController;
import com.dulno.core.redeemable.Redeemable;
import com.dulno.core.redeemable.RedeemableDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.redeemable.UserRedeemableDatabaseTable;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserCouponDeletionController extends UserController {
  private final UserRedeemableDatabaseTable userRedeemableDatabaseTable;
  private final RedeemableDatabaseTable redeemableDatabaseTable;

  private UserCouponDeletionController(
    @Qualifier("appKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    UserRedeemableDatabaseTable userRedeemableDatabaseTable,
    RedeemableDatabaseTable redeemableDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.userRedeemableDatabaseTable = userRedeemableDatabaseTable;
    this.redeemableDatabaseTable = redeemableDatabaseTable;
  }

  @RequestMapping(path = "/user/coupon/delete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> deleteCoupon(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var redeemableId = body.getUUID("coupon");
    if (!hasAuthorization(request)) {
      return deleteCoupon(null, redeemableId);
    }
    return findUser(request, response)
      .thenCompose(user -> checkUserAuthorization(user, redeemableId));
  }

  private CompletableFuture<Map<String, Object>> checkUserAuthorization(
    User user, UUID redeemableId
  ) {
    if (user == null) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return deleteCoupon(user, redeemableId);
  }

  private CompletableFuture<Map<String, Object>> deleteCoupon(
    User user, UUID redeemableId
  ) {
    return redeemableDatabaseTable.redeemableExists(redeemableId)
      .thenCompose(redeemableExists -> userRedeemableDatabaseTable
        .userRedeemableExists(redeemableId)
        .thenCompose(userRedeemableExists -> checkCouponDeleteExistence(
          redeemableId, user, redeemableExists, userRedeemableExists)));
  }

  private CompletableFuture<Map<String, Object>> checkCouponDeleteExistence(
    UUID redeemableId, User user, boolean redeemableExists,
    boolean userRedeemableExists
  ) {
    if (!redeemableExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    if (user == null && userRedeemableExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
    return redeemableDatabaseTable.findRedeemable(redeemableId)
      .thenCompose(redeemable -> checkUnclaimedCouponDeletion(redeemable,
        user, userRedeemableExists));
  }

  private CompletableFuture<Map<String, Object>> checkUnclaimedCouponDeletion(
    Redeemable redeemable, User user, boolean userRedeemableExists
  ) {
    if (user == null && !userRedeemableExists) {
      return performCouponDeletion(redeemable)
        .thenApply(value -> Map.of("success", true));
    }
    return userRedeemableDatabaseTable.findRedeemableUser(redeemable.redeemableId())
      .thenCompose(owner -> checkCouponDeletionOwner(redeemable, user, owner));
  }

  private CompletableFuture<Map<String, Object>> checkCouponDeletionOwner(
    Redeemable redeemable, User user, UUID couponOwner
  ) {
    if (!user.id().equals(couponOwner)) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1002));
    }
    return userRedeemableDatabaseTable
      .deleteUserRedeemable(user.id(), redeemable.redeemableId())
      .thenCompose(value -> performCouponDeletion(redeemable))
      .thenApply(value -> Map.of("success", true));
  }

  private CompletableFuture<Void> performCouponDeletion(Redeemable redeemable) {
    return redeemableDatabaseTable.deleteRedeemable(redeemable.redeemableId())
      .thenApply(value -> null);
  }
}
