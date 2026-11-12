package com.krizaka.test.architecture;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class TestServersBindToLoopbackRuleTest {

  private static final ArchRule RULE = CodeRules.testServersBindToLoopbackRule();
  private static final String FIXTURES = "com.krizaka.test.architecture.fixture.loopback";

  @Test
  void aServerBoundToEveryInterfaceFails() {
    var bad = new ClassFileImporter().importPackages(FIXTURES + ".bad");

    assertThatThrownBy(() -> RULE.check(bad))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("WildcardStub")
        .hasMessageContaining("InetSocketAddress")
        .hasMessageContaining("ServerSocket");
  }

  @Test
  void aServerBoundToTheLoopbackPasses() {
    var good = new ClassFileImporter().importPackages(FIXTURES + ".good");

    assertThatNoException().isThrownBy(() -> RULE.check(good));
  }
}
