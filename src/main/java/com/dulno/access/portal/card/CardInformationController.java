package com.dulno.access.portal.card;

import com.dulno.access.portal.security.portal.PortalEndpoint;
import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.card.CardLogo;
import com.dulno.core.card.CardLogoDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.PartnerMemberDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
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
import java.util.concurrent.CompletableFuture;

@RestController
public final class CardInformationController extends CardController {
  private CardInformationController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    CardDatabaseTable cardDatabaseTable,
    CardLogoDatabaseTable cardLogoDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable, cardDatabaseTable,
      cardLogoDatabaseTable, partnerMemberDatabaseTable);
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
      information.put("rewardNumber", content.getInt("reward_number"));
      information.put("rewardDescription", content.getString("reward_description"));
    } else if (card.type().isValue()) {
      information.put("valueNumber", content.getInt("value_number"));
      information.put("valueDescription", content.getString("value_description"));
    } else if (card.type().isMember()) {
      information.put("memberDescription", content.getString("member_description"));
    }
    return information;
  }
}

