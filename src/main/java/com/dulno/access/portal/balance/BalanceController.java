package com.dulno.access.portal.balance;

import com.dulno.access.portal.PortalRestController;
import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.bundle.Bundle;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.item.Item;
import com.dulno.core.item.ItemDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
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
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.item.UserItemDatabaseTable;
import com.google.api.client.util.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class BalanceController extends PortalRestController {
  private final PartnerDatabaseTable partnerDatabaseTable;
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;
  private final PartnerTransactionDatabaseTable partnerTransactionDatabaseTable;
  private final PartnerActiveCardDatabaseTable partnerActiveCardDatabaseTable;
  private final PartnerOpenStampDatabaseTable partnerOpenStampDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final CardDatabaseTable cardDatabaseTable;
  private final ItemDatabaseTable itemDatabaseTable;
  private final UserItemDatabaseTable userItemDatabaseTable;

  private BalanceController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    PartnerDatabaseTable partnerDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    PartnerTransactionDatabaseTable partnerTransactionDatabaseTable,
    PartnerActiveCardDatabaseTable partnerActiveCardDatabaseTable,
    PartnerOpenStampDatabaseTable partnerOpenStampDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable,
    CardDatabaseTable cardDatabaseTable, ItemDatabaseTable itemDatabaseTable,
    UserItemDatabaseTable userItemDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable);
    this.partnerDatabaseTable = partnerDatabaseTable;
    this.partnerMemberDatabaseTable = partnerMemberDatabaseTable;
    this.partnerTransactionDatabaseTable = partnerTransactionDatabaseTable;
    this.partnerActiveCardDatabaseTable = partnerActiveCardDatabaseTable;
    this.partnerOpenStampDatabaseTable = partnerOpenStampDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.cardDatabaseTable = cardDatabaseTable;
    this.itemDatabaseTable = itemDatabaseTable;
    this.userItemDatabaseTable = userItemDatabaseTable;
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

  @PortalEndpoint
  @RequestMapping(path = "/transaction/withdraw/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> withdrawTransaction(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var transactionId = body.getUUID("transaction");
    return partnerMemberDatabaseTable.findMemberPartner(findMemberId(request))
      .thenCompose(partnerId -> partnerTransactionDatabaseTable
        .partnerTransactionExists(partnerId, transactionId)
        .thenCompose(exists -> checkTransactionExistence(partnerId,
          transactionId, exists)));
  }

  private CompletableFuture<Map<String, Object>> checkTransactionExistence(
    UUID partnerId, UUID transactionId, boolean transactionExists
  ) {
    if (!transactionExists) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return partnerTransactionDatabaseTable
      .findPartnerTransaction(partnerId, transactionId)
      .thenCompose(transaction -> cardDatabaseTable.findCard(transaction.cardId())
        .thenCompose(card -> itemDatabaseTable.itemExists(transaction.itemId())
          .thenCompose(itemExists -> performTransactionWithdraw(transaction,
            card, itemExists))));
  }

  private CompletableFuture<Map<String, Object>> performTransactionWithdraw(
    PartnerTransaction transaction, Card card, boolean itemExists
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    processes.add(partnerTransactionDatabaseTable.deletePartnerTransaction(
      transaction.partnerId(), transaction.transactionId()));
    if (itemExists) {
      processes.add(itemDatabaseTable.findItem(transaction.itemId())
        .thenCompose(item -> deleteTransactionItem(card, item)));
    }
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> Map.of("success", true));
  }

  private CompletableFuture<Void> deleteTransactionItem(Card card, Item item) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    processes.add(itemDatabaseTable.deleteItem(item.itemId()));
    processes.add(userItemDatabaseTable.userItemExists(item.itemId())
      .thenCompose(exists -> exists ?
        userItemDatabaseTable.findItemUser(item.itemId()).thenCompose(userId ->
          userItemDatabaseTable.deleteUserItem(userId, item.itemId())) :
        CompletableFuture.completedFuture(null)));
    processes.add(partnerActiveCardDatabaseTable
      .insertPartnerActiveCard(card.partnerId(), -1, System.currentTimeMillis()));
    if (card.type().isValue()) {
      var content = new JSONObject(item.content());
      processes.add(partnerOpenStampDatabaseTable.insertPartnerOpenStamp(
        card.partnerId(), -content.getInt("stamps"), System.currentTimeMillis()));
    }
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> null);
  }
}
