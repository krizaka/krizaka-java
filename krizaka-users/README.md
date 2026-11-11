<!-- krizaka-header -->
<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/krizaka-dark.svg">
  <img src="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/krizaka-light.svg" alt="Krizaka" width="96">
</picture>

# Krizaka Users

**User management you do not have to write again.**

Registration with e-mail verification, password and Google/GitHub login, forgot/reset password, profile and
preferences, API keys, provider credentials, RBAC and JWT issuance — as a Spring Boot service, a library, a contract
and a typed client.

[![CI](https://github.com/krizaka/krizaka-java/actions/workflows/ci.yml/badge.svg)](https://github.com/krizaka/krizaka-java/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/krizaka/krizaka-java/badge)](https://scorecard.dev/viewer/?uri=github.com/krizaka/krizaka-java)
[![Maven Central](https://img.shields.io/maven-central/v/com.krizaka/krizaka-users-api?color=196df5&label=maven%20central)](https://central.sonatype.com/namespace/com.krizaka)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

[Open source at Krizaka](https://www.krizaka.com/en/open-source) · [Website](https://www.krizaka.com) · [Krizaka on GitHub](https://github.com/krizaka)

</div>
<!-- /krizaka-header -->

Built for and used by [Sovkora](https://github.com/krizaka/sovkora), the sovereign AI engine; usable by any Spring Boot application.

## What it does

| Capability | Endpoint |
|:---|:---|
| Register (an e-mail verification token is issued) | `POST /api/v1/auth/register` |
| Verify the e-mail address | `POST /api/v1/auth/verify` |
| Log in with a password → session JWT | `POST /api/v1/auth/login` |
| Log in with Google or GitHub | `POST /api/v1/auth/oauth` |
| Forgot password (single-use token, SHA-256 hashed, 15 minutes) | `POST /api/v1/auth/forgot` |
| Reset password | `POST /api/v1/auth/reset` |
| Profile and preferences | `GET /api/v1/profile` · `PUT /api/v1/profile/preferences` |
| Organizations | `GET/POST /api/v1/organizations` · `GET /{id}` · `GET/PUT /{id}/members` · `DELETE /{id}/members/{userId}` · `POST /{id}/service-accounts` · `GET /{id}/service-keys` · `DELETE /{id}/service-keys/{keyId}` (owner or administrator; a `svc_*` account is attached as `SERVICE`; schema: `10-identity.sql` and Flyway `V4__organizations.sql`) |
| API keys | `GET/POST /api/v1/api-keys` · `DELETE /api/v1/api-keys/{id}` |
| Provider credentials (bring your own key, encrypted at rest) | `GET/POST /api/v1/credentials` · `DELETE /api/v1/credentials/{provider}` |
| Provider-key grant for one run (on the user's own session) | `POST /api/v1/credentials/{provider}/grants` `{runId}` → `201 {grant, expiresAt}` · `404` without a key |
| Service-to-service — `SERVICE` authority only | `/internal/v1/users/{id}` · `/internal/v1/tokens/exchange` · `/internal/v1/tiers/*` · `POST /internal/v1/credentials/{provider}/use` `{grant, runId}` (allowed callers only; `403` Problem Details when the grant is refused) |

A decrypted provider key leaves the service **only** against a grant (GHSA-3pf7-rgf2-4x3m): the service that owns a run
forwards the user's session to `…/grants` when the user starts it, carries the grant with the run, and redeems it once
at the model call. A grant is 256 random bits stored as its SHA-256, bound to the user, the provider and the run, lives
`AUTH_CREDENTIAL_GRANT_TTL` (default 10 min, at most 1 h) and is consumed by one conditional `UPDATE`, so it cannot be
used twice. Issue, use and refusal are audited (`admin_audit`). A service token alone never reads a key; the guard is
`CredentialReleaseGuardTest` (ArchUnit).
Until the issuer-signed sessions and per-caller keys are set (`AUTH_JWT_SIGNING_*`, `jwt.callers`, `AUTH_JWT_HS256_CUTOFF`),
whoever holds the shared HS256 secret can still forge both a user session and an allowed caller: set them to close it.

Passwords are hashed with BCrypt, provider keys encrypted with AES-256, sessions are HS256 JWTs carrying a `roles`
claim that [`krizaka-security`](../krizaka-service-kit) verifies locally in every other
service. Domain events leave through a transactional outbox (relayed by `krizaka-messaging`): `evt.user.registered`
and `evt.password.reset`, which [`krizaka-notifications`](../krizaka-service-kit/docs/notifications.md) turns into
the verification and reset e-mails. The outbox also implements `krizaka-messaging`'s `OutboxStore.append`, so an
`EventPublisher` can write to it: the event's `messageId` and envelope headers (`kz-type`, `kz-version`, …) are stored
in `identity_outbox` (`headers jsonb`) and published as stored.

## Profiles and onboarding are yours

The service knows users, not your product. A profile is a theme plus **attributes your application defines** — the
answers of your onboarding form — stored as given and never interpreted. You decide which forms users see:

```yaml
krizaka:
  users:
    interceptions:
      schemas:
        onboarding: file:/etc/myapp/onboarding-schema.json   # USERS_ONBOARDING_SCHEMA
        feedback: file:/etc/myapp/feedback-schema.json       # USERS_FEEDBACK_SCHEMA
    preferences:
      reserved-prefixes: myapp.                              # USERS_RESERVED_PREFERENCE_PREFIXES
```

The built-in forms ask only for a theme, a language and a display name. `reserved-prefixes` lists the preference
namespaces your application reads as configuration, so a user can never store a preference that impersonates it
(`preference.` is always reserved). Read your attributes with your own defaults:

```java
UserProfile profile = users.getProfile(userId);
String plan = (String) profile.attributes().getOrDefault("plan", "free");
```

## Modules

| Artifact | Published | Role |
|:---|:--:|:---|
| `com.krizaka:krizaka-users-api` | ✓ | The contract: `User`, `UserProfile`, `RateLimitInfo` and the `UserDirectoryClient` port. Other services depend on **this**. |
| `com.krizaka:krizaka-users-client` | ✓ | `UserDirectoryClient` over HTTP: `SERVICE` token on every call, per-entry cache. Spring Boot auto-configuration. |
| `com.krizaka:krizaka-users-core` | ✓ | Domain and application services: registration, BCrypt, JWT, OAuth federation, RBAC, password recovery, profile. Embed it to host users inside your own application. |
| `com.krizaka:krizaka-users-persistence` | ✓ | JPA entities and repositories of the users database, and its outbox. |
| `krizaka-users-service` | — | The Spring Boot host (port `8083`). Built from source. |
| `infra/initdb/10-identity.sql` | — | Schema, role and development seed of the users database. |

## Call it from another service

```xml
<dependency>
    <groupId>com.krizaka</groupId>
    <artifactId>krizaka-users-client</artifactId>
    <version>0.1.0</version>
</dependency>
```

```yaml
krizaka:
  users:
    client:
      base-url: http://users:8083
      service-key: ${AUTH_SERVICE_KEY}         # this caller's own P-256 private key (PEM); users holds the public half
      service-key-id: billing-1               # its kid
      # service-secret: ...                   # legacy shared HS256 secret; refused after the users service's hs256-cutoff
      service-name: billing-service           # who is calling (the token's subject)
      cache-ttl: PT60S
```

```java
@Service
class InvoiceService {
  private final UserDirectoryClient users;
  InvoiceService(UserDirectoryClient users) { this.users = users; }

  String recipient(String userId) {
    return users.getUser(userId).email();
  }
}
```

## Events it publishes

Through its outbox, on the platform's events exchange (`krizaka.messaging.exchanges.events`); the body is the bare event,
the envelope travels in the `kz-*` AMQP headers. Each event has a JSON Schema (draft 2020-12) in `krizaka-users-api`:

| Routing key | Schema |
|:---|:---|
| `evt.user.registered` | [`events/evt.user.registered.v1.json`](krizaka-users-api/src/main/resources/events/evt.user.registered.v1.json) |
| `evt.password.reset` | [`events/evt.password.reset.v1.json`](krizaka-users-api/src/main/resources/events/evt.password.reset.v1.json) |

A consumer keeps its own copy of the event and checks it against the schema with `krizaka-test-support`'s
`EventContractTest` (`krizaka-users-api` as a `test` dependency only).

## Run the service

```bash
./mvnw -pl krizaka-users-service -am spring-boot:run
```

| Property | Environment | Default |
|:---|:---|:---|
| `krizaka.users.jwt.secret` | `IDENTITY_JWT_SECRET` | — (≥ 32 characters, required) |
| `krizaka.users.jwt.issuer` | `IDENTITY_JWT_ISSUER` | `krizaka-users` |
| `krizaka.users.jwt.ttl` | `IDENTITY_JWT_TTL` | `PT12H` |
| `krizaka.users.crypto.key` / `.salt` | `CRYPTO_KEY` / `CRYPTO_SALT` | — (provider-key encryption) |
| `krizaka.users.credential-grant.ttl` | `AUTH_CREDENTIAL_GRANT_TTL` | `PT10M` — how long a provider-key grant stays redeemable (1 min to 1 h) |
| `krizaka.users.internal.allowed-callers[/internal/v1/credentials/**]` | `AUTH_CREDENTIAL_REDEEMERS` | `sovkora-conversation-service,sovkora-job-service` — who may redeem a grant |
| `krizaka.users.vault.key` / `.key-id` | `AUTH_VAULT_KEY` / `AUTH_VAULT_KEY_ID` | — (32 bytes, base64) / `k1` — the key that seals the identity secrets (see « The vault ») |
| `krizaka.users.password.breach-check` | `AUTH_PASSWORD_BREACH_CHECK` | `true` — a new password is checked against Pwned Passwords (range API, padded; fail-open) |
| `krizaka.users.email-verification.enabled` | `EMAIL_VERIFICATION_ENABLED` | `true` |
| `krizaka.users.registration.enabled` | `AUTH_SIGNUP_ENABLED` | `true` — `false` closes account creation (register → 403, first sign-in of an unknown Google/GitHub identity → 403); existing accounts keep signing in |
| `krizaka.users-service.datasource.url` | `IDENTITY_DB_URL` | `jdbc:postgresql://localhost:5432/krizaka_users_db` |
| `krizaka.users-service.datasource.username` / `.password` | `IDENTITY_DB_USERNAME` / `IDENTITY_DB_PASSWORD` | `krizaka_users` / — |
| `server.port` | `IDENTITY_PORT` | `8083` |

PostgreSQL and RabbitMQ are required. The database, its role (created **without** a password — set it at deployment)
and the schema come from [`infra/initdb/10-identity.sql`](infra/initdb/10-identity.sql).
Later schema changes are Flyway migrations (`krizaka-users-persistence/src/main/resources/db/migration`, `V<n>__<what>.sql`,
idempotent) applied by the service itself at startup, as its own role; the database is baselined at version 1. Never apply
SQL by hand.

### Passwords (ADR-016 slice 2)

New passwords are hashed with **Argon2id** (`{argon2}`, 19 MiB, 2 passes), NFKC-normalised, **15 characters minimum** (8 once a second
factor exists), 256 at most, no composition rule, and refused when they appear in a known breach (Pwned Passwords range API with
padding: only five hex characters of the SHA-1 leave; unreachable = accepted and logged). The BCrypt hashes already stored (no
prefix) and the products' scrypt hashes still verify, and are rehashed to Argon2id after a successful sign-in without invalidating
sessions. A right password is looked up in the breach corpus again at every sign-in (a password can be breached after it was
chosen): a hit leaves an `identity.password_breached_at_signin` audit line (user id, never the password), the sign-in is not blocked
and an unreachable service is ignored (logged).

**Lockout of failed sign-ins** is durable (`login_lockout`, Flyway V6): one counter per account from one address and one per account
from anywhere, keyed by a SHA-256 of the lower-cased account (and address), so the table holds no mailbox and a changed case buys
nothing. Past the threshold every further failure doubles the lock up to a ceiling; a locked account refuses even the right
password; a success clears the address counter only. It survives a restart and is shared by every instance. The thresholds
(`threshold` 5, `accountThreshold` 25, `baseDelaySeconds` 60, `maxDelaySeconds` 900, `forgetAfterSeconds` 1800, ceiling 24 h) are the
single row of `lockout_settings`, read and replaced by an administrator at `GET|PUT /api/v1/admin/lockout-policy`, each change audited
(`identity.lockout_policy_changed`, old and new figures). The old `krizaka.users-service.rate-limit.lockout.*` properties are gone.
If the store is unreachable the check is skipped and logged; the per-address budget still applies.

**The GitHub verifier** reads its client secret from the vault at each call (the environment value is only the fallback while no
vault key is set), so a rotation by an administrator needs no restart.

### The vault (ADR-016 slice 1)

Identity secrets (today the OAuth client secrets of Google and GitHub) are stored in `social_connection` as an envelope,
`v2.<key_id>.<wrapped data key>.<sealed secret>` (AES-256-GCM, a fresh data key per value wrapped by `AUTH_VAULT_KEY`):
the format of `@krizaka/storage`'s vault, with a cross-language test vector. A database dump reads no secret, and the
table itself refuses a value that is not an envelope. The admin API (`GET /api/v1/admin/social-connections`) answers
*secret set* or *not set*, never a value; secrets are opened in one place (`SocialConnectionService.clientSecret`) and never logged.

- **First start:** the OAuth clients of the environment are imported once as sealed connections (`source: env`), each with
  an `identity.social_connection_imported` line in the audit trail. Without `AUTH_VAULT_KEY` nothing is imported (a warning, no secret in it).
- **Rotating the key:** set the new `AUTH_VAULT_KEY` with a new `AUTH_VAULT_KEY_ID`, and keep the old one as
  `KRIZAKA_USERS_VAULT_RETIRED_<OLD_ID>`. The next start (or `POST /api/v1/admin/social-connections/rewrap`) re-wraps every
  data key under the new key (`identity.social_connection_rewrapped`): no secret is retyped. Then drop the retired key.
- **Losing the key:** the sealed secrets are unreadable. Keep `AUTH_VAULT_KEY` in the secret manager with a backup outside the
  deployment; if it is lost, set the OAuth clients again from the providers' consoles.

> **Messaging.** Events are published to the events exchange of the platform the service runs on:
> `krizaka.messaging.exchanges.events` (`EVENTS_EXCHANGE`, default `krizaka.events`) and
> `krizaka.messaging.exchanges.dead-letter` (`DLX_EXCHANGE`, default `krizaka.dlx`).

## Build

```bash
./mvnw verify                        # unit and Testcontainers integration tests (Docker required)
./mvnw verify -Prelease -Dgpg.skip   # + the sources and javadoc jars Maven Central requires
```

It inherits [`krizaka-parent`](https://github.com/krizaka/krizaka-java) and uses
[`krizaka-service-kit`](../krizaka-service-kit): build those first, or let CI do it. JDK 21.

## Design notes

- ADR-001 — Krizaka accounts: the thin community layer (comments, saved posts, reply e-mails) planned over this block for accounts.krizaka.com; moved here from the former `krizaka-accounts` repository.

## Contributing

Issues and pull requests are welcome — see the organisation's
[contributing guide](https://github.com/krizaka/.github/blob/main/CONTRIBUTING.md) and
[security policy](https://github.com/krizaka/.github/blob/main/SECURITY.md).

## License

[Apache License 2.0](LICENSE) © 2026 Krizaka

---

<sub>A Krizaka open-source project · [krizaka.com](https://www.krizaka.com) · Apache-2.0</sub>
