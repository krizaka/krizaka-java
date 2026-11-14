package com.krizaka.test.architecture.fixture.reuse.bypass;

import org.springframework.web.client.RestClient;

/** Goes round the facade. */
public class DirectCaller {
  private final RestClient client = RestClient.create();

  public RestClient client() {
    return client;
  }
}
