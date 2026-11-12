package com.krizaka.test.architecture;

import com.krizaka.test.architecture.KrizakaArchitecture.Rule;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The {@code ArchitectureTest} of a module in one line: a module extends this class and gives its
 * base package, then the frozen debt it cannot pass yet as rule-name and issue pairs.
 *
 * <pre>{@code
 * class ArchitectureTest extends StandardArchitectureTest {
 *   ArchitectureTest() {
 *     super("com.krizaka.web", "EVERY_PACKAGE_NULL_MARKED", "krizaka-internal#3261");
 *   }
 * }
 * }</pre>
 *
 * A rule name is a {@link Rule}; a name that is not one fails the test at once. The test calls
 * {@link KrizakaArchitecture#assertStandard(JavaClasses, String, Map)}; the standard grows there,
 * in every module at once. A module with rules of its own adds them in another test class beside
 * this one.
 */
public abstract class StandardArchitectureTest {

  private final String modulePackage;
  private final Map<Rule, String> frozenDebt = new EnumMap<>(Rule.class);

  /**
   * A module under its base package.
   *
   * @param modulePackage the module's base package
   * @param frozenDebt pairs of a {@link Rule} name and the issue that removes its debt, e.g. {@code
   *     "FIELDS_PRIVATE", "krizaka-internal#12"}; none: the standard, strictly
   * @throws IllegalArgumentException on an odd number of values or an unknown rule name
   */
  protected StandardArchitectureTest(String modulePackage, String... frozenDebt) {
    if (frozenDebt.length % 2 != 0) {
      throw new IllegalArgumentException("frozen debt is pairs of a rule name and an issue");
    }
    this.modulePackage = modulePackage;
    for (int i = 0; i < frozenDebt.length; i += 2) {
      this.frozenDebt.put(Rule.valueOf(frozenDebt[i]), frozenDebt[i + 1]);
    }
  }

  /**
   * The fragment of the artifact's location that selects the classes to check, for a module whose
   * base package is shared with sibling artifacts on the classpath (their debt is frozen in their
   * own store). Empty: every class under the base package.
   *
   * @return the location fragment, e.g. {@code krizaka-notifications-api}
   */
  protected String artifact() {
    return "";
  }

  @Test
  void theStandardArchitecture() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(location -> location.contains(artifact()))
            .importPackages(modulePackage);
    KrizakaArchitecture.assertStandard(classes, modulePackage, frozenDebt);
  }
}
