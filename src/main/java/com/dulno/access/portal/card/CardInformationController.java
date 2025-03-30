package com.dulno.access.portal.card;

import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.PartnerMemberDatabaseTable;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class CardInformationController extends CardController {
  private CardInformationController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    CardDatabaseTable cardDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable, cardDatabaseTable,
      partnerMemberDatabaseTable);
  }

  @RequestMapping(path = "/cards/list/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findCards(
    HttpServletRequest request
  ) {
    return partnerMemberDatabaseTable().findMemberPartner(findMemberId(request))
      .thenCompose(partner -> cardDatabaseTable().findCardsOfPartner(partner)
        .thenApply(cards -> Map.of("cards", cards.stream()
          .map(this::assemblyCardInformation).toList())));
  }

  @RequestMapping(path = "/card/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findCard(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performCardOperation(findMemberId(request), body.getUUID("card"),
      card -> futureResponse.complete(assemblyCardInformation(card)),
      () -> futureResponse.complete(Map.of("success", false)));
    return futureResponse;
  }

  private Map<String, Object> assemblyCardInformation(Card card) {
    var information = Maps.<String, Object>newHashMap();
    information.put("success", true);
    information.put("id", card.id());
    information.put("backgroundColor", card.backgroundColor());
    information.put("foregroundColor", card.foregroundColor());
    information.put("type", card.type().toString());
    information.put("creation", card.creation());
    if (card.type().isCollection()) {
      var content = new JSONObject(card.content());
      information.put("collectionRewardNumber", content.getString("reward_number"));
      information.put("collectionRewardDescription",
        content.getString("reward_description"));
    }
    return information;
  }
}

