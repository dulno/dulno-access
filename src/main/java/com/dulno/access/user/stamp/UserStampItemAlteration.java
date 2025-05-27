package com.dulno.access.user.stamp;

import com.dulno.access.user.card.UserCardInformationController;
import com.dulno.core.card.Card;
import com.dulno.core.item.Item;
import com.dulno.core.item.ItemDatabaseTable;
import com.dulno.core.partner.analysis.card.PartnerCardState;
import com.dulno.core.partner.analysis.card.PartnerCardUsageDatabaseTable;
import com.dulno.core.partner.analysis.card.PartnerNewCardDatabaseTable;
import com.dulno.core.partner.analysis.stamp.PartnerOpenStampDatabaseTable;
import com.dulno.core.stamp.Stamp;
import com.dulno.core.user.User;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.context.annotation.Configuration;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Configuration
@RequiredArgsConstructor
public class UserStampItemAlteration {
  private final ItemDatabaseTable itemDatabaseTable;
  private final PartnerNewCardDatabaseTable partnerNewCardDatabaseTable;
  private final PartnerCardUsageDatabaseTable partnerCardUsageDatabaseTable;
  private final PartnerOpenStampDatabaseTable partnerOpenStampDatabaseTable;
  private final UserCardInformationController itemController;
  private final UserStampItemDeletion itemDeletion;

  public CompletableFuture<Map<String, Object>> alterItem(
    User user, Card card, Stamp stamp, List<Item> cardItems
  ) {
    if (stamp.assignmentRole().isMemberRevoke()) {
      return itemDeletion.deleteItem(user, card, cardItems.get(0).itemId());
    }
    var item = findEmptiesItem(cardItems);
    return updateItemWithStamp(user, card, stamp, item);
  }

  private Item findEmptiesItem(List<Item> cardItems) {
    return cardItems.stream()
      .min(Comparator.comparingLong(entry ->
        new JSONObject(entry.content()).getInt("stamps")))
      .get();
  }

  private CompletableFuture<Map<String, Object>> updateItemWithStamp(
    User user, Card card, Stamp stamp, Item item
  ) {
    var itemContent = new JSONObject(item.content());
    if (stamp.assignmentRole().isValueDebit() && itemContent.getInt("stamps") == 1) {
      switchPartnerCardState(card, PartnerCardState.USED, PartnerCardState.USED_UP);
      return itemDeletion.deleteItem(user, card, item.itemId());
    }
    item.changeContent(updateItemContent(stamp, card, itemContent));
    item.changeLastUpdate(System.currentTimeMillis());
    var futureInformation = itemDatabaseTable.updateItem(item)
      .thenCompose(value -> itemController.collectItemInformation(item));
    futureInformation.thenAccept(information ->
      information.putAll(Map.of("success", true, "action", "UPDATE")));
    return futureInformation;
  }

  private String updateItemContent(
    Stamp stamp, Card card, JSONObject itemContent
  ) {
    var cardContent = new JSONObject(card.content());
    if (stamp.assignmentRole().isCollectionReward()) {
      return updateCollectionItemContent(card, itemContent, cardContent);
    } else if (stamp.assignmentRole().isValueDebit()) {
      return updateValueItemContent(card, itemContent, cardContent);
    }
    return itemContent.toString();
  }

  private String updateCollectionItemContent(
    Card card, JSONObject itemContent, JSONObject cardContent
  ) {
    var stamps = itemContent.getInt("stamps");
    var isFull = stamps >= cardContent.getInt("reward_number");
    if (stamps == 1) {
      switchPartnerCardState(card, PartnerCardState.UNUSED, PartnerCardState.USED);
    } else if (isFull) {
      switchPartnerCardState(card, PartnerCardState.USED, PartnerCardState.USED_UP);
      partnerCardUsageDatabaseTable.addPartnerCardUsage(card.partnerId(),
        PartnerCardState.UNUSED);
      partnerNewCardDatabaseTable.insertPartnerNewCard(card.partnerId(),
        System.currentTimeMillis());
    }
    itemContent.put("stamps", isFull ? 0 : stamps + 1);
    return itemContent.toString();
  }

  private String updateValueItemContent(
    Card card, JSONObject itemContent, JSONObject cardContent
  ) {
    partnerOpenStampDatabaseTable.insertPartnerOpenStamp(card.partnerId(),
      -1, System.currentTimeMillis());
    var stamps = itemContent.getInt("stamps");
    if (stamps == cardContent.getInt("value_number")) {
      switchPartnerCardState(card, PartnerCardState.UNUSED, PartnerCardState.USED);
    }
    itemContent.put("stamps", stamps - 1);
    return itemContent.toString();
  }

  private void switchPartnerCardState(
    Card card, PartnerCardState oldState, PartnerCardState newState
  ) {
    partnerCardUsageDatabaseTable.removePartnerCardUsage(card.partnerId(),
      oldState);
    partnerCardUsageDatabaseTable.addPartnerCardUsage(card.partnerId(),
      newState);
  }
}
