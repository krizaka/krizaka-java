package com.krizaka.users.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.test.net.LoopbackTestServers;
import com.krizaka.users.domain.model.User;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Round-trip of the user directory against a JDK stub of the users service's internal API: snapshot
 * mapping, 404 handling, and the TTL cache collapsing repeated lookups.
 */
class HttpUserDirectoryClientTest {

  private static HttpServer stub;
  private static final AtomicInteger userHits = new AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicReference<String> grantAuth =
      new java.util.concurrent.atomic.AtomicReference<>();
  private static final java.util.concurrent.atomic.AtomicReference<String> useAuth =
      new java.util.concurrent.atomic.AtomicReference<>();
  private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440001";

  @BeforeAll
  static void startStub() throws IOException {
    stub = LoopbackTestServers.httpServer();
    stub.createContext(
        "/api/v1/credentials/openai/grants",
        exchange -> {
          grantAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
          respond(exchange, 201, "{\"grant\":\"g-1\",\"expiresAt\":\"2026-11-03T12:10:00Z\"}");
        });
    stub.createContext(
        "/api/v1/credentials/missing/grants", exchange -> respond(exchange, 404, "{}"));
    stub.createContext(
        "/internal/v1/credentials/openai/use",
        exchange -> {
          useAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          if (body.contains("\"g-1\"") && body.contains("\"run-1\"")) {
            respond(exchange, 200, "{\"apiKey\":\"sk-decrypted\"}");
          } else {
            respond(exchange, 403, "{\"title\":\"Credential grant refused\"}");
          }
        });
    stub.createContext(
        "/internal/v1/users/" + USER_ID + "/profile", exchange -> respond(exchange, 404, "{}"));
    stub.createContext(
        "/internal/v1/users/" + USER_ID,
        exchange -> {
          userHits.incrementAndGet();
          respond(
              exchange,
              200,
              "{\"id\":\""
                  + USER_ID
                  + "\",\"username\":\"admin\",\"email\":\"a@o.com\","
                  + "\"enabled\":true,\"authorities\":[\"ROLE_ADMIN\"],"
                  + "\"preferences\":{\"language\":\"fr\"},"
                  + "\"activeInterceptions\":[],\"rateLimitTier\":\"admin\"}");
        });
    stub.createContext(
        "/internal/v1/tiers/default",
        exchange ->
            respond(
                exchange,
                200,
                "{\"tierKey\":\"free\",\"requestsPerMinute\":60,\"concurrentJobs\":1}"));
    stub.start();
  }

  @AfterAll
  static void stopStub() {
    stub.stop(0);
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  private HttpUserDirectoryClient adapter() {
    return new HttpUserDirectoryClient(
        RestClient.builder(),
        new UsersClientProperties(
            "http://127.0.0.1:" + stub.getAddress().getPort(),
            "krizaka-test-secret-at-least-32-characters!",
            "billing-service",
            Duration.ofMinutes(1)));
  }

  @Test
  @DisplayName("Hydrates the full User from the internal snapshot and caches it")
  void hydratesAndCachesUser() {
    HttpUserDirectoryClient adapter = adapter();
    userHits.set(0);

    User first = adapter.getUser(USER_ID);
    User second = adapter.getUser(USER_ID);

    assertThat(first.username()).isEqualTo("admin");
    assertThat(first.authorities()).containsExactly("ROLE_ADMIN");
    assertThat(first.preferences()).containsEntry("language", "fr");
    assertThat(first.rateLimitTier()).isEqualTo("admin");
    assertThat(second).isEqualTo(first);
    assertThat(userHits.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("A 404 profile resolves to null (provider contract)")
  void missingProfileIsNull() {
    assertThat(adapter().getProfile(USER_ID)).isNull();
  }

  @Test
  @DisplayName("Credentials: a grant on the user's session, then one use on the service token")
  void credentialGrantThenUse() {
    HttpUserDirectoryClient adapter = adapter();

    var grant = adapter.grantCredential("user-session-jwt", "openai", "run-1");
    assertThat(grant).isPresent();
    assertThat(grantAuth.get()).isEqualTo("Bearer user-session-jwt");
    assertThat(grant.get().toString()).doesNotContain("g-1");

    assertThat(adapter.useCredential(grant.get(), "openai", "run-1")).contains("sk-decrypted");
    assertThat(useAuth.get()).startsWith("Bearer ").isNotEqualTo("Bearer user-session-jwt");
    assertThat(adapter.useCredential(grant.get(), "openai", "run-2")).isEmpty();
    assertThat(adapter.grantCredential("user-session-jwt", "missing", "run-1")).isEmpty();
  }

  @Test
  @DisplayName("Default tier resolves through the internal tiers endpoint")
  void defaultTierResolves() {
    assertThat(adapter().getDefaultTierKey()).contains("free");
  }
}
