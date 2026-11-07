package com.krizaka.test.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The one entry point of the architecture a Krizaka module must pass: a module's {@code
 * ArchitectureTest} calls {@link #assertStandard} with its base package and the standard grows
 * here, in every module at once, never copied into a module.
 *
 * <pre>{@code
 * class ArchitectureTest {
 *   private static final String MODULE = "com.krizaka.billing.client";
 *   private static final JavaClasses CLASSES = CodeRules.importProductionClasses(MODULE);
 *
 *   @Test
 *   void theStandardArchitecture() {
 *     KrizakaArchitecture.assertStandard(CLASSES, MODULE);
 *   }
 * }
 * }</pre>
 *
 * <p>The standard is the {@link Rule} set: no package cycle, one top-level class per file, private
 * fields, no field injection, no standard streams, no raw HTTP client outside {@code config},
 * package-private {@code @HttpExchange} interfaces, at most {@value
 * #MAX_TOP_LEVEL_CLASSES_PER_PACKAGE} top-level classes per package, every package
 * {@code @NullMarked}. A module with layers also calls {@link ArchitectureRules#assertLayers}; a
 * module with its own rules adds them beside this call, never in place of it.
 *
 * <p><b>Freezing existing debt.</b> A module that cannot pass a rule yet freezes it, with the issue
 * that will remove the debt, through {@link #assertStandard(JavaClasses, String, Map)}:
 *
 * <pre>{@code
 * KrizakaArchitecture.assertStandard(
 *     CLASSES, MODULE, Map.of(KrizakaArchitecture.Rule.EVERY_PACKAGE_NULL_MARKED, "krizaka-internal#123"));
 * }</pre>
 *
 * The violations that exist are recorded once in the module's {@code archunit_store/} (run its
 * tests with {@code -Darchunit.freeze.store.default.allowStoreCreation=true}, review, commit; see
 * {@link FrozenRules}); a new violation fails, a fixed one leaves the store. Only the debt that
 * exists the day the module adopts the standard is frozen — new code passes every rule strictly,
 * and a store is never edited by hand to get a build green.
 */
public final class KrizakaArchitecture {

  /** The most top-level classes one package may hold in the standard. */
  public static final int MAX_TOP_LEVEL_CLASSES_PER_PACKAGE = 7;

  private static final Pattern ISSUE = Pattern.compile("[\\w.-]+#\\d+");

  /** The rules of the standard; the name is what a module freezes. */
  public enum Rule {
    NO_PACKAGE_CYCLES,
    ONE_TOP_LEVEL_CLASS_PER_FILE,
    FIELDS_PRIVATE,
    NO_FIELD_INJECTION,
    NO_STANDARD_STREAMS,
    NO_RAW_HTTP_CLIENT_IN_ADAPTERS,
    HTTP_EXCHANGE_PACKAGE_PRIVATE,
    MAX_TOP_LEVEL_CLASSES_PER_PACKAGE,
    EVERY_PACKAGE_NULL_MARKED
  }

  private KrizakaArchitecture() {}

  /**
   * The standard, strictly: every rule must pass, with no frozen debt.
   *
   * @param classes the module's imported production classes ({@link
   *     CodeRules#importProductionClasses})
   * @param modulePackage the module's base package
   */
  public static void assertStandard(JavaClasses classes, String modulePackage) {
    assertStandard(classes, modulePackage, Map.of());
  }

  /**
   * The standard, with the listed rules frozen: only a violation absent from the module's store
   * fails.
   *
   * @param classes the module's imported production classes
   * @param modulePackage the module's base package
   * @param frozenDebt the rules to freeze, each with the issue that will remove its debt (for
   *     example {@code krizaka-internal#123}); a rule without an issue is refused
   * @throws IllegalArgumentException when a frozen rule names no issue
   * @throws AssertionError listing every violated rule, not only the first
   */
  public static void assertStandard(
      JavaClasses classes, String modulePackage, Map<Rule, String> frozenDebt) {
    frozenDebt.forEach(
        (rule, issue) -> {
          if (!ISSUE.matcher(issue).matches()) {
            throw new IllegalArgumentException(
                "Rule " + rule + " is frozen without an issue (expected repo#number): " + issue);
          }
        });
    Subjects.require(
        "KA-001",
        "classes under " + modulePackage,
        classes.stream().filter(c -> c.getPackageName().startsWith(modulePackage)).toList());
    List<String> failures = new ArrayList<>();
    standardRules(modulePackage)
        .forEach(
            (rule, archRule) -> {
              try {
                if (frozenDebt.containsKey(rule)) {
                  FrozenRules.check(archRule, classes);
                } else {
                  archRule.check(classes);
                }
              } catch (AssertionError e) {
                failures.add("[" + rule + "] " + e.getMessage());
              }
            });
    if (!failures.isEmpty()) {
      throw new AssertionError(String.join("\n\n", failures));
    }
  }

  /**
   * The rules of the standard for a module.
   *
   * @param modulePackage the module's base package
   * @return the rules, in a stable order
   */
  public static Map<Rule, ArchRule> standardRules(String modulePackage) {
    Map<Rule, ArchRule> rules = new EnumMap<>(Rule.class);
    // a module with no sub-package has nothing to cycle: an empty set is the right answer there
    rules.put(
        Rule.NO_PACKAGE_CYCLES,
        ArchitectureRules.noPackageCyclesRule(modulePackage).allowEmptyShould(true));
    rules.put(
        Rule.ONE_TOP_LEVEL_CLASS_PER_FILE, CodeRules.oneTopLevelClassPerFileRule(modulePackage));
    rules.put(Rule.FIELDS_PRIVATE, CodeRules.fieldsPrivateRule());
    rules.put(Rule.NO_FIELD_INJECTION, CodeRules.noFieldInjectionRule(modulePackage));
    rules.put(Rule.NO_STANDARD_STREAMS, CodeRules.noStandardStreamsRule());
    rules.put(
        Rule.NO_RAW_HTTP_CLIENT_IN_ADAPTERS,
        CodeRules.noRawHttpClientInAdaptersRule(modulePackage));
    rules.put(
        Rule.HTTP_EXCHANGE_PACKAGE_PRIVATE, CodeRules.httpExchangeInterfacesPackagePrivateRule());
    rules.put(
        Rule.MAX_TOP_LEVEL_CLASSES_PER_PACKAGE,
        CodeRules.maxTopLevelClassesPerPackageRule(
            modulePackage, MAX_TOP_LEVEL_CLASSES_PER_PACKAGE));
    rules.put(Rule.EVERY_PACKAGE_NULL_MARKED, AuditRules.everyPackageNullMarkedRule());
    return rules;
  }
}
