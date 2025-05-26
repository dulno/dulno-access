package com.dulno.access.verification;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;

@RequiredArgsConstructor(staticName = "create")
public final class VerificationInjectionModule extends AbstractModule {
  @Provides
  @Singleton
  VerificationConfiguration provideVerificationConfiguration() throws Exception {
    return VerificationConfiguration.createAndLoad();
  }

  @Provides
  @Singleton
  @Named("homeKey")
  Key provideHomeKey(VerificationConfiguration configuration) {
    return new SecretKeySpec(configuration.homeSecret()
      .getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
  }

  @Provides
  @Singleton
  @Named("portalKey")
  Key providePortalKey(VerificationConfiguration configuration) {
    return new SecretKeySpec(configuration.portalSecret()
      .getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
  }

  @Provides
  @Singleton
  @Named("portalRefreshKey")
  Key providePortalRefreshKey(VerificationConfiguration configuration) {
    return new SecretKeySpec(configuration.portalRefreshSecret()
      .getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
  }

  @Provides
  @Singleton
  @Named("appKey")
  Key provideAppKey(VerificationConfiguration configuration) {
    return new SecretKeySpec(configuration.appSecret()
      .getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
  }

  @Provides
  @Singleton
  @Named("appRefreshKey")
  Key provideAppRefreshKey(VerificationConfiguration configuration) {
    return new SecretKeySpec(configuration.appRefreshSecret()
      .getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
  }
}
