package com.dulno.access.portal.setting;

import com.dulno.access.portal.security.mixed.MixedEndpoint;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class LanguageSettingController extends SettingController {
  private LanguageSettingController(
    @Qualifier("productKey") Key productKey, @Qualifier("homeKey") Key homeKey,
    MemberDatabaseTable memberDatabaseTable
  ) {
    super(productKey, homeKey, memberDatabaseTable);
  }

  @MixedEndpoint
  @RequestMapping(path = "/settings/language/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> language(
    HttpServletRequest request
  ) {
    return findMember(request).thenApply(member ->
      Map.of("language", member.language()));
  }

  @MixedEndpoint
  @RequestMapping(path = "/settings/language/change/", method = RequestMethod.POST)
  public CompletableFuture<Void> changeLanguage(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    return memberDatabaseTable().changeMemberLanguage(findMemberId(request),
      body.getString("language"));
  }
}
