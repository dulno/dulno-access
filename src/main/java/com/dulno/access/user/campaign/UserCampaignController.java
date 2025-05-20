package com.dulno.access.user.campaign;

import com.dulno.access.user.UserController;
import com.dulno.access.user.partner.UserPartnerController;
import com.dulno.core.campaign.Campaign;
import com.dulno.core.campaign.CampaignDatabaseTable;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.UserDatabaseTable;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserCampaignController extends UserController {
  private final CampaignDatabaseTable campaignDatabaseTable;
  private final UserPartnerController userPartnerController;

  private UserCampaignController(
    UserDatabaseTable userDatabaseTable, Hashing hashing,
    DulnoEnvironment environment, ErrorRepository errorRepository,
    CampaignDatabaseTable campaignDatabaseTable,
    UserPartnerController userPartnerController
  ) {
    super(userDatabaseTable, hashing, environment, errorRepository);
    this.campaignDatabaseTable = campaignDatabaseTable;
    this.userPartnerController = userPartnerController;
  }

  @RequestMapping(path = "/user/campaigns/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findCampaigns() {
    var current = System.currentTimeMillis();
    return campaignDatabaseTable.findAllCampaigns()
      .thenApply(campaigns -> campaigns.stream()
        .filter(campaign -> campaign.end() > current)
        .toList())
      .thenCompose(campaigns -> AsyncIterator.execute(campaigns,
        this::collectCampaignInformation))
      .thenApply(campaigns -> Map.of("campaigns", campaigns));
  }

  @RequestMapping(path = "/user/campaign/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findCampaign(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var campaignId = body.getUUID("campaign");
    return campaignDatabaseTable.campaignExists(campaignId)
      .thenCompose(exists -> findCampaign(campaignId, exists));
  }

  private CompletableFuture<Map<String, Object>> findCampaign(
    UUID campaignId, boolean campaignExists
  ) {
    if (!campaignExists) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return campaignDatabaseTable.findCampaign(campaignId)
      .thenCompose(this::collectCampaignInformation);
  }

  private CompletableFuture<Map<String, Object>> collectCampaignInformation(
    Campaign campaign
  ) {
    return userPartnerController.collectPartnerInformation(campaign.partnerId())
      .thenApply(partner -> assemblyCampaignInformation(campaign, partner));
  }

  private Map<String, Object> assemblyCampaignInformation(
    Campaign campaign, Map<String, Object> partnerInformation
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", campaign.id());
    information.put("title", campaign.title());
    information.put("description", campaign.description());
    information.put("type", campaign.type());
    information.put("start", campaign.start());
    information.put("end", campaign.end());
    information.put("partner", partnerInformation);
    return information;
  }
}
