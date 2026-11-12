package com.krizaka.test.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The base test refuses an import that selected no class, instead of passing every rule. */
class StandardArchitectureTestTest {

  private static final String GOOD = "com.krizaka.test.architecture.fixture.standard.good";

  @Test
  void aBasePackageWithNoClassIsRefused() {
    StandardArchitectureTest empty =
        new StandardArchitectureTest("com.krizaka.test.architecture.fixture.standard.empty") {};

    assertThatThrownBy(empty::theStandardArchitecture)
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(
            "no production class under com.krizaka.test.architecture.fixture.standard.empty");
  }

  @Test
  void anArtifactFragmentThatMatchesNothingIsRefusedAndNamed() {
    StandardArchitectureTest wrongArtifact =
        new StandardArchitectureTest(GOOD) {
          @Override
          protected String artifact() {
            return "no-such-artifact";
          }
        };

    assertThatThrownBy(wrongArtifact::theStandardArchitecture)
        .hasMessageContaining("no production class under " + GOOD + " in no-such-artifact");
  }
}
