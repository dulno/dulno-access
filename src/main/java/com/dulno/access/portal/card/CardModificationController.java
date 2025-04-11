package com.dulno.access.portal.card;

import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.card.CardType;
import com.dulno.core.card.logo.CardLogoDatabaseTable;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.PartnerConfiguration;
import com.dulno.core.partner.logo.PartnerLogoDatabaseTable;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.stamp.Stamp;
import com.dulno.core.stamp.StampAssignmentRole;
import com.dulno.core.stamp.StampDatabaseTable;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class CardModificationController extends CardController {
  private final StampDatabaseTable stampDatabaseTable;
  private final PartnerConfiguration partnerConfiguration;
  private final PartnerLogoDatabaseTable partnerLogoDatabaseTable;

  private CardModificationController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    CardDatabaseTable cardDatabaseTable,
    CardLogoDatabaseTable cardLogoDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    StampDatabaseTable stampDatabaseTable,
    PartnerConfiguration partnerConfiguration,
    PartnerLogoDatabaseTable partnerLogoDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable, cardDatabaseTable,
      cardLogoDatabaseTable, partnerMemberDatabaseTable);
    this.stampDatabaseTable = stampDatabaseTable;
    this.partnerConfiguration = partnerConfiguration;
    this.partnerLogoDatabaseTable = partnerLogoDatabaseTable;
  }

  @PortalEndpoint
  @RequestMapping(path = "/card/create/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> createCard(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    return createCard(findMemberId(request),
      CardType.valueOf(body.getString("type")));
  }

  private CompletableFuture<Map<String, Object>> createCard(
    UUID memberId, CardType type
  ) {
    return cardDatabaseTable().generateAvailableCardId()
      .thenCompose(cardId -> partnerMemberDatabaseTable().findMemberPartner(memberId)
        .thenCompose(partnerId -> cardDatabaseTable().insertCard(cardId,
            partnerId, "#ffffff", "#000000", type.toString(),
            defaultCardContent(type), System.currentTimeMillis())
          .thenCompose(value -> partnerLogoDatabaseTable.findPartnerLogo(partnerId)
            .thenCompose(logo -> cardLogoDatabaseTable()
              .generateAvailableCardLogoId(cardId)
              .thenCompose(logoId -> cardLogoDatabaseTable()
                .insertCardLogo(cardId, logoId, logo.content()))))
          .thenApply(value -> Map.of("success", true, "card", cardId))));
  }

  private String defaultCardContent(CardType type) {
    var content = new JSONObject();
    if (type.isCollection()) {
      content.put("reward_number", 10);
      content.put("reward_description", "");
    } else if (type.isValue()) {
      content.put("value_number", 10);
      content.put("value_description", "");
    } else if (type.isMember()) {
      content.put("member_description", "");
    }
    return content.toString();
  }

  @PortalEndpoint
  @RequestMapping(path = "/card/update/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> updateCard(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCardOperation(findMemberId(request), body.getUUID("card"),
      card -> updateCard(card, body.getSanitizedString("backgroundColor"),
        body.getSanitizedString("foregroundColor"), body.getObject("content"))
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
      json.put("reward_number", Math.max(1, Math.min(200,
        content.getInt("rewardNumber"))));
      json.put("reward_description",
        content.getSanitizedString("rewardDescription", 128));
    } else if (type.isValue()) {
      json.put("value_number", Math.max(1, Math.min(200,
        content.getInt("valueNumber"))));
      json.put("value_description",
        content.getSanitizedString("valueDescription", 128));
    } else if (type.isMember()) {
      json.put("member_description",
        content.getSanitizedString("memberDescription", 128));
    }
    return json.toString();
  }

  @PortalEndpoint
  @RequestMapping(path = "/card/logo/update/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> updateCardLogo(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCardOperation(findMemberId(request), body.getUUID("card"),
      card -> updateCardLogo(card, body.getString("logo"))
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  private static final long MAX_LOGO_SIZE = 1024L * 1024;

  public CompletableFuture<Map<String, Object>> updateCardLogo(
    Card card, String logo
  ) {
    var parsedLogo = parseCardLogo(logo);
    if (parsedLogo.length > MAX_LOGO_SIZE) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return cardLogoDatabaseTable().findCardLogoId(card.id())
      .thenCompose(previousLogoId -> cardLogoDatabaseTable()
        .generateAvailableCardLogoId(card.id())
        .thenCompose(logoId -> cardLogoDatabaseTable()
          .insertCardLogo(card.id(), logoId, parsedLogo)
          .thenCompose(value -> cardLogoDatabaseTable()
            .deleteCardLogo(card.id(), previousLogoId))))
      .thenApply(value -> Map.of("success", true));
  }

  private byte[] parseCardLogo(String cardLogo) {
    try {
      return Base64.getDecoder().decode(cardLogo);
    } catch (Exception exception) {
      return Base64.getDecoder().decode(partnerConfiguration.defaultLogo());
    }
  }

  @PortalEndpoint
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
      .thenCompose(value -> cardLogoDatabaseTable().deleteCardLogo(card.id()))
      .thenApply(value -> Map.of("success", true));
  }

  private void unassignStampCard(Stamp stamp) {
    stamp.changeAssignment(null, StampAssignmentRole.UNASSIGNED);
    stampDatabaseTable.updateStamp(stamp);
  }
}
