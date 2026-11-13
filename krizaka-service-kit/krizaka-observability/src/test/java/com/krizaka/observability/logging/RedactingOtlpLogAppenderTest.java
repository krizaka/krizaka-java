package com.krizaka.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.Value;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.helpers.NOPLoggerFactory;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/** The bridge redacts every part of an event before the OpenTelemetry SDK sees it. */
class RedactingOtlpLogAppenderTest {

  private static final String EMAIL = "jane.doe@example.com";
  private static final String BEARER = "b34r3r-v4lu3";

  private final List<LogRecordData> exported = new CopyOnWriteArrayList<>();
  private final SdkLoggerProvider provider =
      SdkLoggerProvider.builder()
          .addLogRecordProcessor(SimpleLogRecordProcessor.create(new Collecting()))
          .build();
  private @Nullable RedactingOtlpLogAppender appender;

  private final class Collecting implements LogRecordExporter {
    @Override
    public CompletableResultCode export(Collection<LogRecordData> logs) {
      exported.addAll(logs);
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
      return CompletableResultCode.ofSuccess();
    }
  }

  @AfterEach
  void detach() {
    if (appender != null) {
      appender.detach();
    }
    provider.close();
    MDC.clear();
  }

  @Test
  void theMessageTheFieldsAndTheExceptionAreRedacted() {
    appender = RedactingOtlpLogAppender.attach(provider);
    MDC.put("customer", EMAIL);
    MDC.put("sessionToken", "opaque-value");
    LoggerFactory.getLogger("com.acme.orders.Invoices")
        .atError()
        .addKeyValue("client_secret", "kv-value")
        .setCause(new IllegalStateException("no card on file for " + EMAIL))
        .log("invoice sent to {} with Authorization: Bearer {}", EMAIL, BEARER);

    LogRecordData log =
        exported.stream()
            .filter(
                r -> "com.acme.orders.Invoices".equals(r.getInstrumentationScopeInfo().getName()))
            .findFirst()
            .orElseThrow();
    String body = Optional.ofNullable(log.getBodyValue()).map(Value::asString).orElse("");
    String everything = body + " " + log.getAttributes();
    assertThat(body).isEqualTo("invoice sent to [REDACTED] with Authorization: [REDACTED]");
    assertThat(log.getSeverityText()).isEqualTo("ERROR");
    assertThat(everything)
        .contains("exception.type=\"java.lang.IllegalStateException\"")
        .contains("no card on file for [REDACTED]")
        .contains("customer=\"[REDACTED]\"")
        .contains("sessionToken=\"[REDACTED]\"")
        .contains("client_secret=\"[REDACTED]\"")
        .doesNotContain(EMAIL)
        .doesNotContain(BEARER)
        .doesNotContain("opaque-value")
        .doesNotContain("kv-value");
  }

  @Test
  void theExportersOwnLogsNeverFeedTheExporterBack() {
    appender = RedactingOtlpLogAppender.attach(provider);

    LoggerFactory.getLogger("io.opentelemetry.exporter.internal.http.HttpExporter")
        .warn("Failed to export logs");

    assertThat(exported).isEmpty();
  }

  @Test
  void aDetachedAppenderExportsNothing() {
    appender = RedactingOtlpLogAppender.attach(provider);
    appender.detach();
    appender = null;

    LoggerFactory.getLogger("com.acme.orders.Invoices").info("after close");

    assertThat(exported).isEmpty();
  }

  @Test
  void withoutLogbackTheRedactionCannotBeInstalledAndTheExportRefusesToStart() {
    assertThatThrownBy(() -> RedactingOtlpLogAppender.attach(provider, new NOPLoggerFactory()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("only redacted");
    assertThat(exported).isEmpty();
  }

  @Test
  void theJsonRedactionIsRegisteredWithSpringBoot() throws Exception {
    Properties factories =
        PropertiesLoaderUtils.loadProperties(new ClassPathResource("META-INF/spring.factories"));

    assertThat(factories.getProperty(StructuredLoggingJsonMembersCustomizer.class.getName()))
        .isEqualTo(RedactingJsonMembersCustomizer.class.getName());
  }
}
