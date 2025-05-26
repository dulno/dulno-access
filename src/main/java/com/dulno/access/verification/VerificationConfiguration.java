package com.dulno.access.verification;

import com.dulno.core.configuration.Configuration;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.json.JSONObject;

@Getter
@Accessors(fluent = true)
public final class VerificationConfiguration extends Configuration {
  private static final String CONFIGURATION_PATH = "/configurations/verification/verification.json";

  public static VerificationConfiguration createAndLoad() throws Exception {
    var configuration = new VerificationConfiguration(CONFIGURATION_PATH);
    configuration.load();
    return configuration;
  }

  private String homeSecret;
  private String portalSecret;
  private String portalRefreshSecret;
  private String appSecret;
  private String appRefreshSecret;

  private VerificationConfiguration(String path) {
    super(path);
  }

  @Override
  protected void deserialize(JSONObject json) {
    homeSecret = json.getString("homeSecret");
    portalSecret = json.getString("portalSecret");
    portalRefreshSecret = json.getString("portalRefreshSecret");
    appSecret = json.getString("appSecret");
    appRefreshSecret = json.getString("appRefreshSecret");
  }
}
