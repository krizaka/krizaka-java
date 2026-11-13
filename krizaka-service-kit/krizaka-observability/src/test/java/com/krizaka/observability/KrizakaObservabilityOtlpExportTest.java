package com.krizaka.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.krizaka.test.net.LoopbackTestServers;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The whole starter through Spring Boot, against a fake OTLP receiver and a fake downstream, both
 * on the loopback.
 */
@ExtendWith(OutputCaptureExtension.class)
class KrizakaObservabilityOtlpExportTest {

  private static final String TOKEN = "tok-4f9a1c";
  private static final String EMAIL = "jane.doe@example.com";
  private static final String BEARER = "b34r3r-v4lu3";
  private static final String MDC_SECRET = "mdc-s3cr3t";
  private static final String KEY_VALUE_SECRET = "kv-s3cr3t";

  private record Received(String path, String authorization, String body) {}

  private final List<Received> received = new CopyOnWriteArrayList<>();
  private final List<String> downstreamTraceparents = new CopyOnWriteArrayList<>();
  private HttpServer receiver;
  private HttpServer downstream;

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class App {}

  @BeforeEach
  void startServers() throws IOException {
    receiver = LoopbackTestServers.httpServer();
    receiver.createContext(
        "/",
        exchange -> {
          byte[] raw = exchange.getRequestBody().readAllBytes();
          if ("gzip".equals(exchange.getRequestHeaders().getFirst("Content-Encoding"))) {
            raw = new GZIPInputStream(new java.io.ByteArrayInputStream(raw)).readAllBytes();
          }
          received.add(
              new Received(
                  exchange.getRequestURI().getPath(),
                  exchange.getRequestHeaders().getFirst("Authorization"),
                  new String(raw, StandardCharsets.ISO_8859_1)));
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    receiver.start();
    downstream = LoopbackTestServers.httpServer();
    downstream.createContext(
        "/",
        exchange -> {
          downstreamTraceparents.add(
              String.valueOf(exchange.getRequestHeaders().getFirst("traceparent")));
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    downstream.start();
  }

  @AfterEach
  void stopServers() {
    receiver.stop(0);
    downstream.stop(0);
  }

  private ConfigurableApplicationContext run(String... properties) {
    String[] base = {
      "krizaka.observability.product=acme",
      "krizaka.observability.service=orders",
      "krizaka.observability.version=1.2.3",
      "management.tracing.sampling.probability=1.0",
      "management.opentelemetry.tracing.export.schedule-delay=100ms",
      "management.otlp.metrics.export.step=1s",
      "spring.application.name=orders"
    };
    // Command-line arguments: they win over every default, like a real deployment's settings.
    String[] arguments =
        java.util.stream.Stream.concat(
                java.util.Arrays.stream(base), java.util.Arrays.stream(properties))
            .map(property -> "--" + property)
            .toArray(String[]::new);
    return new SpringApplicationBuilder(App.class).web(WebApplicationType.NONE).run(arguments);
  }

  private String receiverEndpoint() {
    return "http://127.0.0.1:" + receiver.getAddress().getPort();
  }

  @Test
  void exportsTracesAndMetricsWithTheEnvironmentAndTheAuthHeader(CapturedOutput output) {
    try (ConfigurableApplicationContext context =
        run(
            "OBSERVABILITY_OTLP_ENDPOINT=" + receiverEndpoint(),
            "OBSERVABILITY_INGEST_TOKEN=" + TOKEN,
            "OBSERVABILITY_ENVIRONMENT=dev")) {
      assertThat(context.getBean(KrizakaObservabilityProperties.class))
          .returns(receiverEndpoint(), KrizakaObservabilityProperties::otlpEndpoint)
          .returns(TOKEN, KrizakaObservabilityProperties::ingestToken)
          .returns("dev", KrizakaObservabilityProperties::environment);
      Tracer tracer = context.getBean(Tracer.class);
      Span span = tracer.nextSpan().name("a-unit-of-work").start();
      span.end();
      context.getBean(MeterRegistry.class).counter("orders.created").increment();

      await()
          .atMost(Duration.ofSeconds(20))
          .untilAsserted(
              () ->
                  assertThat(received)
                      .extracting(Received::path)
                      .contains("/v1/traces", "/v1/metrics"));
    }

    for (String path : List.of("/v1/traces", "/v1/metrics")) {
      Received export =
          received.stream().filter(r -> r.path().equals(path)).findFirst().orElseThrow();
      assertThat(export.authorization()).as(path).isEqualTo("Bearer " + TOKEN);
      assertThat(export.body())
          .as(path)
          .contains("deployment.environment.name")
          .contains("dev")
          .contains("orders");
    }
    assertThat(output.getAll()).doesNotContain(TOKEN);
  }

  @Test
  void aLogLineWithAnEmailAndABearerTokenLeavesRedactedOnBothPaths(CapturedOutput output) {
    try (ConfigurableApplicationContext context =
        run(
            "OBSERVABILITY_OTLP_ENDPOINT=" + receiverEndpoint(),
            "OBSERVABILITY_INGEST_TOKEN=" + TOKEN,
            "OBSERVABILITY_ENVIRONMENT=dev",
            "management.opentelemetry.logging.export.otlp.compression=none")) {
      assertThat(context.getBeanNamesForType(LogRecordExporter.class)).isNotEmpty();
      Logger logger = LoggerFactory.getLogger("com.acme.orders.Invoices");
      MDC.put("customer", EMAIL);
      MDC.put("apiToken", MDC_SECRET);
      try {
        logger
            .atInfo()
            .addKeyValue("password", KEY_VALUE_SECRET)
            .log("invoice sent to {} with Authorization: Bearer {}", EMAIL, BEARER);
        logger.error("refund failed", new IllegalStateException("no card on file for " + EMAIL));
      } finally {
        MDC.clear();
      }

      await()
          .atMost(Duration.ofSeconds(20))
          .untilAsserted(
              () -> assertThat(logExports()).contains("invoice sent to").contains("refund failed"));
    }

    String exported = logExports();
    assertThat(received.stream().filter(r -> r.path().equals("/v1/logs")))
        .allSatisfy(r -> assertThat(r.authorization()).isEqualTo("Bearer " + TOKEN));
    assertThat(exported)
        .contains("deployment.environment.name")
        .contains("invoice sent to [REDACTED] with Authorization: [REDACTED]")
        .contains("no card on file for [REDACTED]");
    String console = output.getAll();
    assertThat(console)
        .contains("invoice sent to [REDACTED] with Authorization: [REDACTED]")
        .contains("no card on file for [REDACTED]");
    for (String secret : List.of(EMAIL, BEARER, MDC_SECRET, KEY_VALUE_SECRET, TOKEN)) {
      assertThat(exported).as("OTLP export").doesNotContain(secret);
      assertThat(console).as("console").doesNotContain(secret);
    }
  }

  private String logExports() {
    return String.join(
        "\n",
        received.stream().filter(r -> r.path().equals("/v1/logs")).map(Received::body).toList());
  }

  @Test
  void withNoEndpointNothingIsExportedAndNoExporterBeanExists() {
    try (ConfigurableApplicationContext context = run()) {
      context.getBean(Tracer.class).nextSpan().name("lost").start().end();
      context.getBean(MeterRegistry.class).counter("orders.created").increment();

      assertThat(context.getBeanNamesForType(io.opentelemetry.sdk.trace.export.SpanExporter.class))
          .isEmpty();
      assertThat(context.getBeanNamesForType(io.micrometer.registry.otlp.OtlpMeterRegistry.class))
          .isEmpty();
      assertThat(context.getBeanNamesForType(LogRecordExporter.class)).isEmpty();
      LoggerFactory.getLogger("com.acme.orders.Invoices").info("a line that stays home");
      // Two metric steps and several span batches would have passed: nothing arrived.
      await()
          .during(Duration.ofMillis(1500))
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> assertThat(received).isEmpty());
    }
    assertThat(received).isEmpty();
  }

  @Test
  void aCallThroughAKitHttpClientCarriesTraceparent() {
    try (ConfigurableApplicationContext context = run()) {
      RestClient client = context.getBean(RestClient.Builder.class).build();
      Span span = context.getBean(Tracer.class).nextSpan().name("outer").start();
      try (Tracer.SpanInScope ignored = context.getBean(Tracer.class).withSpan(span)) {
        client
            .get()
            .uri(URI.create("http://127.0.0.1:" + downstream.getAddress().getPort() + "/ping"))
            .retrieve()
            .toBodilessEntity();
      } finally {
        span.end();
      }
    }

    assertThat(downstreamTraceparents)
        .singleElement()
        .asString()
        .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[0-3]");
  }
}
