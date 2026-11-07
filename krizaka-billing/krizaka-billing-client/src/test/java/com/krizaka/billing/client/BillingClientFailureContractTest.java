package com.krizaka.billing.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.krizaka.billing.client.StubBillingServer.Received;
import com.krizaka.billing.client.StubBillingServer.Reply;
import com.krizaka.billing.domain.exception.InsufficientCreditsException;
import com.krizaka.billing.domain.exception.PackProvisioningException;
import com.krizaka.billing.domain.model.BillableCapability;
import com.krizaka.billing.domain.model.BillableUnit;
import com.krizaka.billing.domain.model.ConsumptionReport;
import com.krizaka.billing.domain.model.CreditHoldCommand;
import com.krizaka.billing.domain.model.CreditHoldResponse;
import com.krizaka.billing.domain.model.EntitlementGrant;
import com.krizaka.billing.domain.model.EntitlementSnapshot;
import com.krizaka.billing.domain.model.MeteredStep;
import com.krizaka.billing.domain.model.PackPrice;
import com.krizaka.billing.domain.model.PackProvision;
import com.krizaka.billing.domain.model.SettleCreditCommand;
import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import com.krizaka.billing.domain.port.EntitlementProvider;
import com.krizaka.billing.domain.port.PackPricingClient;
import com.krizaka.billing.domain.port.PackProvisioningClient;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins what each billing adapter does on the wire and how it fails, through the domain ports only,
 * against a stub HTTP server and the auto-configuration the SPI declares. It names no adapter class
 * and no HTTP client, so it holds unchanged while the adapters are rebuilt on another HTTP stack
 * (krizaka-internal#2724).
 *
 * <p>Fail modes pinned (ADR-036 invariant 3): provisioning fails hard; withdraw is best-effort;
 * pricing degrades to an empty table; entitlements fail open; a credit hold refuses on 402 and
 * propagates every other failure; settle and release only log.
 */
class BillingClientFailureContractTest {

  private static final String SECRET = "sovkora-test-secret-at-least-32-characters!";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private StubBillingServer billing;
  private ListAppender<ILoggingEvent> logs;
  private Logger rootLogger;

  @BeforeEach
  void startStub() {
    billing = new StubBillingServer();
    rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    logs = new ListAppender<>();
    logs.start();
    rootLogger.addAppender(logs);
  }

  @AfterEach
  void stopStub() {
    rootLogger.detachAppender(logs);
    billing.close();
  }

  // ---- provisioning: fails hard ------------------------------------------------------------

  @Test
  @DisplayName("provision PUTs the pack with a service token")
  void provisionWritesThePack() {
    run(
        billing.baseUrl(),
        context -> {
          PackProvisioningClient client = context.getBean(PackProvisioningClient.class);

          client.provision(
              new PackProvision(
                  "realestate-studio",
                  1900,
                  500,
                  true,
                  List.of(new EntitlementGrant("capability.studio", "BOOLEAN", "true"))));

          Received request = billing.only();
          assertThat(request.method()).isEqualTo("PUT");
          assertThat(request.path()).isEqualTo("/internal/v1/billing/packs/realestate-studio");
          assertThat(request.authorization()).startsWith("Bearer ");
          JsonNode body = JSON.readTree(request.body());
          assertThat(body.path("packKey").asString()).isEqualTo("realestate-studio");
          assertThat(body.path("priceCents").asInt()).isEqualTo(1900);
          assertThat(body.path("includedCredits").asLong()).isEqualTo(500);
          assertThat(body.path("isActive").asBoolean()).isTrue();
          assertThat(body.path("entitlements")).hasSize(1);
          assertThat(body.path("entitlements").get(0).path("key").asString())
              .isEqualTo("capability.studio");
          assertThat(body.path("entitlements").get(0).path("valueType").asString())
              .isEqualTo("BOOLEAN");
        });
  }

  @Test
  @DisplayName("provision throws PackProvisioningException when billing answers an error")
  void provisionFailsHardOnAnErrorStatus() {
    billing.replyWith(Reply.json(500, "{}"));
    run(
        billing.baseUrl(),
        context ->
            assertThatThrownBy(
                    () -> context.getBean(PackProvisioningClient.class).provision(pack()))
                .isInstanceOf(PackProvisioningException.class)
                .hasMessageContaining("realestate-studio")
                .hasCauseInstanceOf(RestClientException.class));
  }

  @Test
  @DisplayName("provision throws PackProvisioningException when billing is unreachable")
  void provisionFailsHardWhenUnreachable() {
    run(
        unreachableUrl(),
        context ->
            assertThatThrownBy(
                    () -> context.getBean(PackProvisioningClient.class).provision(pack()))
                .isInstanceOf(PackProvisioningException.class)
                .hasCauseInstanceOf(RestClientException.class));
  }

  @Test
  @DisplayName("withdraw DELETEs the pack and is true on a 2xx")
  void withdrawIsTrueOnSuccess() {
    run(
        billing.baseUrl(),
        context -> {
          assertThat(context.getBean(PackProvisioningClient.class).withdraw("realestate-studio"))
              .isTrue();

          Received request = billing.only();
          assertThat(request.method()).isEqualTo("DELETE");
          assertThat(request.path()).isEqualTo("/internal/v1/billing/packs/realestate-studio");
          assertThat(request.authorization()).startsWith("Bearer ");
        });
  }

  @Test
  @DisplayName("withdraw is false, without throwing, on a non-2xx")
  void withdrawIsFalseOnANon2xx() {
    billing.replyWith(Reply.json(404, "{}"));
    run(
        billing.baseUrl(),
        context ->
            assertThat(context.getBean(PackProvisioningClient.class).withdraw("realestate-studio"))
                .isFalse());
  }

  @Test
  @DisplayName("withdraw is false and logs an ERROR when billing is unreachable")
  void withdrawIsFalseAndLoudWhenUnreachable() {
    run(
        unreachableUrl(),
        context -> {
          assertThat(context.getBean(PackProvisioningClient.class).withdraw("realestate-studio"))
              .isFalse();

          assertThat(logs.list)
              .anyMatch(
                  event ->
                      event.getLevel() == Level.ERROR
                          && event.getFormattedMessage().contains("realestate-studio"));
        });
  }

  @Test
  @DisplayName("withdraw of a blank key calls nothing")
  void withdrawBlankCallsNothing() {
    run(
        billing.baseUrl(),
        context -> {
          assertThat(context.getBean(PackProvisioningClient.class).withdraw(" ")).isFalse();
          assertThat(context.getBean(PackProvisioningClient.class).withdraw(null)).isFalse();
          assertThat(billing.requests()).isEmpty();
        });
  }

  // ---- pricing: degrades --------------------------------------------------------------------

  @Test
  @DisplayName("prices reads the whole table in one GET, filters it, and caches it")
  void pricesAreReadOnceAndCached() {
    billing.replyWith(
        Reply.json(
            200,
            "[{\"packKey\":\"a\",\"priceCents\":100,\"includedCredits\":5,\"active\":true},"
                + "{\"packKey\":\"b\",\"priceCents\":200,\"includedCredits\":7,\"active\":false}]"));
    run(
        billing.baseUrl(),
        context -> {
          PackPricingClient client = context.getBean(PackPricingClient.class);

          Map<String, PackPrice> prices = client.prices(Set.of("a", "unknown"));
          client.prices(Set.of("b"));

          assertThat(prices).containsOnlyKeys("a");
          assertThat(prices.get("a")).isEqualTo(new PackPrice("a", 100, 5, true));
          Received request = billing.only();
          assertThat(request.method()).isEqualTo("GET");
          assertThat(request.path()).isEqualTo("/api/v1/billing/packs/prices");
          assertThat(request.authorization()).startsWith("Bearer ");
        });
  }

  @Test
  @DisplayName("prices degrade to an empty table on an error, and do not hammer billing")
  void pricesDegradeOnAnErrorStatus() {
    billing.replyWith(Reply.json(500, "{}"));
    run(
        billing.baseUrl(),
        context -> {
          PackPricingClient client = context.getBean(PackPricingClient.class);

          assertThat(client.prices(Set.of("a"))).isEmpty();
          assertThat(client.prices(Set.of("a"))).isEmpty();

          assertThat(billing.requests()).hasSize(1);
          assertThat(logs.list).anyMatch(event -> event.getLevel() == Level.ERROR);
        });
  }

  @Test
  @DisplayName("prices degrade to an empty table when billing is unreachable")
  void pricesDegradeWhenUnreachable() {
    run(
        unreachableUrl(),
        context ->
            assertThat(context.getBean(PackPricingClient.class).prices(Set.of("a"))).isEmpty());
  }

  @Test
  @DisplayName("prices degrade to an empty table on a payload the domain refuses")
  void pricesDegradeOnAnInvalidPayload() {
    billing.replyWith(
        Reply.json(
            200, "[{\"packKey\":\"a\",\"priceCents\":-1,\"includedCredits\":5,\"active\":true}]"));
    run(
        billing.baseUrl(),
        context ->
            assertThat(context.getBean(PackPricingClient.class).prices(Set.of("a"))).isEmpty());
  }

  @Test
  @DisplayName("prices of no pack ask billing nothing")
  void pricesOfNothingMakeNoCall() {
    run(
        billing.baseUrl(),
        context -> {
          assertThat(context.getBean(PackPricingClient.class).prices(Set.of())).isEmpty();
          assertThat(context.getBean(PackPricingClient.class).prices(null)).isEmpty();
          assertThat(billing.requests()).isEmpty();
        });
  }

  // ---- entitlements: fail open --------------------------------------------------------------

  @Test
  @DisplayName("entitlements are read with one GET per actor and cached to their own expiry")
  void entitlementsAreReadAndCached() {
    billing.replyWith(
        Reply.json(
            200,
            "{\"actorId\":\"actor-1\",\"planKey\":\"premium\","
                + "\"entitlements\":{\"capability.chat\":\"true\",\"limit.projects\":\"12\"},"
                + "\"availableCredits\":340,\"expiresAt\":\"2099-01-01T00:00:00Z\"}"));
    run(
        billing.baseUrl(),
        context -> {
          EntitlementProvider provider = context.getBean(EntitlementProvider.class);

          EntitlementSnapshot first = provider.forActor("actor-1");
          EntitlementSnapshot second = provider.forActor("actor-1");

          assertThat(first.resolved()).isTrue();
          assertThat(first.planKey()).isEqualTo("premium");
          assertThat(first.allows("capability.chat")).isTrue();
          assertThat(first.limit("limit.projects", 0)).isEqualTo(12);
          assertThat(first.availableCredits()).isEqualTo(340);
          assertThat(second).isEqualTo(first);
          Received request = billing.only();
          assertThat(request.method()).isEqualTo("GET");
          assertThat(request.path()).isEqualTo("/internal/v1/billing/entitlements/actor-1");
          assertThat(request.authorization()).startsWith("Bearer ");
        });
  }

  @Test
  @DisplayName("entitlements fail open (unresolved) on an error status")
  void entitlementsFailOpenOnAnErrorStatus() {
    billing.replyWith(Reply.json(503, "{}"));
    run(
        billing.baseUrl(),
        context -> {
          EntitlementSnapshot snapshot =
              context.getBean(EntitlementProvider.class).forActor("actor-1");

          assertThat(snapshot.resolved()).isFalse();
          assertThat(snapshot.actorId()).isEqualTo("actor-1");
          assertThat(logs.list).anyMatch(event -> event.getLevel() == Level.ERROR);
        });
  }

  @Test
  @DisplayName("entitlements fail open (unresolved) when billing is unreachable")
  void entitlementsFailOpenWhenUnreachable() {
    run(
        unreachableUrl(),
        context ->
            assertThat(context.getBean(EntitlementProvider.class).forActor("actor-1").resolved())
                .isFalse());
  }

  @Test
  @DisplayName("entitlements fail open (unresolved) on a payload the domain refuses")
  void entitlementsFailOpenOnAnInvalidPayload() {
    billing.replyWith(
        Reply.json(
            200,
            "{\"actorId\":\"actor-1\",\"planKey\":\" \",\"entitlements\":{},"
                + "\"availableCredits\":0,\"expiresAt\":\"2099-01-01T00:00:00Z\"}"));
    run(
        billing.baseUrl(),
        context ->
            assertThat(context.getBean(EntitlementProvider.class).forActor("actor-1").resolved())
                .isFalse());
  }

  @Test
  @DisplayName("entitlements fail open (unresolved) on an empty body")
  void entitlementsFailOpenOnAnEmptyBody() {
    run(
        billing.baseUrl(),
        context ->
            assertThat(context.getBean(EntitlementProvider.class).forActor("actor-1").resolved())
                .isFalse());
  }

  // ---- credit authorization -----------------------------------------------------------------

  @Test
  @DisplayName("hold POSTs the command and returns billing's answer")
  void holdPostsTheCommand() {
    billing.replyWith(
        Reply.json(
            200,
            "{\"holdId\":\"hold-1\",\"granted\":true,\"dryRun\":false,\"estimatedCredits\":360,"
                + "\"balanceAfterHold\":40,\"pricebookVersion\":3}"));
    run(
        billing.baseUrl(),
        context -> {
          CreditHoldResponse response =
              context.getBean(CreditAuthorizationClient.class).hold(holdCommand());

          assertThat(response).isEqualTo(new CreditHoldResponse("hold-1", true, false, 360, 40, 3));
          Received request = billing.only();
          assertThat(request.method()).isEqualTo("POST");
          assertThat(request.path()).isEqualTo("/internal/v1/billing/credits/holds");
          assertThat(request.authorization()).startsWith("Bearer ");
          JsonNode body = JSON.readTree(request.body());
          assertThat(body.path("actorId").asString()).isEqualTo("actor-1");
          assertThat(body.path("capability").asString()).isEqualTo("VIDEO");
          assertThat(body.path("correlationId").asString()).isEqualTo("correlation-1");
          assertThat(body.path("estimatedCredits").asLong()).isEqualTo(360);
        });
  }

  @Test
  @DisplayName("hold carries billing's structured refusal on a 402")
  void holdRefusalCarriesTheShortfall() {
    billing.replyWith(
        Reply.json(
            402,
            "{\"status\":\"INSUFFICIENT\",\"capability\":\"VIDEO\",\"required\":360,"
                + "\"balance\":12,\"remedies\":[\"TOP_UP\",\"UPGRADE\"]}"));
    run(
        billing.baseUrl(),
        context ->
            assertThatThrownBy(
                    () -> context.getBean(CreditAuthorizationClient.class).hold(holdCommand()))
                .isInstanceOfSatisfying(
                    InsufficientCreditsException.class,
                    refusal -> {
                      assertThat(refusal.actorId()).isEqualTo("actor-1");
                      assertThat(refusal.capability()).isEqualTo(BillableCapability.VIDEO);
                      assertThat(refusal.required()).isEqualTo(360);
                      assertThat(refusal.available()).isEqualTo(12);
                      assertThat(refusal.remedies()).containsExactly("TOP_UP", "UPGRADE");
                    }));
  }

  @Test
  @DisplayName("hold on a 402 without a body still refuses, with the estimate and no balance")
  void holdRefusalWithoutABody() {
    billing.replyWith(new Reply(402, ""));
    run(
        billing.baseUrl(),
        context ->
            assertThatThrownBy(
                    () -> context.getBean(CreditAuthorizationClient.class).hold(holdCommand()))
                .isInstanceOfSatisfying(
                    InsufficientCreditsException.class,
                    refusal -> {
                      assertThat(refusal.required()).isEqualTo(360);
                      assertThat(refusal.available()).isZero();
                      assertThat(refusal.remedies()).isEmpty();
                    }));
  }

  @Test
  @DisplayName("hold propagates any other error status")
  void holdPropagatesAServerError() {
    billing.replyWith(Reply.json(500, "{}"));
    run(
        billing.baseUrl(),
        context ->
            assertThatThrownBy(
                    () -> context.getBean(CreditAuthorizationClient.class).hold(holdCommand()))
                .isInstanceOf(RestClientException.class)
                .isNotInstanceOf(InsufficientCreditsException.class));
  }

  @Test
  @DisplayName("hold propagates a transport failure")
  void holdPropagatesATransportFailure() {
    run(
        unreachableUrl(),
        context ->
            assertThatThrownBy(
                    () -> context.getBean(CreditAuthorizationClient.class).hold(holdCommand()))
                .isInstanceOf(RestClientException.class));
  }

  @Test
  @DisplayName("settle POSTs the command; a failure is logged, never thrown")
  void settleIsBestEffort() {
    run(
        billing.baseUrl(),
        context -> {
          CreditAuthorizationClient client = context.getBean(CreditAuthorizationClient.class);
          SettleCreditCommand command =
              new SettleCreditCommand("hold-1", BillableUnit.CALL, new BigDecimal("2"), "key-1");

          client.settle(command);

          Received request = billing.only();
          assertThat(request.method()).isEqualTo("POST");
          assertThat(request.path()).isEqualTo("/internal/v1/billing/credits/hold-1/settle");
          JsonNode body = JSON.readTree(request.body());
          assertThat(body.path("holdId").asString()).isEqualTo("hold-1");
          assertThat(body.path("unit").asString()).isEqualTo("CALL");
          assertThat(body.path("idempotencyKey").asString()).isEqualTo("key-1");
          billing.replyWith(Reply.json(500, "{}"));
          assertThatCode(() -> client.settle(command)).doesNotThrowAnyException();
        });
    run(
        unreachableUrl(),
        context ->
            assertThatCode(
                    () ->
                        context
                            .getBean(CreditAuthorizationClient.class)
                            .settle(
                                new SettleCreditCommand(
                                    "hold-1", BillableUnit.CALL, BigDecimal.ONE, "key-1")))
                .doesNotThrowAnyException());
  }

  @Test
  @DisplayName("settleMeasured POSTs the report with its key; a failure is logged, never thrown")
  void settleMeasuredIsBestEffort() {
    run(
        billing.baseUrl(),
        context -> {
          CreditAuthorizationClient client = context.getBean(CreditAuthorizationClient.class);

          client.settleMeasured("hold-1", report(), "key-1");

          Received request = billing.only();
          assertThat(request.method()).isEqualTo("POST");
          assertThat(request.path())
              .isEqualTo("/internal/v1/billing/credits/hold-1/settle-measured");
          JsonNode body = JSON.readTree(request.body());
          assertThat(body.path("idempotencyKey").asString()).isEqualTo("key-1");
          assertThat(body.path("consumption").path("tokens").asLong()).isEqualTo(4000);
          assertThat(body.path("consumption").path("gpuSeconds").decimalValue())
              .isEqualByComparingTo("1.5");
          billing.replyWith(Reply.json(500, "{}"));
          assertThatCode(() -> client.settleMeasured("hold-1", report(), "key-1"))
              .doesNotThrowAnyException();
        });
  }

  @Test
  @DisplayName("settleAggregate returns billing's boolean, and false on any failure")
  void settleAggregateReturnsFalseOnFailure() {
    billing.replyWith(Reply.json(200, "true"));
    run(
        billing.baseUrl(),
        context -> {
          CreditAuthorizationClient client = context.getBean(CreditAuthorizationClient.class);
          List<MeteredStep> steps =
              List.of(new MeteredStep(BillableCapability.VIDEO, "model-x", report()));

          assertThat(client.settleAggregate("hold-1", steps, "key-1")).isTrue();

          Received request = billing.only();
          assertThat(request.method()).isEqualTo("POST");
          assertThat(request.path())
              .isEqualTo("/internal/v1/billing/credits/hold-1/settle-aggregate");
          JsonNode body = JSON.readTree(request.body());
          assertThat(body.path("idempotencyKey").asString()).isEqualTo("key-1");
          assertThat(body.path("steps")).hasSize(1);
          assertThat(body.path("steps").get(0).path("capability").asString()).isEqualTo("VIDEO");
          assertThat(body.path("steps").get(0).path("modelName").asString()).isEqualTo("model-x");
          assertThat(body.path("steps").get(0).path("consumption").path("tokens").asLong())
              .isEqualTo(4000);
          billing.replyWith(Reply.json(500, "{}"));
          assertThat(client.settleAggregate("hold-1", steps, "key-1")).isFalse();
        });
    run(
        unreachableUrl(),
        context ->
            assertThat(
                    context
                        .getBean(CreditAuthorizationClient.class)
                        .settleAggregate("hold-1", List.of(), "key-1"))
                .isFalse());
  }

  @Test
  @DisplayName("release POSTs the reason; a failure is logged, never thrown")
  void releaseIsBestEffort() {
    run(
        billing.baseUrl(),
        context -> {
          CreditAuthorizationClient client = context.getBean(CreditAuthorizationClient.class);

          client.release("hold-1", "run failed");

          Received request = billing.only();
          assertThat(request.method()).isEqualTo("POST");
          assertThat(request.path()).isEqualTo("/internal/v1/billing/credits/hold-1/release");
          assertThat(JSON.readTree(request.body()).path("reason").asString())
              .isEqualTo("run failed");
          billing.replyWith(Reply.json(500, "{}"));
          assertThatCode(() -> client.release("hold-1", "run failed")).doesNotThrowAnyException();
        });
    run(
        unreachableUrl(),
        context ->
            assertThatCode(
                    () -> context.getBean(CreditAuthorizationClient.class).release("hold-1", "x"))
                .doesNotThrowAnyException());
  }

  // ---- helpers ------------------------------------------------------------------------------

  /** Runs the SPI-declared auto-configuration with billing enabled against {@code baseUrl}. */
  private static void run(String baseUrl, Consumer<ApplicationContext> assertions) {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(autoConfigurationClasses()))
        .withPropertyValues(
            "krizaka.billing.enabled=true",
            "krizaka.billing.base-url=" + baseUrl,
            "krizaka.billing.service-secret=" + SECRET)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertions.accept(context);
            });
  }

  private static Class<?>[] autoConfigurationClasses() {
    return ImportCandidates.load(
            AutoConfiguration.class, BillingClientFailureContractTest.class.getClassLoader())
        .getCandidates()
        .stream()
        .filter(name -> name.startsWith("com.krizaka.billing.client."))
        .map(BillingClientFailureContractTest::load)
        .toArray(Class<?>[]::new);
  }

  private static Class<?> load(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  /** A base URL nothing listens on: the connection is refused. */
  private static String unreachableUrl() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return "http://127.0.0.1:" + socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static PackProvision pack() {
    return new PackProvision("realestate-studio", 1900, 500, true, List.of());
  }

  private static CreditHoldCommand holdCommand() {
    return new CreditHoldCommand(
        "actor-1", BillableCapability.VIDEO, null, "correlation-1", null, 360);
  }

  private static ConsumptionReport report() {
    return new ConsumptionReport(
        new BigDecimal("1.5"), null, null, null, null, null, null, null, null, 4000L, null);
  }
}
