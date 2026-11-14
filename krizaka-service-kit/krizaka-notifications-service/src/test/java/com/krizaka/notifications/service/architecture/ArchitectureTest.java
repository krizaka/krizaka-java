package com.krizaka.notifications.service.architecture;

import com.krizaka.test.architecture.StandardArchitectureTest;

class ArchitectureTest extends StandardArchitectureTest {

  ArchitectureTest() {
    super(
        "com.krizaka.notifications.service",
        "NO_PACKAGE_CYCLES",
        "krizaka-internal#3263",
        "NO_RAW_HTTP_CLIENT_IN_ADAPTERS",
        "krizaka-internal#3266",
        "EVERY_PACKAGE_NULL_MARKED",
        "krizaka-internal#3268",
        "NO_LOCAL_COPY_OF_LIBRARY_TYPE",
        "krizaka-internal#3591");
  }

  @Override
  protected String artifact() {
    return "krizaka-notifications-service";
  }
}
