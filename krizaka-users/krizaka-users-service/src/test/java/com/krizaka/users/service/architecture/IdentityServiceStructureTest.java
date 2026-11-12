package com.krizaka.users.service.architecture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.krizaka.test.architecture.ArchitectureRules;
import com.krizaka.test.architecture.CodeRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The structure of {@code krizaka-users-service}: the shared {@link ArchitectureRules}, all checked
 * strictly (no debt found). A rule that fails later is fixed, or (existing debt only) frozen with
 * {@code FrozenRules}.
 */
class IdentityServiceStructureTest {

  private static final String PKG = "com.krizaka.users.service";
  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    // this module's own classes: its siblings are on the classpath, their debt is frozen in their
    // own store
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(location -> location.contains("krizaka-users-service"))
            .importPackages(PKG);
  }

  @Test
  @DisplayName("No mutable static state (krizaka-users#28, ADR-013)")
  void noMutableStaticFields() {
    assertDoesNotThrow(() -> CodeRules.noStaticMutableStateRule().check(classes));
  }

  @Test
  @DisplayName("No user content in the query of a GET (ADR-013)")
  void noContentOnGet() {
    assertDoesNotThrow(() -> CodeRules.noContentOnGetRule().check(classes));
  }

  @Test
  @DisplayName("No @Transactional method returns a stream (ADR-013)")
  void noTransactionalStreams() {
    assertDoesNotThrow(() -> CodeRules.noTransactionalOnStreamsRule().check(classes));
  }
}
