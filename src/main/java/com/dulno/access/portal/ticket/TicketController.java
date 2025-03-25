package com.dulno.access.portal.ticket;

import com.dulno.core.access.PortalRestController;
import com.dulno.core.member.Member;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.ticket.Ticket;
import com.dulno.core.ticket.TicketDatabaseTable;
import com.dulno.core.ticket.TicketMessageDatabaseTable;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.security.Key;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Accessors(fluent = true)
public class TicketController extends PortalRestController {
  private final Key productKey;
  private final Key homeKey;
  @Getter(AccessLevel.PROTECTED)
  private final TicketDatabaseTable ticketDatabaseTable;
  @Getter(AccessLevel.PROTECTED)
  private final TicketMessageDatabaseTable ticketMessageDatabaseTable;

  protected TicketController(
    Key productKey, Key homeKey, MemberDatabaseTable memberDatabaseTable,
    TicketDatabaseTable ticketDatabaseTable,
    TicketMessageDatabaseTable ticketMessageDatabaseTable
  ) {
    super(productKey, memberDatabaseTable);
    this.productKey = productKey;
    this.homeKey = homeKey;
    this.ticketDatabaseTable = ticketDatabaseTable;
    this.ticketMessageDatabaseTable = ticketMessageDatabaseTable;
  }

  protected void performTicketOperation(
    UUID memberId, UUID ticketId, Consumer<Ticket> operation, Runnable failResponse
  ) {
    ticketDatabaseTable.ticketExists(ticketId).thenAccept(exists ->
      performTicketOperation(memberId, ticketId, exists, operation, failResponse));
  }

  private void performTicketOperation(
    UUID memberId, UUID ticketId, boolean ticketExists, Consumer<Ticket> operation,
    Runnable failResponse
  ) {
    if (!ticketExists) {
      failResponse.run();
      return;
    }
    ticketDatabaseTable.findTicket(ticketId).thenAccept(ticket ->
      performTicketOperation(memberId, ticket, operation, failResponse));
  }

  private void performTicketOperation(
    UUID memberId, Ticket ticket, Consumer<Ticket> operation, Runnable failResponse
  ) {
    if (!ticket.creator().equals(memberId)) {
      failResponse.run();
      return;
    }
    operation.accept(ticket);
  }

  protected UUID findMemberId(HttpServletRequest request) {
    if (request.getHeader("Authorization") != null) {
      return findMemberId(findProductApiKey(request), productKey);
    }
    return findMemberId(findHomeApiKey(request), homeKey);
  }

  private UUID findMemberId(String apiKey, Key key) {
    return UUID.fromString(Jwts.parser().setSigningKey(key).build()
      .parseClaimsJws(apiKey).getPayload().get("id", String.class));
  }

  protected UUID findSessionId(HttpServletRequest request) {
    if (request.getHeader("Authorization") != null) {
      return findSessionId(findProductApiKey(request), productKey);
    }
    return findSessionId(findHomeApiKey(request), homeKey);
  }

  private UUID findSessionId(String apiKey, Key key) {
    return UUID.fromString(Jwts.parser().setSigningKey(key).build()
      .parseClaimsJws(apiKey).getPayload().get("session", String.class));
  }

  protected CompletableFuture<Member> findMember(HttpServletRequest request) {
    return memberDatabaseTable().findMember(findMemberId(request));
  }

  private String findProductApiKey(HttpServletRequest request) {
    return request.getHeader("Authorization").replace("Bearer ", "");
  }

  private String findHomeApiKey(HttpServletRequest request) {
    return request.getHeader("Home-Authorization").replace("Bearer ", "");
  }
}
