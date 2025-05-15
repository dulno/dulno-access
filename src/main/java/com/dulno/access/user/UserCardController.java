package com.dulno.access.user;

import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.card.icon.CardMemberIcon;
import com.dulno.core.card.icon.CardStampIcon;
import com.dulno.core.card.logo.CardLogoDatabaseTable;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.item.Item;
import com.dulno.core.item.ItemDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.partner.Partner;
import com.dulno.core.partner.PartnerDatabaseTable;
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
import com.google.api.client.util.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserCardController extends UserController {
  private final UserItemDatabaseTable userItemDatabaseTable;
  private final ItemDatabaseTable itemDatabaseTable;
  private final CardDatabaseTable cardDatabaseTable;
  private final CardLogoDatabaseTable cardLogoDatabaseTable;
  private final PartnerDatabaseTable partnerDatabaseTable;
  private final PartnerActiveCardDatabaseTable partnerActiveCardDatabaseTable;
  private final PartnerOpenStampDatabaseTable partnerOpenStampDatabaseTable;
  private final PartnerActiveUserDatabaseTable partnerActiveUserDatabaseTable;
  private final PartnerCardGroupDatabaseTable partnerCardGroupDatabaseTable;
  private final PartnerCardUsageDatabaseTable partnerCardUsageDatabaseTable;

  private UserCardController(
    UserDatabaseTable userDatabaseTable, Hashing hashing,
    DulnoEnvironment environment, ErrorRepository errorRepository,
    UserItemDatabaseTable userItemDatabaseTable,
    ItemDatabaseTable itemDatabaseTable,
    CardDatabaseTable cardDatabaseTable,
    CardLogoDatabaseTable cardLogoDatabaseTable,
    PartnerDatabaseTable partnerDatabaseTable,
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
    this.cardLogoDatabaseTable = cardLogoDatabaseTable;
    this.partnerDatabaseTable = partnerDatabaseTable;
    this.partnerActiveCardDatabaseTable = partnerActiveCardDatabaseTable;
    this.partnerOpenStampDatabaseTable = partnerOpenStampDatabaseTable;
    this.partnerActiveUserDatabaseTable = partnerActiveUserDatabaseTable;
    this.partnerCardGroupDatabaseTable = partnerCardGroupDatabaseTable;
    this.partnerCardUsageDatabaseTable = partnerCardUsageDatabaseTable;
  }

  @RequestMapping(path = "/user/cards/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findUserCards(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    return findUser(request).exceptionally(throwable -> null)
      .thenCompose(user -> findUserCards(body, user));
  }

  private CompletableFuture<Map<String, Object>> findUserCards(
    DulnoRequestBody body, User user
  ) {
    if (user == null) {
      return findUnknownUserCards(body);
    }
    return userItemDatabaseTable.findUserItems(user.id())
      .thenCompose(items -> AsyncIterator.execute(items,
        itemDatabaseTable::findItem))
      .thenCompose(this::collectItemsInformation);
  }

  private CompletableFuture<Map<String, Object>> findUnknownUserCards(
    DulnoRequestBody body
  ) {
    if (!body.has("cards")) {
      return CompletableFuture.completedFuture(Map.of("items",
        Lists.newArrayList()));
    }
    var itemIds = body.<String>getList("cards")
      .stream().map(UUID::fromString).toList();
    return AsyncIterator.execute(itemIds, this::findUnclaimedItem)
      .thenApply(items -> items.stream().filter(Objects::nonNull).toList())
      .thenCompose(this::collectItemsInformation);
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

  private CompletableFuture<Map<String, Object>> collectItemsInformation(
    List<Item> items
  ) {
    return AsyncIterator.execute(items, this::collectItemInformation)
      .thenApply(result -> result.stream().filter(Objects::nonNull).toList())
      .thenApply(result -> Map.of("items", result));
  }

  public CompletableFuture<Map<String, Object>> collectItemInformation(Item item) {
    return cardDatabaseTable.cardExists(item.cardId())
      .thenCompose(cardExists -> collectItemInformation(item, cardExists));
  }

  private CompletableFuture<Map<String, Object>> collectItemInformation(
    Item item, boolean cardExists
  ) {
    if (!cardExists) {
      return CompletableFuture.completedFuture(null);
    }
    return cardDatabaseTable.findCard(item.cardId())
      .thenCompose(card -> partnerDatabaseTable.findPartner(card.partnerId())
        .thenCompose(partner -> cardLogoDatabaseTable.findCardLogoId(card.id())
          .thenApply(logoId -> assemblyItemInformation(item, card, partner, logoId))));
  }

  private Map<String, Object> assemblyItemInformation(
    Item item, Card card, Partner partner, UUID logoId
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("itemId", item.itemId());
    information.put("cardId", card.id());
    information.put("cardBackgroundColor", card.backgroundColor());
    information.put("cardForegroundColor", card.foregroundColor());
    information.put("cardType", card.type());
    information.put("lastUpdate", item.lastUpdate());
    information.put("logoId", logoId);
    information.put("partnerId", partner.id());
    information.put("partnerName", partner.name());
    information.put("partnerDescription", partner.description());
    var itemContent = new JSONObject(item.content());
    var cardContent = new JSONObject(card.content());
    if (card.type().isCollection()) {
      information.putAll(assemblyCollectionCardInformation(itemContent,
        cardContent));
    } else if (card.type().isValue()) {
      information.putAll(assemblyValueCardInformation(itemContent,
        cardContent));
    } else if (card.type().isMember()) {
      information.putAll(assemblyMemberCardInformation(cardContent));
    }
    return information;
  }

  private Map<String, Object> assemblyCollectionCardInformation(
    JSONObject itemContent, JSONObject cardContent
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("stampIcon",
      CardStampIcon.valueOf(cardContent.getString("stamp_icon")).identifier());
    information.put("rewardNumber", cardContent.getInt("reward_number"));
    information.put("rewardDescription",
      cardContent.getString("reward_description"));
    information.put("stamps", itemContent.getInt("stamps"));
    return information;
  }

  private Map<String, Object> assemblyValueCardInformation(
    JSONObject itemContent, JSONObject cardContent
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("stampIcon",
      CardStampIcon.valueOf(cardContent.getString("stamp_icon")).identifier());
    information.put("valueNumber", cardContent.getInt("value_number"));
    information.put("valueDescription", cardContent.getString("value_description"));
    information.put("stamps", itemContent.getInt("stamps"));
    return information;
  }

  private Map<String, Object> assemblyMemberCardInformation(
    JSONObject cardContent
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("memberIcon",
      CardMemberIcon.valueOf(cardContent.getString("member_icon")).identifier());
    information.put("memberDescription",
      cardContent.getString("member_description"));
    return information;
  }

  @RequestMapping(path = "/user/card/logo/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findCardLogo(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var cardId = body.getUUID("card");
    return cardDatabaseTable.cardExists(cardId)
      .thenCompose(exists -> findCardLogo(cardId, exists));
  }

  private CompletableFuture<Map<String, Object>> findCardLogo(
    UUID cardId, boolean cardExists
  ) {
    if (!cardExists) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return cardLogoDatabaseTable.findCardLogo(cardId)
      .thenApply(logo -> Map.of("logoId", logo.logoId(), "logo",
        Base64.getEncoder().encodeToString(logo.content())));
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
