package com.dulno.access.portal.verification;

import com.dulno.core.email.EmailValidation;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.locale.Translation;
import com.dulno.core.mail.Mail;
import com.dulno.core.member.Member;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.member.MemberVerificationDatabaseTable;
import com.dulno.core.member.session.SessionDatabaseTable;
import com.dulno.core.partner.*;
import com.dulno.core.partner.link.PartnerLink;
import com.dulno.core.partner.link.PartnerLinkDatabaseTable;
import com.dulno.core.partner.link.PartnerLinkType;
import com.dulno.core.partner.location.PartnerLocation;
import com.dulno.core.partner.location.PartnerLocationDatabaseTable;
import com.dulno.core.partner.logo.PartnerLogoDatabaseTable;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import com.dulno.core.recaptcha.RecaptchaConfiguration;
import com.dulno.core.request.DulnoRequestBody;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class VerificationSignupController {
  private final Key homeKey;
  private final Key productKey;
  private final Key refreshKey;
  private final Mail verificationMail;
  private final Translation translation;
  private final MemberDatabaseTable memberDatabaseTable;
  private final MemberVerificationDatabaseTable memberVerificationDatabaseTable;
  private final RecaptchaConfiguration recaptchaConfiguration;
  private final SessionDatabaseTable sessionDatabaseTable;
  private final PartnerConfiguration partnerConfiguration;
  private final PartnerDatabaseTable partnerDatabaseTable;
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;
  private final PartnerLogoDatabaseTable partnerLogoDatabaseTable;
  private final PartnerLocationDatabaseTable partnerLocationDatabaseTable;
  private final PartnerLinkDatabaseTable partnerLinkDatabaseTable;
  private final VerificationLoginController loginController;
  private final Hashing hashing;
  private final DulnoEnvironment environment;

  private VerificationSignupController(
    @Qualifier("homeKey") Key homeKey, @Qualifier("productKey") Key productKey,
    @Qualifier("productKey") Key refreshKey,
    @Qualifier("verificationMail") Mail verificationMail, Translation translation,
    MemberDatabaseTable memberDatabaseTable,
    MemberVerificationDatabaseTable memberVerificationDatabaseTable,
    RecaptchaConfiguration recaptchaConfiguration,
    SessionDatabaseTable sessionDatabaseTable,
    PartnerConfiguration partnerConfiguration,
    PartnerDatabaseTable partnerDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    PartnerLogoDatabaseTable partnerLogoDatabaseTable,
    PartnerLocationDatabaseTable partnerLocationDatabaseTable,
    PartnerLinkDatabaseTable partnerLinkDatabaseTable,
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
    this.partnerConfiguration = partnerConfiguration;
    this.partnerDatabaseTable = partnerDatabaseTable;
    this.partnerMemberDatabaseTable = partnerMemberDatabaseTable;
    this.partnerLogoDatabaseTable = partnerLogoDatabaseTable;
    this.partnerLocationDatabaseTable = partnerLocationDatabaseTable;
    this.partnerLinkDatabaseTable = partnerLinkDatabaseTable;
    this.loginController = loginController;
    this.hashing = hashing;
    this.environment = environment;
  }

  private static final long MAX_LOGO_SIZE = 1024L * 1024;

  @RequestMapping(path = "/verification/partner/signup/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> signupPartner(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var legalAccepted = body.getBoolean("legalAccepted");
    if (!legalAccepted) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    var email = body.getString("memberEmail");
    if (!EmailValidation.validate(email)) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
    var partnerLogo = parsePartnerLogo(body.getString("partnerLogo"));
    if (partnerLogo.length > MAX_LOGO_SIZE) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1002));
    }
    memberDatabaseTable.memberExists(email)
      .thenAccept(exists -> checkRecaptcha(body.getString("recaptchaToken"))
        .thenAccept(recaptchaVerified -> checkSignupRestrictions(futureResponse,
          exists, recaptchaVerified, body.getSanitizedString("memberName", 64),
          email, body.getString("memberPassword"), body.getString("memberLanguage"),
          body.getSanitizedString("partnerName", 64),
          body.getSanitizedString("partnerDescription", 256), partnerLogo,
          body.getObjectList("partnerLocations"),
          body.getObjectList("partnerLinks"), body.getString("redirect"),
          legalAccepted, body.getBoolean("newsletter"))));
    return futureResponse;
  }

  private byte[] parsePartnerLogo(String partnerLogo) {
    try {
      return Base64.getDecoder().decode(partnerLogo);
    } catch (Exception exception) {
      return Base64.getDecoder().decode(partnerConfiguration.defaultLogo());
    }
  }

  private static final String RECAPTCHA_URL =
    "https://www.google.com/recaptcha/api/siteverify?secret=%s&response=%s";

  private CompletableFuture<Boolean> checkRecaptcha(String token) {
    if (environment.isLocal()) {
      return CompletableFuture.completedFuture(true);
    }
    var url = URI.create(String.format(RECAPTCHA_URL,
      recaptchaConfiguration.secretKey(), token));
    var requestBuilder = HttpRequest.newBuilder().uri(url)
      .POST(HttpRequest.BodyPublishers.noBody())
      .build();
    return HttpClient.newHttpClient()
      .sendAsync(requestBuilder, HttpResponse.BodyHandlers.ofString())
      .thenApply(response -> new JSONObject(response.body()).getBoolean("success"));
  }

  private void checkSignupRestrictions(
    CompletableFuture<Map<String, Object>> futureResponse, boolean alreadyExists,
    boolean recaptchaVerified, String memberName, String memberEmail,
    String memberPassword, String memberLanguage, String partnerName,
    String partnerDescription, byte[] partnerLogo,
    List<DulnoRequestBody> partnerLocations, List<DulnoRequestBody> partnerLinks,
    String redirect, boolean legalAccepted, boolean newsletter
  ) {
    if (alreadyExists) {
      futureResponse.complete(Map.of("success", false, "error", 1003));
      return;
    }
    if (!recaptchaVerified) {
      futureResponse.complete(Map.of("success", false, "error", 1004));
      return;
    }
    generateSignupIds(memberName, memberEmail, memberPassword, memberLanguage,
      partnerName, partnerDescription, partnerLogo, partnerLocations,
      partnerLinks, redirect, legalAccepted, newsletter);
    futureResponse.complete(Map.of("success", true));
  }

  private void generateSignupIds(
    String memberName, String memberEmail, String memberPassword,
    String memberLanguage, String partnerName, String partnerDescription,
    byte[] partnerLogo, List<DulnoRequestBody> partnerLocations,
    List<DulnoRequestBody> partnerLinks, String redirect, boolean legalAccepted,
    boolean newsletter
  ) {
    memberDatabaseTable.generateAvailableMemberId()
      .thenAccept(memberId -> partnerDatabaseTable.generateAvailablePartnerId()
        .thenAccept(partnerId -> performPartnerSignup(memberId, memberName,
          memberEmail, hashing.hash(memberPassword), memberLanguage, partnerId,
          partnerName, partnerDescription, partnerLogo, partnerLocations,
          partnerLinks, redirect, legalAccepted, newsletter)));
  }

  private static final String VERIFICATION_URL = "https://portal.%s/signup/confirm/%s/%s/";

  private void performPartnerSignup(
    UUID memberId, String memberName, String memberEmail, String memberPasswordHash,
    String memberLanguage, UUID partnerId, String partnerName,
    String partnerDescription, byte[] partnerLogo,
    List<DulnoRequestBody> partnerLocations, List<DulnoRequestBody> partnerLinks,
    String redirect, boolean legalAccepted, boolean newsletter
  ) {
    insertNewMember(memberId, memberName, memberEmail, memberPasswordHash,
      memberLanguage, redirect, legalAccepted, newsletter);
    insertNewPartner(partnerId, partnerName, partnerDescription, partnerLogo,
      partnerLocations, partnerLinks, memberId);
  }

  private void insertNewMember(
    UUID memberId, String name, String email, String passwordHash, String language,
    String redirect, boolean legalAccepted, boolean newsletter
  ) {
    language = parseMemberLanguage(language);
    var token = UUID.randomUUID().toString();
    memberVerificationDatabaseTable.insertVerification(memberId, token);
    var title = translation.translate(language, "signup.email.title");
    var verificationUrl = String.format(VERIFICATION_URL, environment.domain(),
      memberId.toString(), token);
    if (!redirect.isEmpty()) {
      verificationUrl += "?redirect=" + redirect;
    }
    var body = String.format(
      translation.translate(language, "signup.email.body"), name,
      verificationUrl);
    verificationMail.send(email, language, title, body);
    var member = Member.create(memberId, name, email, passwordHash, language,
      legalAccepted, newsletter, System.currentTimeMillis());
    memberDatabaseTable.insertMember(member);
  }

  private String parseMemberLanguage(String language) {
    if (language.equals("en") || language.equals("de")) {
      return language;
    }
    return "en";
  }

  private void insertNewPartner(
    UUID partnerId, String partnerName, String partnerDescription,
    byte[] partnerLogo, List<DulnoRequestBody> partnerLocations,
    List<DulnoRequestBody> partnerLinks, UUID memberId
  ) {
    var partner = Partner.create(partnerId, partnerName, partnerDescription,
      System.currentTimeMillis());
    partnerDatabaseTable.insertPartner(partner);
    partnerMemberDatabaseTable.insertPartnerMember(partnerId, memberId);
    partnerLogoDatabaseTable.generateAvailablePartnerLogoId(partnerId)
      .thenAccept(logoId -> partnerLogoDatabaseTable.insertPartnerLogo(partnerId,
        logoId, partnerLogo));
    insertNewPartnerLocations(partnerId, partnerLocations);
    insertNewPartnerLinks(partnerId, partnerLinks);
  }

  private void insertNewPartnerLocations(
    UUID partnerId, List<DulnoRequestBody> partnerLocations
  ) {
    for (var location : partnerLocations) {
      partnerLocationDatabaseTable.insertPartnerLocation(PartnerLocation.create(
        partnerId, location.getSanitizedString("address"),
        location.getDouble("latitude"), location.getDouble("longitude"),
        System.currentTimeMillis()));
    }
  }

  private void insertNewPartnerLinks(
    UUID partnerId, List<DulnoRequestBody> partnerLinks
  ) {
    for (var link : partnerLinks) {
      partnerLinkDatabaseTable.insertPartnerLink(PartnerLink.create(partnerId,
        PartnerLinkType.valueOf(link.getString("type")),
        link.getSanitizedString("link"), System.currentTimeMillis()));
    }
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
    var title = translation.translateMember(member, "signup.email.title");
    memberVerificationDatabaseTable.findVerification(member.id())
      .thenApply(token -> String.format(
        translation.translateMember(member, "signup.email.body"), member.name(),
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
