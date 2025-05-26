package com.dulno.access.user.campaign;

import com.dulno.access.user.UserController;
import com.dulno.access.user.partner.UserPartnerController;
import com.dulno.core.campaign.Campaign;
import com.dulno.core.campaign.CampaignDatabaseTable;
import com.dulno.core.coupon.Coupon;
import com.dulno.core.coupon.CouponDatabaseTable;
import com.dulno.core.coupon.logo.CouponLogoDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.partner.Partner;
import com.dulno.core.partner.PartnerDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.UserDatabaseTable;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserCampaignController extends UserController {
  private final CampaignDatabaseTable campaignDatabaseTable;
  private final PartnerDatabaseTable partnerDatabaseTable;
  private final UserPartnerController userPartnerController;
  private final CouponDatabaseTable couponDatabaseTable;
  private final CouponLogoDatabaseTable couponLogoDatabaseTable;

  private UserCampaignController(
    @Qualifier("appKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    CampaignDatabaseTable campaignDatabaseTable,
    PartnerDatabaseTable partnerDatabaseTable,
    UserPartnerController userPartnerController,
    CouponDatabaseTable couponDatabaseTable,
    CouponLogoDatabaseTable couponLogoDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.campaignDatabaseTable = campaignDatabaseTable;
    this.partnerDatabaseTable = partnerDatabaseTable;
    this.userPartnerController = userPartnerController;
    this.couponDatabaseTable = couponDatabaseTable;
    this.couponLogoDatabaseTable = couponLogoDatabaseTable;
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
      .thenCompose(partner -> collectCampaignCouponInformation(campaign)
        .thenApply(coupon -> assemblyCampaignInformation(campaign, partner, coupon)));
  }

  private Map<String, Object> assemblyCampaignInformation(
    Campaign campaign, Map<String, Object> partnerInformation,
    Map<String, Object> couponInformation
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", campaign.id());
    information.put("title", campaign.title());
    information.put("description", campaign.description());
    information.put("type", campaign.type());
    information.put("start", campaign.start());
    information.put("end", campaign.end());
    information.put("partner", partnerInformation);
    information.put("coupon", couponInformation);
    return information;
  }

  private CompletableFuture<Map<String, Object>> collectCampaignCouponInformation(
    Campaign campaign
  ) {
    if (campaign.couponId() == null) {
      return CompletableFuture.completedFuture(Map.of("found", false));
    }
    return couponDatabaseTable.couponExists(campaign.couponId())
      .thenCompose(exists -> checkCampaignCouponExistence(campaign, exists));
  }

  private CompletableFuture<Map<String, Object>> checkCampaignCouponExistence(
    Campaign campaign, boolean couponExists
  ) {
    if (!couponExists) {
      return CompletableFuture.completedFuture(Map.of("found", false));
    }
    return couponDatabaseTable.findCoupon(campaign.couponId())
      .thenCompose(coupon -> partnerDatabaseTable.findPartner(coupon.partnerId())
        .thenCompose(partner -> couponLogoDatabaseTable.findCouponLogoId(coupon.id())
          .thenApply(logoId -> assemblyCampaignCouponInformation(coupon,
            partner, logoId))));
  }

  private Map<String, Object> assemblyCampaignCouponInformation(
    Coupon coupon, Partner partner, UUID logoId
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("found", true);
    information.put("couponId", coupon.id());
    information.put("couponBackgroundColor", coupon.backgroundColor());
    information.put("couponForegroundColor", coupon.foregroundColor());
    information.put("couponReward", coupon.reward());
    information.put("couponDescription", coupon.description());
    information.put("limitation", coupon.limitation());
    information.put("logoId", logoId);
    information.put("partnerId", partner.id());
    information.put("partnerName", partner.name());
    information.put("partnerDescription", partner.description());
    return information;
  }
}
