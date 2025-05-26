package com.dulno.access.user.bind;

import com.dulno.core.item.ItemDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.device.UserDeviceDatabaseTable;
import com.dulno.core.user.item.UserItemDatabaseTable;
import com.dulno.core.user.verification.UserVerificationDatabaseTable;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Configuration
@RequiredArgsConstructor
public class UserBindSignup {
  private final UserDatabaseTable userDatabaseTable;
  private final UserDeviceDatabaseTable userDeviceDatabaseTable;
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final ItemDatabaseTable itemDatabaseTable;
  private final UserItemDatabaseTable userItemDatabaseTable;

  public CompletableFuture<User> signupUser(String email, DulnoRequestBody body) {
    return generateAvailableUserId()
      .thenCompose(userId -> signupUser(userId, email, body));
  }

  public CompletableFuture<User> signupUser(
    UUID userId, String email, DulnoRequestBody body
  ) {
    var legalAccepted = body.getBoolean("legalAccepted");
    if (!legalAccepted) {
      return CompletableFuture.completedFuture(null);
    }
    var itemIds = body.<String>getList("cards").stream()
      .map(UUID::fromString).toList();
    return userDeviceDatabaseTable.generateAvailableUserDeviceId()
      .thenCompose(deviceId -> signupUser(userId, email, deviceId,
        body.getString("language"), legalAccepted, body.getBoolean("newsletter"),
        body.getString("id"), body.getString("operatingSystem"),
        body.getString("operatingSystemVersion"), body.getString("brand"),
        body.getString("model"), body.getString("name"), itemIds));
  }

  private CompletableFuture<User> signupUser(
    UUID userId, String email, UUID deviceId, String language, boolean compliant,
    boolean newsletter, String publicDeviceId, String operatingSystem,
    String operatingSystemVersion, String brand, String model, String name,
    List<UUID> itemIds
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    language = parseUserLanguage(language);
    var user = User.create(userId, email, language, compliant, newsletter,
      System.currentTimeMillis());
    processes.add(userDatabaseTable.insertUser(user));
    processes.add(userDeviceDatabaseTable.insertUserDevice(deviceId, userId,
      publicDeviceId, operatingSystem, operatingSystemVersion, brand, model, name));
    for (var itemId : itemIds) {
      processes.add(claimItem(userId, itemId));
    }
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> user);
  }

  private CompletableFuture<Void> claimItem(UUID userId, UUID itemId) {
    return itemDatabaseTable.itemExists(itemId)
      .thenCompose(itemExists -> userItemDatabaseTable.userItemExists(itemId)
        .thenCompose(isClaimed -> claimItem(userId, itemId, itemExists,
          isClaimed)));
  }

  private CompletableFuture<Void> claimItem(
    UUID userId, UUID itemId, boolean itemExists, boolean isClaimed
  ) {
    if (!itemExists || isClaimed) {
      return CompletableFuture.completedFuture(null);
    }
    return userItemDatabaseTable.insertUserItem(userId, itemId);
  }

  private String parseUserLanguage(String language) {
    if (language.equals("en") || language.equals("de")) {
      return language;
    }
    return "en";
  }

  public CompletableFuture<UUID> generateAvailableUserId() {
    var futureResponse = new CompletableFuture<UUID>();
    var id = UUID.randomUUID();
    userDatabaseTable.userExists(id).thenCompose(userExists -> userExists ?
      generateAvailableUserId().thenApply(futureResponse::complete) :
      userVerificationDatabaseTable.userVerificationExists(id)
        .thenCompose(verificationExists -> verificationExists ?
          generateAvailableUserId().thenApply(futureResponse::complete) :
          CompletableFuture.completedFuture(futureResponse.complete(id))));
    return futureResponse;
  }
}
