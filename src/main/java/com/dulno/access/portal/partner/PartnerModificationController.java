package com.dulno.access.portal.partner;

import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.*;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class PartnerModificationController extends PartnerController {
  private final PartnerConfiguration partnerConfiguration;

  private PartnerModificationController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    PartnerDatabaseTable partnerDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    PartnerLogoDatabaseTable partnerLogoDatabaseTable,
    PartnerLocationDatabaseTable partnerLocationDatabaseTable,
    PartnerLinkDatabaseTable partnerLinkDatabaseTable,
    PartnerConfiguration partnerConfiguration
  ) {
    super(secretKey, memberDatabaseTable, partnerDatabaseTable,
      partnerMemberDatabaseTable, partnerLogoDatabaseTable,
      partnerLocationDatabaseTable, partnerLinkDatabaseTable);
    this.partnerConfiguration = partnerConfiguration;
  }

  @RequestMapping(path = "/partner/labels/update/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> updatePartnerLabels(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performPartnerOperation(findMemberId(request),
      partner -> updatePartnerLabels(partner, body.getSanitizedString("name", 64),
        body.getSanitizedString("description", 256))
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  public CompletableFuture<Map<String, Object>> updatePartnerLabels(
    Partner partner, String name, String description
  ) {
    partner.changeName(name);
    partner.changeName(description);
    return partnerDatabaseTable().updatePartner(partner)
      .thenApply(value -> Map.of("success", true));
  }

  @RequestMapping(path = "/partner/logo/update/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> updatePartnerLogo(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performPartnerOperation(findMemberId(request),
      partner -> updatePartnerLogo(partner, body.getString("logo"))
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  private static final long MAX_LOGO_SIZE = 1024L * 1024;

  public CompletableFuture<Map<String, Object>> updatePartnerLogo(
    Partner partner, String logo
  ) {
    var parsedLogo = parsePartnerLogo(logo);
    if (parsedLogo.length > MAX_LOGO_SIZE) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return partnerLogoDatabaseTable().updatePartnerLogo(partner.id(), parsedLogo)
      .thenApply(value -> Map.of("success", true));
  }

  private byte[] parsePartnerLogo(String partnerLogo) {
    try {
      return Base64.getDecoder().decode(partnerLogo);
    } catch (Exception exception) {
      return Base64.getDecoder().decode(partnerConfiguration.defaultLogo());
    }
  }

  @RequestMapping(path = "/partner/location/add/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> addPartnerLocation(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performPartnerOperation(findMemberId(request),
      partner -> addPartnerLocation(partner, body.getSanitizedString("address"),
        body.getDouble("latitude"), body.getDouble("longitude"))
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  public CompletableFuture<Map<String, Object>> addPartnerLocation(
    Partner partner, String address, double latitude, double longitude
  ) {
    var location = PartnerLocation.create(partner.id(), address, latitude, longitude);
    return partnerLocationDatabaseTable().insertPartnerLocation(location)
      .thenApply(value -> Map.of("success", true));
  }

  @RequestMapping(path = "/partner/location/remove/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> removePartnerLocation(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performPartnerOperation(findMemberId(request),
      partner -> removePartnerLocation(partner, body.getSanitizedString("address"))
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  public CompletableFuture<Map<String, Object>> removePartnerLocation(
    Partner partner, String address
  ) {
    return partnerLocationDatabaseTable().deletePartnerLocation(partner.id(), address)
      .thenApply(value -> Map.of("success", true));
  }

  @RequestMapping(path = "/partner/link/add/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> addPartnerLink(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performPartnerOperation(findMemberId(request),
      partner -> addPartnerLink(partner,
        PartnerLinkType.valueOf(body.getString("type")),
        body.getSanitizedString("link"))
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  public CompletableFuture<Map<String, Object>> addPartnerLink(
    Partner partner, PartnerLinkType type, String linkValue
  ) {
    var link = PartnerLink.create(partner.id(), type, linkValue);
    return partnerLinkDatabaseTable().insertPartnerLink(link)
      .thenApply(value -> Map.of("success", true));
  }

  @RequestMapping(path = "/partner/link/remove/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> removePartnerLink(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performPartnerOperation(findMemberId(request),
      partner -> removePartnerLink(partner,
        PartnerLinkType.valueOf(body.getString("type")))
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  public CompletableFuture<Map<String, Object>> removePartnerLink(
    Partner partner, PartnerLinkType type
  ) {
    return partnerLinkDatabaseTable().deletePartnerLink(partner.id(), type.toString())
      .thenApply(value -> Map.of("success", true));
  }
}
