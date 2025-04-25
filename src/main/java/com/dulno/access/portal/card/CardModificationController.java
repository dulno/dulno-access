package com.dulno.access.portal.card;

import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.card.CardState;
import com.dulno.core.card.CardType;
import com.dulno.core.card.icon.CardMemberIcon;
import com.dulno.core.card.icon.CardStampIcon;
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
        .thenCompose(partnerId -> cardDatabaseTable()
          .insertCard(createDefaultCard(cardId, partnerId, type))
          .thenCompose(value -> partnerLogoDatabaseTable.findPartnerLogo(partnerId)
            .thenCompose(logo -> cardLogoDatabaseTable()
              .generateAvailableCardLogoId(cardId)
              .thenCompose(logoId -> cardLogoDatabaseTable()
                .insertCardLogo(cardId, logoId, logo.content()))))
          .thenApply(value -> Map.of("success", true, "card", cardId))));
  }

  private Card createDefaultCard(UUID cardId, UUID partnerId, CardType type) {
    return Card.create(cardId, partnerId, "#ffffff", "#000000", type,
      defaultCardContent(type), System.currentTimeMillis(), CardState.UNUSED);
  }

  private String defaultCardContent(CardType type) {
    return switch (type) {
      case CardType.COLLECTION -> defaultCollectionCardContent();
      case CardType.VALUE -> defaultValueCardContent();
      case CardType.MEMBER -> defaultMemberCardContent();
    };
  }

  private String defaultCollectionCardContent() {
    var content = new JSONObject();
    content.put("stamp_icon", CardStampIcon.CIRCLE.toString());
    content.put("reward_number", 10);
    content.put("reward_description", "");
    return content.toString();
  }

  private String defaultValueCardContent() {
    var content = new JSONObject();
    content.put("stamp_icon", CardStampIcon.CIRCLE.toString());
    content.put("value_number", 10);
    content.put("value_price", 0.0);
    content.put("value_description", "");
    return content.toString();
  }

  private String defaultMemberCardContent() {
    var content = new JSONObject();
    content.put("member_icon", CardMemberIcon.CROWN.toString());
    content.put("member_price", 0.0);
    content.put("member_description", "");
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
    if (cardUpdateProhibited(card, content)) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    card.changeBackgroundColor(backgroundColor);
    card.changeForegroundColor(foregroundColor);
    card.changeContent(parseCardContent(card.type(), content));
    return cardDatabaseTable().updateCard(card)
      .thenApply(value -> Map.of("success", true));
  }

  private boolean cardUpdateProhibited(Card card, DulnoRequestBody content) {
    if (card.state().isUnused()) {
      return false;
    }
    var cardContent = new JSONObject(card.content());
    if (card.type().isCollection() && content.getInt("rewardNumber") !=
      cardContent.getInt("reward_number")
    ) {
      return true;
    } else if (card.type().isValue() && content.getInt("valueNumber") !=
      cardContent.getInt("value_number")
    ) {
      return true;
    }
    return false;
  }

  private String parseCardContent(CardType type, DulnoRequestBody content) {
    return switch (type) {
      case CardType.COLLECTION -> parseCollectionCardContent(content);
      case CardType.VALUE -> parseValueCardContent(content);
      case CardType.MEMBER -> parseMemberCardContent(content);
    };
  }

  private String parseCollectionCardContent(DulnoRequestBody content) {
    var json = new JSONObject();
    json.put("stamp_icon", CardStampIcon.valueOf(
      content.getString("stampIcon")).toString());
    json.put("reward_number", Math.max(1, Math.min(200,
      content.getInt("rewardNumber"))));
    json.put("reward_description",
      content.getSanitizedString("rewardDescription", 128));
    return json.toString();
  }

  private String parseValueCardContent(DulnoRequestBody content) {
    var json = new JSONObject();
    json.put("stamp_icon", CardStampIcon.valueOf(
      content.getString("stampIcon")).toString());
    json.put("value_number", Math.max(1, Math.min(200,
      content.getInt("valueNumber"))));
    json.put("value_price", Math.max(0.0, content.getDouble("valuePrice")));
    json.put("value_description",
      content.getSanitizedString("valueDescription", 128));
    return json.toString();
  }

  private String parseMemberCardContent(DulnoRequestBody content) {
    var json = new JSONObject();
    json.put("member_icon", CardMemberIcon.valueOf(
      content.getString("memberIcon")).toString());
    json.put("member_price", Math.max(0.0, content.getDouble("memberPrice")));
    json.put("member_description",
      content.getSanitizedString("memberDescription", 128));
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
    if (card.state().isUsed()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    stampDatabaseTable.findStampsOfPartner(card.partnerId())
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
