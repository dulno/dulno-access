package com.dulno.access.portal.setting;

import com.dulno.access.portal.security.mixed.MixedEndpoint;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.member.session.MemberSession;
import com.dulno.core.member.session.MemberSessionDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.session.SessionStatus;
import com.google.common.collect.Maps;
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
public final class SessionSettingController extends SettingController {
  private final MemberSessionDatabaseTable sessionDatabaseTable;

  private SessionSettingController(
    @Qualifier("portalKey") Key portalKey, @Qualifier("homeKey") Key homeKey,
    MemberDatabaseTable memberDatabaseTable,
    MemberSessionDatabaseTable sessionDatabaseTable
  ) {
    super(portalKey, homeKey, memberDatabaseTable);
    this.sessionDatabaseTable = sessionDatabaseTable;
  }

  @MixedEndpoint
  @RequestMapping(path = "/settings/sessions/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> sessions(
    HttpServletRequest request
  ) {
    var memberSessionId = findSessionId(request);
    return sessionDatabaseTable.findSessionsOfMemberByStatus(
      findMemberId(request), SessionStatus.ACTIVE)
      .thenApply(sessions -> Map.of("sessions", sessions.stream().map(session ->
        assemblySessionInformation(memberSessionId, session)).toList()));
  }

  private Map<String, Object> assemblySessionInformation(
    UUID memberSessionId, MemberSession session
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", session.id());
    information.put("platform", session.devicePlatform());
    information.put("country", session.country());
    information.put("city", session.city());
    information.put("openTime", session.openTime());
    information.put("status", session.status());
    information.put("isCurrent", memberSessionId.equals(session.id()));
    return information;
  }

  @MixedEndpoint
  @RequestMapping(path = "/settings/session/close/", method = RequestMethod.POST)
  public void closeSession(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var sessionId = body.getUUID("session");
    var memberId = findMemberId(request);
    var memberSessionId = findSessionId(request);
    sessionDatabaseTable.sessionExists(sessionId)
      .thenAccept(exists -> closeSession(sessionId, memberId, memberSessionId, exists));
  }

  private void closeSession(
    UUID sessionId, UUID memberId, UUID memberSessionId, boolean sessionExists
  ) {
    if (!sessionExists) {
      return;
    }
    sessionDatabaseTable.findSession(sessionId)
      .thenAccept(session -> closeSession(session, memberId, memberSessionId));
  }

  private void closeSession(
    MemberSession session, UUID memberId, UUID memberSessionId
  ) {
    if (!session.memberId().equals(memberId)) {
      return;
    }
    if (memberSessionId.equals(session.id())) {
      return;
    }
    sessionDatabaseTable.closeSession(session.id());
  }
}