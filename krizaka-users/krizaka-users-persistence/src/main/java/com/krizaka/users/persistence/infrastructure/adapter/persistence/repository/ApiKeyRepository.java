package com.krizaka.users.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.users.persistence.infrastructure.adapter.persistence.entity.ApiKeyEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA Repository interface for {@link ApiKeyEntity}. */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, String> {

  /**
   * Retrieves all API keys owned by a user, most recent first.
   *
   * @param userId The owning user ID.
   * @return The user's API key entities.
   */
  List<ApiKeyEntity> findByUserIdOrderByCreatedAtDesc(String userId);

  /**
   * Counts the API keys currently owned by a user (enforces the per-user cap).
   *
   * @param userId The owning user ID.
   * @return The number of active keys.
   */
  long countByUserId(String userId);

  /**
   * Resolves an API key by its SHA-256 hash for bearer authentication.
   *
   * @param keyHash The hashed presented key.
   * @return An Optional containing the matching key entity, if any.
   */
  Optional<ApiKeyEntity> findByKeyHash(String keyHash);

  /**
   * Deletes a user's API key, scoped by owner to prevent cross-user revocation.
   *
   * @param id The key ID.
   * @param userId The owning user ID.
   * @return The number of rows deleted (0 if the key does not belong to the user).
   */
  long deleteByIdAndUserId(String id, String userId);

  /**
   * Takes the row lock of a key's owner, so two revocations of the same account run one after the
   * other.
   *
   * @param userId The owning user ID.
   * @return The owner's ID, if the account exists.
   */
  @Query(value = "SELECT id FROM users WHERE id = :userId FOR UPDATE", nativeQuery = true)
  Optional<String> lockOwner(@Param("userId") String userId);

  /**
   * Deletes a key only when its owner holds another one.
   *
   * @return The number of rows deleted (0 when the key is the owner's last, or not theirs).
   */
  @Modifying
  @Query(
      value =
          "DELETE FROM api_keys WHERE id = :id AND user_id = :userId"
              + " AND (SELECT count(*) FROM api_keys WHERE user_id = :userId) > 1",
      nativeQuery = true)
  int deleteUnlessLast(@Param("id") String id, @Param("userId") String userId);

  /**
   * Stamps the last-used timestamp of a key without a read-before-write (ERR-109).
   *
   * @param id The key ID.
   * @param lastUsedAt The usage instant.
   */
  @Modifying
  @Query("UPDATE ApiKeyEntity a SET a.lastUsedAt = :lastUsedAt WHERE a.id = :id")
  void touchLastUsed(@Param("id") String id, @Param("lastUsedAt") Instant lastUsedAt);

  /**
   * Retrieves the API keys owned by any of the given users, most recent first.
   *
   * @param userIds The owning user IDs.
   * @return The keys of those users.
   */
  List<ApiKeyEntity> findByUserIdInOrderByCreatedAtDesc(Collection<String> userIds);
}
