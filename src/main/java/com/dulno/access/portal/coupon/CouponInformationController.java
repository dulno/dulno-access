package com.dulno.access.portal.coupon;

import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.coupon.Coupon;
import com.dulno.core.coupon.CouponDatabaseTable;
import com.dulno.core.coupon.logo.CouponLogo;
import com.dulno.core.coupon.logo.CouponLogoDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.locale.Translation;
import com.dulno.core.member.MemberDatabaseTable;
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
import java.util.concurrent.CompletableFuture;

@RestController
public final class CouponInformationController extends CouponController {
  private final Translation translation;

  private CouponInformationController(
    @Qualifier("portalKey") Key secretKey, MemberDatabaseTable memberDatabaseTable,
    CouponDatabaseTable couponDatabaseTable,
    CouponLogoDatabaseTable couponLogoDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    Translation translation
  ) {
    super(secretKey, memberDatabaseTable, couponDatabaseTable,
      couponLogoDatabaseTable, partnerMemberDatabaseTable);
    this.translation = translation;
  }

  @PortalEndpoint
  @RequestMapping(path = "/coupons/list/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findCoupons(
    HttpServletRequest request
  ) {
    return partnerMemberDatabaseTable().findMemberPartner(findMemberId(request))
      .thenCompose(partner -> couponDatabaseTable().findCouponsOfPartner(partner)
        .thenCompose(coupons -> AsyncIterator.execute(coupons,
            this::gatherCouponInformation)
          .thenApply(information -> Map.of("coupons", information))));
  }

  @PortalEndpoint
  @RequestMapping(path = "/coupon/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findCoupon(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCouponOperation(findMemberId(request), body.getUUID("coupon"),
      coupon -> gatherCouponInformation(coupon).thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> gatherCouponInformation(Coupon coupon) {
    return couponLogoDatabaseTable().findCouponLogo(coupon.id())
      .thenApply(logo -> assemblyCouponInformation(coupon, logo));
  }

  private Map<String, Object> assemblyCouponInformation(Coupon coupon, CouponLogo logo) {
    var information = Maps.<String, Object>newHashMap();
    information.put("success", true);
    information.put("id", coupon.id());
    information.put("backgroundColor", coupon.backgroundColor());
    information.put("foregroundColor", coupon.foregroundColor());
    information.put("reward", coupon.reward());
    information.put("description", coupon.description());
    information.put("expiration", coupon.expiration());
    information.put("limitation", coupon.limitation());
    information.put("logoId", logo.logoId());
    information.put("logo", Base64.getEncoder().encodeToString(logo.content()));
    information.put("creation", coupon.creation());
    return information;
  }
}

