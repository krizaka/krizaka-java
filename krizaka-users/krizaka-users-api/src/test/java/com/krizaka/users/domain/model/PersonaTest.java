package com.krizaka.users.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PersonaTest {

  @Test
  void freeUser_returnsValidUser() {
    User user = Persona.freeUser();
    assertNotNull(user);
    assertEquals("test-user", user.username());
    assertEquals("test-user@sovkora.com", user.email());
    assertTrue(user.enabled());
    assertEquals("free", user.rateLimitTier());
    assertTrue(user.authorities().contains("ROLE_USER"));
  }

  @Test
  void freeUser_withPreferences_mergesLanguage() {
    var prefs = java.util.Map.<String, Object>of("theme", "dark");
    User user = Persona.freeUser(prefs);
    assertEquals("dark", user.preferences().get("theme"));
    assertEquals("en", user.preferences().get("language"));
  }

  @Test
  void premiumUser_returnsValidUser() {
    User user = Persona.premiumUser();
    assertNotNull(user);
    assertEquals("premium", user.rateLimitTier());
  }

  @Test
  void adminUser_returnsValidAdmin() {
    User user = Persona.adminUser();
    assertNotNull(user);
    assertEquals("admin", user.username());
    assertEquals("unlimited", user.rateLimitTier());
    assertTrue(user.authorities().contains("ROLE_ADMIN"));
    assertTrue(user.authorities().contains("ROLE_USER"));
  }
}
