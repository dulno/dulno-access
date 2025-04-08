package com.dulno.access.user;

import com.dulno.core.google.GoogleConfiguration;
import com.dulno.core.scan.PartnerScanDatabaseTable;
import com.dulno.core.scan.ScanDatabaseTable;
import com.dulno.core.user.*;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class UserContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final UserDatabaseTable userDatabaseTable;
  private final UserCardDatabaseTable userCardDatabaseTable;
  private final UserDeviceDatabaseTable userDeviceDatabaseTable;
  private final UserFirebaseDatabaseTable userFirebaseDatabaseTable;
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final ScanDatabaseTable scanDatabaseTable;
  private final PartnerScanDatabaseTable partnerScanDatabaseTable;
  private final GoogleConfiguration googleConfiguration;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("userDatabaseTable", userDatabaseTable);
    beanFactory.registerSingleton("userCardDatabaseTable", userCardDatabaseTable);
    beanFactory.registerSingleton("userDeviceDatabaseTable",
      userDeviceDatabaseTable);
    beanFactory.registerSingleton("userFirebaseDatabaseTable",
      userFirebaseDatabaseTable);
    beanFactory.registerSingleton("userVerificationDatabaseTable",
      userVerificationDatabaseTable);
    beanFactory.registerSingleton("scanDatabaseTable", scanDatabaseTable);
    beanFactory.registerSingleton("partnerScanDatabaseTable",
      partnerScanDatabaseTable);
    beanFactory.registerSingleton("googleConfiguration", googleConfiguration);
  }
}
