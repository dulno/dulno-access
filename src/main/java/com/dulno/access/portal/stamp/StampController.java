package com.dulno.access.portal.stamp;

import com.dulno.access.portal.PortalRestController;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.PartnerMemberDatabaseTable;
import com.dulno.core.stamp.Stamp;
import com.dulno.core.stamp.StampDatabaseTable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.security.Key;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Accessors(fluent = true)
@Getter(AccessLevel.PROTECTED)
public class StampController extends PortalRestController {
  private final StampDatabaseTable stampDatabaseTable;
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;

  protected StampController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    StampDatabaseTable stampDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable);
    this.stampDatabaseTable = stampDatabaseTable;
    this.partnerMemberDatabaseTable = partnerMemberDatabaseTable;
  }

  protected void performStampOperation(
    UUID memberId, UUID stampId, Consumer<Stamp> operation,
    Runnable failResponse
  ) {
    stampDatabaseTable.stampExists(stampId).thenAccept(exists ->
      performStampOperation(memberId, stampId, exists, operation,
        failResponse));
  }

  private void performStampOperation(
    UUID memberId, UUID stampId, boolean stampExists,
    Consumer<Stamp> operation, Runnable failResponse
  ) {
    if (!stampExists) {
      failResponse.run();
      return;
    }
    stampDatabaseTable.findStamp(stampId).thenAccept(stamp ->
      checkStampAuthorization(memberId, stamp).thenAccept(authorized ->
        performStampOperation(stamp, authorized, operation, failResponse)));
  }

  private void performStampOperation(
    Stamp stamp, boolean authorized, Consumer<Stamp> operation,
    Runnable failResponse
  ) {
    if (!authorized) {
      failResponse.run();
      return;
    }
    operation.accept(stamp);
  }

  protected CompletableFuture<Boolean> checkStampAuthorization(
    UUID memberId, Stamp stamp
  ) {
    return partnerMemberDatabaseTable.isPartnerMember(stamp.partnerId(), memberId);
  }
}