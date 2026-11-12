package com.krizaka.users.service.architecture;

import com.krizaka.test.architecture.StandardArchitectureTest;

/** The Krizaka standard for {@code krizaka-users-service}; its own rules are beside it. */
class ArchitectureTest extends StandardArchitectureTest {

  ArchitectureTest() {
    super(
        "com.krizaka.users.service",
        "FIELDS_PRIVATE",
        "krizaka-internal#3279",
        "EVERY_PACKAGE_NULL_MARKED",
        "krizaka-internal#3279");
  }

  @Override
  protected String artifact() {
    return "krizaka-users-service";
  }
}
