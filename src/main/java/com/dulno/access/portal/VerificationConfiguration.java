package com.dulno.access.portal;

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
  private String productSecret;
  private String refreshSecret;

  private VerificationConfiguration(String path) {
    super(path);
  }

  @Override
  protected void deserialize(JSONObject json) {
    homeSecret = json.getString("homeSecret");
    productSecret = json.getString("productSecret");
    refreshSecret = json.getString("refreshSecret");
  }
}
