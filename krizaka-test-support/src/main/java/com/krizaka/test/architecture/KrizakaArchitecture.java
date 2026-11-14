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
 * {@code @NullMarked}, the environment read in a {@code config} package or a configuration class
 * only, no local copy of a krizaka-java type and no bypass of its facades ({@link ReuseRules});
 * and, on the module's test classes, no test server bound to every interface ({@link
 * CodeRules#testServersBindToLoopbackRule}, never frozen). A module with layers also calls {@link
 * ArchitectureRules#assertLayers}; a module with its own rules adds them beside this call, never in
 * place of it.
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
    EVERY_PACKAGE_NULL_MARKED,
    NO_ENVIRONMENT_OUTSIDE_CONFIG,
    NO_LOCAL_COPY_OF_LIBRARY_TYPE,
    NO_BYPASS_OF_FACADE
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
    requireProductionClasses(classes, modulePackage, "");
    List<String> failures = new ArrayList<>();
    List<AssertionError> causes = new ArrayList<>();
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
                causes.add(e);
              }
            });
    try {
      CodeRules.testServersBindToLoopbackRule()
          .allowEmptyShould(true)
          .check(CodeRules.importTestClasses(modulePackage));
    } catch (AssertionError e) {
      failures.add("[TEST_SERVERS_BIND_TO_LOOPBACK] " + e.getMessage());
      causes.add(e);
    }
    if (!failures.isEmpty()) {
      // Every rule's own error travels as a suppressed cause (Sonar S1166).
      AssertionError all = new AssertionError(String.join("\n\n", failures));
      causes.forEach(all::addSuppressed);
      throw all;
    }
  }

  /**
   * Refuses an empty import: every standard rule passes on an empty set, so a wrong base package or
   * an artifact fragment that matches nothing would read as a green module.
   *
   * @param classes the imported classes
   * @param modulePackage the module's base package
   * @param artifact the location fragment that selected the classes, empty when none
   * @throws AssertionError when no class lives under {@code modulePackage}
   */
  static void requireProductionClasses(JavaClasses classes, String modulePackage, String artifact) {
    boolean any = classes.stream().anyMatch(c -> c.getPackageName().startsWith(modulePackage));
    if (!any) {
      String where = artifact.isEmpty() ? "" : (" in " + artifact);
      throw new AssertionError(
          "no production class under "
              + modulePackage
              + where
              + " — every rule of the standard passes on an empty set, so the base package or the"
              + " artifact fragment is wrong");
    }
  }

  /**
   * The rules of the standard for a module.
   *
   * @param modulePackage the module's base package
   * @return the rules, in a stable order
   */
  public static Map<Rule, ArchRule> standardRules(String modulePackage) {
    Map<Rule, ArchRule> strict = new EnumMap<>(Rule.class);
    strict.put(Rule.NO_PACKAGE_CYCLES, ArchitectureRules.noPackageCyclesRule(modulePackage));
    strict.put(
        Rule.ONE_TOP_LEVEL_CLASS_PER_FILE, CodeRules.oneTopLevelClassPerFileRule(modulePackage));
    strict.put(Rule.FIELDS_PRIVATE, CodeRules.fieldsPrivateRule());
    strict.put(Rule.NO_FIELD_INJECTION, CodeRules.noFieldInjectionRule(modulePackage));
    strict.put(Rule.NO_STANDARD_STREAMS, CodeRules.noStandardStreamsRule());
    strict.put(
        Rule.NO_RAW_HTTP_CLIENT_IN_ADAPTERS,
        CodeRules.noRawHttpClientInAdaptersRule(modulePackage));
    strict.put(
        Rule.HTTP_EXCHANGE_PACKAGE_PRIVATE, CodeRules.httpExchangeInterfacesPackagePrivateRule());
    strict.put(
        Rule.MAX_TOP_LEVEL_CLASSES_PER_PACKAGE,
        CodeRules.maxTopLevelClassesPerPackageRule(
            modulePackage, MAX_TOP_LEVEL_CLASSES_PER_PACKAGE));
    strict.put(Rule.EVERY_PACKAGE_NULL_MARKED, AuditRules.everyPackageNullMarkedRule());
    strict.put(
        Rule.NO_ENVIRONMENT_OUTSIDE_CONFIG,
        ArchitectureRules.noEnvironmentOutsideConfigRule("..config.."));
    strict.put(
        Rule.NO_LOCAL_COPY_OF_LIBRARY_TYPE, ReuseRules.noLocalCopyOfLibraryTypeRule(modulePackage));
    strict.put(Rule.NO_BYPASS_OF_FACADE, ReuseRules.noBypassOfFacadeRule(modulePackage));
    // a library of records and interfaces has no field to check: an empty subject set is a pass
    // here, the module that imported nothing at all is refused by requireProductionClasses
    Map<Rule, ArchRule> rules = new EnumMap<>(Rule.class);
    strict.forEach((rule, archRule) -> rules.put(rule, archRule.allowEmptyShould(true)));
    return rules;
  }
}
