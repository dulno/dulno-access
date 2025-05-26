package com.dulno.access.portal.setting;

import com.dulno.access.portal.security.mixed.MixedEndpoint;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.member.Member;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.member.mfa.MultiFactorAuthDatabaseTable;
import com.dulno.core.member.mfa.MultiFactorAuthEntry;
import com.dulno.core.member.mfa.MultiFactorAuthFactory;
import com.dulno.core.request.DulnoRequestBody;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class MultiFactorAuthSettingController extends SettingController {
  private final MultiFactorAuthDatabaseTable multiFactorAuthDatabaseTable;
  private final MultiFactorAuthFactory multiFactorAuthFactory;
  private final Hashing hashing;

  private MultiFactorAuthSettingController(
    @Qualifier("portalKey") Key portalKey, @Qualifier("homeKey") Key homeKey,
    MemberDatabaseTable memberDatabaseTable,
    MultiFactorAuthDatabaseTable multiFactorAuthDatabaseTable,
    MultiFactorAuthFactory multiFactorAuthFactory, Hashing hashing
  ) {
    super(portalKey, homeKey, memberDatabaseTable);
    this.multiFactorAuthDatabaseTable = multiFactorAuthDatabaseTable;
    this.multiFactorAuthFactory = multiFactorAuthFactory;
    this.hashing = hashing;
  }

  @MixedEndpoint
  @RequestMapping(path = "/settings/2fa/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> multiFactorAuth(
    HttpServletRequest request
  ) {
    return multiFactorAuthDatabaseTable.authExists(findMemberId(request))
      .thenApply(exists -> Map.of("enabled", exists));
  }

  @MixedEndpoint
  @RequestMapping(path = "/settings/2fa/switch/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> switchMultiFactorAuth(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    return findMember(request).thenCompose(member ->
      switchMultiFactorAuth(member, body.getString("password")));
  }

  private CompletableFuture<Map<String, Object>> switchMultiFactorAuth(
    Member member, String password
  ) {
    if (!hashing.matches(password, member.passwordHash())) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return multiFactorAuthDatabaseTable.authExists(member.id())
      .thenCompose(exists -> switchMultiFactorAuth(member.id(), exists));
  }

  private CompletableFuture<Map<String, Object>> switchMultiFactorAuth(
    UUID memberId, boolean multiFactorAuthEnabled
  ) {
    if (multiFactorAuthEnabled) {
      multiFactorAuthDatabaseTable.deleteAuth(memberId);
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    var auth = multiFactorAuthFactory.createAuth(memberId);
    return auth.setup().thenCompose(value -> auth.generateQRCode()
      .thenApply(Base64.getEncoder()::encodeToString)
      .thenCompose(qrCodeEncoded -> multiFactorAuthDatabaseTable.findAuth(memberId)
        .thenApply(authMember -> Map.of("qrCode", qrCodeEncoded, "secret",
          authMember.secret(), "recoveryCodes", authMember.recoveryCodes()))));
  }

  @MixedEndpoint
  @RequestMapping(path = "/settings/2fa/confirm/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> confirmMultiFactorAuth(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var memberId = findMemberId(request);
    return multiFactorAuthDatabaseTable.authExists(memberId)
      .thenCompose(exists -> confirmMultiFactorAuth(memberId,
        body.getString("code"), exists));
  }

  private CompletableFuture<Map<String, Object>> confirmMultiFactorAuth(
    UUID memberId, String code, boolean multiFactorEnabled
  ) {
    if (!multiFactorEnabled) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return multiFactorAuthDatabaseTable.findAuth(memberId)
      .thenCompose(auth -> confirmMultiFactorAuth(memberId, code, auth));
  }

  private CompletableFuture<Map<String, Object>> confirmMultiFactorAuth(
    UUID memberId, String code, MultiFactorAuthEntry auth
  ) {
    if (auth.confirmed()) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return multiFactorAuthFactory.createAuth(memberId).verifyCode(code)
      .thenCompose(verified -> !verified ?
        CompletableFuture.completedFuture(Map.of("success", false)) :
        multiFactorAuthDatabaseTable.confirmAuth(memberId)
          .thenApply(value -> Map.of("success", true)));
  }

  @MixedEndpoint
  @RequestMapping(path = "/settings/2fa/isConfirmed/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> isMultiFactorAuthConfirmed(
    HttpServletRequest request
  ) {
    var memberId = findMemberId(request);
    return multiFactorAuthDatabaseTable.authExists(memberId)
      .thenCompose(exists -> isMultiFactorAuthConfirmed(memberId, exists));
  }

  private CompletableFuture<Map<String, Object>> isMultiFactorAuthConfirmed(
    UUID memberId, boolean multiFactorEnabled
  ) {
    if (!multiFactorEnabled) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return multiFactorAuthDatabaseTable.findAuth(memberId)
      .thenApply(auth -> Map.of("success", true, "confirmed", auth.confirmed()));
  }
}