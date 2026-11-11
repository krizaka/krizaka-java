package com.krizaka.test.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.test.architecture.fixture.authz.good.ClassLevelProtectedController;
import com.krizaka.test.architecture.fixture.authz.good.ProtectedController;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * Every @RestController endpoint must carry an authorization annotation (audit-03, gate item 4):
 * this test verifies that AuthzRules catches missing annotations and accepts well-annotated ones.
 */
class AuthzRulesTest {

  private static final String ROOT = "com.krizaka.test.architecture.fixture.authz";
  private static final JavaClasses BAD = new ClassFileImporter().importPackages(ROOT + ".bad");
  private static final JavaClasses GOOD = new ClassFileImporter().importPackages(ROOT + ".good");

  @Test
  void anEndpointWithNoAuthorizationAnnotationFailsTheRule() {
    assertThatThrownBy(() -> AuthzRules.assertEndpointsCarryAuthorization(BAD))
        .hasMessageContaining("UnprotectedController")
        .hasMessageContaining("authorization annotation");
  }

  @Test
  void methodLevelPreAuthorizePassesTheRule() {
    JavaClasses classes = new ClassFileImporter().importClasses(ProtectedController.class);
    assertThatCode(() -> AuthzRules.endpointsCarryAuthorizationRule().check(classes))
        .doesNotThrowAnyException();
  }

  @Test
  void classLevelPreAuthorizePassesTheRule() {
    JavaClasses classes =
        new ClassFileImporter().importClasses(ClassLevelProtectedController.class);
    assertThatCode(() -> AuthzRules.endpointsCarryAuthorizationRule().check(classes))
        .doesNotThrowAnyException();
  }

  @Test
  void allCompliantCodePassesTheRule() {
    assertThatCode(() -> AuthzRules.assertEndpointsCarryAuthorization(GOOD))
        .doesNotThrowAnyException();
  }
}
