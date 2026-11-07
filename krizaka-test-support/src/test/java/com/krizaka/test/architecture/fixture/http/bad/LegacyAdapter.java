package com.krizaka.test.architecture.fixture.http.bad;

import org.springframework.web.client.RestTemplate;

/** An adapter on the template. */
public class LegacyAdapter {

  public String get() {
    return new RestTemplate().getForObject("http://localhost", String.class);
  }
}
