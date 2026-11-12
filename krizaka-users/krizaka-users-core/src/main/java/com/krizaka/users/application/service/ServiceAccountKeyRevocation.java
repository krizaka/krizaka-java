package com.krizaka.users.application.service;

import com.krizaka.users.domain.exception.ServiceAccountProtectedException;
import com.krizaka.users.domain.model.ApiKey;
import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.ports.inbound.OrganizationService;
import com.krizaka.users.domain.ports.outbound.AdminUserRepositoryPort;
import com.krizaka.users.domain.ports.outbound.ApiKeyRepositoryPort;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The one way a key of a product's service account ({@code svc_<product>}) is removed. Every path
 * that revokes such a key (the account itself, an organization manager) goes through here, so the
 * rules are written once: the last active key is never removed (rotation creates the new key first,
 * so the old one is never the last), and each revocation leaves an audit row and a WARN line naming
 * the product (krizaka-internal#3214). The secret is never written, only the id and the display
 * prefix.
 */
@Component
class ServiceAccountKeyRevocation {

  static final String AUDIT_ACTION = "service_account.key_revoked";

  private static final Logger logger = LoggerFactory.getLogger(ServiceAccountKeyRevocation.class);

  private final ApiKeyRepositoryPort apiKeys;
  private final AdminUserRepositoryPort audit;

  ServiceAccountKeyRevocation(ApiKeyRepositoryPort apiKeys, AdminUserRepositoryPort audit) {
    this.apiKeys = Objects.requireNonNull(apiKeys, "apiKeys is required");
    this.audit = Objects.requireNonNull(audit, "audit is required");
  }

  /** Whether the account is a product's service account. */
  static boolean isServiceAccount(User account) {
    return account.username() != null
        && account.username().startsWith(OrganizationService.SERVICE_ACCOUNT_PREFIX);
  }

  /**
   * Revokes {@code keyId} of {@code account}.
   *
   * @return false when the account has no such key
   * @throws ServiceAccountProtectedException when it is the account's last active key
   */
  boolean revoke(User account, String keyId, String actorId, String actorLabel) {
    String ownerId = account.id().toString();
    ApiKey key =
        apiKeys.findByUserId(ownerId).stream()
            .filter(k -> k.id().equals(keyId))
            .findFirst()
            .orElse(null);
    if (key == null) {
      return false;
    }
    String product =
        account.username().substring(OrganizationService.SERVICE_ACCOUNT_PREFIX.length());
    if (apiKeys.deleteUnlessLast(keyId, ownerId) == 0) {
      audit.appendAuditOutcome(
          actorId, actorLabel, AUDIT_ACTION, ownerId, detail(product, key), "refused");
      logger.warn(
          "Refused to revoke the last key {} ({}) of the service account of product {}",
          keyId,
          key.keyPrefix(),
          product);
      throw new ServiceAccountProtectedException(
          ServiceAccountProtectedException.LAST_KEY,
          "The last active key of a service account is replaced, not revoked: create the new key first");
    }
    audit.appendAudit(actorId, actorLabel, AUDIT_ACTION, ownerId, detail(product, key));
    logger.warn(
        "Key {} ({}) of the service account of product {} revoked by {}",
        keyId,
        key.keyPrefix(),
        product,
        actorId);
    return true;
  }

  private static String detail(String product, ApiKey key) {
    return "product=" + product + " key=" + key.id() + " prefix=" + key.keyPrefix();
  }
}
