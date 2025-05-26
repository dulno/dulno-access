package com.dulno.access.user.bind;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Component
public final class UserTokenFactory {
  private final Key appKey;
  private final Key appRefreshKey;

  public UserTokenFactory(
    @Qualifier("appKey") Key appKey, @Qualifier("appRefreshKey") Key appRefreshKey
  ) {
    this.appKey = appKey;
    this.appRefreshKey = appRefreshKey;
  }

  private static final long MAXIMUM_AUTHENTICATION_EXPIRATION_TIME =
    1000L * 60 * 10;

  public String generateAuthenticationToken(UUID userId, UUID sessionId) {
    var expirationDate = new Date(System.currentTimeMillis() +
      MAXIMUM_AUTHENTICATION_EXPIRATION_TIME);
    return Jwts.builder().expiration(expirationDate)
      .claim("id", userId.toString())
      .claim("session", sessionId.toString())
      .signWith(appKey)
      .compact();
  }

  private static final long MAXIMUM_REFRESH_EXPIRATION_TIME =
    1000L * 60 * 60 * 24 * 365;

  public String generateRefreshToken(UUID userId, UUID sessionId) {
    var expirationDate = new Date(System.currentTimeMillis() +
      MAXIMUM_REFRESH_EXPIRATION_TIME);
    return Jwts.builder().expiration(expirationDate)
      .claim("id", userId.toString())
      .claim("session", sessionId.toString())
      .signWith(appRefreshKey)
      .compact();
  }
}
