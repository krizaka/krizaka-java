# krizaka-java — Scope (agent-neutral)

> The build foundation of every **Krizaka** JVM artifact (`com.krizaka`, published on Maven Central). It is not part of
> a product: Sovkora consumes it (its `sovkora-parent` inherits `krizaka-parent`), and so can any application.
> When this repository is cloned inside the Sovkora workspace (`krizaka/krizaka-java`), the workspace contract
> ([`krizaka/sovkora/AGENTS.md`](https://github.com/krizaka/sovkora/blob/main/AGENTS.md)) applies as well.

## Rules of this repository

- **One repository, one version, one release (ADR-021).** The Java building blocks are Maven modules here, imported with their history
  (`krizaka-service-kit/` first; users, notifications and billing follow). Every module carries the version of the repository
  (release-please moves them all), the BOM manages them at `${project.version}`, the reactor orders the builds. Artifact coordinates
  do not change. Nothing about a block's version is edited by hand.

- **`krizaka-parent` has no `<dependencyManagement>`.** `krizaka-bom` inherits it, and a BOM carries its parent's
  managed versions into every importer. Importing `krizaka-bom` must change no third-party version.
- **`krizaka-bom` lists every published `com.krizaka` artifact, and only those** (one exception: `org.jspecify:jspecify`, ADR-015, at the version Spring Boot manages), each at its repository's version
  property (`krizaka-service-kit.version`, …; default `${project.version}`). The BOM carries the compatible set; a
  new artifact in any Krizaka repository is added here in the same release.
- **Releases are release-please's.** Pull request titles are Conventional Commits; nobody edits a version or the
  generated part of the CHANGELOG by hand. japicmp (release profile) guards the SemVer boundary.
- **Every plugin is pinned** (enforcer `requirePluginVersions`). A plugin version moves in this repository, once, for
  every Krizaka artifact.
- **PMD and SpotBugs (+ find-sec-bugs) run in `verify`** (ADR-013, `krizaka-quality` holds the rules): a new violation
  fails the build; a module's existing ones are frozen in its `pmd-baseline.properties` and `spotbugs-exclude.xml`,
  **measured** by `node scripts/quality-baseline.mjs init|shrink|check <repo>` (never typed; it only shrinks). Mapping from
  Sonar: S3776 CognitiveComplexity, S1192 AvoidDuplicateLiterals, S1186 UncommentedEmptyMethodBody/Constructor, S107
  ExcessiveParameterList, S1172 UnusedFormalParameter, S127 AvoidReassigningLoopVariables, S106 SystemPrintln, S2077
  SQL_INJECTION*, S5998 REDOS. No twin (left to Sonar): S135, S6218. This repository switches them off
  (`.mvn/maven.config`): its parent cannot resolve the rules it ships with.
- **Nothing Sovkora-specific** (Spring AI, `.env` profiles, SonarCloud, governance rules) — those belong to
  `sovkora-parent` in `krizaka/sovkora-build`.
- Maven Central requirements are part of the build, not of the release day: CI runs `verify -Prelease -Dgpg.skip` on
  every change, so a missing javadoc or source jar fails the pull request.

## Tests

- **No test, not done.** A change to the generic test kit ships with its own tests; a change to the parent or the BOM
  is proven by the workspace build of every consumer (definition of done, 2), never assumed.
- **A fix starts with a failing test** (or a failing consumer build) that reproduces the bug.
- CI runs `./mvnw verify -Prelease -Dgpg.skip` on every pull request and must be green; nothing is skipped to get there.

## The documentation site — `site/` (java.krizaka.com)

- **Krizaka Java's documentation lives here**, because this repository owns what every `com.krizaka` artifact shares (the
  parent POM, the BOM) and is the one that releases first. Decision of 2026-10-14: each thing Krizaka does has its site,
  named like it and built from its repository — `www.krizaka.com` is the company, `ui.krizaka.com` Krizaka UI,
  **`java.krizaka.com` this**. A static Next.js export (`output: "export"`, no server) with Fumadocs, `@krizaka/ui`,
  `@krizaka/tokens`, `@krizaka/fonts`; dark and light, English at `/` and French at `/fr/`.
- **The guides are written here** (`site/content/docs/<page>.mdx` and `<page>.fr.mdx`, listed in `meta.json` and
  `meta.fr.json`); `npm run lint` (`scripts/check-content.mjs`) fails on a page without its twin, or a French page that
  drops a code block, a version token, a component or an internal link. Every user-facing string of the chrome is in
  `site/messages/{en,fr}.json` (`krizaka-i18n check`).
- **The building blocks map is generated, never written**: `scripts/gen-building-blocks.mjs` reads the POMs of this repository
  and a shallow clone of `krizaka-service-kit`, `krizaka-users` and `krizaka-billing` (krizaka-notifications is part of `krizaka-service-kit`) (or
  `KRIZAKA_REPOS_DIR`) → `lib/generated/building-blocks.json` (git-ignored), checked by `lib/model.ts` at build. A fact the
  map lacks is added to that generator.
- **The configuration reference is generated, never written**: `scripts/gen-configuration.mjs` reads
  `docs/configuration-metadata.json` of `krizaka-service-kit`, `krizaka-users` and `krizaka-billing` (each held equal to the
  configuration processor's metadata by that repository's `ConfigurationReferenceTest`) → `lib/generated/configuration.json`;
  pages render it with `<ConfigurationReference artifact="…" />`. A property is described in its Java source, never here.
- **The features matrix is honest by construction**: `lib/features.json` marks each capability shipped, partial or planned;
  `scripts/check-features.mjs` (in `npm run lint`) fails when a shipped or partial line names a file missing from `main` of its
  repository, or a partial or planned line has no issue. Ship the code first, then flip the line.
- **Versions are data**: the pages write `%krizaka-bom%` (an artifact id between percent signs); `scripts/gen-registry-versions.mjs`
  reads Maven Central and the build replaces them — a page never states a typed version. The site publishes nothing to
  Maven Central and its commits are `docs(site):` / `chore(site):` — never `feat:`/`fix:`, which would make release-please
  release the parent POM.
- **CI** (`.github/workflows/site.yml`) lints, tests and builds on every pull request; **deployment** is DigitalOcean App
  Platform's static site `krizaka-java-site` (`.do/app.yaml`, project `krizaka`), rebuilt on every push to `main`.
- Tests: `cd site && npm test` (`tests/*.test.mjs`). A change to a generator, a script or the content rules ships with its test.

## Static rules & codemods (OpenRewrite, ArchUnit)

The org code rules (addendum 2026-10-15) are checked by tools, not by review.

- **OpenRewrite** (`rewrite-maven-plugin`, configured once in `krizaka-parent`, inherited by every JVM repository):
  the Java style is **explicit, never autodetected** (`krizaka-rewrite-style`: no star import, static imports first — the layout google-java-format writes; autodetection once folded the imports of 29 untouched files, sovkora-ai-engine#133),
  common static analysis, test cleanup (`RemoveTestPrefix`; not `TestsShouldIncludeAssertions`, which turns an aborted assumption into a failure), Java 21 idioms, Spring idioms (no `@Autowired` on a constructor; `@Bean`
  methods stay public — the auto-configurations of the service kit are published API), no duplicate POM dependency. `./mvnw rewrite:dryRun` lists what would change and **fails** if
  anything would (CI runs it: `rewrite: true` in `.github/workflows/ci.yml`); `./mvnw rewrite:run` applies it — behaviour
  identical, run the tests, land it in a small pull request. A recipe that is wrong for this repository is excluded here
  (`<exclusions>` / a `rewrite.yml`), with the reason; test fixtures (`**/fixture/**`) are excluded everywhere.
- **ArchUnit** (shared rules in `krizaka-test-support`: `CodeRules`, `ConfigBindingRules`, `SourceRules`,
  `ArchitectureRules`): the governance tests of a module call them. `ArchitectureRules` holds the structure — layers
  (web → service → repository, none skipped, none calling up), no package cycle, `*Controller` / `*Service` /
  `*Repository` names, `@Value` and the environment read only in configuration, `@Transactional` only on services. The
  older `CodeRules` hold the rest (no field injection, no `System.out`, domain free of framework, one class per file…).
- **ADR-015 rules** (krizaka/.github#384, `AuditRules`, all with a `…Rule()` form for `FrozenRules.check`): controllers do not
  reach persistence types, no `*Utils` / `*Util` / `*Helper(s)` class, every `@RequestBody` DTO is `@Valid`, instance fields
  of Spring beans are final, every package is `@NullMarked` (JSpecify). No `*Manager` ban (ADR-015 §5).
- **NullAway** (`krizaka-nullaway` profile of `krizaka-parent`, on by default, `-Dkrizaka.nullaway.skip=true` turns it off):
  Error Prone is only its host; only packages marked `@NullMarked` are checked (`OnlyNullMarked`), so marking a package is
  the ratchet and `AuditRules.everyPackageNullMarkedRule()` (frozen) lists the packages still to mark. A module that
  declares its own `annotationProcessorPaths` (MapStruct, Lombok…) must repeat Error Prone and NullAway there.
- **Debt only shrinks — `FrozenRules`** (ArchUnit `FreezingArchRule`): a rule a module cannot satisfy yet is checked with
  `FrozenRules.check(ArchitectureRules.xxxRule(...), CLASSES)`. The violations that exist are recorded in the module's
  `archunit_store/` (committed); a **new** violation fails, a fixed one leaves the store (commit it). A rule with no store
  fails: record a new rule's debt once with `-Darchunit.freeze.store.default.allowStoreCreation=true`, review the store,
  commit it. Never add a violation to a store by hand to get a build green.
- A rule is never weakened, skipped or deleted to get a PR green; fix the code or freeze the existing debt.

## HTTP clients (Spring Boot 4) and ArchUnit

How a Java module is written, order of work first. Each rule points to the test that enforces it; a rule without a test is not a rule.

**Order of work (pragmatic test-first).** New behaviour starts with a failing test of the port's contract (what the domain expects), then the smallest code that passes, then the refactor. A fix starts with the test that reproduces the bug. A refactor starts with **characterization tests** that pass on the current code and are not edited afterwards (billing-client: `BillingClientFailureContractTest`, one test per failure contract, driven through the domain ports against a stub server, so it names no adapter and no HTTP client). Code is written to be read by a person: small classes with one job, the domain's words for names, short methods, a comment only for a *why* (keep the why of the code you move).

1. **ArchUnit is the law.** A rule exists only as a test; it is never deleted, `@Disabled`, weakened or edited to pass. Existing debt is frozen with `FrozenRules`, **with the issue that will remove it**, never new debt. Every module calls **one entry point** with its base package: `KrizakaArchitecture.assertStandard(classes, "com.krizaka.<module>")` — no package cycle, one top-level class per file, private fields, no field injection, no standard streams, no raw HTTP client outside `config`, package-private `@HttpExchange` interfaces, at most 7 top-level classes per package, every package `@NullMarked`. It reports every broken rule, fails on a module that imported nothing, and freezes only through `assertStandard(classes, pkg, Map.of(Rule.X, "krizaka-internal#123"))` (a frozen rule without an issue is refused). Enforced by `KrizakaArchitectureTest` (krizaka-test-support); the module's own `ArchitectureTest` (billing-client: `architecture/ArchitectureTest`) is its proof.
2. **HTTP clients are declarative.** No `RestClient`, `RestTemplate` or `WebClient` outside a `config` package (the rule covers the types and their nested types, such as `RestClient.Builder`; `RestClientException` stays allowed, an adapter translates it into its domain exception). Per feature: a package-private `@HttpExchange` interface with package-private `record` wire DTOs, registered with `@ImportHttpServices(group = "billing", types = …)`; the base URL, timeouts and service token are set once in `config` (`BillingHttpGroupConfigurer`). Spring Boot's group properties `spring.http.serviceclient.<group>.base-url|connect-timeout|read-timeout` win when set; `krizaka.billing.*` keeps working as the default. Enforced by `CodeRules.noRawHttpClientInAdaptersRule` (`ModuleShapeRulesTest`), `httpExchangeInterfacesPackagePrivateRule` (in the standard) and `httpExchangeSignaturesHaveNoDomainTypeRule` (`ModuleShapeRulesTest`; billing-client calls it with its domain package). The package-private `final` adapter implements the domain port: DTO to domain mapping, logging, translating `RestClientException` into the domain exception; its failure contract (fail hard, fail open, degrade, best-effort) is stated in its Javadoc and pinned by a characterization test.
3. **Package by feature**: `<module>.<feature>` holds the port's adapter, its no-op, its exchange, its DTOs and **one public `*ClientConfiguration`, the feature's only public type**; `<module>.config` holds the auto-configuration and the public `@ConfigurationProperties` record. At most 7 top-level classes per package (nest the DTOs of a large exchange in one package-private holder, as `CreditWire`). Enforced by `CodeRules.maxTopLevelClassesPerPackageRule` (in the standard, `ModuleShapeRulesTest`) and `featuresExposeOnlyTheirConfigurationRule` (`ModuleShapeRulesTest`; billing-client's `ArchitectureTest`).
4. **`@NullMarked` everywhere, no defensive null.** A `package-info.java` with `@NullMarked` in every package, `@Nullable` where null is a real answer (an empty HTTP body), no `Objects.requireNonNull` on a parameter the type already guarantees. Enforced by `AuditRules.everyPackageNullMarkedRule` (in the standard) and NullAway at compile time (tests of a marked package are checked too).

**Tests stay small because of the design.** The adapter depends on its `@HttpExchange` interface, so its unit test stubs that interface in plain Java (mapping, cache, failure contract; no HTTP server: `HttpPackPricingClientTest`). One wiring test per module proves the method, path, body and token against a stub HTTP server (`BillingClientFailureContractTest`, `BillingHttpGroupTest`).

**Self-review before the PR:** no domain type in an `@HttpExchange` signature; adapters, `@HttpExchange` interfaces and DTOs package-private; the module's `ArchitectureTest` calls `assertStandard`; `./mvnw -pl krizaka-billing/krizaka-billing-client -am verify` (the module's own path) is green — that is « done ».

## Definition of done

1. `./mvnw verify -Prelease -Dgpg.skip` is green.
2. Inside the Sovkora workspace, `./mvnw install` from the workspace root is green (every consumer still builds).
3. A change to the release mechanics is reflected in [README.md](README.md#releasing-to-maven-central) and in the
   organisation pipeline (`krizaka/.github/.github/workflows/maven.yml`).

## Search engines & AI assistants (Krizaka family contract)

The contract is defined once, in krizaka-com `AGENTS.md` §2.4, and checked here by `scripts/check-seo-family.mjs` (a copy of
the same dependency-free file in every site's repository — keep the copies identical; its unit test sits beside it).

This site (java.krizaka.com) is **indexed**: robots.txt allows the shared AI-crawler list (`site/app/robots.ts`), the sitemap is dated by the build, `llms.txt` and `llms-full.txt` are written by `scripts/postbuild.mjs` (with the family links), every page carries an Open Graph image (`/og.png`) and JSON-LD (WebSite + SoftwareSourceCode + TechArticle) whose publisher is the Krizaka Organization `https://www.krizaka.com/#organization`. CI serves `out/` and runs the guard (`.github/workflows/site.yml`). No SearchAction: the search is a dialog over a static index, with no search URL.

## Before you open or update a pull request (coding agents)

These are the defects code review found most often on 2026-10-23. Check them yourself before you open or update a pull request:
1. **Checks:** run the repository's full check (lint, typecheck, tests, build) and fix it. Never push a red check.
2. **Concurrency:** any « count then insert » or « check then write » runs in one transaction with a lock, and has a concurrency test.
3. **One gate:** a rule (limit, permission, validation) is enforced in the service, so no route can skip it. Test every route.
4. **Errors:** a cleanup (ROLLBACK, DROP, close) never masks the original error. Rethrow it, or throw an `AggregateError` with the original first.
5. **Validation:** validate the original input, never a truncated or pre-trimmed copy. Server and client reject the same inputs.
6. **Accessibility:**
   - every control has a unique label, and FR/EN pairs say their language;
   - focus rings are never clipped by `overflow`;
   - text never overflows its box at 360 px.
7. **Naming:** environment variables follow `<CONTEXT>_<THING>` (`APP_`, `DATABASE_`, `AUTH_`, `MEDIA_`, `MAIL_`, `PAYMENTS_`, `AI_`…) and are declared once.
8. **Words:** every user-facing string is in the FR and EN messages, and no colour is hard-coded.
9. **Review threads:** answer every one. Fix it test-first, or reply with the reason, then resolve it.

- **A bug means a missing guard** ([standard](https://github.com/krizaka/.github/blob/main/docs/standards/bug-means-missing-guard.md)): the fix PR first adds a guard that fails on `main`, then fixes the bug; the bug issue says why the guards missed it.

**Repository layout:** the root follows [krizaka/.github `docs/standards/repo-layout.md`](https://github.com/krizaka/.github/blob/main/docs/standards/repo-layout.md); the CI guard `check-repo-layout` fails on a stray root file, and a justified extra is declared in `.repo-layout.json` with its why.

## 9. Anti-slop (ADR-015)

What a model tends to write and the code does not need. Each line points at the rule that refuses it
([ADR-015](https://github.com/krizaka/krizaka-internal/blob/main/docs/adr/.github/ADR-015-ai-code-audit-measured-and-enforced.md), [docs/standards/guards.md](https://github.com/krizaka/.github/blob/main/docs/standards/guards.md)).

1. **No defensive null on `@NullMarked` code.** No `Objects.requireNonNull` or `if (x == null)` on a value the type already
   guarantees. Enforced by NullAway at compile time; a new package is `@NullMarked` (ArchUnit).
2. **A closed set is a type.** An enum, a sealed interface or an `as const` object, never a string compared by hand;
   strategies are a `Map<Kind, Strategy>` built once at startup. Enforced by PMD / ArchUnit (Java) and `no-enum-string-compare` (ast-grep).
3. **HTTP in Java is a DTO record, `@Valid` and `ProblemDetail`.** No `Map.of("error", …)`, no entity in a response, one
   `@RestControllerAdvice` per service. Enforced by ArchUnit.
4. **No `*Utils`, `*Helper` or `*Manager`.** Name the thing by what it does (§4). Enforced by ArchUnit (ADR-015 §5).
5. **`?.` and `??` in TypeScript**, not a chain of `&&` or a ternary on null. The target rules are `@typescript-eslint/prefer-optional-chain` and `prefer-nullish-coalescing`; they are not in `@krizaka/config` yet.
6. **The build is the judge.** Compile, lint and test before every push; `krizaka-edit-check` runs on each edit (below) and
   NullAway and ArchUnit run at pre-push and in CI. A rule is never silenced to get green.

**The edit hook.** Every repository adds this to `.claude/settings.json`. After each `Edit` or `Write`, the one file is
checked in under 10 s: ESLint with the repository's config for TypeScript, PMD with the Krizaka rules for Java (never Maven).
A finding comes back to the agent (exit 2); a timeout is silent.

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [{ "type": "command", "command": "npx --yes -p @krizaka/config krizaka-edit-check", "timeout": 10 }]
      }
    ]
  }
}
```

In a TypeScript repository that already depends on `@krizaka/config`, use `npx --no-install krizaka-edit-check` (no download).
