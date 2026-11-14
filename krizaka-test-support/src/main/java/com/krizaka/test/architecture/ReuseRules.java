package com.krizaka.test.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A module uses krizaka-java for what it already provides (krizaka-internal#3581): no local copy of
 * one of its types, no way round one of its facades. Both lists come from {@link LibraryCatalog},
 * never from the rule.
 */
public final class ReuseRules {

  private ReuseRules() {}

  /**
   * A class whose simple name is a public type of krizaka-java is a copy, unless it extends or
   * implements that type (an adapter of a port is the intended use).
   *
   * @param modulePackage the module's root package
   * @return the rule, on the classpath's {@link LibraryCatalog}
   */
  public static ArchRule noLocalCopyOfLibraryTypeRule(String modulePackage) {
    return noLocalCopyOfLibraryTypeRule(modulePackage, LibraryCatalog.fromClasspath());
  }

  /**
   * {@link #noLocalCopyOfLibraryTypeRule(String)} on an explicit catalog.
   *
   * @param modulePackage the module's root package
   * @param catalog what krizaka-java provides
   * @return the rule
   */
  public static ArchRule noLocalCopyOfLibraryTypeRule(
      String modulePackage, LibraryCatalog catalog) {
    return classes()
        .that()
        .resideInAPackage(modulePackage + "..")
        .and()
        .areTopLevelClasses()
        .should(notCopyALibraryType(catalog))
        .as("no local copy of a krizaka-java type")
        .because(
            "krizaka-java already provides it: use it, or implement it to adapt a port; a library"
                + " nobody uses is wasted")
        .allowEmptyShould(true);
  }

  /**
   * A class outside a {@code config} package does not use a third-party API that a krizaka-java
   * facade wraps.
   *
   * @param modulePackage the module's root package
   * @return the rule, on the classpath's {@link LibraryCatalog}
   */
  public static ArchRule noBypassOfFacadeRule(String modulePackage) {
    return noBypassOfFacadeRule(modulePackage, LibraryCatalog.fromClasspath());
  }

  /**
   * {@link #noBypassOfFacadeRule(String)} on an explicit catalog.
   *
   * @param modulePackage the module's root package
   * @param catalog what krizaka-java provides
   * @return the rule
   */
  public static ArchRule noBypassOfFacadeRule(String modulePackage, LibraryCatalog catalog) {
    return classes()
        .that()
        .resideInAPackage(modulePackage + "..")
        .and()
        .resideOutsideOfPackage("..config..")
        .should(notBypassAFacade(catalog))
        .as("no direct use of an API that a krizaka-java facade wraps, outside config")
        .because(
            "the facade holds the configuration, the observability and the failure contract of"
                + " the call; going round it loses them")
        .allowEmptyShould(true);
  }

  private static ArchCondition<JavaClass> notCopyALibraryType(LibraryCatalog catalog) {
    return new ArchCondition<>("not carry the name of a krizaka-java public type") {
      @Override
      public void check(JavaClass type, ConditionEvents events) {
        List<String> provided = catalog.publicTypes().get(type.getSimpleName());
        if (provided == null || provided.contains(type.getName())) {
          return;
        }
        boolean adapter =
            type.getAllRawSuperclasses().stream().anyMatch(s -> provided.contains(s.getName()))
                || type.getAllRawInterfaces().stream()
                    .anyMatch(i -> provided.contains(i.getName()));
        if (!adapter) {
          events.add(
              SimpleConditionEvent.violated(
                  type,
                  type.getName()
                      + " copies a krizaka-java type: use "
                      + String.join(" or ", provided)
                      + " (or implement it to adapt)"));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notBypassAFacade(LibraryCatalog catalog) {
    return new ArchCondition<>("not use a wrapped third-party API directly") {
      @Override
      public void check(JavaClass type, ConditionEvents events) {
        for (Map.Entry<String, String> wrapped : catalog.facades().entrySet()) {
          String[] declared = wrapped.getValue().split("\\s*,\\s*");
          String facade = declared[0];
          if (isInFacadePackage(type, declared)) {
            continue;
          }
          String used =
              type.getDirectDependenciesFromSelf().stream()
                  .map(d -> d.getTargetClass().getName())
                  .filter(name -> covers(wrapped.getKey(), name))
                  .distinct()
                  .collect(Collectors.joining(", "));
          if (!used.isEmpty()) {
            events.add(
                SimpleConditionEvent.violated(
                    type,
                    type.getName()
                        + " uses "
                        + used
                        + " directly: use the facade "
                        + facade
                        + " (a class of a config package may build it)"));
          }
        }
      }
    };
  }

  private static boolean covers(String wrappedApi, String typeName) {
    return typeName.equals(wrappedApi)
        || typeName.startsWith(wrappedApi + "$")
        || typeName.startsWith(wrappedApi + ".");
  }

  /**
   * The facade's own package, and the packages its declaration lists after it (the module that
   * wraps the API), are the code that wraps it: not a bypass.
   */
  private static boolean isInFacadePackage(JavaClass type, String[] declared) {
    String facade = declared[0];
    List<String> packages = new java.util.ArrayList<>();
    packages.add(facade.substring(0, facade.lastIndexOf('.')));
    packages.addAll(java.util.Arrays.asList(declared).subList(1, declared.length));
    return packages.stream()
        .anyMatch(
            pkg ->
                type.getPackageName().equals(pkg) || type.getPackageName().startsWith(pkg + "."));
  }

  /**
   * Asserts both rules.
   *
   * @param classes the imported production classes
   * @param modulePackage the module's root package
   */
  public static void assertReuse(JavaClasses classes, String modulePackage) {
    noLocalCopyOfLibraryTypeRule(modulePackage).check(classes);
    noBypassOfFacadeRule(modulePackage).check(classes);
  }
}
