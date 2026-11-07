package com.krizaka.test.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * The HTTP-client rule and the package-size rule: each passes on its cure and fails on its defect.
 */
class ModuleShapeRulesTest {

  private static final String ROOT = "com.krizaka.test.architecture.fixture";
  private static final String HTTP_GOOD = ROOT + ".http.good";
  private static final String HTTP_BAD = ROOT + ".http.bad";
  private static final String SIZE_GOOD = ROOT + ".size.good";
  private static final String SIZE_BAD = ROOT + ".size.bad";

  private static JavaClasses classes(String pkg) {
    return new ClassFileImporter().importPackages(pkg);
  }

  @Test
  void aClientBuiltInConfigAndAnExceptionCaughtOutsideItPass() {
    assertThatCode(() -> CodeRules.assertNoRawHttpClientInAdapters(classes(HTTP_GOOD), HTTP_GOOD))
        .doesNotThrowAnyException();
  }

  @Test
  void aRestClientFieldInAnAdapterIsFound() {
    assertThatThrownBy(() -> CodeRules.assertNoRawHttpClientInAdapters(classes(HTTP_BAD), HTTP_BAD))
        .hasMessageContaining("RawAdapter")
        .hasMessageContaining("RestClient");
  }

  @Test
  void aRestTemplateInAnAdapterIsFound() {
    assertThatThrownBy(() -> CodeRules.assertNoRawHttpClientInAdapters(classes(HTTP_BAD), HTTP_BAD))
        .hasMessageContaining("LegacyAdapter")
        .hasMessageContaining("RestTemplate");
  }

  @Test
  void aNestedTypeOfTheClientCountsAsTheClient() {
    assertThatThrownBy(() -> CodeRules.assertNoRawHttpClientInAdapters(classes(HTTP_BAD), HTTP_BAD))
        .hasMessageContaining("BuilderAdapter");
  }

  @Test
  void aPackageWithinTheLimitPasses() {
    assertThatCode(
            () -> CodeRules.assertMaxTopLevelClassesPerPackage(classes(SIZE_GOOD), SIZE_GOOD, 2))
        .doesNotThrowAnyException();
  }

  @Test
  void aPackageOverTheLimitIsFoundOnceWithItsClasses() {
    assertThatThrownBy(
            () -> CodeRules.assertMaxTopLevelClassesPerPackage(classes(SIZE_BAD), SIZE_BAD, 2))
        .hasMessageContaining("holds 3 top-level classes (max 2)")
        .hasMessageContaining("Three")
        .hasMessageContaining("(1 times)");
  }

  private static final String EXCHANGE_GOOD = ROOT + ".exchange.good";
  private static final String EXCHANGE_BAD = ROOT + ".exchange.bad";
  private static final String EXPOSURE_GOOD = ROOT + ".exposure.good";
  private static final String EXPOSURE_BAD = ROOT + ".exposure.bad";

  @Test
  void aPackagePrivateExchangeOfWireRecordsPasses() {
    JavaClasses good = classes(EXCHANGE_GOOD);

    assertThatCode(
            () -> {
              CodeRules.httpExchangeInterfacesPackagePrivateRule().check(good);
              CodeRules.httpExchangeSignaturesHaveNoDomainTypeRule(ROOT + ".exchange.bad.domain..")
                  .check(good);
            })
        .doesNotThrowAnyException();
  }

  @Test
  void aPublicExchangeIsFound() {
    assertThatThrownBy(
            () -> CodeRules.httpExchangeInterfacesPackagePrivateRule().check(classes(EXCHANGE_BAD)))
        .hasMessageContaining("PublicExchange");
  }

  @Test
  void aDomainTypeInsideAGenericOfAnExchangeSignatureIsFound() {
    assertThatThrownBy(
            () ->
                CodeRules.httpExchangeSignaturesHaveNoDomainTypeRule(
                        ROOT + ".exchange.bad.domain..")
                    .check(classes(EXCHANGE_BAD)))
        .hasMessageContaining("LeakingExchange.packs()")
        .hasMessageContaining("Pack");
  }

  @Test
  void aFeatureThatExposesOnlyItsConfigurationPasses() {
    assertThatCode(
            () ->
                CodeRules.featuresExposeOnlyTheirConfigurationRule(EXPOSURE_GOOD)
                    .check(classes(EXPOSURE_GOOD)))
        .doesNotThrowAnyException();
  }

  @Test
  void aPublicAdapterIsFound() {
    assertThatThrownBy(
            () ->
                CodeRules.featuresExposeOnlyTheirConfigurationRule(EXPOSURE_BAD)
                    .check(classes(EXPOSURE_BAD)))
        .hasMessageContaining("HttpWidgetClient");
  }
}
