package com.krizaka.test.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A copy of a library type and a bypass of a facade are refused; an adapter and config pass. */
class ReuseRulesTest {

  private static final String ROOT = "com.krizaka.test.architecture.fixture.reuse";
  private static final String LIB = ROOT + ".lib.";
  private static final LibraryCatalog CATALOG =
      LibraryCatalog.of(
          List.of(LIB + "Clock", LIB + "HttpGateway"),
          Map.of("org.springframework.web.client.RestClient", LIB + "HttpGateway"));

  private static JavaClasses classes(String pkg) {
    return new ClassFileImporter().importPackages(pkg);
  }

  @Test
  void aLocalClassNamedLikeALibraryTypeIsRefusedAndNamesTheTypeToUse() {
    String pkg = ROOT + ".copy";

    assertThatThrownBy(
            () -> ReuseRules.noLocalCopyOfLibraryTypeRule(pkg, CATALOG).check(classes(pkg)))
        .hasMessageContaining(pkg + ".Clock copies a krizaka-java type")
        .hasMessageContaining("use " + LIB + "Clock");
  }

  @Test
  void anAdapterThatImplementsTheTypeIsAllowedEvenWhenItSharesTheName() {
    String pkg = ROOT + ".adapter";

    assertThatCode(() -> ReuseRules.noLocalCopyOfLibraryTypeRule(pkg, CATALOG).check(classes(pkg)))
        .doesNotThrowAnyException();
  }

  @Test
  void theLibraryTypeItselfIsNotACopyOfItself() {
    assertThatCode(
            () ->
                ReuseRules.noLocalCopyOfLibraryTypeRule(ROOT + ".lib", CATALOG)
                    .check(classes(ROOT + ".lib")))
        .doesNotThrowAnyException();
  }

  @Test
  void aDirectUseOfAWrappedApiOutsideConfigIsRefusedAndNamesTheFacade() {
    String pkg = ROOT + ".bypass";

    assertThatThrownBy(() -> ReuseRules.noBypassOfFacadeRule(pkg, CATALOG).check(classes(pkg)))
        .hasMessageContaining("DirectCaller uses org.springframework.web.client.RestClient")
        .hasMessageContaining("use the facade " + LIB + "HttpGateway")
        .hasMessageNotContaining("ClientConfig");
  }

  @Test
  void theClasspathCatalogIsGeneratedAndNotEmpty() {
    LibraryCatalog fromClasspath = LibraryCatalog.fromClasspath();

    org.assertj.core.api.Assertions.assertThat(fromClasspath.publicTypes()).isNotEmpty();
    org.assertj.core.api.Assertions.assertThat(fromClasspath.publicTypes())
        .containsKey("OutboxStore");
  }
}
