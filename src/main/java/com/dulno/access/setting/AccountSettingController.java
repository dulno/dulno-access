package com.dulno.access.setting;

import com.dulno.access.password.PasswordController;
import com.dulno.access.ticket.TicketModificationController;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.locale.Translation;
import com.dulno.core.mail.Mail;
import com.dulno.core.notification.NotificationDatabaseTable;
import com.dulno.core.session.SessionDatabaseTable;
import com.dulno.core.ticket.TicketDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserEmailChangeDatabaseTable;
import com.dulno.core.user.UserPasswordResetDatabaseTable;
import com.dulno.core.user.mfa.MultiFactorAuthDatabaseTable;
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
public final class AccountSettingController extends SettingController {
  private final Mail changeMail;
  private final Translation translation;
  private final PasswordController passwordController;
  private final UserPasswordResetDatabaseTable userPasswordResetDatabaseTable;
  private final UserEmailChangeDatabaseTable userEmailChangeDatabaseTable;
  private final NotificationDatabaseTable notificationDatabaseTable;
  private final MultiFactorAuthDatabaseTable multiFactorAuthDatabaseTable;
  private final TicketDatabaseTable ticketDatabaseTable;
  private final TicketModificationController ticketModificationController;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final SessionDatabaseTable sessionDatabaseTable;
  private final Hashing hashing;
  private final DulnoEnvironment environment;

  private AccountSettingController(
    @Qualifier("productKey") Key productKey, @Qualifier("homeKey") Key homeKey,
    UserDatabaseTable userDatabaseTable, @Qualifier("changeMail") Mail changeMail,
    Translation translation, PasswordController passwordController,
    UserPasswordResetDatabaseTable userPasswordResetDatabaseTable,
    UserEmailChangeDatabaseTable userEmailChangeDatabaseTable,
    NotificationDatabaseTable notificationDatabaseTable,
    MultiFactorAuthDatabaseTable multiFactorAuthDatabaseTable,
    TicketDatabaseTable ticketDatabaseTable,
    TicketModificationController ticketModificationController,
    BundleDatabaseTable bundleDatabaseTable,
    SessionDatabaseTable sessionDatabaseTable, Hashing hashing,
    DulnoEnvironment environment
  ) {
    super(productKey, homeKey, userDatabaseTable);
    this.changeMail = changeMail;
    this.translation = translation;
    this.passwordController = passwordController;
    this.userPasswordResetDatabaseTable = userPasswordResetDatabaseTable;
    this.userEmailChangeDatabaseTable = userEmailChangeDatabaseTable;
    this.notificationDatabaseTable = notificationDatabaseTable;
    this.multiFactorAuthDatabaseTable = multiFactorAuthDatabaseTable;
    this.ticketDatabaseTable = ticketDatabaseTable;
    this.ticketModificationController = ticketModificationController;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.sessionDatabaseTable = sessionDatabaseTable;
    this.hashing = hashing;
    this.environment = environment;
  }

  @RequestMapping(path = "/settings/account/unlocked/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> accountSettingsUnlocked(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> futureResponse.complete(
      Map.of("unlocked", !user.passwordHash().equals(""))));
    return futureResponse;
  }

  @RequestMapping(path = "/settings/account/unlock/", method = RequestMethod.GET)
  public void unlockAccountSettings(
    HttpServletRequest request
  ) {
    findUser(request).thenAccept(user ->
      passwordController.requestPasswordReset(user, "password.set.email.title",
        "password.set.email.body"));
  }

  @RequestMapping(path = "/settings/account/password/change/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> changePassword(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> futureResponse.complete(
      changePassword(user, body.getString("currentPassword"),
        body.getString("newPassword"))));
    return futureResponse;
  }

  private Map<String, Object> changePassword(
    User user, String currentPassword, String newPassword
  ) {
    if (!hashing.matches(currentPassword, user.passwordHash())) {
      return Map.of("success", false);
    }
    userDatabaseTable().changeUserPassword(user.id(), hashing.hash(newPassword));
    return Map.of("success", true);
  }

  @RequestMapping(path = "/settings/account/email/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findEmail(
    HttpServletRequest request
  ) {
    return findUser(request).thenApply(user -> Map.of("email", user.email()));
  }

  @RequestMapping(path = "/settings/account/email/change/request/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> requestEmailChange(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var newEmail = body.getString("newEmail");
    findUser(request).thenAccept(user -> userDatabaseTable().userExists(newEmail)
      .thenAccept(exists -> requestEmailChange(user, body.getString("password"),
        newEmail, exists).thenAccept(futureResponse::complete)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> requestEmailChange(
    User user, String password, String newEmail, boolean accountExists
  ) {
    if (!hashing.matches(password, user.passwordHash())) {
      return CompletableFuture.completedFuture(Map.of("success", false, "errorCode", 1000));
    }
    if (accountExists) {
      return CompletableFuture.completedFuture(Map.of("success", false, "errorCode", 1001));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    userEmailChangeDatabaseTable.changeExists(user.id()).thenAccept(requestExists ->
      futureResponse.complete(requestEmailChange(user, newEmail, requestExists)));
    return futureResponse;
  }

  private static final String EMAIL_CHANGE_URL = "https://%s/email/change/complete/%s/%s/";

  private Map<String, Object> requestEmailChange(
    User user, String newEmail, boolean requestExists
  ) {
    var token = UUID.randomUUID().toString();
    if (requestExists) {
      userEmailChangeDatabaseTable.updateChange(user.id(), newEmail, token);
    } else {
      userEmailChangeDatabaseTable.insertChange(user.id(), newEmail, token);
    }
    var title = translation.translate(user, "email.change.email.title");
    var body = String.format(
      translation.translate(user, "email.change.email.body"),
      String.format(EMAIL_CHANGE_URL, environment.domain(), user.id().toString(),
        token));
    changeMail.send(newEmail, user.language(), title, body);
    return Map.of("success", true);
  }

  @RequestMapping(path = "/settings/account/email/change/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> completeEmailChange(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var userId = body.getUUID("user");
    userEmailChangeDatabaseTable.changeExists(userId).thenAccept(exists ->
      completeEmailChange(userId, body.getString("token"), exists)
        .thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> completeEmailChange(
    UUID userId, String token, boolean exists
  ) {
    if (!exists) {
      return CompletableFuture.completedFuture(Map.of("success", false, "errorCode", 1000));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    userEmailChangeDatabaseTable.findChange(userId).thenAccept(changeParameters ->
      completeEmailChange(userId, token, changeParameters).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> completeEmailChange(
    UUID userId, String token, Map.Entry<String, String> changeParameters
  ) {
    if (!token.equals(changeParameters.getValue())) {
      return CompletableFuture.completedFuture(Map.of("success", false, "errorCode", 1001));
    }
    userEmailChangeDatabaseTable.deleteChange(userId);
    userDatabaseTable().changeUserEmail(userId, changeParameters.getKey());
    return CompletableFuture.completedFuture(Map.of("success", true));
  }

  @RequestMapping(path = "/settings/account/delete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> deleteAccount(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> futureResponse.complete(
      deleteAccount(user, body.getString("password"))));
    return futureResponse;
  }

  private Map<String, Object> deleteAccount(
    User user, String password
  ) {
    if (!hashing.matches(password, user.passwordHash())) {
      return Map.of("success", false);
    }
    deleteAccount(user);
    return Map.of("success", true);
  }

  public void deleteAccount(User user) {
    //TODO: DELETE / CANCEL STRIPE & BUNDLE
    userDatabaseTable().deleteUser(user.id());
    userPasswordResetDatabaseTable.deleteResetToken(user.id());
    userEmailChangeDatabaseTable.deleteChange(user.id());
    notificationDatabaseTable.deleteNotificationSettings(user.id());
    multiFactorAuthDatabaseTable.deleteAuth(user.id());
    ticketDatabaseTable.findAllTicketsOfCreator(user.id()).thenAccept(tickets ->
      tickets.forEach(ticketModificationController::deleteTicket));
    sessionDatabaseTable.findSessionsOfUser(user.id()).thenAccept(sessions ->
      sessions.forEach(session -> sessionDatabaseTable.deleteSession(session.id())));
  }
}
