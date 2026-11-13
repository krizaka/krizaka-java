package com.krizaka.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.users.domain.exception.InvalidRequestException;
import com.krizaka.users.domain.model.AdminActor;
import com.krizaka.users.domain.ports.outbound.AdminUserRepositoryPort;
import com.krizaka.users.persistence.domain.model.RateLimitDto;
import com.krizaka.users.persistence.domain.ports.RateLimitPersistenceProvider;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/** The operator sets the daily request quota of a tier: bounded, and every change audited. */
class AdminUserServiceRateLimitTest {

  private static final AdminActor ADMIN = new AdminActor("a-1", "ops@krizaka.com");

  private final RateLimitPersistenceProvider rows = mock(RateLimitPersistenceProvider.class);
  private final AdminUserRepositoryPort audit = mock(AdminUserRepositoryPort.class);
  private final AdminUserServiceImpl service = new AdminUserServiceImpl(audit, rows);

  @Test
  void settingTheQuotaStoresItAndWritesTheAuditLine() {
    when(rows.findById("service")).thenReturn(Optional.of(new RateLimitDto("service", 1000, 20)));
    when(rows.updateDailyRequests("service", 50_000))
        .thenReturn(new RateLimitDto("service", 1000, 20, 50_000));

    var updated = service.setDailyRequests(ADMIN, "service", 50_000);

    assertThat(updated).isPresent();
    assertThat(updated.get().dailyRequests()).isEqualTo(50_000);
    verify(audit)
        .appendAudit(
            "a-1",
            "ops@krizaka.com",
            "identity.rate_limit_daily_requests_changed",
            "service",
            "from=default to=50000");
  }

  @Test
  void clearingTheQuotaAuditsTheChangeBackToTheEdgeDefault() {
    when(rows.findById("service"))
        .thenReturn(Optional.of(new RateLimitDto("service", 1000, 20, 50_000)));
    when(rows.updateDailyRequests("service", null))
        .thenReturn(new RateLimitDto("service", 1000, 20));

    assertThat(service.setDailyRequests(ADMIN, "service", null)).isPresent();
    verify(audit)
        .appendAudit(
            "a-1",
            "ops@krizaka.com",
            "identity.rate_limit_daily_requests_changed",
            "service",
            "from=50000 to=default");
  }

  @Test
  void aQuotaOutOfBoundsIsRefusedAndNothingIsWritten() {
    assertThatThrownBy(() -> service.setDailyRequests(ADMIN, "service", 0))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.setDailyRequests(ADMIN, "service", 10_000_001))
        .isInstanceOf(InvalidRequestException.class);

    verify(rows, never()).updateDailyRequests(ArgumentMatchers.any(), ArgumentMatchers.any());
    verify(audit, never())
        .appendAudit(
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any());
  }

  @Test
  void anUnknownTierIsAbsentAndNothingIsAudited() {
    when(rows.findById("ghost")).thenReturn(Optional.empty());

    assertThat(service.setDailyRequests(ADMIN, "ghost", 10)).isEmpty();
    verify(audit, never())
        .appendAudit(
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any());
  }

  @Test
  void readingATierGivesItsRowWithItsQuota() {
    when(rows.findById("service"))
        .thenReturn(Optional.of(new RateLimitDto("service", 1000, 20, 50_000)));

    assertThat(service.rateLimit("service").orElseThrow().dailyRequests()).isEqualTo(50_000);
  }
}
