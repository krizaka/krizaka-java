# krizaka-java — agent contract

<!-- krizaka-agents-template v2: rendered from krizaka/.github docs/standards/agents-template.md (scripts/agents-md.mjs). The one AGENTS.md standard: six sections in this order, at most 150 lines, bullets and imperatives. The conformance check « agents-template » reads the headings, the length and CLAUDE.md. Detail goes to docs/ via References, not here. -->

## Project context

- The build foundation of every Krizaka JVM artifact (`com.krizaka`, Maven Central): `krizaka-parent`, `krizaka-bom`, the service kit, users, billing, payments, the test kit; plus the docs site `site/` (java.krizaka.com).
- One repository, one version, one release (ADR-021): release-please moves every module; never edit a version or the generated CHANGELOG by hand.
- Kind: `library` · team: platform · registry entry: [repos.json](https://github.com/krizaka/.github/blob/main/repos.json).
- Read this file first, then the section of [docs/agents.md](docs/agents.md) for what you touch.
- `CLAUDE.md` is the one line `@AGENTS.md`: never a second copy of it.

## Essential commands

```bash
./mvnw verify -Prelease -Dgpg.skip                 # the CI build: javadoc + source jars, PMD, SpotBugs, tests
./mvnw -pl krizaka-billing/krizaka-billing-client -am verify   # one module and what it needs
./mvnw rewrite:dryRun                              # OpenRewrite; fails if anything would change (rewrite:run applies)
node scripts/quality-baseline.mjs check <repo>     # PMD/SpotBugs baselines (init|shrink|check; never typed by hand)
cd site && npm run lint && npm test && npm run build   # docs site
```

- Run them on what you changed before every push; CI runs the rest. Inside the Sovkora workspace also run `./mvnw install` from its root.

## Architecture & conventions

- Follow the [krizaka-engineering skill](https://github.com/krizaka/krizaka-skills/blob/main/skills/krizaka-engineering/SKILL.md); search its [packages reference](https://github.com/krizaka/krizaka-skills/blob/main/skills/krizaka-engineering/references/packages.md) before writing anything generic.
- Spring Boot 4 on Java 21; ports and adapters, package by feature, web → service → repository, none skipped, none calling up.
- HTTP clients are declarative: a package-private `@HttpExchange` interface with package-private record DTOs, registered with `@ImportHttpServices`; no `RestClient`/`RestTemplate`/`WebClient` outside a `config` package.
- Every module's `ArchitectureTest` calls `KrizakaArchitecture.assertStandard(classes, "com.krizaka.<module>")`; debt is frozen with an issue (`FrozenRules`, `archunit_store/`) and only shrinks.
- Every package is `@NullMarked` (NullAway); records for DTOs and `@ConfigurationProperties`.
- `krizaka-parent` has no `<dependencyManagement>`; `krizaka-bom` lists every published `com.krizaka` artifact and only those, at `${project.version}`; a new artifact is added there in the same release.
- Every plugin is pinned (enforcer); a plugin version moves here, once, for all artifacts.
- Nothing Sovkora-specific here (Spring AI, `.env` profiles, SonarCloud rules): that is `sovkora-parent`.
- Name things by what they do; no `*Utils`, `*Util`, `*Helper(s)` (ArchUnit).

## Guard-rails & known pitfalls (read before coding)

- The checks are the judge ([ADR-015](https://github.com/krizaka/krizaka-internal/blob/main/docs/adr/.github/ADR-015-ai-code-audit-measured-and-enforced.md), [guards](https://github.com/krizaka/.github/blob/main/docs/standards/guards.md)): never `--no-verify`, never weaken, skip, `@Disabled` or delete an ArchUnit rule, never add a violation to a store or baseline by hand; fix the code or freeze the existing debt with an issue.
- Test servers listen on loopback only: `LoopbackTestServers` / `InetAddress.getLoopbackAddress()`, never `new ServerSocket(port)` (krizaka-internal#3265; ArchUnit, never frozen).
- Anti-slop: no `Objects.requireNonNull`/`if (x == null)` on a `@NullMarked` value; a closed set is an enum or sealed type; HTTP is a `@Valid` record DTO and `ProblemDetail`, no entity in a response.
- Check-then-write or count-then-insert runs in one transaction with a lock and has a concurrency test; a rule (limit, permission) is enforced in the service, not only a route.
- A cleanup never masks the original error: rethrow it first.
- Site: commits are `docs(site):` / `chore(site):`, never `feat:`/`fix:` (they would release the parent POM); pages write `%krizaka-bom%`, never a typed version; the building-blocks map and configuration reference are generated, never written.
- Plugin or OpenRewrite recipe wrong for this repo: exclude it with the reason (`rewrite.yml`), never edit around it.
- Start a fix with a failing test (a bug means a missing guard: [standard](https://github.com/krizaka/.github/blob/main/docs/standards/bug-means-missing-guard.md)).
- Never commit a secret, a capture or a log; no production data in a session.
- Keep the edit hook in `.claude/settings.json` (`krizaka-edit-check` on `Edit|Write`).

## Expected validation workflow

- [ ] `./mvnw verify -Prelease -Dgpg.skip` is green (module path for a module change); `rewrite:dryRun` clean.
- [ ] A parent or BOM change is proven by the workspace build of every consumer.
- [ ] A change to release mechanics is reflected in [README.md](README.md) and `krizaka/.github` `maven.yml`.
- [ ] Site change: `npm run lint`, `npm test`, `npm run build` in `site/`.
- [ ] Tests ship with the change; no check skipped to get green.
- [ ] One issue, one small PR (`Closes #<n>`), Conventional Commit title; merge only with green CI.

## References

- [docs/agents.md](docs/agents.md): the full previous manual (scope, rules, site, static rules, HTTP clients, definition of done, anti-slop), read by section.
- [README.md](README.md): releasing to Maven Central.
- [ADRs](docs/adr/README.md) and [central release plan](docs/central-release-plan.md).
- [Org standards](https://github.com/krizaka/.github/tree/main/docs/standards) of krizaka/.github; the [engineering charter](https://github.com/krizaka/.github/blob/main/AGENTS.template.md).
- [krizaka-engineering skill](https://github.com/krizaka/krizaka-skills/blob/main/skills/krizaka-engineering/SKILL.md).
