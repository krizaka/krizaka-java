package com.krizaka.users.architecture;

import com.krizaka.test.architecture.StandardArchitectureTest;

/** The Krizaka standard for {@code krizaka-users-api}; its own rules are beside it. */
class ArchitectureTest extends StandardArchitectureTest {

  ArchitectureTest() {
    super("com.krizaka.users", "EVERY_PACKAGE_NULL_MARKED", "krizaka-internal#3275");
  }

  @Override
  protected String artifact() {
    return "krizaka-users-api";
  }
}
