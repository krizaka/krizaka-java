package com.krizaka.users.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.test.container.AbstractContainerIntegrationTest;
import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.ports.inbound.TokenService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The whole identity service, started the way it is deployed (its own {@code application.yml}, the
 * connection built by {@code DataSourceConfig}, JPA, schema untouched, on the real tables of {@code
 * db/migration/V1__users_baseline.sql}), and the administration through its real HTTP chain: an
 * administrator's token opens {@code /api/v1/admin/users}, a user's does not, and every action
 * leaves its audit line. A change of bean or filter that stops the service from starting fails
 * here, not in the deployment (krizaka-users#18, sovkora#users-context-load).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.jpa.hibernate.ddl-auto=none",
      "krizaka.messaging.outbox.enabled=false",
      "krizaka.messaging.consumer.enabled=false",
      "krizaka.users.crypto.key=test-crypto-key-0123456789abcdef!",
      "krizaka.users.crypto.salt=0123456789abcdef",
      "management.tracing.enabled=false",
      "krizaka.users.vault.key=BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc=",
      "spring.security.oauth2.client.registration.github.client-id=gh-test-id",
      "spring.security.oauth2.client.registration.github.client-secret=gh-clear-value-0123"
    })
class IdentityServiceContextIT extends AbstractContainerIntegrationTest {

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired TokenService tokens;

  @DynamicPropertySource
  static void connection(DynamicPropertyRegistry registry) {
    registry.add("krizaka.users-service.datasource.url", () -> postgres().getJdbcUrl());
    registry.add("krizaka.users-service.datasource.username", () -> postgres().getUsername());
    registry.add("krizaka.users-service.datasource.password", () -> postgres().getPassword());
  }

  @BeforeAll
  static void createTheRealTables() throws Exception {
    String sql =
        new String(
            java.util.Objects.requireNonNull(
                    IdentityServiceContextIT.class.getResourceAsStream(
                        "/db/migration/V1__users_baseline.sql"))
                .readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8);
    try (var connection =
            java.sql.DriverManager.getConnection(
                postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword());
        var statement = connection.createStatement()) {
      Matcher tables =
          Pattern.compile("CREATE TABLE (\\w+) \\(.*?\\n\\);", Pattern.DOTALL).matcher(sql);
      while (tables.find()) {
        statement.execute("DROP TABLE IF EXISTS " + tables.group(1) + " CASCADE");
        statement.execute(tables.group());
      }
      statement.execute(
          "INSERT INTO rate_limit_tiers (id, capacity, refill_tokens, refill_seconds) VALUES ('free', 10, 10, 60) ON CONFLICT DO NOTHING");
    }
  }

  private UUID account(String name, boolean admin) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, username, email, enabled) VALUES (?, ?, ?, true)",
        id.toString(),
        name,
        name + "@example.test");
    jdbc.update(
        "INSERT INTO authorities (user_id, authority_name) VALUES (?, 'ROLE_USER')", id.toString());
    if (admin) {
      jdbc.update(
          "INSERT INTO authorities (user_id, authority_name) VALUES (?, 'ROLE_ADMIN')",
          id.toString());
    }
    return id;
  }

  private String token(UUID id, String name, boolean admin) {
    Set<String> authorities = admin ? Set.of("ROLE_USER", "ROLE_ADMIN") : Set.of("ROLE_USER");
    return tokens.issue(
        new User(id, name, name + "@example.test", true, authorities, null, null, null));
  }

  private HttpResponse<String> call(String method, String path, String token, String body)
      throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    builder.header("Content-Type", "application/json");
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void theServiceStartsAsDeployedAndTheAdministrationIsForAdministratorsOnly() throws Exception {
    UUID boss = account("boss", true);
    UUID spare = account("spare", true);
    UUID person = account("person", false);
    String bossToken = token(boss, "boss", true);
    String personToken = token(person, "person", false);

    assertThat(call("GET", "/api/v1/admin/users", null, null).statusCode()).isEqualTo(401);
    assertThat(call("GET", "/api/v1/admin/users", personToken, null).statusCode()).isEqualTo(403);

    var list = call("GET", "/api/v1/admin/users?q=person", bossToken, null);
    assertThat(list.statusCode()).isEqualTo(200);
    assertThat(list.body()).contains("person@example.test").doesNotContain("password");

    assertThat(
            call("POST", "/api/v1/admin/users/" + person + "/suspend", bossToken, null)
                .statusCode())
        .isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT enabled FROM users WHERE id = ?", Boolean.class, person.toString()))
        .isFalse();
    assertThat(
            call("POST", "/api/v1/admin/users/" + boss + "/suspend", bossToken, null).statusCode())
        .isEqualTo(409);
    assertThat(
            call("POST", "/api/v1/admin/users/" + person + "/restore", bossToken, null)
                .statusCode())
        .isEqualTo(200);

    assertThat(
            call("PUT", "/api/v1/admin/users/" + person + "/admin", bossToken, "{\"admin\":true}")
                .statusCode())
        .isEqualTo(200);
    assertThat(
            call("PUT", "/api/v1/admin/users/" + person + "/admin", bossToken, "{\"admin\":false}")
                .statusCode())
        .isEqualTo(200);

    var export = call("GET", "/api/v1/admin/users/" + person + "/export", bossToken, null);
    assertThat(export.statusCode()).isEqualTo(200);
    assertThat(export.body())
        .contains("krizaka-identity-export/1")
        .doesNotContainIgnoringCase("password");

    var wrong =
        call(
            "DELETE",
            "/api/v1/admin/users/" + person,
            bossToken,
            "{\"confirmEmail\":\"nobody@example.test\"}");
    assertThat(wrong.statusCode()).isEqualTo(409);
    assertThat(wrong.body()).contains("CONFIRM_MISMATCH");
    assertThat(
            call(
                    "DELETE",
                    "/api/v1/admin/users/" + person,
                    bossToken,
                    "{\"confirmEmail\":\"person@example.test\"}")
                .statusCode())
        .isEqualTo(204);
    assertThat(call("GET", "/api/v1/admin/users/" + person, bossToken, null).statusCode())
        .isEqualTo(404);

    var audit = call("GET", "/api/v1/admin/users/audit?limit=50", bossToken, null);
    assertThat(audit.statusCode()).isEqualTo(200);
    assertThat(audit.body())
        .contains(
            "admin.user_suspended",
            "admin.user_restored",
            "admin.user_role_changed",
            "admin.account_exported",
            "admin.account_deleted");
    assertThat(spare).isNotNull();
  }

  @Test
  void theIdentitySecretsAreSealedInTheDatabaseAndTheAdminSeesSetOrNotSet() throws Exception {
    UUID boss = account("vault-boss", true);
    UUID person = account("vault-person", false);

    String dump =
        String.join(
            "|", jdbc.queryForList("SELECT t::text FROM social_connection t", String.class));
    assertThat(dump).contains("gh-test-id", "v2.k1.").doesNotContain("gh-clear-value-0123");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM admin_audit WHERE action = 'identity.social_connection_imported'"
                    + " AND target_id = 'github'",
                Integer.class))
        .isEqualTo(1);

    assertThat(call("GET", "/api/v1/admin/social-connections", null, null).statusCode())
        .isEqualTo(401);
    assertThat(
            call(
                    "GET",
                    "/api/v1/admin/social-connections",
                    token(person, "vault-person", false),
                    null)
                .statusCode())
        .isEqualTo(403);
    var list =
        call("GET", "/api/v1/admin/social-connections", token(boss, "vault-boss", true), null);
    assertThat(list.statusCode()).isEqualTo(200);
    assertThat(list.body())
        .contains("\"secretSet\":true", "github")
        .doesNotContain("gh-clear-value-0123", "v2.k1.", "sealed");
  }

  @Test
  void aBearerTokenIsReadFromTheAuthorizationHeaderOnly() throws Exception {
    UUID person = account("header-only", false);
    String token = token(person, "header-only", false);

    assertThat(call("GET", "/api/v1/profile", token, null).statusCode()).isEqualTo(200);
    // The same valid token in the query string authenticates nothing: URLs reach access logs,
    // proxies and browser history, a header does not.
    assertThat(call("GET", "/api/v1/profile?access_token=" + token, null, null).statusCode())
        .isEqualTo(401);
  }
}
