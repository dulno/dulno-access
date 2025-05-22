package com.dulno.access.user.card;

import com.dulno.access.user.UserController;
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
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.item.UserItemDatabaseTable;
import com.google.api.client.util.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserCardInformationController extends UserController {
  private final UserItemDatabaseTable userItemDatabaseTable;
  private final ItemDatabaseTable itemDatabaseTable;
  private final CardDatabaseTable cardDatabaseTable;
  private final CardLogoDatabaseTable cardLogoDatabaseTable;
  private final PartnerDatabaseTable partnerDatabaseTable;

  private UserCardInformationController(
    @Qualifier("appKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    UserItemDatabaseTable userItemDatabaseTable,
    ItemDatabaseTable itemDatabaseTable,
    CardDatabaseTable cardDatabaseTable,
    CardLogoDatabaseTable cardLogoDatabaseTable,
    PartnerDatabaseTable partnerDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.userItemDatabaseTable = userItemDatabaseTable;
    this.itemDatabaseTable = itemDatabaseTable;
    this.cardDatabaseTable = cardDatabaseTable;
    this.cardLogoDatabaseTable = cardLogoDatabaseTable;
    this.partnerDatabaseTable = partnerDatabaseTable;
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
}
