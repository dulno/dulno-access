package com.dulno.access.user.bind;

import com.dulno.access.user.UserController;
import com.dulno.core.google.GoogleConfiguration;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserGoogleBindController extends UserController {
  private final UserBindSignup userBindSignup;
  private final UserBindCardMerge userBindCardMerge;
  private final UserBindCouponMerge userBindCouponMerge;
  private final UserBindCompletion userBindCompletion;
  private final GoogleConfiguration googleConfiguration;
  private final GsonFactory gsonFactory = GsonFactory.getDefaultInstance();
  private final NetHttpTransport netHttpTransport = new NetHttpTransport();

  private UserGoogleBindController(
    @Qualifier("appKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    UserBindSignup userBindSignup, UserBindCardMerge userBindCardMerge,
    UserBindCouponMerge userBindCouponMerge,
    UserBindCompletion userBindCompletion,
    GoogleConfiguration googleConfiguration
  ) {
    super(secretKey, userDatabaseTable);
    this.userBindSignup = userBindSignup;
    this.userBindCardMerge = userBindCardMerge;
    this.userBindCouponMerge = userBindCouponMerge;
    this.userBindCompletion = userBindCompletion;
    this.googleConfiguration = googleConfiguration;
  }

  @RequestMapping(path = "/user/bind/google/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> bindGoogleUser(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    return bindGoogleUser(request, body, body.getString("token"));
  }

  public CompletableFuture<Map<String, Object>> bindGoogleUser(
    HttpServletRequest request, DulnoRequestBody body, String token
  ) {
    try {
      var verifier = new GoogleIdTokenVerifier.Builder(netHttpTransport, gsonFactory)
        .setAudience(Collections.singletonList(googleConfiguration.clientId()))
        .build();
      var idToken = verifier.verify(token);
      if (idToken == null) {
        return CompletableFuture.completedFuture(Map.of("success", false,
          "error", 1000));
      }
      var payload = idToken.getPayload();
      var email = payload.getEmail();
      return userDatabaseTable().userExists(email)
        .thenCompose(userExists -> bindGoogleUser(request, body, email, userExists));
    } catch (Exception exception) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
  }

  private CompletableFuture<Map<String, Object>> bindGoogleUser(
    HttpServletRequest request, DulnoRequestBody body, String email,
    boolean userExists
  ) {
    if (!userExists) {
      return userBindSignup.signupUser(email, body).exceptionally(user -> null)
        .thenCompose(newUser -> newUser == null ?
          CompletableFuture.completedFuture(Map.of("success", false, "error", 1002)) :
          completeGoogleUserBinding(request, newUser, body, true));
    }
    return userDatabaseTable().findUser(email)
      .thenCompose(user -> completeGoogleUserBinding(request, user, body, false));
  }

  private CompletableFuture<Map<String, Object>> completeGoogleUserBinding(
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