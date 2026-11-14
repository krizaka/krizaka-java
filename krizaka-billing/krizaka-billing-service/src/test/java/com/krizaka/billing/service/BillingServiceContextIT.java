package com.krizaka.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.payments.stripe.WebhookSignature;
import com.krizaka.test.container.ServiceRoles;
import com.krizaka.test.sql.InitDb;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The whole billing service, started the way the dev environment runs it (its own {@code
 * application.yml}, the datasource built from {@code krizaka.billing-service.datasource.*}, the
 * real tables and seed of {@code infra/initdb/70-billing.sql}, the shared HS256 secret) and its
 * HTTP chain: 401 without a token, 403 for a user on the admin surface, the seeded plans for a
 * user, and an admin crediting a wallet that the user then reads. A bean or filter change that
 * stops the service from starting fails here, not in the deployment (krizaka-users#18).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "krizaka.messaging.outbox.enabled=false",
      "krizaka.messaging.consumer.enabled=false",
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "management.health.rabbit.enabled=false",
      "management.tracing.enabled=false"
    })
class BillingServiceContextIT {

  private static final String SECRET = "sovkora-local-dev-identity-jwt-secret-256bit!";
  private static final String WEBHOOK_SECRET = "whsec_signing-material-for-billing-tests";
  private static final String WEBHOOK = "/api/v1/billing/payments/webhook";
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("bootstrap")
          .withUsername("postgres")
          .withPassword("postgres")
          .withCopyFileToContainer(
              MountableFile.forHostPath(
                  InitDb.locate(Path.of(System.getProperty("user.dir"))).resolve("70-billing.sql")),
              "/docker-entrypoint-initdb.d/70-billing.sql");

  static {
    if (System.getProperty("api.version") == null && System.getenv("DOCKER_API_VERSION") == null) {
      System.setProperty("api.version", "1.43");
    }
    POSTGRES.start();
    ServiceRoles.assignPassword(POSTGRES, "krizaka_billing", "krizaka_billing_pass");
  }

  @LocalServerPort int port;

  @DynamicPropertySource
  static void connection(DynamicPropertyRegistry registry) {
    registry.add(
        "krizaka.billing-service.datasource.url",
        () ->
            "jdbc:postgresql://%s:%d/krizaka_billing_db"
                .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432)));
    registry.add("krizaka.billing-service.datasource.username", () -> "krizaka_billing");
    registry.add("krizaka.billing-service.datasource.password", () -> "krizaka_billing_pass");
    registry.add("krizaka.security.jwt.secret", () -> SECRET);
    registry.add("krizaka.billing-service.payments.webhook-secret", () -> WEBHOOK_SECRET);
  }

  private static String token(String subject, List<String> roles) {
    var key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    var claims =
        JwtClaimsSet.builder()
            .subject(subject)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(600))
            .claim("roles", roles)
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  private HttpResponse<String> call(String method, String path, String token, String body)
      throws Exception {
    return call(method, path, token, body, null);
  }

  private HttpResponse<String> call(
      String method, String path, String token, String body, String signature) throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json");
    if (signature != null) {
      builder.header("Stripe-Signature", signature);
    }
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void theServiceStartsAsDeployedAndAnAdminCanCreditAWallet() throws Exception {
    String user = token("qa-user-1", List.of("ROLE_USER"));
    String admin = token("qa-admin-1", List.of("ROLE_USER", "ROLE_ADMIN"));

    assertThat(call("GET", "/actuator/health", null, null).statusCode()).isEqualTo(200);
    assertThat(call("GET", "/api/v1/billing/plans", null, null).statusCode()).isEqualTo(401);

    var plans = call("GET", "/api/v1/billing/plans", user, null);
    assertThat(plans.statusCode()).isEqualTo(200);
    assertThat(plans.body()).contains("\"free\"");

    var forbidden =
        call(
            "POST",
            "/api/v1/billing/wallets/qa-user-1/adjustments",
            user,
            "{\"bucket\":\"GRANTED\",\"amount\":1000,\"reason\":\"test\"}");
    assertThat(forbidden.statusCode()).isEqualTo(403);

    var credited =
        call(
            "POST",
            "/api/v1/billing/wallets/qa-user-1/adjustments",
            admin,
            "{\"bucket\":\"GRANTED\",\"amount\":1000,\"reason\":\"QA test credits\"}");
    assertThat(credited.statusCode()).isEqualTo(200);

    var wallet = call("GET", "/api/v1/billing/wallets/me", user, null);
    assertThat(wallet.statusCode()).isEqualTo(200);
    assertThat(wallet.body()).contains("1000");
  }

  /** A paid one-off Checkout of {@code kind} by {@code account}, as the processor sends it. */
  private static String paidCheckout(
      String eventId, String sessionId, String account, String kind) {
    return ("{\"id\":\"%s\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":"
            + "{\"id\":\"%s\",\"mode\":\"payment\",\"payment_status\":\"paid\","
            + "\"customer\":\"cus_1\",\"metadata\":{\"account_id\":\"%s\",\"kind\":\"%s\"}}}}")
        .formatted(eventId, sessionId, account, kind);
  }

  private static String signed(String body, String secret) {
    return WebhookSignature.sign(body, secret, Instant.now().getEpochSecond());
  }

  private HttpResponse<String> notify(String body, String signature) throws Exception {
    return call("POST", WEBHOOK, null, body, signature);
  }

  private HttpResponse<String> buy(String token, String packKey, String reference)
      throws Exception {
    return call(
        "POST",
        "/api/v1/billing/pack-subscriptions/me/" + packKey + "?paymentReference=" + reference,
        token,
        null);
  }

  /** [#3685] only a verified payment_paid settles a pack, once, for its own actor and pack. */
  @Test
  void aVerifiedPaymentNotificationSettlesThePackItPaidFor() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String pack = "pk-" + suffix;
    String otherPack = "pk-other-" + suffix;
    String admin = token("qa-admin-1", List.of("ROLE_USER", "ROLE_ADMIN"));
    for (String key : List.of(pack, otherPack)) {
      String priced =
          "{\"packKey\":\"%s\",\"priceCents\":4900,\"includedCredits\":500,\"isActive\":true,\"entitlements\":[]}";
      assertThat(
              call("PUT", "/api/v1/billing/packs/" + key, admin, priced.formatted(key))
                  .statusCode())
          .isEqualTo(200);
    }
    String buyer = "qa-buyer-" + suffix;
    String buyerToken = token(buyer, List.of("ROLE_USER"));
    String session = "cs_test_" + suffix;
    String body = paidCheckout("evt_" + suffix, session, buyer, "pack:" + pack);

    // unsigned and forged notifications are refused, and settle nothing
    var unsigned = notify(body, null);
    assertThat(unsigned.statusCode()).isEqualTo(400);
    assertThat(unsigned.body()).contains("Payment notification refused");
    assertThat(notify(body, signed(body, "whsec_someone-else")).statusCode()).isEqualTo(400);
    String tampered = body.replace(buyer, "qa-thief-" + suffix);
    assertThat(notify(tampered, signed(body, WEBHOOK_SECRET)).statusCode()).isEqualTo(400);
    assertThat(buy(buyerToken, pack, session).statusCode()).isEqualTo(402);

    // the verified notification is applied once, without a session token; a replay is a no-op
    var applied = notify(body, signed(body, WEBHOOK_SECRET));
    assertThat(applied.statusCode()).isEqualTo(200);
    assertThat(applied.body()).contains("\"applied\"");
    var replayed = notify(body, signed(body, WEBHOOK_SECRET));
    assertThat(replayed.statusCode()).isEqualTo(200);
    assertThat(replayed.body()).contains("\"duplicate\"");

    // a notification for a pack billing does not sell settles nothing
    String unknown =
        paidCheckout("evt_u_" + suffix, "cs_u_" + suffix, buyer, "pack:nope-" + suffix);
    assertThat(notify(unknown, signed(unknown, WEBHOOK_SECRET)).body()).contains("\"ignored\"");

    // the payment is bound to its actor and its pack
    String other = token("qa-other-" + suffix, List.of("ROLE_USER"));
    assertThat(buy(other, pack, session).statusCode()).isEqualTo(402);
    assertThat(buy(buyerToken, otherPack, session).statusCode()).isEqualTo(402);

    // the buyer's checkout success hands the reference over: the pack is active with its credits
    var bought = buy(buyerToken, pack, session);
    assertThat(bought.statusCode()).isEqualTo(201);
    assertThat(bought.body()).contains("\"ACTIVE\"");
    assertThat(call("GET", "/api/v1/billing/wallets/me", buyerToken, null).body()).contains("500");
  }
}
