package com.krizaka.users.persistence.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.krizaka.users.persistence.domain.model.RateLimitDto;
import com.krizaka.users.persistence.infrastructure.adapter.persistence.entity.RateLimitEntity;
import com.krizaka.users.persistence.infrastructure.adapter.persistence.repository.RateLimitRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RateLimitPersistenceProviderImplTest {

  @Mock private RateLimitRepository repository;

  @InjectMocks private RateLimitPersistenceProviderImpl provider;

  @Test
  @DisplayName("Constructor throws NullPointerException on null repository")
  void constructorValidation() {
    assertThatThrownBy(() -> new RateLimitPersistenceProviderImpl(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("RateLimitRepository cannot be null");
  }

  @Test
  @DisplayName("findById retrieves and maps rate limit entity")
  void findById() {
    RateLimitEntity entity = new RateLimitEntity();
    entity.setTierKey("premium");
    entity.setRequestsPerMinute(100);
    entity.setConcurrentJobs(150);

    when(repository.findById("premium")).thenReturn(Optional.of(entity));

    Optional<RateLimitDto> result = provider.findById("premium");

    assertThat(result).isPresent();
    assertThat(result.get().tierKey()).isEqualTo("premium");
    assertThat(result.get().requestsPerMinute()).isEqualTo(100);
    assertThat(result.get().concurrentJobs()).isEqualTo(150);

    verify(repository).findById("premium");
  }

  @Test
  @DisplayName("findById throws NullPointerException on null key")
  void findByIdNullKey() {
    assertThatThrownBy(() -> provider.findById(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Tier key cannot be null");
  }

  @Test
  @DisplayName("save persists and maps rate limit DTO")
  void save() {
    RateLimitDto dto = new RateLimitDto("free", 30, 45);
    RateLimitEntity entity = RateLimitPersistenceProviderImpl.toEntity(dto);

    when(repository.save(any(RateLimitEntity.class))).thenReturn(entity);

    RateLimitDto result = provider.save(dto);

    assertThat(result).isNotNull();
    assertThat(result.tierKey()).isEqualTo("free");
    assertThat(result.requestsPerMinute()).isEqualTo(30);
    assertThat(result.concurrentJobs()).isEqualTo(45);

    verify(repository).save(any(RateLimitEntity.class));
  }

  @Test
  @DisplayName("save throws NullPointerException on null DTO")
  void saveNullDto() {
    assertThatThrownBy(() -> provider.save(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("RateLimitDto cannot be null");
  }

  @Test
  @DisplayName("findDefaultTierKey returns the is_default tier key")
  void findDefaultTierKey() {
    RateLimitEntity entity = new RateLimitEntity();
    entity.setTierKey("free");
    entity.setDefault(true);
    when(repository.findFirstByIsDefaultTrue()).thenReturn(Optional.of(entity));

    assertThat(provider.findDefaultTierKey()).contains("free");

    verify(repository).findFirstByIsDefaultTrue();
  }

  @Test
  @DisplayName("updateDailyRequests changes only the quota of the stored row")
  void updateDailyRequests() {
    RateLimitEntity entity = new RateLimitEntity();
    entity.setTierKey("service");
    entity.setRequestsPerMinute(1000);
    entity.setConcurrentJobs(20);
    entity.setDefault(false);
    when(repository.findById("service")).thenReturn(Optional.of(entity));
    when(repository.save(entity)).thenReturn(entity);

    RateLimitDto result = provider.updateDailyRequests("service", 50_000);

    assertThat(result.dailyRequests()).isEqualTo(50_000);
    assertThat(entity.getDailyRequests()).isEqualTo(50_000);
    assertThat(entity.getRequestsPerMinute()).isEqualTo(1000);
  }

  @Test
  @DisplayName("findById maps the quota column, null when unset")
  void findByIdMapsDailyRequests() {
    RateLimitEntity entity = new RateLimitEntity();
    entity.setTierKey("service");
    entity.setRequestsPerMinute(1000);
    entity.setConcurrentJobs(20);
    entity.setDailyRequests(7);
    when(repository.findById("service")).thenReturn(Optional.of(entity));

    assertThat(provider.findById("service").orElseThrow().dailyRequests()).isEqualTo(7);
  }
}
