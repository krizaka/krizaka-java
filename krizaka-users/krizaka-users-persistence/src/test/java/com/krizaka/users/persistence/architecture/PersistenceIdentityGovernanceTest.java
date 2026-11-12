package com.krizaka.users.persistence.architecture;

import static com.krizaka.test.architecture.CodeRules.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.krizaka.test.architecture.SourceRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Governance (naming + structural hygiene) for the krizaka-users-persistence module, reusing the
 * shared {@link com.krizaka.test.architecture.CodeRules}. Module boundaries + persistence hygiene
 * live in {@code PersistenceIdentityBoundaryTest} ([ERR-102/109]).
 *
 * <p>Like the app persistence module, inbound persistence ports are implemented as package-private
 * {@code *PersistenceProviderImpl} in {@code application.service}, each co-located with the
 * package-private final {@code *Mapper} it uses. The "service package = *Service only" rule does
 * not apply; impl package-privacy and mapper visibility are enforced below.
 */
class PersistenceIdentityGovernanceTest {

  private static final String PKG = "com.krizaka.users.persistence";
  private static JavaClasses persistenceClasses;

  @BeforeAll
  static void importClasses() {
    persistenceClasses = importProductionClasses(PKG);
  }

  @Test
  @DisplayName("[ERR-104] No redundant 'Krizaka' prefix")
  void noRedundantPrefix() {
    assertDoesNotThrow(
        () -> {
          assertNoProductPrefix(persistenceClasses, PKG, "Krizaka");
        });
  }

  @Test
  @DisplayName("[ERR-105] Service *Impl classes are package-private")
  void implsArePackagePrivate() {
    assertDoesNotThrow(
        () -> {
          assertImplClassesPackagePrivate(persistenceClasses, PKG + ".application.service");
        });
  }

  // GOV-006: [ERR-107] is not invoked here. This module has no *Mapper class — mapping was inlined
  // into its
  // single consumer, which ERR-129 sanctions — so the rule selected nothing and reported green over
  // the empty set. It runs where mappers exist.

  @Test
  @DisplayName("[ERR-112] No web controllers outside router")
  void noWebControllers() {
    assertDoesNotThrow(
        () -> {
          assertNoWebControllers(persistenceClasses, PKG);
        });
  }

  // GOV-006: [ADR-007] collection-field rule is not invoked here: no non-record, non-entity class
  // in this
  // module declares a List, Map or Set field, so it judged nothing. It runs where such fields
  // exist.

  @Test
  @DisplayName("[GOV-001] No anonymous classes in production")
  void noAnonymousClasses() {
    assertDoesNotThrow(
        () -> {
          assertNoAnonymousClasses(persistenceClasses, PKG);
        });
  }

  @Test
  @DisplayName("[ERR-109] JPA converters, entities and repositories live in their own sub-packs")
  void persistencePackageHygiene() {
    assertDoesNotThrow(
        () -> {
          assertPersistencePackageHygiene(persistenceClasses);
        });
  }

  @Test
  @DisplayName("[ERR-113] No Environment injection in production beans")
  void noEnvironmentInjection() {
    assertDoesNotThrow(
        () -> {
          SourceRules.assertNoEnvironmentInjection(Path.of("src", "main", "java"));
        });
  }
}
