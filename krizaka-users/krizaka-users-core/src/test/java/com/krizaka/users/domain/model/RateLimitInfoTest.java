package com.krizaka.users.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RateLimitInfoTest {

  @Test
  void validConstruction_setsAllFields() {
    var info = new RateLimitInfo("free", 60, 2);
    assertEquals("free", info.tierKey());
    assertEquals(60, info.requestsPerMinute());
    assertEquals(2, info.concurrentJobs());
  }

  @Test
  void nullTierKey_throws() {
    assertThrows(NullPointerException.class, () -> new RateLimitInfo(null, 60, 2));
  }

  @Test
  void blankTierKey_throws() {
    assertThrows(IllegalArgumentException.class, () -> new RateLimitInfo("  ", 60, 2));
  }

  @Test
  void nullRequestsPerMinute_throws() {
    assertThrows(NullPointerException.class, () -> new RateLimitInfo("free", null, 2));
  }

  @Test
  void nullConcurrentJobs_throws() {
    assertThrows(NullPointerException.class, () -> new RateLimitInfo("free", 60, null));
  }

  @Test
  void dailyRequestsIsAbsentByDefault() {
    assertNull(new RateLimitInfo("free", 60, 2).dailyRequests());
    assertNull(new RateLimitInfo("free", 60, 2, null).dailyRequests());
  }

  @Test
  void dailyRequestsAcceptsItsBounds() {
    assertEquals(1, new RateLimitInfo("free", 60, 2, 1).dailyRequests());
    assertEquals(10_000_000, new RateLimitInfo("free", 60, 2, 10_000_000).dailyRequests());
  }

  @Test
  void dailyRequestsOutsideItsBoundsIsRefused() {
    assertThrows(IllegalArgumentException.class, () -> new RateLimitInfo("free", 60, 2, 0));
    assertThrows(IllegalArgumentException.class, () -> new RateLimitInfo("free", 60, 2, -5));
    assertThrows(
        IllegalArgumentException.class, () -> new RateLimitInfo("free", 60, 2, 10_000_001));
  }
}
