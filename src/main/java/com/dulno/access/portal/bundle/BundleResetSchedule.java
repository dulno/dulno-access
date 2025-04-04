package com.dulno.access.portal.bundle;

import com.dulno.core.bundle.Bundle;
import com.dulno.core.bundle.BundleDatabaseTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public final class BundleResetSchedule {
  private final BundleDatabaseTable bundleDatabaseTable;
  private final ScheduledExecutorService executorService =
    Executors.newScheduledThreadPool(1);
  private ScheduledFuture<?> scheduler;

  private static final long CHECK_INTERVAL = 1000L * 60 * 60 * 24;
  private static final TimeUnit CHECK_TIME_UNIT = TimeUnit.MILLISECONDS;

  public void start() {
    scheduler = executorService.scheduleAtFixedRate(this::execute,
      new Random().nextLong(CHECK_INTERVAL), CHECK_INTERVAL, CHECK_TIME_UNIT);
  }

  private void execute() {
    bundleDatabaseTable.findAllBundles()
      .thenAcceptAsync(bundles -> findBundlesToReset(bundles.stream()
        .filter(Objects::nonNull).toList()));
  }

  private void findBundlesToReset(List<Bundle> bundles) {
    var current = ZonedDateTime.now();
    var next = current.minusMonths(6);
    if (next.getDayOfMonth() != current.getDayOfMonth()) {
      next = next.withDayOfMonth(next.getMonth().length(
        next.toLocalDate().isLeapYear()));
    }
    var maximumValue = next.toInstant().toEpochMilli();
    for (var bundle : bundles) {
      if (bundle.expiration() > 0 && bundle.expiration() <= maximumValue) {
        resetBundle(bundle);
      }
    }
  }

  private void resetBundle(Bundle bundle) {
    //TODO: RESET / DELETE PARTNER
  }

  public void stop() {
    scheduler.cancel(false);
  }
}
