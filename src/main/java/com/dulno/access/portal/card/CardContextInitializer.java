package com.dulno.access.portal.card;

import com.dulno.core.card.CardDatabaseTable;
import com.dulno.core.card.logo.CardLogoDatabaseTable;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class CardContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final CardDatabaseTable cardDatabaseTable;
  private final CardLogoDatabaseTable cardLogoDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("cardDatabaseTable", cardDatabaseTable);
    beanFactory.registerSingleton("cardLogoDatabaseTable", cardLogoDatabaseTable);
  }
}
