package com.krizaka.users.architecture;

import static com.krizaka.test.architecture.CodeRules.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.krizaka.test.architecture.SourceRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Governance (naming + one-package-one-kind) for the krizaka-users-core module, reusing the shared
 * {@link com.krizaka.test.architecture.CodeRules}.
 *
 * <p>Identity uses the port/impl split: inbound ports live in {@code domain.ports.inbound} and
 * their implementations in {@code application.service} as package-private {@code
 * *ServiceImpl}/{@code *ProviderImpl}. The router's "service package = *Service only" rule
 * therefore does not apply; impl package-privacy is enforced by {@code IdentityBoundaryTest}
 * ([ERR-105/106]).
 */
class IdentityGovernanceTest {

  private static final String PKG = "com.krizaka.users";
  private static JavaClasses identityClasses;

  @BeforeAll
  static void importClasses() {
    identityClasses = importProductionClasses(PKG);
  }

  @Test
  @DisplayName("[ERR-104] No redundant 'Krizaka' prefix")
  void noRedundantPrefix() {
    assertDoesNotThrow(
        () -> {
          assertNoProductPrefix(identityClasses, PKG, "Krizaka");
        });
  }

  @Test
  @DisplayName("[ERR-107] Mapper classes are final and package-private")
  void mappersAreFinalAndPackagePrivate() {
    assertDoesNotThrow(
        () -> {
          assertMappersFinal(identityClasses, PKG);
          assertMappersPackagePrivate(identityClasses, PKG);
        });
  }

  @Test
  @DisplayName("[ERR-112] Web controllers only in the segregated REST ingress adapter")
  void webControllersOnlyInRestAdapter() {
    assertDoesNotThrow(
        () -> {
          // Strangler-fig Phase 2: the identity context owns its REST ingress
          // (auth/profile/api-keys/credentials) — hosted by the identity service and,
          // during the dual-run window, by the router. Controllers stay segregated in
          // infrastructure.adapter.rest per ERR-112; anywhere else remains banned.
          com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
              .that()
              .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
              .should()
              .resideInAPackage(PKG + ".infrastructure.adapter.rest..")
              .because("The identity REST ingress is segregated in its adapter package [ERR-112]")
              .check(identityClasses);
        });
  }

  @Test
  @DisplayName("[ERR-130] Domain holds no transport DTOs (*Request/*Response)")
  void domainHasNoTransportDtos() {
    assertDoesNotThrow(
        () -> {
          assertDomainHasNoTransportDtos(identityClasses, PKG + ".domain");
        });
  }

  @Test
  @DisplayName("[HEX-002] Domain layer is framework-free (no Spring/JPA/Jackson)")
  void domainPurity() {
    assertDoesNotThrow(
        () -> {
          assertDomainPurity(identityClasses, PKG);
        });
  }

  // GOV-006: [ERR-130] support-package hygiene is not invoked here: this module has no
  // infrastructure.support package, so the rule inspected no class.

  @Test
  @DisplayName("[ERR-130] adapter/persistence holds only *Adapter/*Mapper")
  void persistenceAdapterPackageKind() {
    assertDoesNotThrow(
        () -> {
          assertPersistenceAdapterPackageKind(identityClasses, PKG);
        });
  }

  @Test
  @DisplayName("[ADR-007] Collection fields are private final")
  void collectionFieldsPrivateFinal() {
    assertDoesNotThrow(
        () -> {
          assertCollectionFieldsPrivateFinal(identityClasses);
        });
  }

  @Test
  @DisplayName("[GOV-001] No anonymous classes in production")
  void noAnonymousClasses() {
    assertDoesNotThrow(
        () -> {
          assertNoAnonymousClasses(identityClasses, PKG);
        });
  }

  @Test
  @DisplayName("[ERR-113] No Environment injection in production beans")
  void noEnvironmentInjection() {
    assertDoesNotThrow(
        () -> {
          SourceRules.assertNoEnvironmentInjection(Path.of("src", "main", "java"));
        });
  }
}
