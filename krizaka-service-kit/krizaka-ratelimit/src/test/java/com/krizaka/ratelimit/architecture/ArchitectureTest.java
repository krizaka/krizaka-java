package com.krizaka.ratelimit.architecture;

import com.krizaka.test.architecture.StandardArchitectureTest;

class ArchitectureTest extends StandardArchitectureTest {

  ArchitectureTest() {
    super(
        "com.krizaka.ratelimit",
        "FIELDS_PRIVATE",
        "krizaka-internal#3259",
        "EVERY_PACKAGE_NULL_MARKED",
        "krizaka-internal#3259");
  }
}
