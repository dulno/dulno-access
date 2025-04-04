package com.dulno.access.user;

import com.dulno.core.email.EmailValidation;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.request.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserBindController extends UserController {
  private UserBindController(
    UserDatabaseTable userDatabaseTable, Hashing hashing
  ) {
    super(userDatabaseTable, hashing);
  }

  @RequestMapping(path = "/user/binding/find/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findUserBinding(
    HttpServletRequest request
  ) {
    return findUser(request).thenApply(user -> user.email().isEmpty() ?
      Map.of("bound", false) : Map.of("bound", true, "email", user.email()));
  }

  @RequestMapping(path = "/user/bind/request/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> requestUserBinding(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var email = body.getString("email");
    if (!EmailValidation.validate(email)) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    return findUser(request)
      .thenCompose(user -> userDatabaseTable().userExists(email)
        .thenCompose(alreadyExists -> requestUserBinding(user, email,
          alreadyExists)));
  }

  private CompletableFuture<Map<String, Object>> requestUserBinding(
    User user, String email, boolean alreadyExists
    ) {
    if (alreadyExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
    return null;
  }

  @RequestMapping(path = "/user/bind/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> completeUserBinding(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    return null;
  }

  @RequestMapping(path = "/user/bind/google/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> bindGoogleUser(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    return null;
  }
}