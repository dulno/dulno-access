package com.dulno.access.portal.campaign.notification;

import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.campaign.CampaignDatabaseTable;
import com.dulno.core.campaign.notification.CampaignNotification;
import com.dulno.core.campaign.notification.CampaignNotificationDatabaseTable;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class CampaignNotificationInformationController extends CampaignNotificationController {
  private CampaignNotificationInformationController(
    @Qualifier("portalKey") Key secretKey, MemberDatabaseTable memberDatabaseTable,
    CampaignDatabaseTable campaignDatabaseTable,
    CampaignNotificationDatabaseTable campaignNotificationDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable, campaignDatabaseTable,
      campaignNotificationDatabaseTable, partnerMemberDatabaseTable);
  }

  @PortalEndpoint
  @RequestMapping(path = "/campaign/notification/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findNotificationCampaign(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCampaignNotificationOperation(findMemberId(request),
      body.getUUID("notification"), notification -> futureResponse.complete(
        assemblyCampaignNotificationInformation(notification)),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  public Map<String, Object> assemblyCampaignNotificationInformation(
    CampaignNotification notification
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("success", true);
    information.put("id", notification.id());
    information.put("title", notification.title());
    information.put("body", notification.body());
    information.put("dispatch", notification.dispatch());
    information.put("state", notification.state().toString());
    return information;
  }
}

