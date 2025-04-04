package com.dulno.access.portal.offer;

import com.dulno.access.portal.HomeRestController;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.offer.Offer;
import com.dulno.core.offer.OfferDatabaseTable;
import com.dulno.core.partner.PartnerMemberDatabaseTable;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.security.Key;
import java.util.UUID;
import java.util.function.Consumer;

@Getter
@Accessors(fluent = true)
public class OfferController  extends HomeRestController {
  private final OfferDatabaseTable offerDatabaseTable;
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;

  protected OfferController(
    Key homeKey, MemberDatabaseTable memberDatabaseTable,
    OfferDatabaseTable offerDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable
  ) {
    super(homeKey, memberDatabaseTable);
    this.offerDatabaseTable = offerDatabaseTable;
    this.partnerMemberDatabaseTable = partnerMemberDatabaseTable;
  }

  protected void performOfferOperation(
    UUID memberId, UUID offerId, Consumer<Offer> operation,
    Runnable failResponse
  ) {
    offerDatabaseTable.offerExists(offerId).thenAccept(exists ->
      performOfferOperation(memberId, offerId, exists, operation, failResponse));
  }

  protected void performOfferOperation(
    UUID memberId, UUID offerId, boolean offerExists, Consumer<Offer> operation,
    Runnable failResponse
  ) {
    if (!offerExists) {
      failResponse.run();
      return;
    }
    offerDatabaseTable.findOffer(offerId).thenAccept(offer ->
      partnerMemberDatabaseTable.isPartnerMember(offer.partnerId(), memberId)
        .thenAccept(memberOfPartner -> performOfferOperation(offer,
          memberOfPartner, operation, failResponse)));
  }

  protected void performOfferOperation(
    Offer offer, boolean memberOfPartner, Consumer<Offer> operation,
    Runnable failResponse
  ) {
    if (!offer.offerStatus().isPending()) {
      failResponse.run();
      return;
    }
    if (!memberOfPartner) {
      failResponse.run();
      return;
    }
    operation.accept(offer);
  }
}
