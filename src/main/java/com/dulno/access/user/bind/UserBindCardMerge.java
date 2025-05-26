package com.dulno.access.user.bind;

import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.item.Item;
import com.dulno.core.item.ItemDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.partner.analysis.card.PartnerCardGroupDatabaseTable;
import com.dulno.core.partner.analysis.user.PartnerActiveUserDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.item.UserItemDatabaseTable;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.context.annotation.Configuration;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Configuration
@RequiredArgsConstructor
public class UserBindCardMerge {
  private final ItemDatabaseTable itemDatabaseTable;
  private final UserItemDatabaseTable userItemDatabaseTable;
  private final CardDatabaseTable cardDatabaseTable;
  private final PartnerCardGroupDatabaseTable partnerCardGroupDatabaseTable;
  private final PartnerActiveUserDatabaseTable partnerActiveUserDatabaseTable;

  public CompletableFuture<Void> mergeUserItems(
    User targetUser, DulnoRequestBody body
  ) {
    return userItemDatabaseTable.findUserItems(targetUser.id())
      .thenCompose(this::findItems)
      .thenCompose(targetCards -> findUnknownUserItems(body)
        .thenCompose(sourceCards -> findMergeCards(sourceCards, targetCards)
          .thenCompose(cards -> processItemMerges(targetUser, sourceCards,
            targetCards, cards))));
  }

  private CompletableFuture<List<Item>> findItems(List<UUID> itemIds) {
    return AsyncIterator.execute(itemIds, itemDatabaseTable::findItem);
  }

  private CompletableFuture<List<Item>> findUnknownUserItems(
    DulnoRequestBody body
  ) {
    if (!body.has("cards")) {
      return CompletableFuture.completedFuture(Lists.newArrayList());
    }
    var itemIds = body.<String>getList("cards")
      .stream().map(UUID::fromString).toList();
    return AsyncIterator.execute(itemIds, this::findUnclaimedItem)
      .thenApply(items -> items.stream().filter(Objects::nonNull).toList());
  }

  private CompletableFuture<Item> findUnclaimedItem(UUID itemId) {
    return itemDatabaseTable.itemExists(itemId)
      .thenCompose(itemExists -> userItemDatabaseTable.userItemExists(itemId)
        .thenCompose(isClaimed -> findUnclaimedItem(itemId, itemExists, isClaimed)));
  }

  private CompletableFuture<Item> findUnclaimedItem(
    UUID itemId, boolean itemExists, boolean isClaimed
  ) {
    if (!itemExists || isClaimed) {
      return CompletableFuture.completedFuture(null);
    }
    return itemDatabaseTable.findItem(itemId);
  }

  private CompletableFuture<List<Card>> findMergeCards(
    List<Item> sourceItems, List<Item> targetItems
  ) {
    var items = Lists.<Item>newArrayList();
    items.addAll(sourceItems);
    items.addAll(targetItems);
    var cardIds = items.stream().map(Item::cardId).distinct().toList();
    return AsyncIterator.execute(cardIds, cardId ->
        cardDatabaseTable.cardExists(cardId)
          .thenCompose(exists -> exists ? cardDatabaseTable.findCard(cardId) :
            CompletableFuture.completedFuture(null)))
      .thenApply(result -> result.stream().filter(Objects::nonNull).toList());
  }

  private CompletableFuture<Void> processItemMerges(
    User targetUser, List<Item> sourceItems, List<Item> targetItems,
    List<Card> cards
  ) {
    processItemMergesActiveUsers(sourceItems, targetItems);
    return AsyncIterator.execute(sourceItems,
        item -> checkItemMergeCompatibility(targetUser, item, targetItems, cards)
          .thenCompose(value -> itemDatabaseTable.deleteItem(item.itemId()))
          .thenCompose(value -> partnerCardGroupDatabaseTable
            .deletePartnerCardGroup(item.itemId())))
      .thenApply(value -> null);
  }

  private void processItemMergesActiveUsers(
    List<Item> sourceItems, List<Item> targetItems
  ) {
    targetItems.stream().map(Item::partnerId)
      .filter(id -> sourceItems.stream().map(Item::partnerId).anyMatch(id::equals))
      .distinct().forEach(partners -> partnerActiveUserDatabaseTable
        .insertPartnerActiveUser(partners, -1, System.currentTimeMillis()));
  }

  private CompletableFuture<Void> checkItemMergeCompatibility(
    User targetUser, Item sourceItem, List<Item> targetItems, List<Card> cards
  ) {
    var sourceCardOptional = cards.stream()
      .filter(card -> card.id().equals(sourceItem.cardId())).findFirst();
    if (sourceCardOptional.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    var sourceCard = sourceCardOptional.get();
    var targetItemOptional = targetItems.stream()
      .filter(entry -> entry.cardId().equals(sourceCard.id())).findFirst();
    return mergeUserItem(targetUser, sourceItem, sourceCard,
      targetItemOptional, targetItems);
  }

  private CompletableFuture<Void> mergeUserItem(
    User targetUser, Item sourceItem, Card sourceCard,
    Optional<Item> targetItemOptional, List<Item> targetItems
  ) {
    if (targetItemOptional.isEmpty() || sourceCard.type().isValue()) {
      return itemDatabaseTable.generateAvailableItemId().thenCompose(itemId ->
        mergeUserNewItem(targetUser, sourceItem, targetItems, itemId));
    } else if (sourceCard.type().isCollection()) {
      return mergeUserCollectionItem(sourceItem, sourceCard, targetItemOptional);
    }
    return CompletableFuture.completedFuture(null);
  }

  private CompletableFuture<Void> mergeUserNewItem(
    User targetUser, Item sourceItem, List<Item> targetItems, UUID newItemId
  ) {
    var partnerItem = targetItems.stream()
      .filter(item -> item.partnerId().equals(sourceItem.partnerId())).findFirst();
    selectPartnerCardGroupId(partnerItem, sourceItem)
      .thenApply(groupId -> groupId != null ? partnerCardGroupDatabaseTable
        .insertPartnerCardGroup(newItemId, groupId, sourceItem.partnerId()) : null);
    return itemDatabaseTable.insertItem(Item.create(newItemId,
        sourceItem.partnerId(), sourceItem.cardId(), sourceItem.content(),
        sourceItem.lastUpdate()))
      .thenAccept(value -> userItemDatabaseTable.insertUserItem(
        targetUser.id(), newItemId));
  }

  private CompletableFuture<UUID> selectPartnerCardGroupId(
    Optional<Item> partnerItem, Item sourceItem
  ) {
    if (partnerItem.isEmpty()) {
      return findPartnerCardGroupIdSecure(sourceItem.itemId());
    }
    return findPartnerCardGroupIdSecure(partnerItem.get().itemId());
  }

  private CompletableFuture<UUID> findPartnerCardGroupIdSecure(UUID itemId) {
    return partnerCardGroupDatabaseTable.partnerCardGroupExists(itemId)
      .thenCompose(exists -> exists ?
        partnerCardGroupDatabaseTable.findPartnerCardGroup(itemId) :
        CompletableFuture.completedFuture(null));
  }

  private CompletableFuture<Void> mergeUserCollectionItem(
    Item sourceItem, Card sourceCard, Optional<Item> targetItemOptional
  ) {
    var targetItem = targetItemOptional.get();
    var newCardContent = Map.of("stamps", Math.min(
      new JSONObject(sourceCard.content()).getInt("reward_number"),
      new JSONObject(sourceItem.content()).getInt("stamps") +
        new JSONObject(targetItem.content()).getInt("stamps")));
    return itemDatabaseTable.updateItem(Item.create(targetItem.itemId(),
      targetItem.partnerId(), targetItem.cardId(),
      new JSONObject(newCardContent).toString(), targetItem.lastUpdate()));
  }
}
