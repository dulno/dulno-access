package com.dulno.access.user;

import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Accessors(fluent = true)
@Getter(AccessLevel.PROTECTED)
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class UserController {
  private final UserDatabaseTable userDatabaseTable;
  private final Hashing hashing;
  private final DulnoEnvironment dulnoEnvironment;
  private final ErrorRepository errorRepository;
  private final SecureRandom random = new SecureRandom();

  private static final long REQUEST_TIMEOUT = 1000L * 60;

  /**
   * Is used to authenticate a user request
   * @param request The request
   * @return The user
   */
  protected CompletableFuture<User> findUser(HttpServletRequest request) {
    try {
      var userId = UUID.fromString(request.getHeader("User"));
      var time = Long.parseLong(request.getHeader("Time"));
      var authentication = request.getHeader("Authentication");
      var currentTime = System.currentTimeMillis();
      var difference = currentTime - time;
      if (Math.abs(difference) > REQUEST_TIMEOUT) {
        return CompletableFuture.failedFuture(new Exception(
          "User authentication failed. Request timeout (" + time + ":" +
            currentTime + ":" + difference + ")"));
      }
      return userDatabaseTable.exists(userId)
        .thenCompose(exists -> findUser(userId, time, authentication, exists));
    } catch (Exception exception) {
      return CompletableFuture.failedFuture(exception);
    }
  }

  private CompletableFuture<User> findUser(
    UUID userId, long time, String authentication, boolean exists
  ) {
    if (!exists) {
      return CompletableFuture.failedFuture(new Exception(
        "User authentication failed. User does not " + "exists (" + userId + ")"));
    }
    return userDatabaseTable.findUser(userId)
      .thenCompose(user -> findUser(user, time, authentication));
  }

  private CompletableFuture<User> findUser(
    User user, long time, String authentication
  ) {
    var actualContent = user.id().toString() + user.authenticationKey() + time;
    if (!hashing.matches(actualContent, authentication)) {
      return CompletableFuture.failedFuture(new Exception(
        "User authentication failed. Hash is invalid (" + authentication + ")"));
    }
    return CompletableFuture.completedFuture(user);
  }

  private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
    "abcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+[]{}|;:'\",.<>?/`~";
  private static final int AUTHENTICATION_KEY_LENGTH = 128;

  /**
   * Is used to generate a new authentication key for a new user
   * @return The generated authentication key
   */
  protected String generateAuthenticationKey() {
    var builder = new StringBuilder(AUTHENTICATION_KEY_LENGTH);
    for (var i = 0; i < AUTHENTICATION_KEY_LENGTH; i++) {
      builder.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
    }
    return builder.toString();
  }

  protected Map<String, Object> processError(Throwable throwable) {
    errorRepository.processError(throwable);
    return Maps.newHashMap();
  }
}
