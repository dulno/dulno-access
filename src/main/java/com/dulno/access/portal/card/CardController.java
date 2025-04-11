package com.dulno.access.portal.card;

import com.dulno.access.portal.PortalRestController;
import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.card.logo.CardLogoDatabaseTable;
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
public class CardController extends PortalRestController {
  private final CardDatabaseTable cardDatabaseTable;
  private final CardLogoDatabaseTable cardLogoDatabaseTable;
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;

  protected CardController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    CardDatabaseTable cardDatabaseTable,
    CardLogoDatabaseTable cardLogoDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable);
    this.cardDatabaseTable = cardDatabaseTable;
    this.cardLogoDatabaseTable = cardLogoDatabaseTable;
    this.partnerMemberDatabaseTable = partnerMemberDatabaseTable;
  }

  protected void performCardOperation(
    UUID memberId, UUID cardId, Consumer<Card> operation,
    Runnable failResponse
  ) {
    cardDatabaseTable.cardExists(cardId).thenAccept(exists ->
      performCardOperation(memberId, cardId, exists, operation,
        failResponse));
  }

  private void performCardOperation(
    UUID memberId, UUID cardId, boolean cardExists,
    Consumer<Card> operation, Runnable failResponse
  ) {
    if (!cardExists) {
      failResponse.run();
      return;
    }
    cardDatabaseTable.findCard(cardId).thenAccept(card ->
      checkCardAuthorization(memberId, card).thenAccept(authorized ->
        performCardOperation(card, authorized, operation, failResponse)));
  }

  private void performCardOperation(
    Card card, boolean authorized, Consumer<Card> operation,
    Runnable failResponse
  ) {
    if (!authorized) {
      failResponse.run();
      return;
    }
    operation.accept(card);
  }

  protected CompletableFuture<Boolean> checkCardAuthorization(
    UUID memberId, Card card
  ) {
    return partnerMemberDatabaseTable.isPartnerMember(card.partner(), memberId);
  }
}