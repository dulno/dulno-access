package com.dulno.access.portal.stamp;

import com.dulno.core.stamp.StampDatabaseTable;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class StampContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final StampDatabaseTable stampDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("stampDatabaseTable", stampDatabaseTable);
  }
}
