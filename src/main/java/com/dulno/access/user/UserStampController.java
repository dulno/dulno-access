package com.dulno.access.user;

import com.dulno.core.hashing.Hashing;
import com.dulno.core.ntag.NTAG;
import com.dulno.core.ntag.cmac.CMACType;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.UserCardDatabaseTable;
import com.dulno.core.user.UserDatabaseTable;
import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserStampController extends UserController {
  private final UserCardDatabaseTable userCardDatabaseTable;

  private UserStampController(
    UserDatabaseTable userDatabaseTable, Hashing hashing,
    UserCardDatabaseTable userCardDatabaseTable
  ) {
    super(userDatabaseTable, hashing);
    this.userCardDatabaseTable = userCardDatabaseTable;
  }

  //TODO: STORE IN DB
  private final List<String> knownStamps = Lists.newArrayList();

  //TODO: STORE IN DB
  private static String KEY = "00000000000000000000000000000000";

  @RequestMapping(path = "/user/stamp/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> applyUserStamp(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
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
