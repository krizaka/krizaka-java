package com.krizaka.observability.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

/**
 * The only bridge from Logback to the OpenTelemetry log export: every event is redacted with {@link
 * LogRedaction} in this class before it is handed to the SDK, so an unredacted record has no way
 * out. Attached to the root logger by {@link RedactedLogExportAutoConfiguration}, detached when the
 * application context closes.
 */
final class RedactingOtlpLogAppender extends AppenderBase<ILoggingEvent> {

  /** The appender's name on the root logger. */
  static final String NAME = "KRIZAKA_REDACTED_OTLP";

  /** The exporter's own logs never feed the exporter back. */
  private static final String OPENTELEMETRY_LOGGERS = "io.opentelemetry";

  private static final AttributeKey<String> THREAD = AttributeKey.stringKey("thread.name");
  private static final AttributeKey<String> EXCEPTION_TYPE =
      AttributeKey.stringKey("exception.type");
  private static final AttributeKey<String> EXCEPTION_MESSAGE =
      AttributeKey.stringKey("exception.message");
  private static final AttributeKey<String> EXCEPTION_STACKTRACE =
      AttributeKey.stringKey("exception.stacktrace");

  private final LoggerProvider loggerProvider;
  private final LoggerContext loggerContext;

  private RedactingOtlpLogAppender(LoggerProvider loggerProvider, LoggerContext loggerContext) {
    this.loggerProvider = loggerProvider;
    this.loggerContext = loggerContext;
  }

  /**
   * Attaches a started appender to the root logger of the running Logback.
   *
   * @param loggerProvider the SDK the redacted records go to
   * @param loggerFactory SLF4J's logger factory, which must be Logback's
   * @return the attached appender
   * @throws IllegalStateException when Logback is not the logging system: the redaction cannot be
   *     installed, so the export must not start
   */
  static RedactingOtlpLogAppender attach(
      LoggerProvider loggerProvider, ILoggerFactory loggerFactory) {
    if (!(loggerFactory instanceof LoggerContext context)) {
      throw new IllegalStateException(
          "Logs are exported over OTLP only redacted, and the redaction needs Logback; the"
              + " logging system is "
              + loggerFactory.getClass().getName()
              + ". Remove management.logging.export.otlp.enabled=true or use Logback.");
    }
    RedactingOtlpLogAppender appender = new RedactingOtlpLogAppender(loggerProvider, context);
    appender.setName(NAME);
    appender.setContext(context);
    appender.start();
    context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(appender);
    return appender;
  }

  /** Attaches to the running SLF4J binding. */
  static RedactingOtlpLogAppender attach(LoggerProvider loggerProvider) {
    return attach(loggerProvider, LoggerFactory.getILoggerFactory());
  }

  /** Detaches from the root logger and stops; called when the application context closes. */
  void detach() {
    loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).detachAppender(this);
    stop();
  }

  @Override
  protected void append(ILoggingEvent event) {
    String loggerName = event.getLoggerName();
    if (loggerName != null && loggerName.startsWith(OPENTELEMETRY_LOGGERS)) {
      return;
    }
    LogRecordBuilder log =
        loggerProvider
            .get(loggerName == null ? org.slf4j.Logger.ROOT_LOGGER_NAME : loggerName)
            .logRecordBuilder()
            .setTimestamp(Instant.ofEpochMilli(event.getTimeStamp()))
            .setSeverity(severity(event.getLevel()))
            .setSeverityText(event.getLevel().toString())
            .setContext(Context.current());
    String body = LogRedaction.redact(event.getFormattedMessage());
    if (body != null) {
      log.setBody(body);
    }
    String thread = event.getThreadName();
    if (thread != null) {
      log.setAttribute(THREAD, thread);
    }
    attributes(log, event.getMDCPropertyMap(), event.getKeyValuePairs());
    exception(log, event.getThrowableProxy());
    log.emit();
  }

  private static void attributes(
      LogRecordBuilder log,
      @Nullable Map<String, String> mdc,
      @Nullable List<KeyValuePair> keyValuePairs) {
    if (mdc != null) {
      mdc.forEach((key, value) -> put(log, key, value));
    }
    if (keyValuePairs != null) {
      for (KeyValuePair pair : keyValuePairs) {
        put(log, pair.key, pair.value == null ? null : String.valueOf(pair.value));
      }
    }
  }

  private static void put(LogRecordBuilder log, @Nullable String key, @Nullable String value) {
    String redacted = LogRedaction.redact(key, value);
    if (key != null && redacted != null) {
      log.setAttribute(AttributeKey.stringKey(key), redacted);
    }
  }

  private static void exception(LogRecordBuilder log, @Nullable IThrowableProxy throwable) {
    if (throwable == null) {
      return;
    }
    log.setAttribute(EXCEPTION_TYPE, throwable.getClassName());
    String message = LogRedaction.redact(throwable.getMessage());
    if (message != null) {
      log.setAttribute(EXCEPTION_MESSAGE, message);
    }
    String stackTrace = LogRedaction.redact(ThrowableProxyUtil.asString(throwable));
    if (stackTrace != null) {
      log.setAttribute(EXCEPTION_STACKTRACE, stackTrace);
    }
  }

  private static Severity severity(Level level) {
    return switch (level.toInt()) {
      case Level.ERROR_INT -> Severity.ERROR;
      case Level.WARN_INT -> Severity.WARN;
      case Level.INFO_INT -> Severity.INFO;
      case Level.DEBUG_INT -> Severity.DEBUG;
      case Level.TRACE_INT -> Severity.TRACE;
      default -> Severity.UNDEFINED_SEVERITY_NUMBER;
    };
  }
}
