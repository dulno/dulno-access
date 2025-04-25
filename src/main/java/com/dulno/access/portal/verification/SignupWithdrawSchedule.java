package com.dulno.access.portal.verification;

import com.dulno.access.portal.setting.AccountSettingController;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.member.Member;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.member.MemberVerificationDatabaseTable;
import com.dulno.core.worker.environment.WorkerEnvironment;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public final class SignupWithdrawSchedule {
  private final MemberVerificationDatabaseTable memberVerificationDatabaseTable;
  private final MemberDatabaseTable memberDatabaseTable;
  private final AccountSettingController accountSettingController;
  private final WorkerEnvironment workerEnvironment;
  private final ScheduledExecutorService executorService =
    Executors.newScheduledThreadPool(1);
  private ScheduledFuture<?> scheduler;

  public SignupWithdrawSchedule(
    MemberVerificationDatabaseTable memberVerificationDatabaseTable,
    MemberDatabaseTable memberDatabaseTable,
    AccountSettingController accountSettingController,
    WorkerEnvironment workerEnvironment
  ) {
    this.memberVerificationDatabaseTable = memberVerificationDatabaseTable;
    this.memberDatabaseTable = memberDatabaseTable;
    this.accountSettingController = accountSettingController;
    this.workerEnvironment = workerEnvironment;
  }

  private static final int WITHDRAW_INTERVAL = 1000 * 60 * 60 * 24;
  private static final TimeUnit WITHDRAW_TIME_UNIT = TimeUnit.MILLISECONDS;

  public void start() {
    scheduler = executorService.scheduleAtFixedRate(this::execute,
      calculateInitialDelay(), WITHDRAW_INTERVAL, WITHDRAW_TIME_UNIT);
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
    memberVerificationDatabaseTable.findAllVerifications()
      .thenCompose(members -> AsyncIterator.execute(members,
          memberDatabaseTable::findMember)
        .thenAccept(this::processPendingMembers));
  }

  private static final long WITHDRAW_THRESHOLD = 1000L * 60 * 60 * 24 * 7;

  private void processPendingMembers(List<Member> pendingMembers) {
    var current = System.currentTimeMillis();
    for (var member : pendingMembers) {
      if (current - member.accession() >= WITHDRAW_THRESHOLD) {
        accountSettingController.deleteAccount(member);
      }
    }
  }

  public void stop() {
    scheduler.cancel(false);
  }
}
