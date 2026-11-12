package com.krizaka.users.domain.ports.outbound;

import com.krizaka.users.domain.model.ApiKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port defining inbound API key persistence operations, isolating the domain from database
 * entities.
 */
public interface ApiKeyRepositoryPort {

  /**
   * Retrieves all API keys owned by a user, most recent first.
   *
   * @param userId The owning user ID.
   * @return The user's API key metadata records.
   */
  List<ApiKey> findByUserId(String userId);

  /**
   * Counts the API keys currently owned by a user.
   *
   * @param userId The owning user ID.
   * @return The number of active keys.
   */
  long countByUserId(String userId);

  /**
   * Resolves an API key by the SHA-256 hash of its plaintext secret.
   *
   * @param keyHash The hashed presented key.
   * @return The matching key metadata, if any.
   */
  Optional<ApiKey> findByKeyHash(String keyHash);

  /**
   * Persists a new API key.
   *
   * @param id The key ID (UUID string).
   * @param userId The owning user ID.
   * @param name The user-supplied label.
   * @param keyPrefix The short display prefix.
   * @param keyHash The SHA-256 hash of the plaintext secret.
   */
  void save(
      String id,
      String userId,
      String name,
      String keyPrefix,
      String keyHash,
      java.util.Set<String> scopes);

  /** Saves an unscoped key (the full rights of its owner). */
  default void save(String id, String userId, String name, String keyPrefix, String keyHash) {
    save(id, userId, name, keyPrefix, keyHash, java.util.Set.of());
  }

  /**
   * Deletes a user's API key, scoped by owner.
   *
   * @param id The key ID.
   * @param userId The owning user ID.
   * @return The number of rows deleted (0 if none matched the owner).
   */
  long deleteByIdAndUserId(String id, String userId);

  /**
   * Deletes a user's API key unless it is their last one (atomic: the owner is locked first).
   *
   * @return 1 when deleted, 0 when the key is the owner's last or is not theirs.
   */
  long deleteUnlessLast(String id, String userId);

  /**
   * Stamps the last-used timestamp of a key.
   *
   * @param id The key ID.
   * @param lastUsedAt The usage instant.
   */
  void touchLastUsed(String id, Instant lastUsedAt);
}
