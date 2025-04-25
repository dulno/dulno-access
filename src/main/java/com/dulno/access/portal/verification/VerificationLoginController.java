package com.dulno.access.portal.verification;

import com.dulno.access.portal.PortalRestController;
import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.bundle.Bundle;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.member.Member;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.member.MemberVerificationDatabaseTable;
import com.dulno.core.member.mfa.MultiFactorAuthFactory;
import com.dulno.core.member.session.Session;
import com.dulno.core.member.session.SessionDatabaseTable;
import com.dulno.core.member.session.SessionStatus;
import com.dulno.core.member.session.UserAgent;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.maxmind.geoip2.DatabaseReader;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.security.Key;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
public final class VerificationLoginController extends PortalRestController {
  private final Key homeKey;
  private final Key productKey;
  private final Key refreshKey;
  private final MemberVerificationDatabaseTable memberVerificationDatabaseTable;
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final MultiFactorAuthFactory multiFactorAuthFactory;
  private final SessionDatabaseTable sessionDatabaseTable;
  private final DatabaseReader geoDatabaseReader;
  private final Hashing hashing;

  private VerificationLoginController(
    @Qualifier("homeKey") Key homeKey, @Qualifier("productKey") Key productKey,
    @Qualifier("refreshKey") Key refreshKey, MemberDatabaseTable memberDatabaseTable,
    MemberVerificationDatabaseTable memberVerificationDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable,
    MultiFactorAuthFactory multiFactorAuthFactory,
    SessionDatabaseTable sessionDatabaseTable, DatabaseReader geoDatabaseReader,
    Hashing hashing
  ) {
    super(productKey, memberDatabaseTable);
    this.homeKey = homeKey;
    this.productKey = productKey;
    this.refreshKey = refreshKey;
    this.memberVerificationDatabaseTable = memberVerificationDatabaseTable;
    this.partnerMemberDatabaseTable = partnerMemberDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.multiFactorAuthFactory = multiFactorAuthFactory;
    this.sessionDatabaseTable = sessionDatabaseTable;
    this.geoDatabaseReader = geoDatabaseReader;
    this.hashing = hashing;
  }

  @RequestMapping(path = "/verification/login/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> login(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) throws Exception {
    var body = DulnoRequestBody.of(payload, response);
    var loginFuture = login(request, body.getString("email"),
      body.getString("password"), body.getString("multiFactorCode"));
    loginFuture.thenAccept(result -> applyLoginResponseStatus(response, result));
    return loginFuture;
  }

  private void applyLoginResponseStatus(
    HttpServletResponse response, Map<String, Object> result
  ) {
    if ((boolean) result.get("success")) {
      response.setStatus(HttpServletResponse.SC_OK);
      return;
    }
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  private CompletableFuture<Map<String, Object>> login(
    HttpServletRequest request, String email, String password,
    String multiFactorCode
  ) {
    var verification = Verification.create(memberDatabaseTable(), homeKey, productKey,
      refreshKey, hashing, email.trim(), password);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    verification.isAuthenticated().thenAccept(isAuthenticated ->
      checkAuthorization(request, verification, multiFactorCode, futureResponse,
        isAuthenticated));
    return futureResponse;
  }

  private void checkAuthorization(
    HttpServletRequest request, Verification verification, String multiFactorCode,
    CompletableFuture<Map<String, Object>> futureResponse, boolean isAuthenticated
  ) {
    if (!isAuthenticated) {
      futureResponse.complete(Map.of("success", false, "error", 1000));
      return;
    }
    memberDatabaseTable().findMember(verification.email()).thenAccept(member ->
      multiFactorAuthFactory.createAuth(member.id()).verifyCode(multiFactorCode)
        .thenAccept(verified -> checkMultiFactorAuth(request, member, verification,
          futureResponse, verified)));
  }

  private void checkMultiFactorAuth(
    HttpServletRequest request, Member member, Verification verification,
    CompletableFuture<Map<String, Object>> futureResponse,
    boolean multiFactorVerified
  ) {
    if (!multiFactorVerified) {
      futureResponse.complete(Map.of("success", false, "error", 1002));
      return;
    }
    processAuthorizedLogin(request, member, verification, futureResponse);
  }

  public void processAuthorizedLogin(
    HttpServletRequest request, Verification verification,
    CompletableFuture<Map<String, Object>> futureResponse
  ) {
    memberDatabaseTable().findMember(verification.email()).thenAccept(member ->
      processAuthorizedLogin(request, member, verification, futureResponse));
  }

  public void processAuthorizedLogin(
    HttpServletRequest request, Member member, Verification verification,
    CompletableFuture<Map<String, Object>> futureResponse
  ) {
    memberVerificationDatabaseTable.verificationExists(member.id())
      .thenAccept(completionPending -> checkBundle(request, verification,
        futureResponse, member, completionPending));
  }

  private void checkBundle(
    HttpServletRequest request, Verification verification,
    CompletableFuture<Map<String, Object>> futureResponse, Member member,
    boolean completionPending
  ) {
    if (completionPending) {
      futureResponse.complete(Map.of("success", false, "error", 1001));
      return;
    }
    findLatestBundleExpiration(member).thenAccept(latestExpiration ->
      sessionDatabaseTable.generateAvailableSessionId().thenAccept(sessionId ->
        checkBundle(request, verification, futureResponse, member,
          latestExpiration > System.currentTimeMillis(), latestExpiration, sessionId)));
  }

  private CompletableFuture<Long> findLatestBundleExpiration(Member member) {
    return partnerMemberDatabaseTable.findMemberPartners(member.id())
      .thenCompose(partners -> AsyncIterator.execute(partners,
          partner -> bundleDatabaseTable.bundleExists(partner)
            .thenCompose(exists -> exists ? bundleDatabaseTable.findBundle(partner) :
              CompletableFuture.completedFuture(null)))
        .thenApply(bundles -> bundles.stream().filter(Objects::nonNull)
          .map(Bundle::expiration)
          .map(expiration -> expiration < 0 ? Long.MAX_VALUE : expiration)
          .sorted(Comparator.reverseOrder()).findFirst().orElse(0L)));
  }

  private void checkBundle(
    HttpServletRequest request, Verification verification,
    CompletableFuture<Map<String, Object>> futureResponse, Member member,
    boolean bundleEnabled, long expiration, UUID sessionId
  ) {
    var homeApiKey = verification.generateHomeApiKey(member.id(), sessionId);
    if (!bundleEnabled) {
      storeSession(request, member.id(), sessionId, "");
      futureResponse.complete(Map.of("success", false, "error", 1003,
        "homeApiKey", homeApiKey, "memberName", member.name()));
      return;
    }
    completeLogin(request, verification, futureResponse, member, expiration,
      homeApiKey, sessionId);
  }

  private void completeLogin(
    HttpServletRequest request, Verification verification,
    CompletableFuture<Map<String, Object>> futureResponse,
    Member member, long expiration, String homeApiKey, UUID sessionId
  ) {
    var productApiKey = verification.generateProductApiKey(member.id(),
      sessionId, expiration);
    var refreshToken = verification.generateRefreshToken(member.id(),
      sessionId, expiration);
    storeSession(request, member.id(), sessionId, refreshToken);
    futureResponse.complete(Map.of("success", true, "productApiKey", productApiKey,
      "homeApiKey", homeApiKey, "refreshToken", refreshToken));
  }

  public void storeSession(
    HttpServletRequest request, UUID memberId, UUID sessionId, String refreshToken
  ) {
    var country = "";
    var city = "";
    var ipAddress = request.getHeader("X-Real-IP");
    try {
      var location = geoDatabaseReader.city(InetAddress.getByName(ipAddress));
      country = location.getCountry().getName();
      city = location.getCity().getName();
    } catch (Exception ignored) {
    }
    try {
      var platform = UserAgent.create(request.getHeader("User-Agent"))
        .findPlatform();
      sessionDatabaseTable.insertSession(sessionId, memberId, SessionStatus.ACTIVE,
        platform, ipAddress, country, city, System.currentTimeMillis(),
        refreshToken, System.currentTimeMillis());
    } catch (Exception ignored) {
    }
  }

  @RequestMapping(path = "/verification/refresh/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> refreshVerification(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var refreshToken = body.getString("refreshToken");
    var result = verifyToken(refreshKey, refreshToken);
    if (result.getKey() != HttpServletResponse.SC_ACCEPTED) {
      return CompletableFuture.completedFuture(Map.of("success", "false"));
    }
    var memberId = UUID.fromString(result.getValue().get("id", String.class));
    var sessionId = UUID.fromString(result.getValue().get("session", String.class));
    return memberDatabaseTable().memberExists(memberId)
      .thenCompose(memberExists -> sessionDatabaseTable.sessionExists(sessionId)
        .thenCompose(sessionExists -> refreshVerification(refreshToken, memberId,
          sessionId, memberExists, sessionExists)));
  }

  private CompletableFuture<Map<String, Object>> refreshVerification(
    String refreshToken, UUID memberId, UUID sessionId, boolean memberExists,
    boolean sessionExists
  ) {
    if (!memberExists || !sessionExists) {
      return CompletableFuture.completedFuture(Map.of("success", "false"));
    }
    return memberDatabaseTable().findMember(memberId)
      .thenCompose(member -> sessionDatabaseTable.findSession(sessionId)
        .thenCompose(session -> findLatestBundleExpiration(member)
          .thenApply(latestExpiration -> refreshVerification(refreshToken, member,
            session, latestExpiration > System.currentTimeMillis(), latestExpiration))));
  }

  private Map<String, Object> refreshVerification(
    String refreshToken, Member member, Session session, boolean bundleEnabled,
    long expiration
  ) {
    if (!bundleEnabled) {
      return Map.of("success", "false");
    }
    if (session.status().isClosed() ||
      !session.lastRefreshToken().equals(refreshToken)
    ) {
      return Map.of("success", "false");
    }
    var verification = Verification.create(memberDatabaseTable(), homeKey,
      productKey, refreshKey, hashing);
    var newProductApiKey = verification.generateProductApiKey(member.id(),
      session.id(), expiration);
    var newRefreshToken = verification.generateRefreshToken(member.id(),
      session.id(), expiration);
    sessionDatabaseTable.updateSessionRefreshToken(session.id(), newRefreshToken);
    return Map.of("success", "true", "productApiKey", newProductApiKey,
      "refreshToken", newRefreshToken);
  }

  @PortalEndpoint
  @RequestMapping(path = "/verification/logout/", method = RequestMethod.GET)
  public void logout(HttpServletRequest request) {
    sessionDatabaseTable.closeSession(findSessionId(request));
  }

  @RequestMapping(path = "/verification/isValid/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> isValid(
    @RequestBody String payload, HttpServletResponse response
  ) {
    return isValid(productKey, payload, response)
      .thenCompose(result -> isValidCheckBundle(result.getKey(), result.getValue()))
      .thenApply(this::finishValidation);
  }

  private CompletableFuture<Boolean> isValidCheckBundle(
    boolean isValid, Claims claims
  ) {
    if (!isValid) {
      return CompletableFuture.completedFuture(false);
    }
    return memberDatabaseTable().findMember(UUID.fromString((String) claims.get("id")))
      .thenCompose(member -> findLatestBundleExpiration(member)
        .thenApply(latestExpiration -> latestExpiration > System.currentTimeMillis()));
  }

  @RequestMapping(path = "/verification/home/isValid/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> isHomeValid(
    @RequestBody String payload, HttpServletResponse response
  ) {
    return isValid(homeKey, payload, response)
      .thenApply(result -> finishValidation(result.getKey()));
  }

  private Map<String, Object> finishValidation(boolean isValid) {
    return Map.of("isValid", isValid ? "true" : "false");
  }

  private CompletableFuture<Map.Entry<Boolean, Claims> > isValid(
    Key key, String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var result = verifyToken(key, body.getString("token"));
    if (result.getKey() != HttpServletResponse.SC_ACCEPTED) {
      response.setStatus(result.getKey());
      return CompletableFuture.completedFuture(
        new AbstractMap.SimpleEntry<>(false, result.getValue()));
    }
    var memberId = UUID.fromString(result.getValue().get("id", String.class));
    return memberDatabaseTable().memberExists(memberId)
      .thenApply(exists -> new AbstractMap.SimpleEntry<>(exists, result.getValue()));
  }

  private Map.Entry<Integer, Claims> verifyToken(Key key, String token) {
    try {
      return new AbstractMap.SimpleEntry(HttpServletResponse.SC_ACCEPTED,
        Jwts.parser()
          .setSigningKey(key)
          .build()
          .parseClaimsJws(token)
          .getPayload());
    } catch (ExpiredJwtException exception) {
      return new AbstractMap.SimpleEntry(
        HttpServletResponse.SC_EXPECTATION_FAILED, null);
    } catch (Exception exception) {
      return new AbstractMap.SimpleEntry(
        HttpServletResponse.SC_FORBIDDEN, null);
    }
  }

  @RequestMapping(path = "/address/", method = RequestMethod.GET)
  public Map<String, Object> findRequestAddress(HttpServletRequest request) {
    var address = request.getHeader("X-Real-IP");
    if (address == null) {
      return Map.of("address", "");
    }
    return Map.of("address", address);
  }
}
