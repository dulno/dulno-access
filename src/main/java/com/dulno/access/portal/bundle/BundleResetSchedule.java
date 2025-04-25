package com.dulno.access.portal.bundle;

import com.dulno.access.portal.partner.PartnerDeletion;
import com.dulno.core.bundle.Bundle;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.worker.environment.WorkerEnvironment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public final class BundleResetSchedule {
  private final BundleDatabaseTable bundleDatabaseTable;
  private final WorkerEnvironment workerEnvironment;
  private final PartnerDeletion partnerDeletion;
  private final ScheduledExecutorService executorService =
    Executors.newScheduledThreadPool(1);
  private ScheduledFuture<?> scheduler;

  private static final long CHECK_INTERVAL = 1000L * 60 * 60 * 24;
  private static final TimeUnit CHECK_TIME_UNIT = TimeUnit.MILLISECONDS;

  public void start() {
    scheduler = executorService.scheduleAtFixedRate(this::execute,
      calculateInitialDelay(), CHECK_INTERVAL, CHECK_TIME_UNIT);
  }

  private long calculateInitialDelay() {
    var calendar = Calendar.getInstance();
    calendar.add(Calendar.DAY_OF_MONTH, 1);
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    return calendar.getTimeInMillis() - System.currentTimeMillis();
  }

  private void execute() {
    if (workerEnvironment.state().isWorker()) {
      return;
    }
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
        partnerDeletion.deletePartner(bundle.partnerId());
      }
    }
  }

  public void stop() {
    scheduler.cancel(false);
  }
}
