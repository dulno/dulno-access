package com.dulno.access.user.partner;

import com.dulno.access.user.UserController;
import com.dulno.core.partner.PartnerDatabaseTable;
import com.dulno.core.partner.report.PartnerReport;
import com.dulno.core.partner.report.PartnerReportDatabaseTable;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.UserDatabaseTable;
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
public final class UserPartnerReportController extends UserController {
  private final PartnerDatabaseTable partnerDatabaseTable;
  private final PartnerReportDatabaseTable partnerReportDatabaseTable;

  private UserPartnerReportController(
    @Qualifier("appKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    PartnerDatabaseTable partnerDatabaseTable,
    PartnerReportDatabaseTable partnerReportDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.partnerDatabaseTable = partnerDatabaseTable;
    this.partnerReportDatabaseTable = partnerReportDatabaseTable;
  }

  @RequestMapping(path = "/user/partner/report/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> reportPartner(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var partnerId = body.getUUID("partner");
    var message = body.getSanitizedString("message", 512);
    return partnerDatabaseTable.partnerExists(partnerId)
      .thenCompose(exists -> reportPartner(partnerId, message, exists));
  }

  private CompletableFuture<Map<String, Object>> reportPartner(
    UUID partnerId, String message, boolean partnerExists
  ) {
    if (!partnerExists) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return partnerReportDatabaseTable.generateAvailablePartnerReportId()
      .thenApply(id -> PartnerReport.create(id, partnerId, message,
        PartnerReport.Status.OPEN, System.currentTimeMillis()))
      .thenCompose(partnerReportDatabaseTable::insertPartnerReport)
      .thenApply(value -> Map.of("success", true));
  }
}
