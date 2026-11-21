package com.krizaka.users.persistence.infrastructure.adapter.persistence.entity;

import com.krizaka.users.persistence.infrastructure.adapter.persistence.converter.JsonMapConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA Entity mapping the {@code users} database table.
 *
 * <p>Persists core user profile attributes, credentials, and preferences.
 */
@Entity
@Table(name = "users")
public class UserEntity {

  @Id
  @Column(name = "id", length = 255)
  private String id;

  @Column(name = "username", unique = true, nullable = false, length = 100)
  private String username;

  @Column(name = "password_hash", nullable = true, length = 255)
  private String passwordHash;

  @Column(name = "email", nullable = false, length = 255)
  private String email;

  @Column(name = "enabled")
  private Boolean enabled = true;

  @Column(name = "preferences", columnDefinition = "TEXT")
  @Convert(converter = JsonMapConverter.class)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  private Map<String, Object> preferences;

  @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
  private Set<AuthorityEntity> authorities = new HashSet<>();

  @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
  private Set<UserInterceptionEntity> interceptions = new HashSet<>();

  @Column(name = "provider", nullable = false, length = 50)
  private String provider = "local";

  @Column(name = "provider_id", length = 255)
  private String providerId;

  @Column(name = "rate_limit_tier", length = 50)
  private String rateLimitTier;

  @Column(name = "password_changed_at")
  private Instant passwordChangedAt;

  @Column(name = "created_at")
  private Instant createdAt;

  /** Default constructor required by JPA/Hibernate. */
  public UserEntity() {
    /* JPA requires no-arg constructor */
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Map<String, Object> getPreferences() {
    return preferences == null ? null : new LinkedHashMap<>(preferences);
  }

  public void setPreferences(Map<String, Object> preferences) {
    this.preferences = preferences == null ? null : new LinkedHashMap<>(preferences);
  }

  public Set<AuthorityEntity> getAuthorities() {
    return authorities;
  }

  public void setAuthorities(Set<AuthorityEntity> authorities) {
    this.authorities = authorities;
  }

  public Set<UserInterceptionEntity> getInterceptions() {
    return interceptions;
  }

  public void setInterceptions(Set<UserInterceptionEntity> interceptions) {
    this.interceptions = interceptions;
  }

  public String getRateLimitTier() {
    return rateLimitTier;
  }

  public void setRateLimitTier(String rateLimitTier) {
    this.rateLimitTier = rateLimitTier;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getProviderId() {
    return providerId;
  }

  public void setProviderId(String providerId) {
    this.providerId = providerId;
  }

  public Instant getPasswordChangedAt() {
    return passwordChangedAt;
  }

  public void setPasswordChangedAt(Instant passwordChangedAt) {
    this.passwordChangedAt = passwordChangedAt;
  }
}
