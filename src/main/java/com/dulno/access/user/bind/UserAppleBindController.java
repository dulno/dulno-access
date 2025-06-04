package com.dulno.access.user.bind;

import com.dulno.access.user.UserController;
import com.dulno.core.apple.AppleDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.URL;
import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserAppleBindController extends UserController {
  private final UserBindSignup userBindSignup;
  private final UserBindCardMerge userBindCardMerge;
  private final UserBindCouponMerge userBindCouponMerge;
  private final UserBindCompletion userBindCompletion;
  private final AppleDatabaseTable appleDatabaseTable;

  private UserAppleBindController(
    @Qualifier("appKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    UserBindSignup userBindSignup, UserBindCardMerge userBindCardMerge,
    UserBindCouponMerge userBindCouponMerge,
    UserBindCompletion userBindCompletion,
    AppleDatabaseTable appleDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.userBindSignup = userBindSignup;
    this.userBindCardMerge = userBindCardMerge;
    this.userBindCouponMerge = userBindCouponMerge;
    this.userBindCompletion = userBindCompletion;
    this.appleDatabaseTable = appleDatabaseTable;
  }

  @RequestMapping(path = "/user/bind/apple/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> bindAppleUser(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    new Thread(() -> bindAppleUser(request, body, body.getString("token"))
      .thenAccept(futureResponse::complete)).start();
    return futureResponse;
  }

  public CompletableFuture<Map<String, Object>> bindAppleUser(
    HttpServletRequest request, DulnoRequestBody body, String token
  ) {
    try {
      System.out.println(token);
      var jwt = SignedJWT.parse(token);
      if (!verifyAppleJWT(jwt)) {
        return CompletableFuture.completedFuture(Map.of("success", false,
          "error", 1000));
      }
      var claims = jwt.getJWTClaimsSet();
      if (!checkAppleClaimFormalities(claims)) {
        return CompletableFuture.completedFuture(Map.of("success", false,
          "error", 1001));
      }
      var subject = claims.getSubject();
      System.out.println("Sub: " + subject);
      if (subject == null) {
        return CompletableFuture.completedFuture(Map.of("success", false,
          "error", 1002));
      }
      System.out.println(claims.getClaims().toString());
      var email = claims.getStringClaim("email");
      var emailVerified = claims.getStringClaim("email_verified");
      if (email != null && emailVerified != null && emailVerified.equals("true")) {
        appleDatabaseTable.insertAppleSubject(subject, email);
        return userDatabaseTable().userExists(email)
          .thenCompose(exists -> bindAppleUser(request, body, email, exists));
      }
      return appleDatabaseTable.appleSubjectExists(subject)
        .thenCompose(exists -> bindKnownAppleUser(request, body, subject, exists));
    } catch (Exception exception) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1003));
    }
  }

  private CompletableFuture<Map<String, Object>> bindKnownAppleUser(
    HttpServletRequest request, DulnoRequestBody body, String subject,
    boolean subjectExists
  ) {
    if (!subjectExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1004));
    }
    return appleDatabaseTable.findAppleSubjectEmail(subject)
      .thenCompose(email -> userDatabaseTable().userExists(email)
        .thenCompose(exists -> bindAppleUser(request, body, email, exists)));
  }

  private boolean verifyAppleJWT(SignedJWT jwt) throws Exception {
    var publicKeys = JWKSet.load(new URL("https://appleid.apple.com/auth/keys"));
    var key = publicKeys.getKeyByKeyId(jwt.getHeader().getKeyID());
    return key != null && jwt.verify(new RSASSAVerifier((RSAKey) key));
  }

  private boolean checkAppleClaimFormalities(JWTClaimsSet claims) {
    var audience = claims.getAudience().get(0);
    var issuer = claims.getIssuer();
    System.out.println("A: " + audience);
    System.out.println("B: " + issuer);
    return "https://appleid.apple.com".equals(issuer) &&
      "com.dulno".equals(audience);
  }

  private CompletableFuture<Map<String, Object>> bindAppleUser(
    HttpServletRequest request, DulnoRequestBody body, String email,
    boolean userExists
  ) {
    if (!userExists) {
      return userBindSignup.signupUser(email, body).exceptionally(user -> null)
        .thenCompose(newUser -> newUser == null ?
          CompletableFuture.completedFuture(Map.of("success", false, "error", 1005)) :
          completeAppleUserBinding(request, newUser, body, true));
    }
    return userDatabaseTable().findUser(email)
      .thenCompose(user -> completeAppleUserBinding(request, user, body, false));
  }

  private CompletableFuture<Map<String, Object>> completeAppleUserBinding(
    HttpServletRequest request, User user, DulnoRequestBody body,
    boolean isNewUser
  ) {
    if (isNewUser) {
      return userBindCompletion.completeBinding(request, user);
    }
    return userBindCardMerge.mergeUserItems(user, body)
      .thenCompose(value -> userBindCouponMerge.mergeUserCoupons(user, body))
      .thenCompose(value -> userBindCompletion.completeBinding(request, user));
  }
}