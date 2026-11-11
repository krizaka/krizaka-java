package com.krizaka.users.persistence.infrastructure.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.test.container.AbstractContainerIntegrationTest;
import com.krizaka.users.persistence.domain.model.ApiKeyDto;
import com.krizaka.users.persistence.domain.model.OrganizationDto;
import com.krizaka.users.persistence.domain.model.OrganizationMemberDto;
import com.krizaka.users.persistence.domain.ports.ApiKeyPersistenceProvider;
import com.krizaka.users.persistence.domain.ports.OrganizationPersistenceProvider;
import com.krizaka.users.persistence.infrastructure.adapter.persistence.entity.UserEntity;
import com.krizaka.users.persistence.infrastructure.adapter.persistence.repository.UserRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Organizations against the identity context's <b>real</b> tables ({@code
 * db/migration/V1__users_baseline.sql}): the unique slug, memberships and their roles, the cascade
 * when a user or an organization goes, and the keys of an organization's service accounts only.
 */
@SpringBootTest(
    classes = OrganizationPersistenceIT.App.class,
    properties = {
      "krizaka.users.crypto.key=test-crypto-key-0123456789abcdef!",
      "krizaka.users.crypto.salt=0123456789abcdef",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.flyway.enabled=false",
      "krizaka.messaging.outbox.enabled=false",
      "krizaka.messaging.consumer.enabled=false",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration"
    })
class OrganizationPersistenceIT extends AbstractContainerIntegrationTest {

  @Autowired OrganizationPersistenceProvider organizations;
  @Autowired ApiKeyPersistenceProvider apiKeys;
  @Autowired JdbcTemplate jdbc;

  @BeforeAll
  static void createTheRealTables() throws Exception {
    String sql =
        new String(
            java.util.Objects.requireNonNull(
                    OrganizationPersistenceIT.class.getResourceAsStream(
                        "/db/migration/V1__users_baseline.sql"))
                .readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8);
    String schema = sql.substring(sql.indexOf("CREATE TABLE rate_limit_tiers"));
    try (var connection =
            java.sql.DriverManager.getConnection(
                postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword());
        var statement = connection.createStatement()) {
      statement.execute(
          "DROP TABLE IF EXISTS organization_members, organizations, identity_outbox,"
              + " admin_audit, rate_limits, password_resets, api_keys, user_credentials,"
              + " user_profiles, user_interceptions, verification_tokens, authorities, users,"
              + " rate_limit_tiers CASCADE");
      statement.execute(schema);
    }
  }

  @BeforeEach
  void clean() {
    jdbc.update("DELETE FROM organizations");
    jdbc.update("DELETE FROM api_keys");
    jdbc.update("DELETE FROM users");
    user("owner", "olga");
    user("svc-folioviv", "svc_folioviv");
    user("svc-verikora", "svc_verikora");
    user("person", "pat");
    organizations.save(new OrganizationDto("org-1", "krizaka", "Krizaka", null));
  }

  private void user(String id, String username) {
    jdbc.update(
        "INSERT INTO users (id, username, email, enabled) VALUES (?, ?, ?, TRUE)",
        id,
        username,
        username + "@example.test");
  }

  private static OrganizationMemberDto member(String user, String role) {
    return new OrganizationMemberDto("org-1", user, role, null);
  }

  @Test
  void anOrganizationRoundTripsAndItsSlugIsUnique() {
    assertThat(organizations.findBySlug("krizaka"))
        .hasValueSatisfying(
            o -> {
              assertThat(o.id()).isEqualTo("org-1");
              assertThat(o.name()).isEqualTo("Krizaka");
              assertThat(o.createdAt()).isNotNull();
            });
    assertThat(organizations.findById("org-1")).isPresent();
    assertThat(organizations.findById("nope")).isEmpty();
    var sameSlug = new OrganizationDto("org-2", "krizaka", "Other", null);
    assertThatThrownBy(() -> organizations.save(sameSlug))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aMembershipIsStoredAndItsRoleChangesInPlace() {
    organizations.saveMember(member("owner", "ADMIN"));
    organizations.saveMember(member("owner", "OWNER"));

    assertThat(organizations.findMembers("org-1")).hasSize(1);
    assertThat(organizations.findMember("org-1", "owner"))
        .hasValueSatisfying(
            m -> {
              assertThat(m.role()).isEqualTo("OWNER");
              assertThat(m.createdAt()).isNotNull();
            });
    assertThat(organizations.countByRole("org-1", "OWNER")).isEqualTo(1);
    assertThat(organizations.countByRole("org-1", "ADMIN")).isZero();
    assertThat(organizations.findByMember("owner"))
        .extracting(OrganizationDto::slug)
        .containsExactly("krizaka");
    assertThat(organizations.findByMember("person")).isEmpty();
  }

  @Test
  void aRoleOutsideTheFourIsRefusedByTheDatabase() {
    var boss = member("person", "BOSS");
    assertThatThrownBy(() -> organizations.saveMember(boss))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deletingAMembershipReportsWhatItRemoved() {
    organizations.saveMember(member("person", "MEMBER"));

    assertThat(organizations.deleteMember("org-1", "person")).isEqualTo(1);
    assertThat(organizations.deleteMember("org-1", "person")).isZero();
  }

  @Test
  void onlyTheServiceAccountsKeysAreTheOrganizationsKeys() {
    organizations.saveMember(member("svc-folioviv", "SERVICE"));
    organizations.saveMember(member("person", "MEMBER"));
    apiKeys.save(
        new ApiKeyDto(
            "k1", "svc-folioviv", "prod", "oz_live_aaaa", "hash1", null, null, Set.of("ai:embed")));
    apiKeys.save(
        new ApiKeyDto("k2", "svc-verikora", "prod", "oz_live_bbbb", "hash2", null, null, Set.of()));
    apiKeys.save(
        new ApiKeyDto("k3", "person", "mine", "oz_live_cccc", "hash3", null, null, Set.of()));

    List<ApiKeyDto> keys = organizations.findServiceKeys("org-1");

    assertThat(keys).extracting(ApiKeyDto::id).containsExactly("k1");
    assertThat(keys.getFirst().scopes()).containsExactly("ai:embed");

    organizations.saveMember(member("svc-verikora", "SERVICE"));
    assertThat(organizations.findServiceKeys("org-1"))
        .extracting(ApiKeyDto::id)
        .containsExactlyInAnyOrder("k1", "k2");
  }

  @Test
  void anOrganizationWithoutServiceAccountsHasNoKeys() {
    assertThat(organizations.findServiceKeys("org-1")).isEmpty();
    assertThat(apiKeys.findByUserIds(List.of())).isEmpty();
  }

  @Test
  void removingAUserOrAnOrganizationRemovesTheMemberships() {
    organizations.saveMember(member("person", "MEMBER"));
    organizations.saveMember(member("owner", "OWNER"));

    jdbc.update("DELETE FROM users WHERE id = 'person'");
    assertThat(organizations.findMembers("org-1"))
        .extracting(OrganizationMemberDto::userId)
        .containsExactly("owner");

    jdbc.update("DELETE FROM organizations WHERE id = 'org-1'");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organization_members", Long.class))
        .isZero();
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableJpaRepositories(basePackageClasses = UserRepository.class)
  @EntityScan(basePackageClasses = UserEntity.class)
  @ComponentScan(
      basePackages = "com.krizaka.users.persistence",
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = FilterType.REGEX,
              pattern = ".*\\.(OrganizationPersistenceProviderImpl|ApiKeyPersistenceProviderImpl)"))
  static class App {}
}
