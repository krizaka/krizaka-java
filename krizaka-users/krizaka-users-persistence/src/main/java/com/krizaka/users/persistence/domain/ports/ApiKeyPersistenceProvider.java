package com.krizaka.users.persistence.domain.ports;

import com.krizaka.users.persistence.domain.model.ApiKeyDto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Port interface for managing inbound API key persistence operations. */
public interface ApiKeyPersistenceProvider {

  List<ApiKeyDto> findByUserId(String userId);

  long countByUserId(String userId);

  Optional<ApiKeyDto> findByKeyHash(String keyHash);

  ApiKeyDto save(ApiKeyDto apiKeyDto);

  long deleteByIdAndUserId(String id, String userId);

  /**
   * Deletes a user's key unless it is their last one; the owner's row is locked first, so two
   * concurrent calls cannot each remove one of two keys.
   *
   * @return 1 when deleted, 0 when the key is the owner's last or is not theirs.
   */
  long deleteUnlessLast(String id, String userId);

  void touchLastUsed(String id, Instant lastUsedAt);

  /**
   * Retrieves the API keys owned by any of the given users, most recent first.
   *
   * @param userIds The owning user IDs.
   * @return The keys of those users; empty when no id is given.
   */
  List<ApiKeyDto> findByUserIds(java.util.Collection<String> userIds);
}
