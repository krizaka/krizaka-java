package com.krizaka.web.architecture;

import com.krizaka.test.architecture.StandardArchitectureTest;

class ArchitectureTest extends StandardArchitectureTest {

  ArchitectureTest() {
    super(
        "com.krizaka.web",
        "FIELDS_PRIVATE",
        "krizaka-internal#3261",
        "EVERY_PACKAGE_NULL_MARKED",
        "krizaka-internal#3261");
  }
}
