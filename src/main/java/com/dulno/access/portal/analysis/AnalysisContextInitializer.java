package com.dulno.access.portal.analysis;

import com.dulno.core.partner.analysis.*;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class AnalysisContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final PartnerScanDatabaseTable partnerScanDatabaseTable;
  private final PartnerActivityDatabaseTable partnerActivityDatabaseTable;
  private final PartnerNewUserDatabaseTable partnerNewUserDatabaseTable;
  private final PartnerNewCardDatabaseTable partnerNewCardDatabaseTable;
  private final PartnerUserLanguageDatabaseTable partnerUserLanguageDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("partnerScanDatabaseTable",
      partnerScanDatabaseTable);
    beanFactory.registerSingleton("partnerActivityDatabaseTable",
      partnerActivityDatabaseTable);
    beanFactory.registerSingleton("partnerNewUserDatabaseTable",
      partnerNewUserDatabaseTable);
    beanFactory.registerSingleton("partnerNewCardDatabaseTable",
      partnerNewCardDatabaseTable);
    beanFactory.registerSingleton("partnerUserLanguageDatabaseTable",
      partnerUserLanguageDatabaseTable);
  }
}
