package com.dulno.access.portal.coupon;

import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.bundle.Bundle;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.coupon.Coupon;
import com.dulno.core.coupon.CouponDatabaseTable;
import com.dulno.core.coupon.logo.CouponLogoDatabaseTable;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.PartnerConfiguration;
import com.dulno.core.partner.logo.PartnerLogoDatabaseTable;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class CouponModificationController extends CouponController {
  private final PartnerConfiguration partnerConfiguration;
  private final PartnerLogoDatabaseTable partnerLogoDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;

  private CouponModificationController(
    @Qualifier("portalKey") Key secretKey, MemberDatabaseTable memberDatabaseTable,
    CouponDatabaseTable couponDatabaseTable,
    CouponLogoDatabaseTable couponLogoDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    PartnerConfiguration partnerConfiguration,
    PartnerLogoDatabaseTable partnerLogoDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable, couponDatabaseTable,
      couponLogoDatabaseTable, partnerMemberDatabaseTable);
    this.partnerConfiguration = partnerConfiguration;
    this.partnerLogoDatabaseTable = partnerLogoDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
  }

  @PortalEndpoint
  @RequestMapping(path = "/coupon/create/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> createCoupon(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var memberId = findMemberId(request);
    return partnerMemberDatabaseTable().findMemberPartner(memberId)
      .thenCompose(partnerId -> bundleDatabaseTable.findBundle(partnerId)
        .thenCompose(bundle -> createCoupon(memberId, bundle)));
  }

  private CompletableFuture<Map<String, Object>> createCoupon(
    UUID memberId, Bundle bundle
  ) {
    if (!bundle.couponAccess()) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return createCoupon(memberId);
  }

  private CompletableFuture<Map<String, Object>> createCoupon(
    UUID memberId
  ) {
    return couponDatabaseTable().generateAvailableCouponId()
      .thenCompose(couponId -> partnerMemberDatabaseTable().findMemberPartner(memberId)
        .thenCompose(partnerId -> couponDatabaseTable()
          .insertCoupon(createDefaultCoupon(couponId, partnerId))
          .thenCompose(value -> partnerLogoDatabaseTable.findPartnerLogo(partnerId)
            .thenCompose(logo -> couponLogoDatabaseTable()
              .generateAvailableCouponLogoId(couponId)
              .thenCompose(logoId -> couponLogoDatabaseTable()
                .insertCouponLogo(couponId, logoId, logo.content()))))
          .thenApply(value -> Map.of("success", true, "coupon", couponId))));
  }

  private Coupon createDefaultCoupon(UUID couponId, UUID partnerId) {
    return Coupon.create(couponId, partnerId, "#ffffff", "#000000", "", "",
      -1, System.currentTimeMillis());
  }

  @PortalEndpoint
  @RequestMapping(path = "/coupon/update/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> updateCoupon(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCouponOperation(findMemberId(request), body.getUUID("coupon"),
      coupon -> updateCoupon(coupon, body.getSanitizedString("backgroundColor"),
        body.getSanitizedString("foregroundColor"),
        body.getSanitizedString("reward"), body.getSanitizedString("description"),
        body.getLong("expiration"))
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> updateCoupon(
    Coupon coupon, String backgroundColor, String foregroundColor,
    String reward, String description, long expiration
  ) {
    coupon.changeBackgroundColor(backgroundColor);
    coupon.changeForegroundColor(foregroundColor);
    coupon.changeReward(reward);
    coupon.changeDescription(description);
    coupon.changeExpiration(expiration);
    return couponDatabaseTable().updateCoupon(coupon)
      .thenApply(value -> Map.of("success", true));
  }

  @PortalEndpoint
  @RequestMapping(path = "/coupon/logo/update/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> updateCouponLogo(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCouponOperation(findMemberId(request), body.getUUID("coupon"),
      coupon -> updateCouponLogo(coupon, body.getString("logo"))
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  private static final long MAX_LOGO_SIZE = 1024L * 1024;

  public CompletableFuture<Map<String, Object>> updateCouponLogo(
    Coupon coupon, String logo
  ) {
    var parsedLogo = parseCouponLogo(logo);
    if (parsedLogo.length > MAX_LOGO_SIZE) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return couponLogoDatabaseTable().findCouponLogoId(coupon.id())
      .thenCompose(previousLogoId -> couponLogoDatabaseTable()
        .generateAvailableCouponLogoId(coupon.id())
        .thenCompose(logoId -> couponLogoDatabaseTable()
          .insertCouponLogo(coupon.id(), logoId, parsedLogo)
          .thenCompose(value -> couponLogoDatabaseTable()
            .deleteCouponLogo(coupon.id(), previousLogoId))))
      .thenApply(value -> Map.of("success", true));
  }

  private byte[] parseCouponLogo(String couponLogo) {
    try {
      return Base64.getDecoder().decode(couponLogo);
    } catch (Exception exception) {
      return Base64.getDecoder().decode(partnerConfiguration.defaultLogo());
    }
  }

  @PortalEndpoint
  @RequestMapping(path = "/coupon/delete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> deleteCoupon(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCouponOperation(findMemberId(request), body.getUUID("coupon"),
      coupon -> deleteCoupon(coupon).thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> deleteCoupon(Coupon coupon) {
    return couponDatabaseTable().deleteCoupon(coupon.id())
      .thenCompose(value -> couponLogoDatabaseTable().deleteCouponLogo(coupon.id()))
      .thenApply(value -> Map.of("success", true));
  }
}
