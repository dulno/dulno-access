package com.dulno.access.portal.stamp;

import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.stamp.Stamp;
import com.dulno.core.stamp.StampDatabaseTable;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.PartnerMemberDatabaseTable;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StampInformationController extends StampController {
  private StampInformationController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    StampDatabaseTable stampDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable, stampDatabaseTable,
      partnerMemberDatabaseTable);
  }

  @RequestMapping(path = "/stamps/list/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findStamps(
    HttpServletRequest request
  ) {
    return partnerMemberDatabaseTable().findMemberPartner(findMemberId(request))
      .thenCompose(partner -> stampDatabaseTable().findStampsOfPartner(partner)
        .thenApply(stamps -> Map.of("stamps", stamps.stream()
          .map(this::assemblyStampInformation).toList())));
  }

  @RequestMapping(path = "/stamp/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findStamp(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performStampOperation(findMemberId(request), body.getUUID("stamp"),
      stamp -> futureResponse.complete(assemblyStampInformation(stamp)),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  private Map<String, Object> assemblyStampInformation(Stamp stamp) {
    var information = Maps.<String, Object>newHashMap();
    information.put("success", true);
    information.put("id", stamp.id());
    information.put("name", stamp.name());
    information.put("type", stamp.type());
    information.put("state", stamp.state());
    information.put("assignedCard", stamp.assignedCard());
    information.put("assignmentRole", stamp.assignmentRole());
    information.put("creation", stamp.creation());
    return information;
  }
}

