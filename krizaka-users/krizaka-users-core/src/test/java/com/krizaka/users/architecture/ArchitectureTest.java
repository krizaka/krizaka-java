package com.krizaka.users.architecture;

import com.krizaka.test.architecture.StandardArchitectureTest;

/** The Krizaka standard for {@code krizaka-users-core}; its own rules are beside it. */
class ArchitectureTest extends StandardArchitectureTest {

  ArchitectureTest() {
    super(
        "com.krizaka.users",
        "NO_RAW_HTTP_CLIENT_IN_ADAPTERS",
        "krizaka-internal#3277",
        "MAX_TOP_LEVEL_CLASSES_PER_PACKAGE",
        "krizaka-internal#3277",
        "EVERY_PACKAGE_NULL_MARKED",
        "krizaka-internal#3277");
  }

  @Override
  protected String artifact() {
    return "krizaka-users-core";
  }
}
