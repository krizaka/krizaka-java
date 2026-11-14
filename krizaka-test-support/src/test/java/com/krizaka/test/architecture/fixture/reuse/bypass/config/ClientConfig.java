package com.krizaka.test.architecture.fixture.reuse.bypass.config;

import org.springframework.web.client.RestClient;

/** A config class may build the client. */
public class ClientConfig {
  public RestClient build() {
    return RestClient.create();
  }
}
