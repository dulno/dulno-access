package com.dulno.access.portal.campaign;

import com.dulno.access.portal.campaign.notification.CampaignNotificationInformationController;
import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.campaign.Campaign;
import com.dulno.core.campaign.CampaignDatabaseTable;
import com.dulno.core.campaign.CampaignType;
import com.dulno.core.campaign.notification.CampaignNotification;
import com.dulno.core.campaign.notification.CampaignNotificationDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.locale.Translation;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.google.api.client.util.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class CampaignInformationController extends CampaignController {
  private final Translation translation;
  private final CampaignNotificationInformationController campaignNotificationInformationController;

  private CampaignInformationController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    CampaignDatabaseTable campaignDatabaseTable,
    CampaignNotificationDatabaseTable campaignNotificationDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    Translation translation,
    CampaignNotificationInformationController campaignNotificationInformationController
  ) {
    super(secretKey, memberDatabaseTable, campaignDatabaseTable,
      campaignNotificationDatabaseTable, partnerMemberDatabaseTable);
    this.translation = translation;
    this.campaignNotificationInformationController = campaignNotificationInformationController;
  }

  @PortalEndpoint
  @RequestMapping(path = "/campaigns/list/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findCampaigns(
    HttpServletRequest request
  ) {
    return partnerMemberDatabaseTable().findMemberPartner(findMemberId(request))
      .thenCompose(partner -> campaignDatabaseTable()
        .findCampaignsOfPartner(partner)
        .thenCompose(campaigns -> AsyncIterator.execute(campaigns,
            this::gatherCampaignInformation)
          .thenApply(information -> Map.of("campaigns", information))));
  }

  @PortalEndpoint
  @RequestMapping(path = "/campaign/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findCampaign(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCampaignOperation(findMemberId(request), body.getUUID("campaign"),
      campaign -> gatherCampaignInformation(campaign)
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> gatherCampaignInformation(
    Campaign campaign
  ) {
    return campaignNotificationDatabaseTable()
      .findNotificationsOfCampaign(campaign.id())
      .thenApply(notifications -> assemblyCampaignInformation(campaign,
        notifications));
  }

  private Map<String, Object> assemblyCampaignInformation(
    Campaign campaign, List<CampaignNotification> notifications
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("success", true);
    information.put("id", campaign.id());
    information.put("title", campaign.title());
    information.put("description", campaign.description());
    information.put("type", campaign.type());
    information.put("start", campaign.start());
    information.put("end", campaign.end());
    var notificationsInformation = Lists.<Map<String, Object>>newArrayList();
    for (var notification : notifications) {
      notificationsInformation.add(campaignNotificationInformationController
        .assemblyCampaignNotificationInformation(notification));
    }
    information.put("notifications", notificationsInformation);
    return information;
  }

  @PortalEndpoint
  @RequestMapping(path = "/campaign/types/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findCampaignTypes(
    HttpServletRequest request
  ) {
    return findMember(request).thenApply(member ->
      Map.of("types", Arrays.stream(CampaignType.values())
        .map(entry -> Map.of("type", entry, "name",
          translation.translateMember(member, entry.locale()),
          "icon", entry.icon()))
        .toList()));
  }
}

