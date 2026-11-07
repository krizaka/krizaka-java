package com.krizaka.test.architecture.fixture.http.good.config;

import org.springframework.web.client.RestClient;

/** The one place a client is built. */
public class ClientWiring {

  public RestClient client() {
    return RestClient.create("http://localhost");
  }
}
