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
import com.dulno.core.partner.scan.PartnerScanDatabaseTable;
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
      .thenCompose(exists -> checkStampExistence(request, stampId,
        body.getString("picc"), body.getString("cmac"), exists));
  }

  private CompletableFuture<Map<String, Object>> checkStampExistence(
    HttpServletRequest request, UUID stampId, String picc, String cmac,
    boolean stampExists
  ) {
    if (!stampExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    return stampDatabaseTable.findStamp(stampId)
      .thenCompose(stamp -> validateStampScan(request, stamp, picc, cmac));
  }

  private CompletableFuture<Map<String, Object>> validateStampScan(
    HttpServletRequest request, Stamp stamp, String picc, String cmac
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
        checkScanExistence(request, stamp, picc, cmac, exists));
    } catch (Exception exception) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1003));
    }
  }

  private CompletableFuture<Map<String, Object>> checkScanExistence(
    HttpServletRequest request, Stamp stamp, String picc, String cmac,
    boolean scanExists
  ) {
    if (scanExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1004));
    }
    if (request.getHeader("User") == null) {

    }
    return findUser(request)
      .thenCompose(user -> cardDatabaseTable.findCard(stamp.assignedCard())
        .thenCompose(card -> processItemStamp(user, card, stamp)
          .thenCompose(itemResult -> storeScan(user, stamp, picc, cmac, card,
            itemResult))))
      .exceptionally(this::processError);
  }

  private CompletableFuture<Map<String, Object>> processItemStamp(
    User user, Card card, Stamp stamp
  ) {
    return userItemDatabaseTable.findUserItems(user.id())
      .thenCompose(items -> AsyncIterator.execute(items,
        itemDatabaseTable::findItem))
      .thenApply(items -> items.stream()
        .filter(item -> item.cardId().equals(card.id())).toList())
      .thenCompose(items -> checkItemConstellations(user, card, stamp, items));
  }

  private CompletableFuture<Map<String, Object>> checkItemConstellations(
    User user, Card card, Stamp stamp, List<Item> items
  ) {
    if (items.isEmpty() && stamp.assignmentRole().isValueDebit()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1005));
    }
    if (items.isEmpty() && stamp.assignmentRole().isMemberRevoke()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1006));
    }
    if (!items.isEmpty() && stamp.assignmentRole().isMemberIssue()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1007));
    }
    return storeItemStamp(user, card, stamp, items);
  }

  private CompletableFuture<Map<String, Object>> storeItemStamp(
    User user, Card card, Stamp stamp, List<Item> items
  ) {
    if (items.isEmpty() || stamp.assignmentRole().isValueCredit()) {
      var futureInformation = createNewItem(user, card)
        .thenCompose(itemController::collectItemInformation);
      futureInformation.thenAccept(information ->
        information.putAll(Map.of("success", true, "action", "CREATE")));
      return futureInformation;
    }
    return alterItem(user, card, stamp, items);
  }

  private CompletableFuture<Item> createNewItem(User user, Card card) {
    return itemDatabaseTable.generateAvailableItemId()
      .thenApply(itemId -> Item.create(itemId, card.id(),
        new JSONObject(createNewItemContent(card)).toString(),
        System.currentTimeMillis()))
      .thenCompose(item -> itemDatabaseTable.insertItem(item)
        .thenCompose(value -> userItemDatabaseTable.insertUserItem(
          user.id(), item.itemId()))
        .thenApply(value -> item));
  }

  private CompletableFuture<Map<String, Object>> alterItem(
    User user, Card card, Stamp stamp, List<Item> items
  ) {
    if (stamp.assignmentRole().isMemberRevoke()) {
      return deleteItem(user, items.get(0).itemId());
    }
    var item = items.stream()
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
    if (stamp.assignmentRole().isCollectionReward()) {
      var cardContent = new JSONObject(card.content());
      var stamps = itemContent.getInt("stamps");
      itemContent.put("stamps", stamps >= cardContent.getInt("reward_number") ?
        1 : stamps + 1);
    } else if (stamp.assignmentRole().isValueDebit()) {
      itemContent.put("stamps", itemContent.getInt("stamps") - 1);
    }
    return itemContent.toString();
  }

  private CompletableFuture<Map<String, Object>> deleteItem(
    User user, UUID itemId
  ) {
    return itemDatabaseTable.deleteItem(itemId)
      .thenCompose(value -> userItemDatabaseTable.deleteUserItem(user.id(), itemId))
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
          user.id(), stamp.id(), card.id(), card.partner(), System.currentTimeMillis())
        .thenCompose(value -> userScanDatabaseTable.insertUserScan(user.id(), scanId))));
    processes.add(partnerScanDatabaseTable.addPartnerScan(card.partner()));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> itemStampResult);
  }
}
