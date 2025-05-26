package com.dulno.access.portal.transaction;

import com.dulno.access.portal.PortalRestController;
import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.item.Item;
import com.dulno.core.item.ItemDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.analysis.card.PartnerActiveCardDatabaseTable;
import com.dulno.core.partner.analysis.card.PartnerCardGroupDatabaseTable;
import com.dulno.core.partner.analysis.card.PartnerCardState;
import com.dulno.core.partner.analysis.card.PartnerCardUsageDatabaseTable;
import com.dulno.core.partner.analysis.stamp.PartnerOpenStampDatabaseTable;
import com.dulno.core.partner.analysis.user.PartnerActiveUserDatabaseTable;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import com.dulno.core.partner.transaction.PartnerTransaction;
import com.dulno.core.partner.transaction.PartnerTransactionDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.item.UserItemDatabaseTable;
import com.google.api.client.util.Lists;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class TransactionController extends PortalRestController {
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;
  private final PartnerTransactionDatabaseTable partnerTransactionDatabaseTable;
  private final PartnerActiveCardDatabaseTable partnerActiveCardDatabaseTable;
  private final PartnerOpenStampDatabaseTable partnerOpenStampDatabaseTable;
  private final PartnerActiveUserDatabaseTable partnerActiveUserDatabaseTable;
  private final PartnerCardGroupDatabaseTable partnerCardGroupDatabaseTable;
  private final PartnerCardUsageDatabaseTable partnerCardUsageDatabaseTable;
  private final CardDatabaseTable cardDatabaseTable;
  private final ItemDatabaseTable itemDatabaseTable;
  private final UserItemDatabaseTable userItemDatabaseTable;

  private TransactionController(
    @Qualifier("portalKey") Key secretKey, MemberDatabaseTable memberDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    PartnerTransactionDatabaseTable partnerTransactionDatabaseTable,
    PartnerActiveCardDatabaseTable partnerActiveCardDatabaseTable,
    PartnerOpenStampDatabaseTable partnerOpenStampDatabaseTable,
    PartnerActiveUserDatabaseTable partnerActiveUserDatabaseTable,
    PartnerCardGroupDatabaseTable partnerCardGroupDatabaseTable,
    PartnerCardUsageDatabaseTable partnerCardUsageDatabaseTable,
    CardDatabaseTable cardDatabaseTable, ItemDatabaseTable itemDatabaseTable,
    UserItemDatabaseTable userItemDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable);
    this.partnerMemberDatabaseTable = partnerMemberDatabaseTable;
    this.partnerTransactionDatabaseTable = partnerTransactionDatabaseTable;
    this.partnerActiveCardDatabaseTable = partnerActiveCardDatabaseTable;
    this.partnerOpenStampDatabaseTable = partnerOpenStampDatabaseTable;
    this.partnerActiveUserDatabaseTable = partnerActiveUserDatabaseTable;
    this.partnerCardGroupDatabaseTable = partnerCardGroupDatabaseTable;
    this.partnerCardUsageDatabaseTable = partnerCardUsageDatabaseTable;
    this.cardDatabaseTable = cardDatabaseTable;
    this.itemDatabaseTable = itemDatabaseTable;
    this.userItemDatabaseTable = userItemDatabaseTable;
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
    processes.add(checkTransactionDeletionPartnerAnalysis(card, item));
    processes.add(checkTransactionDeletionPartnerCardGroup(card, item));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> null);
  }

  private CompletableFuture<Void> checkTransactionDeletionPartnerAnalysis(
    Card card, Item item
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    processes.add(partnerActiveCardDatabaseTable
      .insertPartnerActiveCard(card.partnerId(), -1, System.currentTimeMillis()));
    if (card.type().isValue()) {
      var itemContent = new JSONObject(item.content());
      var stamps = itemContent.getInt("stamps");
      var cardContent = new JSONObject(card.content());
      processes.add(partnerOpenStampDatabaseTable.insertPartnerOpenStamp(
        card.partnerId(), -stamps, System.currentTimeMillis()));
      processes.add(partnerCardUsageDatabaseTable.removePartnerCardUsage(
        card.partnerId(), stamps == cardContent.getInt("value_number") ?
          PartnerCardState.UNUSED : PartnerCardState.USED));
    }
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> null);
  }

  private CompletableFuture<Void> checkTransactionDeletionPartnerCardGroup(
    Card card, Item item
  ) {
    return partnerCardGroupDatabaseTable.partnerCardGroupExists(item.itemId())
      .thenCompose(exists -> exists ?
        partnerCardGroupDatabaseTable.findPartnerCardGroup(item.itemId())
          .thenCompose(partnerCardGroupDatabaseTable::findPartnerCardGroupItems)
          .thenCompose(groupItems -> checkTransactionDeletionPartnerCardGroup(
            card, item, groupItems)) :
        CompletableFuture.completedFuture(null));
  }

  private CompletableFuture<Void> checkTransactionDeletionPartnerCardGroup(
    Card card, Item item, List<UUID> groupItems
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    if (groupItems.size() == 1) {
      processes.add(partnerActiveUserDatabaseTable.insertPartnerActiveUser(
        card.partnerId(), -1, System.currentTimeMillis()));
    }
    processes.add(partnerCardGroupDatabaseTable.deletePartnerCardGroup(
      item.itemId()));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> null);
  }
}
