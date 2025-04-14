package com.dulno.access.portal.campaign.notification;

import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.campaign.Campaign;
import com.dulno.core.campaign.CampaignDatabaseTable;
import com.dulno.core.campaign.notification.CampaignNotification;
import com.dulno.core.campaign.notification.CampaignNotificationDatabaseTable;
import com.dulno.core.campaign.notification.CampaignNotificationState;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
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
public final class CampaignNotificationModificationController extends CampaignNotificationController {
  private CampaignNotificationModificationController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    CampaignDatabaseTable campaignDatabaseTable,
    CampaignNotificationDatabaseTable campaignNotificationDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable, campaignDatabaseTable,
      campaignNotificationDatabaseTable, partnerMemberDatabaseTable);
  }

  @PortalEndpoint
  @RequestMapping(path = "/campaign/notification/create/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> createCampaignNotification(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var dispatch = body.getLong("dispatch");
    if (dispatch < System.currentTimeMillis()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCampaignOperation(findMemberId(request), body.getUUID("campaign"),
      campaign -> checkNotificationSpacing(campaign.partnerId(), dispatch, null)
        .thenCompose(spacingMaintained -> createCampaignNotification(
          campaign, body.getSanitizedString("title", 64),
          body.getSanitizedString("body", 128), dispatch, spacingMaintained))
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> createCampaignNotification(
    Campaign campaign, String title, String body, long dispatch,
    boolean spacingMaintained
  ) {
    if (!spacingMaintained) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
    return campaignNotificationDatabaseTable()
      .generateAvailableCampaignNotificationId()
      .thenCompose(notificationId -> createCampaignNotification(notificationId,
        campaign, title, body, dispatch));
  }

  private CompletableFuture<Map<String, Object>> createCampaignNotification(
    UUID notificationId, Campaign campaign, String title, String body,
    long dispatch
  ) {
    var notification = CampaignNotification.create(notificationId, campaign.id(),
      campaign.partnerId(), title, body, dispatch,
      CampaignNotificationState.PENDING);
    return campaignNotificationDatabaseTable()
      .insertCampaignNotification(notification)
      .thenApply(value -> Map.of("success", true,
        "notification", notification.id()));
  }

  @PortalEndpoint
  @RequestMapping(path = "/campaign/notification/update/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> updateCampaign(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var dispatch = body.getLong("dispatch");
    if (dispatch < System.currentTimeMillis()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCampaignNotificationOperation(findMemberId(request),
      body.getUUID("notification"), notification ->
        checkNotificationSpacing(notification.partnerId(), dispatch, notification.id())
          .thenCompose(spacingMaintained -> updateCampaignNotification(
            notification, body.getSanitizedString("title", 64),
            body.getSanitizedString("body", 128), dispatch, spacingMaintained))
          .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> updateCampaignNotification(
    CampaignNotification notification, String title, String body, long dispatch,
    boolean spacingMaintained
  ) {
    if (!spacingMaintained) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
    if (notification.state().isSent()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1002));
    }
    notification.changeTitle(title);
    notification.changeBody(body);
    notification.changeDispatch(dispatch);
    return campaignNotificationDatabaseTable()
      .updateCampaignNotification(notification)
      .thenApply(value -> Map.of("success", true));
  }

  private static final long NOTIFICATION_SPACING = 1000L * 60 * 60 * 12;

  private CompletableFuture<Boolean> checkNotificationSpacing(
    UUID partnerId, long dispatch, UUID notificationId
  ) {
    return campaignNotificationDatabaseTable()
      .findNotificationsOfPartner(partnerId)
      .thenApply(notifications -> notificationId == null ? notifications :
        notifications.stream().filter(notification ->
          !notification.id().equals(notificationId)).toList())
      .thenApply(notifications -> notifications.stream()
        .allMatch(notification -> Math.abs(dispatch - notification.dispatch()) >=
          NOTIFICATION_SPACING));
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
    if (notification.state().isSent()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    return campaignNotificationDatabaseTable()
      .deleteCampaignNotification(notification.id())
      .thenApply(value -> Map.of("success", true));
  }
}
