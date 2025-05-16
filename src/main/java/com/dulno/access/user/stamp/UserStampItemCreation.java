package com.dulno.access.user.stamp;

import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.card.CardState;
import com.dulno.core.item.Item;
import com.dulno.core.item.ItemDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.partner.analysis.card.*;
import com.dulno.core.partner.analysis.stamp.PartnerOpenStampDatabaseTable;
import com.dulno.core.partner.analysis.user.PartnerActiveUserDatabaseTable;
import com.dulno.core.partner.analysis.user.PartnerNewUserDatabaseTable;
import com.dulno.core.partner.analysis.user.PartnerUserLanguageDatabaseTable;
import com.dulno.core.partner.transaction.PartnerTransaction;
import com.dulno.core.partner.transaction.PartnerTransactionDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.item.UserItemDatabaseTable;
import com.dulno.core.user.partner.UserPartnerDatabaseTable;
import com.google.common.collect.Maps;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Configuration
@RequiredArgsConstructor
public class UserStampItemCreation {
  private final ItemDatabaseTable itemDatabaseTable;
  private final UserItemDatabaseTable userItemDatabaseTable;
  private final CardDatabaseTable cardDatabaseTable;
  private final PartnerNewUserDatabaseTable partnerNewUserDatabaseTable;
  private final PartnerNewCardDatabaseTable partnerNewCardDatabaseTable;
  private final PartnerCardUsageDatabaseTable partnerCardUsageDatabaseTable;
  private final PartnerUserLanguageDatabaseTable partnerUserLanguageDatabaseTable;
  private final PartnerTransactionDatabaseTable partnerTransactionDatabaseTable;
  private final PartnerActiveCardDatabaseTable partnerActiveCardDatabaseTable;
  private final PartnerOpenStampDatabaseTable partnerOpenStampDatabaseTable;
  private final PartnerActiveUserDatabaseTable partnerActiveUserDatabaseTable;
  private final PartnerCardGroupDatabaseTable partnerCardGroupDatabaseTable;
  private final UserPartnerDatabaseTable userPartnerDatabaseTable;

  public CompletableFuture<Item> createNewItem(
    User user, Card card, List<Item> allItems, boolean isNewPartnerUser,
    String language
  ) {
    return itemDatabaseTable.generateAvailableItemId()
      .thenCompose(itemId -> createNewItem(user, card, allItems, itemId,
        isNewPartnerUser, language));
  }

  private CompletableFuture<Item> createNewItem(
    User user, Card card, List<Item> allItems, UUID itemId,
    boolean isNewPartnerUser, String language
  ) {
    if (card.state().isUnused()) {
      card.changeState(CardState.USED);
      cardDatabaseTable.updateCard(card);
    }
    checkNewItemPartnerAnalytics(user, card, isNewPartnerUser, language);
    checkNewItemPartnerBalances(card, itemId);
    checkNewItemPartnerCardGroup(card, allItems, itemId);
    var item = Item.create(itemId, card.partnerId(), card.id(),
      new JSONObject(createNewItemContent(card)).toString(),
      System.currentTimeMillis());
    return itemDatabaseTable.insertItem(item)
      .thenCompose(value -> user != null ?
        userItemDatabaseTable.insertUserItem(user.id(), item.itemId()) :
        CompletableFuture.completedFuture(null))
      .thenApply(value -> item);
  }

  private Map<String, Object> createNewItemContent(Card card) {
    if (card.type().isCollection()) {
      return Map.of("stamps", 1);
    } else if (card.type().isValue()) {
      return Map.of("stamps", new JSONObject(card.content()).getInt("value_number"));
    } else if (card.type().isMember()) {
      return Maps.newHashMap();
    }
    return Maps.newHashMap();
  }

  private void checkNewItemPartnerAnalytics(
    User user, Card card, boolean isNewPartnerUser, String language
  ) {
    partnerNewCardDatabaseTable.insertPartnerNewCard(card.partnerId(),
      System.currentTimeMillis());
    if (card.type().isCollection() || card.type().isValue()) {
      partnerCardUsageDatabaseTable.addPartnerCardUsage(card.partnerId(),
        PartnerCardState.UNUSED);
    }
    if (!isNewPartnerUser) {
      return;
    }
    partnerNewUserDatabaseTable.insertPartnerNewUser(card.partnerId(),
      System.currentTimeMillis());
    partnerUserLanguageDatabaseTable.addPartnerUserLanguage(card.partnerId(),
      language);
    if (user != null) {
      userPartnerDatabaseTable.insertUserPartner(user.id(), card.partnerId());
    }
  }

  private void checkNewItemPartnerBalances(Card card, UUID itemId) {
    if (!card.type().isValue() && !card.type().isMember()) {
      return;
    }
    var content = new JSONObject(card.content());
    var value = card.type().isValue() ? content.getDouble("value_price") :
      content.getDouble("member_price");
    partnerTransactionDatabaseTable
      .generateAvailablePartnerTransactionId(card.partnerId())
      .thenAccept(id -> partnerTransactionDatabaseTable
        .insertPartnerTransaction(PartnerTransaction.create(card.partnerId(),
          id, card.id(), itemId, value, System.currentTimeMillis())));
    partnerActiveCardDatabaseTable.insertPartnerActiveCard(card.partnerId(), 1,
      System.currentTimeMillis());
    if (!card.type().isValue()) {
      return;
    }
    partnerOpenStampDatabaseTable.insertPartnerOpenStamp(card.partnerId(),
      content.getInt("value_number"), System.currentTimeMillis());
  }

  private void checkNewItemPartnerCardGroup(
    Card card, List<Item> allItems, UUID itemId
  ) {
    var partnerItems = allItems.stream()
      .filter(item -> item.partnerId().equals(card.partnerId())).toList();
    findPartnerCardGroupId(card, partnerItems)
      .thenAccept(groupId -> partnerCardGroupDatabaseTable
        .insertPartnerCardGroup(itemId, groupId, card.partnerId()));
  }

  private CompletableFuture<UUID> findPartnerCardGroupId(
    Card card, List<Item> partnerItems
  ) {
    if (partnerItems.isEmpty()) {
      return createNewPartnerCardGroup(card);
    }
    var itemId = partnerItems.get(0).itemId();
    return partnerCardGroupDatabaseTable.partnerCardGroupExists(itemId)
      .thenCompose(exists -> exists ?
        partnerCardGroupDatabaseTable.findPartnerCardGroup(itemId) :
        migratePartnerCardGroup(card, partnerItems));
  }

  private CompletableFuture<UUID> migratePartnerCardGroup(
    Card card, List<Item> partnerItems
  ) {
    return createNewPartnerCardGroup(card)
      .thenCompose(groupId -> AsyncIterator.execute(partnerItems,
          item -> partnerCardGroupDatabaseTable
            .insertPartnerCardGroup(item.itemId(), groupId, card.partnerId()))
        .thenApply(value -> groupId));
  }

  private CompletableFuture<UUID> createNewPartnerCardGroup(Card card) {
    partnerActiveUserDatabaseTable.insertPartnerActiveUser(card.partnerId(),
      1, System.currentTimeMillis());
    return partnerCardGroupDatabaseTable.generateAvailablePartnerCardGroupId();
  }
}
