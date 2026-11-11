package com.krizaka.billing.client.architecture;

import com.krizaka.test.architecture.CodeRules;
import com.krizaka.test.architecture.KrizakaArchitecture;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The Krizaka standard for a module, strictly: no frozen debt. A rule that fails is a design to
 * fix, never a rule to weaken (AGENTS.md, « HTTP clients (Spring Boot 4) and ArchUnit »).
 */
class ArchitectureTest {

  private static final String MODULE = "com.krizaka.billing.client";
  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes = CodeRules.importProductionClasses(MODULE);
  }

  @Test
  void theStandardArchitecture() {
    KrizakaArchitecture.assertStandard(classes, MODULE);
  }

  @Test
  void anExchangeSpeaksWireRecordsNeverTheDomain() {
    CodeRules.httpExchangeSignaturesHaveNoDomainTypeRule("com.krizaka.billing.domain..")
        .check(classes);
  }

  @Test
  void aFeatureExposesOnlyItsConfiguration() {
    CodeRules.featuresExposeOnlyTheirConfigurationRule(MODULE).check(classes);
  }
}
