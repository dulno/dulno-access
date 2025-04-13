package com.dulno.access.portal.campaign;

import com.dulno.access.portal.PortalRestController;
import com.dulno.core.campaign.Campaign;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Accessors(fluent = true)
@Getter(AccessLevel.PROTECTED)
public class CampaignController extends PortalRestController {
  private final CampaignDatabaseTable campaignDatabaseTable;
  private final CampaignNotificationDatabaseTable campaignNotificationDatabaseTable;
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;

  protected CampaignController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    CampaignDatabaseTable campaignDatabaseTable,
    CampaignNotificationDatabaseTable campaignNotificationDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable);
    this.campaignDatabaseTable = campaignDatabaseTable;
    this.campaignNotificationDatabaseTable = campaignNotificationDatabaseTable;
    this.partnerMemberDatabaseTable = partnerMemberDatabaseTable;
  }

  protected void performCampaignOperation(
    UUID memberId, UUID campaignId, Consumer<Campaign> operation,
    Runnable failResponse
  ) {
    campaignDatabaseTable.campaignExists(campaignId).thenAccept(exists ->
      performCampaignOperation(memberId, campaignId, exists, operation,
        failResponse));
  }

  private void performCampaignOperation(
    UUID memberId, UUID campaignId, boolean campaignExists,
    Consumer<Campaign> operation, Runnable failResponse
  ) {
    if (!campaignExists) {
      failResponse.run();
      return;
    }
    campaignDatabaseTable.findCampaign(campaignId).thenAccept(campaign ->
      checkCampaignAuthorization(memberId, campaign).thenAccept(authorized ->
        performCampaignOperation(campaign, authorized, operation, failResponse)));
  }

  private void performCampaignOperation(
    Campaign campaign, boolean authorized, Consumer<Campaign> operation,
    Runnable failResponse
  ) {
    if (!authorized) {
      failResponse.run();
      return;
    }
    operation.accept(campaign);
  }

  protected void performCampaignNotificationOperation(
    UUID memberId, UUID notificationId, Consumer<CampaignNotification> operation,
    Runnable failResponse
  ) {
    campaignNotificationDatabaseTable.campaignNotificationExists(notificationId)
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
    campaignNotificationDatabaseTable.findCampaignNotification(notificationId)
      .thenAccept(notification -> campaignDatabaseTable
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

  protected CompletableFuture<Boolean> checkCampaignAuthorization(
    UUID memberId, Campaign campaign
  ) {
    return partnerMemberDatabaseTable.isPartnerMember(campaign.partnerId(),
      memberId);
  }
}