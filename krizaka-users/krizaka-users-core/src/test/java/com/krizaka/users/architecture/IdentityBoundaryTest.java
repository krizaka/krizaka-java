package com.krizaka.users.architecture;

import static com.krizaka.users.TestConstants.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit boundary enforcement for the krizaka-users-core module.
 *
 * <p>Validates that identity remains a pure domain hexagon with no outward dependencies on router,
 * core, or tools modules (ERR-102, §1.A).
 */
class IdentityBoundaryTest {

  private static JavaClasses identityClasses;

  @BeforeAll
  static void importClasses() {
    identityClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.krizaka.users");
  }

  @Test
  @DisplayName("[ERR-102] Users depends on no product")
  void usersDependsOnNoProduct() {
    assertDoesNotThrow(
        () -> {
          noClasses()
              .that()
              .resideInAPackage(PKG_IDENTITY)
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage("com.krizaka.sovkora..", "com.sovkora..")
              .because("a Krizaka building block depends on no product — products depend on it")
              .check(identityClasses);
        });
  }

  @Test
  @DisplayName("[ERR-106] No field injection in identity classes")
  void noFieldInjection() {
    assertDoesNotThrow(
        () -> {
          noFields()
              .that()
              .areDeclaredInClassesThat()
              .resideInAPackage(PKG_IDENTITY)
              .should()
              .beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
              .because(
                  "Field injection is prohibited — constructor-based DI is mandatory [ADR-012]")
              .check(identityClasses);
        });
  }

  @Test
  @DisplayName("[ERR-102] Service implementations must not be public")
  void serviceImplNotPublic() {
    assertDoesNotThrow(
        () -> {
          classes()
              .that()
              .resideInAPackage("com.krizaka.users.application.service..")
              .and()
              .haveSimpleNameEndingWith("Impl")
              .should()
              .notBePublic()
              .because(
                  "Service implementations are package-private — only interfaces are public [Ports & Adapters]")
              .check(identityClasses);
        });
  }
}
