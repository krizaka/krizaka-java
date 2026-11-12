package com.krizaka.webhooks.architecture;

import com.krizaka.test.architecture.StandardArchitectureTest;

class ArchitectureTest extends StandardArchitectureTest {

  ArchitectureTest() {
    super(
        "com.krizaka.webhooks",
        "NO_RAW_HTTP_CLIENT_IN_ADAPTERS",
        "krizaka-internal#3262",
        "EVERY_PACKAGE_NULL_MARKED",
        "krizaka-internal#3262");
  }
}
