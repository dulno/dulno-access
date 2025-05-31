package com.dulno.access.portal.setting;

import com.dulno.core.member.mfa.MemberMultiFactorAuthDatabaseTable;
import com.dulno.core.member.mfa.MemberMultiFactorAuthFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class SettingContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final MemberMultiFactorAuthDatabaseTable memberMultiFactorAuthDatabaseTable;
  private final MemberMultiFactorAuthFactory memberMultiFactorAuthFactory;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("memberMultiFactorAuthDatabaseTable",
      memberMultiFactorAuthDatabaseTable);
    beanFactory.registerSingleton("memberMultiFactorAuthFactory",
      memberMultiFactorAuthFactory);
  }
}
