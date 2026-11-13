package com.krizaka.observability;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * The names a service is observed under ({@code krizaka.observability}).
 *
 * <p>All three are <b>required</b>: a metric, a trace or a log line that cannot say which product,
 * which service and which build produced it is noise on a shared dashboard. A service that does not
 * name itself does not start, and the failure lists every missing key.
 *
 * <pre>{@code
 * krizaka:
 *   observability: { product: sovkora, service: conversation, version: "@project.version@" }
 * }</pre>
 *
 * @param product Product the service belongs to, e.g. 'sovkora'. Required: tagged on every meter.
 * @param service Short name of the service within its product, e.g. 'conversation'. Required:
 *     tagged on every meter.
 * @param version Version of the running build, usually filtered in by the build
 *     ('@project.version@'). Required: tagged on every meter.
 * @param otlpEndpoint Base URL of the OTLP receiver (environment variable
 *     OBSERVABILITY_OTLP_ENDPOINT). Optional: with none, traces and metrics are not exported at
 *     all. Traces go to the endpoint's /v1/traces, metrics to its /v1/metrics.
 * @param ingestToken Token sent as a bearer Authorization header to the receiver (environment
 *     variable OBSERVABILITY_INGEST_TOKEN). Optional; never printed by toString.
 * @param environment Name of the deployment environment, e.g. 'dev' (environment variable
 *     OBSERVABILITY_ENVIRONMENT), exported as the resource attribute deployment.environment.name.
 *     Optional.
 */
@ConfigurationProperties(prefix = "krizaka.observability")
public record KrizakaObservabilityProperties(
    String product,
    String service,
    String version,
    String otlpEndpoint,
    String ingestToken,
    String environment) {

  /** The prefix of the three properties, for messages and tests. */
  public static final String PREFIX = "krizaka.observability";

  /**
   * The three required names, with no OTLP export.
   *
   * @param product product the service belongs to
   * @param service short name of the service
   * @param version version of the running build
   */
  public KrizakaObservabilityProperties(String product, String service, String version) {
    this(product, service, version, null, null, null);
  }

  /**
   * Rejects a service that does not name itself.
   *
   * @throws IllegalArgumentException naming every missing or blank property
   */
  @ConstructorBinding
  public KrizakaObservabilityProperties {
    List<String> missing = new ArrayList<>();
    if (isBlank(product)) {
      missing.add(PREFIX + ".product");
    }
    if (isBlank(service)) {
      missing.add(PREFIX + ".service");
    }
    if (isBlank(version)) {
      missing.add(PREFIX + ".version");
    }
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException(
          "A Krizaka service must name itself to be observed; missing: "
              + String.join(", ", missing)
              + " (e.g. krizaka.observability: { product: acme, service: orders,"
              + " version: \"@project.version@\" })");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /**
   * The names, without the token.
   *
   * @return a description safe to log
   */
  @Override
  public String toString() {
    return "KrizakaObservabilityProperties[product="
        + product
        + ", service="
        + service
        + ", version="
        + version
        + ", otlpEndpoint="
        + otlpEndpoint
        + ", environment="
        + environment
        + "]";
  }
}
