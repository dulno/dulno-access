package com.dulno.access.portal.offer;

import com.dulno.access.portal.security.home.HomeEndpoint;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.offer.Offer;
import com.dulno.core.offer.OfferDatabaseTable;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class OfferInformationController extends OfferController {
  private OfferInformationController(
    @Qualifier("homeKey") Key secretKey, MemberDatabaseTable memberDatabaseTable,
    OfferDatabaseTable offerDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable, offerDatabaseTable,
      partnerMemberDatabaseTable);
  }

  @HomeEndpoint
  @RequestMapping(path = "/offer/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findOffer(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performOfferOperation(findMemberId(request), body.getUUID("offer"),
      offer -> futureResponse.complete(findOffer(offer)),
      () -> futureResponse.complete(Map.of("found", false)));
    return futureResponse;
  }

  private Map<String, Object> findOffer(Offer offer) {
    var information = Maps.<String, Object>newHashMap();
    information.put("found", true);
    information.put("id", offer.id().toString());
    information.put("type", offer.bundleType().toString());
    information.put("runtime", offer.bundleRuntime().toString());
    information.put("price", offer.price());
    information.put("locationLimit", offer.locationLimit());
    information.put("portalAccess", offer.portalAccess());
    information.put("collectionCardAccess", offer.collectionCardAccess());
    information.put("valueCardAccess", offer.valueCardAccess());
    information.put("memberCardAccess", offer.memberCardAccess());
    information.put("analysisAccess", offer.analysisAccess());
    information.put("balanceAccess", offer.balanceAccess());
    information.put("couponAccess", offer.couponAccess());
    information.put("campaignAccess", offer.campaignAccess());
    information.put("notificationAccess", offer.notificationAccess());
    return information;
  }
}
