package com.krizaka.observability.logging;

import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.json.JsonWriter.MemberPath;
import org.springframework.boot.json.JsonWriter.Members;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Redacts every value of Spring Boot's structured JSON logs (ECS, Logstash, GELF) with {@link
 * LogRedaction}: the message, the MDC and key-value pairs, the exception message and the stack
 * trace. Spring Boot loads it from {@code META-INF/spring.factories} for every JSON format, so a
 * service has nothing to configure.
 */
public final class RedactingJsonMembersCustomizer
    implements StructuredLoggingJsonMembersCustomizer<Object> {

  /** Creates the customizer; Spring Boot instantiates it from {@code spring.factories}. */
  public RedactingJsonMembersCustomizer() {
    // Stateless: the rules live in LogRedaction.
  }

  /**
   * Applies the redaction to every value written.
   *
   * @param members the members of the log line
   */
  @Override
  public void customize(Members<Object> members) {
    members.applyingValueProcessor(RedactingJsonMembersCustomizer::redact);
  }

  /** A JSON-native value stays as is; any other value is redacted as the text it is written as. */
  private static @Nullable Object redact(MemberPath path, @Nullable Object value) {
    if (value == null
        || value instanceof Number
        || value instanceof Boolean
        || value instanceof Map<?, ?>
        || value instanceof Collection<?>
        || value.getClass().isArray()) {
      return value;
    }
    return LogRedaction.redact(path.name(), value.toString());
  }
}
