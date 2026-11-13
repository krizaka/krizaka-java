package com.krizaka.users.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA Entity mapping the rate limits configuration table inside the identity domain. */
@Entity
@Table(name = "rate_limits")
public class RateLimitEntity {

  @Id
  @Column(name = "tier_key", length = 50)
  private String tierKey;

  @Column(name = "requests_per_minute", nullable = false)
  private Integer requestsPerMinute;

  @Column(name = "concurrent_jobs", nullable = false)
  private Integer concurrentJobs;

  @Column(name = "daily_requests")
  private Integer dailyRequests;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  public String getTierKey() {
    return tierKey;
  }

  public void setTierKey(String tierKey) {
    this.tierKey = tierKey;
  }

  public Integer getRequestsPerMinute() {
    return requestsPerMinute;
  }

  public void setRequestsPerMinute(Integer requestsPerMinute) {
    this.requestsPerMinute = requestsPerMinute;
  }

  public Integer getConcurrentJobs() {
    return concurrentJobs;
  }

  public void setConcurrentJobs(Integer concurrentJobs) {
    this.concurrentJobs = concurrentJobs;
  }

  public Integer getDailyRequests() {
    return dailyRequests;
  }

  public void setDailyRequests(Integer dailyRequests) {
    this.dailyRequests = dailyRequests;
  }

  public boolean isDefault() {
    return isDefault;
  }

  public void setDefault(boolean isDefault) {
    this.isDefault = isDefault;
  }
}
