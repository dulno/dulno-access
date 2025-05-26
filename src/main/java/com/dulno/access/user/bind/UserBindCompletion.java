package com.dulno.access.user.bind;

import com.dulno.core.session.SessionStatus;
import com.dulno.core.session.UserAgent;
import com.dulno.core.user.User;
import com.dulno.core.user.session.UserSessionDatabaseTable;
import com.maxmind.geoip2.DatabaseReader;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Configuration
@RequiredArgsConstructor
public class UserBindCompletion {
  private final UserTokenFactory userTokenFactory;
  private final UserSessionDatabaseTable sessionDatabaseTable;
  private final DatabaseReader geoDatabaseReader;

  public CompletableFuture<Map<String, Object>> completeBinding(
    HttpServletRequest request, User user
  ) {
    return sessionDatabaseTable.generateAvailableSessionId()
      .thenComposeAsync(sessionId -> completeBinding(request, user, sessionId));
  }

  private CompletableFuture<Map<String, Object>> completeBinding(
    HttpServletRequest request, User user, UUID sessionId
  ) {
    var authenticationToken = userTokenFactory.generateAuthenticationToken(
      user.id(), sessionId);
    var refreshToken = userTokenFactory.generateRefreshToken(user.id(),
      sessionId);
    return storeSession(request, user.id(), sessionId, refreshToken)
      .thenApply(value -> Map.of("success", true, "email", user.email(),
        "user", user.id(), "authenticationToken", authenticationToken,
        "refreshToken", refreshToken));
  }

  private CompletableFuture<Void> storeSession(
    HttpServletRequest request, UUID userId, UUID sessionId, String refreshToken
  ) {
    var country = "";
    var city = "";
    var platform = "";
    var ipAddress = request.getHeader("X-Real-IP");
    try {
      var location = geoDatabaseReader.city(InetAddress.getByName(ipAddress));
      country = location.getCountry().getName();
      city = location.getCity().getName();
      platform = UserAgent.create(request.getHeader("User-Agent"))
        .findPlatform();
    } catch (Exception ignored) {
    }
    return sessionDatabaseTable.insertSession(sessionId, userId,
      SessionStatus.ACTIVE, platform, ipAddress, country, city,
      System.currentTimeMillis(), refreshToken, System.currentTimeMillis());
  }
}
