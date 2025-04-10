package com.dulno.access.user;

import com.dulno.core.card.Card;
import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.card.CardLogoDatabaseTable;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.partner.Partner;
import com.dulno.core.partner.PartnerDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserCard;
import com.dulno.core.user.UserCardDatabaseTable;
import com.dulno.core.user.UserDatabaseTable;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserCardController extends UserController {
  private final UserCardDatabaseTable userCardDatabaseTable;
  private final CardDatabaseTable cardDatabaseTable;
  private final CardLogoDatabaseTable cardLogoDatabaseTable;
  private final PartnerDatabaseTable partnerDatabaseTable;

  private UserCardController(
    UserDatabaseTable userDatabaseTable, Hashing hashing,
    DulnoEnvironment environment, ErrorRepository errorRepository,
    UserCardDatabaseTable userCardDatabaseTable,
    CardDatabaseTable cardDatabaseTable,
    CardLogoDatabaseTable cardLogoDatabaseTable,
    PartnerDatabaseTable partnerDatabaseTable
  ) {
    super(userDatabaseTable, hashing, environment, errorRepository);
    this.userCardDatabaseTable = userCardDatabaseTable;
    this.cardDatabaseTable = cardDatabaseTable;
    this.cardLogoDatabaseTable = cardLogoDatabaseTable;
    this.partnerDatabaseTable = partnerDatabaseTable;
  }

  @RequestMapping(path = "/user/cards/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findUserCards(
    HttpServletRequest request
  ) {
    return findUser(request).thenCompose(this::findUserCards)
      .exceptionally(this::processError);
  }

  private CompletableFuture<Map<String, Object>> findUserCards(User user) {
    return userCardDatabaseTable.findUserCards(user.id())
      .thenCompose(cards -> AsyncIterator.execute(cards,
        userCard -> cardDatabaseTable.findCard(userCard.cardId())
          .thenCompose(card -> partnerDatabaseTable.findPartner(card.partner())
            .thenCompose(partner -> cardLogoDatabaseTable.findCardLogoId(card.id())
              .thenApply(logoId -> assemblyCardInformation(userCard, card,
                partner, logoId))))))
      .thenApply(cards -> Map.of("cards", cards));
  }

  private Map<String, Object> assemblyCardInformation(
    UserCard userCard, Card card, Partner partner, UUID logoId
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("cardId", card.id());
    information.put("cardBackgroundColor", card.backgroundColor());
    information.put("cardForegroundColor", card.foregroundColor());
    information.put("cardType", card.type());
    information.put("lastUpdate", userCard.lastUpdate());
    information.put("logoId", logoId);
    information.put("partnerId", partner.id());
    information.put("partnerName", partner.name());
    information.put("partnerDescription", partner.description());
    var userCardContent = new JSONObject(userCard.content());
    var cardContent = new JSONObject(card.content());
    if (card.type().isCollection()) {
      information.putAll(assemblyCollectionCardInformation(userCardContent,
        cardContent));
    } else if (card.type().isValue()) {
      information.putAll(assemblyValueCardInformation(userCardContent,
        cardContent));
    } else if (card.type().isMember()) {
      information.putAll(assemblyMemberCardInformation(userCardContent,
        cardContent));
    }
    return information;
  }

  private Map<String, Object> assemblyCollectionCardInformation(
    JSONObject userCardContent, JSONObject cardContent
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("rewardNumber", cardContent.getInt("reward_number"));
    information.put("rewardDescription",
      cardContent.getString("reward_description"));
    information.put("stamps", userCardContent.getInt("stamps"));
    return information;
  }

  private Map<String, Object> assemblyValueCardInformation(
    JSONObject userCardContent, JSONObject cardContent
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("valueNumber", cardContent.getInt("value_number"));
    information.put("valueDescription", cardContent.getString("value_description"));
    information.put("stamps", userCardContent.getInt("stamps"));
    return information;
  }

  private Map<String, Object> assemblyMemberCardInformation(
    JSONObject userCardContent, JSONObject cardContent
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("memberDescription",
      cardContent.getString("member_description"));
    return information;
  }

  @RequestMapping(path = "/user/card/logo/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findCardLogo(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var cardId = body.getUUID("card");
    return findUser(request)
      .thenCompose(user -> cardDatabaseTable.cardExists(cardId)
        .thenCompose(exists -> findCardLogo(cardId, exists)))
      .exceptionally(this::processError);
  }

  private CompletableFuture<Map<String, Object>> findCardLogo(
    UUID cardId, boolean cardExists
  ) {
    if (!cardExists) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return cardLogoDatabaseTable.findCardLogo(cardId)
      .thenApply(logo -> Map.of("logoId", logo.logoId(), "logo",
        Base64.getEncoder().encodeToString(logo.content())));
  }
}
