package com.dulno.access.user.bind;

import com.dulno.core.coupon.Coupon;
import com.dulno.core.coupon.CouponDatabaseTable;
import com.dulno.core.redeemable.Redeemable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.redeemable.RedeemableDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.redeemable.UserRedeemableDatabaseTable;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Configuration
@RequiredArgsConstructor
public class UserBindCouponMerge {
  private final RedeemableDatabaseTable redeemableDatabaseTable;
  private final UserRedeemableDatabaseTable userRedeemableDatabaseTable;
  private final CouponDatabaseTable couponDatabaseTable;

  public CompletableFuture<Void> mergeUserCoupons(
    User targetUser, DulnoRequestBody body
  ) {
    return userRedeemableDatabaseTable.findAllUserRedeemable(targetUser.id())
      .thenCompose(this::findRedeemableList)
      .thenCompose(targetCoupons -> findUnknownUserRedeemableList(body)
        .thenCompose(sourceCoupons -> findMergeCoupons(sourceCoupons, targetCoupons)
          .thenCompose(coupons -> processRedeemableMerges(targetUser, sourceCoupons,
            targetCoupons, coupons))));
  }

  private CompletableFuture<List<Redeemable>> findRedeemableList(
    List<UUID> redeemableIds
  ) {
    return AsyncIterator.execute(redeemableIds,
      redeemableDatabaseTable::findRedeemable);
  }

  private CompletableFuture<List<Redeemable>> findUnknownUserRedeemableList(
    DulnoRequestBody body
  ) {
    if (!body.has("coupons")) {
      return CompletableFuture.completedFuture(Lists.newArrayList());
    }
    var redeemableIds = body.<String>getList("coupons")
      .stream().map(UUID::fromString).toList();
    return AsyncIterator.execute(redeemableIds, this::findUnclaimedRedeemable)
      .thenApply(redeemableList -> redeemableList.stream()
        .filter(Objects::nonNull).toList());
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

  private CompletableFuture<List<Coupon>> findMergeCoupons(
    List<Redeemable> sourceRedeemableList, List<Redeemable> targetRedeemableList
  ) {
    var allRedeemable = Lists.<Redeemable>newArrayList();
    allRedeemable.addAll(sourceRedeemableList);
    allRedeemable.addAll(targetRedeemableList);
    var couponIds = allRedeemable.stream().map(Redeemable::couponId)
      .distinct().toList();
    return AsyncIterator.execute(couponIds, couponId ->
        couponDatabaseTable.couponExists(couponId)
          .thenCompose(exists -> exists ?
            couponDatabaseTable.findCoupon(couponId) :
            CompletableFuture.completedFuture(null)))
      .thenApply(result -> result.stream().filter(Objects::nonNull).toList());
  }

  private CompletableFuture<Void> processRedeemableMerges(
    User targetUser, List<Redeemable> sourceRedeemableList,
    List<Redeemable> targetRedeemableList, List<Coupon> coupons
  ) {
    return AsyncIterator.execute(sourceRedeemableList,
        redeemable -> checkRedeemableMergeCompatibility(targetUser, redeemable,
          targetRedeemableList, coupons)
          .thenCompose(value -> redeemableDatabaseTable
            .deleteRedeemable(redeemable.redeemableId())))
      .thenApply(value -> null);
  }

  private CompletableFuture<Void> checkRedeemableMergeCompatibility(
    User targetUser, Redeemable sourceRedeemable,
    List<Redeemable> targetRedeemableList, List<Coupon> coupons
  ) {
    var sourceCouponOptional = coupons.stream()
      .filter(coupon -> coupon.id().equals(sourceRedeemable.couponId())).findFirst();
    if (sourceCouponOptional.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    var sourceCoupon = sourceCouponOptional.get();
    var targetRedeemableOptional = targetRedeemableList.stream()
      .filter(entry -> entry.couponId().equals(sourceCoupon.id())).findFirst();
    return mergeUserRedeemable(targetUser, sourceRedeemable,
      targetRedeemableOptional);
  }

  private CompletableFuture<Void> mergeUserRedeemable(
    User targetUser, Redeemable sourceRedeemable,
    Optional<Redeemable> targetRedeemableOptional
  ) {
    if (targetRedeemableOptional.isEmpty()) {
      return redeemableDatabaseTable.generateAvailableRedeemableId()
        .thenCompose(redeemableId -> mergeUserNewRedeemable(targetUser,
          sourceRedeemable, redeemableId));
    }
    return CompletableFuture.completedFuture(null);
  }

  private CompletableFuture<Void> mergeUserNewRedeemable(
    User targetUser, Redeemable sourceRedeemable, UUID newRedeemableId
  ) {
    var newRedeemable = Redeemable.create(newRedeemableId,
      sourceRedeemable.partnerId(), sourceRedeemable.couponId(),
      sourceRedeemable.expiration(), sourceRedeemable.lastUpdate());
    return redeemableDatabaseTable.insertRedeemable(newRedeemable)
      .thenAccept(value -> userRedeemableDatabaseTable.insertUserRedeemable(
        targetUser.id(), newRedeemableId));
  }
}
