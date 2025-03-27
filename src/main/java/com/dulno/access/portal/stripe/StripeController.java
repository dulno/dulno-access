package com.dulno.access.portal.stripe;

import com.dulno.core.access.PortalRestController;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.PartnerDatabaseTable;
import com.dulno.core.partner.PartnerMemberDatabaseTable;
import com.dulno.core.stripe.StripeAccount;
import com.dulno.core.stripe.StripeConfiguration;
import com.dulno.core.stripe.StripeDatabaseTable;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.security.Key;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Getter(AccessLevel.PROTECTED)
@Accessors(fluent = true)
public class StripeController extends PortalRestController {
  private final StripeConfiguration stripeConfiguration;
  private final StripeDatabaseTable stripeDatabaseTable;
  private final PartnerDatabaseTable partnerDatabaseTable;
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;

  protected StripeController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    StripeConfiguration stripeConfiguration,
    StripeDatabaseTable stripeDatabaseTable,
    PartnerDatabaseTable partnerDatabaseTable,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable);
    this.stripeConfiguration = stripeConfiguration;
    this.stripeDatabaseTable = stripeDatabaseTable;
    this.partnerDatabaseTable = partnerDatabaseTable;
    this.partnerMemberDatabaseTable = partnerMemberDatabaseTable;
  }

  protected void performStripeOperation(
    UUID memberId, Consumer<StripeAccount> operation,
    Runnable failResponse
  ) {
    partnerMemberDatabaseTable.findMemberPartner(memberId).thenAccept(partnerId ->
      stripeDatabaseTable.stripeAccountExistsByPartner(partnerId).thenAccept(exists ->
        performStripeOperation(partnerId, exists, operation, failResponse)));
  }

  private void performStripeOperation(
    UUID partnerId, boolean accountExists,
    Consumer<StripeAccount> operation, Runnable failResponse
  ) {
    if (!accountExists) {
      failResponse.run();
      return;
    }
    stripeDatabaseTable.findStripeAccountByPartner(partnerId)
      .thenAccept(operation::accept);
  }

  protected Optional<StripeObject> findStripeObject(Event event) {
    var dataObjectDeserializer = event.getDataObjectDeserializer();
    if (dataObjectDeserializer.getObject().isEmpty()) {
      return Optional.empty();
    }
    return dataObjectDeserializer.getObject();
  }

  protected Optional<Event> findStripeEvent(
    HttpServletRequest request, String payload, String secret
  ) {
    var signature = request.getHeader("Stripe-Signature");
    try {
      return Optional.of(Webhook.constructEvent(payload, signature, secret));
    } catch (Exception exception) {
      return Optional.empty();
    }
  }
}

