package com.dulno.access.portal.card;

import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.card.CardType;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.PartnerMemberDatabaseTable;
import com.dulno.core.stamp.Stamp;
import com.dulno.core.stamp.StampAssignmentRole;
import com.dulno.core.stamp.StampDatabaseTable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class CardModificationController extends CardController {
  private final StampDatabaseTable stampDatabaseTable;

  private CardModificationController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    CardDatabaseTable cardDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    StampDatabaseTable stampDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable, cardDatabaseTable,
      partnerMemberDatabaseTable);
    this.stampDatabaseTable = stampDatabaseTable;
  }

  @RequestMapping(path = "/card/create/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> createCard(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    return createCard(findMemberId(request),
      body.getSanitizedString("backgroundColor"),
      body.getSanitizedString("foregroundColor"),
      CardType.valueOf(body.getString("type")), body.getObject("content"));
  }

  private CompletableFuture<Map<String, Object>> createCard(
    UUID memberId, String backgroundColor, String foregroundColor, CardType type,
    DulnoRequestBody content
  ) {
    var parsedContent = parseCardContent(type, content);
    return cardDatabaseTable().generateAvailableCardId()
      .thenCompose(cardId -> partnerMemberDatabaseTable().findMemberPartner(memberId)
        .thenCompose(partnerId -> cardDatabaseTable().insertCard(cardId,
            partnerId, backgroundColor, foregroundColor, type.toString(),
            parsedContent, System.currentTimeMillis())
          .thenApply(value -> Map.of("success", true, "card", cardId))));
  }

  @RequestMapping(path = "/card/update/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> updateCard(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCardOperation(findMemberId(request), body.getUUID("card"),
      card -> updateCard(card, body.getString("backgroundColor"),
        body.getString("foregroundColor"), body.getObject("content"))
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> updateCard(
    Card card, String backgroundColor, String foregroundColor,
    DulnoRequestBody content
  ) {
    card.changeBackgroundColor(backgroundColor);
    card.changeForegroundColor(foregroundColor);
    card.changeContent(parseCardContent(card.type(), content));
    return cardDatabaseTable().updateCard(card)
      .thenApply(value -> Map.of("success", true));
  }

  private String parseCardContent(CardType type, DulnoRequestBody content) {
    var json = new JSONObject();
    if (type.isCollection()) {
      json.put("reward_number", content.getInt("reward_number"));
      json.put("reward_description",
        content.getSanitizedString("reward_description", 128));
    }
    return json.toString();
  }

  @RequestMapping(path = "/card/delete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> deleteCard(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCardOperation(findMemberId(request), body.getUUID("card"),
      card -> deleteCard(card).thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> deleteCard(Card card) {
    stampDatabaseTable.findStampsOfPartner(card.partner())
      .thenAccept(stamps -> stamps.stream()
        .filter(stamp -> stamp.assignedCard().equals(card.id()))
        .forEach(this::unassignStampCard));
    return cardDatabaseTable().deleteCard(card.id())
      .thenApply(value -> Map.of("success", true));
  }

  private void unassignStampCard(Stamp stamp) {
    stamp.changeAssignment(null, StampAssignmentRole.UNASSIGNED);
    stampDatabaseTable.updateStamp(stamp);
  }
}
