package com.dulno.access.portal.accessory;

import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.PortalRestController;
import com.dulno.core.accessory.AccessoryDatabaseTable;
import com.dulno.core.accessory.AccessoryPreset;
import com.dulno.core.accessory.AccessoryPresetRepository;
import com.dulno.core.locale.Translation;
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

@RestController
public final class AccessoryController extends PortalRestController {
  private final AccessoryDatabaseTable accessoryDatabaseTable;
  private final AccessoryPresetRepository accessoryPresetRepository;
  private final PartnerMemberDatabaseTable partnerMemberDatabaseTable;
  private final Translation translation;

  private AccessoryController(
    Key secretKey, MemberDatabaseTable memberDatabaseTable,
    AccessoryDatabaseTable accessoryDatabaseTable,
    AccessoryPresetRepository accessoryPresetRepository,
    PartnerMemberDatabaseTable partnerMemberDatabaseTable, Translation translation
  ) {
    super(secretKey, memberDatabaseTable);
    this.accessoryDatabaseTable = accessoryDatabaseTable;
    this.accessoryPresetRepository = accessoryPresetRepository;
    this.partnerMemberDatabaseTable = partnerMemberDatabaseTable;
    this.translation = translation;
  }

  @RequestMapping(path = "/accessories/list/", method = RequestMethod.POST)
  public Map<String, Object> listAccessories(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    return Map.of("accessories", accessoryPresetRepository.findAll().stream()
      .map(preset -> assemblyAccessoryPresetInformation(body.getString("language"),
        preset)).toList());
  }

  private Map<String, Object> assemblyAccessoryPresetInformation(
    String language, AccessoryPreset preset
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", preset.id());
    information.put("price", preset.price());
    information.put("name", translation.translate(language, preset.name()));
    information.put("description", translation.translate(language,
      preset.description()));
    return information;
  }
}
