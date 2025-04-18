package com.dulno.access.portal.analysis;

import com.dulno.access.portal.PortalRestController;
import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.locale.Translation;
import com.dulno.core.member.Member;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.Partner;
import com.dulno.core.partner.PartnerDatabaseTable;
import com.dulno.core.partner.analysis.*;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import com.google.api.client.util.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
public final class AnalysisController extends PortalRestController {
  private final PartnerDatabaseTable partnerDatabaseTable;
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;
  private final PartnerScanDatabaseTable partnerScanDatabaseTable;
  private final PartnerNewUserDatabaseTable partnerNewUserDatabaseTable;
  private final PartnerNewCardDatabaseTable partnerNewCardDatabaseTable;
  private final PartnerCardUsageDatabaseTable partnerCardUsageDatabaseTable;
  private final PartnerUserLanguageDatabaseTable partnerUserLanguageDatabaseTable;
  private final Translation translation;

  private AnalysisController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    PartnerDatabaseTable partnerDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    PartnerScanDatabaseTable partnerScanDatabaseTable,
    PartnerNewUserDatabaseTable partnerNewUserDatabaseTable,
    PartnerNewCardDatabaseTable partnerNewCardDatabaseTable,
    PartnerCardUsageDatabaseTable partnerCardUsageDatabaseTable,
    PartnerUserLanguageDatabaseTable partnerUserLanguageDatabaseTable,
    Translation translation
  ) {
    super(secretKey, memberDatabaseTable);
    this.partnerDatabaseTable = partnerDatabaseTable;
    this.partnerMemberDatabaseTable = partnerMemberDatabaseTable;
    this.partnerScanDatabaseTable = partnerScanDatabaseTable;
    this.partnerNewUserDatabaseTable = partnerNewUserDatabaseTable;
    this.partnerNewCardDatabaseTable = partnerNewCardDatabaseTable;
    this.partnerCardUsageDatabaseTable = partnerCardUsageDatabaseTable;
    this.partnerUserLanguageDatabaseTable = partnerUserLanguageDatabaseTable;
    this.translation = translation;
  }

  @PortalEndpoint
  @RequestMapping(path = "/analysis/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> performAnalysis(
    HttpServletRequest request
  ) {
    return findMember(request)
      .thenCompose(member -> partnerMemberDatabaseTable.findMemberPartner(member.id())
        .thenCompose(partnerDatabaseTable::findPartner)
        .thenCompose(partner -> performAnalysis(member, partner)));
  }

  private CompletableFuture<Map<String, Object>> performAnalysis(
    Member member, Partner partner
  ) {
    return collectPartnerStampInformation(partner)
      .thenCompose(stampInformation -> collectPartnerUserInformation(member, partner)
        .thenCompose(userInformation -> collectPartnerCardInformation(member, partner)
          .thenApply(cardInformation -> assemblyAnalysisInformation(
            stampInformation, userInformation, cardInformation))));
  }

  private Map<String, Object> assemblyAnalysisInformation(
    Map<String, Object> stampInformation, Map<String, Object> userInformation,
    Map<String, Object> cardInformation
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.putAll(stampInformation);
    information.putAll(userInformation);
    information.putAll(cardInformation);
    return information;
  }

  private CompletableFuture<Map<String, Object>> collectPartnerStampInformation(
    Partner partner
  ) {
    return partnerScanDatabaseTable.findPartnerScans(partner.id())
      .thenApply(this::assemblyPartnerStampInformation);
  }

  private Map<String, Object> assemblyPartnerStampInformation(
    List<Long> scanDates
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("stamps", scanDates.size());
    information.put("stampTimestamps", scanDates);
    information.put("weekdayDistribution",
      calculatePartnerWeekdayDistribution(scanDates));
    return information;
  }

  private double[] calculatePartnerWeekdayDistribution(List<Long> scanDates) {
    if (scanDates.isEmpty()) {
      return new double[7];
    }
    var counts = countEventsPerWeekday(scanDates);
    var start = findMinDate(scanDates);
    var end = findMaxDate(scanDates);
    var dayCounts = countDaysPerWeekday(start, end);
    var averages = new double[7];
    for (var i = 1; i <= 7; i++) {
      var dow = DayOfWeek.of(i);
      var totalEvents = counts.getOrDefault(dow, 0L);
      var totalDays = dayCounts.getOrDefault(dow, 0L);
      var avg = 0.0D;
      if (totalDays > 0) {
        avg = totalEvents / (double) totalDays;
      }
      averages[i - 1] = avg;
    }
    return averages;
  }

  private Map<DayOfWeek, Long> countEventsPerWeekday(List<Long> timestamps) {
    return timestamps.stream()
      .map(t -> Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
        .toLocalDate().getDayOfWeek())
      .collect(Collectors.groupingBy(day -> day,
        () -> new EnumMap<>(DayOfWeek.class), Collectors.counting()));
  }

  private LocalDate findMinDate(List<Long> timestamps) {
    return timestamps.stream()
      .map(t -> Instant.ofEpochMilli(t)
        .atZone(ZoneId.systemDefault()).toLocalDate())
      .min(Comparator.naturalOrder()).get();
  }

  private LocalDate findMaxDate(List<Long> timestamps) {
    return timestamps.stream()
      .map(t -> Instant.ofEpochMilli(t)
        .atZone(ZoneId.systemDefault()).toLocalDate())
      .max(Comparator.naturalOrder()).get();
  }

  private Map<DayOfWeek, Long> countDaysPerWeekday(LocalDate start, LocalDate end) {
    var map = new EnumMap<DayOfWeek, Long>(DayOfWeek.class);
    var totalDays = ChronoUnit.DAYS.between(start, end) + 1;
    for (var i = 0L; i < totalDays; i++) {
      var dow = start.plusDays(i).getDayOfWeek();
      map.put(dow, map.getOrDefault(dow, 0L) + 1);
    }
    return map;
  }

  private CompletableFuture<Map<String, Object>> collectPartnerUserInformation(
    Member member, Partner partner
  ) {
    return partnerNewUserDatabaseTable.findPartnerNewUsers(partner.id())
      .thenCompose(newUserDates -> partnerUserLanguageDatabaseTable
        .findPartnerUserLanguages(partner.id())
        .thenApply(languagesNumber -> assemblyPartnerUserInformation(member,
          newUserDates, languagesNumber)));
  }

  private Map<String, Object> assemblyPartnerUserInformation(
    Member member, List<Long> newUserDates, Map<String, Long> languages
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("users", newUserDates.size());
    information.put("userTimestamps", newUserDates);
    information.put("languageDistribution",
      assemblyPartnerLanguageInformation(member, languages));
    return information;
  }

  private Map<String, Object> assemblyPartnerLanguageInformation(
    Member member, Map<String, Long> languages
  ) {
    var entries = languages.entrySet();
    var labels = Lists.<String>newArrayList();
    var shares = Lists.<Long>newArrayList();
    for (var entry : entries) {
      labels.add(formatUserLanguage(member, entry.getKey()));
      shares.add(entry.getValue());
    }
    return Map.of("languages", labels, "shares", shares);
  }

  private String formatUserLanguage(Member member, String language) {
    if (language.equalsIgnoreCase("de")) {
      return translation.translateMember(member, "analysis.language.german");
    }
    return translation.translateMember(member, "analysis.language.english");
  }

  private CompletableFuture<Map<String, Object>> collectPartnerCardInformation(
    Member member, Partner partner
  ) {
    return partnerNewCardDatabaseTable.findPartnerNewCards(partner.id())
      .thenCompose(newCardDates -> partnerCardUsageDatabaseTable
        .findPartnerCardUsages(partner.id())
        .thenApply(cardUsages -> assemblyPartnerCardInformation(member,
          newCardDates, cardUsages)));
  }

  private Map<String, Object> assemblyPartnerCardInformation(
    Member member, List<Long> newCardDates, Map<PartnerCardState, Long> cardUsages
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("cards", newCardDates.size());
    information.put("cardTimestamps", newCardDates);
    information.put("cardUsages",
      assemblyPartnerCardUsageInformation(member, cardUsages));
    return information;
  }

  private Map<String, Object> assemblyPartnerCardUsageInformation(
    Member member, Map<PartnerCardState, Long> cardUsages
  ) {
    var labels = Lists.<String>newArrayList();
    var usages = Lists.<Long>newArrayList();
    for (var state : PartnerCardState.values()) {
      labels.add(formatPartnerCardState(member, state));
      usages.add(cardUsages.containsKey(state) ? cardUsages.get(state) : 0);
    }
    return Map.of("states", labels, "usages", usages);
  }

  private String formatPartnerCardState(Member member, PartnerCardState state) {
    if (state.isUnused()) {
      return translation.translateMember(member, "analysis.card.state.unused");
    } else if (state.isUsed()) {
      return translation.translateMember(member, "analysis.card.state.used");
    } else if (state.isUsedUp()) {
      return translation.translateMember(member, "analysis.card.state.used.up");
    }
    return "";
  }
}
