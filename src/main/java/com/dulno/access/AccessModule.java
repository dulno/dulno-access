package com.dulno.access;

import com.dulno.access.maintenance.MaintenanceContextInitializer;
import com.dulno.access.portal.accessory.AccessoryContextInitializer;
import com.dulno.access.portal.analysis.AnalysisContextInitializer;
import com.dulno.access.portal.balance.BalanceContextInitializer;
import com.dulno.access.portal.bundle.BundleContextInitializer;
import com.dulno.access.portal.campaign.CampaignContextInitializer;
import com.dulno.access.portal.card.CardContextInitializer;
import com.dulno.access.portal.coupon.CouponContextInitializer;
import com.dulno.access.portal.offer.OfferContextInitializer;
import com.dulno.access.portal.partner.PartnerContextInitializer;
import com.dulno.access.portal.setting.SettingContextInitializer;
import com.dulno.access.portal.stamp.StampContextInitializer;
import com.dulno.access.portal.stripe.StripeContextInitializer;
import com.dulno.access.portal.ticket.TicketContextInitializer;
import com.dulno.access.portal.verification.VerificationContextInitializer;
import com.dulno.access.question.QuestionContextInitializer;
import com.dulno.access.sale.SaleContextInitializer;
import com.dulno.access.user.UserContextInitializer;
import com.dulno.core.log.Log;
import com.dulno.core.module.Module;
import com.dulno.core.module.ModuleDescription;
import com.dulno.core.module.ModuleLoadPriority;
import com.google.inject.Injector;
import org.springframework.boot.SpringApplication;

@ModuleDescription(name = "access", version = "1.0.0-SNAPSHOT",
  priority = ModuleLoadPriority.HIGH)
public final class AccessModule extends Module {
  private Log log;

  public AccessModule(Injector injector) {
    super(injector.createChildInjector(AccessInjectionModule.create()));
  }

  @Override
  public void enable() {
    System.setProperty("jdk.httpclient.allowRestrictedHeaders",
      "host,connection,content-length,upgrade");
    log = injector().getInstance(Log.class).subLog("Access");
    registerContextInitializers(injector().getInstance(SpringApplication.class));
  }

  private void registerContextInitializers(SpringApplication application) {
    application.addInitializers(AccessContextInitializer.create(log, injector()));
    application.addInitializers(injector().getInstance(MaintenanceContextInitializer.class));
    application.addInitializers(injector().getInstance(VerificationContextInitializer.class));
    application.addInitializers(injector().getInstance(PartnerContextInitializer.class));
    application.addInitializers(injector().getInstance(StampContextInitializer.class));
    application.addInitializers(injector().getInstance(CardContextInitializer.class));
    application.addInitializers(injector().getInstance(CouponContextInitializer.class));
    application.addInitializers(injector().getInstance(CampaignContextInitializer.class));
    application.addInitializers(injector().getInstance(BundleContextInitializer.class));
    application.addInitializers(injector().getInstance(AccessoryContextInitializer.class));
    application.addInitializers(injector().getInstance(StripeContextInitializer.class));
    application.addInitializers(injector().getInstance(OfferContextInitializer.class));
    application.addInitializers(injector().getInstance(TicketContextInitializer.class));
    application.addInitializers(injector().getInstance(SettingContextInitializer.class));
    application.addInitializers(injector().getInstance(QuestionContextInitializer.class));
    application.addInitializers(injector().getInstance(SaleContextInitializer.class));
    application.addInitializers(injector().getInstance(UserContextInitializer.class));
    application.addInitializers(injector().getInstance(AnalysisContextInitializer.class));
    application.addInitializers(injector().getInstance(BalanceContextInitializer.class));
  }

  @Override
  public void disable() {

  }
}
