package com.dulno.access.user;

import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.item.Item;
import com.dulno.core.item.ItemDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.ntag.NTAG;
import com.dulno.core.ntag.cmac.CMACType;
import com.dulno.core.partner.analysis.*;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.scan.ScanDatabaseTable;
import com.dulno.core.stamp.Stamp;
import com.dulno.core.stamp.StampDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.item.UserItemDatabaseTable;
import com.dulno.core.user.scan.UserScanDatabaseTable;
import com.google.common.collect.Lists;
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
public final class UserStampController extends UserController {
  private final ItemDatabaseTable itemDatabaseTable;
  private final UserItemDatabaseTable userItemDatabaseTable;
  private final StampDatabaseTable stampDatabaseTable;
  private final CardDatabaseTable cardDatabaseTable;
  private final ScanDatabaseTable scanDatabaseTable;
  private final PartnerScanDatabaseTable partnerScanDatabaseTable;
  private final PartnerNewUserDatabaseTable partnerNewUserDatabaseTable;
  private final PartnerNewCardDatabaseTable partnerNewCardDatabaseTable;
  private final PartnerCardUsageDatabaseTable partnerCardUsageDatabaseTable;
  private final PartnerUserLanguageDatabaseTable partnerUserLanguageDatabaseTable;
  private final UserScanDatabaseTable userScanDatabaseTable;
  private final UserCardController itemController;

  private UserStampController(
    UserDatabaseTable userDatabaseTable, Hashing hashing,
    DulnoEnvironment environment, ErrorRepository errorRepository,
    ItemDatabaseTable itemDatabaseTable,
    UserItemDatabaseTable userItemDatabaseTable,
    StampDatabaseTable stampDatabaseTable, CardDatabaseTable cardDatabaseTable,
    ScanDatabaseTable scanDatabaseTable,
    PartnerScanDatabaseTable partnerScanDatabaseTable,
    PartnerNewUserDatabaseTable partnerNewUserDatabaseTable,
    PartnerNewCardDatabaseTable partnerNewCardDatabaseTable,
    PartnerCardUsageDatabaseTable partnerCardUsageDatabaseTable,
    PartnerUserLanguageDatabaseTable partnerUserLanguageDatabaseTable,
    UserScanDatabaseTable userScanDatabaseTable,
    UserCardController itemController
  ) {
    super(userDatabaseTable, hashing, environment, errorRepository);
    this.itemDatabaseTable = itemDatabaseTable;
    this.userItemDatabaseTable = userItemDatabaseTable;
    this.stampDatabaseTable = stampDatabaseTable;
    this.cardDatabaseTable = cardDatabaseTable;
    this.scanDatabaseTable = scanDatabaseTable;
    this.partnerScanDatabaseTable = partnerScanDatabaseTable;
    this.partnerNewUserDatabaseTable = partnerNewUserDatabaseTable;
    this.partnerNewCardDatabaseTable = partnerNewCardDatabaseTable;
    this.partnerCardUsageDatabaseTable = partnerCardUsageDatabaseTable;
    this.partnerUserLanguageDatabaseTable = partnerUserLanguageDatabaseTable;
    this.userScanDatabaseTable = userScanDatabaseTable;
    this.itemController = itemController;
  }

  @RequestMapping(path = "/user/stamp/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> requestUserStamp(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var stampId = body.getUUID("stamp");
    return stampDatabaseTable.stampExists(stampId)
      .thenCompose(exists -> checkStampExistence(request, body, stampId,
        body.getString("picc"), body.getString("cmac"), exists));
  }

  private CompletableFuture<Map<String, Object>> checkStampExistence(
    HttpServletRequest request, DulnoRequestBody body, UUID stampId, String picc,
    String cmac, boolean stampExists
  ) {
    if (!stampExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    return stampDatabaseTable.findStamp(stampId)
      .thenCompose(stamp -> validateStampScan(request, body, stamp, picc, cmac));
  }

  private CompletableFuture<Map<String, Object>> validateStampScan(
    HttpServletRequest request, DulnoRequestBody body, Stamp stamp, String picc,
    String cmac
  ) {
    if (stamp.assignmentRole().isUnassigned() || stamp.state().isDeactivated()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
    try {
      var ntag = NTAG.of(stamp.masterKey(), picc, cmac, CMACType.LRP);
      if (!ntag.validate()) {
        return CompletableFuture.completedFuture(Map.of("success", false,
          "error", 1002));
      }
      return scanDatabaseTable.scanExists(picc, cmac).thenCompose(exists ->
        checkScanExistence(request, body, stamp, picc, cmac, exists));
    } catch (Exception exception) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1003));
    }
  }

  private CompletableFuture<Map<String, Object>> checkScanExistence(
    HttpServletRequest request, DulnoRequestBody body, Stamp stamp, String picc,
    String cmac, boolean scanExists
  ) {
    if (scanExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1004));
    }
    if (request.getHeader("User") == null) {
      return checkCardExistence(body, null, stamp, picc, cmac);
    }
    return findUser(request)
      .thenCompose(user -> checkCardExistence(body, user, stamp, picc, cmac)
      .exceptionally(this::processError));
  }

  private CompletableFuture<Map<String, Object>> checkCardExistence(
    DulnoRequestBody body, User user, Stamp stamp, String picc, String cmac
  ) {
    return cardDatabaseTable.cardExists(stamp.assignedCard())
      .thenCompose(exists -> processItemStamp(body, user, stamp, picc,
        cmac, exists));
  }

  private CompletableFuture<Map<String, Object>> processItemStamp(
    DulnoRequestBody body, User user, Stamp stamp, String picc, String cmac,
    boolean cardExists
  ) {
    if (!cardExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1005));
    }
    return cardDatabaseTable.findCard(stamp.assignedCard())
      .thenCompose(card -> findUserItems(user, body)
        .thenCompose(items -> processUserInventory(body, user, card, stamp, items)
          .thenCompose(itemResult -> storeScan(user, stamp, picc, cmac, card,
            itemResult))));
  }

  private CompletableFuture<List<Item>> findUserItems(
    User user, DulnoRequestBody body
  ) {
    if (user == null) {
      var itemIds = body.<String>getList("cards").stream()
        .map(UUID::fromString).toList();
      return AsyncIterator.execute(itemIds, this::findUnclaimedItem);
    }
    return userItemDatabaseTable.findUserItems(user.id()).thenCompose(items ->
      AsyncIterator.execute(items, itemDatabaseTable::findItem));
  }

  private String findUserLanguage(User user, DulnoRequestBody body) {
    if (user == null) {
      return body.getString("language");
    }
    return user.language();
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

  private CompletableFuture<Map<String, Object>> processUserInventory(
    DulnoRequestBody body, User user, Card card, Stamp stamp, List<Item> allItems
  ) {
    var cardItems = allItems.stream().filter(Objects::nonNull)
      .filter(item -> item.cardId().equals(card.id())).toList();
    var language = findUserLanguage(user, body);
    return checkItemConstellations(user, card, stamp, allItems, cardItems,
      language);
  }

  private CompletableFuture<Map<String, Object>> checkItemConstellations(
    User user, Card card, Stamp stamp, List<Item> allItems, List<Item> cardItems,
    String language
  ) {
    if (cardItems.isEmpty() && stamp.assignmentRole().isValueDebit()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1006));
    }
    if (cardItems.isEmpty() && stamp.assignmentRole().isMemberRevoke()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1007));
    }
    if (!cardItems.isEmpty() && stamp.assignmentRole().isMemberIssue()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1008));
    }
    return storeItemStamp(user, card, stamp, allItems, cardItems, language);
  }

  private CompletableFuture<Map<String, Object>> storeItemStamp(
    User user, Card card, Stamp stamp, List<Item> allItems, List<Item> cardItems,
    String language
  ) {
    if (cardItems.isEmpty() || stamp.assignmentRole().isValueCredit()) {
      var futureInformation = createNewItem(user, card, allItems, language)
        .thenCompose(itemController::collectItemInformation);
      futureInformation.thenAccept(information ->
        information.putAll(Map.of("success", true, "action", "CREATE")));
      return futureInformation;
    }
    return alterItem(user, card, stamp, cardItems);
  }

  private CompletableFuture<Item> createNewItem(
    User user, Card card, List<Item> allItems, String language
  ) {
    checkPartnerAnalytics(card, allItems, language);
    return itemDatabaseTable.generateAvailableItemId()
      .thenApply(itemId -> Item.create(itemId, card.partnerId(), card.id(),
        new JSONObject(createNewItemContent(card)).toString(),
        System.currentTimeMillis()))
      .thenCompose(item -> itemDatabaseTable.insertItem(item)
        .thenCompose(value -> user != null ?
          userItemDatabaseTable.insertUserItem(user.id(), item.itemId()) :
          CompletableFuture.completedFuture(null))
        .thenApply(value -> item));
  }

  private void checkPartnerAnalytics(
    Card card, List<Item> allItems, String language
  ) {
    partnerNewCardDatabaseTable.insertPartnerNewCard(card.partnerId(),
      System.currentTimeMillis());
    if (card.type().isCollection() || card.type().isValue()) {
      partnerCardUsageDatabaseTable.addPartnerCardUsage(card.partnerId(),
        PartnerCardState.UNUSED);
    }
    var isNewUser = allItems.stream()
      .noneMatch(item -> item.partnerId().equals(card.partnerId()));
    if (!isNewUser) {
      return;
    }
    partnerNewUserDatabaseTable.insertPartnerNewUser(card.partnerId(),
      System.currentTimeMillis());
    partnerUserLanguageDatabaseTable.addPartnerUserLanguage(card.partnerId(),
      language);
  }

  private CompletableFuture<Map<String, Object>> alterItem(
    User user, Card card, Stamp stamp, List<Item> cardItems
  ) {
    if (stamp.assignmentRole().isMemberRevoke()) {
      return deleteItem(user, cardItems.get(0).itemId());
    }
    var item = cardItems.stream()
      .max(Comparator.comparingLong(Item::lastUpdate)).get();
    return updateItemWithStamp(user, card, stamp, item);
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

  private CompletableFuture<Map<String, Object>> updateItemWithStamp(
    User user, Card card, Stamp stamp, Item item
  ) {
    var itemContent = new JSONObject(item.content());
    if (stamp.assignmentRole().isValueDebit() && itemContent.getInt("stamps") == 1) {
      switchPartnerCardState(card, PartnerCardState.USED, PartnerCardState.USED_UP);
      return deleteItem(user, item.itemId());
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
      itemContent.put("stamps", isFull ? 1 : stamps + 1);
    } else if (stamp.assignmentRole().isValueDebit()) {
      var stamps = itemContent.getInt("stamps");
      if (stamps == cardContent.getInt("value_number")) {
        switchPartnerCardState(card, PartnerCardState.UNUSED, PartnerCardState.USED);
      }
      itemContent.put("stamps", stamps - 1);
    }
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

  private CompletableFuture<Map<String, Object>> deleteItem(
    User user, UUID itemId
  ) {
    return itemDatabaseTable.deleteItem(itemId)
      .thenCompose(value -> user != null ?
        userItemDatabaseTable.deleteUserItem(user.id(), itemId) :
        CompletableFuture.completedFuture(null))
      .thenApply(value -> Map.of("success", true, "action", "REMOVE",
        "itemId", itemId));
  }

  private CompletableFuture<Map<String, Object>> storeScan(
    User user, Stamp stamp, String picc, String cmac, Card card,
    Map<String, Object> itemStampResult
  ) {
    if (!((boolean) itemStampResult.get("success"))) {
      return CompletableFuture.completedFuture(itemStampResult);
    }
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    processes.add(scanDatabaseTable.generateAvailableScanId()
      .thenCompose(scanId -> scanDatabaseTable.insertScan(scanId, picc, cmac,
          stamp.id(), card.id(), card.partnerId(), System.currentTimeMillis())
        .thenCompose(value -> user != null ?
          userScanDatabaseTable.insertUserScan(user.id(), scanId) :
          CompletableFuture.completedFuture(null))));
    processes.add(partnerScanDatabaseTable.insertPartnerScan(card.partnerId(),
      System.currentTimeMillis()));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> itemStampResult);
  }
}
