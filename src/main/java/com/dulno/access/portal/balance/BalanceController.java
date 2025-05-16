package com.dulno.access.portal.balance;

import com.dulno.access.portal.PortalRestController;
import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.bundle.Bundle;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.Partner;
import com.dulno.core.partner.PartnerDatabaseTable;
import com.dulno.core.partner.analysis.card.PartnerActiveCardDatabaseTable;
import com.dulno.core.partner.analysis.card.PartnerActiveCardEntry;
import com.dulno.core.partner.analysis.stamp.PartnerOpenStampDatabaseTable;
import com.dulno.core.partner.analysis.stamp.PartnerOpenStampEntry;
import com.dulno.core.partner.analysis.user.PartnerActiveUserDatabaseTable;
import com.dulno.core.partner.analysis.user.PartnerActiveUserEntry;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import com.dulno.core.partner.transaction.PartnerTransaction;
import com.dulno.core.partner.transaction.PartnerTransactionDatabaseTable;
import com.google.api.client.util.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@RestController
public final class BalanceController extends PortalRestController {
  private final PartnerDatabaseTable partnerDatabaseTable;
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;
  private final PartnerTransactionDatabaseTable partnerTransactionDatabaseTable;
  private final PartnerActiveCardDatabaseTable partnerActiveCardDatabaseTable;
  private final PartnerOpenStampDatabaseTable partnerOpenStampDatabaseTable;
  private final PartnerActiveUserDatabaseTable partnerActiveUserDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final CardDatabaseTable cardDatabaseTable;

  private BalanceController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    PartnerDatabaseTable partnerDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    PartnerTransactionDatabaseTable partnerTransactionDatabaseTable,
    PartnerActiveCardDatabaseTable partnerActiveCardDatabaseTable,
    PartnerOpenStampDatabaseTable partnerOpenStampDatabaseTable,
    PartnerActiveUserDatabaseTable partnerActiveUserDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable,
    CardDatabaseTable cardDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable);
    this.partnerDatabaseTable = partnerDatabaseTable;
    this.partnerMemberDatabaseTable = partnerMemberDatabaseTable;
    this.partnerTransactionDatabaseTable = partnerTransactionDatabaseTable;
    this.partnerActiveCardDatabaseTable = partnerActiveCardDatabaseTable;
    this.partnerOpenStampDatabaseTable = partnerOpenStampDatabaseTable;
    this.partnerActiveUserDatabaseTable = partnerActiveUserDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.cardDatabaseTable = cardDatabaseTable;
  }

  @PortalEndpoint
  @RequestMapping(path = "/balances/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findBalances(
    HttpServletRequest request
  ) {
    return findMember(request)
      .thenCompose(member -> partnerMemberDatabaseTable.findMemberPartner(member.id())
        .thenCompose(partnerDatabaseTable::findPartner)
        .thenCompose(partner -> bundleDatabaseTable.findBundle(partner.id())
          .thenCompose(bundle -> findBalances(partner, bundle))));
  }

  private CompletableFuture<Map<String, Object>> findBalances(
    Partner partner, Bundle bundle
  ) {
    if (!bundle.balanceAccess()) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return collectPartnerTransactions(partner)
      .thenCompose(transactions -> collectPartnerBalanceStatistics(partner)
        .thenApply(statistics -> assemblyBalancesInformation(transactions,
          statistics)));
  }

  private Map<String, Object> assemblyBalancesInformation(
    Map<String, Object> transactions, Map<String, Object> statistics
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("success", true);
    information.putAll(transactions);
    information.putAll(statistics);
    return information;
  }

  private CompletableFuture<Map<String, Object>> collectPartnerTransactions(
    Partner partner
  ) {
    return partnerTransactionDatabaseTable.findPartnerTransactions(partner.id())
      .thenCompose(transactions -> cardDatabaseTable.findCardsOfPartner(partner.id())
        .thenApply(cards -> assemblyPartnerTransactionsInformation(transactions,
          cards)));
  }

  private Map<String, Object> assemblyPartnerTransactionsInformation(
    List<PartnerTransaction> transactions, List<Card> cards
  ) {
    var information = Maps.<String, Object>newHashMap();
    var transactionsInformation = Lists.<Map<String, Object>>newArrayList();
    for (var transaction : transactions) {
      var card = cards.stream()
        .filter(entry -> entry.id().equals(transaction.cardId())).findFirst();
      transactionsInformation.add(
        assemblyPartnerTransactionInformation(transaction, card));
    }
    information.put("transactions", transactionsInformation);
    return information;
  }

  private Map<String, Object> assemblyPartnerTransactionInformation(
    PartnerTransaction transaction, Optional<Card> cardOptional
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", transaction.transactionId());
    information.put("value", transaction.value());
    if (cardOptional.isPresent()) {
      var card = cardOptional.get();
      information.put("card", card.id());
      information.put("type", card.type());
    }
    information.put("time", transaction.time());
    return information;
  }

  private CompletableFuture<Map<String, Object>> collectPartnerBalanceStatistics(
    Partner partner
  ) {
    return partnerActiveCardDatabaseTable.findPartnerActiveCards(partner.id())
      .thenCompose(activeCards -> partnerOpenStampDatabaseTable
        .findPartnerOpenStamps(partner.id())
        .thenCompose(openStamps -> partnerActiveUserDatabaseTable
          .findPartnerActiveUsers(partner.id())
          .thenApply(activeUsers -> assemblyPartnerBalanceStatisticsInformation(
            activeCards, openStamps, activeUsers))));
  }

  private Map<String, Object> assemblyPartnerBalanceStatisticsInformation(
    List<PartnerActiveCardEntry> activeCards,
    List<PartnerOpenStampEntry> openStamps,
    List<PartnerActiveUserEntry> activeUsers
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("activeCards", activeCards.stream().map(entry ->
      assemblyPartnerGraphEntryInformation(entry.change(), entry.date())).toList());
    information.put("openStamps", openStamps.stream().map(entry ->
      assemblyPartnerGraphEntryInformation(entry.change(), entry.date())).toList());
    information.put("activeUsers", activeUsers.stream().map(entry ->
      assemblyPartnerGraphEntryInformation(entry.change(), entry.date())).toList());
    return information;
  }

  private Map<String, Object> assemblyPartnerGraphEntryInformation(
    int change, long date
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("change", change);
    information.put("date", date);
    return information;
  }
}
