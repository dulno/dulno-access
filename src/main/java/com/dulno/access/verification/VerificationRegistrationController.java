package com.dulno.access.verification;

import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.locale.Translation;
import com.dulno.core.mail.Mail;
import com.dulno.core.member.Member;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.member.MemberVerificationDatabaseTable;
import com.dulno.core.member.session.SessionDatabaseTable;
import com.dulno.core.recaptcha.RecaptchaConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@RestController
public final class VerificationRegistrationController {
  private final Key homeKey;
  private final Key productKey;
  private final Key refreshKey;
  private final Mail verificationMail;
  private final Translation translation;
  private final MemberDatabaseTable memberDatabaseTable;
  private final MemberVerificationDatabaseTable memberVerificationDatabaseTable;
  private final RecaptchaConfiguration recaptchaConfiguration;
  private final SessionDatabaseTable sessionDatabaseTable;
  private final VerificationLoginController loginController;
  private final Hashing hashing;
  private final DulnoEnvironment environment;

  private VerificationRegistrationController(
    @Qualifier("homeKey") Key homeKey, @Qualifier("productKey") Key productKey,
    @Qualifier("productKey") Key refreshKey,
    @Qualifier("verificationMail") Mail verificationMail, Translation translation,
    MemberDatabaseTable memberDatabaseTable,
    MemberVerificationDatabaseTable memberVerificationDatabaseTable,
    RecaptchaConfiguration recaptchaConfiguration,
    SessionDatabaseTable sessionDatabaseTable,
    VerificationLoginController loginController, Hashing hashing,
    DulnoEnvironment environment
  ) {
    this.homeKey = homeKey;
    this.productKey = productKey;
    this.refreshKey = refreshKey;
    this.verificationMail = verificationMail;
    this.translation = translation;
    this.memberDatabaseTable = memberDatabaseTable;
    this.memberVerificationDatabaseTable = memberVerificationDatabaseTable;
    this.recaptchaConfiguration = recaptchaConfiguration;
    this.sessionDatabaseTable = sessionDatabaseTable;
    this.loginController = loginController;
    this.hashing = hashing;
    this.environment = environment;
  }

  @RequestMapping(path = "/verification/register/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> register(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var legalAccepted = body.getBoolean("legalAccepted");
    if (!legalAccepted) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1002));
    }
    var email = body.getString("email");
    if (!checkEmailFormat(email)) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1003));
    }
    memberDatabaseTable.memberExists(email)
      .thenAccept(exists -> checkRecaptcha(body.getString("recaptchaToken"))
        .thenAccept(recaptchaVerified -> completeRegistration(futureResponse,
          exists, recaptchaVerified, email, body.getSanitizedString("name", 32),
          body.getString("password"), body.getString("language"),
          body.getString("redirect"), legalAccepted, body.getBoolean("newsletter"))));
    return futureResponse;
  }

  private static final Pattern EMAIL_PATTERN = Pattern.compile(
    "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");

  private boolean checkEmailFormat(String email) {
    if (email == null) {
      return false;
    }
    var matcher = EMAIL_PATTERN.matcher(email);
    return matcher.matches();
  }

  private static final String RECAPTCHA_URL =
    "https://www.google.com/recaptcha/api/siteverify?secret=%s&response=%s";

  private CompletableFuture<Boolean> checkRecaptcha(String token) {
    var url = URI.create(String.format(RECAPTCHA_URL,
      recaptchaConfiguration.secretKey(), token));
    var requestBuilder = HttpRequest.newBuilder().uri(url)
      .POST(HttpRequest.BodyPublishers.noBody())
      .build();
    return HttpClient.newHttpClient()
      .sendAsync(requestBuilder, HttpResponse.BodyHandlers.ofString())
      .thenApply(response -> new JSONObject(response.body()).getBoolean("success"));
  }

  private void completeRegistration(
    CompletableFuture<Map<String, Object>> futureResponse, boolean alreadyExists,
    boolean recaptchaVerified, String email, String name, String password,
    String language, String redirect, boolean legalAccepted,
    boolean newsletter
  ) {
    if (alreadyExists) {
      futureResponse.complete(Map.of("success", false, "error", 1000));
      return;
    }
    if (!recaptchaVerified) {
      futureResponse.complete(Map.of("success", false, "error", 1001));
      return;
    }
    createMember(name, email, hashing.hash(password), language, redirect,
      legalAccepted, newsletter, true);
    futureResponse.complete(Map.of("success", true));
  }

  public CompletableFuture<Member> createMember(
    String name, String email, String passwordHash, String language,
    String redirect,  boolean legalAccepted, boolean newsletter,
    boolean verificationRequired
  ) {
    return memberDatabaseTable.generateAvailableMemberId().thenCompose(id ->
      insertNewMember(id, name, email, passwordHash, language, redirect,
        legalAccepted, newsletter, verificationRequired));
  }

  private static final String VERIFICATION_URL = "https://%s/register/confirm/%s/%s/";

  private CompletableFuture<Member> insertNewMember(
    UUID memberId, String name, String email, String passwordHash, String language,
    String redirect, boolean legalAccepted, boolean newsletter,
    boolean verificationRequired
  ) {
    language = parseMemberLanguage(language);
    var token = UUID.randomUUID().toString();
    if (verificationRequired) {
      memberVerificationDatabaseTable.insertVerification(memberId, token);
    }
    var title = translation.translate(language, "registration.email.title");
    var verificationUrl = String.format(VERIFICATION_URL, environment.domain(),
      memberId.toString(), token);
    if (!redirect.isEmpty()) {
      verificationUrl += "?redirect=" + redirect;
    }
    var body = String.format(
      translation.translate(language, "registration.email.body"), name,
      verificationUrl);
    verificationMail.send(email, language, title, body);
    var member = Member.create(memberId, name, email, passwordHash, language,
      legalAccepted, newsletter, System.currentTimeMillis());
    return memberDatabaseTable.insertMember(member).thenApply(memberValue -> member);
  }

  private String parseMemberLanguage(String language) {
    if (language.equals("en") || language.equals("de")) {
      return language;
    }
    return "en";
  }

  @RequestMapping(path = "/verification/email/resend/", method = RequestMethod.POST)
  public void resendEmail(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var email = body.getString("email").trim();
    memberDatabaseTable.memberExists(email).thenAccept(memberExists ->
      resendEmailCheckMember(email, memberExists));
  }

  private void resendEmailCheckMember(String email, boolean memberExists) {
    if (!memberExists) {
      return;
    }
    memberDatabaseTable.findMember(email).thenAccept(member ->
      memberVerificationDatabaseTable.verificationExists(member.id())
        .thenAccept(verificationExists ->
          resendEmailCheckRegistration(member, verificationExists)));
  }

  private void resendEmailCheckRegistration(Member member, boolean verificationExists) {
    if (!verificationExists) {
      return;
    }
    var title = translation.translate(member, "registration.email.title");
    memberVerificationDatabaseTable.findVerification(member.id())
      .thenApply(token -> String.format(
        translation.translate(member, "registration.email.body"), member.name(),
        String.format(VERIFICATION_URL, environment.domain(),
          member.id().toString(), token)))
      .thenAccept(body -> verificationMail.send(member, title, body));
  }

  @RequestMapping(path = "/verification/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> complete(
    HttpServletRequest request, @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var memberId = body.getUUID("member");
    return memberVerificationDatabaseTable.verificationExists(memberId)
      .thenCompose(exists -> complete(request, memberId, body.getString("token"),
        exists));
  }

  private CompletableFuture<Map<String, Object>> complete(
    HttpServletRequest request, UUID memberId, String submittedToken,
    boolean tokenExists
  ) {
    if (!tokenExists) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return memberVerificationDatabaseTable.findVerification(memberId)
      .thenCompose(originalToken -> complete(request, memberId, submittedToken,
        originalToken));
  }

  private CompletableFuture<Map<String, Object>> complete(
    HttpServletRequest request, UUID memberId, String submittedToken,
    String originalToken
  ) {
    if (!submittedToken.equals(originalToken)) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return sessionDatabaseTable.generateAvailableSessionId()
      .thenCompose(sessionId -> complete(request, memberId, sessionId));
  }

  private CompletableFuture<Map<String, Object>> complete(
    HttpServletRequest request, UUID memberId, UUID sessionId
  ) {
    memberVerificationDatabaseTable.deleteVerification(memberId);
    var apiKey = Verification.create(memberDatabaseTable, homeKey, productKey,
      refreshKey, hashing).generateHomeApiKey(memberId, sessionId);
    loginController.storeSession(request, memberId, sessionId, "");
    return memberDatabaseTable.findMember(memberId).thenApply(member ->
      Map.of("success", true, "homeApiKey", apiKey, "memberName", member.name()));
  }
}

