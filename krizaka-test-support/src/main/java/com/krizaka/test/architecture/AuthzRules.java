package com.krizaka.test.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * ArchUnit rules for HTTP authorization (audit-03, gate item 4): every endpoint of a
 * {@code @RestController} must carry {@code @PreAuthorize}, {@code @Secured} or
 * {@code @RolesAllowed} at the method or class level, so that an unprotected endpoint fails at
 * build time, not in production.
 *
 * <pre>{@code
 * @Test
 * void endpointsCarryAuthorization() {
 *   AuthzRules.assertEndpointsCarryAuthorization(CLASSES);
 * }
 * }</pre>
 */
public final class AuthzRules {

  private static final String PRE_AUTHORIZE =
      "org.springframework.security.access.prepost.PreAuthorize";
  private static final String SECURED = "org.springframework.security.access.annotation.Secured";
  private static final String ROLES_ALLOWED = "jakarta.annotation.security.RolesAllowed";

  private static final String GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping";
  private static final String POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping";
  private static final String PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping";
  private static final String DELETE_MAPPING =
      "org.springframework.web.bind.annotation.DeleteMapping";
  private static final String PATCH_MAPPING =
      "org.springframework.web.bind.annotation.PatchMapping";
  private static final String REQUEST_MAPPING =
      "org.springframework.web.bind.annotation.RequestMapping";
  private static final String REST_CONTROLLER =
      "org.springframework.web.bind.annotation.RestController";

  private AuthzRules() {}

  /**
   * Every method of a {@code @RestController} that carries a request mapping annotation must also
   * carry {@code @PreAuthorize}, {@code @Secured} or {@code @RolesAllowed} — either on the method
   * itself or on its declaring class. Use {@link FrozenRules#check} to admit existing debt while
   * keeping new code clean.
   *
   * @return the rule
   */
  public static ArchRule endpointsCarryAuthorizationRule() {
    return methods()
        .that(haveEndpointAnnotation())
        .and()
        .areDeclaredInClassesThat()
        .areAnnotatedWith(REST_CONTROLLER)
        .should(carryAuthorizationAnnotation())
        .as("every @RestController endpoint carries an authorization annotation")
        .because(
            "an endpoint without @PreAuthorize, @Secured or @RolesAllowed is open to every caller"
                + " (audit-03, gate item 4)");
  }

  /**
   * Asserts {@link #endpointsCarryAuthorizationRule()}.
   *
   * @param classes the imported production classes
   */
  public static void assertEndpointsCarryAuthorization(JavaClasses classes) {
    endpointsCarryAuthorizationRule().check(classes);
  }

  private static DescribedPredicate<JavaMethod> haveEndpointAnnotation() {
    return DescribedPredicate.describe(
        "are annotated with a Spring MVC request mapping",
        method ->
            method.isAnnotatedWith(GET_MAPPING)
                || method.isAnnotatedWith(POST_MAPPING)
                || method.isAnnotatedWith(PUT_MAPPING)
                || method.isAnnotatedWith(DELETE_MAPPING)
                || method.isAnnotatedWith(PATCH_MAPPING)
                || method.isAnnotatedWith(REQUEST_MAPPING));
  }

  private static ArchCondition<JavaMethod> carryAuthorizationAnnotation() {
    return new ArchCondition<>(
        "carry @PreAuthorize, @Secured or @RolesAllowed at method or class level") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        if (!hasAuthzAnnotation(method) && !hasAuthzAnnotation(method.getOwner())) {
          events.add(
              SimpleConditionEvent.violated(
                  method,
                  method.getFullName()
                      + " has no authorization annotation"
                      + " (@PreAuthorize, @Secured or @RolesAllowed)"));
        }
      }
    };
  }

  private static boolean hasAuthzAnnotation(CanBeAnnotated annotatable) {
    return annotatable.isAnnotatedWith(PRE_AUTHORIZE)
        || annotatable.isAnnotatedWith(SECURED)
        || annotatable.isAnnotatedWith(ROLES_ALLOWED);
  }
}
