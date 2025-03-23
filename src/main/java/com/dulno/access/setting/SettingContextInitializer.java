package com.dulno.access.setting;

import com.dulno.core.member.mfa.MultiFactorAuthDatabaseTable;
import com.dulno.core.member.mfa.MultiFactorAuthFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class SettingContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final MultiFactorAuthDatabaseTable multiFactorAuthDatabaseTable;
  private final MultiFactorAuthFactory multiFactorAuthFactory;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("multiFactorAuthDatabaseTable", multiFactorAuthDatabaseTable);
    beanFactory.registerSingleton("multiFactorAuthFactory", multiFactorAuthFactory);
  }
}
