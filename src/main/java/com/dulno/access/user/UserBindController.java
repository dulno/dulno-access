package com.dulno.access.user;

import com.dulno.core.email.EmailValidation;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.locale.Translation;
import com.dulno.core.mail.Mail;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserVerification;
import com.dulno.core.user.UserVerificationDatabaseTable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserBindController extends UserController {
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final Translation translation;
  private final Mail verificationMail;

  private UserBindController(
    UserDatabaseTable userDatabaseTable, Hashing hashing,
    UserVerificationDatabaseTable userVerificationDatabaseTable,
    Translation translation, @Qualifier("verificationMail") Mail verificationMail
  ) {
    super(userDatabaseTable, hashing);
    this.userVerificationDatabaseTable = userVerificationDatabaseTable;
    this.translation = translation;
    this.verificationMail = verificationMail;
  }

  @RequestMapping(path = "/user/binding/find/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findUserBinding(
    HttpServletRequest request
  ) {
    return findUser(request).thenApply(user -> user.email().isEmpty() ?
      Map.of("bound", false) : Map.of("bound", true, "email", user.email()));
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
          body.getBoolean("newsletter"), alreadyExists)));
  }

  private Map<String, Object> requestUserBinding(
    User user, String email, boolean newsletter, boolean alreadyExists
    ) {
    if (alreadyExists) {
      return Map.of("success", false, "error", 1001);
    }
    if (!user.email().isEmpty()) {
      return Map.of("success", false, "error", 1002);
    }
    var verification = UserVerificationCode.create(translation, user);
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
          body.getString("code"), exists)));
  }

  private CompletableFuture<Map<String, Object>> completeUserBinding(
    User user, String code, boolean verificationExists
  ) {
    if (!verificationExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    return userVerificationDatabaseTable.findUserVerification(user.id())
      .thenApply(verification -> completeUserBinding(user, code, verification));
  }

  private Map<String, Object> completeUserBinding(
    User user, String code, UserVerification verification
  ) {
    if (!verification.code().equals(code)) {
      return Map.of("success", false, "error", 1001);
    }
    userDatabaseTable().changeUserEmail(user.id(), verification.email());
    userVerificationDatabaseTable.deleteUserVerification(user.id());
    return Map.of("success", true);
  }

  @RequestMapping(path = "/user/bind/google/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> bindGoogleUser(
    HttpServletRequest request, @RequestParam("state") String state,
    @RequestParam("code") String code, HttpServletResponse response
  ) {
    //TODO: IMPLEMENT
    return null;
  }
}