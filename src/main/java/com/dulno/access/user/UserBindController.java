package com.dulno.access.user;

import com.datastax.oss.driver.shaded.guava.common.collect.Lists;
import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.email.EmailValidation;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.google.GoogleConfiguration;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.locale.Translation;
import com.dulno.core.mail.Mail;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.*;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserBindController extends UserController {
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final UserCardDatabaseTable userCardDatabaseTable;
  private final UserDeviceDatabaseTable userDeviceDatabaseTable;
  private final UserFirebaseDatabaseTable userFirebaseDatabaseTable;
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
    UserCardDatabaseTable userCardDatabaseTable,
    UserDeviceDatabaseTable userDeviceDatabaseTable,
    UserFirebaseDatabaseTable userFirebaseDatabaseTable,
    CardDatabaseTable cardDatabaseTable, Translation translation,
    @Qualifier("verificationMail") Mail verificationMail,
    GoogleConfiguration googleConfiguration
  ) {
    super(userDatabaseTable, hashing, environment, errorRepository);
    this.userVerificationDatabaseTable = userVerificationDatabaseTable;
    this.userCardDatabaseTable = userCardDatabaseTable;
    this.userDeviceDatabaseTable = userDeviceDatabaseTable;
    this.userFirebaseDatabaseTable = userFirebaseDatabaseTable;
    this.cardDatabaseTable = cardDatabaseTable;
    this.translation = translation;
    this.verificationMail = verificationMail;
    this.googleConfiguration = googleConfiguration;
  }

  @RequestMapping(path = "/user/binding/find/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findUserBinding(
    HttpServletRequest request
  ) {
    return findUser(request).thenApply(this::findUserBinding)
      .exceptionally(throwable -> Maps.newHashMap());
  }

  private Map<String, Object> findUserBinding(User user) {
    if (user.email().isEmpty()) {
      return Map.of("bound", false);
    }
    return Map.of("bound", true, "email", user.email());
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
    return findUser(request)
      .thenApply(user -> requestUserBinding(user, email,
        body.getBoolean("newsletter")))
      .exceptionally(throwable -> Maps.newHashMap());
  }

  private Map<String, Object> requestUserBinding(
    User user, String email, boolean newsletter
  ) {
    var verification = UserVerificationCode.create(translation, user);
    verification.generate();
    userVerificationDatabaseTable.insertUserVerification(user.id(), email,
      verification.code());
    if (!user.newsletter()) {
      userDatabaseTable().changeUserNewsletter(user.id(), newsletter);
    }
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
      .exceptionally(throwable -> Maps.newHashMap());
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
    userDatabaseTable().changeUserEmail(user.id(), verification.email());
    return CompletableFuture.completedFuture(Map.of("success", true));
  }

  @RequestMapping(path = "/user/bind/google/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> bindGoogleUser(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    return findUser(request)
      .thenCompose(user -> bindGoogleUser(user, body.getString("token")))
      .exceptionally(throwable -> Maps.newHashMap());
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
    userDatabaseTable().changeUserEmail(user.id(), email);
    return CompletableFuture.completedFuture(Map.of("success", true, "email", email));
  }

  private CompletableFuture<Map<String, Object>> mergeUsers(
    User sourceUser, String targetEmail
  ) {
    return userDatabaseTable().findUser(targetEmail)
      .thenCompose(targetUser -> userCardDatabaseTable
        .findUserCards(sourceUser.id())
        .thenCompose(sourceCards -> userCardDatabaseTable
          .findUserCards(targetUser.id())
          .thenCompose(targetCards -> findMergeCards(sourceCards, targetCards)
            .thenCompose(cards -> mergeUsers(sourceUser, targetUser,
              sourceCards, targetCards, cards)))));
  }

  private CompletableFuture<List<Card>> findMergeCards(
    List<UserCard> sourceCards, List<UserCard> targetCards
  ) {
    var cards = Lists.<UserCard>newArrayList();
    cards.addAll(sourceCards);
    cards.addAll(targetCards);
    var cardIds = cards.stream().map(UserCard::cardId).distinct().toList();
    return AsyncIterator.execute(cardIds, cardDatabaseTable::findCard);
  }

  private CompletableFuture<Map<String, Object>> mergeUsers(
    User sourceUser, User targetUser, List<UserCard> sourceCards,
    List<UserCard> targetCards, List<Card> cards
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    processes.add(mergeUserDevices(sourceUser, targetUser));
    processes.add(mergerUserCards(sourceUser, targetUser, sourceCards,
      targetCards, cards));
    processes.add(userDatabaseTable().deleteUser(sourceUser.id()));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> Map.of("success", true, "email", targetUser.email(),
        "user", targetUser.id(), "authenticationKey",
        targetUser.authenticationKey()));
  }

  private CompletableFuture<Void> mergerUserCards(
    User sourceUser, User targetUser, List<UserCard> sourceCards,
    List<UserCard> targetCards, List<Card> cards
  ) {
    return AsyncIterator.execute(sourceCards,
        card -> mergeUserCard(targetUser, card, targetCards, cards)
          .thenCompose(value -> userCardDatabaseTable
            .deleteUserCard(sourceUser.id(), card.cardId(), card.userCardId())))
      .thenApply(value -> null);
  }

  private CompletableFuture<Void> mergeUserCard(
    User targetUser, UserCard sourceUserCard, List<UserCard> targetUserCards,
    List<Card> cards
  ) {
    var sourceCard = cards.stream()
      .filter(card -> card.id().equals(sourceUserCard.cardId())).findFirst().get();
    var targetUserCardOptional = targetUserCards.stream()
      .filter(entry -> entry.cardId().equals(sourceCard.id())).findFirst();
    if (targetUserCardOptional.isEmpty() || sourceCard.type().isValue()) {
      return userCardDatabaseTable
        .generateAvailableUserId(targetUser.id(), sourceCard.id())
        .thenCompose(userCardId -> userCardDatabaseTable
          .insertUserCard(targetUser.id(), sourceCard.id(), userCardId,
            sourceUserCard.content(), sourceUserCard.lastUpdate()));
    } else if (sourceCard.type().isCollection()) {
      var targetUserCard = targetUserCardOptional.get();
      var newCardContent = Map.of("stamps", Math.min(
        new JSONObject(sourceCard.content()).getInt("reward_number"),
        new JSONObject(sourceUserCard.content()).getInt("stamps") +
          new JSONObject(targetUserCard.content()).getInt("stamps")));
      userCardDatabaseTable.updateUserCardContent(targetUser.id(), sourceCard.id(),
        targetUserCard.userCardId(), new JSONObject(newCardContent).toString(),
        targetUserCard.lastUpdate());
    }
    return CompletableFuture.completedFuture(null);
  }

  private CompletableFuture<Void> mergeUserDevices(
    User sourceUser, User targetUser
  ) {
    return userDeviceDatabaseTable.findUserDevices(sourceUser.id())
      .thenCompose(devices -> AsyncIterator.execute(devices,
          device -> mergeUserDevice(sourceUser, targetUser, device))
        .thenApply(value -> null));
  }

  private CompletableFuture<Void> mergeUserDevice(
    User sourceUser, User targetUser, UserDevice device
  ) {
    return userDeviceDatabaseTable.transferUserDevice(device.id(), targetUser.id())
      .thenCompose(value -> userFirebaseDatabaseTable
        .findUserDeviceFirebaseIdentifier(sourceUser.id(), device.id())
        .thenCompose(firebaseToken -> userFirebaseDatabaseTable
          .insertUserFirebase(targetUser.id(), device.id(), firebaseToken)))
      .thenCompose(value -> userFirebaseDatabaseTable
        .deleteUserFirebase(sourceUser.id(), device.id()));
  }
}