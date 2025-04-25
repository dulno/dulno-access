package com.dulno.access.portal.campaign;

import com.dulno.core.campaign.CampaignDatabaseTable;
import com.dulno.core.campaign.notification.CampaignNotificationDatabaseTable;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class CampaignContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final CampaignDatabaseTable campaignDatabaseTable;
  private final CampaignNotificationDatabaseTable campaignNotificationDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("campaignDatabaseTable", campaignDatabaseTable);
    beanFactory.registerSingleton("campaignNotificationDatabaseTable",
      campaignNotificationDatabaseTable);
  }
}
