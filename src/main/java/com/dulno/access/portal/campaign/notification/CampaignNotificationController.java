package com.dulno.access.portal.campaign.notification;

import com.dulno.access.portal.campaign.CampaignController;
import com.dulno.core.campaign.CampaignDatabaseTable;
import com.dulno.core.campaign.notification.CampaignNotification;
import com.dulno.core.campaign.notification.CampaignNotificationDatabaseTable;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.security.Key;
import java.util.UUID;
import java.util.function.Consumer;

@Accessors(fluent = true)
@Getter(AccessLevel.PROTECTED)
public class CampaignNotificationController extends CampaignController {
  protected CampaignNotificationController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    CampaignDatabaseTable campaignDatabaseTable,
    CampaignNotificationDatabaseTable campaignNotificationDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable, campaignDatabaseTable,
      campaignNotificationDatabaseTable, partnerMemberDatabaseTable);
  }

  protected void performCampaignNotificationOperation(
    UUID memberId, UUID notificationId, Consumer<CampaignNotification> operation,
    Runnable failResponse
  ) {
    campaignNotificationDatabaseTable().campaignNotificationExists(notificationId)
      .thenAccept(exists -> performCampaignNotificationOperation(memberId,
        notificationId, exists, operation, failResponse));
  }

  private void performCampaignNotificationOperation(
    UUID memberId, UUID notificationId, boolean campaignExists,
    Consumer<CampaignNotification> operation, Runnable failResponse
  ) {
    if (!campaignExists) {
      failResponse.run();
      return;
    }
    campaignNotificationDatabaseTable().findCampaignNotification(notificationId)
      .thenAccept(notification -> campaignDatabaseTable()
        .findCampaign(notification.campaignId())
        .thenAccept(campaign -> checkCampaignAuthorization(memberId, campaign)
          .thenAccept(authorized -> performCampaignNotificationOperation(
            notification, authorized, operation, failResponse))));
  }

  private void performCampaignNotificationOperation(
    CampaignNotification notification, boolean authorized,
    Consumer<CampaignNotification> operation, Runnable failResponse
  ) {
    if (!authorized) {
      failResponse.run();
      return;
    }
    operation.accept(notification);
  }
}