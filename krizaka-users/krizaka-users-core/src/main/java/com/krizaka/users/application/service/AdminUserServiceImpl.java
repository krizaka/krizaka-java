package com.krizaka.users.application.service;

import com.krizaka.users.domain.exception.AdminActionRefusedException;
import com.krizaka.users.domain.exception.ServiceAccountProtectedException;
import com.krizaka.users.domain.exception.UserNotFoundException;
import com.krizaka.users.domain.model.AdminActor;
import com.krizaka.users.domain.model.AdminAuditEntry;
import com.krizaka.users.domain.model.AdminUserPage;
import com.krizaka.users.domain.model.AdminUserView;
import com.krizaka.users.domain.ports.inbound.AdminUserService;
import com.krizaka.users.domain.ports.inbound.OrganizationService;
import com.krizaka.users.domain.ports.outbound.AdminUserRepositoryPort;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link AdminUserService}: the rules, then the audit line. */
@Service
@Transactional
class AdminUserServiceImpl implements AdminUserService {

  private static final int MAX_PAGE_SIZE = 100;
  private static final int MAX_AUDIT = 500;

  private final AdminUserRepositoryPort repository;

  AdminUserServiceImpl(AdminUserRepositoryPort repository) {
    this.repository = Objects.requireNonNull(repository, "AdminUserRepositoryPort");
  }

  @Override
  @Transactional(readOnly = true)
  public AdminUserPage list(String query, int page, int size) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    return new AdminUserPage(
        repository.search(query, safePage, safeSize), repository.count(query), safePage, safeSize);
  }

  @Override
  @Transactional(readOnly = true)
  public AdminUserView get(String id) {
    return repository.find(id).orElseThrow(() -> new UserNotFoundException(id));
  }

  private AdminUserView doSuspend(AdminActor actor, String id) {
    AdminUserView user = get(id);
    refuseSelf(actor, id);
    if (user.admin() && user.enabled() && repository.countEnabledAdmins() <= 1) {
      throw new AdminActionRefusedException("LAST_ADMIN", "The last administrator keeps the role");
    }
    repository.setEnabled(id, false);
    repository.appendAudit(actor.id(), actor.label(), "admin.user_suspended", id, null);
    return get(id);
  }

  private AdminUserView doRestore(AdminActor actor, String id) {
    get(id);
    repository.setEnabled(id, true);
    repository.appendAudit(actor.id(), actor.label(), "admin.user_restored", id, null);
    return get(id);
  }

  private AdminUserView doSetAdmin(AdminActor actor, String id, boolean admin) {
    AdminUserView user = get(id);
    if (user.admin() == admin) {
      return user;
    }
    refuseSelf(actor, id);
    if (!admin && user.enabled() && repository.countEnabledAdmins() <= 1) {
      throw new AdminActionRefusedException("LAST_ADMIN", "The last administrator keeps the role");
    }
    repository.setAdmin(id, admin);
    repository.appendAudit(
        actor.id(),
        actor.label(),
        "admin.user_role_changed",
        id,
        admin ? "ADMIN granted" : "ADMIN withdrawn");
    return get(id);
  }

  private Map<String, Object> doExport(AdminActor actor, String id) {
    AdminUserView user = get(id);
    List<AdminAuditEntry> trail = repository.recentAudit(id, MAX_AUDIT);
    repository.appendAudit(actor.id(), actor.label(), "admin.account_exported", id, null);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("format", "krizaka-identity-export/1");
    out.put("exportedAt", Instant.now().toString());
    Map<String, Object> account = new LinkedHashMap<>();
    account.put("id", user.id());
    account.put("username", user.username());
    account.put("email", user.email());
    account.put("enabled", user.enabled());
    account.put("provider", user.provider());
    account.put("authorities", user.authorities().stream().sorted().toList());
    account.put("createdAt", user.createdAt() == null ? null : user.createdAt().toString());
    out.put("account", account);
    out.put("audit", trail);
    return out;
  }

  private void doErase(AdminActor actor, String id, String confirmEmail) {
    AdminUserView user = get(id);
    refuseSelf(actor, id);
    // A product's service account holds the key it calls Sovkora with; erasing it (and, by cascade,
    // its keys) is not an admin action (krizaka-internal#3214). No override exists: nobody needs
    // one.
    if (user.username() != null
        && user.username().startsWith(OrganizationService.SERVICE_ACCOUNT_PREFIX)) {
      throw new ServiceAccountProtectedException(
          ServiceAccountProtectedException.ACCOUNT_PROTECTED,
          "The service account of a product is not erased");
    }
    if (user.admin() && user.enabled() && repository.countEnabledAdmins() <= 1) {
      throw new AdminActionRefusedException("LAST_ADMIN", "The last administrator keeps the role");
    }
    String typed = confirmEmail == null ? "" : confirmEmail.trim().toLowerCase(Locale.ROOT);
    if (!typed.equals(user.email().toLowerCase(Locale.ROOT))) {
      throw new AdminActionRefusedException(
          "CONFIRM_MISMATCH", "The e-mail typed does not match the account");
    }
    repository.anonymiseAudit(id);
    repository.delete(id);
    repository.appendAudit(actor.id(), actor.label(), "admin.account_deleted", id, null);
  }

  @Override
  public AdminUserView suspend(AdminActor actor, String id) {
    return audited(actor, "admin.user_suspended", id, () -> doSuspend(actor, id));
  }

  @Override
  public AdminUserView restore(AdminActor actor, String id) {
    return audited(actor, "admin.user_restored", id, () -> doRestore(actor, id));
  }

  @Override
  public AdminUserView setAdmin(AdminActor actor, String id, boolean admin) {
    return audited(actor, "admin.user_role_changed", id, () -> doSetAdmin(actor, id, admin));
  }

  @Override
  public Map<String, Object> export(AdminActor actor, String id) {
    return audited(actor, "admin.account_exported", id, () -> doExport(actor, id));
  }

  @Override
  public void erase(AdminActor actor, String id, String confirmEmail) {
    audited(
        actor,
        "admin.account_deleted",
        id,
        () -> {
          doErase(actor, id, confirmEmail);
          return null;
        });
  }

  /**
   * Runs an administration action and audits what did not happen as well as what did (ADR-085): a
   * refusal (the last administrator, one's own account, a wrong confirmation) is a {@code refused}
   * line, an unknown account a {@code failed} one, each in its own transaction.
   */
  private <T> T audited(
      AdminActor actor, String action, String id, java.util.function.Supplier<T> body) {
    try {
      return body.get();
    } catch (AdminActionRefusedException refused) {
      repository.appendAuditOutcome(
          actor.id(), actor.label(), action, id, refused.code(), "refused");
      throw refused;
    } catch (UserNotFoundException missing) {
      repository.appendAuditOutcome(actor.id(), actor.label(), action, id, "not_found", "failed");
      throw missing;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<AdminAuditEntry> audit(String targetId, int limit) {
    return repository.recentAudit(
        targetId == null || targetId.isBlank() ? null : targetId,
        Math.min(MAX_AUDIT, Math.max(1, limit)));
  }

  private static void refuseSelf(AdminActor actor, String id) {
    if (actor.id().equals(id)) {
      throw new AdminActionRefusedException("SELF", "You cannot do this to your own account");
    }
  }
}
