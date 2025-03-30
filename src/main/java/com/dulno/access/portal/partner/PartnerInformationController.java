package com.dulno.access.portal.partner;

import com.dulno.core.partner.*;
import com.dulno.core.member.MemberDatabaseTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class PartnerInformationController extends PartnerController {
  private PartnerInformationController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    PartnerDatabaseTable partnerDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    PartnerLogoDatabaseTable partnerLogoDatabaseTable,
    PartnerLocationDatabaseTable partnerLocationDatabaseTable,
    PartnerLinkDatabaseTable partnerLinkDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable, partnerDatabaseTable,
      partnerMemberDatabaseTable, partnerLogoDatabaseTable,
      partnerLocationDatabaseTable, partnerLinkDatabaseTable);
  }

  @RequestMapping(path = "/partner/find/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findPartner(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performPartnerOperation(findMemberId(request),
      partner -> futureResponse.complete(assemblyPartnerInformation(partner)),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  private Map<String, Object> assemblyPartnerInformation(
    Partner partner
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", partner.id());
    information.put("name", partner.name());
    information.put("description", partner.description());
    information.put("accession", partner.accession());
    return information;
  }

  @RequestMapping(path = "/partner/logo/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findPartnerLogo(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performPartnerOperation(findMemberId(request),
      partner -> partnerLogoDatabaseTable().findPartnerLogo(partner.id())
        .thenAccept(logo -> futureResponse.complete(Map.of("logo",
          Base64.getEncoder().encodeToString(logo)))),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  @RequestMapping(path = "/partner/locations/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findPartnerLocations(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performPartnerOperation(findMemberId(request),
      partner -> partnerLocationDatabaseTable().findPartnerLocations(partner.id())
        .thenAccept(locations -> futureResponse.complete(
          assemblyPartnerLocationInformation(locations))),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  private Map<String, Object> assemblyPartnerLocationInformation(
    List<PartnerLocation> locations
  ) {
    var locationsInformation = Lists.<Map<String, Object>>newArrayList();
    for (var location : locations) {
      var locationInformation = Maps.<String, Object>newHashMap();
      locationInformation.put("address", location.address());
      locationInformation.put("latitude", location.latitude());
      locationInformation.put("longitude", location.longitude());
      locationsInformation.add(locationInformation);
    }
    return Map.of("locations", locationsInformation);
  }

  @RequestMapping(path = "/partner/links/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findPartnerLinks(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performPartnerOperation(findMemberId(request),
      partner -> partnerLinkDatabaseTable().findPartnerLinks(partner.id())
        .thenAccept(links -> futureResponse.complete(
          assemblyPartnerLinkInformation(links))),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  private Map<String, Object> assemblyPartnerLinkInformation(
    List<PartnerLink> links
  ) {
    var linksInformation = Lists.<Map<String, Object>>newArrayList();
    for (var link : links) {
      var linkInformation = Maps.<String, Object>newHashMap();
      linkInformation.put("type", link.type().toString());
      linkInformation.put("link", link.link());
      linksInformation.add(linkInformation);
    }
    return Map.of("links", linksInformation);
  }

  @RequestMapping(path = "/partner/link/types/", method = RequestMethod.GET)
  public Map<String, Object> findPartnerLinkTypes() {
    return Map.of("types", Arrays.stream(PartnerLinkType.values()).toList());
  }
}

