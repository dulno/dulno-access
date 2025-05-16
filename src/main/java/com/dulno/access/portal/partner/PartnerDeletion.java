package com.dulno.access.portal.partner;

import com.dulno.access.portal.stripe.StripeTerminationController;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.campaign.CampaignDatabaseTable;
import com.dulno.core.campaign.notification.CampaignNotificationDatabaseTable;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.card.logo.CardLogoDatabaseTable;
import com.dulno.core.partner.PartnerDatabaseTable;
import com.dulno.core.partner.analysis.card.PartnerActiveCardDatabaseTable;
import com.dulno.core.partner.analysis.card.PartnerCardUsageDatabaseTable;
import com.dulno.core.partner.analysis.card.PartnerNewCardDatabaseTable;
import com.dulno.core.partner.analysis.stamp.PartnerOpenStampDatabaseTable;
import com.dulno.core.partner.analysis.stamp.PartnerScanDatabaseTable;
import com.dulno.core.partner.analysis.user.PartnerActiveUserDatabaseTable;
import com.dulno.core.partner.analysis.user.PartnerNewUserDatabaseTable;
import com.dulno.core.partner.analysis.user.PartnerUserLanguageDatabaseTable;
import com.dulno.core.partner.link.PartnerLinkDatabaseTable;
import com.dulno.core.partner.location.PartnerLocationDatabaseTable;
import com.dulno.core.partner.logo.PartnerLogoDatabaseTable;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import com.dulno.core.partner.transaction.PartnerTransactionDatabaseTable;
import com.dulno.core.stamp.StampDatabaseTable;
import com.dulno.core.stripe.StripeDatabaseTable;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class PartnerDeletion {
  private final PartnerDatabaseTable partnerDatabaseTable;
  private final PartnerLogoDatabaseTable partnerLogoDatabaseTable;
  private final PartnerLocationDatabaseTable partnerLocationDatabaseTable;
  private final PartnerLinkDatabaseTable partnerLinkDatabaseTable;
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;
  private final PartnerScanDatabaseTable partnerScanDatabaseTable;
  private final PartnerNewUserDatabaseTable partnerNewUserDatabaseTable;
  private final PartnerNewCardDatabaseTable partnerNewCardDatabaseTable;
  private final PartnerCardUsageDatabaseTable partnerCardUsageDatabaseTable;
  private final PartnerUserLanguageDatabaseTable partnerUserLanguageDatabaseTable;
  private final PartnerTransactionDatabaseTable partnerTransactionDatabaseTable;
  private final PartnerActiveCardDatabaseTable partnerActiveCardDatabaseTable;
  private final PartnerOpenStampDatabaseTable partnerOpenStampDatabaseTable;
  private final PartnerActiveUserDatabaseTable partnerActiveUserDatabaseTable;
  private final CardDatabaseTable cardDatabaseTable;
  private final CardLogoDatabaseTable cardLogoDatabaseTable;
  private final StampDatabaseTable stampDatabaseTable;
  private final CampaignDatabaseTable campaignDatabaseTable;
  private final CampaignNotificationDatabaseTable campaignNotificationDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final StripeDatabaseTable stripeDatabaseTable;
  private final StripeTerminationController terminationController;

  public void deletePartner(UUID partnerId) {
    deletePartnerDependencies(partnerId);
    deletePartnerAnalysis(partnerId);
    deletePartnerCards(partnerId);
    deletePartnerStamps(partnerId);
    deletePartnerCampaigns(partnerId);
    deletePartnerBundle(partnerId);
  }

  private void deletePartnerDependencies(UUID partnerId) {
    partnerDatabaseTable.deletePartner(partnerId);
    partnerLogoDatabaseTable.deletePartnerLogo(partnerId);
    partnerLocationDatabaseTable.findPartnerLocations(partnerId)
      .thenAccept(locations -> locations.forEach(location ->
        partnerLocationDatabaseTable.deletePartnerLocation(
          partnerId, location.address())));
    partnerLinkDatabaseTable.findPartnerLinks(partnerId)
      .thenAccept(links -> links.forEach(link -> partnerLinkDatabaseTable
        .deletePartnerLink(partnerId, link.type().toString())));
    partnerMemberDatabaseTable.findPartnerMembers(partnerId)
      .thenAccept(members -> members.forEach(member -> partnerMemberDatabaseTable
        .deletePartnerMember(partnerId, member)));
  }

  private void deletePartnerAnalysis(UUID partnerId) {
    partnerScanDatabaseTable.deletePartnerScans(partnerId);
    partnerNewUserDatabaseTable.deletePartnerNewUsers(partnerId);
    partnerNewCardDatabaseTable.deletePartnerNewCards(partnerId);
    partnerCardUsageDatabaseTable.deletePartnerCardUsages(partnerId);
    partnerUserLanguageDatabaseTable.deletePartnerUserLanguages(partnerId);
    partnerTransactionDatabaseTable.deletePartnerTransactions(partnerId);
    partnerActiveCardDatabaseTable.deletePartnerActiveCards(partnerId);
    partnerOpenStampDatabaseTable.deletePartnerOpenStamps(partnerId);
    partnerActiveUserDatabaseTable.deletePartnerActiveUsers(partnerId);
  }

  private void deletePartnerCards(UUID partnerId) {
    cardDatabaseTable.findCardsOfPartner(partnerId)
      .thenAccept(cards -> cards.forEach(card ->
        cardDatabaseTable.deleteCard(card.id()).thenAccept(value ->
          cardLogoDatabaseTable.deleteCardLogo(card.id()))));
  }

  private void deletePartnerStamps(UUID partnerId) {
    stampDatabaseTable.findStampsOfPartner(partnerId)
      .thenAccept(stamps -> stamps.forEach(stamp ->
        stampDatabaseTable.deleteStamp(stamp.id())));
  }

  private void deletePartnerCampaigns(UUID partnerId) {
    campaignDatabaseTable.findCampaignsOfPartner(partnerId)
      .thenAccept(campaigns -> campaigns.forEach(campaign ->
        campaignDatabaseTable.deleteCampaign(campaign.id())));
    campaignNotificationDatabaseTable.findNotificationsOfPartner(partnerId)
      .thenAccept(notifications -> notifications.forEach(notification ->
        campaignNotificationDatabaseTable.deleteCampaignNotification(
          notification.id())));
  }

  private void deletePartnerBundle(UUID partnerId) {
    terminationController.terminate(partnerId)
      .thenAccept(terminationValue -> bundleDatabaseTable.deleteBundle(partnerId)
        .thenAccept(deletionValue -> stripeDatabaseTable
          .findStripeAccountByPartner(partnerId)
          .thenAccept(account -> stripeDatabaseTable
            .deleteStripeAccount(account.accountId()))));
  }
}
