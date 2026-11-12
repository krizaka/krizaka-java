package com.krizaka.security.architecture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.krizaka.test.architecture.CodeRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rules of {@code krizaka-security} that the standard ({@code ArchitectureTest}) does not
 * cover.
 */
class SecurityGovernanceTest {

  private static final String PKG = "com.krizaka.security";
  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes = CodeRules.importProductionClasses(PKG);
  }

  @Test
  @DisplayName("The HS256 shared-secret API is used by SharedSecretDecoder only (ADR-001)")
  void sharedSecretApiIsConfinedToTheLegacyDecoder() {
    assertDoesNotThrow(
        () ->
            com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that()
                .doNotHaveFullyQualifiedName("com.krizaka.security.jwt.SharedSecretDecoder")
                .should()
                .callMethodWhere(
                    com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                        com.tngtech.archunit.core.domain.properties.HasName.Predicates.name(
                            "withSecretKey")))
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.security.oauth2.jose.jws.MacAlgorithm")
                .because("session tokens are verified with the issuer's keys, not a shared secret")
                .check(classes));
  }
}
