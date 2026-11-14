package com.krizaka.billing.service.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.krizaka.billing.domain.model.EntitlementSnapshot;
import com.krizaka.billing.service.BillingSchema;
import com.krizaka.billing.service.application.service.PackSubscriptionService.PackSubscriptionView;
import com.krizaka.billing.service.domain.exception.PaymentRequiredException;
import com.krizaka.billing.service.domain.model.CatalogPack;
import com.krizaka.billing.service.domain.model.Entitlement;
import com.krizaka.billing.service.domain.model.SubscriptionStatus;
import com.krizaka.messaging.topology.MessagingExchanges;
import com.krizaka.test.container.ServiceRoles;
import com.krizaka.test.sql.InitDb;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.ObjectMapper;

/**
 * Owning a pack, against the <b>real</b> {@code infra/initdb/70-billing.sql}.
 *
 * <p>The claim under test is the one the catalogue alone cannot make: a pack an actor holds
 * actually <b>grants</b> something. Before the actor↔pack link existed, a pack could be authored,
 * priced and displayed while every entitlement it declared stayed unreachable — so the assertions
 * that matter here are the ones that read back through {@link EntitlementService}, not the ones
 * that read back the row that was just written.
 */
class PackSubscriptionIT {

  private static final String BILLING_DB = "krizaka_billing_db";
  private static final String BILLING_ROLE = "krizaka_billing";
  private static final String BILLING_PASSWORD = "krizaka_billing_pass";
  private static final String ADMIN = "550e8400-e29b-41d4-a716-446655440001";

  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("bootstrap")
          .withUsername("postgres")
          .withPassword("postgres")
          .withCopyFileToContainer(
              MountableFile.forHostPath(billingBootstrapScript()),
              "/docker-entrypoint-initdb.d/70-billing.sql");

  private static AnnotationConfigApplicationContext context;
  private static JdbcTemplate jdbcTemplate;
  private static PackPricingService packPricing;
  private static PackSubscriptionService packSubscriptions;
  private static EntitlementService entitlements;

  private String packKey;
  private String actorId;

  private static Path billingBootstrapScript() {
    return InitDb.locate(Path.of(System.getProperty("user.dir"))).resolve("70-billing.sql");
  }

  @BeforeAll
  static void startContainer() {
    if (System.getProperty("api.version") == null && System.getenv("DOCKER_API_VERSION") == null) {
      System.setProperty("api.version", "1.43");
    }
    POSTGRES.start();
    // The initdb file creates this role WITHOUT a password (ADR-035); `sovkora start`
    // applies the ALTER ROLE at runtime, and a hermetic test has to do the same.
    ServiceRoles.assignPassword(POSTGRES, BILLING_ROLE, BILLING_PASSWORD);
    BillingSchema.migrate(POSTGRES, BILLING_ROLE, BILLING_PASSWORD);
    context = new AnnotationConfigApplicationContext(TestWiring.class);
    jdbcTemplate = context.getBean(JdbcTemplate.class);
    packPricing = context.getBean(PackPricingService.class);
    packSubscriptions = context.getBean(PackSubscriptionService.class);
    entitlements = context.getBean(EntitlementService.class);
  }

  @AfterAll
  static void stopContainer() {
    if (context != null) {
      context.close();
    }
    POSTGRES.stop();
  }

  @BeforeEach
  void freshKeys() {
    packKey = "pack-" + UUID.randomUUID().toString().substring(0, 8);
    actorId = UUID.randomUUID().toString();
  }

  private CatalogPack seedPack(long includedCredits, List<Entitlement> grants) {
    return packPricing.save(new CatalogPack(packKey, 4900, includedCredits, true, grants), ADMIN);
  }

  @Test
  @DisplayName("[§14] a pack an actor holds grants its entitlements on top of their plan")
  void packEntitlementsUnionWithThePlan() {
    seedPack(0, List.of(new Entitlement("studio.realestate-reels", "boolean", "true")));

    EntitlementSnapshot before = entitlements.forActor(actorId);
    assertFalse(
        before.entitlements().containsKey("studio.realestate-reels"),
        "the entry plan grants no Studio it did not pay for");

    packSubscriptions.subscribeAsAdmin(actorId, packKey);

    EntitlementSnapshot after = entitlements.forActor(actorId);
    assertEquals("true", after.entitlements().get("studio.realestate-reels"));
    assertTrue(
        after.entitlements().containsKey("capability.chat"),
        "the plan's own matrix survives the union");
  }

  @Test
  @DisplayName(
      "a FREE pack is included for every actor — a fresh account and a service account — with no subscription")
  void aFreePackNeedsNoSubscription() {
    String freeKey = packKey + "-free";
    packPricing.save(
        new CatalogPack(
            freeKey, 0, 0, true, List.of(new Entitlement("studio.free-studio", "boolean", "true"))),
        ADMIN);
    seedPack(0, List.of(new Entitlement("studio.paid-studio", "boolean", "true")));

    for (String actor : List.of(actorId, "sovkora-service-account", "verikora-service-key")) {
      EntitlementSnapshot snapshot = entitlements.forActor(actor);
      assertEquals(
          "true",
          snapshot.entitlements().get("studio.free-studio"),
          actor + " runs a free pack's Studio without subscribing");
      assertFalse(
          snapshot.entitlements().containsKey("studio.paid-studio"),
          "a priced pack still needs its subscription");
    }
  }

  @Test
  @DisplayName("an inactive free pack is not included")
  void anInactiveFreePackIsNotIncluded() {
    String freeKey = packKey + "-off";
    packPricing.save(
        new CatalogPack(
            freeKey, 0, 0, false, List.of(new Entitlement("studio.off-studio", "boolean", "true"))),
        ADMIN);
    assertFalse(entitlements.forActor(actorId).entitlements().containsKey("studio.off-studio"));
  }

  @Test
  @DisplayName("a pack widens the plan but can never narrow it")
  void theUnionOnlyEverWidens() {
    // The plan the actor falls back to grants concurrency.jobs = 1 and capability.chat = true.
    seedPack(
        0,
        List.of(
            new Entitlement("concurrency.jobs", "int", "12"),
            new Entitlement("capability.chat", "boolean", "false")));

    packSubscriptions.subscribeAsAdmin(actorId, packKey);
    EntitlementSnapshot snapshot = entitlements.forActor(actorId);

    assertEquals("12", snapshot.entitlements().get("concurrency.jobs"), "the larger limit wins");
    assertEquals(
        "true",
        snapshot.entitlements().get("capability.chat"),
        "buying an add-on must never take away access the plan granted");
  }

  @Test
  @DisplayName("the pack's included credits land in the PURCHASED bucket, which does not expire")
  void includedCreditsAreCredited() {
    seedPack(5_000, List.of());

    packSubscriptions.subscribeAsAdmin(actorId, packKey);

    Long purchased =
        jdbcTemplate.queryForObject(
            "SELECT balance_purchased FROM credit_wallet WHERE actor_id = ?", Long.class, actorId);
    assertEquals(5_000L, purchased);

    Long entries =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM credit_ledger_entry WHERE actor_id = ? AND entry_type = 'PURCHASE'",
            Long.class,
            actorId);
    assertEquals(1L, entries, "one attributable ledger entry, not a bare balance change");
  }

  @Test
  @DisplayName("subscribing twice neither duplicates the row nor grants the credits again")
  void subscribeIsIdempotent() {
    seedPack(5_000, List.of());

    PackSubscriptionView first = packSubscriptions.subscribeAsAdmin(actorId, packKey);
    PackSubscriptionView second = packSubscriptions.subscribeAsAdmin(actorId, packKey);

    assertEquals(first.id(), second.id(), "the live row is returned, not a second one");
    Long purchased =
        jdbcTemplate.queryForObject(
            "SELECT balance_purchased FROM credit_wallet WHERE actor_id = ?", Long.class, actorId);
    assertEquals(5_000L, purchased, "a double-submitted purchase credits once");
  }

  @Test
  @DisplayName("an actor holds many packs at once — unlike plans, which are exclusive")
  void anActorHoldsManyPacks() {
    seedPack(0, List.of(new Entitlement("studio.first", "boolean", "true")));
    String secondKey = packKey + "-b";
    packPricing.save(
        new CatalogPack(
            secondKey, 0, 0, true, List.of(new Entitlement("studio.second", "boolean", "true"))),
        ADMIN);

    packSubscriptions.subscribeAsAdmin(actorId, packKey);
    packSubscriptions.subscribeAsAdmin(actorId, secondKey);

    assertEquals(2, packSubscriptions.forActor(actorId).size());
    EntitlementSnapshot snapshot = entitlements.forActor(actorId);
    assertEquals("true", snapshot.entitlements().get("studio.first"));
    assertEquals("true", snapshot.entitlements().get("studio.second"));
  }

  @Test
  @DisplayName("giving a pack up withdraws its entitlements but not its credits")
  void cancelRevokesAccessAndKeepsCredits() {
    seedPack(5_000, List.of(new Entitlement("studio.realestate-reels", "boolean", "true")));
    packSubscriptions.subscribeAsAdmin(actorId, packKey);

    assertTrue(packSubscriptions.cancel(actorId, packKey).isPresent());

    EntitlementSnapshot after = entitlements.forActor(actorId);
    assertFalse(after.entitlements().containsKey("studio.realestate-reels"));
    Long purchased =
        jdbcTemplate.queryForObject(
            "SELECT balance_purchased FROM credit_wallet WHERE actor_id = ?", Long.class, actorId);
    assertEquals(5_000L, purchased, "credits already bought are the user's property");
  }

  @Test
  @DisplayName("cancelling frees the key, so the same pack can be bought again")
  void cancelFreesThePartialUniqueIndex() {
    seedPack(0, List.of());
    packSubscriptions.subscribeAsAdmin(actorId, packKey);
    packSubscriptions.cancel(actorId, packKey);

    PackSubscriptionView again = packSubscriptions.subscribeAsAdmin(actorId, packKey);

    assertEquals(SubscriptionStatus.ACTIVE, again.status());
    assertEquals(1, packSubscriptions.forActor(actorId).size(), "only the live one counts");
  }

  @Test
  @DisplayName("a withdrawn pack cannot be bought, but its existing holders keep it")
  void withdrawnPacksAreNotSoldButAreNotRevoked() {
    seedPack(0, List.of(new Entitlement("studio.realestate-reels", "boolean", "true")));
    packSubscriptions.subscribeAsAdmin(actorId, packKey);
    packPricing.withdraw(packKey, ADMIN);

    assertEquals(
        "true",
        entitlements.forActor(actorId).entitlements().get("studio.realestate-reels"),
        "taking an offer off the shelf is not confiscating it");

    String latecomer = UUID.randomUUID().toString();
    assertThrows(
        IllegalStateException.class, () -> packSubscriptions.subscribeAsAdmin(latecomer, packKey));
  }

  @Test
  @DisplayName("subscribing to a pack that does not exist is a miss, not a 500")
  void unknownPacksAreRefused() {
    assertThrows(
        NoSuchElementException.class,
        () -> packSubscriptions.subscribeAsAdmin(actorId, "no-such-pack"));
  }

  @Test
  @DisplayName("every change to what an actor owns is announced, so cached snapshots expire early")
  void changesAreAnnounced() {
    seedPack(0, List.of());

    packSubscriptions.subscribeAsAdmin(actorId, packKey);
    packSubscriptions.cancel(actorId, packKey);

    Long events =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM billing_outbox WHERE aggregate_id = ?"
                + " AND routing_key IN ('evt.pack.subscribed', 'evt.pack.canceled')",
            Long.class,
            actorId);
    assertEquals(2L, events);
  }

  private long activeRows() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM billing_pack_subscription WHERE actor_id = ? AND pack_key = ?",
        Long.class,
        actorId,
        packKey);
  }

  private long credits() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM credit_ledger_entry WHERE actor_id = ?", Long.class, actorId);
  }

  @Test
  @DisplayName("[#269] a priced pack without a settled payment is refused: no row, no credits")
  void pricedPackNeedsASettledPayment() {
    seedPack(5_000, List.of());

    assertThrows(
        PaymentRequiredException.class, () -> packSubscriptions.subscribe(actorId, packKey, null));
    assertThrows(
        PaymentRequiredException.class, () -> packSubscriptions.subscribe(actorId, packKey, " "));
    assertThrows(
        PaymentRequiredException.class,
        () -> packSubscriptions.subscribe(actorId, packKey, "pi_never_settled"));

    assertEquals(0L, activeRows());
    assertEquals(0L, credits());
  }

  @Test
  @DisplayName("[#269] a settled payment unlocks the pack once, for its own actor and pack only")
  void aSettledPaymentIsSingleUseAndBound() {
    seedPack(5_000, List.of());
    String ref = "pi_" + UUID.randomUUID();
    packSubscriptions.recordSettledPayment(ref, actorId, packKey);

    String other = UUID.randomUUID().toString();
    assertThrows(
        PaymentRequiredException.class, () -> packSubscriptions.subscribe(other, packKey, ref));

    PackSubscriptionView bought = packSubscriptions.subscribe(actorId, packKey, ref);
    assertEquals(SubscriptionStatus.ACTIVE, bought.status());
    assertEquals(1L, credits());

    // replay of the same purchase: the live row, no second grant
    assertEquals(bought.id(), packSubscriptions.subscribe(actorId, packKey, ref).id());
    assertEquals(1L, credits());

    // the payment cannot buy the pack again after cancelling
    packSubscriptions.cancel(actorId, packKey);
    assertThrows(
        PaymentRequiredException.class, () -> packSubscriptions.subscribe(actorId, packKey, ref));
  }

  @Test
  @DisplayName("[#269] a payment recorded for another pack does not unlock this one")
  void aPaymentIsBoundToItsPack() {
    seedPack(0, List.of());
    String otherPack = packKey + "-other";
    packPricing.save(new CatalogPack(otherPack, 4900, 0, true, List.of()), ADMIN);
    String ref = "pi_" + UUID.randomUUID();
    packSubscriptions.recordSettledPayment(ref, actorId, otherPack);

    assertThrows(
        PaymentRequiredException.class, () -> packSubscriptions.subscribe(actorId, packKey, ref));
    assertEquals(0L, activeRows());
  }

  @Test
  @DisplayName("[#269] one settled payment racing on two requests buys exactly one pack")
  void aPaymentRacesToOneSubscription() throws Exception {
    seedPack(5_000, List.of());
    String ref = "pi_" + UUID.randomUUID();
    packSubscriptions.recordSettledPayment(ref, actorId, packKey);

    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var go = new java.util.concurrent.CountDownLatch(1);
      var a =
          pool.submit(
              () -> {
                go.await();
                return packSubscriptions.subscribe(actorId, packKey, ref);
              });
      var b =
          pool.submit(
              () -> {
                go.await();
                return packSubscriptions.subscribe(actorId, packKey, ref);
              });
      go.countDown();
      assertEquals(a.get().id(), b.get().id());
    } finally {
      pool.shutdownNow();
    }
    assertEquals(1L, activeRows());
    assertEquals(1L, credits());
  }

  @Test
  @DisplayName("[#269] a free pack needs no payment")
  void aFreePackNeedsNoPayment() {
    packPricing.save(new CatalogPack(packKey, 0, 100, true, List.of()), ADMIN);

    assertEquals(
        SubscriptionStatus.ACTIVE, packSubscriptions.subscribe(actorId, packKey, null).status());
  }

  @Test
  @DisplayName("[#269] the admin path grants a priced pack with no payment")
  void theAdminPathNeedsNoPayment() {
    seedPack(0, List.of());

    assertEquals(
        SubscriptionStatus.ACTIVE, packSubscriptions.subscribeAsAdmin(actorId, packKey).status());
  }

  /** Minimal transactional wiring: real {@code @Transactional} proxies, no web/security/AMQP. */
  @Configuration
  // Class proxies, as Spring Boot creates them in production: a service that implements a port
  // (OutboxService is an OutboxStore) must still be injectable by its class.
  @EnableTransactionManagement(proxyTargetClass = true)
  static class TestWiring {

    @Bean
    DataSource dataSource() {
      HikariDataSource dataSource = new HikariDataSource();
      dataSource.setJdbcUrl(
          "jdbc:postgresql://"
              + POSTGRES.getHost()
              + ":"
              + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
              + "/"
              + BILLING_DB);
      dataSource.setUsername(BILLING_ROLE);
      dataSource.setPassword(BILLING_PASSWORD);
      dataSource.setDriverClassName("org.postgresql.Driver");
      return dataSource;
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
      return new JdbcTemplate(dataSource);
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    BillingVersionHistoryService billingVersionHistoryService(JdbcTemplate jdbcTemplate) {
      return new BillingVersionHistoryService(jdbcTemplate, new ObjectMapper());
    }

    @Bean
    BillingConfigurationService billingConfigurationService(
        JdbcTemplate jdbcTemplate, BillingVersionHistoryService versionHistoryService) {
      return new BillingConfigurationService(jdbcTemplate, versionHistoryService);
    }

    @Bean
    PricingService pricingService(JdbcTemplate jdbcTemplate) {
      return new PricingService(jdbcTemplate);
    }

    @Bean
    OutboxService outboxService(JdbcTemplate jdbcTemplate) {
      return new OutboxService(jdbcTemplate, new ObjectMapper(), MessagingExchanges.defaults());
    }

    @Bean
    CreditRefusalService creditRefusalService(JdbcTemplate jdbcTemplate) {
      return new CreditRefusalService(jdbcTemplate);
    }

    @Bean
    CreditLedgerService creditLedgerService(
        JdbcTemplate jdbcTemplate,
        PricingService pricingService,
        BillingConfigurationService configurationService,
        OutboxService outboxService,
        CreditRefusalService creditRefusalService) {
      return new CreditLedgerService(
          jdbcTemplate,
          pricingService,
          configurationService,
          outboxService,
          creditRefusalService,
          new SimpleMeterRegistry());
    }

    @Bean
    EntitlementService entitlementService(
        JdbcTemplate jdbcTemplate, CreditLedgerService creditLedgerService) {
      return new EntitlementService(jdbcTemplate, creditLedgerService);
    }

    @Bean
    PackPricingService packPricingService(
        JdbcTemplate jdbcTemplate, BillingVersionHistoryService versionHistoryService) {
      return new PackPricingService(jdbcTemplate, versionHistoryService);
    }

    @Bean
    PackSubscriptionService packSubscriptionService(
        JdbcTemplate jdbcTemplate,
        PackPricingService packPricingService,
        CreditLedgerService creditLedgerService,
        OutboxService outboxService) {
      return new PackSubscriptionService(
          jdbcTemplate, packPricingService, creditLedgerService, outboxService);
    }
  }
}
