package com.krizaka.users.infrastructure.adapter.rest.dto;

import static com.krizaka.users.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;

import com.krizaka.users.domain.exception.InvalidRequestException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for the auth request/response DTOs. */
class AuthDtoTest {

  @Test
  @DisplayName("records compare by value")
  void recordsCompareByValue() {
    assertEquals(new LoginRequest(TEST_EMAIL, PASS), new LoginRequest(TEST_EMAIL, PASS));
    assertEquals(new VerifyTokenRequest("abc"), new VerifyTokenRequest("abc"));
  }

  @Nested
  @DisplayName("LoginRequest")
  class LoginRequestTests {

    @Test
    @DisplayName("valid credentials accepted")
    void validCredentials() {
      var req = new LoginRequest(TEST_EMAIL, "pass123");
      assertEquals(TEST_EMAIL, req.email());
      assertEquals("pass123", req.password());
    }

    @Test
    @DisplayName("null email throws InvalidRequestException")
    void nullEmail() {
      assertThrows(InvalidRequestException.class, () -> new LoginRequest(null, PASS));
    }

    @Test
    @DisplayName("blank email throws InvalidRequestException")
    void blankEmail() {
      assertThrows(InvalidRequestException.class, () -> new LoginRequest("  ", PASS));
    }

    @Test
    @DisplayName("null password throws InvalidRequestException")
    void nullPassword() {
      assertThrows(InvalidRequestException.class, () -> new LoginRequest(TEST_EMAIL, null));
    }

    @Test
    @DisplayName("blank password throws InvalidRequestException")
    void blankPassword() {
      assertThrows(InvalidRequestException.class, () -> new LoginRequest(TEST_EMAIL, "  "));
    }
  }

  @Nested
  @DisplayName("OAuthRequest")
  class OAuthRequestTests {

    @Test
    @DisplayName("valid OAuth request accepted")
    void validOAuth() {
      var req = new OAuthRequest("google", "token-123", TEST_EMAIL, "display");
      assertEquals("google", req.provider());
      assertEquals("token-123", req.idToken());
      assertEquals(TEST_EMAIL, req.email());
    }

    @Test
    @DisplayName("null provider throws InvalidRequestException")
    void nullProvider() {
      assertThrows(
          InvalidRequestException.class,
          () -> new OAuthRequest(null, "token", TEST_EMAIL_SHORT, "name"));
    }

    @Test
    @DisplayName("blank provider throws InvalidRequestException")
    void blankProvider() {
      assertThrows(
          InvalidRequestException.class,
          () -> new OAuthRequest("  ", "token", TEST_EMAIL_SHORT, "name"));
    }

    @Test
    @DisplayName("null idToken throws InvalidRequestException")
    void nullIdToken() {
      assertThrows(
          InvalidRequestException.class,
          () -> new OAuthRequest("google", null, TEST_EMAIL_SHORT, "name"));
    }

    @Test
    @DisplayName("blank idToken throws InvalidRequestException")
    void blankIdToken() {
      assertThrows(
          InvalidRequestException.class,
          () -> new OAuthRequest("google", "  ", TEST_EMAIL_SHORT, "name"));
    }
  }

  @Nested
  @DisplayName("VerifyTokenRequest")
  class VerifyTokenRequestTests {

    @Test
    @DisplayName("valid token accepted")
    void validToken() {
      var req = new VerifyTokenRequest("abc-123");
      assertEquals("abc-123", req.token());
    }

    @Test
    @DisplayName("null token throws InvalidRequestException")
    void nullToken() {
      assertThrows(InvalidRequestException.class, () -> new VerifyTokenRequest(null));
    }

    @Test
    @DisplayName("blank token throws InvalidRequestException")
    void blankToken() {
      assertThrows(InvalidRequestException.class, () -> new VerifyTokenRequest("  "));
    }
  }

  @Nested
  @DisplayName("RegisterRequest")
  class RegisterRequestTests {

    @Test
    @DisplayName("valid registration accepted")
    void validRegistration() {
      var req = new RegisterRequest("user", "u@t.com", PASS, LANG_EN);
      assertEquals("user", req.username());
      assertEquals("u@t.com", req.email());
      assertEquals(PASS, req.password());
      assertEquals(LANG_EN, req.language());
    }

    @Test
    @DisplayName("null username throws InvalidRequestException")
    void nullUsername() {
      assertThrows(
          InvalidRequestException.class, () -> new RegisterRequest(null, "u@t.com", PASS, LANG_EN));
    }

    @Test
    @DisplayName("null email throws InvalidRequestException")
    void nullEmail() {
      assertThrows(
          InvalidRequestException.class, () -> new RegisterRequest("user", null, PASS, LANG_EN));
    }

    @Test
    @DisplayName("null password throws InvalidRequestException")
    void nullPassword() {
      assertThrows(
          InvalidRequestException.class,
          () -> new RegisterRequest("user", "u@t.com", null, LANG_EN));
    }
  }

  @Nested
  @DisplayName("UserDescriptor")
  class UserDescriptorTests {

    @Test
    @DisplayName("valid descriptor accepted")
    void validDescriptor() {
      var desc =
          new UserDescriptor(
              "id-123", ADMIN, "a@b.com", List.of(ROLE_USER), Map.of("lang", LANG_EN));
      assertEquals("id-123", desc.id());
      assertEquals(ADMIN, desc.username());
      assertEquals(List.of(ROLE_USER), desc.authorities());
    }

    @Test
    @DisplayName("null id throws NullPointerException")
    void nullId() {
      assertThrows(
          NullPointerException.class,
          () -> new UserDescriptor(null, "name", TEST_EMAIL_SHORT, null, null));
    }

    @Test
    @DisplayName("null username throws NullPointerException")
    void nullUsername() {
      assertThrows(
          NullPointerException.class,
          () -> new UserDescriptor("id", null, TEST_EMAIL_SHORT, null, null));
    }

    @Test
    @DisplayName("null authorities defaults to empty list")
    void nullAuthorities() {
      var desc = new UserDescriptor("id", "name", TEST_EMAIL_SHORT, null, null);
      assertEquals(List.of(), desc.authorities());
    }
  }

  @Nested
  @DisplayName("AuthResponse")
  class AuthResponseTests {

    @Test
    @DisplayName("valid response accepted")
    void validResponse() {
      var resp =
          new AuthResponse(
              "token-123",
              ADMIN,
              "admin@sovkora.com",
              List.of("ROLE_ADMIN"),
              List.of("onboarding"));
      assertEquals("token-123", resp.token());
      assertEquals(ADMIN, resp.username());
      assertEquals("admin@sovkora.com", resp.email());
      assertEquals(List.of("ROLE_ADMIN"), resp.authorities());
      assertEquals(List.of("onboarding"), resp.activeInterceptions());
    }

    @Test
    @DisplayName("null token throws NullPointerException")
    void nullToken() {
      List<String> emptyAuthorities = List.of();
      List<String> emptyInterceptions = List.of();
      assertThrows(
          NullPointerException.class,
          () -> new AuthResponse(null, ADMIN, "a@b.com", emptyAuthorities, emptyInterceptions));
    }

    @Test
    @DisplayName("null activeInterceptions defaults to empty list")
    void nullInterceptions() {
      var resp = new AuthResponse("token", ADMIN, "admin@sovkora.com", List.of("ROLE_ADMIN"), null);
      assertEquals(List.of(), resp.activeInterceptions());
    }
  }
}
