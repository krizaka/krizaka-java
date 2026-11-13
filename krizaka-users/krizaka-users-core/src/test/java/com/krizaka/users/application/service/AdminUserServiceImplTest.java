package com.krizaka.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.krizaka.users.domain.exception.AdminActionRefusedException;
import com.krizaka.users.domain.exception.ServiceAccountProtectedException;
import com.krizaka.users.domain.exception.UserNotFoundException;
import com.krizaka.users.domain.model.AdminActor;
import com.krizaka.users.domain.model.AdminUserView;
import com.krizaka.users.domain.ports.outbound.AdminUserRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

  private static final AdminActor ME = new AdminActor("admin-1", "admin@example.test");

  @Mock private AdminUserRepositoryPort repository;
  @Mock private com.krizaka.users.persistence.domain.ports.RateLimitPersistenceProvider rateLimits;
  private AdminUserServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new AdminUserServiceImpl(repository, rateLimits);
  }

  private static AdminUserView user(String id, String email, boolean enabled, boolean admin) {
    return new AdminUserView(
        id,
        "u-" + id,
        email,
        enabled,
        admin,
        "local",
        admin ? Set.of("ROLE_USER", "ROLE_ADMIN") : Set.of("ROLE_USER"),
        Instant.parse("2026-10-01T00:00:00Z"));
  }

  @Test
  void listBoundsThePage() {
    when(repository.search("x", 0, 100)).thenReturn(List.of());
    when(repository.count("x")).thenReturn(0L);
    var page = service.list("x", -3, 5000);
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(100);
  }

  @Test
  void suspendDisablesAndAudits() {
    when(repository.find("u1")).thenReturn(Optional.of(user("u1", "a@x.test", true, false)));
    service.suspend(ME, "u1");
    verify(repository).setEnabled("u1", false);
    verify(repository)
        .appendAudit("admin-1", "admin@example.test", "admin.user_suspended", "u1", null);
  }

  @Test
  void nobodySuspendsTheirOwnAccount() {
    when(repository.find("admin-1"))
        .thenReturn(Optional.of(user("admin-1", "admin@example.test", true, true)));
    assertThatThrownBy(() -> service.suspend(ME, "admin-1"))
        .isInstanceOfSatisfying(
            AdminActionRefusedException.class, e -> assertThat(e.code()).isEqualTo("SELF"));
    verify(repository, never()).setEnabled(any(), eq(false));
    // ADR-085: a refused action is audited too (what was tried, and why it was refused).
    verify(repository)
        .appendAuditOutcome(
            "admin-1", "admin@example.test", "admin.user_suspended", "admin-1", "SELF", "refused");
  }

  @Test
  void anActionOnAnUnknownAccountIsAuditedAsFailed() {
    when(repository.find("ghost")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.suspend(ME, "ghost"))
        .isInstanceOf(UserNotFoundException.class);
    verify(repository)
        .appendAuditOutcome(
            "admin-1",
            "admin@example.test",
            "admin.user_suspended",
            "ghost",
            "not_found",
            "failed");
  }

  @Test
  void theLastAdministratorKeepsTheRole() {
    when(repository.find("u2")).thenReturn(Optional.of(user("u2", "b@x.test", true, true)));
    when(repository.countEnabledAdmins()).thenReturn(1L);
    assertThatThrownBy(() -> service.setAdmin(ME, "u2", false))
        .isInstanceOfSatisfying(
            AdminActionRefusedException.class, e -> assertThat(e.code()).isEqualTo("LAST_ADMIN"));
    assertThatThrownBy(() -> service.suspend(ME, "u2"))
        .isInstanceOfSatisfying(
            AdminActionRefusedException.class, e -> assertThat(e.code()).isEqualTo("LAST_ADMIN"));
  }

  @Test
  void anAdministratorIsWithdrawnWhileAnotherRemains() {
    when(repository.find("u2")).thenReturn(Optional.of(user("u2", "b@x.test", true, true)));
    when(repository.countEnabledAdmins()).thenReturn(2L);
    service.setAdmin(ME, "u2", false);
    verify(repository).setAdmin("u2", false);
    verify(repository)
        .appendAudit(
            "admin-1", "admin@example.test", "admin.user_role_changed", "u2", "ADMIN withdrawn");
  }

  @Test
  void grantingTheRoleAlreadyHeldChangesNothing() {
    when(repository.find("u2")).thenReturn(Optional.of(user("u2", "b@x.test", true, true)));
    service.setAdmin(ME, "u2", true);
    verify(repository, never()).setAdmin(any(), eq(true));
  }

  @Test
  void erasureNeedsTheTypedEmailAndKeepsTheFactNotThePerson() {
    when(repository.find("u3")).thenReturn(Optional.of(user("u3", "c@x.test", true, false)));
    assertThatThrownBy(() -> service.erase(ME, "u3", "someone@else.test"))
        .isInstanceOfSatisfying(
            AdminActionRefusedException.class,
            e -> assertThat(e.code()).isEqualTo("CONFIRM_MISMATCH"));
    verify(repository, never()).delete(any());
    service.erase(ME, "u3", "  C@X.TEST ");
    var order = org.mockito.Mockito.inOrder(repository);
    order.verify(repository).anonymiseAudit("u3");
    order.verify(repository).delete("u3");
    order
        .verify(repository)
        .appendAudit("admin-1", "admin@example.test", "admin.account_deleted", "u3", null);
  }

  @Test
  void aProductsServiceAccountIsNeverErasedAndTheRefusalIsAudited() {
    when(repository.find("svc1"))
        .thenReturn(
            Optional.of(
                new AdminUserView(
                    "svc1",
                    "svc_folioviv",
                    "svc@x.test",
                    true,
                    false,
                    "local",
                    Set.of("ROLE_USER"),
                    Instant.parse("2026-10-01T00:00:00Z"))));
    assertThatThrownBy(() -> service.erase(ME, "svc1", "svc@x.test"))
        .isInstanceOfSatisfying(
            ServiceAccountProtectedException.class,
            e -> assertThat(e.code()).isEqualTo("SERVICE_ACCOUNT_PROTECTED"));
    verify(repository, never()).delete(any());
    verify(repository, never()).anonymiseAudit(any());
    verify(repository)
        .appendAuditOutcome(
            "admin-1",
            "admin@example.test",
            "admin.account_deleted",
            "svc1",
            "SERVICE_ACCOUNT_PROTECTED",
            "refused");
  }

  @Test
  void exportCarriesTheAccountButNoCredential() {
    when(repository.find("u4")).thenReturn(Optional.of(user("u4", "d@x.test", true, false)));
    when(repository.recentAudit(eq("u4"), any(Integer.class))).thenReturn(List.of());
    var out = service.export(ME, "u4");
    assertThat(out.toString())
        .doesNotContainIgnoringCase("password")
        .doesNotContainIgnoringCase("hash");
    assertThat(out).containsEntry("format", "krizaka-identity-export/1");
    verify(repository)
        .appendAudit("admin-1", "admin@example.test", "admin.account_exported", "u4", null);
  }

  @Test
  void anUnknownAccountIsNotFound() {
    when(repository.find("nope")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get("nope")).isInstanceOf(UserNotFoundException.class);
  }
}
