package com.krizaka.test.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.domain.Source;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Code rules every Krizaka repository holds, as ArchUnit checks a governance test calls.
 *
 * <pre>{@code
 * class UsersGovernanceTest {
 *   private static final JavaClasses CLASSES =
 *       new ClassFileImporter()
 *           .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
 *           .importPackages("com.krizaka.users");
 *
 *   @Test
 *   void oneClassPerFile() {
 *     CodeRules.assertOneTopLevelClassPerFile(CLASSES, "com.krizaka.users");
 *   }
 * }
 * }</pre>
 *
 * <p>Each rule names the reason it exists in its failure message, so a violation explains itself.
 */
public final class CodeRules {

  private static final String MAPPER_SUFFIX = "Mapper";
  private static final String JPA_ENTITY = "jakarta.persistence.Entity";

  private CodeRules() {}

  /**
   * The production classes of a package, tests excluded — the input every rule below takes.
   *
   * @param basePackage the root package to import
   * @return the imported classes
   */
  public static JavaClasses importProductionClasses(String basePackage) {
    return new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages(basePackage);
  }

  /**
   * Every top-level class lives in a file named after it.
   *
   * <p>A second top-level class in a file is invisible to anyone looking for it by name, and is
   * where helpers that should have been a type of their own accumulate.
   *
   * @param classes the imported production classes
   * @param modulePackage the root package to check
   */
  public static void assertOneTopLevelClassPerFile(JavaClasses classes, String modulePackage) {
    oneTopLevelClassPerFileRule(modulePackage).check(classes);
  }

  /**
   * {@link #assertOneTopLevelClassPerFile} as a rule, so it can be frozen.
   *
   * @param modulePackage the root package to check
   * @return the rule
   */
  public static ArchRule oneTopLevelClassPerFileRule(String modulePackage) {
    return classes()
        .that()
        .resideInAPackage(modulePackage + "..")
        .and()
        .doNotHaveModifier(JavaModifier.SYNTHETIC)
        .should(
            new ArchCondition<JavaClass>("reside in a dedicated file matching their class name") {
              @Override
              public void check(JavaClass javaClass, ConditionEvents events) {
                if (javaClass.isAnonymousClass() || javaClass.isMemberClass()) {
                  return;
                }
                String sourceFileName =
                    javaClass.getSource().flatMap(Source::getFileName).orElse(null);
                if (sourceFileName != null && !"Unknown Source".equals(sourceFileName)) {
                  String expectedFileName = javaClass.getSimpleName() + ".java";
                  if (!sourceFileName.equals(expectedFileName)) {
                    events.add(
                        SimpleConditionEvent.violated(
                            javaClass,
                            String.format(
                                "Class '%s' is bundled inside '%s'.",
                                javaClass.getFullName(), sourceFileName)));
                  }
                }
              }
            })
        .because("every top-level class lives in its own file, named after it");
  }

  /**
   * No class writes to {@code System.out} or {@code System.err}.
   *
   * @param classes the imported production classes
   */
  public static void assertNoStandardStreams(JavaClasses classes) {
    noStandardStreamsRule().check(classes);
  }

  /**
   * {@link #assertNoStandardStreams} as a rule, so it can be frozen.
   *
   * @return the rule
   */
  public static ArchRule noStandardStreamsRule() {
    return GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.because(
        "output goes through an SLF4J logger, which levels, routes and redacts it");
  }

  /**
   * No field is injected with {@code @Autowired}.
   *
   * <p>Constructor injection makes a dependency visible, mandatory and final, and lets a unit test
   * build the class without a container.
   *
   * @param classes the imported production classes
   * @param modulePackage the root package to check
   */
  public static void assertNoFieldInjection(JavaClasses classes, String modulePackage) {
    noFieldInjectionRule(modulePackage).check(classes);
  }

  /**
   * {@link #assertNoFieldInjection} as a rule, so it can be frozen.
   *
   * @param modulePackage the root package to check
   * @return the rule
   */
  public static ArchRule noFieldInjectionRule(String modulePackage) {
    return noFields()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage(modulePackage + "..")
        .should()
        .beAnnotatedWith(Autowired.class)
        .because("dependencies are injected through the constructor");
  }

  /**
   * Every top-level class of an application-service package is a {@code *Service}.
   *
   * <p>Services are named after the capability they serve; mappers, properties and exceptions live
   * with the code they belong to, and pattern names ({@code *Manager}, {@code *Handler}) say how,
   * not what.
   *
   * @param classes the imported production classes
   * @param servicePackage the application-service package
   */
  public static void assertServicePackageOnlyServices(JavaClasses classes, String servicePackage) {
    classes()
        .that()
        .resideInAPackage(servicePackage + "..")
        .and()
        .areTopLevelClasses()
        .should()
        .haveSimpleNameEndingWith("Service")
        .because("an application-service package holds capability-named *Service classes only")
        .check(classes);
  }

  /**
   * The domain holds no transport DTOs ({@code *Request}, {@code *Response}).
   *
   * @param classes the imported production classes
   * @param domainPackage the domain package
   */
  public static void assertDomainHasNoTransportDtos(JavaClasses classes, String domainPackage) {
    noClasses()
        .that()
        .resideInAPackage(domainPackage + "..")
        .should()
        .haveSimpleNameEndingWith("Request")
        .orShould()
        .haveSimpleNameEndingWith("Response")
        .because("transport DTOs belong to the adapters that speak the transport, never the domain")
        .check(classes);
  }

  /**
   * No class name repeats the product or organisation name ({@code Engine}, not {@code
   * KrizakaEngine}): the package already says whose it is.
   *
   * @param classes the imported production classes
   * @param modulePackage the root package to check
   * @param prefix the forbidden prefix, e.g. {@code "Krizaka"}
   */
  public static void assertNoProductPrefix(
      JavaClasses classes, String modulePackage, String prefix) {
    classes()
        .that()
        .resideInAPackage(modulePackage + "..")
        .should()
        .haveSimpleNameNotStartingWith(prefix)
        .because("the package already names the product; " + prefix + "* class names repeat it")
        .check(classes);
  }

  /**
   * No anonymous class in production code, except enum constant bodies and Jackson-style {@code
   * TypeReference} captures. Compiler-generated synthetic classes are ignored.
   *
   * @param classes the imported production classes
   * @param modulePackage the root package to check
   */
  public static void assertNoAnonymousClasses(JavaClasses classes, String modulePackage) {
    classes()
        .that()
        .resideInAPackage(modulePackage + "..")
        .and()
        .doNotHaveModifier(JavaModifier.SYNTHETIC)
        .should()
        .notBeAnonymousClasses()
        .orShould(beEnumConstantOrTypeReference())
        .because("an anonymous class is a type nobody can name, test or find")
        .check(classes);
  }

  /**
   * Instance fields of concrete, non-record classes are private.
   *
   * @param classes the imported production classes
   */
  public static void assertFieldsPrivate(JavaClasses classes) {
    fieldsPrivateRule().check(classes);
  }

  /**
   * {@link #assertFieldsPrivate} as a rule, so it can be frozen.
   *
   * @return the rule
   */
  public static ArchRule fieldsPrivateRule() {
    return fields()
        .that()
        .areDeclaredInClassesThat()
        .areNotRecords()
        .and()
        .areDeclaredInClassesThat()
        .areNotEnums()
        .and()
        .areDeclaredInClassesThat()
        .areNotInterfaces()
        .and()
        .areDeclaredInClassesThat()
        .haveSimpleNameNotContaining("Abstract")
        .and()
        .areNotStatic()
        .should(
            new ArchCondition<JavaField>("be private") {
              @Override
              public void check(JavaField field, ConditionEvents events) {
                if (!field.getModifiers().contains(JavaModifier.PRIVATE)) {
                  events.add(
                      SimpleConditionEvent.violated(
                          field,
                          String.format(
                              "Field <%s> in <%s> must be private",
                              field.getName(), field.getOwner().getName())));
                }
              }
            })
        .because("state is reached through behaviour, never through a field");
  }

  /**
   * Collection fields ({@code List}, {@code Map}, {@code Set}) of non-record classes are {@code
   * private final}. JPA entities are exempt: the ORM replaces their collections on load.
   *
   * @param classes the imported production classes
   */
  public static void assertCollectionFieldsPrivateFinal(JavaClasses classes) {
    fields()
        .that()
        .haveRawType(List.class)
        .or()
        .haveRawType(Map.class)
        .or()
        .haveRawType(Set.class)
        .and()
        .areDeclaredInClassesThat()
        .areNotRecords()
        .and()
        .areDeclaredInClassesThat()
        .areNotEnums()
        .and()
        .areDeclaredInClassesThat()
        .areNotInterfaces()
        .and()
        .areDeclaredInClassesThat()
        .haveSimpleNameNotContaining("Abstract")
        .and()
        .areDeclaredInClassesThat()
        .areNotAnnotatedWith(JPA_ENTITY)
        .should(bePrivateAndFinal())
        .because("a collection field that can be reassigned or shared is mutable state in disguise")
        .check(classes);
  }

  /**
   * JPA converters, entities and Spring Data repositories live in their own sub-packages of {@code
   * infrastructure.adapter.persistence}.
   *
   * @param classes the imported production classes
   */
  public static void assertPersistencePackageHygiene(JavaClasses classes) {
    classes()
        .that()
        .implement("jakarta.persistence.AttributeConverter")
        .should()
        .resideInAPackage("..infrastructure.adapter.persistence.converter..")
        .because("JPA AttributeConverters live in the .converter package")
        .check(classes);
    classes()
        .that()
        .areAnnotatedWith(JPA_ENTITY)
        .should()
        .resideInAPackage("..infrastructure.adapter.persistence.entity..")
        .because("JPA entities live in the .entity package")
        .check(classes);
    classes()
        .that()
        .areAssignableTo("org.springframework.data.repository.Repository")
        .should()
        .resideInAPackage("..infrastructure.adapter.persistence.repository..")
        .because("Spring Data repositories live in the .repository package")
        .check(classes);
  }

  /**
   * The {@code infrastructure.adapter.persistence} package itself (not its sub-packages) holds only
   * {@code *Adapter} and {@code *Mapper} classes: one package, one kind of component.
   *
   * @param classes the imported production classes
   * @param modulePackage the root package to check
   */
  public static void assertPersistenceAdapterPackageKind(
      JavaClasses classes, String modulePackage) {
    classes()
        .that()
        .resideInAPackage(modulePackage + ".infrastructure.adapter.persistence")
        .and()
        .areTopLevelClasses()
        .should()
        .haveSimpleNameEndingWith("Adapter")
        .orShould()
        .haveSimpleNameEndingWith(MAPPER_SUFFIX)
        .because("adapter/persistence holds outbound-port adapters and their mappers only")
        .check(classes);
  }

  /**
   * A module declares no web controller — for libraries and modules that must not serve HTTP.
   *
   * @param classes the imported production classes
   * @param modulePackage the root package to check
   */
  public static void assertNoWebControllers(JavaClasses classes, String modulePackage) {
    classes()
        .that()
        .resideInAPackage(modulePackage + "..")
        .should()
        .notBeAnnotatedWith("org.springframework.web.bind.annotation.RestController")
        .andShould()
        .notBeAnnotatedWith("org.springframework.stereotype.Controller")
        .because("this module serves no HTTP; controllers belong to the service that does")
        .check(classes);
  }

  /**
   * {@code *Mapper} classes are final.
   *
   * @param classes the imported production classes
   * @param modulePackage the root package to check
   */
  public static void assertMappersFinal(JavaClasses classes, String modulePackage) {
    classes()
        .that()
        .resideInAPackage(modulePackage + "..")
        .and()
        .haveSimpleNameEndingWith(MAPPER_SUFFIX)
        .should()
        .haveModifier(JavaModifier.FINAL)
        .because("a mapper is a final, stateless set of static functions")
        .check(classes);
  }

  /**
   * {@code *Mapper} classes are not public: they are a detail of the adapter they serve.
   *
   * @param classes the imported production classes
   * @param modulePackage the root package to check
   */
  public static void assertMappersPackagePrivate(JavaClasses classes, String modulePackage) {
    classes()
        .that()
        .resideInAPackage(modulePackage + "..")
        .and()
        .haveSimpleNameEndingWith(MAPPER_SUFFIX)
        .should()
        .notBePublic()
        .because("a mapper is an internal detail of the code it maps for")
        .check(classes);
  }

  /**
   * {@code *Impl} classes are not public: other modules reach them through their interface.
   *
   * @param classes the imported production classes
   * @param servicePackage the package holding the implementations
   */
  public static void assertImplClassesPackagePrivate(JavaClasses classes, String servicePackage) {
    classes()
        .that()
        .resideInAPackage(servicePackage + "..")
        .and()
        .haveSimpleNameEndingWith("Impl")
        .should()
        .notBePublic()
        .because("an implementation is reached through its interface, never by name")
        .check(classes);
  }

  /**
   * The domain layer depends on no framework: no Spring, no JPA, no Jackson.
   *
   * @param classes the imported production classes
   * @param modulePackage the root package whose {@code .domain} is checked
   */
  public static void assertDomainPurity(JavaClasses classes, String modulePackage) {
    noClasses()
        .that()
        .resideInAPackage(modulePackage + ".domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta.persistence..",
            "com.fasterxml.jackson..",
            "tools.jackson..")
        .because("the domain is plain records and classes; frameworks live in the adapters")
        .check(classes);
  }

  // ---- Security lessons (ADR-013, krizaka/.github#333) -------------------------------------

  private static final String REQUEST_PARAM =
      "org.springframework.web.bind.annotation.RequestParam";
  private static final String GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping";
  private static final String TRANSACTIONAL =
      "org.springframework.transaction.annotation.Transactional";
  private static final String EVENT_STREAM = "text/event-stream";
  private static final Set<String> CONTENT_PARAMS =
      Set.of("prompt", "message", "content", "text", "body");
  private static final Set<String> STREAM_TYPES =
      Set.of(
          "reactor.core.publisher.Flux",
          "reactor.core.publisher.Mono",
          "java.util.stream.Stream",
          "org.springframework.web.servlet.mvc.method.annotation.SseEmitter",
          "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter");

  /**
   * Rule form of {@link #assertNoStaticMutableState}, for {@link FrozenRules#check}.
   *
   * @return the rule
   */
  public static ArchRule noStaticMutableStateRule() {
    return classes()
        .should(notHaveStaticMutableFields())
        .as("no non-final static field in production code")
        .because(
            "static mutable state is shared by every request and every tenant — the crypto state "
                + "held that way in krizaka-users#57 leaked across callers (krizaka-java#102, ADR-013)");
  }

  /**
   * No non-final static field in production code (krizaka-users#57).
   *
   * @param classes the imported production classes
   */
  public static void assertNoStaticMutableState(JavaClasses classes) {
    noStaticMutableStateRule().check(classes);
  }

  /**
   * Rule form of {@link #assertNoGetenvOutsideConfig}, for {@link FrozenRules#check}.
   *
   * @param configPackage the package pattern of the configuration layer, e.g. {@code "..config.."}
   * @return the rule
   */
  public static ArchRule noGetenvOutsideConfigRule(String configPackage) {
    return classes()
        .that()
        .resideOutsideOfPackage(configPackage)
        .should(notCallSystemGetenv())
        .as("no System.getenv outside " + configPackage)
        .because(
            "an environment fallback hidden in code bypasses the validated, documented settings "
                + "(krizaka-users#57, krizaka-java#102, ADR-013)");
  }

  /**
   * No {@code System.getenv} call outside the configuration layer (krizaka-users#57).
   *
   * @param classes the imported production classes
   * @param configPackage the package pattern of the configuration layer, e.g. {@code "..config.."}
   */
  public static void assertNoGetenvOutsideConfig(JavaClasses classes, String configPackage) {
    noGetenvOutsideConfigRule(configPackage).check(classes);
  }

  /**
   * Rule form of {@link #assertNoContentOnGet}, for {@link FrozenRules#check}.
   *
   * @return the rule
   */
  public static ArchRule noContentOnGetRule() {
    return classes()
        .should(notCarryUserContentInTheQuery())
        .as("no user content in the query of a GET endpoint")
        .because(
            "a GET query lands in access logs and proxies: a prompt must travel in a POST body "
                + "(sovkora-conversation-service#19 and #66, krizaka-java#103, ADR-013)");
  }

  /**
   * No {@code @GetMapping} method takes user content in a {@code @RequestParam}: a parameter named
   * {@code prompt}, {@code message}, {@code content}, {@code text} or {@code body}, one of a
   * request-DTO type, or any parameter at all on a method producing {@code text/event-stream}.
   *
   * @param classes the imported production classes
   */
  public static void assertNoContentOnGet(JavaClasses classes) {
    noContentOnGetRule().check(classes);
  }

  /**
   * Rule form of {@link #assertNoTransactionalOnStreams}, for {@link FrozenRules#check}.
   *
   * @return the rule
   */
  public static ArchRule noTransactionalOnStreamsRule() {
    return classes()
        .should(notHaveTransactionalStreams())
        .as(
            "no @Transactional on a method returning Flux, Stream, SseEmitter or ResponseBodyEmitter")
        .because(
            "the transaction, and its pooled connection, stays open while the stream is consumed: "
                + "the pool leaked (sovkora-conversation-service#10, krizaka-java#102, ADR-013)");
  }

  /**
   * No {@code @Transactional} method returns a {@code Flux}, {@code Stream}, {@code SseEmitter} or
   * {@code ResponseBodyEmitter}.
   *
   * @param classes the imported production classes
   */
  public static void assertNoTransactionalOnStreams(JavaClasses classes) {
    noTransactionalOnStreamsRule().check(classes);
  }

  /**
   * Rule form of {@link #assertNoSelfInvocationOfTransactional}, for {@link FrozenRules#check}.
   *
   * @return the rule
   */
  public static ArchRule noSelfInvocationOfTransactionalRule() {
    return classes()
        .should(notCallTheirOwnTransactionalMethods())
        .as("no class calls one of its own @Transactional methods")
        .because(
            "a call through this skips Spring's proxy, so the annotation is ignored and the work "
                + "runs outside its transaction: row locks end at once (Sonar java:S2229 and "
                + "java:S6809, krizaka-internal#2153). Annotate the entry point callers use and "
                + "share a private method, or move the work to another bean");
  }

  /**
   * No class calls a {@code @Transactional} method of its own: the call goes through {@code this},
   * not through the proxy, so the transaction is never opened.
   *
   * @param classes the imported production classes
   */
  public static void assertNoSelfInvocationOfTransactional(JavaClasses classes) {
    noSelfInvocationOfTransactionalRule().check(classes);
  }

  /**
   * Rule form of {@link #assertNoUriQueryBearerTokens}, for {@link FrozenRules#check}.
   *
   * @return the rule
   */
  public static ArchRule noUriQueryBearerTokensRule() {
    return noClasses()
        .should()
        .callMethod(
            "org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver",
            "setAllowUriQueryParameter",
            "boolean")
        .orShould()
        .callMethod(
            "org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver",
            "setAllowFormEncodedBodyParameter",
            "boolean")
        .as("no security chain reads a bearer token from the query string or a form body")
        .because(
            "a URL lands in access logs, proxies and browser history: a bearer token travels in "
                + "the Authorization header only, and Spring's default resolver reads nothing else "
                + "(krizaka-internal#2397)");
  }

  /**
   * No class lets {@code DefaultBearerTokenResolver} read the token from the query string ({@code
   * ?access_token=}) or a form-encoded body: the default resolver, header only, is the one to use.
   *
   * @param classes the imported production classes
   */
  public static void assertNoUriQueryBearerTokens(JavaClasses classes) {
    noUriQueryBearerTokensRule().check(classes);
  }

  private static ArchCondition<JavaClass> notHaveStaticMutableFields() {
    return new ArchCondition<>("not declare a non-final static field") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaField field : javaClass.getFields()) {
          if (field.getModifiers().contains(JavaModifier.STATIC)
              && !field.getModifiers().contains(JavaModifier.FINAL)
              && !field.getModifiers().contains(JavaModifier.SYNTHETIC)) {
            events.add(
                SimpleConditionEvent.violated(
                    field, field.getDescription() + " is a non-final static field"));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notHaveTransactionalStreams() {
    return new ArchCondition<>("not have a @Transactional method returning a stream") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaMethod method : javaClass.getMethods()) {
          if (method.isAnnotatedWith(TRANSACTIONAL)
              && STREAM_TYPES.contains(method.getRawReturnType().getName())) {
            events.add(
                SimpleConditionEvent.violated(
                    method, method.getDescription() + " is @Transactional and returns a stream"));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notCallTheirOwnTransactionalMethods() {
    return new ArchCondition<>("not call one of their own @Transactional methods") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
          if (call.getTargetOwner().equals(javaClass)
              && call.getTarget().resolveMember().stream()
                  .anyMatch(
                      target ->
                          target.isAnnotatedWith(TRANSACTIONAL)
                              || target.getOwner().isAnnotatedWith(TRANSACTIONAL))) {
            events.add(
                SimpleConditionEvent.violated(
                    call, call.getDescription() + " goes through this, not the proxy"));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notCallSystemGetenv() {
    return new ArchCondition<>("not call System.getenv") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
          if (call.getTargetOwner().isEquivalentTo(System.class)
              && "getenv".equals(call.getName())) {
            events.add(SimpleConditionEvent.violated(call, call.getDescription()));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notCarryUserContentInTheQuery() {
    return new ArchCondition<>("not take user content in a GET request parameter") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getMethods().stream()
            .filter(m -> m.isAnnotatedWith(GET_MAPPING))
            .forEach(m -> checkMethod(m, events));
      }

      private void checkMethod(JavaMethod method, ConditionEvents events) {
        boolean stream = producesEventStream(method);
        method.getParameters().stream()
            .filter(p -> p.isAnnotatedWith(REQUEST_PARAM))
            .filter(p -> stream || carriesUserContent(p))
            .forEach(
                p ->
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            String.format(
                                "%s takes @RequestParam %s on a GET%s",
                                method.getFullName(),
                                Objects.requireNonNullElse(
                                    requestParamName(p), p.getRawType().getSimpleName()),
                                stream ? " event stream" : ""))));
      }
    };
  }

  private static boolean carriesUserContent(JavaParameter parameter) {
    String name = requestParamName(parameter);
    return (name != null && CONTENT_PARAMS.contains(name.toLowerCase(Locale.ROOT)))
        || parameter.getRawType().getSimpleName().endsWith("Request");
  }

  private static boolean producesEventStream(JavaMethod method) {
    return method
        .tryGetAnnotationOfType(GET_MAPPING)
        .flatMap(a -> a.get("produces"))
        .map(v -> v instanceof Object[] values && Arrays.asList(values).contains(EVENT_STREAM))
        .orElse(false);
  }

  private static String requestParamName(JavaParameter parameter) {
    return parameter
        .tryGetAnnotationOfType(REQUEST_PARAM)
        .map(
            a -> {
              for (String key : List.of("name", "value")) {
                Object v = a.get(key).orElse(null);
                if (v instanceof String s && !s.isEmpty()) {
                  return s;
                }
              }
              return null;
            })
        .orElse(null);
  }

  private static ArchCondition<JavaClass> beEnumConstantOrTypeReference() {
    return new ArchCondition<>("be an enum constant body or TypeReference") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        boolean isEnumBody = javaClass.getEnclosingClass().map(JavaClass::isEnum).orElse(false);
        boolean isTypeRef =
            javaClass
                .getSuperclass()
                .map(superClass -> superClass.getName().contains("TypeReference"))
                .orElse(false);
        if (!isEnumBody && !isTypeRef) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass, "Anonymous class <" + javaClass.getName() + "> is not exempt"));
        }
      }
    };
  }

  private static ArchCondition<JavaField> bePrivateAndFinal() {
    return new ArchCondition<>("be private and final") {
      @Override
      public void check(JavaField field, ConditionEvents events) {
        boolean isPrivate = field.getModifiers().contains(JavaModifier.PRIVATE);
        boolean isFinal = field.getModifiers().contains(JavaModifier.FINAL);
        if (!isPrivate || !isFinal) {
          events.add(
              SimpleConditionEvent.violated(
                  field,
                  String.format(
                      "Field <%s> in <%s> is not private final (private=%s, final=%s)",
                      field.getName(), field.getOwner().getName(), isPrivate, isFinal)));
        }
      }
    };
  }

  /** The HTTP clients a feature adapter must not build or call by hand. */
  private static final Set<String> RAW_HTTP_CLIENTS =
      Set.of(
          "org.springframework.web.client.RestClient",
          "org.springframework.web.client.RestTemplate",
          "org.springframework.web.reactive.function.client.WebClient");

  /**
   * No class of the module depends on {@code RestClient}, {@code RestTemplate} or {@code WebClient}
   * unless it lives in a {@code config} package.
   *
   * <p><b>Scope.</b> Every class under {@code modulePackage} whose package has no {@code config}
   * segment ({@code ..config..}). A dependency is any use of the client type or one of its nested
   * types (for example {@code RestClient.Builder}) in a field, a parameter, a return type, a call
   * or a constructor; {@code RestClientException} and {@code RestClientResponseException} are other
   * types and stay allowed, because an adapter translates them into its domain exception. Test
   * classes are not imported by {@link #importProductionClasses}.
   *
   * <p><b>Why.</b> An HTTP call is declared, not coded: an {@code @HttpExchange} interface
   * registered with {@code @ImportHttpServices}, whose base URL, timeouts and authentication are
   * set once by the group configuration in {@code config}. An adapter that builds its own client
   * forgets the authentication of the next method, or the timeout of the next client.
   *
   * @param modulePackage the module's root package, e.g. {@code "com.krizaka.billing.client"}
   * @return the rule
   */
  public static ArchRule noRawHttpClientInAdaptersRule(String modulePackage) {
    return noClasses()
        .that()
        .resideInAPackage(modulePackage + "..")
        .and()
        .resideOutsideOfPackage("..config..")
        .should()
        .dependOnClassesThat(rawHttpClients())
        .as("no RestClient, RestTemplate or WebClient outside a config package")
        .because(
            "an HTTP call is a package-private @HttpExchange interface registered with"
                + " @ImportHttpServices; the client, its base URL, timeouts and authentication are"
                + " built once, in config")
        // a module whose classes all live in config has no adapter to judge
        .allowEmptyShould(true);
  }

  /**
   * Asserts {@link #noRawHttpClientInAdaptersRule}.
   *
   * @param classes the imported production classes
   * @param modulePackage the module's root package
   */
  public static void assertNoRawHttpClientInAdapters(JavaClasses classes, String modulePackage) {
    noRawHttpClientInAdaptersRule(modulePackage).check(classes);
  }

  /**
   * A package holds at most {@code max} top-level classes (nested types and {@code package-info} do
   * not count; interfaces, enums and records do).
   *
   * <p>A package that grows past a handful of types is several features in one folder; the limit
   * forces the split by feature. One violation is reported per package, on its first class by name,
   * so a frozen store stays stable when classes are added to a package already over the limit.
   *
   * @param modulePackage the module's root package
   * @param max the most top-level classes one package may hold
   * @return the rule
   */
  public static ArchRule maxTopLevelClassesPerPackageRule(String modulePackage, int max) {
    return classes()
        .that()
        .resideInAPackage(modulePackage + "..")
        .should(holdAtMostTopLevelClasses(max))
        .as("a package holds at most " + max + " top-level classes")
        .because(
            "a package that holds more is several features in one folder: split it by feature");
  }

  /**
   * Asserts {@link #maxTopLevelClassesPerPackageRule}.
   *
   * @param classes the imported production classes
   * @param modulePackage the module's root package
   * @param max the most top-level classes one package may hold
   */
  public static void assertMaxTopLevelClassesPerPackage(
      JavaClasses classes, String modulePackage, int max) {
    maxTopLevelClassesPerPackageRule(modulePackage, max).check(classes);
  }

  private static DescribedPredicate<JavaClass> rawHttpClients() {
    return DescribedPredicate.describe(
        "a raw HTTP client (RestClient, RestTemplate, WebClient)",
        type ->
            RAW_HTTP_CLIENTS.stream()
                .anyMatch(
                    client ->
                        type.getName().equals(client) || type.getName().startsWith(client + "$")));
  }

  private static ArchCondition<JavaClass> holdAtMostTopLevelClasses(int max) {
    return new ArchCondition<>("hold at most " + max + " top-level classes") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        List<String> topLevel = topLevelClassNames(javaClass);
        if (topLevel.size() > max && topLevel.get(0).equals(javaClass.getName())) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  String.format(
                      "Package '%s' holds %d top-level classes (max %d): %s",
                      javaClass.getPackageName(), topLevel.size(), max, topLevel)));
        }
      }
    };
  }

  /** The top-level classes of the package of {@code javaClass}, sorted by name. */
  private static List<String> topLevelClassNames(JavaClass javaClass) {
    return javaClass.getPackage().getClasses().stream()
        .filter(JavaClass::isTopLevelClass)
        .filter(type -> !"package-info".equals(type.getSimpleName()))
        .map(JavaClass::getName)
        .sorted()
        .toList();
  }

  private static final String HTTP_EXCHANGE =
      "org.springframework.web.service.annotation.HttpExchange";

  /**
   * An {@code @HttpExchange} interface is package-private: it is the declared transport of one
   * feature, reached only by that feature's adapter.
   *
   * @return the rule
   */
  public static ArchRule httpExchangeInterfacesPackagePrivateRule() {
    return classes()
        .that()
        .areInterfaces()
        .and()
        .areAnnotatedWith(HTTP_EXCHANGE)
        .should()
        .bePackagePrivate()
        .as("an @HttpExchange interface is package-private")
        .because("the declared transport belongs to one feature; the port is what others see")
        .allowEmptyShould(true);
  }

  /**
   * No parameter or return type of an {@code @HttpExchange} interface (generic arguments included)
   * lives in the domain package: the interface speaks wire records, the adapter maps them to and
   * from the domain.
   *
   * @param domainPackage the domain package pattern, e.g. {@code "com.krizaka.billing.domain.."}
   * @return the rule
   */
  public static ArchRule httpExchangeSignaturesHaveNoDomainTypeRule(String domainPackage) {
    return noMethods()
        .that()
        .areDeclaredInClassesThat()
        .areAnnotatedWith(HTTP_EXCHANGE)
        .should(mentionAType(domainPackage))
        .as("an @HttpExchange signature names no domain type")
        .because(
            "the wire shape and the domain change for different reasons: map them in the adapter")
        .allowEmptyShould(true);
  }

  /**
   * Outside a {@code config} package, the only public top-level types are {@code *Configuration}
   * classes: a feature exposes its wiring and nothing else, so its adapters, exchanges and wire
   * records stay package-private. For a module whose feature packages are internal to a published
   * port (a client SDK); a module with a public API does not apply it.
   *
   * @param modulePackage the module's root package
   * @return the rule
   */
  public static ArchRule featuresExposeOnlyTheirConfigurationRule(String modulePackage) {
    return classes()
        .that()
        .resideInAPackage(modulePackage + "..")
        .and()
        .resideOutsideOfPackage("..config..")
        .and()
        .arePublic()
        .and()
        .areTopLevelClasses()
        .and()
        .doNotHaveSimpleName("package-info")
        .should()
        .haveSimpleNameEndingWith("Configuration")
        .as("a feature package exposes only its *Configuration")
        .because("adapters, exchanges and wire records are package-private: the port is the API")
        .allowEmptyShould(true);
  }

  private static ArchCondition<JavaMethod> mentionAType(String packagePattern) {
    DescribedPredicate<JavaClass> inPackage = JavaClass.Predicates.resideInAPackage(packagePattern);
    return new ArchCondition<>("name a type of " + packagePattern) {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        Stream.concat(
                method.getReturnType().getAllInvolvedRawTypes().stream(),
                method.getParameterTypes().stream()
                    .flatMap(type -> type.getAllInvolvedRawTypes().stream()))
            .filter(inPackage)
            .findFirst()
            .ifPresent(
                type ->
                    events.add(
                        SimpleConditionEvent.satisfied(
                            method,
                            String.format(
                                "Method <%s> names the domain type <%s>",
                                method.getFullName(), type.getName()))));
      }
    };
  }
}
