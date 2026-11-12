package com.krizaka.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.test.container.AbstractContainerIntegrationTest;
import com.krizaka.users.domain.exception.ServiceAccountProtectedException;
import com.krizaka.users.domain.model.AdminActor;
import com.krizaka.users.domain.ports.inbound.AdminUserService;
import java.sql.DriverManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

/**
 * Erasing a product's {@code svc_} account is refused on the identity context's <b>real</b> tables
 * ({@code db/migration/V1__users_baseline.sql}): the account and its key stay, the refusal is an
 * audit row, an ordinary account is still erased (krizaka-internal#3214).
 */
@SpringBootTest(
    classes = AdminUserEraseIT.Wiring.class,
    properties = {
      "krizaka.messaging.consumer.enabled=false",
      "krizaka.messaging.outbox.enabled=false",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.autoconfigure.exclude=org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration"
    })
class AdminUserEraseIT extends AbstractContainerIntegrationTest {

  private static final AdminActor ADMIN = new AdminActor("admin-1", "admin@example.test");

  @Autowired AdminUserService service;
  @Autowired JdbcTemplate jdbc;

  @BeforeAll
  static void createTheRealTables() throws Exception {
    try (var connection =
        DriverManager.getConnection(
            postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword())) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("/db/migration/V1__users_baseline.sql"));
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

  /** The administration on the real tables: native SQL, so no entity is needed. */
  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude =
          org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration
              .class)
  @ComponentScan(
      basePackages = "com.krizaka.users",
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = FilterType.REGEX,
              pattern =
                  ".*\\.Admin(UserPersistenceProviderImpl|UserRepositoryAdapter|UserServiceImpl)"))
  static class Wiring {

    @Bean
    PersistenceManagedTypes noEntity() {
      return PersistenceManagedTypes.of();
    }
  }
}
