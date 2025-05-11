package com.dulno.access.portal.verification;

import com.dulno.core.hashing.Hashing;
import com.dulno.core.member.MemberDatabaseTable;
import io.jsonwebtoken.Jwts;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.security.Key;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
@AllArgsConstructor(staticName = "create")
public final class Verification {
  private final MemberDatabaseTable memberDatabaseTable;
  private final Key homeSecret;
  private final Key productSecret;
  private final Key refreshSecret;
  private final Hashing hashing;
  @Getter
  private String email;
  private String password;

  public CompletableFuture<Boolean> isAuthenticated() {
    var futureResponse = new CompletableFuture<Boolean>();
    if (email == null || password == null || email.isEmpty()) {
      futureResponse.complete(false);
      return futureResponse;
    }
    memberDatabaseTable.memberExists(email).thenAccept(exists ->
      isAuthenticated(exists).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Boolean> isAuthenticated(boolean exists) {
    var futureResponse = new CompletableFuture<Boolean>();
    if (!exists) {
      futureResponse.complete(false);
      return futureResponse;
    }
    memberDatabaseTable.findMember(email).thenAccept(member ->
      futureResponse.complete(hashing.matches(password, member.passwordHash())));
    return futureResponse;
  }

  private static final long MAXIMUM_PRODUCT_EXPIRATION_TIME = 1000L * 60 * 10;

  public String generateProductApiKey(UUID memberId, UUID sessionId) {
    return generateProductApiKey(memberId, sessionId,
      System.currentTimeMillis() + MAXIMUM_PRODUCT_EXPIRATION_TIME);
  }

  public String generateProductApiKey(UUID memberId, UUID sessionId, long expiration) {
    if (expiration - System.currentTimeMillis() > MAXIMUM_PRODUCT_EXPIRATION_TIME) {
      expiration = System.currentTimeMillis() + MAXIMUM_PRODUCT_EXPIRATION_TIME;
    }
    var expirationDate = new Date(expiration);
    return Jwts.builder().expiration(expirationDate)
      .claim("id", memberId.toString())
      .claim("session", sessionId.toString())
      .signWith(productSecret)
      .compact();
  }

  private static final long MAXIMUM_REFRESH_EXPIRATION_TIME = 1000L * 60 * 60 * 24 * 30;

  public String generateRefreshToken(UUID memberId, UUID sessionId) {
    return generateRefreshToken(memberId, sessionId,
      System.currentTimeMillis() + MAXIMUM_REFRESH_EXPIRATION_TIME);
  }

  public String generateRefreshToken(UUID memberId, UUID sessionId, long expiration) {
    if (expiration - System.currentTimeMillis() > MAXIMUM_REFRESH_EXPIRATION_TIME) {
      expiration = System.currentTimeMillis() + MAXIMUM_REFRESH_EXPIRATION_TIME;
    }
    var expirationDate = new Date(expiration);
    return Jwts.builder().expiration(expirationDate)
      .claim("id", memberId.toString())
      .claim("session", sessionId.toString())
      .signWith(refreshSecret)
      .compact();
  }

  private static final long MAXIMUM_HOME_EXPIRATION_TIME = 1000L * 60 * 60;

  public String generateHomeApiKey(UUID memberId, UUID sessionId) {
    var expirationDate = new Date(System.currentTimeMillis() +
      MAXIMUM_HOME_EXPIRATION_TIME);
    return Jwts.builder().expiration(expirationDate)
      .claim("id", memberId.toString())
      .claim("session", sessionId.toString())
      .signWith(homeSecret)
      .compact();
  }
}
