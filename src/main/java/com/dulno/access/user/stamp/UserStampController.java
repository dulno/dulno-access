package com.dulno.access.user.stamp;

import com.dulno.access.user.UserController;
import com.dulno.access.user.card.UserCardInformationController;
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
import com.dulno.core.partner.analysis.stamp.PartnerScanDatabaseTable;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserStampController extends UserController {
  private final ItemDatabaseTable itemDatabaseTable;
  private final UserItemDatabaseTable userItemDatabaseTable;
  private final StampDatabaseTable stampDatabaseTable;
  private final CardDatabaseTable cardDatabaseTable;
  private final ScanDatabaseTable scanDatabaseTable;
  private final PartnerScanDatabaseTable partnerScanDatabaseTable;
  private final UserScanDatabaseTable userScanDatabaseTable;
  private final UserPartnerDatabaseTable userPartnerDatabaseTable;
  private final UserCardInformationController itemController;
  private final UserStampItemCreation itemCreation;
  private final UserStampItemAlteration itemAlteration;

  private UserStampController(
    UserDatabaseTable userDatabaseTable, Hashing hashing,
    DulnoEnvironment environment, ErrorRepository errorRepository,
    ItemDatabaseTable itemDatabaseTable,
    UserItemDatabaseTable userItemDatabaseTable,
    StampDatabaseTable stampDatabaseTable, CardDatabaseTable cardDatabaseTable,
    ScanDatabaseTable scanDatabaseTable,
    PartnerScanDatabaseTable partnerScanDatabaseTable,
    UserScanDatabaseTable userScanDatabaseTable,
    UserPartnerDatabaseTable userPartnerDatabaseTable,
    UserCardInformationController itemController,
    UserStampItemCreation itemCreation, UserStampItemAlteration itemAlteration
  ) {
    super(userDatabaseTable, hashing, environment, errorRepository);
    this.itemDatabaseTable = itemDatabaseTable;
    this.userItemDatabaseTable = userItemDatabaseTable;
    this.stampDatabaseTable = stampDatabaseTable;
    this.cardDatabaseTable = cardDatabaseTable;
    this.scanDatabaseTable = scanDatabaseTable;
    this.partnerScanDatabaseTable = partnerScanDatabaseTable;
    this.userScanDatabaseTable = userScanDatabaseTable;
    this.userPartnerDatabaseTable = userPartnerDatabaseTable;
    this.itemController = itemController;
    this.itemCreation = itemCreation;
    this.itemAlteration = itemAlteration;
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
    allItems = allItems.stream().filter(Objects::nonNull).toList();
    var cardItems = allItems.stream()
      .filter(item -> item.cardId().equals(card.id())).toList();
    var language = findUserLanguage(user, body);
    return checkItemConstellations(user, card, stamp, cardItems, allItems,
      isNewPartnerUser, language);
  }

  private String findUserLanguage(User user, DulnoRequestBody body) {
    if (user == null) {
      return body.getString("language");
    }
    return user.language();
  }

  private CompletableFuture<Map<String, Object>> checkItemConstellations(
    User user, Card card, Stamp stamp, List<Item> cardItems, List<Item> allItems,
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
    return storeItemStamp(user, card, stamp, cardItems, allItems,
      isNewPartnerUser, language);
  }

  private CompletableFuture<Map<String, Object>> storeItemStamp(
    User user, Card card, Stamp stamp, List<Item> cardItems, List<Item> allItems,
    boolean isNewPartnerUser, String language
  ) {
    if (cardItems.isEmpty() || stamp.assignmentRole().isValueCredit()) {
      var futureInformation = itemCreation.createNewItem(user, card,
          allItems, isNewPartnerUser, language)
        .thenCompose(itemController::collectItemInformation);
      futureInformation.thenAccept(information ->
        information.putAll(Map.of("success", true, "action", "CREATE")));
      return futureInformation;
    }
    return itemAlteration.alterItem(user, card, stamp, cardItems);
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
