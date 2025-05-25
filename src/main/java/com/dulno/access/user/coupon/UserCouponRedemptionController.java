package com.dulno.access.user.coupon;

import com.dulno.access.user.UserController;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.ntag.NTAG;
import com.dulno.core.ntag.cmac.CMACType;
import com.dulno.core.partner.analysis.stamp.PartnerScanDatabaseTable;
import com.dulno.core.redeemable.Redeemable;
import com.dulno.core.redeemable.RedeemableDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.scan.ScanDatabaseTable;
import com.dulno.core.stamp.Stamp;
import com.dulno.core.stamp.StampDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.redeemable.UserRedeemableDatabaseTable;
import com.dulno.core.user.scan.UserScanDatabaseTable;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserCouponRedemptionController extends UserController {
  private final StampDatabaseTable stampDatabaseTable;
  private final RedeemableDatabaseTable redeemableDatabaseTable;
  private final UserRedeemableDatabaseTable userRedeemableDatabaseTable;
  private final ScanDatabaseTable scanDatabaseTable;
  private final PartnerScanDatabaseTable partnerScanDatabaseTable;
  private final UserScanDatabaseTable userScanDatabaseTable;

  private UserCouponRedemptionController(
    @Qualifier("appKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    StampDatabaseTable stampDatabaseTable,
    RedeemableDatabaseTable redeemableDatabaseTable,
    UserRedeemableDatabaseTable userRedeemableDatabaseTable,
    ScanDatabaseTable scanDatabaseTable,
    PartnerScanDatabaseTable partnerScanDatabaseTable,
    UserScanDatabaseTable userScanDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.stampDatabaseTable = stampDatabaseTable;
    this.redeemableDatabaseTable = redeemableDatabaseTable;
    this.userRedeemableDatabaseTable = userRedeemableDatabaseTable;
    this.scanDatabaseTable = scanDatabaseTable;
    this.partnerScanDatabaseTable = partnerScanDatabaseTable;
    this.userScanDatabaseTable = userScanDatabaseTable;
  }

  @RequestMapping(path = "/user/coupon/redeem/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> redeemCoupon(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var redeemableId = body.getUUID("coupon");
    var stampId = body.getUUID("stamp");
    var picc = body.getString("picc");
    var cmac = body.getString("cmac");
    if (!hasAuthorization(request)) {
      return checkStampExistence(null, redeemableId, stampId, picc, cmac);
    }
    return findUser(request, response)
      .thenCompose(user -> checkUserAuthorization(user, redeemableId, stampId,
        picc, cmac));
  }

  private CompletableFuture<Map<String, Object>> checkUserAuthorization(
    User user, UUID redeemableId, UUID stampId, String picc, String cmac
  ) {
    if (user == null) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return checkStampExistence(user, redeemableId, stampId, picc, cmac);
  }

  private CompletableFuture<Map<String, Object>> checkStampExistence(
    User user, UUID redeemableId, UUID stampId, String picc, String cmac
  ) {
    return stampDatabaseTable.stampExists(stampId)
      .thenCompose(exists -> findStamp(user, redeemableId, stampId, picc,
        cmac, exists));
  }

  private CompletableFuture<Map<String, Object>> findStamp(
    User user, UUID redeemableId, UUID stampId, String picc, String cmac,
    boolean stampExists
  ) {
    if (!stampExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    return stampDatabaseTable.findStamp(stampId)
      .thenCompose(stamp -> validateStampScan(user, redeemableId, stamp,
        picc, cmac));
  }

  private CompletableFuture<Map<String, Object>> validateStampScan(
    User user, UUID redeemableId, Stamp stamp, String picc, String cmac
  ) {
    if (stamp.state().isDeactivated()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
    try {
      var ntag = NTAG.of(stamp.masterKey(), picc, cmac, CMACType.LRP);
      if (!ntag.validate()) {
        return CompletableFuture.completedFuture(Map.of("success", false,
          "error", 1002));
      }
      return scanDatabaseTable.scanExists(picc, cmac).thenCompose(exists ->
        checkScanExistence(user, redeemableId, stamp, picc, cmac,
          ntag.counter(), exists));
    } catch (Exception exception) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1003));
    }
  }

  private CompletableFuture<Map<String, Object>> checkScanExistence(
    User user, UUID redeemableId, Stamp stamp, String picc, String cmac,
    int counter, boolean scanExists
  ) {
    if (scanExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1004));
    }
    return redeemableDatabaseTable.redeemableExists(redeemableId)
      .thenCompose(exists -> checkRedeemableExistence(user, redeemableId,
        stamp, picc, cmac, counter, exists));
  }

  private CompletableFuture<Map<String, Object>> checkRedeemableExistence(
    User user, UUID redeemableId, Stamp stamp, String picc, String cmac,
    int counter, boolean redeemableExists
  ) {
    if (!redeemableExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1005));
    }
    return redeemableDatabaseTable.findRedeemable(redeemableId)
      .thenCompose(redeemable -> processRedeemableStamp(user, redeemable,
        stamp, picc, cmac, counter));
  }

  private CompletableFuture<Map<String, Object>> processRedeemableStamp(
    User user, Redeemable redeemable, Stamp stamp, String picc, String cmac,
    int counter
  ) {
    if (!redeemable.partnerId().equals(stamp.partnerId())) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1006));
    }
    return redeemCoupon(user, redeemable)
      .thenCompose(value -> storeScan(user, stamp, picc, cmac, counter,
        redeemable.couponId()));
  }

  private CompletableFuture<Void> redeemCoupon(User user, Redeemable redeemable) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    processes.add(redeemableDatabaseTable.deleteRedeemable(
      redeemable.redeemableId()));
    if (user != null) {
      processes.add(userRedeemableDatabaseTable.deleteUserRedeemable(
        user.id(), redeemable.redeemableId()));
    }
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> null);
  }

  private CompletableFuture<Map<String, Object>> storeScan(
    User user, Stamp stamp, String picc, String cmac, int counter, UUID couponId
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    processes.add(scanDatabaseTable.generateAvailableScanId()
      .thenCompose(scanId -> scanDatabaseTable.insertScan(scanId, picc, cmac,
          stamp.id(), couponId, stamp.partnerId(), counter,
          System.currentTimeMillis())
        .thenCompose(value -> user != null ?
          userScanDatabaseTable.insertUserScan(user.id(), scanId) :
          CompletableFuture.completedFuture(null))));
    processes.add(partnerScanDatabaseTable.insertPartnerScan(stamp.partnerId(),
      System.currentTimeMillis()));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> Map.of("success", true));
  }
}

