package com.dulno.access.portal.accessory;

import com.dulno.core.accessory.AccessoryDatabaseTable;
import com.dulno.core.accessory.AccessoryPresetRepository;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class AccessoryContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final AccessoryDatabaseTable accessoryDatabaseTable;
  private final AccessoryPresetRepository accessoryPresetRepository;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("accessoryDatabaseTable",
      accessoryDatabaseTable);
    beanFactory.registerSingleton("accessoryPresetRepository",
      accessoryPresetRepository);
  }
}
