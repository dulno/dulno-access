package com.dulno.access.portal.stripe;

import com.dulno.access.portal.HomeRestController;
import com.dulno.access.portal.security.home.HomeEndpoint;
import com.dulno.access.portal.verification.Verification;
import com.dulno.access.portal.verification.VerificationLoginController;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.member.Member;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.stripe.StripeCompletionDatabaseTable;
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
public final class StripeCompletionController extends HomeRestController {
  private final Key productKey;
  private final Key refreshKey;
  private final StripeCompletionDatabaseTable stripeCompletionDatabaseTable;
  private final VerificationLoginController verificationLoginController;
  private final Hashing hashing;

  private StripeCompletionController(
    @Qualifier("homeKey") Key homeKey, @Qualifier("productKey") Key productKey,
    @Qualifier("refreshKey") Key refreshKey, MemberDatabaseTable memberDatabaseTable,
    StripeCompletionDatabaseTable stripeCompletionDatabaseTable,
    VerificationLoginController verificationLoginController, Hashing hashing
  ) {
    super(homeKey, memberDatabaseTable);
    this.productKey = productKey;
    this.refreshKey = refreshKey;
    this.stripeCompletionDatabaseTable = stripeCompletionDatabaseTable;
    this.verificationLoginController = verificationLoginController;
    this.hashing = hashing;
  }

  @HomeEndpoint
  @RequestMapping(path = "/stripe/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> completeStripeProcess(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var completionToken = body.getString("token");
    var memberId = findMemberId(request);
    return stripeCompletionDatabaseTable
      .stripeCompletionExists(memberId, completionToken)
      .thenCompose(exists -> checkCompletionExistence(request, memberId,
        completionToken, exists));
  }

  private CompletableFuture<Map<String, Object>> checkCompletionExistence(
    HttpServletRequest request, UUID memberId, String completionToken,
    boolean completionExists
  ) {
    if (!completionExists) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return memberDatabaseTable().findMember(memberId).thenCompose(member ->
      stripeCompletionDatabaseTable
        .isStripeCompletionConfirmed(memberId, completionToken)
        .thenCompose(confirmed -> checkCompletionConfirmed(request, member,
          completionToken, confirmed)));
  }

  private CompletableFuture<Map<String, Object>> checkCompletionConfirmed(
    HttpServletRequest request, Member member,  String completionToken,
    boolean completionConfirmed
  ) {
    if (!completionConfirmed) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    stripeCompletionDatabaseTable.deleteStripeCompletion(member.id(), completionToken);
    var verification = Verification.create(memberDatabaseTable(), secretKey(),
      productKey, refreshKey, hashing, member.email(), "");
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    verificationLoginController.processAuthorizedLogin(request, verification,
      futureResponse);
    return futureResponse;
  }
}