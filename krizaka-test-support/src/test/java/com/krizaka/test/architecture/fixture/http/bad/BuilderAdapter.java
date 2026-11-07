package com.krizaka.test.architecture.fixture.http.bad;

import org.springframework.web.client.RestClient;

/** Only a nested type of the client: the builder. */
public class BuilderAdapter {

  public void configure(RestClient.Builder builder) {
    builder.baseUrl("http://localhost");
  }
}
