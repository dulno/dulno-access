package com.dulno.access;

import com.dulno.core.database.DatabaseConnection;
import com.dulno.core.database.DatabaseKeyspace;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.locale.Translation;
import com.dulno.core.log.Log;
import com.dulno.core.mail.MailFactory;
import com.dulno.core.module.ModuleLoader;
import com.dulno.core.worker.WorkerConfiguration;
import com.dulno.core.worker.WorkerDistribution;
import com.dulno.core.worker.client.WorkerOperatorClient;
import com.dulno.core.worker.environment.WorkerEnvironment;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.security.Key;

@RequiredArgsConstructor(staticName = "create")
public class AccessContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  public static AccessContextInitializer create(Log log, Injector injector) {
    return create(log,
      injector.getInstance(com.google.inject.Key.get(Key.class,
        Names.named("homeKey"))),
      injector.getInstance(com.google.inject.Key.get(Key.class,
        Names.named("portalKey"))),
      injector.getInstance(com.google.inject.Key.get(Key.class,
        Names.named("portalRefreshKey"))),
      injector.getInstance(com.google.inject.Key.get(Key.class,
        Names.named("appKey"))),
      injector.getInstance(com.google.inject.Key.get(Key.class,
        Names.named("appRefreshKey"))),
      injector.getInstance(ModuleLoader.class),
      injector.getInstance(DatabaseConnection.class),
      injector.getInstance(DatabaseKeyspace.class),
      injector.getInstance(WorkerDistribution.class),
      injector.getInstance(WorkerOperatorClient.class),
      injector.getInstance(WorkerConfiguration.class),
      injector.getInstance(Translation.class),
      injector.getInstance(ErrorRepository.class),
      injector.getInstance(MailFactory.class),
      injector.getInstance(Hashing.class),
      injector.getInstance(DulnoEnvironment.class),
      injector.getInstance(WorkerEnvironment.class));
  }

  private final Log log;
  private final Key homeKey;
  private final Key portalKey;
  private final Key portalRefreshKey;
  private final Key appKey;
  private final Key appRefreshKey;
  private final ModuleLoader moduleLoader;
  private final DatabaseConnection databaseConnection;
  private final DatabaseKeyspace databaseKeyspace;
  private final WorkerDistribution distribution;
  private final WorkerOperatorClient workerOperatorClient;
  private final WorkerConfiguration workerConfiguration;
  private final Translation translation;
  private final ErrorRepository errorRepository;
  private final MailFactory mailFactory;
  private final Hashing hashing;
  private final DulnoEnvironment environment;
  private final WorkerEnvironment workerEnvironment;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("accessLog", log);
    beanFactory.registerSingleton("moduleLoader", moduleLoader);
    beanFactory.registerSingleton("databaseConnection", databaseConnection);
    beanFactory.registerSingleton("databaseKeyspace", databaseKeyspace);
    beanFactory.registerSingleton("distribution", distribution);
    beanFactory.registerSingleton("workerOperatorClient", workerOperatorClient);
    beanFactory.registerSingleton("workerConfiguration", workerConfiguration);
    beanFactory.registerSingleton("translation", translation);
    beanFactory.registerSingleton("errorRepository", errorRepository);
    beanFactory.registerSingleton("mailFactory", mailFactory);
    beanFactory.registerSingleton("hashing", hashing);
    beanFactory.registerSingleton("dulnoEnvironment", environment);
    beanFactory.registerSingleton("workerEnvironment", workerEnvironment);
    beanFactory.registerSingleton("homeKey", homeKey);
    beanFactory.registerSingleton("portalKey", portalKey);
    beanFactory.registerSingleton("portalRefreshKey", portalRefreshKey);
    beanFactory.registerSingleton("appKey", appKey);
    beanFactory.registerSingleton("appRefreshKey", appRefreshKey);
  }
}
