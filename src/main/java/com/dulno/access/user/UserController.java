package com.dulno.access.user;

import com.dulno.core.user.UserDatabaseTable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
@Getter(AccessLevel.PROTECTED)
public class UserController {
  private final UserDatabaseTable userDatabaseTable;

  protected UserController(UserDatabaseTable userDatabaseTable) {
    this.userDatabaseTable = userDatabaseTable;
  }
}
