# krizaka-observability

The observability standard of a Krizaka service: the names it is observed under, the common metric tags, the ECS
JSON logs and the OTLP export of traces, metrics and logs. Configuration and defaults:
[krizaka-service-kit/README.md](../README.md#krizaka-observability).

## The three names

| Variable | Typed property | What |
|:---|:---|:---|
| `OBSERVABILITY_OTLP_ENDPOINT` | `krizaka.observability.otlp-endpoint` | base URL of the receiver: `/v1/traces`, `/v1/metrics`, `/v1/logs` |
| `OBSERVABILITY_INGEST_TOKEN` | `krizaka.observability.ingest-token` | the bearer `Authorization` header of every export; never logged |
| `OBSERVABILITY_ENVIRONMENT` | `krizaka.observability.environment` | the resource attribute `deployment.environment.name` |

With no endpoint nothing is exported. A service's own `application*.yml` never sets an OTLP endpoint, URL or header:
the starter owns them, and `ServicesLeaveTheOtlpEndpointToTheStarterTest` refuses a hand-set one in this repository.

## Logs leave a service only redacted (Law 25)

One rule set, `LogRedaction.RULES` (an immutable list, run in this order), is applied to the local ECS JSON log and to
the OTLP log export, so the two can never differ. Each match becomes `[REDACTED]`:

| Rule | What it hides |
|:---|:---|
| `authorization` | the value of an `Authorization` / `Proxy-Authorization` header, scheme included |
| `bearer` | a `Bearer` token anywhere in a text |
| `jwt` | a JWT-shaped string (`eyJ….….…`), even without a key or a scheme |
| `secret` | the value assigned to a key named like `*token*`, `*secret*`, `*password*`, `*passwd*`, `*api[_-]key*` (`key=value`, `key: value`, JSON, query strings) |
| `email` | an e-mail address |

A structured field (MDC entry, key-value pair, JSON member) whose **name** looks like a token, a secret, a password,
an API key, `authorization` or `cookie` loses its whole value. The message, every field, the exception message and
the stack trace pass the rules. Every pattern is bounded, so a hostile line cannot stall the logging thread.

- **Console and files** (`logging.structured.format.*`: ECS by default, Logstash, GELF): `RedactingJsonMembersCustomizer`,
  registered in `META-INF/spring.factories`, rewrites every value Spring Boot writes; the line stays strict JSON.
  A plain-text pattern log (a developer who turns structured logging off) is not redacted: it stays on the machine.
- **OTLP**: Spring Boot builds the log exporter; `RedactingOtlpLogAppender` is the only bridge from Logback to the
  OpenTelemetry SDK and redacts each event before the SDK sees it. The exporter's own logs (`io.opentelemetry.*`) are
  not fed back.

**Fail closed.** The log export is switched on only when an endpoint is set **and** the redaction is installable:
Logback is present and `LogRedaction.selfTest()` hides every part of its canary. Otherwise
`management.logging.export.otlp.enabled=false`, a warning names the reason (never a value), and traces and metrics
still leave. If Logback is not the running logging system when the context starts, the appender refuses to attach and
the application does not start.
