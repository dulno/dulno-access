package com.dulno.access.user.bind;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.Charset;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserVerificationCodeTemplate {
  private static final String CONFIGURATION_PATH = "/configurations/mail/user-code-template.html";

  public static UserVerificationCodeTemplate createAndLoad() throws Exception {
    var configuration = new UserVerificationCodeTemplate(CONFIGURATION_PATH);
    configuration.load();
    return configuration;
  }

  private final String path;
  private String userCodeTemplate;

  public void load() throws Exception  {
    userCodeTemplate = FileUtils.readFileToString(
      new File(absolutePath()), Charset.defaultCharset());
  }

  private String absolutePath() {
    return System.getProperty("user.dir") + path;
  }
}