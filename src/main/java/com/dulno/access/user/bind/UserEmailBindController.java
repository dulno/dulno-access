package com.dulno.access.user.bind;

import com.dulno.access.user.UserController;
import com.dulno.core.email.EmailValidation;
import com.dulno.core.locale.Translation;
import com.dulno.core.mail.Mail;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.verification.UserVerification;
import com.dulno.core.user.verification.UserVerificationDatabaseTable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserEmailBindController extends UserController {
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final Translation translation;
  private final Mail verificationMail;
  private final UserBindSignup userBindSignup;
  private final UserBindMerge userBindMerge;
  private final UserBindCompletion userBindCompletion;

  private UserEmailBindController(
    @Qualifier("appKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    UserVerificationDatabaseTable userVerificationDatabaseTable,
    Translation translation, @Qualifier("verificationMail") Mail verificationMail,
    UserBindSignup userBindSignup, UserBindMerge userBindMerge,
    UserBindCompletion userBindCompletion
  ) {
    super(secretKey, userDatabaseTable);
    this.userVerificationDatabaseTable = userVerificationDatabaseTable;
    this.translation = translation;
    this.verificationMail = verificationMail;
    this.userBindSignup = userBindSignup;
    this.userBindMerge = userBindMerge;
    this.userBindCompletion = userBindCompletion;
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
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var userId = body.getUUID("user");
    return userVerificationDatabaseTable.userVerificationExists(userId)
      .thenCompose(exists -> completeUserBinding(request, body, userId,
        body.getString("code"), exists));
  }

  private CompletableFuture<Map<String, Object>> completeUserBinding(
    HttpServletRequest request, DulnoRequestBody body, UUID userId, String code,
    boolean verificationExists
  ) {
    if (!verificationExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    return userVerificationDatabaseTable.findUserVerification(userId)
      .thenCompose(verification -> userDatabaseTable().userExists(userId)
        .thenCompose(userExists -> completeUserBinding(request, body, userId,
          code, verification, userExists)));
  }

  private CompletableFuture<Map<String, Object>> completeUserBinding(
    HttpServletRequest request, DulnoRequestBody body, UUID userId, String code,
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
          completeUserBinding(request, user, body, true));
    }
    return userDatabaseTable().findUser(userId)
      .thenCompose(user -> completeUserBinding(request, user, body, false));
  }

  private CompletableFuture<Map<String, Object>> completeUserBinding(
    HttpServletRequest request, User user, DulnoRequestBody body,
    boolean isNewUser
  ) {
    userVerificationDatabaseTable.deleteUserVerification(user.id());
    if (isNewUser) {
      return userBindCompletion.completeBinding(request, user);
    }
    return userBindMerge.mergeUserItems(user, body)
      .thenCompose(value -> userBindCompletion.completeBinding(request, user));
  }
}
