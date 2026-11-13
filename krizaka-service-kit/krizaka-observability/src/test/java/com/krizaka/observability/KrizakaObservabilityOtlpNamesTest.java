package com.krizaka.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** The OTLP names the starter reads, and what it derives from them for Spring Boot. */
class KrizakaObservabilityOtlpNamesTest {

  private static final String TOKEN = "tok-4f9a1c";
  private static final String TRACES = "management.opentelemetry.tracing.export.otlp";
  private static final String METRICS = "management.otlp.metrics.export";
  private static final String AUTH = ".headers.Authorization";
  private static final String TRACES_ENDPOINT = TRACES + ".endpoint";
  private static final String TRACES_HEADER = TRACES + AUTH;
  private static final String TRACES_ENABLED = "management.tracing.export.otlp.enabled";
  private static final String METRICS_URL = METRICS + ".url";
  private static final String METRICS_HEADER = METRICS + AUTH;
  private static final String METRICS_ENABLED = METRICS + ".enabled";
  private static final String ENVIRONMENT_ATTRIBUTE =
      "management.opentelemetry.resource-attributes" + ".deployment.environment.name";

  private final List<String> logged = new ArrayList<>();

  private StandardEnvironment process(Map<String, Object> variables) {
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("variables", variables));
    new KrizakaObservabilityEnvironmentPostProcessor(destination -> recordingLog())
        .postProcessEnvironment(environment, new SpringApplication());
    return environment;
  }

  @Test
  void theFunctionalNamesDriveTracesMetricsAndTheEnvironment() {
    StandardEnvironment environment =
        process(
            Map.of(
                "OBSERVABILITY_OTLP_ENDPOINT", "https://otlp.example.test/",
                "OBSERVABILITY_INGEST_TOKEN", TOKEN,
                "OBSERVABILITY_ENVIRONMENT", "dev"));

    assertThat(environment.getProperty(TRACES_ENDPOINT))
        .isEqualTo("https://otlp.example.test/v1/traces");
    assertThat(environment.getProperty(METRICS_URL))
        .isEqualTo("https://otlp.example.test/v1/metrics");
    assertThat(environment.getProperty(TRACES_HEADER)).isEqualTo("Bearer " + TOKEN);
    assertThat(environment.getProperty(METRICS_HEADER)).isEqualTo("Bearer " + TOKEN);
    assertThat(environment.getProperty(ENVIRONMENT_ATTRIBUTE)).isEqualTo("dev");
    assertThat(environment.getProperty(TRACES_ENABLED)).isNull();
    assertThat(environment.getProperty(METRICS_ENABLED)).isNull();
  }

  @Test
  void theTypedPropertiesCarryTheNames() {
    StandardEnvironment environment =
        process(
            Map.of(
                "OBSERVABILITY_OTLP_ENDPOINT", "https://otlp.example.test",
                "OBSERVABILITY_INGEST_TOKEN", TOKEN,
                "OBSERVABILITY_ENVIRONMENT", "dev"));

    assertThat(environment.getProperty("krizaka.observability.otlp-endpoint"))
        .isEqualTo("https://otlp.example.test");
    assertThat(environment.getProperty("krizaka.observability.environment")).isEqualTo("dev");
    assertThat(environment.getProperty("krizaka.observability.ingest-token")).isEqualTo(TOKEN);
  }

  @Test
  void theOldNameIsStillAcceptedAndWarnsWithoutAnyValue() {
    StandardEnvironment environment =
        process(
            Map.of(
                "OTEL_TRACES_ENDPOINT",
                "https://old.example.test/v1/traces",
                "OBSERVABILITY_INGEST_TOKEN",
                TOKEN));

    assertThat(environment.getProperty(TRACES_ENDPOINT))
        .isEqualTo("https://old.example.test/v1/traces");
    assertThat(environment.getProperty(METRICS_URL))
        .isEqualTo("https://old.example.test/v1/metrics");
    assertThat(logged)
        .singleElement()
        .asString()
        .contains("OTEL_TRACES_ENDPOINT", "OBSERVABILITY_OTLP_ENDPOINT", "deprecated")
        .doesNotContain(TOKEN, "old.example.test");
  }

  @Test
  void theNewNameWinsOverTheOldOneAndStaysQuiet() {
    StandardEnvironment environment =
        process(
            Map.of(
                "OBSERVABILITY_OTLP_ENDPOINT", "https://new.example.test",
                "OTEL_TRACES_ENDPOINT", "https://old.example.test/v1/traces"));

    assertThat(environment.getProperty(TRACES_ENDPOINT))
        .isEqualTo("https://new.example.test/v1/traces");
    assertThat(logged).isEmpty();
  }

  @Test
  void noEndpointMeansNothingIsExported() {
    StandardEnvironment environment = process(Map.of("OBSERVABILITY_INGEST_TOKEN", TOKEN));

    assertThat(environment.getProperty(TRACES_ENABLED)).isEqualTo("false");
    assertThat(environment.getProperty(METRICS_ENABLED)).isEqualTo("false");
    assertThat(environment.getProperty(TRACES_ENDPOINT)).isNull();
    assertThat(environment.getProperty(METRICS_URL)).isNull();
    assertThat(environment.getProperty(TRACES_HEADER)).isNull();
    assertThat(logged).isEmpty();
  }

  @Test
  void aBlankEndpointIsNoEndpoint() {
    StandardEnvironment environment = process(Map.of("OBSERVABILITY_OTLP_ENDPOINT", "  "));

    assertThat(environment.getProperty(TRACES_ENABLED)).isEqualTo("false");
  }

  @Test
  void anEndpointSetTheSpringBootWayIsNotSwitchedOff() {
    StandardEnvironment environment =
        process(Map.of(TRACES_ENDPOINT, "http://127.0.0.1:4318/v1/traces"));

    assertThat(environment.getProperty(TRACES_ENABLED)).isNull();
    assertThat(environment.getProperty(METRICS_ENABLED)).isEqualTo("false");
  }

  @Test
  void samplingStaysASettingWithTenPercentByDefault() {
    assertThat(process(Map.of()).getProperty("management.tracing.sampling.probability"))
        .isEqualTo("0.1");
    assertThat(
            process(Map.of("management.tracing.sampling.probability", "1.0"))
                .getProperty("management.tracing.sampling.probability"))
        .isEqualTo("1.0");
  }

  @Test
  void logsAreNeverExportedOverOtlpWhateverTheEndpoint() {
    StandardEnvironment environment =
        process(Map.of("OBSERVABILITY_OTLP_ENDPOINT", "https://otlp.example.test"));

    assertThat(environment.getProperty("management.logging.export.otlp.enabled"))
        .isEqualTo("false");
    assertThat(environment.getProperty("management.otlp.logging.export.enabled"))
        .isEqualTo("false");
  }

  @Test
  void theTokenNeverShowsInTheRecordToString() {
    KrizakaObservabilityProperties properties =
        new KrizakaObservabilityProperties(
            "acme", "orders", "1.2.3", "https://otlp.example.test", TOKEN, "dev");

    assertThat(properties.toString()).doesNotContain(TOKEN).contains("acme");
    assertThat(properties.ingestToken()).isEqualTo(TOKEN);
  }

  private Log recordingLog() {
    return (Log)
        Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {Log.class},
            (proxy, method, args) -> {
              if ("warn".equals(method.getName()) && args != null) {
                logged.add(String.valueOf(args[0]));
              }
              return method.getReturnType() == boolean.class ? Boolean.TRUE : null;
            });
  }
}
