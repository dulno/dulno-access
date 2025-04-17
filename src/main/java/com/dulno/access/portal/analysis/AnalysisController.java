package com.dulno.access.portal.analysis;

import com.dulno.access.portal.PortalRestController;
import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.locale.Translation;
import com.dulno.core.member.Member;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.Partner;
import com.dulno.core.partner.PartnerDatabaseTable;
import com.dulno.core.partner.analysis.*;
import com.google.api.client.util.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class AnalysisController extends PortalRestController {
  private final PartnerDatabaseTable partnerDatabaseTable;
  private final PartnerScanDatabaseTable partnerScanDatabaseTable;
  private final PartnerActivityDatabaseTable partnerActivityDatabaseTable;
  private final PartnerNewUserDatabaseTable partnerNewUserDatabaseTable;
  private final PartnerNewCardDatabaseTable partnerNewCardDatabaseTable;
  private final PartnerUserLanguageDatabaseTable partnerUserLanguageDatabaseTable;
  private final Translation translation;

  private AnalysisController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    PartnerDatabaseTable partnerDatabaseTable,
    PartnerScanDatabaseTable partnerScanDatabaseTable,
    PartnerActivityDatabaseTable partnerActivityDatabaseTable,
    PartnerNewUserDatabaseTable partnerNewUserDatabaseTable,
    PartnerNewCardDatabaseTable partnerNewCardDatabaseTable,
    PartnerUserLanguageDatabaseTable partnerUserLanguageDatabaseTable,
    Translation translation
  ) {
    super(secretKey, memberDatabaseTable);
    this.partnerDatabaseTable = partnerDatabaseTable;
    this.partnerScanDatabaseTable = partnerScanDatabaseTable;
    this.partnerActivityDatabaseTable = partnerActivityDatabaseTable;
    this.partnerNewUserDatabaseTable = partnerNewUserDatabaseTable;
    this.partnerNewCardDatabaseTable = partnerNewCardDatabaseTable;
    this.partnerUserLanguageDatabaseTable = partnerUserLanguageDatabaseTable;
    this.translation = translation;
  }

  @PortalEndpoint
  @RequestMapping(path = "/analysis/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> performAnalysis(
    HttpServletRequest request
  ) {
    return findMember(request).thenApply(member ->
      Maps.newHashMap());
  }

  private CompletableFuture<Map<String, Object>> performAnalysis(
    Member member, Partner partner
  ) {
    return collectPartnerUserInformation(partner)
      .thenApply(userInformation -> assemblyAnalysisInformation(userInformation));
  }

  private Map<String, Object> assemblyAnalysisInformation(
    Map<String, Object> userInformation
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.putAll(userInformation);
    return information;
  }

  private CompletableFuture<Map<String, Object>> collectPartnerUserInformation(
    Partner partner
  ) {
    return partnerNewUserDatabaseTable.findPartnerNewUsers(partner.id())
      .thenCompose(newUserDates -> partnerUserLanguageDatabaseTable
        .findPartnerUserLanguages(partner.id())
        .thenApply(languagesNumber -> assemblyPartnerUserInformation(newUserDates,
          languagesNumber)));
  }

  private Map<String, Object> assemblyPartnerUserInformation(
    List<Long> newUserDates, Map<String, Long> languages
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("newUsersTimestamps", newUserDates);
    information.put("userTimestamps", newUserDates);
    information.put("languages", assemblyPartnerLanguageInformation(languages));
    return information;
  }

  private Map<String, Object> assemblyPartnerLanguageInformation(
    Map<String, Long> languages
  ) {
    var entries = languages.entrySet();
    var labels = Lists.<String>newArrayList();
    var total = 0D;
    for (var entry : entries) {
      labels.add(entry.getKey());
      total += entry.getValue();
    }
    var shares = Lists.<Double>newArrayList();
    for (var entry : entries) {
      shares.add(entry.getValue() / total);
    }
    return Map.of("languages", labels, "shares", shares);
  }
}
