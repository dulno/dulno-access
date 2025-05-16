package com.dulno.access.user.card;

import com.dulno.access.user.UserController;
import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.item.Item;
import com.dulno.core.item.ItemDatabaseTable;
import com.dulno.core.partner.analysis.card.PartnerActiveCardDatabaseTable;
import com.dulno.core.partner.analysis.card.PartnerCardGroupDatabaseTable;
import com.dulno.core.partner.analysis.card.PartnerCardState;
import com.dulno.core.partner.analysis.card.PartnerCardUsageDatabaseTable;
import com.dulno.core.partner.analysis.stamp.PartnerOpenStampDatabaseTable;
import com.dulno.core.partner.analysis.user.PartnerActiveUserDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.item.UserItemDatabaseTable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserCardDeletionController extends UserController {
  private final UserItemDatabaseTable userItemDatabaseTable;
  private final ItemDatabaseTable itemDatabaseTable;
  private final CardDatabaseTable cardDatabaseTable;
  private final PartnerActiveCardDatabaseTable partnerActiveCardDatabaseTable;
  private final PartnerOpenStampDatabaseTable partnerOpenStampDatabaseTable;
  private final PartnerActiveUserDatabaseTable partnerActiveUserDatabaseTable;
  private final PartnerCardGroupDatabaseTable partnerCardGroupDatabaseTable;
  private final PartnerCardUsageDatabaseTable partnerCardUsageDatabaseTable;

  private UserCardDeletionController(
    UserDatabaseTable userDatabaseTable, Hashing hashing,
    DulnoEnvironment environment, ErrorRepository errorRepository,
    UserItemDatabaseTable userItemDatabaseTable,
    ItemDatabaseTable itemDatabaseTable,
    CardDatabaseTable cardDatabaseTable,
    PartnerActiveCardDatabaseTable partnerActiveCardDatabaseTable,
    PartnerOpenStampDatabaseTable partnerOpenStampDatabaseTable,
    PartnerActiveUserDatabaseTable partnerActiveUserDatabaseTable,
    PartnerCardGroupDatabaseTable partnerCardGroupDatabaseTable,
    PartnerCardUsageDatabaseTable partnerCardUsageDatabaseTable
  ) {
    super(userDatabaseTable, hashing, environment, errorRepository);
    this.userItemDatabaseTable = userItemDatabaseTable;
    this.itemDatabaseTable = itemDatabaseTable;
    this.cardDatabaseTable = cardDatabaseTable;
    this.partnerActiveCardDatabaseTable = partnerActiveCardDatabaseTable;
    this.partnerOpenStampDatabaseTable = partnerOpenStampDatabaseTable;
    this.partnerActiveUserDatabaseTable = partnerActiveUserDatabaseTable;
    this.partnerCardGroupDatabaseTable = partnerCardGroupDatabaseTable;
    this.partnerCardUsageDatabaseTable = partnerCardUsageDatabaseTable;
  }

  @RequestMapping(path = "/user/card/delete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> deleteCard(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var itemId = body.getUUID("item");
    return findUser(request).exceptionally(throwable -> null)
      .thenCompose(user -> itemDatabaseTable.itemExists(itemId)
        .thenCompose(itemExists -> userItemDatabaseTable.userItemExists(itemId)
          .thenCompose(userItemExists -> checkCardDeleteExistence(itemId, user,
            itemExists, userItemExists))));
  }

  private CompletableFuture<Map<String, Object>> checkCardDeleteExistence(
    UUID itemId, User user, boolean itemExists, boolean userItemExists
  ) {
    if (!itemExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    if (user == null && userItemExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
    return itemDatabaseTable.findItem(itemId)
      .thenCompose(item -> checkUnclaimedCardDeletion(item, user, userItemExists));
  }

  private CompletableFuture<Map<String, Object>> checkUnclaimedCardDeletion(
    Item item, User user, boolean userItemExists
  ) {
    if (user == null && !userItemExists) {
      return performCardDeletion(item).thenApply(value -> Map.of("success", true));
    }
    return userItemDatabaseTable.findItemUser(item.itemId())
      .thenCompose(owner -> checkCardDeletionOwner(item, user, owner));
  }

  private CompletableFuture<Map<String, Object>> checkCardDeletionOwner(
    Item item, User user, UUID cardOwner
  ) {
    if (!user.id().equals(cardOwner)) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1002));
    }
    return userItemDatabaseTable.deleteUserItem(user.id(), item.itemId())
      .thenCompose(value -> performCardDeletion(item))
      .thenApply(value -> Map.of("success", true));
  }

  private CompletableFuture<Void> performCardDeletion(Item item) {
    return cardDatabaseTable.findCard(item.cardId())
      .thenCompose(card -> performCardDeletion(item, card));
  }

  private CompletableFuture<Void> performCardDeletion(Item item, Card card) {
    checkCardDeletionPartnerAnalysis(item, card);
    checkCardDeletionPartnerCardGroup(item, card);
    return itemDatabaseTable.deleteItem(item.itemId()).thenApply(value -> null);
  }

  private void checkCardDeletionPartnerAnalysis(Item item, Card card) {
    if (card.type().isValue() || card.type().isMember()) {
      partnerActiveCardDatabaseTable
        .insertPartnerActiveCard(card.partnerId(), -1, System.currentTimeMillis());
    }
    var itemContent = new JSONObject(item.content());
    var cardContent = new JSONObject(card.content());
    if (card.type().isValue()) {
      var stamps = itemContent.getInt("stamps");
      partnerOpenStampDatabaseTable.insertPartnerOpenStamp(
        card.partnerId(), -stamps, System.currentTimeMillis());
      partnerCardUsageDatabaseTable.removePartnerCardUsage(
        card.partnerId(), stamps == cardContent.getInt("value_number") ?
          PartnerCardState.UNUSED : PartnerCardState.USED);
    }
    if (card.type().isCollection()) {
      partnerCardUsageDatabaseTable.removePartnerCardUsage(
        card.partnerId(), itemContent.getInt("stamps") == 1 ?
          PartnerCardState.UNUSED : PartnerCardState.USED);
    }
  }

  private void checkCardDeletionPartnerCardGroup(Item item, Card card) {
    partnerCardGroupDatabaseTable.partnerCardGroupExists(item.itemId())
      .thenCompose(exists -> exists ?
        partnerCardGroupDatabaseTable.findPartnerCardGroup(item.itemId())
          .thenCompose(partnerCardGroupDatabaseTable::findPartnerCardGroupItems)
          .thenAccept(groupItems -> checkCardDeletionPartnerCardGroup(item,
            card, groupItems)) :
        CompletableFuture.completedFuture(null));
  }

  private void checkCardDeletionPartnerCardGroup(
    Item item, Card card, List<UUID> groupItems
  ) {
    if (groupItems.size() == 1) {
      partnerActiveUserDatabaseTable.insertPartnerActiveUser(card.partnerId(),
        -1, System.currentTimeMillis());
    }
    partnerCardGroupDatabaseTable.deletePartnerCardGroup(item.itemId());
  }
}
