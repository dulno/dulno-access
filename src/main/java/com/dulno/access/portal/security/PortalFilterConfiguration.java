package com.dulno.access.portal.security;

import com.dulno.access.portal.security.home.HomeAuthorizationFilter;
import com.dulno.access.portal.security.mixed.MixedAuthorizationFilter;
import com.dulno.access.portal.security.portal.PortalAuthorizationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class PortalFilterConfiguration {
  @Autowired
  private PortalAuthorizationFilter portalAuthorizationFilter;
  @Autowired
  private HomeAuthorizationFilter homeAuthorizationFilter;
  @Autowired
  private MixedAuthorizationFilter mixedAuthorizationFilter;

  @Bean
  public FilterRegistrationBean<PortalAuthorizationFilter> provideProductAuthorizationFilter() {
    var registrationBean = new FilterRegistrationBean<PortalAuthorizationFilter>();
    registrationBean.setFilter(portalAuthorizationFilter);
    registrationBean.setOrder(3);
    return registrationBean;
  }

  @Bean
  public FilterRegistrationBean<HomeAuthorizationFilter> provideHomeAuthorizationFilter() {
    var registrationBean = new FilterRegistrationBean<HomeAuthorizationFilter>();
    registrationBean.setFilter(homeAuthorizationFilter);
    registrationBean.setOrder(4);
    return registrationBean;
  }

  @Bean
  public FilterRegistrationBean<MixedAuthorizationFilter> provideMixedAuthorizationFilter() {
    var registrationBean = new FilterRegistrationBean<MixedAuthorizationFilter>();
    registrationBean.setFilter(mixedAuthorizationFilter);
    registrationBean.setOrder(5);
    return registrationBean;
  }
}
