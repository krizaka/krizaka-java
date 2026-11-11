package com.krizaka.users.domain.model;

import java.util.Objects;

/**
 * An API key of one of the organization's service accounts, with the account that owns it: the row
 * of the organization's one view of its products' keys (their usage and cost are read per key owner
 * from the billing usage API).
 *
 * @param key The key's metadata (never the secret).
 * @param ownerUsername The service account's username, such as {@code svc_folioviv}.
 */
public record OrganizationServiceKey(ApiKey key, String ownerUsername) {

  /** Compact constructor enforcing fail-fast invariants. */
  public OrganizationServiceKey {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(ownerUsername, "ownerUsername must not be null");
  }
}
