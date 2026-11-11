package com.krizaka.users.persistence.infrastructure.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.test.container.AbstractContainerIntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The migration of sovkora#372 ({@code db/migration/V4__organizations.sql}, run on deploy by
 * Flyway) applied to an identity database that predates organizations: it creates the same tables
 * as {@code V1__users_baseline.sql}, seeds the Krizaka organization with the existing {@code svc_*}
 * accounts, and can be applied twice.
 */
class OrganizationMigrationIT extends AbstractContainerIntegrationTest {

  private static final String ORG_BLOCK_START = "-- An organization owns accounts";
  private static final String ORG_BLOCK_END = "CREATE TABLE password_resets";

  private static String migrationV4() throws Exception {
    try (var in =
        OrganizationMigrationIT.class.getResourceAsStream("/db/migration/V4__organizations.sql")) {
      return new String(java.util.Objects.requireNonNull(in).readAllBytes());
    }
  }

  private static Connection connect() throws Exception {
    return DriverManager.getConnection(
        postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword());
  }

  private static void reset(Statement s) throws Exception {
    // A schema of its own: the container is shared with the other integration tests.
    s.execute("DROP SCHEMA IF EXISTS migration_check CASCADE");
    s.execute("CREATE SCHEMA migration_check");
    s.execute("SET search_path TO migration_check");
  }

  private static String identitySql() throws Exception {
    String sql =
        new String(
            java.util.Objects.requireNonNull(
                    OrganizationMigrationIT.class.getResourceAsStream(
                        "/db/migration/V1__users_baseline.sql"))
                .readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8);
    return sql.substring(sql.indexOf("CREATE TABLE rate_limit_tiers"));
  }

  private static String columns(Statement s) throws Exception {
    var rs =
        s.executeQuery(
            "SELECT table_name || '.' || column_name || ':' || data_type || ':' || is_nullable FROM"
                + " information_schema.columns WHERE table_schema = 'migration_check' AND table_name IN ('organizations',"
                + " 'organization_members') ORDER BY 1");
    StringBuilder out = new StringBuilder();
    while (rs.next()) {
      out.append(rs.getString(1)).append('\n');
    }
    return out.toString();
  }

  @Test
  void theMigrationSeedsKrizakaWithTheExistingServiceAccountsAndIsIdempotent() throws Exception {
    String before = identitySql();
    int from = before.indexOf(ORG_BLOCK_START);
    String withoutOrganizations =
        before.substring(0, from) + before.substring(before.indexOf(ORG_BLOCK_END));
    String migration = migrationV4();
    try (Connection c = connect();
        Statement s = c.createStatement()) {
      reset(s);
      s.execute(withoutOrganizations);
      for (String[] u :
          List.of(
              new String[] {"u1", "svc_folioviv"}, new String[] {"u2", "svc_verikora"},
              new String[] {"u3", "oussama"}, new String[] {"u4", "svcxfoo"})) {
        s.execute(
            "INSERT INTO users (id, username, email, enabled) VALUES ('"
                + u[0]
                + "', '"
                + u[1]
                + "', '"
                + u[1]
                + "@x.test', TRUE)");
      }

      s.execute(migration);
      s.execute(migration);

      var rs = s.executeQuery("SELECT slug, name FROM organizations");
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo("krizaka");
      assertThat(rs.getString(2)).isEqualTo("Krizaka");
      assertThat(rs.next()).isFalse();
      var members =
          s.executeQuery("SELECT user_id, role FROM organization_members ORDER BY user_id");
      assertThat(members.next()).isTrue();
      assertThat(members.getString(1)).isEqualTo("u1");
      assertThat(members.getString(2)).isEqualTo("SERVICE");
      assertThat(members.next()).isTrue();
      assertThat(members.getString(1)).isEqualTo("u2");
      assertThat(members.next()).as("neither a person nor svcxfoo is attached").isFalse();
    }
  }

  @Test
  void theMigrationCreatesTheTablesOfTheInstallSchema() throws Exception {
    String full = identitySql();
    String withoutOrganizations =
        full.substring(0, full.indexOf(ORG_BLOCK_START))
            + full.substring(full.indexOf(ORG_BLOCK_END));
    String migration = migrationV4();
    String fromInstall;
    String fromMigration;
    try (Connection c = connect();
        Statement s = c.createStatement()) {
      reset(s);
      s.execute(full);
      fromInstall = columns(s);
      reset(s);
      s.execute(withoutOrganizations);
      s.execute(migration);
      fromMigration = columns(s);
    }
    assertThat(fromInstall).isNotBlank().isEqualTo(fromMigration);
  }

  @Test
  void flywayBaselinesAnExistingDatabaseAndAppliesTheOrganizationsOnceOnDeploy() throws Exception {
    String full = identitySql();
    String withoutOrganizations =
        full.substring(0, full.indexOf(ORG_BLOCK_START))
            + full.substring(full.indexOf(ORG_BLOCK_END));
    try (Connection c = connect();
        Statement s = c.createStatement()) {
      reset(s);
      s.execute(withoutOrganizations);
      s.execute(
          "INSERT INTO users (id, username, email, enabled) VALUES ('u1', 'svc_folioviv',"
              + " 'a@x.test', TRUE)");
    }
    var flyway = flyway();
    assertThat(flyway.migrate().migrationsExecuted)
        .as("V2..V8 on the baselined database")
        .isEqualTo(7);
    assertThat(flyway.migrate().migrationsExecuted).as("a second start does nothing").isZero();
    try (Connection c = connect();
        Statement s = c.createStatement()) {
      s.execute("SET search_path TO migration_check");
      var rs = s.executeQuery("SELECT count(*) FROM organization_members WHERE role = 'SERVICE'");
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt(1)).isEqualTo(1);
    }
  }

  @Test
  void flywayPassesThroughAFreshInstallWithoutChangingIt() throws Exception {
    try (Connection c = connect();
        Statement s = c.createStatement()) {
      reset(s);
      s.execute(identitySql());
      String before = columns(s);
      assertThat(flyway().migrate().migrationsExecuted).isEqualTo(7);
      assertThat(columns(s)).isEqualTo(before);
    }
  }

  private static org.flywaydb.core.Flyway flyway() {
    return org.flywaydb.core.Flyway.configure()
        .dataSource(postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword())
        .schemas("migration_check")
        .defaultSchema("migration_check")
        .locations("classpath:db/migration")
        .baselineOnMigrate(true)
        .baselineVersion("1")
        .load();
  }
}
