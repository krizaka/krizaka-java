package com.krizaka.test.architecture;

import static org.junit.jupiter.api.Assertions.fail;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaWildcardType;
import com.tngtech.archunit.core.domain.ReferencedClassObject;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * [CFG-001] Every type Spring builds for you has a constructor Spring can choose.
 *
 * <p>Two silent failures share one cause — more than one constructor and no marker saying which:
 *
 * <ul>
 *   <li><b>The configuration binder.</b> A {@code @ConfigurationProperties} record (or any type
 *       reachable from one, or a class literal handed to {@code Binder.bind}) with a second,
 *       convenience constructor and no {@code @ConstructorBinding}: the binder builds no instance,
 *       every value under the prefix is dropped, and the field default wins. A kill-switch that
 *       cannot be switched on looks exactly like one that is off.
 *   <li><b>The container.</b> A {@code @Component}, {@code @Service}, {@code @Repository},
 *       {@code @Controller} or {@code @Configuration} with two constructors, none
 *       {@code @Autowired} and none no-arg: the service does not start, and no unit test that
 *       builds the bean by hand will ever say so.
 * </ul>
 *
 * <p>Read from bytecode with ArchUnit — a constructor set, an annotation on a constructor and a
 * class literal beside a {@code bind} call are structure, not text. Pass the root packages of the
 * code you own: types outside them are judged by their own authors.
 *
 * <pre>{@code
 * ConfigBindingRules.assertConfigurationBindsUnambiguously("com.example");
 * ConfigBindingRules.assertInjectableComponentsHaveOneConstructor("com.example");
 * }</pre>
 */
public final class ConfigBindingRules {

  private static final String CONFIGURATION_PROPERTIES =
      "org.springframework.boot.context.properties.ConfigurationProperties";
  private static final String CONSTRUCTOR_BINDING =
      "org.springframework.boot.context.properties.bind.ConstructorBinding";
  private static final String BINDER = "org.springframework.boot.context.properties.bind.Binder";
  private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";

  /** The stereotypes whose classes the container instantiates itself. */
  private static final java.util.Set<String> SPRING_BEAN_ANNOTATIONS =
      java.util.Set.of(
          "org.springframework.stereotype.Component",
          "org.springframework.stereotype.Service",
          "org.springframework.stereotype.Repository",
          "org.springframework.stereotype.Controller",
          "org.springframework.web.bind.annotation.RestController",
          "org.springframework.context.annotation.Configuration");

  private ConfigBindingRules() {}

  /**
   * Asserts the binder half of [CFG-001] over every production class of {@code ownPackages} on the
   * classpath.
   *
   * <p>The classpath, not the module: binding happens in a running service, over the configuration
   * types of every library it pulls in — a library has no suite of its own, and its properties are
   * bound in the services that depend on it.
   */
  public static void assertConfigurationBindsUnambiguously(String... ownPackages) {
    assertConfigurationBindsUnambiguously(
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ownPackages),
        ownPackages);
  }

  /**
   * Asserts [CFG-001] over the given classes: no type reachable from a configuration-binding root
   * has more than one constructor without one of them annotated {@code @ConstructorBinding}.
   *
   * <p>Its population is the set of types the binder builds — the roots and everything reachable
   * from them — and an empty one fails the rule (GOV-006).
   *
   * @param classes the classes to search for roots; components are followed wherever they resolve
   * @param ownPackages the root packages whose types are judged
   */
  public static void assertConfigurationBindsUnambiguously(
      JavaClasses classes, String... ownPackages) {
    Map<JavaClass, String> roots = roots(classes, ownPackages);
    Map<JavaClass, String> bound =
        Subjects.require(
            "CFG-001",
            "types the configuration binder builds (no @ConfigurationProperties type and no"
                + " Binder.bind target was found)",
            reachable(roots, ownPackages));

    List<String> violations = new ArrayList<>();
    for (Map.Entry<JavaClass, String> entry : bound.entrySet()) {
      JavaClass type = entry.getKey();
      List<JavaConstructor> constructors = new ArrayList<>(type.getConstructors());
      boolean ambiguous =
          constructors.size() > 1
              && constructors.stream().noneMatch(c -> c.isAnnotatedWith(CONSTRUCTOR_BINDING));
      // A class with a no-argument constructor and setters is bound through its setters, so its
      // constructor set does not decide whether binding happens.
      if (ambiguous && !isJavaBean(type)) {
        violations.add(
            type.getName()
                + " is built by the configuration binder ("
                + entry.getValue()
                + ") and has "
                + constructors.size()
                + " constructors, none annotated @ConstructorBinding. The binder cannot choose,"
                + " builds no instance, and every value under its prefix is dropped for the field"
                + " default. Keep one constructor, or annotate the canonical one @ConstructorBinding.");
      }
    }
    if (!violations.isEmpty()) {
      fail(
          "[CFG-001] configuration types the binder cannot construct:\n  "
              + String.join("\n  ", violations));
    }
  }

  /** Every {@code @ConfigurationProperties} type, and every class literal passed to a bind call. */
  private static Map<JavaClass, String> roots(JavaClasses classes, String... ownPackages) {
    Map<JavaClass, String> roots = new LinkedHashMap<>();
    for (JavaClass type : classes) {
      if (type.isAnnotatedWith(CONFIGURATION_PROPERTIES)) {
        roots.putIfAbsent(type, "@ConfigurationProperties on " + type.getSimpleName());
      }
      for (JavaCodeUnit unit : type.getCodeUnits()) {
        boolean binds =
            unit.getMethodCallsFromSelf().stream()
                .anyMatch(
                    call ->
                        BINDER.equals(call.getTargetOwner().getName())
                            && "bind".equals(call.getName()));
        if (!binds) {
          continue;
        }
        unit.getReferencedClassObjects().stream()
            .map(ReferencedClassObject::getRawType)
            .filter(target -> isOwnCode(target, ownPackages))
            .forEach(
                target ->
                    roots.putIfAbsent(
                        target,
                        "Binder.bind in "
                            + type.getSimpleName()
                            + "."
                            + unit.getName()
                            + "() binds "
                            + target.getSimpleName()));
      }
    }
    return roots;
  }

  /** The roots and every own type reachable through their fields' types and type arguments. */
  private static Map<JavaClass, String> reachable(
      Map<JavaClass, String> roots, String... ownPackages) {
    Map<JavaClass, String> seen = new LinkedHashMap<>();
    Deque<JavaClass> queue = new ArrayDeque<>();
    roots.forEach(
        (root, why) -> {
          if (seen.putIfAbsent(root, why) == null) {
            queue.add(root);
          }
        });
    while (!queue.isEmpty()) {
      enqueueFieldTypes(queue.poll(), seen, queue, ownPackages);
    }
    return seen;
  }

  /**
   * Marks every own, buildable type held by a field of {@code owner} as reached through that field.
   */
  private static void enqueueFieldTypes(
      JavaClass owner, Map<JavaClass, String> seen, Deque<JavaClass> queue, String... ownPackages) {
    for (JavaField field : owner.getFields()) {
      if (field.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.STATIC)) {
        continue;
      }
      for (JavaClass component : rawTypesIn(field.getType())) {
        // An enum is converted from its name, never constructed; an interface cannot be built.
        if (isOwnCode(component, ownPackages)
            && !component.isEnum()
            && !component.isInterface()
            && seen.putIfAbsent(component, seen.get(owner) + " → " + field.getName()) == null) {
          queue.add(component);
        }
      }
    }
  }

  private static List<JavaClass> rawTypesIn(JavaType type) {
    List<JavaClass> raw = new ArrayList<>();
    Deque<JavaType> pending = new ArrayDeque<>();
    pending.add(type);
    while (!pending.isEmpty()) {
      JavaType current = pending.poll();
      raw.add(current.toErasure());
      if (current instanceof JavaParameterizedType parameterized) {
        pending.addAll(parameterized.getActualTypeArguments());
      } else if (current instanceof JavaWildcardType wildcard) {
        pending.addAll(wildcard.getUpperBounds());
      }
      JavaClass erasure = current.toErasure();
      if (erasure.isArray()) {
        pending.add(erasure.getComponentType());
      }
    }
    return raw;
  }

  private static boolean isJavaBean(JavaClass type) {
    boolean noArgs =
        type.getConstructors().stream().anyMatch(c -> c.getRawParameterTypes().isEmpty());
    boolean setters =
        type.getMethods().stream()
            .map(HasName::getName)
            .anyMatch(name -> name.startsWith("set") && name.length() > 3);
    return !type.isRecord() && noArgs && setters;
  }

  private static boolean isOwnCode(JavaClass type, String... ownPackages) {
    for (String own : ownPackages) {
      if (type.getPackageName().startsWith(own)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Asserts every Spring-instantiated component has a constructor set the container can choose
   * from.
   *
   * <p>The container's own rule, applied as written — and "as written" is load-bearing, because the
   * first version of this rule paraphrased it and cleared the bean that had just failed to start.
   * {@code AutowiredAnnotationBeanPostProcessor.determineCandidateConstructors} begins at {@code
   * beanClass.getDeclaredConstructors()} and takes its one-candidate shortcut only when {@code
   * rawCandidates.length == 1}. <b>{@code getDeclaredConstructors()} includes private ones.</b> So
   * a {@code private} constructor behind a static factory does <i>not</i> hide a second injection
   * point from the container: it is a second raw candidate, the shortcut does not apply, no
   * {@code @Autowired} marks a choice, and instantiation falls back to a no-arg constructor that
   * does not exist. That is exactly how a bean gets "fixed" and stays broken — the rule judged
   * non-private constructors while the container counted all of them, so the rule and the thing it
   * protects were reading two different populations.
   *
   * <p>A <b>candidate</b> is therefore any declared constructor, whatever its modifier. A test seam
   * belongs behind a static factory <i>over the same single constructor</i>, or behind an explicit
   * {@code @Autowired} on the one the container must use.
   *
   * <p>Scoped to what the container builds: {@code @Component}, {@code @Service},
   * {@code @Repository}, {@code @Controller}, {@code @RestController} and {@code @Configuration}. A
   * record or a value object with four constructors is nobody's problem; a bean with two is a
   * service that does not start.
   *
   * @param ownPackages the root packages of the code to judge
   */
  public static void assertInjectableComponentsHaveOneConstructor(String... ownPackages) {
    assertInjectableComponentsHaveOneConstructor(
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ownPackages),
        ownPackages);
  }

  /**
   * The rule, over classes the caller imported.
   *
   * @param classes production classes to judge
   * @param ownPackages the root packages whose types are judged
   */
  public static void assertInjectableComponentsHaveOneConstructor(
      JavaClasses classes, String... ownPackages) {
    List<String> violations = new ArrayList<>();
    List<String> judged = new ArrayList<>();
    for (JavaClass type : classes) {
      if (isJudgedBean(type, ownPackages)) {
        judged.add(type.getSimpleName());
        unchoosableConstructors(type).ifPresent(violations::add);
      }
    }
    Subjects.require("CFG-001", "Spring beans on this module's classpath", judged);
    if (!violations.isEmpty()) {
      fail(
          "[CFG-001] a bean the container cannot instantiate is a service that does not start,"
              + " and no test that builds it by hand will ever say so:\n  "
              + String.join("\n  ", violations));
    }
  }

  private static boolean isJudgedBean(JavaClass type, String[] ownPackages) {
    return isOwnCode(type, ownPackages)
        && !type.isInterface()
        && !type.isEnum()
        && isSpringBean(type);
  }

  /**
   * The violation of a bean whose container cannot choose a constructor. Every DECLARED
   * constructor, private included — see the class rule's javadoc. Filtering private ones out is
   * what let a two-constructor bean pass while the container refused it.
   */
  private static Optional<String> unchoosableConstructors(JavaClass type) {
    List<JavaConstructor> candidates = List.copyOf(type.getConstructors());
    if (candidates.size() < 2) {
      return Optional.empty();
    }
    boolean chosen =
        candidates.stream()
            .anyMatch(
                constructor ->
                    constructor.getAnnotations().stream()
                        .anyMatch(a -> AUTOWIRED.equals(a.getRawType().getName())));
    boolean noArg = candidates.stream().anyMatch(c -> c.getRawParameterTypes().isEmpty());
    if (chosen || noArg) {
      return Optional.empty();
    }
    return Optional.of(
        type.getSimpleName()
            + " is a Spring bean with "
            + candidates.size()
            + " constructors the container cannot choose from"
            + " (none is @Autowired and none is no-arg)");
  }

  /** Whether the container instantiates this type. */
  private static boolean isSpringBean(JavaClass type) {
    return type.getAnnotations().stream()
        .map(a -> a.getRawType().getName())
        .anyMatch(SPRING_BEAN_ANNOTATIONS::contains);
  }
}
