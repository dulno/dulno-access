package com.dulno.access.user;

import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.security.Key;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Accessors(fluent = true)
@Getter(AccessLevel.PROTECTED)
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class UserController {
  private final Key secretKey;
  private final UserDatabaseTable userDatabaseTable;

  /**
   * Is used to authenticate a user request
   * @param request The request
   * @return The user
   */
  protected CompletableFuture<User> findUser(HttpServletRequest request) {
    try {
      var apiKey = findApiKey(request);
      if (!isValidApiKey(apiKey)) {
        return CompletableFuture.failedFuture(
          new Exception("User authentication failed. The api key is wrong."));
      }
      var userId = findUserId(apiKey);
      return userDatabaseTable.exists(userId)
        .thenCompose(exists -> findUser(userId, exists));
    } catch (Exception exception) {
      return CompletableFuture.failedFuture(exception);
    }
  }

  private CompletableFuture<User> findUser(
    UUID userId, boolean exists
  ) {
    if (!exists) {
      return CompletableFuture.failedFuture(new Exception(
        "User authentication failed. User does not exists (" + userId + ")"));
    }
    return userDatabaseTable.findUser(userId);
  }

  /**
   * Is used to find the id of the user that send the request
   * @param request The request
   * @return The id of the user
   */
  protected UUID findUserId(HttpServletRequest request) {
    return findUserId(findApiKey(request));
  }

  /**
   * Is used to find the id of a user inside an api key
   * @param apiKey The api key
   * @return The id of the user
   */
  protected UUID findUserId(String apiKey) {
    return UUID.fromString(Jwts.parser().setSigningKey(secretKey).build()
      .parseClaimsJws(apiKey).getPayload().get("id", String.class));
  }

  /**
   * Is used to find the id of the session that send the request
   * @param request The request
   * @return The id of the session
   */
  protected UUID findSessionId(HttpServletRequest request) {
    return findSessionId(findApiKey(request));
  }

  /**
   * Is used to find the id of a session inside an api key
   * @param apiKey The api key
   * @return The id of the session
   */
  protected UUID findSessionId(String apiKey) {
    return UUID.fromString(Jwts.parser().setSigningKey(secretKey).build()
      .parseClaimsJws(apiKey).getPayload().get("session", String.class));
  }

  /**
   * Is used to find the api key that is sent via a request
   * @param request The request
   * @return The api key
   */
  protected String findApiKey(HttpServletRequest request) {
    return request.getHeader("Authorization").replace("Bearer ", "");
  }

  /**
   * Checks whether an api key is valid
   * @param apiKey The api key
   * @return Is true if api key is valid, otherwise false
   */
  protected boolean isValidApiKey(String apiKey) {
    try {
      Jwts.parser()
        .setSigningKey(secretKey)
        .build()
        .parseClaimsJws(apiKey);
      return true;
    } catch (Exception exception) {
      return false;
    }
  }
}
