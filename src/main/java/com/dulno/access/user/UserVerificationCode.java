package com.dulno.access.user;

import com.dulno.core.locale.Translation;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.security.SecureRandom;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public final class UserVerificationCode {
  private final Translation translation;
  private final String language;
  private String code;
  private String content;
  private final SecureRandom secureRandom = new SecureRandom();

  private static final String HTML_CONTENT_FORMAT =
    "<div style=\"font-family: Arial, sans-serif; text-align: center;\">" +
      "  <div style=\"font-size: 20px; font-weight: bold; margin-bottom: 15px;\">" +
      "    %s" +
      "  </div>" +
      "  <div style=\"font-size: 36px; letter-spacing: 10px; font-weight: bold; color: #2b7cff; background-color: #f0f4ff; padding: 15px 25px; border-radius: 8px; display: inline-block;\">" +
      "    %s" +
      "  </div>" +
      "  <div style=\"font-size: 12px; margin-top: 20px; color: #888; width: min(300px, 100%%); margin-left: auto; margin-right: auto\">" +
      "    %s" +
      "  </div>" +
      "</div>";

  public void generate() {
    code = generateCode();
    content = String.format(HTML_CONTENT_FORMAT,
      translation.translate(language, "user.verification.email.body.headline"),
      code.substring(0, 3) + "&nbsp;" + code.substring(3),
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
