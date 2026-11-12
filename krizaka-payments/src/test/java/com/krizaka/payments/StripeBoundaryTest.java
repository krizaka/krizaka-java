package com.krizaka.payments;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Nobody imports the vendor outside the adapter; the port is the module's face. */
class StripeBoundaryTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.krizaka.payments");

  @Test
  void stripeIsImportedOnlyByTheAdapter() {
    noClasses()
        .that()
        .resideOutsideOfPackage("com.krizaka.payments.stripe..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.stripe..")
        .check(CLASSES);
  }

  @Test
  void thePortDoesNotDependOnItsAdapter() {
    noClasses()
        .that()
        .resideInAPackage("com.krizaka.payments")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.krizaka.payments.stripe..")
        .check(CLASSES);
  }
}
