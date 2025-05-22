package com.dulno.access.user.bind;

import com.dulno.access.user.UserController;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.session.UserSession;
import com.dulno.core.user.session.UserSessionDatabaseTable;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
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
public final class UserAuthorizationController extends UserController {
  private final Key appRefreshKey;
  private final UserTokenFactory userTokenFactory;
  private final UserSessionDatabaseTable sessionDatabaseTable;

  private UserAuthorizationController(
    @Qualifier("appKey") Key appKey, @Qualifier("appRefreshKey") Key appRefreshKey,
    UserDatabaseTable userDatabaseTable, UserTokenFactory userTokenFactory,
    UserSessionDatabaseTable sessionDatabaseTable
  ) {
    super(appKey, userDatabaseTable);
    this.appRefreshKey = appRefreshKey;
    this.userTokenFactory = userTokenFactory;
    this.sessionDatabaseTable = sessionDatabaseTable;
  }

  @RequestMapping(path = "/user/authorization/refresh/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> refreshAuthorization(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var refreshToken = body.getString("refreshToken");
    var result = verifyToken(appRefreshKey, refreshToken);
    if (result == null) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    var userId = UUID.fromString(result.get("id", String.class));
    var sessionId = UUID.fromString(result.get("session", String.class));
    return userDatabaseTable().userExists(userId)
      .thenCompose(userExists -> sessionDatabaseTable.sessionExists(sessionId)
        .thenCompose(sessionExists -> refreshAuthorization(refreshToken, userId,
          sessionId, userExists, sessionExists)));
  }

  private CompletableFuture<Map<String, Object>> refreshAuthorization(
    String refreshToken, UUID userId, UUID sessionId, boolean userExists,
    boolean sessionExists
  ) {
    if (!userExists || !sessionExists) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return userDatabaseTable().findUser(userId)
      .thenCompose(user -> sessionDatabaseTable.findSession(sessionId)
        .thenApply(session -> refreshAuthorization(refreshToken, user, session)));
  }

  private Map<String, Object> refreshAuthorization(
    String refreshToken, User user, UserSession session
  ) {
    if (session.status().isClosed() ||
      !session.lastRefreshToken().equals(refreshToken)
    ) {
      return Map.of("success", false);
    }
    var newAuthenticationToken = userTokenFactory.generateAuthenticationToken(
      user.id(), session.id());
    var newRefreshToken = userTokenFactory.generateRefreshToken(user.id(),
      session.id());
    sessionDatabaseTable.updateSessionRefreshToken(session.id(), newRefreshToken);
    return Map.of("success", true, "authenticationToken", newAuthenticationToken,
      "refreshToken", newRefreshToken);
  }

  @RequestMapping(path = "/user/logout/", method = RequestMethod.GET)
  public void logout(HttpServletRequest request) {
    findUser(request).exceptionally(throwable -> null)
      .thenApply(user -> user == null ? null :
        sessionDatabaseTable.closeSession(findSessionId(request)));
  }

  @RequestMapping(path = "/user/authorized/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> isAuthorized(
    HttpServletRequest request
  ) {
    return findUser(request).exceptionally(throwable -> null)
      .thenApply(user -> Map.of("authorized", user != null));
  }

  private Claims verifyToken(Key key, String token) {
    try {
      return Jwts.parser()
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token)
        .getPayload();
    } catch (Exception exception) {
      return null;
    }
  }
}