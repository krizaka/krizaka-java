package com.krizaka.messaging.architecture;

import com.krizaka.test.architecture.StandardArchitectureTest;

class ArchitectureTest extends StandardArchitectureTest {

  ArchitectureTest() {
    super(
        "com.krizaka.messaging",
        "MAX_TOP_LEVEL_CLASSES_PER_PACKAGE",
        "krizaka-internal#3255",
        "EVERY_PACKAGE_NULL_MARKED",
        "krizaka-internal#3255");
  }
}
