package com.krizaka.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.users.domain.exception.CredentialGrantRefusedException;
import com.krizaka.users.domain.exception.CredentialNotFoundException;
import com.krizaka.users.domain.exception.InvalidRequestException;
import com.krizaka.users.domain.model.CredentialGrant;
import com.krizaka.users.domain.model.UserCredential;
import com.krizaka.users.domain.ports.inbound.IdentityService;
import com.krizaka.users.domain.ports.outbound.AdminUserRepositoryPort;
import com.krizaka.users.domain.ports.outbound.CredentialGrantRepositoryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** GHSA-3pf7-rgf2-4x3m: a provider key is released once, for its run, on the user's grant. */
class CredentialGrantServiceImplTest {

  private static final String ALICE = "550e8400-e29b-41d4-a716-446655440001";
  private static final String BOB = "550e8400-e29b-41d4-a716-446655440002";
  private static final Instant T0 = Instant.parse("2026-11-03T12:00:00Z");

  private final IdentityService identity = mock(IdentityService.class);
  private final AdminUserRepositoryPort audit = mock(AdminUserRepositoryPort.class);
  private final InMemoryGrants grants = new InMemoryGrants();
  private final MutableClock clock = new MutableClock(T0);
  private final CredentialGrantServiceImpl service =
      new CredentialGrantServiceImpl(grants, identity, audit, Duration.ofMinutes(10), clock);

  CredentialGrantServiceImplTest() {
    when(identity.getUserCredentials(ALICE))
        .thenReturn(List.of(new UserCredential("openai", true)));
    when(identity.getUserCredentials(BOB)).thenReturn(List.of());
    when(identity.getDecryptedApiKey(ALICE, "openai")).thenReturn(Optional.of("sk-alice"));
  }

  @Test
  @DisplayName("a service token alone (no grant) never reads another user's key")
  void aServiceWithoutAGrantIsRefused() {
    assertThatThrownBy(() -> service.redeem("forged", "openai", "run-1", "sovkora-worker-media"))
        .isInstanceOf(CredentialGrantRefusedException.class)
        .hasMessageNotContaining("forged");
    verify(audit)
        .appendAuditOutcome(
            isNull(),
            eq("sovkora-worker-media"),
            eq("credential.used"),
            isNull(),
            any(),
            eq("refused"));
  }

  @Test
  @DisplayName("a grant releases the key once, for its run, then never again")
  void aGrantIsSingleUse() {
    CredentialGrant g = service.issue(ALICE, "openai", "run-1");

    assertThat(g.expiresAt()).isEqualTo(T0.plus(Duration.ofMinutes(10)));
    assertThat(service.redeem(g.grant(), "openai", "run-1", "sovkora-job-service"))
        .isEqualTo("sk-alice");
    assertThatThrownBy(() -> service.redeem(g.grant(), "openai", "run-1", "sovkora-job-service"))
        .isInstanceOf(CredentialGrantRefusedException.class);
    verify(audit)
        .appendAudit(isNull(), eq("sovkora-job-service"), eq("credential.used"), eq(ALICE), any());
  }

  @Test
  @DisplayName("a grant is bound to its run and its provider")
  void aGrantIsBoundToItsRunAndProvider() {
    CredentialGrant g = service.issue(ALICE, "openai", "run-1");

    assertThatThrownBy(() -> service.redeem(g.grant(), "openai", "run-2", "sovkora-job-service"))
        .isInstanceOf(CredentialGrantRefusedException.class);
    assertThatThrownBy(() -> service.redeem(g.grant(), "anthropic", "run-1", "sovkora-job-service"))
        .isInstanceOf(CredentialGrantRefusedException.class);
  }

  @Test
  @DisplayName("an expired grant is refused")
  void anExpiredGrantIsRefused() {
    CredentialGrant g = service.issue(ALICE, "openai", "run-1");
    clock.now = T0.plus(Duration.ofMinutes(10));

    assertThatThrownBy(() -> service.redeem(g.grant(), "openai", "run-1", "sovkora-job-service"))
        .isInstanceOf(CredentialGrantRefusedException.class);
  }

  @Test
  @DisplayName("no grant is issued for a key the user does not have")
  void noGrantWithoutAKey() {
    assertThatThrownBy(() -> service.issue(BOB, "openai", "run-1"))
        .isInstanceOf(CredentialNotFoundException.class);
    assertThat(grants.rows).isEmpty();
  }

  @Test
  @DisplayName("only the hash of the grant is stored, and nothing prints the secret")
  void theSecretIsNeverStoredNorPrinted() {
    CredentialGrant g = service.issue(ALICE, "openai", "run-1");

    assertThat(grants.rows).doesNotContainKey(g.grant()).hasSize(1);
    assertThat(grants.rows.keySet().iterator().next()).hasSize(64);
    assertThat(g.toString()).doesNotContain(g.grant());
  }

  @Test
  @DisplayName("a malformed run id is a bad request at issuance, an audited refusal at use")
  void aBadRunIdIsRefused() {
    assertThatThrownBy(() -> service.issue(ALICE, "openai", " "))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.issue(ALICE, "openai", "run 1\nforged=audit"))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.redeem("g", "openai", "r".repeat(129), "svc"))
        .isInstanceOf(CredentialGrantRefusedException.class);
    verify(audit)
        .appendAuditOutcome(
            isNull(), eq("svc"), eq("credential.used"), isNull(), any(), eq("refused"));
    assertThat(grants.rows).isEmpty();
  }

  @Test
  @DisplayName("a grant longer than an hour is a configuration error")
  void theTtlIsBounded() {
    assertThatThrownBy(
            () ->
                new CredentialGrantServiceImpl(grants, identity, audit, Duration.ofHours(2), clock))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** Same contract as the conditional UPDATE: consumed at most once. */
  private static final class InMemoryGrants implements CredentialGrantRepositoryPort {

    record Row(String userId, String provider, String runId, Instant expiresAt, boolean used) {}

    final Map<String, Row> rows = new ConcurrentHashMap<>();

    @Override
    public void save(
        String grantHash, String userId, String providerName, String runId, Instant expiresAt) {
      rows.put(grantHash, new Row(userId, providerName, runId, expiresAt, false));
    }

    @Override
    public synchronized Optional<String> consume(
        String grantHash, String providerName, String runId, Instant now) {
      Row r = rows.get(grantHash);
      if (r == null
          || r.used()
          || !r.provider().equals(providerName)
          || !r.runId().equals(runId)
          || !r.expiresAt().isAfter(now)) {
        return Optional.empty();
      }
      rows.put(grantHash, new Row(r.userId(), r.provider(), r.runId(), r.expiresAt(), true));
      return Optional.of(r.userId());
    }

    @Override
    public int purgeExpiredBefore(Instant before) {
      return 0;
    }
  }

  private static final class MutableClock extends Clock {
    Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
