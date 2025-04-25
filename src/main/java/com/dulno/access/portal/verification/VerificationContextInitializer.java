package com.dulno.access.portal.verification;

import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.member.MemberEmailChangeDatabaseTable;
import com.dulno.core.member.MemberPasswordResetDatabaseTable;
import com.dulno.core.member.MemberVerificationDatabaseTable;
import com.dulno.core.member.session.SessionDatabaseTable;
import com.dulno.core.recaptcha.RecaptchaConfiguration;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.maxmind.geoip2.DatabaseReader;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class VerificationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final MemberDatabaseTable memberDatabaseTable;
  private final MemberVerificationDatabaseTable memberVerificationDatabaseTable;
  private final MemberPasswordResetDatabaseTable memberPasswordResetDatabaseTable;
  private final MemberEmailChangeDatabaseTable memberEmailChangeDatabaseTable;
  private final RecaptchaConfiguration recaptchaConfiguration;
  private final SessionDatabaseTable sessionDatabaseTable;
  private final DatabaseReader databaseReader;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("memberDatabaseTable", memberDatabaseTable);
    beanFactory.registerSingleton("memberVerificationDatabaseTable", memberVerificationDatabaseTable);
    beanFactory.registerSingleton("memberPasswordResetDatabaseTable", memberPasswordResetDatabaseTable);
    beanFactory.registerSingleton("memberEmailChangeDatabaseTable", memberEmailChangeDatabaseTable);
    beanFactory.registerSingleton("recaptchaConfiguration", recaptchaConfiguration);
    beanFactory.registerSingleton("sessionDatabaseTable", sessionDatabaseTable);
    beanFactory.registerSingleton("databaseReader", databaseReader);
  }
}
