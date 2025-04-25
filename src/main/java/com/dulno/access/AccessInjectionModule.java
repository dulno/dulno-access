package com.dulno.access;

import com.dulno.access.portal.PortalInjectionModule;
import com.google.inject.AbstractModule;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(staticName = "create")
public class AccessInjectionModule extends AbstractModule {
  @Override
  protected void configure() {
    install(PortalInjectionModule.create());
  }
}
