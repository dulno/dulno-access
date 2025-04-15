package com.dulno.access;

import com.dulno.access.maintenance.MaintenanceContextInitializer;
import com.dulno.access.portal.accessory.AccessoryContextInitializer;
import com.dulno.access.portal.bundle.BundleContextInitializer;
import com.dulno.access.portal.campaign.CampaignContextInitializer;
import com.dulno.access.portal.card.CardContextInitializer;
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
import com.dulno.core.database.DatabaseConnection;
import com.dulno.core.database.DatabaseKeyspace;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.locale.Translation;
import com.dulno.core.log.Log;
import com.dulno.core.mail.MailFactory;
import com.dulno.core.module.Module;
import com.dulno.core.module.ModuleDescription;
import com.dulno.core.module.ModuleLoadPriority;
import com.dulno.core.module.ModuleLoader;
import com.dulno.core.worker.WorkerConfiguration;
import com.dulno.core.worker.WorkerDistribution;
import com.dulno.core.worker.client.WorkerOperatorClient;
import com.dulno.core.worker.environment.WorkerEnvironment;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
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
    application.addInitializers(AccessContextInitializer.create(log,
      injector().getInstance(Key.get(java.security.Key.class, Names.named("homeKey"))),
      injector().getInstance(Key.get(java.security.Key.class, Names.named("productKey"))),
      injector().getInstance(Key.get(java.security.Key.class, Names.named("refreshKey"))),
      injector().getInstance(ModuleLoader.class),
      injector().getInstance(DatabaseConnection.class),
      injector().getInstance(DatabaseKeyspace.class),
      injector().getInstance(WorkerDistribution.class),
      injector().getInstance(WorkerOperatorClient.class),
      injector().getInstance(WorkerConfiguration.class),
      injector().getInstance(Translation.class),
      injector().getInstance(ErrorRepository.class),
      injector().getInstance(MailFactory.class),
      injector().getInstance(Hashing.class),
      injector().getInstance(DulnoEnvironment.class),
      injector().getInstance(WorkerEnvironment.class)));
    application.addInitializers(injector().getInstance(MaintenanceContextInitializer.class));
    application.addInitializers(injector().getInstance(VerificationContextInitializer.class));
    application.addInitializers(injector().getInstance(PartnerContextInitializer.class));
    application.addInitializers(injector().getInstance(StampContextInitializer.class));
    application.addInitializers(injector().getInstance(CardContextInitializer.class));
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
  }

  @Override
  public void disable() {

  }
}
