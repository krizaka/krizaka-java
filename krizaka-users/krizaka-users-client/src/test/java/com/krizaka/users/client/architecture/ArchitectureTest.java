package com.krizaka.users.client.architecture;

import com.krizaka.test.architecture.StandardArchitectureTest;

/** The Krizaka standard for {@code krizaka-users-client}; its own rules are beside it. */
class ArchitectureTest extends StandardArchitectureTest {

  ArchitectureTest() {
    super(
        "com.krizaka.users.client",
        "NO_RAW_HTTP_CLIENT_IN_ADAPTERS",
        "krizaka-internal#3276",
        "EVERY_PACKAGE_NULL_MARKED",
        "krizaka-internal#3276");
  }

  @Override
  protected String artifact() {
    return "krizaka-users-client";
  }
}
