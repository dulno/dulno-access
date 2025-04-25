package com.dulno.access.portal.offer;

import com.dulno.core.offer.OfferDatabaseTable;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class OfferContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final OfferDatabaseTable offerDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("offerDatabaseTable", offerDatabaseTable);
  }
}
