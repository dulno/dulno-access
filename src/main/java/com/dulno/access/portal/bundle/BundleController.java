package com.dulno.access.portal.bundle;

import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.PortalRestController;
import com.dulno.core.bundle.*;
import com.dulno.core.member.MemberDatabaseTable;
import com.dulno.core.partner.PartnerMemberDatabaseTable;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class BundleController extends PortalRestController {
  private final BundleDatabaseTable bundleDatabaseTable;
  private final BundlePresetRepository bundlePresetRepository;
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;

  private BundleController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable,
    BundlePresetRepository bundlePresetRepository,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable
  ) {
    super(secretKey, memberDatabaseTable);
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.bundlePresetRepository = bundlePresetRepository;
    this.partnerMemberDatabaseTable = partnerMemberDatabaseTable;
  }

  @RequestMapping(path = "/bundle/preset/", method = RequestMethod.POST)
  public Map<String, Object> findBundlePreset(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var bundleType = BundleType.valueOf(body.getString("type"));
    var bundleRuntime = BundleRuntime.valueOf(body.getString("runtime"));
    if (bundleType.isEnterprise()) {
      return assemblyBundlePresetInformation(
        bundlePresetRepository.findPreset(BundleType.ENTERPRISE).get(),
        bundleRuntime);
    }
    return assemblyBundlePresetInformation(
      bundlePresetRepository.findPreset(bundleType).get(), bundleRuntime);
  }

  private Map<String, Object> assemblyBundlePresetInformation(
    BundlePreset preset, BundleRuntime runtime
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("price", preset.hasPrice() ? (runtime.isMonthly() ?
      preset.monthlyPrice() : preset.yearlyPrice()) : "NEGOTIABLE");
    information.put("collectionCardAccess", preset.collectionCardAccess());
    information.put("valueCardAccess", preset.valueCardAccess());
    information.put("memberCardAccess", preset.memberCardAccess());
    information.put("analysisAccess", preset.analysisAccess());
    information.put("actionAccess", preset.actionAccess());
    information.put("notificationAccess", preset.notificationAccess());
    information.put("portalAccess", preset.portalAccess());
    information.put("multiPortalUserAccess", preset.multiPortalUserAccess());
    information.put("portalPermissionAccess", preset.portalPermissionAccess());
    return information;
  }

  @RequestMapping(path = "/bundle/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findBundle(
    HttpServletRequest request
  ) {
    var memberId = findMemberId(request);
    return partnerMemberDatabaseTable.findMemberPartner(memberId)
      .thenCompose(target -> findBundle(target, true));
  }

  private CompletableFuture<Map<String, Object>> findBundle(
    UUID targetId, boolean hasPermission
  ) {
    if (!hasPermission) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return bundleDatabaseTable.findBundle(targetId)
      .thenApply(this::assemblyBundleInformation);
  }

  private Map<String, Object> assemblyBundleInformation(Bundle bundle) {
    var information = Maps.<String, Object>newHashMap();
    information.put("type", bundle.bundleType().toString());
    information.put("runtime", bundle.bundleRuntime().toString());
    information.put("price", bundle.price());
    information.put("expiration", bundle.expiration());
    information.put("collectionCardAccess", bundle.collectionCardAccess());
    information.put("valueCardAccess", bundle.valueCardAccess());
    information.put("memberCardAccess", bundle.memberCardAccess());
    information.put("analysisAccess", bundle.analysisAccess());
    information.put("actionAccess", bundle.actionAccess());
    information.put("notificationAccess", bundle.notificationAccess());
    information.put("portalAccess", bundle.portalAccess());
    information.put("multiPortalUserAccess", bundle.multiPortalUserAccess());
    information.put("portalPermissionAccess", bundle.portalPermissionAccess());
    return information;
  }
}
