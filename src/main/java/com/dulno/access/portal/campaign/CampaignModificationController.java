package com.dulno.access.portal.campaign;

import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.bundle.Bundle;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.campaign.Campaign;
import com.dulno.core.campaign.CampaignDatabaseTable;
import com.dulno.core.campaign.CampaignType;
import com.dulno.core.campaign.notification.CampaignNotificationDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.google.api.client.util.Lists;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
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
public final class CampaignModificationController extends CampaignController {
  private final BundleDatabaseTable bundleDatabaseTable;

  private CampaignModificationController(
    @Qualifier("portalKey") Key secretKey, MemberDatabaseTable memberDatabaseTable,
    CampaignDatabaseTable campaignDatabaseTable,
    CampaignNotificationDatabaseTable campaignNotificationDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable, campaignDatabaseTable,
      campaignNotificationDatabaseTable, partnerMemberDatabaseTable);
    this.bundleDatabaseTable = bundleDatabaseTable;
  }

  @PortalEndpoint
  @RequestMapping(path = "/campaign/create/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> createCampaign(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var start = body.getLong("start");
    var end = body.getLong("end");
    if (start > end) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    return createCampaign(findMemberId(request),
      body.getSanitizedString("title", 64),
      body.getSanitizedString("description", 256),
      CampaignType.valueOf(body.getString("type")), start, end);
  }

  private CompletableFuture<Map<String, Object>> createCampaign(
    UUID memberId, String title, String description,
    CampaignType type, long start, long end
  ) {
    return campaignDatabaseTable().generateAvailableCampaignId()
      .thenCompose(campaignId -> partnerMemberDatabaseTable()
        .findMemberPartner(memberId)
        .thenCompose(partnerId -> bundleDatabaseTable.findBundle(partnerId)
          .thenCompose(bundle -> checkCampaignCreation(bundle, campaignId,
            partnerId, title, description, type, start, end))));
  }

  private CompletableFuture<Map<String, Object>> checkCampaignCreation(
    Bundle bundle, UUID campaignId, UUID partnerId, String title,
    String description, CampaignType type, long start, long end
  ) {
    if (!bundle.campaignAccess()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
    return completeCampaignCreation(campaignId, partnerId, title, description,
      type, start, end);
  }

  private CompletableFuture<Map<String, Object>> completeCampaignCreation(
    UUID campaignId, UUID partnerId, String title, String description,
    CampaignType type, long start, long end
  ) {
    var campaign = Campaign.create(campaignId, partnerId, title, description,
      type, start, end, null);
    return campaignDatabaseTable().insertCampaign(campaign)
      .thenApply(value -> Map.of("success", true, "campaign", campaign.id()));
  }

  @PortalEndpoint
  @RequestMapping(path = "/campaign/update/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> updateCampaign(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var start = body.getLong("start");
    var end = body.getLong("end");
    if (start > end) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    var couponId = body.getAny("coupon") == JSONObject.NULL ? null :
      body.getUUID("coupon");
    return updateCampaign(findMemberId(request), body.getUUID("campaign"),
      body.getSanitizedString("title", 64),
      body.getSanitizedString("description", 256),
      CampaignType.valueOf(body.getString("type")), start, end, couponId);
  }

  private CompletableFuture<Map<String, Object>> updateCampaign(
    UUID memberId, UUID campaignId, String title, String description,
    CampaignType type, long start, long end, UUID couponId
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCampaignOperation(memberId, campaignId,
      campaign -> completeCampaignUpdate(campaign, title, description, type,
        start, end, couponId).thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> completeCampaignUpdate(
    Campaign campaign, String title, String description, CampaignType type,
    long start, long end, UUID couponId
  ) {
    campaign.changeTitle(title);
    campaign.changeDescription(description);
    campaign.changeType(type);
    campaign.changeStart(start);
    campaign.changeEnd(end);
    campaign.changeCoupon(couponId);
    return campaignDatabaseTable().updateCampaign(campaign)
      .thenApply(value -> Map.of("success", true));
  }

  @PortalEndpoint
  @RequestMapping(path = "/campaign/delete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> deleteCampaign(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCampaignOperation(findMemberId(request), body.getUUID("campaign"),
      campaign -> deleteCampaign(campaign).thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> deleteCampaign(Campaign campaign) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    processes.add(campaignDatabaseTable().deleteCampaign(campaign.id()));
    processes.add(campaignNotificationDatabaseTable()
      .findNotificationsOfCampaign(campaign.id())
      .thenApply(notifications -> notifications.stream()
        .filter(notification -> notification.state().isPending()).toList())
      .thenCompose(notifications -> AsyncIterator.execute(notifications,
        notification -> campaignNotificationDatabaseTable()
          .deleteCampaignNotification(notification.id()))
        .thenApply(value -> null)));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> Map.of("success", true));
  }
}
