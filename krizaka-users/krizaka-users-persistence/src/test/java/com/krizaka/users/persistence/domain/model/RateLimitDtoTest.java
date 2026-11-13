package com.krizaka.users.persistence.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RateLimitDtoTest {

  @Test
  void validConstruction_setsAllFields() {
    var dto = new RateLimitDto("free", 60, 2);
    assertEquals("free", dto.tierKey());
    assertEquals(60, dto.requestsPerMinute());
    assertEquals(2, dto.concurrentJobs());
  }

  @Test
  void nullTierKey_throws() {
    assertThrows(NullPointerException.class, () -> new RateLimitDto(null, 60, 2));
  }

  @Test
  void nullRequestsPerMinute_throws() {
    assertThrows(NullPointerException.class, () -> new RateLimitDto("free", null, 2));
  }

  @Test
  void nullConcurrentJobs_throws() {
    assertThrows(NullPointerException.class, () -> new RateLimitDto("free", 60, null));
  }

  @Test
  void dailyRequestsIsAbsentByDefault() {
    assertNull(new RateLimitDto("free", 60, 2).dailyRequests());
  }

  @Test
  void dailyRequestsOutsideItsBoundsIsRefused() {
    assertEquals(1, new RateLimitDto("free", 60, 2, 1).dailyRequests());
    assertEquals(10_000_000, new RateLimitDto("free", 60, 2, 10_000_000).dailyRequests());
    assertThrows(IllegalArgumentException.class, () -> new RateLimitDto("free", 60, 2, 0));
    assertThrows(IllegalArgumentException.class, () -> new RateLimitDto("free", 60, 2, 10_000_001));
  }
}
