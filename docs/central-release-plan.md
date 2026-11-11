# The building blocks missing from Maven Central: the check and the plan

This is phase 1 of krizaka/krizaka-java#82, written 2026-10-23. Nothing here releases anything. Releases are done by the CTO and Oussama, never by an agent.

It builds on the issues already open:
- **#11:** the BOM names unpublished versions;
- **#15:** the November releases;
- **#14:** the secrets and the `maven-central` environment;
- **#13:** the Central token;
- **#10:** releases from CI.

## 1. What Central has

Each artifact's `maven-metadata.xml` on `repo1.maven.org` against the published `krizaka-bom-0.2.0.pom`, read on 2026-10-23:

| Block on the picker | Artifact (`site/lib/project.ts:23-25`) | Module | Central has | BOM 0.2.0 manages |
|---|---|---|---|---|
| App config | `krizaka-spring-boot-starter-app-config` → `krizaka-app-config` | krizaka-service-kit `starters/krizaka-spring-boot-starter-app-config/pom.xml`, `krizaka-app-config/pom.xml` | **nothing** | 0.2.0 |
| Users client | `krizaka-users-client` (→ `krizaka-users-api`) | krizaka-users `krizaka-users-client/pom.xml` | 0.1.0 | 0.2.0 |
| Billing client | `krizaka-billing-client` (→ `krizaka-billing-api`) | krizaka-billing `krizaka-billing-client/pom.xml` | 0.1.0 | 0.2.0 |

The picker shows three blocks; the problem is wider. **The published BOM 0.2.0 manages 17 artifacts that do not resolve at the version it names:**
- 10 from the kit: `krizaka-app-config`, `krizaka-idempotency`, `krizaka-persistence`, `krizaka-webhooks`, `krizaka-ratelimit`, and their five starters;
- `krizaka-users-{api,client,persistence,core}`;
- `krizaka-notifications-api`;
- `krizaka-billing-{api,client}`.

These do resolve: `krizaka-parent`, `krizaka-bom`, `krizaka-test-support`, `krizaka-security`, `krizaka-messaging`, `krizaka-web`, `krizaka-observability`, and the web, security, rabbitmq and observability starters, all at 0.2.0.

## 2. Why each one is missing

| Block | Cause |
|---|---|
| App config (and idempotency, persistence, webhooks, ratelimit, with their starters) | **Added to the kit after its `v0.2.0` tag** (2026-10-10). The reactor at `v0.2.0` lists security, messaging, web, observability and starters only. `main` still carries 0.2.0 until release-please's krizaka-platform-kit#10 (« release 0.3.0 », open) is merged. That PR bumps every module, the new ones included. |
| Users client | **0.2.0 was never released.** krizaka-users has no tag, no release-please and no `maven-central` environment. Its version is inherited from `krizaka-parent` 0.2.0 (`pom.xml:12-16`, no `<version>` of its own). Its CHANGELOG sits under « Unreleased ». The japicmp gate is relaxed until 0.2.0 ships (`pom.xml:52-56`). |
| Billing client | **Same as users:** no tag, no release-please, no environment, the version inherited from the parent. |
| All of them | **The CI release path has never worked.** The kit's `v0.2.0` tag run failed at « Sign and upload to Maven Central »: `maven-gpg-plugin … Secret key not found` (run 38009874948). The 0.1.0 and 0.2.0 artifacts were published from a laptop (#10). |
| The BOM | **It names versions without checking them** (#11). Its properties are typed (`krizaka-bom/pom.xml:26-29`, all `0.2.0`), and nothing in the `release` profile checks that they resolve. |

## 3. Are the blocks ready?

| Block | Tests | README | API | Depends on unpublished? | Verdict |
|---|---|---|---|---|---|
| App config | 10 test files + `AppConfigGovernanceTest`; the starter has 1 | `krizaka-app-config/README.md`; guide `site/content/docs/app-config.mdx` | the version contract is shared with `@krizaka/app-config` (krizaka-ui, « twin of the Java building block ») | no: Spring only; `krizaka-test-support` in test scope (0.2.0 on Central) | **ready**; ships with kit 0.3.0 |
| Users client | 3 test files; CI green on `main` (2026-10-23) | the repository's README (no module README) | 0.2.0 breaks 0.1.0 on purpose, a minor before 1.0 (`pom.xml:52-55`) | `krizaka-users-api` 0.2.0, released in the same tag; `krizaka-security` 0.2.0 is on Central | **ready** once released with its api |
| Billing client | 5 test files | the repository's README | — | `krizaka-billing-api` 0.2.0 (same tag); `krizaka-security` 0.2.0 on Central | **ready** once released with its api |

**Publishing order (parent first):**
1. `krizaka-parent` 0.2.0 (on Central)
2. kit 0.3.0
3. users 0.2.0
4. notifications 0.2.0
5. billing 0.2.0
6. **then** the BOM 0.3.0 with properties set to those versions

The domain repositories build against parent 0.2.0 and security 0.2.0, which are already published.

## 4. The plan

### 4.1 A trap to avoid first

**Do not merge krizaka-java#8 (« release 0.3.0 ») as it is.** It bumps the BOM to 0.3.0 but leaves its properties at `0.2.0`. BOM 0.3.0 would therefore manage the kit at 0.2.0, which still has no app-config, and the domain blocks at an unpublished 0.2.0: the same 17 broken artifacts in a new, immutable release. It merges **last**, after §4.3's steps 1–3 and the properties PR.

### 4.2 Code and POM fixes (phase 2, agent PRs, no release)

| # | Repository | Fix | Test |
|---|---|---|---|
| 1 | krizaka-java | **The BOM release guard (#11 item 1).** Before signing in the `release` job, a dependency-free script reads every managed `com.krizaka` artifact at its property's version from `repo1.maven.org` and fails with the list of the missing ones. It runs only on a tag, where the network exists. | Unit test on fixtures (a metadata file with and without the version); no network in the test. |
| 2 | krizaka-java | **The properties PR.** `krizaka-service-kit.version` → 0.3.0; users, notifications and billing stay 0.2.0. Opened only after those releases exist, so the guard passes. | The guard itself. |
| 3 | krizaka-users, krizaka-notifications, krizaka-billing | **release-please, as in the kit** (`release-please-config.json`, manifest, `release-please.yml` dispatching `ci.yml` on the tag). Each repository gets its own `<version>` instead of inheriting the parent's, because a parent bump must never re-version a domain block. | CI green; the release PR lists the right files. |
| 4 | krizaka-users | Restore `japicmp.breakBuildOnBinaryIncompatibleModifications=true` once 0.2.0 is on Central (#15). | `verify -Prelease`. |
| 5 | krizaka-java `site/` | **Refresh after a release elsewhere.** App Platform builds the site only on a push to `main` (`.do/app.yaml` `deploy_on_push`); the weekly cron in `site.yml` runs CI, not a deployment. Add the pattern krizaka/.github `changelog-rebuild.yml` already uses for krizaka-com: on `repository_dispatch: central-release` and daily, `doctl apps create-deployment --force-rebuild` for `krizaka-java-site`, with the `dev` environment's `DIGITALOCEAN_ACCESS_TOKEN`. Each Java repository's release job sends the dispatch after its upload. | `tests/` on the workflow (as krizaka/.github `tests/ci-workflows.test.mjs` does). |

### 4.3 The releases (CTO and Oussama, in this order)

**Prerequisites (Oussama):**
- #13: a new Central token.
- #14: the organisation secrets `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `MAVEN_GPG_KEY` and `MAVEN_GPG_PASSPHRASE`, and the `maven-central` environment on krizaka-users, krizaka-notifications and krizaka-billing. Today it exists only on krizaka-java and krizaka-service-kit.
- The Central publishing quota for the month (#15: the October allowance was spent).

**The steps:**
1. **Kit 0.3.0.** Merge krizaka-platform-kit#10. release-please tags `v0.3.0` and dispatches `ci.yml` on it. The `maven` job verifies, signs and uploads. Then press **Publish** on central.sonatype.com (`autoPublish=false`). This first CI release is the test of #10 and #14.
2. **Users 0.2.0, notifications 0.2.0, billing 0.2.0, in that order.** With §4.2 #3 merged, each one's release-please PR is merged, which tags `v0.2.0`, uploads, and waits for Publish. Without #3, a CTO pushes the `v0.2.0` tag by hand on `main`, and the same job runs.
3. **Check:** `curl -s https://repo1.maven.org/maven2/com/krizaka/<artifact>/maven-metadata.xml` lists the new versions for the 17 artifacts. The search index lags; the metadata file does not.
4. **BOM 0.3.0.** Merge §4.2 #2, the properties PR, then krizaka-java#8 rebased. The guard (§4.2 #1) proves that every managed artifact resolves before signing. Publish.
5. **The site.** The krizaka-java release is a push to `main`, so java.krizaka.com rebuilds. `gen-registry-versions.mjs` reads BOM 0.3.0, and the three blocks turn from « pending » to « ready » (`site/lib/project.ts:42-47`).

**Can the release move to CI?** Yes, and it is designed for it: `krizaka/.github` `maven.yml` signs and uploads on a `v*` tag in the `maven-central` environment, and a human presses Publish. What blocks it is only §4.3's prerequisites. A local release stays the fallback, never the norm.

**Hide the blocks until ready?** No. The picker already shows them as « not on Maven Central at the BOM's version yet », and its snippet only includes ready blocks (`mavenSnippet`, `site/lib/project.ts:53-56`). That is honest, and it will update itself.

## 5. Alignment with the TypeScript module map (krizaka/krizaka-ts#47)

The map is ADR-001 and `docs/design/module-map.md` §4 in krizaka-ts (PR krizaka/krizaka-ts#48).

| Java block | TypeScript module | Names | Boundary |
|---|---|---|---|
| `krizaka-users` (`-api`, `-client`, `-core`, `-persistence`, `-service`) | `@krizaka/auth` | they differ: the TS packages are published and the Java artifacts are on Central, so both names stay and the docs say « auth ↔ users » | **Java:** a central identity service (JWT, OAuth, API keys). **TS:** accounts inside each product's database. They meet at **federation**: `@krizaka/auth`'s `IdentityProvider` port with a krizaka-users adapter (krizaka/krizaka-ts#32). The users client is what a Java service uses; a TS product never calls it directly. |
| `krizaka-billing` (`-api`, `-client`, `-service`) | `@krizaka/credits` + `@krizaka/payments` | partly the same | **One ledger model:** buckets `GRANTED`/`PURCHASED`; entries `GRANT\|PURCHASE\|DEBIT\|REFUND\|EXPIRY\|ADJUSTMENT`; holds with a TTL; `idempotency_key` per entry (`krizaka-billing/infra/initdb/70-billing.sql:145-184`); events `evt.credit.*.v1`. Java has no payment provider; TS has Stripe. Coupons get a Java twin in krizaka-billing for Sovkora (krizaka/krizaka-ts ADR-002 §7). |
| `krizaka-app-config` + starter | `@krizaka/app-config` (krizaka-ui) | **the same** | One contract: the JSON Schema `@krizaka/app-config/contract/app-config.schema.json`, served by both. It is not `@krizaka/settings`: app-config is version compatibility, and settings are the admin's parameters. |
