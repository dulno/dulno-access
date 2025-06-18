package com.dulno.access.user.transmission;

import com.dulno.access.user.UserController;
import com.dulno.access.user.card.UserCardInformationController;
import com.dulno.core.item.Item;
import com.dulno.core.item.ItemDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.item.UserItemDatabaseTable;
import com.dulno.core.user.transmission.UserTransmissionDatabaseTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserTransmissionController extends UserController {
  private final UserTransmissionDatabaseTable userTransmissionDatabaseTable;
  private final UserTransmissionCardMerge userTransmissionCardMerge;
  private final UserItemDatabaseTable userItemDatabaseTable;
  private final ItemDatabaseTable itemDatabaseTable;
  private final UserCardInformationController itemController;

  private UserTransmissionController(
    @Qualifier("appKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    UserTransmissionDatabaseTable userTransmissionDatabaseTable,
    UserTransmissionCardMerge userTransmissionCardMerge,
    UserItemDatabaseTable userItemDatabaseTable,
    ItemDatabaseTable itemDatabaseTable,
    UserCardInformationController itemController
  ) {
    super(secretKey, userDatabaseTable);
    this.userTransmissionDatabaseTable = userTransmissionDatabaseTable;
    this.userTransmissionCardMerge = userTransmissionCardMerge;
    this.userItemDatabaseTable = userItemDatabaseTable;
    this.itemDatabaseTable = itemDatabaseTable;
    this.itemController = itemController;
  }

  @RequestMapping(path = "/user/transmission/request/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> requestUserTransmission(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var itemIds = body.<String>getList("cards").stream()
      .map(UUID::fromString).toList();
    return requestUserTransmission(itemIds);
  }

  private CompletableFuture<Map<String, Object>> requestUserTransmission(
    List<UUID> itemIds
  ) {
    return userTransmissionDatabaseTable.generateAvailableTransmissionId()
      .thenCompose(transmission -> userTransmissionDatabaseTable
        .insertUserTransmission(transmission, itemIds)
        .thenApply(value -> Map.of("success", true, "transmission", transmission)));
  }

  @RequestMapping(path = "/user/transmission/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> completeUserTransmission(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var transmissionId = body.getUUID("transmission");
    return userTransmissionDatabaseTable.userTransmissionExists(transmissionId)
      .thenCompose(exists -> completeUserTransmission(request, response, body,
        transmissionId, exists));
  }

  private CompletableFuture<Map<String, Object>> completeUserTransmission(
    HttpServletRequest request, HttpServletResponse response,
    DulnoRequestBody body, UUID transmissionId, boolean transmissionExists
  ) {
    if (!transmissionExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    return userTransmissionDatabaseTable.findUserTransmission(transmissionId)
      .thenCompose(items -> completeUserTransmission(request, response, body,
        transmissionId, items));
  }

  private CompletableFuture<Map<String, Object>> completeUserTransmission(
    HttpServletRequest request, HttpServletResponse response,
    DulnoRequestBody body, UUID transmissionId, List<UUID> transmissionItemIds
  ) {
    if (!hasAuthorization(request)) {
      return completeUserTransmission(body, transmissionId, transmissionItemIds,
        null, false);
    }
    return findUser(request, response)
      .thenCompose(user -> completeUserTransmission(body, transmissionId,
        transmissionItemIds, user, true));
  }

  private CompletableFuture<Map<String, Object>> completeUserTransmission(
    DulnoRequestBody body, UUID transmissionId, List<UUID> transmissionItemIds,
    User user, boolean hasAuthorization
  ) {
    if (hasAuthorization && user == null) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    userTransmissionDatabaseTable.deleteUserTransmission(transmissionId);
    return findUserItems(body, user, hasAuthorization)
      .thenCompose(userItems -> findUnclaimedItems(transmissionItemIds)
        .thenCompose(transmissionItems -> userTransmissionCardMerge
          .mergeUserItems(user, Lists.newArrayList(userItems), transmissionItems)
          .thenCompose(itemController::collectItemsInformation)
          .thenApply(items -> Map.of("success", true,
            "items", items.get("items")))));
  }

  private CompletableFuture<List<Item>> findUserItems(
    DulnoRequestBody body, User user, boolean hasAuthorization
  ) {
    if (!hasAuthorization) {
      return findUnknownUserItems(body);
    }
    return userItemDatabaseTable.findUserItems(user.id())
      .thenCompose(this::findItems);
  }

  private CompletableFuture<List<Item>> findItems(List<UUID> itemIds) {
    return AsyncIterator.execute(itemIds, itemDatabaseTable::findItem);
  }

  private CompletableFuture<List<Item>> findUnknownUserItems(
    DulnoRequestBody body
  ) {
    if (!body.has("cards")) {
      return CompletableFuture.completedFuture(Lists.newArrayList());
    }
    var itemIds = body.<String>getList("cards")
      .stream().map(UUID::fromString).toList();
    return findUnclaimedItems(itemIds);
  }

  private CompletableFuture<List<Item>> findUnclaimedItems(List<UUID> itemIds) {
    return AsyncIterator.execute(itemIds, this::findUnclaimedItem)
      .thenApply(items -> items.stream().filter(Objects::nonNull).toList());
  }

  private CompletableFuture<Item> findUnclaimedItem(UUID itemId) {
    return itemDatabaseTable.itemExists(itemId)
      .thenCompose(itemExists -> userItemDatabaseTable.userItemExists(itemId)
        .thenCompose(isClaimed -> findUnclaimedItem(itemId, itemExists, isClaimed)));
  }

  private CompletableFuture<Item> findUnclaimedItem(
    UUID itemId, boolean itemExists, boolean isClaimed
  ) {
    if (!itemExists || isClaimed) {
      return CompletableFuture.completedFuture(null);
    }
    return itemDatabaseTable.findItem(itemId);
  }
}