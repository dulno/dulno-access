package com.dulno.access.user;

import com.dulno.core.hashing.Hashing;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserDeviceDatabaseTable;
import com.dulno.core.user.UserFirebaseDatabaseTable;
import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserSignupController extends UserController {
  private final UserDeviceDatabaseTable userDeviceDatabaseTable;
  private final UserFirebaseDatabaseTable userFirebaseDatabaseTable;

  private UserSignupController(
    UserDatabaseTable userDatabaseTable, Hashing hashing,
    UserDeviceDatabaseTable userDeviceDatabaseTable,
    UserFirebaseDatabaseTable userFirebaseDatabaseTable
  ) {
    super(userDatabaseTable, hashing);
    this.userDeviceDatabaseTable = userDeviceDatabaseTable;
    this.userFirebaseDatabaseTable = userFirebaseDatabaseTable;
  }

  @RequestMapping(path = "/user/signup/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> signupUser(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var legalAccepted = body.getBoolean("legalAccepted");
    if (!legalAccepted) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    return userDatabaseTable().generateAvailableUserId()
      .thenCompose(userId -> userDeviceDatabaseTable.generateAvailableUserDeviceId()
        .thenCompose(deviceId -> signupUser(userId, deviceId,
          body.getString("language"), legalAccepted, body.getString("id"),
          body.getString("operatingSystem"),
          body.getString("operatingSystemVersion"), body.getString("brand"),
          body.getString("model"), body.getString("name"),
          body.getString("firebaseIdentifier"))));
  }

  public CompletableFuture<Map<String, Object>> signupUser(
    UUID userId, UUID deviceId, String language, boolean legalAccepted,
    String publicDeviceId, String operatingSystem, String operatingSystemVersion,
    String brand, String model, String name, String firebaseIdentifier
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    var authenticationKey = generateAuthenticationKey();
    language = parseUserLanguage(language);
    processes.add(userDatabaseTable().insertUser(userId, authenticationKey, "",
      language, legalAccepted, false, System.currentTimeMillis()));
    processes.add(userDeviceDatabaseTable.insertUserDevice(deviceId, userId,
      publicDeviceId, operatingSystem, operatingSystemVersion, brand, model, name));
    processes.add(userFirebaseDatabaseTable.insertUserFirebase(userId, deviceId,
      firebaseIdentifier));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> Map.of("success", true, "id", userId,
        "authenticationKey", authenticationKey));
  }

  private String parseUserLanguage(String language) {
    if (language.equals("en") || language.equals("de")) {
      return language;
    }
    return "en";
  }
}
