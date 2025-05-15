package com.dulno.access.user;

import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.email.EmailValidation;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.google.GoogleConfiguration;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.item.Item;
import com.dulno.core.item.ItemDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.locale.Translation;
import com.dulno.core.mail.Mail;
import com.dulno.core.partner.analysis.card.PartnerCardGroupDatabaseTable;
import com.dulno.core.partner.analysis.user.PartnerActiveUserDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.device.UserDeviceDatabaseTable;
import com.dulno.core.user.item.UserItemDatabaseTable;
import com.dulno.core.user.verification.UserVerification;
import com.dulno.core.user.verification.UserVerificationDatabaseTable;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserBindController extends UserController {
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final ItemDatabaseTable itemDatabaseTable;
  private final UserItemDatabaseTable userItemDatabaseTable;
  private final UserDeviceDatabaseTable userDeviceDatabaseTable;
  private final CardDatabaseTable cardDatabaseTable;
  private final PartnerCardGroupDatabaseTable partnerCardGroupDatabaseTable;
  private final PartnerActiveUserDatabaseTable partnerActiveUserDatabaseTable;
  private final Translation translation;
  private final Mail verificationMail;
  private final GoogleConfiguration googleConfiguration;
  private final GsonFactory gsonFactory = GsonFactory.getDefaultInstance();
  private final NetHttpTransport netHttpTransport = new NetHttpTransport();

  private UserBindController(
    UserDatabaseTable userDatabaseTable, Hashing hashing,
    DulnoEnvironment environment, ErrorRepository errorRepository,
    UserVerificationDatabaseTable userVerificationDatabaseTable,
    ItemDatabaseTable itemDatabaseTable,
    UserItemDatabaseTable userItemDatabaseTable,
    UserDeviceDatabaseTable userDeviceDatabaseTable,
    CardDatabaseTable cardDatabaseTable,
    PartnerCardGroupDatabaseTable partnerCardGroupDatabaseTable,
    PartnerActiveUserDatabaseTable partnerActiveUserDatabaseTable,
    Translation translation, @Qualifier("verificationMail") Mail verificationMail,
    GoogleConfiguration googleConfiguration
  ) {
    super(userDatabaseTable, hashing, environment, errorRepository);
    this.userVerificationDatabaseTable = userVerificationDatabaseTable;
    this.itemDatabaseTable = itemDatabaseTable;
    this.userItemDatabaseTable = userItemDatabaseTable;
    this.userDeviceDatabaseTable = userDeviceDatabaseTable;
    this.cardDatabaseTable = cardDatabaseTable;
    this.partnerCardGroupDatabaseTable = partnerCardGroupDatabaseTable;
    this.partnerActiveUserDatabaseTable = partnerActiveUserDatabaseTable;
    this.translation = translation;
    this.verificationMail = verificationMail;
    this.googleConfiguration = googleConfiguration;
  }

  @RequestMapping(path = "/user/bind/request/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> requestUserBinding(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var email = body.getString("email");
    if (!EmailValidation.validate(email)) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    var language = body.getString("language");
    return userDatabaseTable().userExists(email)
      .thenCompose(exists -> requestUserBinding(email, language, exists));
  }

  private CompletableFuture<Map<String, Object>> requestUserBinding(
    String email, String language, boolean userExists
  ) {
    if (!userExists) {
      return generateAvailableUserId()
        .thenApply(userId -> requestUserBinding(userId, email, language));
    }
    return userDatabaseTable().findUser(email)
      .thenApply(user -> requestUserBinding(user.id(), email, language));
  }

  private Map<String, Object> requestUserBinding(
    UUID userId, String email, String language
  ) {
    var verification = UserVerificationCode.create(translation, language);
    verification.generate();
    userVerificationDatabaseTable.insertUserVerification(userId, email,
      verification.code());
    var title = translation.translate(language, "user.verification.email.title");
    verificationMail.send(email, language, title, verification.content());
    return Map.of("success", true, "user", userId);
  }

  @RequestMapping(path = "/user/bind/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> completeUserBinding(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var userId = body.getUUID("user");
    return userVerificationDatabaseTable.userVerificationExists(userId)
      .thenCompose(exists -> completeUserBinding(body, userId,
        body.getString("code"), exists));
  }

  private CompletableFuture<Map<String, Object>> completeUserBinding(
    DulnoRequestBody body, UUID userId, String code, boolean verificationExists
  ) {
    if (!verificationExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    return userVerificationDatabaseTable.findUserVerification(userId)
      .thenCompose(verification -> userDatabaseTable().userExists(userId)
        .thenCompose(userExists -> completeUserBinding(body, userId, code,
          verification, userExists)));
  }

  private CompletableFuture<Map<String, Object>> completeUserBinding(
    DulnoRequestBody body, UUID userId, String code,
    UserVerification verification, boolean userExists
  ) {
    if (!verification.code().equals(code)) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
    if (!userExists) {
      return signupUser(userId, verification.email(), body)
        .exceptionally(user -> null)
        .thenCompose(user -> user == null ?
          CompletableFuture.completedFuture(Map.of("success", false, "error", 1002)) :
          completeUserBinding(user, body, true));
    }
    return userDatabaseTable().findUser(userId)
      .thenCompose(user -> completeUserBinding(user, body, false));
  }

  private CompletableFuture<Map<String, Object>> completeUserBinding(
    User user, DulnoRequestBody body, boolean isNewUser
  ) {
    userVerificationDatabaseTable.deleteUserVerification(user.id());
    if (isNewUser) {
      return CompletableFuture.completedFuture(bindingResponse(user));
    }
    return mergeUserItems(user, body).thenApply(value -> bindingResponse(user));
  }

  @RequestMapping(path = "/user/bind/google/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> bindGoogleUser(
    @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    return bindGoogleUser(body, body.getString("token"));
  }

  public CompletableFuture<Map<String, Object>> bindGoogleUser(
    DulnoRequestBody body, String token
  ) {
    try {
      var verifier = new GoogleIdTokenVerifier.Builder(netHttpTransport, gsonFactory)
        .setAudience(Collections.singletonList(googleConfiguration.clientId()))
        .build();
      var idToken = verifier.verify(token);
      if (idToken == null) {
        return CompletableFuture.completedFuture(Map.of("success", false,
          "error", 1000));
      }
      var payload = idToken.getPayload();
      var email = payload.getEmail();
      return userDatabaseTable().userExists(email)
        .thenCompose(userExists -> bindGoogleUser(body, email, userExists));
    } catch (Exception exception) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
  }

  private CompletableFuture<Map<String, Object>> bindGoogleUser(
    DulnoRequestBody body, String email, boolean userExists
  ) {
    if (!userExists) {
      return signupUser(email, body).exceptionally(user -> null)
        .thenCompose(newUser -> newUser == null ?
          CompletableFuture.completedFuture(Map.of("success", false, "error", 1002)) :
          completeGoogleUserBinding(newUser, body, true));
    }
    return userDatabaseTable().findUser(email)
      .thenCompose(user -> completeGoogleUserBinding(user, body, false));
  }

  private CompletableFuture<Map<String, Object>> completeGoogleUserBinding(
    User user, DulnoRequestBody body, boolean isNewUser
  ) {
    if (isNewUser) {
      return CompletableFuture.completedFuture(bindingResponse(user));
    }
    return mergeUserItems(user, body).thenApply(value -> bindingResponse(user));
  }

  private CompletableFuture<Void> mergeUserItems(
    User targetUser, DulnoRequestBody body
  ) {
    return userItemDatabaseTable.findUserItems(targetUser.id())
      .thenCompose(this::findItems)
      .thenCompose(targetCards -> findUnknownUserItems(body)
        .thenCompose(sourceCards -> findMergeCards(sourceCards, targetCards)
          .thenCompose(cards -> processItemMerges(targetUser, sourceCards,
            targetCards, cards))));
  }

  private CompletableFuture<List<Item>> findItems(List<UUID> itemIds) {
    return AsyncIterator.execute(itemIds, itemDatabaseTable::findItem);
  }

  private CompletableFuture<List<Item>> findUnknownUserItems(
    DulnoRequestBody body
  ) {
    if (!body.has("cards")) {
      return CompletableFuture.completedFuture(Lists.newArrayList());
    }
    var itemIds = body.<String>getList("cards")
      .stream().map(UUID::fromString).toList();
    return AsyncIterator.execute(itemIds, this::findUnclaimedItem)
      .thenApply(items -> items.stream().filter(Objects::nonNull).toList());
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

  private CompletableFuture<List<Card>> findMergeCards(
    List<Item> sourceItems, List<Item> targetItems
  ) {
    var items = Lists.<Item>newArrayList();
    items.addAll(sourceItems);
    items.addAll(targetItems);
    var cardIds = items.stream().map(Item::cardId).distinct().toList();
    return AsyncIterator.execute(cardIds, cardId ->
        cardDatabaseTable.cardExists(cardId)
          .thenCompose(exists -> exists ? cardDatabaseTable.findCard(cardId) :
            CompletableFuture.completedFuture(null)))
      .thenApply(result -> result.stream().filter(Objects::nonNull).toList());
  }

  private CompletableFuture<Void> processItemMerges(
    User targetUser, List<Item> sourceItems, List<Item> targetItems,
    List<Card> cards
  ) {
    processItemMergesActiveUsers(sourceItems, targetItems);
    return AsyncIterator.execute(sourceItems,
        item -> checkItemMergeCompatibility(targetUser, item, targetItems, cards)
          .thenCompose(value -> itemDatabaseTable.deleteItem(item.itemId()))
          .thenCompose(value -> partnerCardGroupDatabaseTable
            .deletePartnerCardGroup(item.itemId())))
      .thenApply(value -> null);
  }

  private void processItemMergesActiveUsers(
    List<Item> sourceItems, List<Item> targetItems
  ) {
    targetItems.stream().map(Item::partnerId)
      .filter(id -> sourceItems.stream().map(Item::partnerId).anyMatch(id::equals))
      .distinct().forEach(partners -> partnerActiveUserDatabaseTable
        .insertPartnerActiveUser(partners, -1, System.currentTimeMillis()));
  }

  private CompletableFuture<Void> checkItemMergeCompatibility(
    User targetUser, Item sourceItem, List<Item> targetItems, List<Card> cards
  ) {
    var sourceCardOptional = cards.stream()
      .filter(card -> card.id().equals(sourceItem.cardId())).findFirst();
    if (sourceCardOptional.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    var sourceCard = sourceCardOptional.get();
    var targetItemOptional = targetItems.stream()
      .filter(entry -> entry.cardId().equals(sourceCard.id())).findFirst();
    return mergeUserItem(targetUser, sourceItem, sourceCard,
      targetItemOptional, targetItems);
  }

  private CompletableFuture<Void> mergeUserItem(
    User targetUser, Item sourceItem, Card sourceCard,
    Optional<Item> targetItemOptional, List<Item> targetItems
  ) {
    if (targetItemOptional.isEmpty() || sourceCard.type().isValue()) {
      return itemDatabaseTable.generateAvailableItemId().thenCompose(itemId ->
        mergeUserNewItem(targetUser, sourceItem, targetItems, itemId));
    } else if (sourceCard.type().isCollection()) {
      return mergeUserCollectionItem(sourceItem, sourceCard, targetItemOptional);
    }
    return CompletableFuture.completedFuture(null);
  }

  private CompletableFuture<Void> mergeUserNewItem(
    User targetUser, Item sourceItem, List<Item> targetItems, UUID newItemId
  ) {
    var partnerItem = targetItems.stream()
      .filter(item -> item.partnerId().equals(sourceItem.partnerId())).findFirst();
    selectPartnerCardGroupId(partnerItem, sourceItem)
      .thenAccept(groupId -> partnerCardGroupDatabaseTable
        .insertPartnerCardGroup(newItemId, groupId, sourceItem.partnerId()));
    return itemDatabaseTable.insertItem(Item.create(newItemId,
        sourceItem.partnerId(), sourceItem.cardId(), sourceItem.content(),
        sourceItem.lastUpdate()))
      .thenAccept(value -> userItemDatabaseTable.insertUserItem(
        targetUser.id(), newItemId));
  }

  private CompletableFuture<UUID> selectPartnerCardGroupId(
    Optional<Item> partnerItem, Item sourceItem
  ) {
    if (partnerItem.isEmpty()) {
      return findPartnerCardGroupIdSecure(sourceItem.itemId());
    }
    return findPartnerCardGroupIdSecure(partnerItem.get().itemId());
  }

  private CompletableFuture<UUID> findPartnerCardGroupIdSecure(UUID itemId) {
    return partnerCardGroupDatabaseTable.partnerCardGroupExists(itemId)
      .thenCompose(exists -> exists ?
        partnerCardGroupDatabaseTable.findPartnerCardGroup(itemId) :
        partnerCardGroupDatabaseTable.generateAvailablePartnerCardGroupId());
  }

  private CompletableFuture<Void> mergeUserCollectionItem(
    Item sourceItem, Card sourceCard, Optional<Item> targetItemOptional
  ) {
    var targetItem = targetItemOptional.get();
    var newCardContent = Map.of("stamps", Math.min(
      new JSONObject(sourceCard.content()).getInt("reward_number"),
      new JSONObject(sourceItem.content()).getInt("stamps") +
        new JSONObject(targetItem.content()).getInt("stamps")));
    return itemDatabaseTable.updateItem(Item.create(targetItem.itemId(),
      targetItem.partnerId(), targetItem.cardId(),
      new JSONObject(newCardContent).toString(), targetItem.lastUpdate()));
  }

  private Map<String, Object> bindingResponse(User user) {
    return Map.of("success", true, "email", user.email(),
      "user", user.id(), "authenticationKey", user.authenticationKey());
  }

  private CompletableFuture<User> signupUser(String email, DulnoRequestBody body) {
    return generateAvailableUserId()
      .thenCompose(userId -> signupUser(userId, email, body));
  }

  private CompletableFuture<User> signupUser(
    UUID userId, String email, DulnoRequestBody body
  ) {
    var legalAccepted = body.getBoolean("legalAccepted");
    if (!legalAccepted) {
      return CompletableFuture.completedFuture(null);
    }
    var itemIds = body.<String>getList("cards").stream()
      .map(UUID::fromString).toList();
    return userDeviceDatabaseTable.generateAvailableUserDeviceId()
      .thenCompose(deviceId -> signupUser(userId, email, deviceId,
        body.getString("language"), legalAccepted, body.getBoolean("newsletter"),
        body.getString("id"), body.getString("operatingSystem"),
        body.getString("operatingSystemVersion"), body.getString("brand"),
        body.getString("model"), body.getString("name"), itemIds));
  }

  private CompletableFuture<User> signupUser(
    UUID userId, String email, UUID deviceId, String language, boolean compliant,
    boolean newsletter, String publicDeviceId, String operatingSystem,
    String operatingSystemVersion, String brand, String model, String name,
    List<UUID> itemIds
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    var authenticationKey = generateAuthenticationKey();
    language = parseUserLanguage(language);
    var user = User.create(userId, authenticationKey, email, language, compliant,
      newsletter, System.currentTimeMillis());
    processes.add(userDatabaseTable().insertUser(user));
    processes.add(userDeviceDatabaseTable.insertUserDevice(deviceId, userId,
      publicDeviceId, operatingSystem, operatingSystemVersion, brand, model, name));
    for (var itemId : itemIds) {
      processes.add(claimItem(userId, itemId));
    }
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> user);
  }

  private CompletableFuture<Void> claimItem(UUID userId, UUID itemId) {
    return itemDatabaseTable.itemExists(itemId)
      .thenCompose(itemExists -> userItemDatabaseTable.userItemExists(itemId)
        .thenCompose(isClaimed -> claimItem(userId, itemId, itemExists,
          isClaimed)));
  }

  private CompletableFuture<Void> claimItem(
    UUID userId, UUID itemId, boolean itemExists, boolean isClaimed
  ) {
    if (!itemExists || isClaimed) {
      return CompletableFuture.completedFuture(null);
    }
    return userItemDatabaseTable.insertUserItem(userId, itemId);
  }

  private String parseUserLanguage(String language) {
    if (language.equals("en") || language.equals("de")) {
      return language;
    }
    return "en";
  }

  private CompletableFuture<UUID> generateAvailableUserId() {
    var futureResponse = new CompletableFuture<UUID>();
    var id = UUID.randomUUID();
    userDatabaseTable().userExists(id).thenCompose(userExists -> userExists ?
      generateAvailableUserId().thenApply(futureResponse::complete) :
      userVerificationDatabaseTable.userVerificationExists(id)
        .thenCompose(verificationExists -> verificationExists ?
          generateAvailableUserId().thenApply(futureResponse::complete) :
          CompletableFuture.completedFuture(futureResponse.complete(id))));
    return futureResponse;
  }
}