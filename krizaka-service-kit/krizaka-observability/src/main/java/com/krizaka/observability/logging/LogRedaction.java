package com.krizaka.observability.logging;

import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The one rule set a log line passes before it leaves a service (Law 25): the local ECS JSON log
 * and the OTLP log export both call {@link #redact(String)} and {@link #redact(String, String)}, so
 * what one path hides the other hides too.
 *
 * <p>What is redacted, each replaced by {@value #REDACTED}:
 *
 * <ul>
 *   <li>the value of an {@code Authorization} / {@code Proxy-Authorization} header, scheme
 *       included;
 *   <li>a {@code Bearer} token anywhere;
 *   <li>a JWT-shaped string ({@code eyJ….….…}), even without a key or a scheme;
 *   <li>the value assigned to a key named like {@code *token*}, {@code *secret*}, {@code
 *       *password*}, {@code *passwd*} or {@code *api[_-]key*} ({@code key=value}, {@code key:
 *       value}, JSON, query strings);
 *   <li>an e-mail address.
 * </ul>
 *
 * <p>A structured field (MDC entry, key-value pair, JSON member) whose name matches {@link
 * #isSensitiveKey(String)} loses its whole value. Every pattern is bounded, so a hostile line
 * cannot make the logging thread backtrack for long.
 */
public final class LogRedaction {

  /** What a redacted value is replaced with. */
  public static final String REDACTED = "[REDACTED]";

  /**
   * One redaction: a pattern and its replacement.
   *
   * @param name the rule's name, for tests and documentation
   * @param pattern what the rule finds
   * @param replacement what a match becomes ({@code $1} keeps the key of a {@code key=value})
   */
  public record Rule(String name, Pattern pattern, String replacement) {

    /**
     * Applies the rule to a text.
     *
     * @param text the text to redact
     * @return the text with every match replaced
     */
    public String apply(String text) {
      return pattern.matcher(text).replaceAll(replacement);
    }
  }

  /**
   * The rules, in the order they run: the header before the bare bearer token, so {@code
   * Authorization: Bearer x} becomes {@code Authorization: [REDACTED]}.
   */
  public static final List<Rule> RULES =
      List.of(
          new Rule(
              "authorization",
              Pattern.compile(
                  "(?i)\\b(authorization[\"']?\\s{0,16}[:=]\\s{0,16}[\"']?)"
                      + "(?:(?:bearer|basic|digest|token|negotiate|apikey)\\s{1,16})?"
                      + "[^\\s\"',;&}]++"),
              "$1" + REDACTED),
          new Rule(
              "bearer",
              Pattern.compile("(?i)\\bbearer\\s{1,16}[A-Za-z0-9._~+/=-]++"),
              "Bearer " + REDACTED),
          new Rule(
              "jwt",
              Pattern.compile("\\beyJ[A-Za-z0-9_-]++\\.[A-Za-z0-9_-]++\\.[A-Za-z0-9_-]*+"),
              REDACTED),
          new Rule(
              "secret",
              Pattern.compile(
                  "(?i)(\\b[\\w.-]{0,64}(?:token|secret|passw|api.?key)[\\w.-]{0,64}"
                      + "[\"']?\\s{0,16}[:=]\\s{0,16}[\"']?)[^\\s\"',;&}]++"),
              "$1" + REDACTED),
          new Rule(
              "email",
              Pattern.compile(
                  "[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]{1,63}(?:\\.[A-Za-z0-9-]{1,63}){1,8}"),
              REDACTED));

  private static final Pattern SENSITIVE_KEY =
      Pattern.compile("(?i)token|secret|passw|api.?key|authorization|cookie");

  /** The canary {@link #selfTest()} runs; every part of it must disappear. */
  private static final List<String> CANARY_SECRETS =
      List.of(
          "canary@example.com",
          "c4n4ry-b34r3r",
          "eyJjYW5hcnkiOjF9.eyJjYW5hcnkiOjJ9.Y2FuYXJ5",
          "c4n4ry-p4ss");

  private LogRedaction() {}

  /**
   * Redacts a free text: a log message, an exception message, a stack trace.
   *
   * @param text the text, possibly null
   * @return the text with every rule applied, or null for null
   */
  public static @Nullable String redact(@Nullable String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    String redacted = text;
    for (Rule rule : RULES) {
      redacted = rule.apply(redacted);
    }
    return redacted;
  }

  /**
   * Redacts the value of a structured field: the whole value when the field is named like a secret,
   * otherwise the value as a free text.
   *
   * @param key the field's name, possibly null (an array element)
   * @param value the field's value
   * @return the redacted value
   */
  public static @Nullable String redact(@Nullable String key, @Nullable String value) {
    if (value != null && key != null && isSensitiveKey(key)) {
      return REDACTED;
    }
    return redact(value);
  }

  /**
   * Whether a field of this name holds a secret whatever its value looks like.
   *
   * @param key the field's name
   * @return true for a name like {@code *token*}, {@code *secret*}, {@code *password*}, {@code
   *     *api_key*}, {@code authorization} or {@code cookie}
   */
  public static boolean isSensitiveKey(String key) {
    return SENSITIVE_KEY.matcher(key).find();
  }

  /**
   * Runs the rules on a canary line carrying one secret of each kind. The OTLP log export starts
   * only when this passes: logs leave a service redacted or not at all. A rule that throws makes
   * the caller fail, which keeps the export off as well.
   *
   * @return true when nothing of the canary survives
   */
  public static boolean selfTest() {
    String canary =
        "user "
            + CANARY_SECRETS.getFirst()
            + " Authorization: Bearer "
            + CANARY_SECRETS.get(1)
            + " session "
            + CANARY_SECRETS.get(2)
            + " password="
            + CANARY_SECRETS.getLast();
    String redacted = redact(canary);
    return redacted != null && CANARY_SECRETS.stream().noneMatch(redacted::contains);
  }
}
