package com.dulno.access.user.stamp;

import com.dulno.core.card.Card;
import com.dulno.core.item.ItemDatabaseTable;
import com.dulno.core.partner.analysis.card.PartnerActiveCardDatabaseTable;
import com.dulno.core.partner.analysis.card.PartnerCardGroupDatabaseTable;
import com.dulno.core.partner.analysis.stamp.PartnerOpenStampDatabaseTable;
import com.dulno.core.partner.analysis.user.PartnerActiveUserDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.item.UserItemDatabaseTable;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Configuration
@RequiredArgsConstructor
public class UserStampItemDeletion {
  private final ItemDatabaseTable itemDatabaseTable;
  private final UserItemDatabaseTable userItemDatabaseTable;
  private final PartnerActiveCardDatabaseTable partnerActiveCardDatabaseTable;
  private final PartnerOpenStampDatabaseTable partnerOpenStampDatabaseTable;
  private final PartnerActiveUserDatabaseTable partnerActiveUserDatabaseTable;
  private final PartnerCardGroupDatabaseTable partnerCardGroupDatabaseTable;

  public CompletableFuture<Map<String, Object>> deleteItem(
    User user, Card card, UUID itemId
  ) {
    checkDeleteItemPartnerBalances(card);
    checkDeleteItemPartnerCardGroup(card, itemId);
    return itemDatabaseTable.deleteItem(itemId)
      .thenCompose(value -> user != null ?
        userItemDatabaseTable.deleteUserItem(user.id(), itemId) :
        CompletableFuture.completedFuture(null))
      .thenApply(value -> Map.of("success", true, "action", "REMOVE",
        "itemId", itemId));
  }

  private void checkDeleteItemPartnerBalances(Card card) {
    if (!card.type().isValue() && !card.type().isMember()) {
      return;
    }
    partnerActiveCardDatabaseTable.insertPartnerActiveCard(card.partnerId(),
      -1, System.currentTimeMillis());
    if (!card.type().isValue()) {
      return;
    }
    partnerOpenStampDatabaseTable.insertPartnerOpenStamp(card.partnerId(),
      -1, System.currentTimeMillis());
  }

  private void checkDeleteItemPartnerCardGroup(Card card, UUID itemId) {
    partnerCardGroupDatabaseTable.partnerCardGroupExists(itemId)
      .thenCompose(exists -> exists ?
        partnerCardGroupDatabaseTable.findPartnerCardGroup(itemId)
          .thenCompose(partnerCardGroupDatabaseTable::findPartnerCardGroupItems)
          .thenAccept(groupItems -> checkDeleteItemPartnerCardGroup(card,
            itemId, groupItems)) :
        CompletableFuture.completedFuture(null));
  }

  private void checkDeleteItemPartnerCardGroup(
    Card card, UUID itemId, List<UUID> groupItems
  ) {
    if (groupItems.size() == 1) {
      partnerActiveUserDatabaseTable.insertPartnerActiveUser(card.partnerId(),
        -1, System.currentTimeMillis());
    }
    partnerCardGroupDatabaseTable.deletePartnerCardGroup(itemId);
  }
}
