package com.dulno.access.portal;

import com.dulno.core.member.Member;
import com.dulno.core.member.MemberDatabaseTable;
import jakarta.servlet.http.HttpServletRequest;

import java.security.Key;
import java.util.concurrent.CompletableFuture;

public class HomeRestController extends PortalRestController {
  protected HomeRestController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable);
  }

  @Override
  protected CompletableFuture<Member> findMember(HttpServletRequest request) {
    var apiKey = request.getHeader("Home-Authorization").replace("Bearer ", "");
    return memberDatabaseTable().findMember(findMemberId(apiKey));
  }

  @Override
  protected String findApiKey(HttpServletRequest request) {
    return request.getHeader("Home-Authorization").replace("Bearer ", "");
  }
}
