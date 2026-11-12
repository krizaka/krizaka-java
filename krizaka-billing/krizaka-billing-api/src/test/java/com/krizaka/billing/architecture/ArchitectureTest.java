package com.krizaka.billing.architecture;

import com.krizaka.test.architecture.StandardArchitectureTest;

/** The Krizaka standard for {@code krizaka-billing-api}; its siblings share the base package. */
class ArchitectureTest extends StandardArchitectureTest {

  ArchitectureTest() {
    super("com.krizaka.billing", "EVERY_PACKAGE_NULL_MARKED", "krizaka-internal#3271");
  }

  @Override
  protected String artifact() {
    return "krizaka-billing-api";
  }
}
