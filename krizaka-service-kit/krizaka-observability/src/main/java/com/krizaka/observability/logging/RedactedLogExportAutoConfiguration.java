package com.krizaka.observability.logging;

import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Feeds Spring Boot's OTLP log export with redacted Logback events, and only with those.
 *
 * <p>Spring Boot builds the exporter and the SDK's logger provider when {@code
 * management.logging.export.otlp.enabled} is true and an endpoint is set (the starter sets both
 * from {@code OBSERVABILITY_OTLP_ENDPOINT}, and only after {@link LogRedaction#selfTest()} passed);
 * it does not bridge Logback to them. This configuration attaches {@code RedactingOtlpLogAppender}
 * to the root logger, which redacts every event before the SDK sees it. When the appender cannot be
 * attached (Logback is not the logging system) the application does not start: logs leave a service
 * redacted or not at all.
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.opentelemetry.autoconfigure.logging.OpenTelemetryLoggingAutoConfiguration",
      "org.springframework.boot.opentelemetry.autoconfigure.logging.otlp.OtlpLoggingAutoConfiguration"
    })
@ConditionalOnClass(
    name = {"ch.qos.logback.classic.LoggerContext", "io.opentelemetry.sdk.logs.SdkLoggerProvider"})
@ConditionalOnBean({LogRecordExporter.class, SdkLoggerProvider.class})
public final class RedactedLogExportAutoConfiguration {

  /** Creates the auto-configuration; Spring Boot instantiates it. */
  public RedactedLogExportAutoConfiguration() {
    // Nothing to hold: the appender is the bean.
  }

  /**
   * The redacting bridge, attached to the root logger for the life of the context.
   *
   * @param loggerProvider the SDK's logger provider Spring Boot built
   * @return the attached appender, detached when the context closes
   */
  @Bean(destroyMethod = "detach")
  public RedactingOtlpLogAppender krizakaRedactingOtlpLogAppender(
      SdkLoggerProvider loggerProvider) {
    return RedactingOtlpLogAppender.attach(loggerProvider);
  }
}
