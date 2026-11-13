package com.krizaka.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.krizaka.test.net.LoopbackTestServers;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
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
  void withNoEndpointNothingIsExportedAndNoExporterBeanExists() throws Exception {
    try (ConfigurableApplicationContext context = run()) {
      context.getBean(Tracer.class).nextSpan().name("lost").start().end();
      context.getBean(MeterRegistry.class).counter("orders.created").increment();

      assertThat(context.getBeanNamesForType(io.opentelemetry.sdk.trace.export.SpanExporter.class))
          .isEmpty();
      assertThat(context.getBeanNamesForType(io.micrometer.registry.otlp.OtlpMeterRegistry.class))
          .isEmpty();
      Thread.sleep(1500);
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
