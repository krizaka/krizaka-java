package com.krizaka.test.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.test.architecture.KrizakaArchitecture.Rule;
import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The standard passes a well-formed module, names every defect of a bad one, and freezes debt. */
class KrizakaArchitectureTest {

  private static final String GOOD = "com.krizaka.test.architecture.fixture.standard.good";
  private static final String BAD = "com.krizaka.test.architecture.fixture.standard.bad";
  private static final JavaClasses GOOD_CLASSES = new ClassFileImporter().importPackages(GOOD);
  private static final JavaClasses BAD_CLASSES = new ClassFileImporter().importPackages(BAD);

  @TempDir Path store;

  @BeforeEach
  void pointTheStoreAtATempDirectory() {
    ArchConfiguration.get().reset();
    ArchConfiguration.get().setProperty("freeze.store.default.path", store.toString());
  }

  @AfterEach
  void restoreTheConfiguration() {
    ArchConfiguration.get().reset();
  }

  @Test
  void aWellFormedModulePassesTheStandard() {
    assertThatCode(() -> KrizakaArchitecture.assertStandard(GOOD_CLASSES, GOOD))
        .doesNotThrowAnyException();
  }

  @Test
  void everyViolatedRuleIsReportedNotOnlyTheFirst() {
    assertThatThrownBy(() -> KrizakaArchitecture.assertStandard(BAD_CLASSES, BAD))
        .hasMessageContaining("[NO_RAW_HTTP_CLIENT_IN_ADAPTERS]")
        .hasMessageContaining("[FIELDS_PRIVATE]")
        .hasMessageContaining("[NO_STANDARD_STREAMS]")
        .hasMessageContaining("[EVERY_PACKAGE_NULL_MARKED]");
  }

  @Test
  void theStandardCoversEveryRuleOfTheEnum() {
    assertThat(KrizakaArchitecture.standardRules(GOOD)).containsOnlyKeys(Rule.values());
  }

  @Test
  void aModuleThatImportedNothingFailsInsteadOfPassing() {
    JavaClasses nothing = new ClassFileImporter().importPackages("com.krizaka.test.nowhere");

    assertThatThrownBy(() -> KrizakaArchitecture.assertStandard(nothing, GOOD))
        .hasMessageContaining("examined no");
  }

  @Test
  void aFrozenRuleNeedsAnIssue() {
    assertThatThrownBy(
            () ->
                KrizakaArchitecture.assertStandard(
                    BAD_CLASSES, BAD, Map.of(Rule.FIELDS_PRIVATE, "later")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("FIELDS_PRIVATE");
  }

  @Test
  void frozenDebtIsToleratedWhileEveryOtherRuleStaysStrict() {
    Map<Rule, String> everyDefect =
        Map.of(
            Rule.NO_RAW_HTTP_CLIENT_IN_ADAPTERS, "krizaka-internal#1",
            Rule.FIELDS_PRIVATE, "krizaka-internal#1",
            Rule.NO_STANDARD_STREAMS, "krizaka-internal#1");
    ArchConfiguration.get().setProperty("freeze.store.default.allowStoreCreation", "true");

    assertThatThrownBy(() -> KrizakaArchitecture.assertStandard(BAD_CLASSES, BAD, everyDefect))
        .as("the null-marking debt is not frozen, so it still fails")
        .hasMessageContaining("[EVERY_PACKAGE_NULL_MARKED]")
        .hasMessageNotContaining("[FIELDS_PRIVATE]")
        .hasMessageNotContaining("[NO_RAW_HTTP_CLIENT_IN_ADAPTERS]");

    ArchConfiguration.get().setProperty("freeze.store.default.allowStoreCreation", "false");
    Map<Rule, String> allFrozen = new java.util.EnumMap<>(Rule.class);
    everyDefect.forEach(allFrozen::put);
    allFrozen.put(Rule.EVERY_PACKAGE_NULL_MARKED, "krizaka-internal#1");
    ArchConfiguration.get().setProperty("freeze.store.default.allowStoreCreation", "true");
    assertThatCode(() -> KrizakaArchitecture.assertStandard(BAD_CLASSES, BAD, allFrozen))
        .doesNotThrowAnyException();
    ArchConfiguration.get().setProperty("freeze.store.default.allowStoreCreation", "false");
    assertThatCode(() -> KrizakaArchitecture.assertStandard(BAD_CLASSES, BAD, allFrozen))
        .doesNotThrowAnyException();
  }
}
