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
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.*;
import com.dulno.core.user.device.UserDeviceDatabaseTable;
import com.dulno.core.user.item.UserItemDatabaseTable;
import com.dulno.core.user.verification.UserVerification;
import com.dulno.core.user.verification.UserVerificationDatabaseTable;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserBindController extends UserController {
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final ItemDatabaseTable itemDatabaseTable;
  private final UserItemDatabaseTable userItemDatabaseTable;
  private final UserDeviceDatabaseTable userDeviceDatabaseTable;
  private final CardDatabaseTable cardDatabaseTable;
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
    CardDatabaseTable cardDatabaseTable, Translation translation,
    @Qualifier("verificationMail") Mail verificationMail,
    GoogleConfiguration googleConfiguration
  ) {
    super(userDatabaseTable, hashing, environment, errorRepository);
    this.userVerificationDatabaseTable = userVerificationDatabaseTable;
    this.itemDatabaseTable = itemDatabaseTable;
    this.userItemDatabaseTable = userItemDatabaseTable;
    this.userDeviceDatabaseTable = userDeviceDatabaseTable;
    this.cardDatabaseTable = cardDatabaseTable;
    this.translation = translation;
    this.verificationMail = verificationMail;
    this.googleConfiguration = googleConfiguration;
  }

  @RequestMapping(path = "/user/bind/request/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> requestUserBinding(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var email = body.getString("email");
    if (!EmailValidation.validate(email)) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    if (request.getHeader("User") == null) {
      return signupUser(body).thenApply(user -> user == null ?
        Map.of("success", false, "error", 1001) :
        requestUserBinding(user, email));
    }
    return findUser(request)
      .thenApply(user -> requestUserBinding(user, email))
      .exceptionally(this::processError);
  }

  private Map<String, Object> requestUserBinding(
    User user, String email
  ) {
    var verification = UserVerificationCode.create(translation, user);
    verification.generate();
    userVerificationDatabaseTable.insertUserVerification(user.id(), email,
      verification.code());
    var title = translation.translateUser(user, "user.verification.email.title");
    verificationMail.send(email, user.language(), title, verification.content());
    return Map.of("success", true);
  }

  @RequestMapping(path = "/user/bind/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> completeUserBinding(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    return findUser(request)
      .thenCompose(user -> userVerificationDatabaseTable
        .userVerificationExists(user.id())
        .thenCompose(exists -> completeUserBinding(user,
          body.getString("code"), exists)))
      .exceptionally(this::processError);
  }

  private CompletableFuture<Map<String, Object>> completeUserBinding(
    User user, String code, boolean verificationExists
  ) {
    if (!verificationExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    return userVerificationDatabaseTable.findUserVerification(user.id())
      .thenCompose(verification -> userDatabaseTable()
        .userExists(verification.email())
        .thenCompose(alreadyExists -> completeUserBinding(user, code,
          verification, alreadyExists)));
  }

  private CompletableFuture<Map<String, Object>> completeUserBinding(
    User user, String code, UserVerification verification, boolean alreadyExists
  ) {
    if (!verification.code().equals(code)) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
    userVerificationDatabaseTable.deleteUserVerification(user.id());
    if (alreadyExists) {
      return mergeUsers(user, verification.email());
    }
    user.changeEmail(verification.email());
    userDatabaseTable().updateUser(user);
    return CompletableFuture.completedFuture(bindingResponse(user));
  }

  @RequestMapping(path = "/user/bind/google/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> bindGoogleUser(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var token = body.getString("token");
    if (request.getHeader("User") == null) {
      return signupUser(body).thenCompose(user -> user == null ?
        CompletableFuture.completedFuture(Map.of("success", false, "error", 1000)) :
        bindGoogleUser(user, token));
    }
    return findUser(request).thenCompose(user -> bindGoogleUser(user, token))
      .exceptionally(this::processError);
  }

  public CompletableFuture<Map<String, Object>> bindGoogleUser(
    User user, String token
  ) {
    try {
      var verifier = new GoogleIdTokenVerifier.Builder(netHttpTransport, gsonFactory)
        .setAudience(Collections.singletonList(googleConfiguration.clientId()))
        .build();
      var idToken = verifier.verify(token);
      if (idToken == null) {
        return CompletableFuture.completedFuture(Map.of("success", false));
      }
      var payload = idToken.getPayload();
      var email = payload.getEmail();
      return userDatabaseTable().userExists(email)
        .thenCompose(alreadyExists -> completeGoogleUserBinding(user, email,
          alreadyExists));
    } catch (Exception exception) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
  }

  private CompletableFuture<Map<String, Object>> completeGoogleUserBinding(
    User user, String email, boolean alreadyExists
  ) {
    if (alreadyExists) {
      return mergeUsers(user, email);
    }
    user.changeEmail(email);
    userDatabaseTable().updateUser(user);
    return CompletableFuture.completedFuture(bindingResponse(user));
  }

  private CompletableFuture<Map<String, Object>> mergeUsers(
    User sourceUser, String targetEmail
  ) {
    return userDatabaseTable().findUser(targetEmail)
      .thenCompose(targetUser -> mergeUsers(sourceUser, targetUser));
  }

  private CompletableFuture<Map<String, Object>> mergeUsers(
    User sourceUser, User targetUser
  ) {
    if (sourceUser.id().equals(targetUser.id())) {
      return CompletableFuture.completedFuture(bindingResponse(targetUser));
    }
    return userItemDatabaseTable.findUserItems(sourceUser.id())
      .thenCompose(this::findItems)
      .thenCompose(sourceCards -> userItemDatabaseTable
        .findUserItems(targetUser.id()).thenCompose(this::findItems)
        .thenCompose(targetCards -> findMergeCards(sourceCards, targetCards)
          .thenCompose(cards -> mergeUsers(sourceUser, targetUser,
            sourceCards, targetCards, cards))));
  }

  private CompletableFuture<List<Item>> findItems(List<UUID> itemIds) {
    return AsyncIterator.execute(itemIds, itemDatabaseTable::findItem);
  }

  private CompletableFuture<List<Card>> findMergeCards(
    List<Item> sourceItems, List<Item> targetItems
  ) {
    var cards = Lists.<Item>newArrayList();
    cards.addAll(sourceItems);
    cards.addAll(targetItems);
    var cardIds = cards.stream().map(Item::cardId).distinct().toList();
    return AsyncIterator.execute(cardIds, cardDatabaseTable::findCard);
  }

  private CompletableFuture<Map<String, Object>> mergeUsers(
    User sourceUser, User targetUser, List<Item> sourceItems,
    List<Item> targetItems, List<Card> cards
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    processes.add(mergeUserDevices(sourceUser, targetUser));
    processes.add(mergeUserItems(sourceUser, targetUser, sourceItems,
      targetItems, cards));
    processes.add(userDatabaseTable().deleteUser(sourceUser.id()));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> bindingResponse(targetUser));
  }

  private CompletableFuture<Void> mergeUserItems(
    User sourceUser, User targetUser, List<Item> sourceItems,
    List<Item> targetItems, List<Card> cards
  ) {
    return AsyncIterator.execute(sourceItems,
        item -> mergeUserItems(targetUser, item, targetItems, cards)
          .thenCompose(value -> itemDatabaseTable.deleteItem(item.itemId()))
          .thenCompose(value -> userItemDatabaseTable.deleteUserItem(
            sourceUser.id(), item.itemId())))
      .thenApply(value -> null);
  }

  private CompletableFuture<Void> mergeUserItems(
    User targetUser, Item sourceItem, List<Item> targetItems, List<Card> cards
  ) {
    var sourceCard = cards.stream()
      .filter(card -> card.id().equals(sourceItem.cardId())).findFirst().get();
    var targetItemOptional = targetItems.stream()
      .filter(entry -> entry.cardId().equals(sourceCard.id())).findFirst();
    if (targetItemOptional.isEmpty() || sourceCard.type().isValue()) {
      return itemDatabaseTable.generateAvailableItemId()
        .thenCompose(itemId -> itemDatabaseTable.insertItem(itemId,
          sourceItem.cardId(), sourceItem.content(), sourceItem.lastUpdate())
          .thenAccept(value -> userItemDatabaseTable.insertUserItem(
            targetUser.id(), itemId)));
    } else if (sourceCard.type().isCollection()) {
      var targetItem = targetItemOptional.get();
      var newCardContent = Map.of("stamps", Math.min(
        new JSONObject(sourceCard.content()).getInt("reward_number"),
        new JSONObject(targetItem.content()).getInt("stamps") +
          new JSONObject(targetItem.content()).getInt("stamps")));
      itemDatabaseTable.updateItem(targetItem.itemId(), targetItem.cardId(),
        new JSONObject(newCardContent).toString(), targetItem.lastUpdate());
    }
    return CompletableFuture.completedFuture(null);
  }

  private CompletableFuture<Void> mergeUserDevices(
    User sourceUser, User targetUser
  ) {
    return userDeviceDatabaseTable.findUserDevices(sourceUser.id())
      .thenCompose(devices -> AsyncIterator.execute(devices,
          device -> userDeviceDatabaseTable.transferUserDevice(device.id(),
            targetUser.id()))
        .thenApply(value -> null));
  }

  private Map<String, Object> bindingResponse(User user) {
    return Map.of("success", true, "email", user.email(),
      "user", user.id(), "authenticationKey", user.authenticationKey());
  }

  public CompletableFuture<User> signupUser(DulnoRequestBody body) {
    var legalAccepted = body.getBoolean("legalAccepted");
    if (!legalAccepted) {
      return CompletableFuture.completedFuture(null);
    }
    return userDatabaseTable().generateAvailableUserId()
      .thenCompose(userId -> userDeviceDatabaseTable.generateAvailableUserDeviceId()
        .thenCompose(deviceId -> signupUser(userId, deviceId,
          body.getString("language"), legalAccepted,
          body.getBoolean("legalAccepted"),
          body.getString("id"), body.getString("operatingSystem"),
          body.getString("operatingSystemVersion"), body.getString("brand"),
          body.getString("model"), body.getString("name"))));
  }

  public CompletableFuture<User> signupUser(
    UUID userId, UUID deviceId, String language, boolean compliant,
    boolean newsletter, String publicDeviceId, String operatingSystem,
    String operatingSystemVersion, String brand, String model, String name
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    var authenticationKey = generateAuthenticationKey();
    language = parseUserLanguage(language);
    var user = User.create(userId, authenticationKey, "", language, compliant,
      newsletter, System.currentTimeMillis());
    processes.add(userDatabaseTable().insertUser(user));
    processes.add(userDeviceDatabaseTable.insertUserDevice(deviceId, userId,
      publicDeviceId, operatingSystem, operatingSystemVersion, brand, model, name));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> user);
  }

  private String parseUserLanguage(String language) {
    if (language.equals("en") || language.equals("de")) {
      return language;
    }
    return "en";
  }
}