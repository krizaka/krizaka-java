package com.krizaka.users.persistence.architecture;

import com.krizaka.test.architecture.StandardArchitectureTest;

/** The Krizaka standard for {@code krizaka-users-persistence}; its own rules are beside it. */
class ArchitectureTest extends StandardArchitectureTest {

  ArchitectureTest() {
    super(
        "com.krizaka.users.persistence",
        "EVERY_PACKAGE_NULL_MARKED",
        "krizaka-internal#3278",
        "MAX_TOP_LEVEL_CLASSES_PER_PACKAGE",
        "krizaka-internal#3278");
  }

  @Override
  protected String artifact() {
    return "krizaka-users-persistence";
  }
}
