<!-- krizaka-header -->
<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/krizaka-dark.svg">
  <img src="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/krizaka-light.svg" alt="Krizaka" width="96">
</picture>

# Krizaka Java

**One build for every Krizaka JVM artifact.**

The parent POM, the BOM and the test kit behind every `com.krizaka` artifact on Maven Central: complete Central metadata, Java 21
conventions checked on every build, and a signed release in one command.

[![CI](https://github.com/krizaka/krizaka-java/actions/workflows/ci.yml/badge.svg)](https://github.com/krizaka/krizaka-java/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/krizaka/krizaka-java/badge)](https://scorecard.dev/viewer/?uri=github.com/krizaka/krizaka-java)
[![Maven Central](https://img.shields.io/maven-central/v/com.krizaka/krizaka-bom?color=196df5&label=maven%20central)](https://central.sonatype.com/namespace/com.krizaka)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=krizaka_krizaka-java&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=krizaka_krizaka-java) [![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_krizaka-java&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_krizaka-java) [![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_krizaka-java&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_krizaka-java) [![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_krizaka-java&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_krizaka-java) [![Coverage](https://sonarcloud.io/api/project_badges/measure?project=krizaka_krizaka-java&metric=coverage)](https://sonarcloud.io/summary/new_code?id=krizaka_krizaka-java)

[Documentation](https://java.krizaka.com) · [Open source at Krizaka](https://www.krizaka.com/en/open-source) · [Website](https://www.krizaka.com) · [Krizaka on GitHub](https://github.com/krizaka)

</div>
<!-- /krizaka-header -->

> **Renamed on 2026-11-06.** This repository was `krizaka-build` (GitHub redirects the old address). The family version
> property is `krizaka-java.version` (was `krizaka-build.version`), the folder `krizaka-platform-kit` is `krizaka-service-kit`
> (artifact `com.krizaka:krizaka-service-kit`; the old aggregator stays published, deprecated) and the Sonar project is
> `krizaka_krizaka-java`. Coordinates of the modules themselves did not change.

## What it provides

| Artifact | Packaging | Role |
|:---|:---|:---|
| `com.krizaka:krizaka-parent` | pom | Maven Central metadata (licence, developers, SCM, issues), Java 21, google-java-format checked at `validate`, unit tests (Surefire) and integration tests (Failsafe, `*IT`), JaCoCo, an enforced toolchain (Maven ≥ 3.9, Java ≥ 21, every plugin pinned), and the `release` profile. |
| `com.krizaka:krizaka-bom` | pom | Every `com.krizaka` artifact, at the release of each repository this BOM was tested with: the compatible set. Import it once; it never changes a third-party version. |
| `com.krizaka:krizaka-test-support` | jar (test scope) | The governance kit of the Krizaka repositories: `CodeRules` (hexagonal layering, one class per file, constructor injection, private state, mappers, domain purity), `SourceRules` (no `Environment` injection, virtual threads), `ConfigBindingRules` (beans and configuration types Spring can build), `InitDb` (locates `infra/initdb`), `AbstractContainerIntegrationTest` (one shared PostgreSQL + RabbitMQ per run, wired into Spring), `ServiceRoles` (Testcontainers) and `EventContractTest` (an event serialised by its producer, or read by a consumer's copy, checked against the JSON Schema its `-api` publishes at `events/<routing-key>.v<n>.json`, draft 2020-12). Every rule fails on an empty population — a rule that judges nothing passes nothing. |

## Use the BOM

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.krizaka</groupId>
            <artifactId>krizaka-bom</artifactId>
            <version>0.2.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.krizaka</groupId>
        <artifactId>krizaka-security</artifactId>
    </dependency>
</dependencies>
```

> **BOM 0.2.0 and the domain blocks.** `krizaka-bom:0.2.0` manages `krizaka-users-*`, `krizaka-notifications-api` and
> `krizaka-billing-*` at 0.2.0, which is not on Maven Central yet (November). Until BOM 0.3.0, declare them with
> `<version>0.1.0</version>`, their published release
> ([#11](https://github.com/krizaka/krizaka-java/issues/11)).

The BOM deliberately carries **only** Krizaka artifacts. The parent therefore has no `<dependencyManagement>` of its own:
a BOM imports its parent's managed versions, and importing `krizaka-bom` must never pin a Spring version you did not
choose. The artifacts are built and tested against Spring Boot `4.0.x` (property `spring-boot.version`).

## Build a Krizaka repository on it

```xml
<parent>
    <groupId>com.krizaka</groupId>
    <artifactId>krizaka-parent</artifactId>
    <version>0.2.0</version>
    <relativePath/>
</parent>
```

| Command | What runs |
|:---|:---|
| `./mvnw verify` | formatting check, compile with `-Xlint:all`, unit and integration tests, coverage report |
| `./mvnw spotless:apply` | formats the sources (google-java-format) |
| `./mvnw verify -Prelease -Dgpg.skip` | everything Central requires except the signature: sources and javadoc jars (javadoc is linted), and the binary compatibility of every jar with its latest release (japicmp) |
| `./mvnw deploy -Prelease,registry-central` | signs every file and uploads the deployment to the Central Portal |

## Where the blocks are published — Maven Central only

**Maven Central is the one registry of `com.krizaka`** (founder, 2026-11-06): nothing to configure, no token to read.
A `v*` tag (release-please) signs every file and uploads the whole reactor to the Central Portal from CI (the `registry:
central` input of the organisation's `maven.yml`, secrets `MAVEN_CENTRAL_*` and `MAVEN_GPG_*`); the deployment waits for
**Publish** on central.sonatype.com (`autoPublish=false`: a Central release can never be deleted), then
`scripts/check-published.mjs` proves every module is on `repo1.maven.org`.

Versions 0.3 to 0.11 were published to GitHub Packages only (ADR-019, now abandoned for Maven); they stay readable there
for now and are never republished. A consumer pins a version that is on Central.

**Guard** — `scripts/check-bom-released.mjs` (CI job `bom-released`) fails when `krizaka-bom` manages a building block
version that has no `v<version>` tag in its repository; `krizaka-users`, `krizaka-notifications` and `krizaka-billing`
are listed there until their first release (the list only shrinks).

## Releasing (Central steps apply when `registry` includes `central`)

Each Krizaka JVM repository has its own [SemVer](https://semver.org/) version; **`krizaka-bom` carries the compatible
set** — one property per repository (`krizaka-service-kit.version`, …) names the release it was tested with.

- **Conventional Commits** — pull request titles are checked by `commitlint` (the squash merge keeps the title).
- **release-please** keeps a release pull request open on `main` (`pom.xml` versions, CHANGELOG). Merging it tags
  `v<version>`, creates the GitHub Release and dispatches CI on the tag (a tag pushed by `GITHUB_TOKEN` starts no
  workflow by itself), which runs the organisation pipeline
  [`krizaka/.github/.github/workflows/maven.yml`](https://github.com/krizaka/.github/blob/main/.github/workflows/maven.yml):
  verify, sign, upload. After a release, release-please proposes the next `-SNAPSHOT`.
- **Order** — Central validates a POM against its parent *on Central*: release `krizaka-java` first and publish it;
  then move `krizaka-parent` (and `krizaka-java.version`) in the repositories built on it to that release, and
  release them (`krizaka-service-kit`, then the domain services). Before tagging `krizaka-java`, set each BOM
  property to the version of that repository it ships with.
- **Binary compatibility** — the `release` profile compares every jar with its latest release on Central (japicmp)
  and fails on a binary-incompatible change. A deliberate break (a major, or a minor before 1.0) sets
  `japicmp.breakBuildOnBinaryIncompatibleModifications=false` in that repository for that release.
- **Signature** — the Bouncy Castle signer of `maven-gpg-plugin` reads `MAVEN_GPG_KEY` (ASCII-armoured private key) and
  `MAVEN_GPG_PASSPHRASE`; no `gpg` binary is needed.
- **Upload** — `central-publishing-maven-plugin`, server id `central`, a Central Portal user token in
  `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD`. Secrets live in the organisation, never in a repository.
- **Publication is manual** — `central.autoPublish` defaults to `false`: the deployment is validated, then waits on
  [central.sonatype.com](https://central.sonatype.com/publishing/deployments) until someone presses *Publish*. A release
  on Central can never be deleted, so it is checked by eye first. Pass `-Dcentral.autoPublish=true` once the process is
  trusted.

Releasing by hand works the same way:

```bash
export MAVEN_GPG_KEY="$(gpg --armor --export-secret-keys <key-id>)" MAVEN_GPG_PASSPHRASE=…
./mvnw versions:set -DnewVersion=0.1.0 -DprocessAllModules -DgenerateBackupPoms=false
./mvnw deploy -Prelease          # with the `central` server in ~/.m2/settings.xml
```

## Contributing

Issues and pull requests are welcome — see the organisation's
[contributing guide](https://github.com/krizaka/.github/blob/main/CONTRIBUTING.md) and
[security policy](https://github.com/krizaka/.github/blob/main/SECURITY.md). `main` is protected: every change is a pull
request with a green CI.

<!-- krizaka:workflows:start -->
## Workflows

What runs in the Actions tab of this repository.

- **Bump @krizaka packages** (`bump-krizaka.yml`): Opens one auto-merged PR that bumps the @krizaka packages after a release. Runs when another repository triggers it, on demand.
- **CI** (`ci.yml`): Lint, tests and build of the repository. Runs on push, on every pull request, in the merge queue, on demand.
- **PR title lint** (`commitlint.yml`): Checks that the PR title is a Conventional Commit. Runs on every pull request.
- **Dependabot auto-merge** (`dependabot-automerge.yml`): Auto-merges safe Dependabot updates. Runs on every pull request.
- **Docs map** (`docs-map.yml`): Checks the docs map of the repository. Runs on every pull request.
- **Merge resolution** (`merge-resolution.yml`): Fails a PR that drops what main added without a deletion-explained line. Runs on every pull request.
- **Post-deploy check** (`post-deploy.yml`): Waits for the live site to serve the new build, then pings IndexNow. Runs on push, on demand.
- **Maven Central check** (`published-check.yml`): Checks the latest release is on Maven Central. Runs on a schedule, on demand.
- **Release PR** (`release-please.yml`): Keeps one release PR open from the merged commits; its merge tags the release. Runs on push.
- **Scorecard** (`scorecard.yml`): OpenSSF Scorecard of the repository. Runs when branch rules change, on a schedule, on demand.
- **Security scan** (`security.yml`): Secret scan (gitleaks) and workflow scan (zizmor); both are required checks. Runs on every pull request, in the merge queue, on push.
- **Docs site** (`site.yml`): Lints, tests and builds the documentation site. Runs on push, on every pull request, in the merge queue, on demand, on a schedule.
<!-- krizaka:workflows:end -->

## License

[Apache License 2.0](LICENSE) © 2026 Krizaka

---

<sub>A Krizaka open-source project · [krizaka.com](https://www.krizaka.com) · Apache-2.0</sub>

The Krizaka names and birds are not licensed by Apache-2.0 (s. 6): [trademark policy](https://github.com/krizaka/.github/blob/main/TRADEMARKS.md).
