package com.krizaka.users.persistence.architecture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.krizaka.test.architecture.ArchitectureRules;
import com.krizaka.test.architecture.CodeRules;
import com.krizaka.test.architecture.FrozenRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The structure of {@code krizaka-users-persistence}: the shared {@link ArchitectureRules}, frozen.
 * The violations that exist today are recorded in {@code archunit_store/} (committed); a new one
 * fails the build and the debt can only shrink. To fix one, fix the code and commit the smaller
 * store.
 */
class PersistenceStructureTest {

  private static final String PKG = "com.krizaka.users.persistence";
  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    // this module's own classes: its siblings are on the classpath, their debt is frozen in their
    // own store
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(location -> location.contains("krizaka-users-persistence"))
            .importPackages(PKG);
  }

  @Test
  @DisplayName("Services are named *Service")
  void servicesNamed() {
    assertDoesNotThrow(
        () -> {
          FrozenRules.check(ArchitectureRules.servicesNamedServiceRule(), classes);
        });
  }

  @Test
  @DisplayName("Repositories are named *Repository")
  void repositoriesNamed() {
    assertDoesNotThrow(
        () -> {
          FrozenRules.check(ArchitectureRules.repositoriesNamedRepositoryRule(), classes);
        });
  }

  @Test
  @DisplayName("A transaction starts on a service only")
  void transactionalOnlyOnServices() {
    assertDoesNotThrow(
        () -> {
          FrozenRules.check(
              ArchitectureRules.transactionalOnlyOnServicesRule(
                  "..application.service..", "..infrastructure.adapter.."),
              classes);
        });
  }

  @Test
  @DisplayName("No mutable static state: a static field is final (krizaka-users#28, ADR-013)")
  void noMutableStaticFields() {
    assertDoesNotThrow(() -> FrozenRules.check(CodeRules.noStaticMutableStateRule(), classes));
  }

  @Test
  @DisplayName("No @Transactional method returns a stream (ADR-013)")
  void noTransactionalStreams() {
    assertDoesNotThrow(() -> FrozenRules.check(CodeRules.noTransactionalOnStreamsRule(), classes));
  }
}
