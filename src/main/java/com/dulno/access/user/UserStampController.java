package com.dulno.access.user;

import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.card.CardState;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.item.Item;
import com.dulno.core.item.ItemDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.ntag.NTAG;
import com.dulno.core.ntag.cmac.CMACType;
import com.dulno.core.partner.analysis.card.*;
import com.dulno.core.partner.analysis.stamp.PartnerOpenStampDatabaseTable;
import com.dulno.core.partner.analysis.stamp.PartnerScanDatabaseTable;
import com.dulno.core.partner.analysis.user.PartnerActiveUserDatabaseTable;
import com.dulno.core.partner.analysis.user.PartnerNewUserDatabaseTable;
import com.dulno.core.partner.analysis.user.PartnerUserLanguageDatabaseTable;
import com.dulno.core.partner.transaction.PartnerTransaction;
import com.dulno.core.partner.transaction.PartnerTransactionDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.scan.ScanDatabaseTable;
import com.dulno.core.stamp.Stamp;
import com.dulno.core.stamp.StampDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.item.UserItemDatabaseTable;
import com.dulno.core.user.partner.UserPartnerDatabaseTable;
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
  private final PartnerTransactionDatabaseTable partnerTransactionDatabaseTable;
  private final PartnerActiveCardDatabaseTable partnerActiveCardDatabaseTable;
  private final PartnerOpenStampDatabaseTable partnerOpenStampDatabaseTable;
  private final PartnerActiveUserDatabaseTable partnerActiveUserDatabaseTable;
  private final PartnerCardGroupDatabaseTable partnerCardGroupDatabaseTable;
  private final UserScanDatabaseTable userScanDatabaseTable;
  private final UserPartnerDatabaseTable userPartnerDatabaseTable;
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
    PartnerTransactionDatabaseTable partnerTransactionDatabaseTable,
    PartnerActiveCardDatabaseTable partnerActiveCardDatabaseTable,
    PartnerOpenStampDatabaseTable partnerOpenStampDatabaseTable,
    PartnerActiveUserDatabaseTable partnerActiveUserDatabaseTable,
    PartnerCardGroupDatabaseTable partnerCardGroupDatabaseTable,
    UserScanDatabaseTable userScanDatabaseTable,
    UserPartnerDatabaseTable userPartnerDatabaseTable,
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
    this.partnerTransactionDatabaseTable = partnerTransactionDatabaseTable;
    this.partnerActiveCardDatabaseTable = partnerActiveCardDatabaseTable;
    this.partnerOpenStampDatabaseTable = partnerOpenStampDatabaseTable;
    this.partnerActiveUserDatabaseTable = partnerActiveUserDatabaseTable;
    this.partnerCardGroupDatabaseTable = partnerCardGroupDatabaseTable;
    this.userScanDatabaseTable = userScanDatabaseTable;
    this.userPartnerDatabaseTable = userPartnerDatabaseTable;
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
        checkScanExistence(request, body, stamp, picc, cmac, ntag.counter(),
          exists));
    } catch (Exception exception) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1003));
    }
  }

  private CompletableFuture<Map<String, Object>> checkScanExistence(
    HttpServletRequest request, DulnoRequestBody body, Stamp stamp, String picc,
    String cmac, int counter, boolean scanExists
  ) {
    if (scanExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1004));
    }
    return findUser(request).exceptionally(throwable -> null)
      .thenCompose(user -> checkCardExistence(body, user, stamp, picc, cmac,
        counter));
  }

  private CompletableFuture<Map<String, Object>> checkCardExistence(
    DulnoRequestBody body, User user, Stamp stamp, String picc, String cmac,
    int counter
  ) {
    return cardDatabaseTable.cardExists(stamp.assignedCard())
      .thenCompose(exists -> processItemStamp(body, user, stamp, picc,
        cmac, counter, exists));
  }

  private CompletableFuture<Map<String, Object>> processItemStamp(
    DulnoRequestBody body, User user, Stamp stamp, String picc, String cmac,
    int counter, boolean cardExists
  ) {
    if (!cardExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1005));
    }
    return cardDatabaseTable.findCard(stamp.assignedCard())
      .thenCompose(card -> findUserItems(user, body)
        .thenCompose(items -> checkIsNewPartnerUser(user, body, card, items)
          .thenCompose(isNewPartnerUser -> processUserInventory(body, user,
            card, stamp, items, isNewPartnerUser)
            .thenCompose(itemResult -> storeScan(user, stamp, picc, cmac,
              counter, card, itemResult)))));
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

  private CompletableFuture<Boolean> checkIsNewPartnerUser(
    User user, DulnoRequestBody body, Card card, List<Item> allItems
  ) {
    if (user == null) {
      var hasPartnerItem = allItems.stream()
        .anyMatch(item -> item.partnerId().equals(card.partnerId()));
      var partners = body.<String>getList("partners").stream()
        .map(UUID::fromString).toList();
      return CompletableFuture.completedFuture(!partners.contains(card.partnerId())
        && !hasPartnerItem);
    }
    return userPartnerDatabaseTable.userPartnerExists(user.id(),
      card.partnerId()).thenApply(exists -> !exists);
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
    DulnoRequestBody body, User user, Card card, Stamp stamp,
    List<Item> allItems, boolean isNewPartnerUser
  ) {
    var cardItems = allItems.stream().filter(Objects::nonNull)
      .filter(item -> item.cardId().equals(card.id())).toList();
    var language = findUserLanguage(user, body);
    return checkItemConstellations(user, card, stamp, cardItems,
      isNewPartnerUser, language);
  }

  private String findUserLanguage(User user, DulnoRequestBody body) {
    if (user == null) {
      return body.getString("language");
    }
    return user.language();
  }

  private CompletableFuture<Map<String, Object>> checkItemConstellations(
    User user, Card card, Stamp stamp, List<Item> cardItems,
    boolean isNewPartnerUser, String language
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
    return storeItemStamp(user, card, stamp, cardItems, isNewPartnerUser, language);
  }

  private CompletableFuture<Map<String, Object>> storeItemStamp(
    User user, Card card, Stamp stamp, List<Item> cardItems,
    boolean isNewPartnerUser, String language
  ) {
    if (cardItems.isEmpty() || stamp.assignmentRole().isValueCredit()) {
      var futureInformation = createNewItem(user, card,
        cardItems, isNewPartnerUser, language)
        .thenCompose(itemController::collectItemInformation);
      futureInformation.thenAccept(information ->
        information.putAll(Map.of("success", true, "action", "CREATE")));
      return futureInformation;
    }
    return alterItem(user, card, stamp, cardItems);
  }

  private CompletableFuture<Item> createNewItem(
    User user, Card card, List<Item> cardItems, boolean isNewPartnerUser,
    String language
  ) {
    return itemDatabaseTable.generateAvailableItemId()
      .thenCompose(itemId -> createNewItem(user, card, cardItems, itemId,
        isNewPartnerUser, language));
  }

  private CompletableFuture<Item> createNewItem(
    User user, Card card, List<Item> cardItems, UUID itemId,
    boolean isNewPartnerUser, String language
  ) {
    if (card.state().isUnused()) {
      card.changeState(CardState.USED);
      cardDatabaseTable.updateCard(card);
    }
    checkNewItemPartnerAnalytics(user, card, isNewPartnerUser, language);
    checkNewItemPartnerBalances(card, itemId);
    checkNewItemPartnerCardGroup(card, cardItems, itemId);
    var item = Item.create(itemId, card.partnerId(), card.id(),
      new JSONObject(createNewItemContent(card)).toString(),
      System.currentTimeMillis());
    return itemDatabaseTable.insertItem(item)
      .thenCompose(value -> user != null ?
        userItemDatabaseTable.insertUserItem(user.id(), item.itemId()) :
        CompletableFuture.completedFuture(null))
      .thenApply(value -> item);
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
    Card card, List<Item> cardItems, UUID itemId
  ) {
    var partnerItem = cardItems.stream()
      .filter(item -> item.partnerId().equals(card.partnerId())).findFirst();
    findPartnerCardGroupId(partnerItem)
      .thenAccept(groupId -> partnerCardGroupDatabaseTable
        .insertPartnerCardGroup(itemId, groupId, card.partnerId()));
    if (partnerItem.isEmpty()) {
      partnerActiveUserDatabaseTable.insertPartnerActiveUser(card.partnerId(),
        1, System.currentTimeMillis());
    }
  }

  private CompletableFuture<UUID> findPartnerCardGroupId(Optional<Item> partnerItem) {
    if (partnerItem.isEmpty()) {
      return partnerCardGroupDatabaseTable.generateAvailablePartnerCardGroupId();
    }
    var itemId = partnerItem.get().itemId();
    return partnerCardGroupDatabaseTable.partnerCardGroupExists(itemId)
      .thenCompose(exists -> exists ?
        partnerCardGroupDatabaseTable.findPartnerCardGroup(itemId) :
        partnerCardGroupDatabaseTable.generateAvailablePartnerCardGroupId());
  }

  private CompletableFuture<Map<String, Object>> alterItem(
    User user, Card card, Stamp stamp, List<Item> cardItems
  ) {
    if (stamp.assignmentRole().isMemberRevoke()) {
      return deleteItem(user, card, cardItems.get(0).itemId());
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
      return deleteItem(user, card, item.itemId());
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

  private CompletableFuture<Map<String, Object>> deleteItem(
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

  private CompletableFuture<Map<String, Object>> storeScan(
    User user, Stamp stamp, String picc, String cmac, int counter, Card card,
    Map<String, Object> itemStampResult
  ) {
    if (!((boolean) itemStampResult.get("success"))) {
      return CompletableFuture.completedFuture(itemStampResult);
    }
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    processes.add(scanDatabaseTable.generateAvailableScanId()
      .thenCompose(scanId -> scanDatabaseTable.insertScan(scanId, picc, cmac,
          stamp.id(), card.id(), card.partnerId(), counter,
          System.currentTimeMillis())
        .thenCompose(value -> user != null ?
          userScanDatabaseTable.insertUserScan(user.id(), scanId) :
          CompletableFuture.completedFuture(null))));
    processes.add(partnerScanDatabaseTable.insertPartnerScan(card.partnerId(),
      System.currentTimeMillis()));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> itemStampResult);
  }
}
