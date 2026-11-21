package com.krizaka.users.persistence.infrastructure.adapter.persistence.entity;

import com.krizaka.users.persistence.infrastructure.adapter.persistence.converter.JsonMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA Entity mapping the {@code user_profiles} database table. */
@Entity
@Table(name = "user_profiles")
public class UserProfileEntity {

  @Id
  @Column(name = "user_id", length = 255)
  private String userId;

  @Column(name = "theme", length = 50)
  private String theme = "emerald";

  @Column(name = "raw_preferences", columnDefinition = "TEXT")
  @Convert(converter = JsonMapConverter.class)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  private Map<String, Object> rawPreferences;

  /** Default constructor required by JPA/Hibernate. */
  public UserProfileEntity() {
    /* JPA requires no-arg constructor */
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getTheme() {
    return theme;
  }

  public void setTheme(String theme) {
    this.theme = theme;
  }

  public Map<String, Object> getRawPreferences() {
    return rawPreferences == null ? null : new LinkedHashMap<>(rawPreferences);
  }

  public void setRawPreferences(Map<String, Object> rawPreferences) {
    this.rawPreferences = rawPreferences == null ? null : new LinkedHashMap<>(rawPreferences);
  }
}
