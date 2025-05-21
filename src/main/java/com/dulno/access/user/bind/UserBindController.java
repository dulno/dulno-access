package com.dulno.access.user.bind;

import com.dulno.access.user.UserController;
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
import com.dulno.core.user.verification.UserVerification;
import com.dulno.core.user.verification.UserVerificationDatabaseTable;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserBindController extends UserController {
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final Translation translation;
  private final Mail verificationMail;
  private final UserBindSignup userBindSignup;
  private final UserBindMerge userBindMerge;
  private final GoogleConfiguration googleConfiguration;
  private final GsonFactory gsonFactory = GsonFactory.getDefaultInstance();
  private final NetHttpTransport netHttpTransport = new NetHttpTransport();

  private UserBindController(
    UserDatabaseTable userDatabaseTable, Hashing hashing,
    DulnoEnvironment environment, ErrorRepository errorRepository,
    UserVerificationDatabaseTable userVerificationDatabaseTable,
    Translation translation, @Qualifier("verificationMail") Mail verificationMail,
    UserBindSignup userBindSignup, UserBindMerge userBindMerge,
    GoogleConfiguration googleConfiguration
  ) {
    super(userDatabaseTable, hashing, environment, errorRepository);
    this.userVerificationDatabaseTable = userVerificationDatabaseTable;
    this.translation = translation;
    this.verificationMail = verificationMail;
    this.userBindSignup = userBindSignup;
    this.userBindMerge = userBindMerge;
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
      return userBindSignup.generateAvailableUserId()
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
      return userBindSignup.signupUser(userId, verification.email(), body)
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
    return userBindMerge.mergeUserItems(user, body)
      .thenApply(value -> bindingResponse(user));
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
      return userBindSignup.signupUser(email, body).exceptionally(user -> null)
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
    return userBindMerge.mergeUserItems(user, body)
      .thenApply(value -> bindingResponse(user));
  }

  private Map<String, Object> bindingResponse(User user) {
    return Map.of("success", true, "email", user.email(),
      "user", user.id(), "authenticationKey", user.authenticationKey());
  }

  @RequestMapping(path = "/user/authorized/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> isUserAuthorized(
    HttpServletRequest request
  ) {
    return findUser(request).exceptionally(throwable -> null)
      .thenApply(user -> Map.of("authorized", user != null));
  }
}