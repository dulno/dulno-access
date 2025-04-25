package com.dulno.access.portal.balance;

import com.dulno.access.portal.PortalRestController;
import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.Partner;
import com.dulno.core.partner.PartnerDatabaseTable;
import com.dulno.core.partner.analysis.card.PartnerActiveCardDatabaseTable;
import com.dulno.core.partner.analysis.card.PartnerActiveCardEntry;
import com.dulno.core.partner.analysis.stamp.PartnerOpenStampDatabaseTable;
import com.dulno.core.partner.analysis.stamp.PartnerOpenStampEntry;
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
  private final CardDatabaseTable cardDatabaseTable;

  private BalanceController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    PartnerDatabaseTable partnerDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    PartnerTransactionDatabaseTable partnerTransactionDatabaseTable,
    PartnerActiveCardDatabaseTable partnerActiveCardDatabaseTable,
    PartnerOpenStampDatabaseTable partnerOpenStampDatabaseTable,
    CardDatabaseTable cardDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable);
    this.partnerDatabaseTable = partnerDatabaseTable;
    this.partnerMemberDatabaseTable = partnerMemberDatabaseTable;
    this.partnerTransactionDatabaseTable = partnerTransactionDatabaseTable;
    this.partnerActiveCardDatabaseTable = partnerActiveCardDatabaseTable;
    this.partnerOpenStampDatabaseTable = partnerOpenStampDatabaseTable;
    this.cardDatabaseTable = cardDatabaseTable;
  }

  @PortalEndpoint
  @RequestMapping(path = "/balances/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findBalances(
    HttpServletRequest request
  ) {
    return findMember(request)
      .thenCompose(member -> partnerMemberDatabaseTable
        .findMemberPartner(member.id())
        .thenCompose(partnerDatabaseTable::findPartner)
        .thenCompose(this::performAnalysis));
  }

  private CompletableFuture<Map<String, Object>> performAnalysis(
    Partner partner
  ) {
    return collectPartnerTransactions(partner)
      .thenCompose(transactions -> collectPartnerBalanceStatistics(partner)
        .thenApply(statistics -> assemblyAnalysisInformation(transactions,
          statistics)));
  }

  private Map<String, Object> assemblyAnalysisInformation(
    Map<String, Object> transactions, Map<String, Object> statistics
  ) {
    var information = Maps.<String, Object>newHashMap();
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
        .thenApply(openStamps -> assemblyPartnerBalanceStatisticsInformation(
          activeCards, openStamps)));
  }

  private Map<String, Object> assemblyPartnerBalanceStatisticsInformation(
    List<PartnerActiveCardEntry> activeCards,
    List<PartnerOpenStampEntry> openStamps
  ) {
    var information = Maps.<String, Object>newHashMap();
    var activeCardsInformation = Lists.<Map<String, Object>>newArrayList();
    for (var activeCard : activeCards) {
      activeCardsInformation.add(
        assemblyPartnerActiveCardInformation(activeCard));
    }
    information.put("activeCards", activeCardsInformation);
    var openStampsInformation = Lists.<Map<String, Object>>newArrayList();
    for (var openStamp : openStamps) {
      openStampsInformation.add(
        assemblyPartnerOpenStampInformation(openStamp));
    }
    information.put("openStamps", openStampsInformation);
    return information;
  }

  private Map<String, Object> assemblyPartnerActiveCardInformation(
    PartnerActiveCardEntry activeCard
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("change", activeCard.change());
    information.put("date", activeCard.date());
    return information;
  }

  private Map<String, Object> assemblyPartnerOpenStampInformation(
    PartnerOpenStampEntry openStamp
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("change", openStamp.change());
    information.put("date", openStamp.date());
    return information;
  }
}
