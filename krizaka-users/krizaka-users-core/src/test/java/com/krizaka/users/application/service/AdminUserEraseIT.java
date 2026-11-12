package com.krizaka.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.test.container.AbstractContainerIntegrationTest;
import com.krizaka.users.domain.exception.ServiceAccountProtectedException;
import com.krizaka.users.domain.model.AdminActor;
import com.krizaka.users.domain.ports.inbound.AdminUserService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

/**
 * Erasing a product's {@code svc_} account is refused on the identity context's <b>real</b> tables
 * ({@code db/migration/V1__users_baseline.sql}): the account and its key stay, the refusal is an
 * audit row, an ordinary account is still erased (krizaka-internal#3214).
 */
@SpringBootTest(
    classes = AdminUserEraseIT.App.class,
    properties = {
      "krizaka.users.crypto.key=test-crypto-key-0123456789abcdef!",
      "krizaka.users.crypto.salt=0123456789abcdef",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.flyway.enabled=false",
      "krizaka.messaging.outbox.enabled=false",
      "krizaka.messaging.consumer.enabled=false",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration,"
          + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
    })
class AdminUserEraseIT extends AbstractContainerIntegrationTest {

  private static final AdminActor ADMIN = new AdminActor("admin-1", "admin@example.test");

  @Autowired AdminUserService service;
  @Autowired JdbcTemplate jdbc;

  @BeforeAll
  static void createTheRealTables() throws Exception {
    String sql =
        new String(
            java.util.Objects.requireNonNull(
                    AdminUserEraseIT.class.getResourceAsStream(
                        "/db/migration/V1__users_baseline.sql"))
                .readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8);
    try (var connection =
            java.sql.DriverManager.getConnection(
                postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword());
        var statement = connection.createStatement()) {
      for (String table :
          new String[] {"rate_limit_tiers", "users", "authorities", "api_keys", "admin_audit"}) {
        Matcher m =
            Pattern.compile("CREATE TABLE " + table + " \\(.*?\\n\\);", Pattern.DOTALL)
                .matcher(sql);
        assertThat(m.find()).as(table + " in V1__users_baseline.sql").isTrue();
        statement.execute("DROP TABLE IF EXISTS " + table + " CASCADE");
        statement.execute(m.group());
      }
    }
  }

  private void user(String id, String username, String email) {
    jdbc.update(
        "INSERT INTO users (id, username, email, enabled) VALUES (?, ?, ?, TRUE)",
        id,
        username,
        email);
    jdbc.update("INSERT INTO authorities (user_id, authority_name) VALUES (?, 'ROLE_USER')", id);
  }

  @Test
  void aProductsServiceAccountIsNotErasedAndItsKeyStays() {
    user("svc-1", "svc_folioviv", "svc@example.test");
    jdbc.update(
        "INSERT INTO api_keys (id, user_id, name, key_prefix, key_hash) VALUES"
            + " ('k1', 'svc-1', 'prod', 'oz_live_abcd', 'hash-k1')");

    assertThatThrownBy(() -> service.erase(ADMIN, "svc-1", "svc@example.test"))
        .isInstanceOfSatisfying(
            ServiceAccountProtectedException.class,
            e -> assertThat(e.code()).isEqualTo("SERVICE_ACCOUNT_PROTECTED"));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = 'svc-1'", Long.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM api_keys WHERE id = 'k1'", Long.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForList(
                "SELECT outcome FROM admin_audit WHERE target_id = 'svc-1'"
                    + " AND action = 'admin.account_deleted'",
                String.class))
        .containsExactly("refused");
  }

  @Test
  void anOrdinaryAccountIsStillErased() {
    user("p-1", "pat", "pat@example.test");

    service.erase(ADMIN, "p-1", "pat@example.test");

    assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = 'p-1'", Long.class))
        .isZero();
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @ComponentScan(
      basePackages = {
        "com.krizaka.users.persistence",
        "com.krizaka.users.application.service",
        "com.krizaka.users.infrastructure.adapter.persistence"
      },
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = FilterType.REGEX,
              pattern =
                  ".*\\.(AdminUserPersistenceProviderImpl|AdminUserRepositoryAdapter|AdminUserServiceImpl)"))
  static class App {

    /** No entity: the administration is native SQL over the identity tables. */
    @org.springframework.context.annotation.Bean
    PersistenceManagedTypes persistenceManagedTypes() {
      return PersistenceManagedTypes.of();
    }
  }
}
