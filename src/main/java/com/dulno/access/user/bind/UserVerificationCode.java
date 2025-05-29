package com.dulno.access.user.bind;

import com.dulno.core.locale.Translation;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.security.SecureRandom;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public final class UserVerificationCode {
  private final UserVerificationCodeTemplate userVerificationCodeTemplate;
  private final Translation translation;
  private final String language;
  private String code;
  private String content;
  private final SecureRandom secureRandom = new SecureRandom();

  public void generate() {
    code = generateCode();
    content = String.format(userVerificationCodeTemplate.userCodeTemplate(),
      translation.translate(language, "user.verification.email.body.headline"),
      code.substring(0, 3), code.substring(3),
      translation.translate(language, "user.verification.email.body.description"));
  }

  private String generateCode() {
    var code = new StringBuilder(6);
    for (var i = 0; i < 6; i++) {
      var digit = secureRandom.nextInt(10);
      code.append(digit);
    }
    return code.toString();
  }
}
