package com.krizaka.test.architecture.fixture.http.bad;

import org.springframework.web.client.RestClient;

/** An adapter that builds its own client. */
public class RawAdapter {

  private final RestClient client = RestClient.create("http://localhost");

  public String get() {
    return client.get().retrieve().body(String.class);
  }
}
