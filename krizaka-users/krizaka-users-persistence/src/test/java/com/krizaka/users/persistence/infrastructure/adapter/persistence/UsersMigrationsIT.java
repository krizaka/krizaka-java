package com.krizaka.users.persistence.infrastructure.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.test.container.AbstractContainerIntegrationTest;
import com.krizaka.test.sql.InitDb;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * The identity schema is code (krizaka-internal#2255): {@code db/migration} applied by Flyway on an
 * empty database creates every table and the rate-limit tiers, as the database's owner role;
 * applied a second time it runs nothing and still validates.
 */
class UsersMigrationsIT extends AbstractContainerIntegrationTest {

  private static final String DATABASE = "users_migrations_check";

  private static String url(String database) {
    String url = postgres().getJdbcUrl();
    return url.substring(0, url.lastIndexOf('/') + 1) + database;
  }

  private static Flyway flyway() {
    return Flyway.configure()
        .dataSource(url(DATABASE), postgres().getUsername(), postgres().getPassword())
        .locations("classpath:db/migration")
        .validateOnMigrate(true)
        .cleanDisabled(true)
        .load();
  }

  private static long count(Statement statement, String sql) throws Exception {
    try (ResultSet rows = statement.executeQuery(sql)) {
      rows.next();
      return rows.getLong(1);
    }
  }

  @Test
  void anEmptyDatabaseGetsTheWholeSchemaAndASecondRunAppliesNothing() throws Exception {
    try (Connection admin =
            DriverManager.getConnection(
                postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword());
        Statement statement = admin.createStatement()) {
      statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
      statement.execute("CREATE DATABASE " + DATABASE);
    }

    assertThat(flyway().migrate().migrationsExecuted)
        .as("first run on an empty database: V1, then V2-V9 (idempotent, no-ops on V1)")
        .isEqualTo(9);
    assertThat(flyway().migrate().migrationsExecuted).as("second run").isZero();
    flyway().validate();

    try (Connection connection =
            DriverManager.getConnection(
                url(DATABASE), postgres().getUsername(), postgres().getPassword());
        Statement statement = connection.createStatement()) {
      assertThat(
              count(
                  statement,
                  "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
                      + " AND table_name <> 'flyway_schema_history'"))
          .as("the context's tables: V1's 14, the 3 of V5-V6 and V8's credential_grants")
          .isEqualTo(18);
      assertThat(count(statement, "SELECT count(*) FROM rate_limits WHERE is_default"))
          .as("the reference catalogue")
          .isEqualTo(1);
      assertThat(
              count(
                  statement,
                  "SELECT count(*) FROM information_schema.tables WHERE table_name = 'organizations'"))
          .as("a table V4 also creates")
          .isEqualTo(1);
      assertThat(count(statement, "SELECT count(*) FROM rate_limits WHERE daily_requests IS NULL"))
          .as("V9: the seeded tiers keep the edge's default quota")
          .isEqualTo(count(statement, "SELECT count(*) FROM rate_limits"));
      statement.execute("UPDATE rate_limits SET daily_requests = 10000000 WHERE tier_key = 'free'");
      for (String bad : new String[] {"0", "10000001"}) {
        assertThatThrownBy(
                () ->
                    statement.execute(
                        "UPDATE rate_limits SET daily_requests = "
                            + bad
                            + " WHERE tier_key = 'free'"))
            .as("V9 CHECK refuses " + bad)
            .isInstanceOf(java.sql.SQLException.class);
      }
    }
  }

  @Test
  void theLocalBootstrapCreatesOnlyTheRoleAndTheDatabase() throws Exception {
    // One source for the schema: a table in the initdb file would be a second copy, and the one a
    // deployed database never gets.
    String initdb =
        Files.readString(
            InitDb.locate(Path.of(System.getProperty("user.dir"))).resolve("10-identity.sql"));
    assertThat(initdb)
        .doesNotContainIgnoringCase("CREATE TABLE")
        .doesNotContainIgnoringCase("INSERT INTO");
  }
}
