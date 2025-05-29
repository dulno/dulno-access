package com.dulno.access.user.bind;

import com.dulno.access.user.UserController;
import com.dulno.core.email.EmailValidation;
import com.dulno.core.locale.Translation;
import com.dulno.core.mail.Mail;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.change.UserEmailChange;
import com.dulno.core.user.change.UserEmailChangeDatabaseTable;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserEmailChangeController extends UserController {
  private final UserEmailChangeDatabaseTable userEmailChangeDatabaseTable;
  private final Translation translation;
  private final Mail verificationMail;
  private final UserVerificationCodeTemplate userVerificationCodeTemplate;

  private UserEmailChangeController(
    @Qualifier("appKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    UserEmailChangeDatabaseTable userEmailChangeDatabaseTable,
    Translation translation, @Qualifier("verificationMail") Mail verificationMail,
    UserVerificationCodeTemplate userVerificationCodeTemplate
  ) {
    super(secretKey, userDatabaseTable);
    this.userEmailChangeDatabaseTable = userEmailChangeDatabaseTable;
    this.translation = translation;
    this.verificationMail = verificationMail;
    this.userVerificationCodeTemplate = userVerificationCodeTemplate;
  }

  @RequestMapping(path = "/user/email/change/request/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> requestUserEmailChange(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var email = body.getString("email");
    if (!EmailValidation.validate(email)) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    return findUser(request, response)
      .thenApply(user -> requestUserEmailChange(user, email));
  }

  private Map<String, Object> requestUserEmailChange(
    User user, String email
  ) {
    if (user == null) {
      return Maps.newHashMap();
    }
    var verification = UserVerificationCode.create(userVerificationCodeTemplate,
      translation, user.language());
    verification.generate();
    userEmailChangeDatabaseTable.insertUserEmailChange(user.id(), email,
      verification.code());
    var title = translation.translate(user.language(),
      "user.verification.email.title");
    verificationMail.send(email, user.language(), title, verification.content());
    return Map.of("success", true);
  }

  @RequestMapping(path = "/user/email/change/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> completeUserEmailChange(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var code = body.getString("code");
    return findUser(request, response)
      .thenCompose(user -> completeUserEmailChange(user, code));
  }

  private CompletableFuture<Map<String, Object>> completeUserEmailChange(
    User user, String code
  ) {
    if (user == null) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return userEmailChangeDatabaseTable.userEmailChangeExists(user.id())
      .thenCompose(exists -> completeUserEmailChange(user, code, exists));
  }

  private CompletableFuture<Map<String, Object>> completeUserEmailChange(
    User user, String code, boolean emailChangeExists
  ) {
    if (!emailChangeExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    return userEmailChangeDatabaseTable.findUserEmailChange(user.id())
      .thenApply(emailChange -> completeUserEmailChange(user, code, emailChange));
  }

  private Map<String, Object> completeUserEmailChange(
    User user, String code, UserEmailChange emailChange
  ) {
    if (!emailChange.code().equals(code)) {
      return Map.of("success", false, "error", 1001);
    }
    userEmailChangeDatabaseTable.deleteUserEmailChange(user.id());
    user.changeEmail(emailChange.email());
    userDatabaseTable().updateUser(user);
    return Map.of("success", true, "email", emailChange.email());
  }
}
