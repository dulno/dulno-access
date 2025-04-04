package com.dulno.access.user;

import com.dulno.core.hashing.Hashing;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.partner.Partner;
import com.dulno.core.partner.PartnerDatabaseTable;
import com.dulno.core.partner.PartnerLogoDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserPartnerController extends UserController {
  private final PartnerDatabaseTable partnerDatabaseTable;
  private final PartnerLogoDatabaseTable partnerLogoDatabaseTable;

  private UserPartnerController(
    UserDatabaseTable userDatabaseTable, Hashing hashing,
    PartnerDatabaseTable partnerDatabaseTable,
    PartnerLogoDatabaseTable partnerLogoDatabaseTable
  ) {
    super(userDatabaseTable, hashing);
    this.partnerDatabaseTable = partnerDatabaseTable;
    this.partnerLogoDatabaseTable = partnerLogoDatabaseTable;
  }

  @RequestMapping(path = "/user/partners/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findAllPartners(
    HttpServletRequest request
  ) {
    return findUser(request).thenCompose(this::findAllPartners);
  }

  private CompletableFuture<Map<String, Object>> findAllPartners(User user) {
    return partnerDatabaseTable.findAllPartners()
      .thenCompose(partners -> AsyncIterator.execute(partners,
        partner -> partnerLogoDatabaseTable.findPartnerLogoId(partner.id())
          .thenApply(logoId -> assemblyPartnerInformation(partner, logoId))))
      .thenApply(partners -> Map.of("partners", partners));
  }

  private Map<String, Object> assemblyPartnerInformation(
    Partner partner, UUID logoId
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", partner.id());
    information.put("name", partner.name());
    information.put("description", partner.description());
    information.put("logoId", logoId);
    return information;
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
        .thenCompose(exists -> findPartnerLogo(partnerId, exists)));
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
