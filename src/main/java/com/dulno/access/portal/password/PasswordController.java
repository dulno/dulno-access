package com.dulno.access.portal.password;

import com.dulno.access.portal.PortalRestController;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.locale.Translation;
import com.dulno.core.mail.Mail;
import com.dulno.core.member.Member;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.member.MemberPasswordResetDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
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
public final class PasswordController extends PortalRestController {
  private final Mail changeMail;
  private final MemberPasswordResetDatabaseTable memberPasswordResetDatabaseTable;
  private final Translation translation;
  private final Hashing hashing;
  private final DulnoEnvironment environment;

  private PasswordController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    @Qualifier("changeMail") Mail changeMail,
    MemberPasswordResetDatabaseTable memberPasswordResetDatabaseTable,
    Translation translation, Hashing hashing, DulnoEnvironment environment
  ) {
    super(secretKey, memberDatabaseTable);
    this.changeMail = changeMail;
    this.memberPasswordResetDatabaseTable = memberPasswordResetDatabaseTable;
    this.translation = translation;
    this.hashing = hashing;
    this.environment = environment;
  }

  @RequestMapping(path = "/password/reset/request/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> requestPasswordReset(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var email = body.getString("email");
    memberDatabaseTable().memberExists(email).thenAccept(exists ->
      requestPasswordReset(email, exists).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> requestPasswordReset(
    String email, boolean exists
  ) {
    if (!exists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    return memberDatabaseTable().findMember(email)
      .thenCompose(member -> requestPasswordReset(member,
        "password.reset.email.title", "password.reset.email.body"));
  }

  public CompletableFuture<Map<String, Object>> requestPasswordReset(
    Member member, String emailTitle, String emailBody
  ) {
    return memberPasswordResetDatabaseTable.resetTokenExists(member.id())
      .thenCompose(requestExists -> requestPasswordReset(member, emailTitle,
        emailBody, requestExists));
  }

  private CompletableFuture<Map<String, Object>> requestPasswordReset(
    Member member, String emailTitle, String emailBody, boolean requestExists
  ) {
    if (requestExists) {
      return memberPasswordResetDatabaseTable.findResetToken(member.id())
        .thenApply(token -> sendPasswordResetMail(member, token, emailTitle,
          emailBody));
    }
    var token = UUID.randomUUID().toString();
    memberPasswordResetDatabaseTable.insertResetToken(member.id(), token);
    return CompletableFuture.completedFuture(sendPasswordResetMail(member, token,
      emailTitle, emailBody));
  }

  private static final String PASSWORD_RESET_URL = "https://portal.%s/password/reset/complete/%s/%s/";

  private Map<String, Object> sendPasswordResetMail(
    Member member, String token, String emailTitle, String emailBody
  ) {
    var title = translation.translateMember(member, emailTitle);
    var body = String.format(translation.translateMember(member, emailBody), member.name(),
      String.format(PASSWORD_RESET_URL, environment.domain(),
        member.id().toString(), token));
    changeMail.send(member, title, body);
    return Map.of("success", true);
  }

  @RequestMapping(path = "/password/reset/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> completePasswordReset(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var memberId = body.getUUID("member");
    memberPasswordResetDatabaseTable.resetTokenExists(memberId).thenAccept(exists ->
      completePasswordReset(memberId, body.getString("token"), exists,
        body.getString("newPassword")).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> completePasswordReset(
    UUID memberId, String token, boolean exists, String newPassword
  ) {
    if (!exists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    memberPasswordResetDatabaseTable.findResetToken(memberId).thenAccept(actualToken ->
      completePasswordReset(memberId, token, actualToken, newPassword)
        .thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> completePasswordReset(
    UUID memberId, String token, String actualToken, String newPassword
  ) {
    if (!token.equals(actualToken)) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
    memberPasswordResetDatabaseTable.deleteResetToken(memberId);
    memberDatabaseTable().changeMemberPassword(memberId, hashing.hash(newPassword));
    return CompletableFuture.completedFuture(Map.of("success", true));
  }
}

