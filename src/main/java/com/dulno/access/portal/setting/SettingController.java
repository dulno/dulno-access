package com.dulno.access.portal.setting;

import com.dulno.core.access.PortalRestController;
import com.dulno.core.member.Member;
import com.dulno.core.member.MemberDatabaseTable;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.Accessors;

import java.security.Key;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Accessors(fluent = true)
public class SettingController extends PortalRestController {
  private final Key productKey;
  private final Key homeKey;

  protected SettingController(
    Key productKey, Key homeKey, MemberDatabaseTable memberDatabaseTable
  ) {
    super(productKey, memberDatabaseTable);
    this.productKey = productKey;
    this.homeKey = homeKey;
  }

  protected UUID findMemberId(HttpServletRequest request) {
    if (request.getHeader("Authorization") != null) {
      return findMemberId(findProductApiKey(request), productKey);
    }
    return findMemberId(findHomeApiKey(request), homeKey);
  }

  private UUID findMemberId(String apiKey, Key key) {
    return UUID.fromString(Jwts.parser().setSigningKey(key).build()
      .parseClaimsJws(apiKey).getPayload().get("id", String.class));
  }

  protected UUID findSessionId(HttpServletRequest request) {
    if (request.getHeader("Authorization") != null) {
      return findSessionId(findProductApiKey(request), productKey);
    }
    return findSessionId(findHomeApiKey(request), homeKey);
  }

  private UUID findSessionId(String apiKey, Key key) {
    return UUID.fromString(Jwts.parser().setSigningKey(key).build()
      .parseClaimsJws(apiKey).getPayload().get("session", String.class));
  }

  protected CompletableFuture<Member> findMember(HttpServletRequest request) {
    return memberDatabaseTable().findMember(findMemberId(request));
  }

  private String findProductApiKey(HttpServletRequest request) {
    return request.getHeader("Authorization").replace("Bearer ", "");
  }

  private String findHomeApiKey(HttpServletRequest request) {
    return request.getHeader("Home-Authorization").replace("Bearer ", "");
  }
}
