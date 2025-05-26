package com.dulno.access.portal.coupon;

import com.dulno.core.coupon.CouponDatabaseTable;
import com.dulno.core.coupon.logo.CouponLogoDatabaseTable;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class CouponContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final CouponDatabaseTable couponDatabaseTable;
  private final CouponLogoDatabaseTable couponLogoDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("couponDatabaseTable", couponDatabaseTable);
    beanFactory.registerSingleton("couponLogoDatabaseTable", couponLogoDatabaseTable);
  }
}
