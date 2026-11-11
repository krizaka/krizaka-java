package com.krizaka.billing.service.schema;

import static org.assertj.core.api.Assertions.assertThat;

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
 * The billing schema is code (krizaka-internal#2255): {@code db/migration} applied by Flyway on an
 * empty database creates every table and the reference catalogue, as the database's owner role;
 * applied a second time it runs nothing and still validates.
 */
class BillingMigrationsIT extends AbstractContainerIntegrationTest {

  private static final String DATABASE = "billing_migrations_check";

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
        .as("first run on an empty database: the baseline and the commercial names")
        .isEqualTo(2);
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
          .as("the context's tables")
          .isEqualTo(19);
      assertThat(count(statement, "SELECT count(*) FROM billing_plan WHERE plan_key = 'free'"))
          .as("the reference catalogue")
          .isEqualTo(1);
      assertThat(
              count(
                  statement,
                  "SELECT count(*) FROM information_schema.columns WHERE table_name = 'billing_plan'"
                      + " AND column_name = 'overage_credit_price_cents'"))
          .as("the column dev lacked on 2026-11-02")
          .isEqualTo(1);
      assertThat(
              count(
                  statement,
                  "SELECT (SELECT count(*) FROM billing_price WHERE price_key LIKE 'sovkora.%')"
                      + " + (SELECT count(*) FROM credit_pricebook WHERE model_name LIKE 'sovkora-%')"))
          .as("the seeded keys under the commercial name (V2)")
          .isEqualTo(16);
      assertThat(
              count(
                  statement,
                  "SELECT (SELECT count(*) FROM billing_price WHERE price_key NOT LIKE 'sovkora.%'"
                      + " AND price_key LIKE '%.%' AND split_part(price_key, '.', 1) NOT IN"
                      + " ('plan', 'sovkora'))"))
          .as("no product prefix but the commercial one")
          .isZero();
    }
  }

  @Test
  void theLocalBootstrapCreatesOnlyTheRoleAndTheDatabase() throws Exception {
    // One source for the schema: a table in the initdb file would be a second copy, and the one a
    // deployed database never gets.
    String initdb =
        Files.readString(
            InitDb.locate(Path.of(System.getProperty("user.dir"))).resolve("70-billing.sql"));
    assertThat(initdb)
        .doesNotContainIgnoringCase("CREATE TABLE")
        .doesNotContainIgnoringCase("INSERT INTO");
  }
}
