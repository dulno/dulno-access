package com.dulno.access.portal.campaign;

import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.campaign.CampaignDatabaseTable;
import com.dulno.core.campaign.CampaignType;
import com.dulno.core.campaign.notification.CampaignNotification;
import com.dulno.core.campaign.notification.CampaignNotificationDatabaseTable;
import com.dulno.core.campaign.Campaign;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.google.api.client.util.Lists;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
  private CampaignModificationController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    CampaignDatabaseTable campaignDatabaseTable,
    CampaignNotificationDatabaseTable campaignNotificationDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable, campaignDatabaseTable,
      campaignNotificationDatabaseTable, partnerMemberDatabaseTable);
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
    return partnerMemberDatabaseTable().findMemberPartner(findMemberId(request))
      .thenCompose(partnerId -> campaignDatabaseTable().generateAvailableCampaignId()
        .thenCompose(campaignId -> createCampaign(partnerId, campaignId,
          body.getSanitizedString("title", 64),
          body.getSanitizedString("description", 256),
          CampaignType.valueOf(body.getString("type")), start, end)));
  }

  private CompletableFuture<Map<String, Object>> createCampaign(
    UUID partnerId, UUID campaignId, String title, String description,
    CampaignType type, long start, long end
  ) {
    var campaign = Campaign.create(campaignId, partnerId, title, description,
      type, start, end);
    return campaignDatabaseTable().insertCampaign(campaign)
      .thenApply(value -> Map.of("success", true, "campaign", campaign.id()));
  }

  /*@PortalEndpoint
  @RequestMapping(path = "/campaign/update/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> updateCampaign(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCampaignOperation(findMemberId(request), body.getUUID("campaign"),
      campaign -> updateCampaign(campaign, body.getSanitizedString("backgroundColor"),
        body.getSanitizedString("foregroundColor"), body.getObject("content"))
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> updateCampaign(
    Campaign campaign, String backgroundColor, String foregroundColor,
    DulnoRequestBody content
  ) {
    campaign.changeBackgroundColor(backgroundColor);
    campaign.changeForegroundColor(foregroundColor);
    campaign.changeContent(parseCampaignContent(campaign.type(), content));
    return campaignDatabaseTable().updateCampaign(campaign)
      .thenApply(value -> Map.of("success", true));
  }*/

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
      .thenCompose(notifications -> AsyncIterator.execute(notifications,
        notification -> campaignNotificationDatabaseTable()
          .deleteCampaignNotification(notification.id()))
        .thenApply(value -> null)));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> Map.of("success", true));
  }

  @PortalEndpoint
  @RequestMapping(path = "/campaign/notification/delete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> deleteCampaignNotification(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCampaignNotificationOperation(findMemberId(request),
      body.getUUID("notification"), notification ->
        deleteCampaignNotification(notification)
          .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> deleteCampaignNotification(
    CampaignNotification notification
  ) {
    return campaignNotificationDatabaseTable()
      .deleteCampaignNotification(notification.id())
      .thenApply(value -> Map.of("success", true));
  }
}
