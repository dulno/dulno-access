package com.dulno.access.portal.partner;

import com.dulno.access.portal.PortalRestController;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.*;
import com.dulno.core.partner.link.PartnerLinkDatabaseTable;
import com.dulno.core.partner.location.PartnerLocationDatabaseTable;
import com.dulno.core.partner.logo.PartnerLogoDatabaseTable;
import com.dulno.core.partner.member.PartnerMemberDatabaseTable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.security.Key;
import java.util.UUID;
import java.util.function.Consumer;

@Accessors(fluent = true)
@Getter(AccessLevel.PROTECTED)
public class PartnerController extends PortalRestController {
  private final PartnerDatabaseTable partnerDatabaseTable;
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;
  private final PartnerLogoDatabaseTable partnerLogoDatabaseTable;
  private final PartnerLocationDatabaseTable partnerLocationDatabaseTable;
  private final PartnerLinkDatabaseTable partnerLinkDatabaseTable;

  protected PartnerController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    PartnerDatabaseTable partnerDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable,
    PartnerLogoDatabaseTable partnerLogoDatabaseTable,
    PartnerLocationDatabaseTable partnerLocationDatabaseTable,
    PartnerLinkDatabaseTable partnerLinkDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable);
    this.partnerDatabaseTable = partnerDatabaseTable;
    this.partnerMemberDatabaseTable = partnerMemberDatabaseTable;
    this.partnerLogoDatabaseTable = partnerLogoDatabaseTable;
    this.partnerLocationDatabaseTable = partnerLocationDatabaseTable;
    this.partnerLinkDatabaseTable = partnerLinkDatabaseTable;
  }

  protected void performPartnerOperation(
    UUID memberId, Consumer<Partner> operation, Runnable failResponse
  ) {
    partnerMemberDatabaseTable().findMemberPartner(memberId)
      .thenAccept(partnerId -> partnerDatabaseTable().findPartner(partnerId)
        .thenAccept(operation::accept));
  }
}