package com.dulno.access.portal.partner;

import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.locale.Translation;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.*;
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
  private final Translation translation;

  private PartnerInformationController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    PartnerDatabaseTable partnerDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    PartnerLogoDatabaseTable partnerLogoDatabaseTable,
    PartnerLocationDatabaseTable partnerLocationDatabaseTable,
    PartnerLinkDatabaseTable partnerLinkDatabaseTable, Translation translation
  ) {
    super(secretKey, memberDatabaseTable, partnerDatabaseTable,
      partnerMemberDatabaseTable, partnerLogoDatabaseTable,
      partnerLocationDatabaseTable, partnerLinkDatabaseTable);
    this.translation = translation;
  }

  @PortalEndpoint
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

  @PortalEndpoint
  @RequestMapping(path = "/partner/logo/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findPartnerLogo(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performPartnerOperation(findMemberId(request),
      partner -> partnerLogoDatabaseTable().findPartnerLogo(partner.id())
        .thenAccept(logo -> futureResponse.complete(Map.of("logoId", logo.logoId(),
          "logo", Base64.getEncoder().encodeToString(logo.content())))),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  @PortalEndpoint
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
      locationInformation.put("creation", location.creation());
      locationsInformation.add(locationInformation);
    }
    return Map.of("locations", locationsInformation);
  }

  @PortalEndpoint
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
      linkInformation.put("creation", link.creation());
      linksInformation.add(linkInformation);
    }
    return Map.of("links", linksInformation);
  }

  @PortalEndpoint
  @RequestMapping(path = "/partner/link/types/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findPartnerLinkTypes(
    HttpServletRequest request
  ) {
    return findMember(request).thenApply(member ->
      Map.of("types", Arrays.stream(PartnerLinkType.values())
        .map(entry -> Map.of("type", entry, "name",
          translation.translateMember(member, "partner.link.type." +
            entry.toString().toLowerCase())))
        .toList()));
  }
}

