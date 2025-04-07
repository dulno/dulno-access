package com.dulno.access.user;

import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.partner.*;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserPartnerController extends UserController {
  private final PartnerDatabaseTable partnerDatabaseTable;
  private final PartnerLocationDatabaseTable partnerLocationDatabaseTable;
  private final PartnerLinkDatabaseTable partnerLinkDatabaseTable;
  private final PartnerLogoDatabaseTable partnerLogoDatabaseTable;

  private UserPartnerController(
    UserDatabaseTable userDatabaseTable, Hashing hashing,
    PartnerDatabaseTable partnerDatabaseTable,
    DulnoEnvironment environment, ErrorRepository errorRepository,
    PartnerLocationDatabaseTable partnerLocationDatabaseTable,
    PartnerLinkDatabaseTable partnerLinkDatabaseTable,
    PartnerLogoDatabaseTable partnerLogoDatabaseTable
  ) {
    super(userDatabaseTable, hashing, environment, errorRepository);
    this.partnerDatabaseTable = partnerDatabaseTable;
    this.partnerLocationDatabaseTable = partnerLocationDatabaseTable;
    this.partnerLinkDatabaseTable = partnerLinkDatabaseTable;
    this.partnerLogoDatabaseTable = partnerLogoDatabaseTable;
  }

  @RequestMapping(path = "/user/partners/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findAllPartners(
    HttpServletRequest request
  ) {
    return findUser(request).thenCompose(this::findAllPartners)
      .exceptionally(throwable -> Maps.newHashMap());
  }

  private CompletableFuture<Map<String, Object>> findAllPartners(User user) {
    return partnerDatabaseTable.findAllPartners()
      .thenCompose(partners -> AsyncIterator.execute(partners,
        this::collectPartnerInformation))
      .thenApply(partners -> Map.of("partners", partners));
  }

  @RequestMapping(path = "/user/partner/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findPartner(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var partnerId = body.getUUID("partner");
    return findUser(request)
      .thenCompose(user -> partnerDatabaseTable.partnerExists(partnerId)
        .thenCompose(exists -> findPartner(partnerId, exists)))
      .exceptionally(throwable -> Maps.newHashMap());
  }

  private CompletableFuture<Map<String, Object>> findPartner(
    UUID partnerId, boolean partnerExists
  ) {
    if (!partnerExists) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return partnerDatabaseTable.findPartner(partnerId)
      .thenCompose(this::collectPartnerInformation);
  }

  private CompletableFuture<Map<String, Object>> collectPartnerInformation(
    Partner partner
  ) {
    return partnerLogoDatabaseTable.findPartnerLogoId(partner.id())
      .thenCompose(logoId -> partnerLocationDatabaseTable
        .findPartnerLocations(partner.id())
        .thenCompose(locations -> partnerLinkDatabaseTable
          .findPartnerLinks(partner.id())
          .thenApply(links -> assemblyPartnerInformation(partner, logoId,
            locations, links))));
  }

  private Map<String, Object> assemblyPartnerInformation(
    Partner partner, UUID logoId, List<PartnerLocation> locations,
    List<PartnerLink> links
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", partner.id());
    information.put("name", partner.name());
    information.put("description", partner.description());
    information.put("logoId", logoId);
    information.put("locations", assemblyPartnerLocationInformation(locations));
    information.put("links", assemblyPartnerLinkInformation(links));
    return information;
  }

  private List<Map<String, Object>> assemblyPartnerLocationInformation(
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
    return locationsInformation;
  }

  private List<Map<String, Object>> assemblyPartnerLinkInformation(
    List<PartnerLink> links
  ) {
    links.sort(Comparator.comparing(PartnerLink::type));
    var linksInformation = Lists.<Map<String, Object>>newArrayList();
    for (var link : links) {
      var linkInformation = Maps.<String, Object>newHashMap();
      linkInformation.put("type", link.type().toString());
      linkInformation.put("link", link.link());
      linksInformation.add(linkInformation);
    }
    return linksInformation;
  }

  @RequestMapping(path = "/user/partner/logo/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findPartnerLogo(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var partnerId = body.getUUID("partner");
    return findUser(request)
      .thenCompose(user -> partnerDatabaseTable.partnerExists(partnerId)
        .thenCompose(exists -> findPartnerLogo(partnerId, exists)))
      .exceptionally(throwable -> Maps.newHashMap());
  }

  private CompletableFuture<Map<String, Object>> findPartnerLogo(
    UUID partnerId, boolean partnerExists
  ) {
    if (!partnerExists) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return partnerLogoDatabaseTable.findPartnerLogo(partnerId)
      .thenApply(logo -> Map.of("logoId", logo.logoId(), "logo",
        Base64.getEncoder().encodeToString(logo.content())));
  }
}
