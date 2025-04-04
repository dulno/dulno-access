package com.dulno.access.user;

import com.dulno.core.user.UserCardDatabaseTable;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserDeviceDatabaseTable;
import com.dulno.core.user.UserFirebaseDatabaseTable;
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

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("userDatabaseTable", userDatabaseTable);
    beanFactory.registerSingleton("userCardDatabaseTable", userCardDatabaseTable);
    beanFactory.registerSingleton("userDeviceDatabaseTable",
      userDeviceDatabaseTable);
    beanFactory.registerSingleton("userFirebaseDatabaseTable",
      userFirebaseDatabaseTable);
  }
}
