package com.dulno.access.user.statistic;

import com.dulno.access.user.UserController;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.statistic.UserAppOpenDatabaseTable;
import com.dulno.core.user.statistic.UserJoinDatabaseTable;
import com.dulno.core.user.statistic.UserStatisticConfiguration;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserStatisticController extends UserController {
  private final UserJoinDatabaseTable userJoinDatabaseTable;
  private final UserAppOpenDatabaseTable userAppOpenDatabaseTable;
  private final UserStatisticConfiguration userStatisticConfiguration;

  private UserStatisticController(
    @Qualifier("appKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    UserJoinDatabaseTable userJoinDatabaseTable,
    UserAppOpenDatabaseTable userAppOpenDatabaseTable,
    UserStatisticConfiguration userStatisticConfiguration
  ) {
    super(secretKey, userDatabaseTable);
    this.userJoinDatabaseTable = userJoinDatabaseTable;
    this.userAppOpenDatabaseTable = userAppOpenDatabaseTable;
    this.userStatisticConfiguration = userStatisticConfiguration;
  }

  @RequestMapping(path = "/user/join/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> noteUserJoin(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    if (!checkStatisticKey(body)) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return userJoinDatabaseTable.insertUserJoin(System.currentTimeMillis())
      .thenApply(partners -> Map.of("success", true));
  }

  @RequestMapping(path = "/user/app/open/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> noteUserAppOpen(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    if (!checkStatisticKey(body)) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return userAppOpenDatabaseTable.insertUserAppOpen(System.currentTimeMillis(),
        body.getSanitizedString("version", 32))
      .thenApply(partners -> Map.of("success", true));
  }

  private boolean checkStatisticKey(DulnoRequestBody body) {
    var originalKey = userStatisticConfiguration.statisticKey()
      .getBytes(StandardCharsets.UTF_8);
    var providedKey = body.getString("key").getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(originalKey, providedKey);
  }
}
