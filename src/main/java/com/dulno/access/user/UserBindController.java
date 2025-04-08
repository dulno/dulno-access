package com.dulno.access.user;

import com.dulno.core.email.EmailValidation;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.google.GoogleConfiguration;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.locale.Translation;
import com.dulno.core.mail.Mail;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserVerification;
import com.dulno.core.user.UserVerificationDatabaseTable;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserBindController extends UserController {
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final Translation translation;
  private final Mail verificationMail;
  private final GoogleConfiguration googleConfiguration;
  private final GsonFactory gsonFactory = GsonFactory.getDefaultInstance();
  private final NetHttpTransport netHttpTransport = new NetHttpTransport();

  private UserBindController(
    UserDatabaseTable userDatabaseTable, Hashing hashing,
    DulnoEnvironment environment, ErrorRepository errorRepository,
    UserVerificationDatabaseTable userVerificationDatabaseTable,
    Translation translation, @Qualifier("verificationMail") Mail verificationMail,
    GoogleConfiguration googleConfiguration
  ) {
    super(userDatabaseTable, hashing, environment, errorRepository);
    this.userVerificationDatabaseTable = userVerificationDatabaseTable;
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
      .thenCompose(user -> userDatabaseTable().userExists(email)
        .thenApply(alreadyExists -> requestUserBinding(user, email,
          body.getBoolean("newsletter"), alreadyExists)))
      .exceptionally(throwable -> Maps.newHashMap());
  }

  private Map<String, Object> requestUserBinding(
    User user, String email, boolean newsletter, boolean alreadyExists
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
    return Map.of("success", true, "alreadyExists", alreadyExists);
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
      return userDatabaseTable().findUser(verification.email())
        .thenApply(targetUser -> Map.of("success", true, "user", targetUser.id(),
          "authenticationKey", targetUser.authenticationKey()));
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
      return userDatabaseTable().findUser(email)
        .thenApply(targetUser -> Map.of("success", true, "email", email,
          "user", targetUser.id(),
          "authenticationKey", targetUser.authenticationKey()));
    }
    userDatabaseTable().changeUserEmail(user.id(), email);
    return CompletableFuture.completedFuture(Map.of("success", true, "email", email));
  }
}