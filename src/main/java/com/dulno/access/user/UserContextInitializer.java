package com.dulno.access.user;

import com.dulno.core.google.GoogleConfiguration;
import com.dulno.core.item.ItemDatabaseTable;
import com.dulno.core.scan.ScanDatabaseTable;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.device.UserDeviceDatabaseTable;
import com.dulno.core.user.item.UserItemDatabaseTable;
import com.dulno.core.user.partner.UserPartnerDatabaseTable;
import com.dulno.core.user.scan.UserScanDatabaseTable;
import com.dulno.core.user.statistic.UserAppOpenDatabaseTable;
import com.dulno.core.user.statistic.UserJoinDatabaseTable;
import com.dulno.core.user.statistic.UserStatisticConfiguration;
import com.dulno.core.user.verification.UserVerificationDatabaseTable;
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
  private final ItemDatabaseTable itemDatabaseTable;
  private final UserItemDatabaseTable userItemDatabaseTable;
  private final UserDeviceDatabaseTable userDeviceDatabaseTable;
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final ScanDatabaseTable scanDatabaseTable;
  private final UserScanDatabaseTable userScanDatabaseTable;
  private final UserPartnerDatabaseTable userPartnerDatabaseTable;
  private final UserJoinDatabaseTable userJoinDatabaseTable;
  private final UserAppOpenDatabaseTable userAppOpenDatabaseTable;
  private final UserStatisticConfiguration userStatisticConfiguration;
  private final GoogleConfiguration googleConfiguration;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("userDatabaseTable", userDatabaseTable);
    beanFactory.registerSingleton("itemDatabaseTable", itemDatabaseTable);
    beanFactory.registerSingleton("userItemDatabaseTable", userItemDatabaseTable);
    beanFactory.registerSingleton("userDeviceDatabaseTable",
      userDeviceDatabaseTable);
    beanFactory.registerSingleton("userVerificationDatabaseTable",
      userVerificationDatabaseTable);
    beanFactory.registerSingleton("scanDatabaseTable", scanDatabaseTable);
    beanFactory.registerSingleton("userScanDatabaseTable", userScanDatabaseTable);
    beanFactory.registerSingleton("userPartnerDatabaseTable",
      userPartnerDatabaseTable);
    beanFactory.registerSingleton("userJoinDatabaseTable", userJoinDatabaseTable);
    beanFactory.registerSingleton("userAppOpenDatabaseTable",
      userAppOpenDatabaseTable);
    beanFactory.registerSingleton("userStatisticConfiguration",
      userStatisticConfiguration);
    beanFactory.registerSingleton("googleConfiguration", googleConfiguration);
  }
}
