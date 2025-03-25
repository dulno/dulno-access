package com.dulno.access.portal.setting;

import com.dulno.access.portal.password.PasswordController;
import com.dulno.access.portal.ticket.TicketModificationController;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.locale.Translation;
import com.dulno.core.mail.Mail;
import com.dulno.core.member.Member;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.member.MemberEmailChangeDatabaseTable;
import com.dulno.core.member.MemberPasswordResetDatabaseTable;
import com.dulno.core.member.mfa.MultiFactorAuthDatabaseTable;
import com.dulno.core.member.session.SessionDatabaseTable;
import com.dulno.core.ticket.TicketDatabaseTable;
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
  private final MemberPasswordResetDatabaseTable memberPasswordResetDatabaseTable;
  private final MemberEmailChangeDatabaseTable memberEmailChangeDatabaseTable;
  private final MultiFactorAuthDatabaseTable multiFactorAuthDatabaseTable;
  private final TicketDatabaseTable ticketDatabaseTable;
  private final TicketModificationController ticketModificationController;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final SessionDatabaseTable sessionDatabaseTable;
  private final Hashing hashing;
  private final DulnoEnvironment environment;

  private AccountSettingController(
    @Qualifier("productKey") Key productKey, @Qualifier("homeKey") Key homeKey,
    MemberDatabaseTable memberDatabaseTable, @Qualifier("changeMail") Mail changeMail,
    Translation translation, PasswordController passwordController,
    MemberPasswordResetDatabaseTable memberPasswordResetDatabaseTable,
    MemberEmailChangeDatabaseTable memberEmailChangeDatabaseTable,
    MultiFactorAuthDatabaseTable multiFactorAuthDatabaseTable,
    TicketDatabaseTable ticketDatabaseTable,
    TicketModificationController ticketModificationController,
    BundleDatabaseTable bundleDatabaseTable,
    SessionDatabaseTable sessionDatabaseTable, Hashing hashing,
    DulnoEnvironment environment
  ) {
    super(productKey, homeKey, memberDatabaseTable);
    this.changeMail = changeMail;
    this.translation = translation;
    this.passwordController = passwordController;
    this.memberPasswordResetDatabaseTable = memberPasswordResetDatabaseTable;
    this.memberEmailChangeDatabaseTable = memberEmailChangeDatabaseTable;
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
    findMember(request).thenAccept(member -> futureResponse.complete(
      Map.of("unlocked", !member.passwordHash().equals(""))));
    return futureResponse;
  }

  @RequestMapping(path = "/settings/account/unlock/", method = RequestMethod.GET)
  public void unlockAccountSettings(
    HttpServletRequest request
  ) {
    findMember(request).thenAccept(member ->
      passwordController.requestPasswordReset(member, "password.set.email.title",
        "password.set.email.body"));
  }

  @RequestMapping(path = "/settings/account/password/change/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> changePassword(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findMember(request).thenAccept(member -> futureResponse.complete(
      changePassword(member, body.getString("currentPassword"),
        body.getString("newPassword"))));
    return futureResponse;
  }

  private Map<String, Object> changePassword(
    Member member, String currentPassword, String newPassword
  ) {
    if (!hashing.matches(currentPassword, member.passwordHash())) {
      return Map.of("success", false);
    }
    memberDatabaseTable().changeMemberPassword(member.id(), hashing.hash(newPassword));
    return Map.of("success", true);
  }

  @RequestMapping(path = "/settings/account/email/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findEmail(
    HttpServletRequest request
  ) {
    return findMember(request).thenApply(member -> Map.of("email", member.email()));
  }

  @RequestMapping(path = "/settings/account/email/change/request/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> requestEmailChange(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var newEmail = body.getString("newEmail");
    findMember(request).thenAccept(member -> memberDatabaseTable().memberExists(newEmail)
      .thenAccept(exists -> requestEmailChange(member, body.getString("password"),
        newEmail, exists).thenAccept(futureResponse::complete)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> requestEmailChange(
    Member member, String password, String newEmail, boolean accountExists
  ) {
    if (!hashing.matches(password, member.passwordHash())) {
      return CompletableFuture.completedFuture(Map.of("success", false, "errorCode", 1000));
    }
    if (accountExists) {
      return CompletableFuture.completedFuture(Map.of("success", false, "errorCode", 1001));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    memberEmailChangeDatabaseTable.changeExists(member.id()).thenAccept(requestExists ->
      futureResponse.complete(requestEmailChange(member, newEmail, requestExists)));
    return futureResponse;
  }

  private static final String EMAIL_CHANGE_URL = "https://%s/email/change/complete/%s/%s/";

  private Map<String, Object> requestEmailChange(
    Member member, String newEmail, boolean requestExists
  ) {
    var token = UUID.randomUUID().toString();
    if (requestExists) {
      memberEmailChangeDatabaseTable.updateChange(member.id(), newEmail, token);
    } else {
      memberEmailChangeDatabaseTable.insertChange(member.id(), newEmail, token);
    }
    var title = translation.translate(member, "email.change.email.title");
    var body = String.format(
      translation.translate(member, "email.change.email.body"),
      String.format(EMAIL_CHANGE_URL, environment.domain(), member.id().toString(),
        token));
    changeMail.send(newEmail, member.language(), title, body);
    return Map.of("success", true);
  }

  @RequestMapping(path = "/settings/account/email/change/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> completeEmailChange(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var memberId = body.getUUID("member");
    memberEmailChangeDatabaseTable.changeExists(memberId).thenAccept(exists ->
      completeEmailChange(memberId, body.getString("token"), exists)
        .thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> completeEmailChange(
    UUID memberId, String token, boolean exists
  ) {
    if (!exists) {
      return CompletableFuture.completedFuture(Map.of("success", false, "errorCode", 1000));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    memberEmailChangeDatabaseTable.findChange(memberId).thenAccept(changeParameters ->
      completeEmailChange(memberId, token, changeParameters).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> completeEmailChange(
    UUID memberId, String token, Map.Entry<String, String> changeParameters
  ) {
    if (!token.equals(changeParameters.getValue())) {
      return CompletableFuture.completedFuture(Map.of("success", false, "errorCode", 1001));
    }
    memberEmailChangeDatabaseTable.deleteChange(memberId);
    memberDatabaseTable().changeMemberEmail(memberId, changeParameters.getKey());
    return CompletableFuture.completedFuture(Map.of("success", true));
  }

  @RequestMapping(path = "/settings/account/delete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> deleteAccount(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findMember(request).thenAccept(member -> futureResponse.complete(
      deleteAccount(member, body.getString("password"))));
    return futureResponse;
  }

  private Map<String, Object> deleteAccount(
    Member member, String password
  ) {
    if (!hashing.matches(password, member.passwordHash())) {
      return Map.of("success", false);
    }
    deleteAccount(member);
    return Map.of("success", true);
  }

  public void deleteAccount(Member member) {
    //TODO: DELETE / CANCEL STRIPE & BUNDLE
    memberDatabaseTable().deleteMember(member.id());
    memberPasswordResetDatabaseTable.deleteResetToken(member.id());
    memberEmailChangeDatabaseTable.deleteChange(member.id());
    multiFactorAuthDatabaseTable.deleteAuth(member.id());
    ticketDatabaseTable.findAllTicketsOfCreator(member.id()).thenAccept(tickets ->
      tickets.forEach(ticketModificationController::deleteTicket));
    sessionDatabaseTable.findSessionsOfMember(member.id()).thenAccept(sessions ->
      sessions.forEach(session -> sessionDatabaseTable.deleteSession(session.id())));
  }
}
