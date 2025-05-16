package com.dulno.access.user.statistic;

import com.dulno.access.user.UserController;
import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.statistic.UserAppOpenDatabaseTable;
import com.dulno.core.user.statistic.UserJoinDatabaseTable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserStatisticController extends UserController {
  private final UserJoinDatabaseTable userJoinDatabaseTable;
  private final UserAppOpenDatabaseTable userAppOpenDatabaseTable;

  private UserStatisticController(
    UserDatabaseTable userDatabaseTable, Hashing hashing,
    DulnoEnvironment environment, ErrorRepository errorRepository,
    UserJoinDatabaseTable userJoinDatabaseTable,
    UserAppOpenDatabaseTable userAppOpenDatabaseTable
  ) {
    super(userDatabaseTable, hashing, environment, errorRepository);
    this.userJoinDatabaseTable = userJoinDatabaseTable;
    this.userAppOpenDatabaseTable = userAppOpenDatabaseTable;
  }

  @RequestMapping(path = "/user/join/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> noteUserJoin() {
    return userJoinDatabaseTable.insertUserJoin(System.currentTimeMillis())
      .thenApply(partners -> Map.of("success", true));
  }

  @RequestMapping(path = "/user/app/open/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> noteUserAppOpen() {
    return userAppOpenDatabaseTable.insertUserAppOpen(System.currentTimeMillis())
      .thenApply(partners -> Map.of("success", true));
  }
}
