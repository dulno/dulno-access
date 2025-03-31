package com.dulno.access.portal.stamp;

import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.PartnerMemberDatabaseTable;
import com.dulno.core.stamp.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StampModificationController extends StampController {
  private final CardDatabaseTable cardDatabaseTable;

  private StampModificationController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    StampDatabaseTable stampDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    CardDatabaseTable cardDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable, stampDatabaseTable,
      partnerMemberDatabaseTable);
    this.cardDatabaseTable = cardDatabaseTable;
  }

  @RequestMapping(path = "/stamp/rename/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> renameStamp(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performStampOperation(findMemberId(request), body.getUUID("stamp"),
      stamp -> renameStamp(stamp, body.getSanitizedString("name", 64))
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  public CompletableFuture<Map<String, Object>> renameStamp(
    Stamp stamp, String name
  ) {
    stamp.changeName(name);
    return stampDatabaseTable().updateStamp(stamp)
      .thenApply(value -> Map.of("success", true));
  }

  @RequestMapping(path = "/stamp/status/change/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> changeStampState(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performStampOperation(findMemberId(request), body.getUUID("stamp"),
      stamp -> changeStampState(stamp, StampState.valueOf(body.getString("state")))
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  public CompletableFuture<Map<String, Object>> changeStampState(
    Stamp stamp, StampState state
  ) {
    stamp.changeState(state);
    return stampDatabaseTable().updateStamp(stamp)
      .thenApply(value -> Map.of("success", true));
  }

  @RequestMapping(path = "/stamp/assign/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> assignStamp(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var assignedCard = body.getUUID("assignedCard");
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performStampOperation(findMemberId(request), body.getUUID("stamp"),
      stamp -> cardDatabaseTable.cardExists(assignedCard)
        .thenAccept(exists -> assignStamp(stamp, assignedCard,
          StampAssignmentRole.valueOf("assignmentRole"), exists)
          .thenAccept(futureResponse::complete)),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  public CompletableFuture<Map<String, Object>> assignStamp(
    Stamp stamp, UUID assignedCard, StampAssignmentRole assignmentRole,
    boolean cardExists
  ) {
    if (!cardExists) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return cardDatabaseTable.findCard(assignedCard)
      .thenCompose(card -> assignStamp(stamp, card, assignmentRole));
  }

  public CompletableFuture<Map<String, Object>> assignStamp(
    Stamp stamp, Card assignedCard, StampAssignmentRole assignmentRole
  ) {
    if (assignedCard.type().isCollection() && !assignmentRole.isCollectionReward()) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    if (assignedCard.type().isValue() && (!assignmentRole.isValueCredit() &&
      !assignmentRole.isValueDebit())
    ) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    if (assignedCard.type().isMember() && (!assignmentRole.isMemberIssue() &&
      !assignmentRole.isMemberRevoke())
    ) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    stamp.changeAssignment(assignedCard.id(), assignmentRole);
    return stampDatabaseTable().updateStamp(stamp)
      .thenApply(value -> Map.of("success", true));
  }
}
