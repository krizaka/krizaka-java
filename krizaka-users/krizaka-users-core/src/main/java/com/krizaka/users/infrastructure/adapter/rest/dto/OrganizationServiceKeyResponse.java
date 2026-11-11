package com.krizaka.users.infrastructure.adapter.rest.dto;

import com.krizaka.users.domain.model.OrganizationServiceKey;
import java.time.Instant;
import java.util.Set;

/**
 * A service account's key as the organization's view shows it: never the secret. The usage and the
 * cost of a key are read from the billing usage API per key owner ({@code ownerUserId}).
 *
 * @param id The key id.
 * @param name The label.
 * @param keyPrefix The display prefix.
 * @param ownerUserId The service account's user id.
 * @param ownerUsername The service account's username, such as {@code svc_folioviv}.
 * @param scopes What the key may do.
 * @param createdAt Creation instant.
 * @param lastUsedAt Last use, or {@code null}.
 */
public record OrganizationServiceKeyResponse(
    String id,
    String name,
    String keyPrefix,
    String ownerUserId,
    String ownerUsername,
    Set<String> scopes,
    Instant createdAt,
    Instant lastUsedAt) {

  /** Maps a domain row. */
  public static OrganizationServiceKeyResponse from(OrganizationServiceKey row) {
    return new OrganizationServiceKeyResponse(
        row.key().id(),
        row.key().name(),
        row.key().keyPrefix(),
        row.key().userId(),
        row.ownerUsername(),
        row.key().scopes(),
        row.key().createdAt(),
        row.key().lastUsedAt());
  }
}
