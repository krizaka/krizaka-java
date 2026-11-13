package com.krizaka.observability;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * The observability defaults every Krizaka service shares, at the <b>lowest priority</b>.
 *
 * <ul>
 *   <li>{@code logging.structured.format.console=ecs} — one JSON line per event, the format a
 *       single log parser reads for every service;
 *   <li>{@code management.endpoints.web.exposure.include=health,info,prometheus} — what a platform
 *       scrapes and probes, and nothing else;
 *   <li>{@code management.tracing.sampling.probability=0.1} — one request in ten traced;
 *   <li>OTLP log export switched off.
 * </ul>
 *
 * <p>The values are added as the last property source: {@code application.yaml}, the environment
 * and the command line still win, so a service (or a developer who wants plain console logs) can
 * override each of them.
 */
public class KrizakaObservabilityEnvironmentPostProcessor implements EnvironmentPostProcessor {

  private static final String OFF = "false";

  /** The name of the property source this post-processor adds. */
  public static final String PROPERTY_SOURCE_NAME = "krizakaObservabilityDefaults";

  /** The defaults, by property name. */
  public static final Map<String, Object> DEFAULTS =
      Map.of(
          "logging.structured.format.console", "ecs",
          "management.endpoints.web.exposure.include", "health,info,prometheus",
          "management.tracing.sampling.probability", "0.1",
          // Logs leave a service only once the redaction exists (Law 25): never over OTLP before.
          "management.logging.export.otlp.enabled", OFF,
          "management.otlp.logging.export.enabled", OFF);

  /** The OTLP names, by environment variable. */
  static final String ENDPOINT_VARIABLE = "OBSERVABILITY_OTLP_ENDPOINT";

  static final String TOKEN_VARIABLE = "OBSERVABILITY_INGEST_TOKEN";
  static final String ENVIRONMENT_VARIABLE = "OBSERVABILITY_ENVIRONMENT";
  static final String DEPRECATED_ENDPOINT_VARIABLE = "OTEL_TRACES_ENDPOINT";

  private static final String ENDPOINT_PROPERTY =
      KrizakaObservabilityProperties.PREFIX + ".otlp-endpoint";
  private static final String TOKEN_PROPERTY =
      KrizakaObservabilityProperties.PREFIX + ".ingest-token";
  private static final String ENVIRONMENT_PROPERTY =
      KrizakaObservabilityProperties.PREFIX + ".environment";
  private static final String TRACES_ENDPOINT =
      "management.opentelemetry.tracing.export.otlp.endpoint";
  private static final String TRACES_HEADER =
      "management.opentelemetry.tracing.export.otlp.headers.Authorization";
  private static final String TRACES_ENABLED = "management.tracing.export.otlp.enabled";
  private static final String METRICS_URL = "management.otlp.metrics.export.url";
  private static final String METRICS_HEADER =
      "management.otlp.metrics.export.headers.Authorization";
  private static final String METRICS_ENABLED = "management.otlp.metrics.export.enabled";
  private static final String ENVIRONMENT_ATTRIBUTE =
      "management.opentelemetry.resource-attributes.deployment.environment.name";
  private static final String TRACES_PATH = "/v1/traces";

  private final Log log;

  /** Creates the post-processor; Spring Boot instantiates it from {@code spring.factories}. */
  public KrizakaObservabilityEnvironmentPostProcessor() {
    this(Supplier::get);
  }

  /**
   * Creates the post-processor with the log Spring Boot defers until logging is configured.
   *
   * @param logFactory where the deprecation warning goes
   */
  public KrizakaObservabilityEnvironmentPostProcessor(DeferredLogFactory logFactory) {
    this.log = logFactory.getLog(KrizakaObservabilityEnvironmentPostProcessor.class);
  }

  /**
   * Adds the defaults, and the Spring Boot settings derived from the OTLP names, as the last
   * property source.
   *
   * @param environment the environment being prepared
   * @param app the application being started
   */
  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication app) {
    if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
      return;
    }
    Map<String, Object> source = new HashMap<>(DEFAULTS);
    otlp(environment, source);
    environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, source));
  }

  private void otlp(ConfigurableEnvironment environment, Map<String, Object> source) {
    String endpoint = endpoint(environment);
    String token = first(environment, TOKEN_PROPERTY, TOKEN_VARIABLE);
    String deploymentEnvironment = first(environment, ENVIRONMENT_PROPERTY, ENVIRONMENT_VARIABLE);
    if (deploymentEnvironment != null) {
      source.put(ENVIRONMENT_PROPERTY, deploymentEnvironment);
      source.put(ENVIRONMENT_ATTRIBUTE, deploymentEnvironment);
    }
    if (endpoint == null) {
      // Fail closed: Spring Boot would otherwise export to localhost:4318 where nothing listens.
      if (!environment.containsProperty(TRACES_ENDPOINT)) {
        source.put(TRACES_ENABLED, OFF);
      }
      if (!environment.containsProperty(METRICS_URL)) {
        source.put(METRICS_ENABLED, OFF);
      }
      return;
    }
    source.put(ENDPOINT_PROPERTY, endpoint);
    source.put(TRACES_ENDPOINT, endpoint + TRACES_PATH);
    source.put(METRICS_URL, endpoint + "/v1/metrics");
    if (token != null) {
      source.put(TOKEN_PROPERTY, token);
      source.put(TRACES_HEADER, "Bearer " + token);
      source.put(METRICS_HEADER, "Bearer " + token);
    }
  }

  /** The base URL of the receiver, without a trailing slash; null when none is set. */
  private String endpoint(ConfigurableEnvironment environment) {
    String endpoint = first(environment, ENDPOINT_PROPERTY, ENDPOINT_VARIABLE);
    if (endpoint == null) {
      endpoint = first(environment, DEPRECATED_ENDPOINT_VARIABLE);
      if (endpoint == null) {
        return null;
      }
      // Only the name is logged, never a value.
      log.warn(
          DEPRECATED_ENDPOINT_VARIABLE
              + " is deprecated and will be removed in the next version; use "
              + ENDPOINT_VARIABLE
              + " (a base URL, traces and metrics are sent to its /v1/traces and /v1/metrics)");
      // The old name held the traces URL itself.
      if (endpoint.endsWith(TRACES_PATH)) {
        endpoint = endpoint.substring(0, endpoint.length() - TRACES_PATH.length());
      }
    }
    return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
  }

  private static String first(ConfigurableEnvironment environment, String... names) {
    for (String name : names) {
      String value = environment.getProperty(name);
      if (value != null && !value.isBlank()) {
        return value.strip();
      }
    }
    return null;
  }
}
