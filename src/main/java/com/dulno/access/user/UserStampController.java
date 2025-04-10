package com.dulno.access.user;

import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.ntag.NTAG;
import com.dulno.core.ntag.cmac.CMACType;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.scan.PartnerScanDatabaseTable;
import com.dulno.core.scan.ScanDatabaseTable;
import com.dulno.core.stamp.Stamp;
import com.dulno.core.stamp.StampDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserCard;
import com.dulno.core.user.UserCardDatabaseTable;
import com.dulno.core.user.UserDatabaseTable;
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
  private final UserCardDatabaseTable userCardDatabaseTable;
  private final StampDatabaseTable stampDatabaseTable;
  private final CardDatabaseTable cardDatabaseTable;
  private final ScanDatabaseTable scanDatabaseTable;
  private final PartnerScanDatabaseTable partnerScanDatabaseTable;
  private final UserCardController userCardController;

  private UserStampController(
    UserDatabaseTable userDatabaseTable, Hashing hashing,
    DulnoEnvironment environment, ErrorRepository errorRepository,
    UserCardDatabaseTable userCardDatabaseTable,
    StampDatabaseTable stampDatabaseTable, CardDatabaseTable cardDatabaseTable,
    ScanDatabaseTable scanDatabaseTable,
    PartnerScanDatabaseTable partnerScanDatabaseTable,
    UserCardController userCardController
  ) {
    super(userDatabaseTable, hashing, environment, errorRepository);
    this.userCardDatabaseTable = userCardDatabaseTable;
    this.stampDatabaseTable = stampDatabaseTable;
    this.cardDatabaseTable = cardDatabaseTable;
    this.scanDatabaseTable = scanDatabaseTable;
    this.partnerScanDatabaseTable = partnerScanDatabaseTable;
    this.userCardController = userCardController;
  }

  @RequestMapping(path = "/user/stamp/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> requestUserStamp(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var stampId = body.getUUID("stamp");
    return findUser(request)
      .thenCompose(user -> stampDatabaseTable.stampExists(stampId)
        .thenCompose(exists -> checkStampExistence(user, stampId,
          body.getString("picc"), body.getString("cmac"), exists)))
      .exceptionally(this::processError);
  }

  private CompletableFuture<Map<String, Object>> checkStampExistence(
    User user, UUID stampId, String picc, String cmac, boolean stampExists
  ) {
    if (!stampExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    return stampDatabaseTable.findStamp(stampId)
      .thenCompose(stamp -> validateStampScan(user, stamp, picc, cmac));
  }

  private CompletableFuture<Map<String, Object>> validateStampScan(
    User user, Stamp stamp, String picc, String cmac
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
      return scanDatabaseTable.scanExists(picc, cmac)
        .thenCompose(exists -> checkScanExistence(user, stamp, picc, cmac, exists));
    } catch (Exception exception) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1003));
    }
  }

  private CompletableFuture<Map<String, Object>> checkScanExistence(
    User user, Stamp stamp, String picc, String cmac, boolean scanExists
  ) {
    if (scanExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1004));
    }
    return cardDatabaseTable.findCard(stamp.assignedCard())
      .thenCompose(card -> processUserCardStamp(user, card, stamp)
        .thenCompose(userCardResult -> storeScan(user, stamp, picc, cmac, card,
          userCardResult)));
  }

  private CompletableFuture<Map<String, Object>> processUserCardStamp(
    User user, Card card, Stamp stamp
  ) {
    return userCardDatabaseTable.userCardExists(user.id(), card.id())
      .thenCompose(exists -> checkUserCardConstellations(user, card, stamp, exists));
  }

  private CompletableFuture<Map<String, Object>> checkUserCardConstellations(
    User user, Card card, Stamp stamp, boolean userCardExists
  ) {
    if (!userCardExists && stamp.assignmentRole().isValueDebit()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1005));
    }
    if (!userCardExists && stamp.assignmentRole().isMemberRevoke()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1006));
    }
    if (userCardExists && stamp.assignmentRole().isMemberIssue()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1007));
    }
    return storeUserCardStamp(user, card, stamp, userCardExists);
  }

  private CompletableFuture<Map<String, Object>> storeUserCardStamp(
    User user, Card card, Stamp stamp, boolean userCardExists
  ) {
    if (!userCardExists || stamp.assignmentRole().isValueCredit()) {
      var futureInformation = createNewUserCard(user, card)
        .thenCompose(userCard -> userCardDatabaseTable.insertUserCard(userCard)
          .thenCompose(value -> userCardController.collectCardInformation(userCard)));
      futureInformation.thenAccept(information ->
        information.putAll(Map.of("success", true, "action", "CREATE")));
      return futureInformation;
    }
    return userCardDatabaseTable.findUserCards(user.id(), card.id())
      .thenCompose(userCards -> alterUserCard(user, card, stamp, userCards));
  }

  private CompletableFuture<UserCard> createNewUserCard(User user, Card card) {
    return userCardDatabaseTable.generateAvailableUserId(user.id(), card.id())
      .thenApply(userCardId -> UserCard.create(user.id(), card.id(), userCardId,
        new JSONObject(createNewUserCardContent(card)).toString(),
        System.currentTimeMillis()));
  }

  private CompletableFuture<Map<String, Object>> alterUserCard(
    User user, Card card, Stamp stamp, List<UserCard> userCards
  ) {
    if (stamp.assignmentRole().isMemberRevoke()) {
      return deleteUserCard(user, card, userCards.get(0).userCardId());
    }
    var userCard = userCards.stream()
      .max(Comparator.comparingLong(UserCard::lastUpdate)).get();
    return updateUserCardWithStamp(user, card, stamp, userCard);
  }

  private Map<String, Object> createNewUserCardContent(Card card) {
    if (card.type().isCollection()) {
      return Map.of("stamps", 1);
    } else if (card.type().isValue()) {
      return Map.of("stamps", new JSONObject(card.content()).getInt("value_number"));
    } else if (card.type().isMember()) {
      return Maps.newHashMap();
    }
    return Maps.newHashMap();
  }

  private CompletableFuture<Map<String, Object>> updateUserCardWithStamp(
    User user, Card card, Stamp stamp, UserCard userCard
  ) {
    var userCardContent = new JSONObject(userCard.content());
    if (stamp.assignmentRole().isValueDebit() && userCardContent.getInt("stamps") == 1) {
      return deleteUserCard(user, card, userCard.userCardId());
    }
    userCard.changeContent(updateUserCardContent(stamp, card, userCardContent));
    userCard.changeLastUpdate(System.currentTimeMillis());
    var futureInformation = userCardDatabaseTable.updateUserCard(userCard)
      .thenCompose(value -> userCardController.collectCardInformation(userCard));
    futureInformation.thenAccept(information ->
      information.putAll(Map.of("success", true, "action", "UPDATE")));
    return futureInformation;
  }

  private String updateUserCardContent(
    Stamp stamp, Card card, JSONObject userCardContent
  ) {
    if (stamp.assignmentRole().isCollectionReward()) {
      var cardContent = new JSONObject(card.content());
      var stamps = userCardContent.getInt("stamps");
      userCardContent.put("stamps", stamps >= cardContent.getInt("reward_number") ?
        1 : stamps + 1);
    } else if (stamp.assignmentRole().isValueDebit()) {
      userCardContent.put("stamps", userCardContent.getInt("stamps") - 1);
    }
    return userCardContent.toString();
  }

  private CompletableFuture<Map<String, Object>> deleteUserCard(
    User user, Card card, UUID userCardId
  ) {
    return userCardDatabaseTable.deleteUserCard(user.id(), card.id(), userCardId)
      .thenApply(value -> Map.of("success", true, "action", "REMOVE",
        "userCardId", userCardId));
  }

  private CompletableFuture<Map<String, Object>> storeScan(
    User user, Stamp stamp, String picc, String cmac, Card card,
    Map<String, Object> userCardResult
  ) {
    if (!((boolean) userCardResult.get("success"))) {
      return CompletableFuture.completedFuture(userCardResult);
    }
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    processes.add(scanDatabaseTable.generateAvailableScanId()
      .thenCompose(scanId -> scanDatabaseTable.insertScan(scanId, picc, cmac,
        user.id(), stamp.id(), card.id(), card.partner(),
        System.currentTimeMillis())));
    processes.add(partnerScanDatabaseTable.addPartnerScan(card.partner()));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> userCardResult);
  }
}
