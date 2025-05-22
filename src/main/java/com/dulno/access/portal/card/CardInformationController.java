package com.dulno.access.portal.card;

import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.card.icon.CardMemberIcon;
import com.dulno.core.card.icon.CardStampIcon;
import com.dulno.core.card.logo.CardLogo;
import com.dulno.core.card.logo.CardLogoDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.locale.Translation;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class CardInformationController extends CardController {
  private final Translation translation;

  private CardInformationController(
    @Qualifier("portalKey") Key secretKey, MemberDatabaseTable memberDatabaseTable,
    CardDatabaseTable cardDatabaseTable,
    CardLogoDatabaseTable cardLogoDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    Translation translation
  ) {
    super(secretKey, memberDatabaseTable, cardDatabaseTable,
      cardLogoDatabaseTable, partnerMemberDatabaseTable);
    this.translation = translation;
  }

  @PortalEndpoint
  @RequestMapping(path = "/cards/list/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findCards(
    HttpServletRequest request
  ) {
    return partnerMemberDatabaseTable().findMemberPartner(findMemberId(request))
      .thenCompose(partner -> cardDatabaseTable().findCardsOfPartner(partner)
        .thenCompose(cards -> AsyncIterator.execute(cards,
            this::gatherCardInformation)
          .thenApply(information -> Map.of("cards", information))));
  }

  @PortalEndpoint
  @RequestMapping(path = "/card/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findCard(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCardOperation(findMemberId(request), body.getUUID("card"),
      card -> gatherCardInformation(card).thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> gatherCardInformation(Card card) {
    return cardLogoDatabaseTable().findCardLogo(card.id())
      .thenApply(logo -> assemblyCardInformation(card, logo));
  }

  private Map<String, Object> assemblyCardInformation(Card card, CardLogo logo) {
    var information = Maps.<String, Object>newHashMap();
    information.put("success", true);
    information.put("id", card.id());
    information.put("backgroundColor", card.backgroundColor());
    information.put("foregroundColor", card.foregroundColor());
    information.put("logoId", logo.logoId());
    information.put("logo", Base64.getEncoder().encodeToString(logo.content()));
    information.put("type", card.type().toString());
    information.put("creation", card.creation());
    var content = new JSONObject(card.content());
    if (card.type().isCollection()) {
      information.putAll(assemblyCollectionCardInformation(content));
    } else if (card.type().isValue()) {
      information.putAll(assemblyValueCardInformation(content));
    } else if (card.type().isMember()) {
      information.putAll(assemblyMemberCardInformation(content));
    }
    return information;
  }

  private Map<String, Object> assemblyCollectionCardInformation(JSONObject content) {
    var information = Maps.<String, Object>newHashMap();
    information.put("stampIcon", content.getString("stamp_icon"));
    information.put("stampIconName",
      CardStampIcon.valueOf(content.getString("stamp_icon")).identifier());
    information.put("rewardNumber", content.getInt("reward_number"));
    information.put("rewardDescription", content.getString("reward_description"));
    return information;
  }

  private Map<String, Object> assemblyValueCardInformation(JSONObject content) {
    var information = Maps.<String, Object>newHashMap();
    information.put("stampIcon", content.getString("stamp_icon"));
    information.put("stampIconName",
      CardStampIcon.valueOf(content.getString("stamp_icon")).identifier());
    information.put("valueNumber", content.getInt("value_number"));
    information.put("valuePrice", content.getDouble("value_price"));
    information.put("valueDescription", content.getString("value_description"));
    return information;
  }

  private Map<String, Object> assemblyMemberCardInformation(JSONObject content) {
    var information = Maps.<String, Object>newHashMap();
    information.put("memberIcon", content.getString("member_icon"));
    information.put("memberIconName",
      CardMemberIcon.valueOf(content.getString("member_icon")).identifier());
    information.put("memberPrice", content.getDouble("member_price"));
    information.put("memberDescription", content.getString("member_description"));
    return information;
  }

  @PortalEndpoint
  @RequestMapping(path = "/card/stamp/icons/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findCardStampIcons(
    HttpServletRequest request
  ) {
    return findMember(request).thenApply(member ->
      Map.of("icons", Arrays.stream(CardStampIcon.values())
        .map(entry -> Map.of("type", entry, "name",
          translation.translateMember(member, entry.locale()),
            "icon", entry.identifier()))
        .toList()));
  }

  @PortalEndpoint
  @RequestMapping(path = "/card/member/icons/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findCardMemberIcons(
    HttpServletRequest request
  ) {
    return findMember(request).thenApply(member ->
      Map.of("icons", Arrays.stream(CardMemberIcon.values())
        .map(entry -> Map.of("type", entry, "name",
          translation.translateMember(member, entry.locale()),
          "icon", entry.identifier()))
        .toList()));
  }
}

