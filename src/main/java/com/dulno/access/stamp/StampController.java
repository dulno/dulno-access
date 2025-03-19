package com.dulno.access.stamp;

import com.google.common.collect.Lists;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.ntag.NTAG;
import com.dulno.core.ntag.cmac.CMACType;
import com.dulno.core.user.UserDatabaseTable;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StampController extends DulnoRestController {
  //TODO: STORE IN DB
  private final List<String> knownStamps = Lists.newArrayList();

  private StampController(
    Key secretKey, UserDatabaseTable userDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
  }

  //TODO: STORE IN DB
  private static String KEY = "00000000000000000000000000000000";

  @RequestMapping(path = "/stamp/apply/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> applyStamp(
    @RequestBody String payload, HttpServletResponse response
  ) throws Exception {
    var body = DulnoRequestBody.of(payload, response);
    //var userId = body.getUUID("user");
    var picc = body.getString("picc");
    var cmac = body.getString("cmac");
    var ntag = NTAG.of(KEY, picc, cmac, CMACType.AES);
    if (!ntag.validate()) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    if (knownStamps.contains(picc)) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    knownStamps.add(picc);
    return CompletableFuture.completedFuture(Map.of("success", true));
  }
}
