package com.krizaka.billing.service.architecture;

import com.krizaka.test.architecture.StandardArchitectureTest;

/** The Krizaka standard for {@code krizaka-billing-service}; its own rules are beside it. */
class ArchitectureTest extends StandardArchitectureTest {

  ArchitectureTest() {
    super(
        "com.krizaka.billing.service",
        "MAX_TOP_LEVEL_CLASSES_PER_PACKAGE",
        "krizaka-internal#3272",
        "EVERY_PACKAGE_NULL_MARKED",
        "krizaka-internal#3272");
  }

  @Override
  protected String artifact() {
    return "krizaka-billing-service";
  }
}
