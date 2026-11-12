package com.krizaka.notifications.architecture;

import com.krizaka.test.architecture.StandardArchitectureTest;

class ArchitectureTest extends StandardArchitectureTest {

  ArchitectureTest() {
    super("com.krizaka.notifications", "EVERY_PACKAGE_NULL_MARKED", "krizaka-internal#3256");
  }

  @Override
  protected String artifact() {
    return "krizaka-notifications-api";
  }
}
