package com.krizaka.test.architecture.fixture.standard.bad;

import org.springframework.web.client.RestClient;

/** Raw client, public field, standard output, no @NullMarked package. */
public class Shout {

  public final RestClient client = RestClient.create("http://localhost");

  public void say() {
    System.out.println("hello");
  }
}
