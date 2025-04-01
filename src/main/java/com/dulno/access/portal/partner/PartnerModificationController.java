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
    partner.changeDescription(description);
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

  @RequestMapping(path = "/partner/location/change/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> changePartnerLocation(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var previousAddress = body.getSanitizedString("previousAddress");
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performPartnerOperation(findMemberId(request),
      partner -> partnerLocationDatabaseTable()
        .partnerLocationExists(partner.id(), previousAddress)
        .thenAccept(exists -> changePartnerLocation(partner, previousAddress,
          body.getSanitizedString("newAddress"), body.getDouble("newLatitude"),
          body.getDouble("newLongitude"), exists)
          .thenAccept(futureResponse::complete)),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  public CompletableFuture<Map<String, Object>> changePartnerLocation(
    Partner partner, String previousAddress, String newAddress,
    double newLatitude, double newLongitude, boolean previousLocationExists
  ) {
    if (!previousLocationExists) {
      var newLocation = PartnerLocation.create(partner.id(), newAddress,
        newLatitude, newLongitude, System.currentTimeMillis());
      return partnerLocationDatabaseTable().insertPartnerLocation(newLocation)
        .thenApply(value -> Map.of("success", true));
    }
    return partnerLocationDatabaseTable()
      .findPartnerLocation(partner.id(), previousAddress)
      .thenCompose(location -> changePartnerLocation(partner, location,
        newAddress, newLatitude, newLongitude));
  }

  public CompletableFuture<Map<String, Object>> changePartnerLocation(
    Partner partner, PartnerLocation previousLocation, String newAddress,
    double newLatitude, double newLongitude
  ) {
    var newLocation = PartnerLocation.create(partner.id(), newAddress,
      newLatitude, newLongitude, previousLocation.creation());
    return partnerLocationDatabaseTable()
      .deletePartnerLocation(partner.id(), previousLocation.address())
      .thenCompose(value -> partnerLocationDatabaseTable()
        .insertPartnerLocation(newLocation))
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
      partner -> partnerLocationDatabaseTable().countPartnerLocations(partner.id())
        .thenAccept(count -> removePartnerLocation(partner,
          body.getSanitizedString("address"), count)
          .thenAccept(futureResponse::complete)),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  public CompletableFuture<Map<String, Object>> removePartnerLocation(
    Partner partner, String address, long locationCount
  ) {
    if (locationCount == 1) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return partnerLocationDatabaseTable().deletePartnerLocation(partner.id(), address)
      .thenApply(value -> Map.of("success", true));
  }

  @RequestMapping(path = "/partner/link/change/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> changePartnerLink(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var previousType = body.getString("previousType");
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performPartnerOperation(findMemberId(request),
      partner -> partnerLinkDatabaseTable()
        .partnerLinkExists(partner.id(), previousType)
        .thenAccept(exists -> changePartnerLink(partner, previousType,
          body.getString("newType"), body.getSanitizedString("newLink"), exists)
          .thenAccept(futureResponse::complete)),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  public CompletableFuture<Map<String, Object>> changePartnerLink(
    Partner partner, String previousType, String newType,
    String newLinkValue, boolean previousLinkExists
  ) {
    if (!previousLinkExists) {
      var newLink = PartnerLink.create(partner.id(),
        PartnerLinkType.valueOf(newType), newLinkValue, System.currentTimeMillis());
      return partnerLinkDatabaseTable().insertPartnerLink(newLink)
        .thenApply(value -> Map.of("success", true));
    }
    return partnerLinkDatabaseTable()
      .findPartnerLink(partner.id(), previousType)
      .thenCompose(link -> changePartnerLink(partner, link, newType, newLinkValue));
  }

  public CompletableFuture<Map<String, Object>> changePartnerLink(
    Partner partner, PartnerLink previousLink, String newType, String newLinkValue
  ) {
    var newLink = PartnerLink.create(partner.id(), PartnerLinkType.valueOf(newType),
      newLinkValue, previousLink.creation());
    return partnerLinkDatabaseTable()
      .deletePartnerLink(partner.id(), previousLink.type().toString())
      .thenCompose(value -> partnerLinkDatabaseTable().insertPartnerLink(newLink))
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
