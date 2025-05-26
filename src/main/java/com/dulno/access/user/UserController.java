package com.dulno.access.user;

import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
  protected CompletableFuture<User> findUser(
    HttpServletRequest request, HttpServletResponse response
  ) {
    try {
      var apiKey = findApiKey(request);
      var status = checkApiKey(apiKey);
      if (status != HttpServletResponse.SC_ACCEPTED) {
        response.setStatus(status);
        return CompletableFuture.completedFuture(null);
      }
      var userId = findUserId(apiKey);
      return userDatabaseTable.exists(userId)
        .thenCompose(exists -> findUser(response, userId, exists));
    } catch (Exception exception) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return CompletableFuture.completedFuture(null);
    }
  }

  private CompletableFuture<User> findUser(
    HttpServletResponse response, UUID userId, boolean exists
  ) {
    if (!exists) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return CompletableFuture.completedFuture(null);
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
   * Checks whether the request has an Authorization header
   * @param request The request
   * @return Whether there is an Authorization header
   */
  protected boolean hasAuthorization(HttpServletRequest request) {
    return request.getHeader("Authorization") != null;
  }

  /**
   * Checks whether an api key is valid
   * @param apiKey The api key
   * @return The http status
   */
  protected int checkApiKey(String apiKey) {
    try {
      Jwts.parser()
        .setSigningKey(secretKey)
        .build()
        .parseClaimsJws(apiKey);
      return HttpServletResponse.SC_ACCEPTED;
    } catch (ExpiredJwtException exception) {
      return HttpServletResponse.SC_EXPECTATION_FAILED;
    } catch (Exception exception) {
      return HttpServletResponse.SC_FORBIDDEN;
    }
  }
}
