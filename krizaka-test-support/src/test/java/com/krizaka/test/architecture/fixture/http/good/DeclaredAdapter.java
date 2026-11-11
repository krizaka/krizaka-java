package com.krizaka.test.architecture.fixture.http.good;

import org.springframework.web.client.RestClientException;

/** Translates the client's exception; names no client. */
public class DeclaredAdapter {

  public String get() {
    try {
      return "ok";
    } catch (RestClientException e) {
      return "degraded";
    }
  }
}
