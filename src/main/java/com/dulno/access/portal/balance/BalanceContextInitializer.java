package com.dulno.access.portal.balance;

import com.dulno.core.partner.analysis.card.PartnerActiveCardDatabaseTable;
import com.dulno.core.partner.analysis.stamp.PartnerOpenStampDatabaseTable;
import com.dulno.core.partner.transaction.PartnerTransactionDatabaseTable;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class BalanceContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final PartnerTransactionDatabaseTable partnerTransactionDatabaseTable;
  private final PartnerActiveCardDatabaseTable partnerActiveCardDatabaseTable;
  private final PartnerOpenStampDatabaseTable partnerOpenStampDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("partnerTransactionDatabaseTable",
      partnerTransactionDatabaseTable);
    beanFactory.registerSingleton("partnerActiveCardDatabaseTable",
      partnerActiveCardDatabaseTable);
    beanFactory.registerSingleton("partnerOpenStampDatabaseTable",
      partnerOpenStampDatabaseTable);
  }
}
