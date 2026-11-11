package com.krizaka.test.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The security lessons of ADR-013: each rule fails on the defect it names and passes on its cure.
 */
class SecurityRulesTest {

  private static final String ROOT = "com.krizaka.test.architecture.fixture.security";
  private static final JavaClasses BAD = new ClassFileImporter().importPackages(ROOT + ".bad");
  private static final JavaClasses GOOD = new ClassFileImporter().importPackages(ROOT + ".good");

  @Test
  void compliantCodePassesEverySecurityRule() {
    assertThatCode(
            () -> {
              CodeRules.assertNoStaticMutableState(GOOD);
              CodeRules.assertNoGetenvOutsideConfig(GOOD, "..config..");
              CodeRules.assertNoContentOnGet(GOOD);
              CodeRules.assertNoTransactionalOnStreams(GOOD);
              CodeRules.assertNoSelfInvocationOfTransactional(GOOD);
              CodeRules.assertNoUriQueryBearerTokens(GOOD);
            })
        .doesNotThrowAnyException();
  }

  @Test
  void staticMutableStateIsFound() {
    assertThatThrownBy(() -> CodeRules.assertNoStaticMutableState(BAD))
        .hasMessageContaining("StaticCrypto.key")
        .hasMessageContaining("krizaka-users#57");
  }

  @Test
  void getenvOutsideTheConfigLayerIsFound() {
    assertThatThrownBy(() -> CodeRules.assertNoGetenvOutsideConfig(BAD, "..config.."))
        .hasMessageContaining("StaticCrypto")
        .hasMessageContaining("krizaka-java#102");
  }

  @Test
  void aRequestParamOnAnEventStreamIsFound() {
    assertThatThrownBy(() -> CodeRules.assertNoContentOnGet(BAD))
        .hasMessageContaining("stream")
        .hasMessageContaining("event stream");
  }

  @Test
  void aPromptOnAGetIsFound() {
    assertThatThrownBy(() -> CodeRules.assertNoContentOnGet(BAD))
        .hasMessageContaining("ask")
        .hasMessageContaining("prompt");
  }

  @Test
  void aTransactionalStreamIsFound() {
    assertThatThrownBy(() -> CodeRules.assertNoTransactionalOnStreams(BAD))
        .hasMessageContaining("history")
        .hasMessageContaining("conversation-service#10");
  }

  @Test
  void aSelfCallOfATransactionalMethodIsFound() {
    assertThatThrownBy(() -> CodeRules.assertNoSelfInvocationOfTransactional(BAD))
        .hasMessageContaining("Sweeper.sweep()")
        .hasMessageContaining("goes through this, not the proxy")
        .hasMessageContaining("krizaka-internal#2153")
        .hasMessageNotContaining("Sweeper.sweepAll()");
  }

  @Test
  void aBearerTokenReadFromTheQueryStringIsFound() {
    assertThatThrownBy(() -> CodeRules.assertNoUriQueryBearerTokens(BAD))
        .hasMessageContaining("QueryTokenChain.resolver()")
        .hasMessageContaining("setAllowUriQueryParameter")
        .hasMessageContaining("Authorization header only");
  }

  @Test
  void theRuleFormsFreezeExistingDebt(@TempDir Path store) {
    ArchConfiguration.get().reset();
    ArchConfiguration.get().setProperty("freeze.store.default.path", store.toString());
    try {
      ArchConfiguration.get().setProperty("freeze.store.default.allowStoreCreation", "true");
      FrozenRules.check(CodeRules.noStaticMutableStateRule(), BAD);
      ArchConfiguration.get().setProperty("freeze.store.default.allowStoreCreation", "false");
      assertThatCode(() -> FrozenRules.check(CodeRules.noStaticMutableStateRule(), BAD))
          .doesNotThrowAnyException();
    } finally {
      ArchConfiguration.get().reset();
    }
  }
}
