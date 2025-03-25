package com.dulno.access.portal.partner;

import com.dulno.core.partner.*;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class PartnerContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final PartnerConfiguration partnerConfiguration;
  private final PartnerDatabaseTable partnerDatabaseTable;
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;
  private final PartnerLogoDatabaseTable partnerLogoDatabaseTable;
  private final PartnerLocationDatabaseTable partnerLocationDatabaseTable;
  private final PartnerLinkDatabaseTable partnerLinkDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("partnerConfiguration", partnerConfiguration);
    beanFactory.registerSingleton("partnerDatabaseTable", partnerDatabaseTable);
    beanFactory.registerSingleton("partnerMemberDatabaseTable", partnerMemberDatabaseTable);
    beanFactory.registerSingleton("partnerLogoDatabaseTable", partnerLogoDatabaseTable);
    beanFactory.registerSingleton("partnerLocationDatabaseTable", partnerLocationDatabaseTable);
    beanFactory.registerSingleton("partnerLinkDatabaseTable", partnerLinkDatabaseTable);
  }
}
