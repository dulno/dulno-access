package com.dulno.access.portal.coupon;

import com.dulno.access.portal.PortalRestController;
import com.dulno.core.coupon.Coupon;
import com.dulno.core.coupon.CouponDatabaseTable;
import com.dulno.core.coupon.logo.CouponLogoDatabaseTable;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Qualifier;

import java.security.Key;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Accessors(fluent = true)
@Getter(AccessLevel.PROTECTED)
public class CouponController extends PortalRestController {
  private final CouponDatabaseTable couponDatabaseTable;
  private final CouponLogoDatabaseTable couponLogoDatabaseTable;
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;

  protected CouponController(
    @Qualifier("portalKey") Key secretKey, MemberDatabaseTable memberDatabaseTable,
    CouponDatabaseTable couponDatabaseTable,
    CouponLogoDatabaseTable couponLogoDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable);
    this.couponDatabaseTable = couponDatabaseTable;
    this.couponLogoDatabaseTable = couponLogoDatabaseTable;
    this.partnerMemberDatabaseTable = partnerMemberDatabaseTable;
  }

  protected void performCouponOperation(
    UUID memberId, UUID couponId, Consumer<Coupon> operation,
    Runnable failResponse
  ) {
    couponDatabaseTable.couponExists(couponId).thenAccept(exists ->
      performCouponOperation(memberId, couponId, exists, operation,
        failResponse));
  }

  private void performCouponOperation(
    UUID memberId, UUID couponId, boolean couponExists,
    Consumer<Coupon> operation, Runnable failResponse
  ) {
    if (!couponExists) {
      failResponse.run();
      return;
    }
    couponDatabaseTable.findCoupon(couponId).thenAccept(coupon ->
      checkCouponAuthorization(memberId, coupon).thenAccept(authorized ->
        performCouponOperation(coupon, authorized, operation, failResponse)));
  }

  private void performCouponOperation(
    Coupon coupon, boolean authorized, Consumer<Coupon> operation,
    Runnable failResponse
  ) {
    if (!authorized) {
      failResponse.run();
      return;
    }
    operation.accept(coupon);
  }

  protected CompletableFuture<Boolean> checkCouponAuthorization(
    UUID memberId, Coupon coupon
  ) {
    return partnerMemberDatabaseTable.isPartnerMember(coupon.partnerId(), memberId);
  }
}