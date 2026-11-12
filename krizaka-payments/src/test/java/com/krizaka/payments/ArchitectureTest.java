package com.krizaka.payments;

import com.krizaka.test.architecture.StandardArchitectureTest;

/** The Krizaka standard, strictly; the Stripe boundary is {@link StripeBoundaryTest}. */
class ArchitectureTest extends StandardArchitectureTest {

  ArchitectureTest() {
    super("com.krizaka.payments");
  }
}
