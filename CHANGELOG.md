# Changelog

Versions follow [Semantic Versioning](https://semver.org/) per repository; the BOM carries the compatible set
(`krizaka-bom` names, for each Krizaka repository, the release it was tested with). Releases are written by
[release-please](https://github.com/googleapis/release-please) from the Conventional Commits merged on `main`; the
hand-written history is under *Before release-please*.

## [0.16.2](https://github.com/krizaka/krizaka-java/compare/v0.16.1...v0.16.2) (2026-11-13)


### Dependencies

* @krizaka/fonts 0.5.4, @krizaka/i18n 0.2.7, @krizaka/icons 0.10.0, @krizaka/query 0.3.0, @krizaka/tailwind 4.4.0, @krizaka/tokens 4.4.0, @krizaka/ui 4.4.0 ([#262](https://github.com/krizaka/krizaka-java/issues/262)) ([4d5b781](https://github.com/krizaka/krizaka-java/commit/4d5b7819e4c3209a3f67ff308ad6026932b3d25b))

## [0.16.1](https://github.com/krizaka/krizaka-java/compare/v0.16.0...v0.16.1) (2026-11-13)


### Bug Fixes

* **site:** the registry versions read retries Maven Central with a bounded backoff (krizaka-internal[#3354](https://github.com/krizaka/krizaka-java/issues/3354)) ([#263](https://github.com/krizaka/krizaka-java/issues/263)) ([9af1b44](https://github.com/krizaka/krizaka-java/commit/9af1b4490799effd3145900ffafa3baafd2775cd))

## [0.16.0](https://github.com/krizaka/krizaka-java/compare/v0.15.0...v0.16.0) (2026-11-13)


### Features

* **observability:** logs leave a service only redacted, then over OTLP ([#260](https://github.com/krizaka/krizaka-java/issues/260)) ([8ffba0f](https://github.com/krizaka/krizaka-java/commit/8ffba0fc90b217768f209e6baf4739fbc29024b6))

## [0.15.0](https://github.com/krizaka/krizaka-java/compare/v0.14.1...v0.15.0) (2026-11-13)


### Features

* **observability:** export traces and metrics over OTLP under functional names ([#255](https://github.com/krizaka/krizaka-java/issues/255)) ([ed0778d](https://github.com/krizaka/krizaka-java/commit/ed0778dfbb8b44eba6598e5cd5b35b483dcafcbd))


### Bug Fixes

* **release:** the bump job grants contents: read, the pin moves to krizaka/.github a5c115f (krizaka-internal[#3342](https://github.com/krizaka/krizaka-java/issues/3342)) ([#256](https://github.com/krizaka/krizaka-java/issues/256)) ([291baa8](https://github.com/krizaka/krizaka-java/commit/291baa827a103ee4849000c6784303160d5b4c6b))
* **release:** the bump workflow runs in site/, the npm package of this repository (krizaka-internal[#3342](https://github.com/krizaka/krizaka-java/issues/3342)) ([#258](https://github.com/krizaka/krizaka-java/issues/258)) ([4186eb5](https://github.com/krizaka/krizaka-java/commit/4186eb5dd3eae7b646cb9235dfabb0ca5e2d5a63))

## [0.14.1](https://github.com/krizaka/krizaka-java/compare/v0.14.0...v0.14.1) (2026-11-12)


### Bug Fixes

* **test-support:** test servers bind to the loopback, never to all interfaces ([#252](https://github.com/krizaka/krizaka-java/issues/252)) ([23962a9](https://github.com/krizaka/krizaka-java/commit/23962a9a4b4169c3855274bcce96bf8b4514ad67))

## [0.14.0](https://github.com/krizaka/krizaka-java/compare/v0.13.1...v0.14.0) (2026-11-12)


### Features

* **users:** protect the service account of a product, audit every key revocation ([#247](https://github.com/krizaka/krizaka-java/issues/247)) ([e530cd5](https://github.com/krizaka/krizaka-java/commit/e530cd53c75f1cf6e05fc141fe0bb7d3f570660d))

## [0.13.1](https://github.com/krizaka/krizaka-java/compare/v0.13.0...v0.13.1) (2026-11-11)


### Bug Fixes

* **site:** gen-proof tolerates inaccessible Sovkora repositories ([#245](https://github.com/krizaka/krizaka-java/issues/245)) ([48eb214](https://github.com/krizaka/krizaka-java/commit/48eb214fc6eac0b24ae7a5e0873e07a808c3e8bf))

## [0.13.0](https://github.com/krizaka/krizaka-java/compare/v0.12.0...v0.13.0) (2026-11-07)


### ⚠ BREAKING CHANGES

* **architecture:** a shared ArchUnit entry point, and billing-client on declarative HTTP clients by feature ([#237](https://github.com/krizaka/krizaka-java/issues/237))

### Features

* **architecture:** a shared ArchUnit entry point, and billing-client on declarative HTTP clients by feature ([#237](https://github.com/krizaka/krizaka-java/issues/237)) ([3595a70](https://github.com/krizaka/krizaka-java/commit/3595a70680c8b42122c029ca14ff7543ce8fd1c7))


### Bug Fixes

* **adr:** the ADRs live in krizaka-internal, not in this public repository ([#242](https://github.com/krizaka/krizaka-java/issues/242)) ([a65413d](https://github.com/krizaka/krizaka-java/commit/a65413d927df8d682c84b4066b2c8215bdd0def5))

## [0.12.0](https://github.com/krizaka/krizaka-java/compare/v0.11.2...v0.12.0) (2026-11-06)


### Features

* **release:** releases go to Maven Central only, GitHub Packages is abandoned for Maven ([#235](https://github.com/krizaka/krizaka-java/issues/235)) ([4050f55](https://github.com/krizaka/krizaka-java/commit/4050f552be03891adadf9d93b3f8bbf9434ef1d0))

## [0.11.2](https://github.com/krizaka/krizaka-java/compare/v0.11.1...v0.11.2) (2026-11-06)


### Bug Fixes

* **main:** CI and the release no longer read the deleted repositories ([#230](https://github.com/krizaka/krizaka-java/issues/230)) ([5352504](https://github.com/krizaka/krizaka-java/commit/5352504f88542918a3114cf45a28cd847b131413))
* **site:** Krizaka Java is read from this repository's folders, never cloned from the deleted krizaka-platform-kit, krizaka-users and krizaka-billing ([#229](https://github.com/krizaka/krizaka-java/issues/229)) ([b7fa372](https://github.com/krizaka/krizaka-java/commit/b7fa37299100474d8fa6a9254821d0325830f0a3))

## [0.11.1](https://github.com/krizaka/krizaka-build/compare/v0.11.0...v0.11.1) (2026-11-04)


### Bug Fixes

* **users:** security hardening, bearer tokens only in the Authorization header ([#225](https://github.com/krizaka/krizaka-build/issues/225)) ([8651256](https://github.com/krizaka/krizaka-build/commit/86512569026b45d6886578d135210f4ec9fd6193))


### Documentation

* **users:** the users page links its folder here, where the code lives (ADR-021) ([#224](https://github.com/krizaka/krizaka-build/issues/224)) ([88a126e](https://github.com/krizaka/krizaka-build/commit/88a126eab7aed1bdd9dbdbf9fe65479382606f91))

## [0.11.0](https://github.com/krizaka/krizaka-build/compare/v0.10.0...v0.11.0) (2026-11-04)


### ⚠ BREAKING CHANGES

* **users:** UserDirectoryClient.getDecryptedApiKey is replaced by grantCredential(userSessionToken, provider, runId) and useCredential(grant, provider, runId).

### Features

* **users:** a provider key is released only against a run-bound, single-use grant ([#219](https://github.com/krizaka/krizaka-build/issues/219)) ([7db0aec](https://github.com/krizaka/krizaka-build/commit/7db0aecab5777173c06013ba8d996ec981ff4972))

## [0.10.0](https://github.com/krizaka/krizaka-build/compare/v0.9.2...v0.10.0) (2026-11-04)


### Features

* **users:** a provider key is sealed per user and provider, with a full CRUD that never answers the key ([#218](https://github.com/krizaka/krizaka-build/issues/218)) ([ba6869c](https://github.com/krizaka/krizaka-build/commit/ba6869c6a23ca6e28fc283642052dc06ca3d9489))

## [0.9.2](https://github.com/krizaka/krizaka-build/compare/v0.9.1...v0.9.2) (2026-11-04)


### Bug Fixes

* **main:** the starters example is built after the modules whose metadata it reads (krizaka-internal[#2352](https://github.com/krizaka/krizaka-build/issues/2352)) ([#216](https://github.com/krizaka/krizaka-build/issues/216)) ([1306d41](https://github.com/krizaka/krizaka-build/commit/1306d41e95f8c3d497f94fef7b530ef274c956ce))

## [0.9.1](https://github.com/krizaka/krizaka-build/compare/v0.9.0...v0.9.1) (2026-11-02)


### Bug Fixes

* **ci:** the post-deploy check passes the app key so its failure is filed in krizaka-internal (krizaka-internal[#2267](https://github.com/krizaka/krizaka-build/issues/2267)) ([#210](https://github.com/krizaka/krizaka-build/issues/210)) ([0af72fc](https://github.com/krizaka/krizaka-build/commit/0af72fc894a38dfe510dfe3a9fc292369a02aaa4))

## [0.9.0](https://github.com/krizaka/krizaka-build/compare/v0.8.2...v0.9.0) (2026-11-02)


### Features

* **db:** users and billing migrate with Flyway from a V1 baseline ([#205](https://github.com/krizaka/krizaka-build/issues/205)) ([e3b7dde](https://github.com/krizaka/krizaka-build/commit/e3b7dde370177a549d509413f6a060dd85dbb31d))

## [0.8.2](https://github.com/krizaka/krizaka-build/compare/v0.8.1...v0.8.2) (2026-11-02)


### Bug Fixes

* **release:** the tag deploy leaves out the modules of other repositories so billing is published; the release checks the whole reactor ([#199](https://github.com/krizaka/krizaka-build/issues/199)) ([15007d6](https://github.com/krizaka/krizaka-build/commit/15007d640a9216dae2a356330c5be2dc354102f9))

## [0.8.1](https://github.com/krizaka/krizaka-build/compare/v0.8.0...v0.8.1) (2026-11-02)


### Bug Fixes

* **billing:** the hold sweep runs in one transaction (S2229) ([#197](https://github.com/krizaka/krizaka-build/issues/197)) ([de046a6](https://github.com/krizaka/krizaka-build/commit/de046a6d4864a3f9a7b9a884de5248bb47a3da11))
* **sonar:** check-seo-family prints without nested template literals (S4624), same as template-next-app[#38](https://github.com/krizaka/krizaka-build/issues/38) ([#202](https://github.com/krizaka/krizaka-build/issues/202)) ([e9d4044](https://github.com/krizaka/krizaka-build/commit/e9d4044015b6cb2d2c90e9550fb43332fe4d7407))
* **sonar:** clear new-code issues on main (S1166, S864, S6353, S3358, S2384, S1172, S1905) ([#203](https://github.com/krizaka/krizaka-build/issues/203)) ([4ac9134](https://github.com/krizaka/krizaka-build/commit/4ac91345e583a04a51383cbe2f8fc174fd59428d))
* **sonar:** the three newest major issues on main (S3358, S1068, S2925), so the new-code gate passes ([#204](https://github.com/krizaka/krizaka-build/issues/204)) ([b184d44](https://github.com/krizaka/krizaka-build/commit/b184d4477bc3089ad6cdd63a45a7790c5f743b88))

## [0.8.0](https://github.com/krizaka/krizaka-build/compare/v0.7.1...v0.8.0) (2026-11-01)


### Features

* **billing:** krizaka-billing joins the reactor with its history (ADR-021) ([#188](https://github.com/krizaka/krizaka-build/issues/188)) ([1b00164](https://github.com/krizaka/krizaka-build/commit/1b00164b5fcc66ffe1934b170db3268413f99b8d))

## [0.7.1](https://github.com/krizaka/krizaka-build/compare/v0.7.0...v0.7.1) (2026-11-01)


### Bug Fixes

* **ci:** deploy-repository input, republish a package through the repository it is bound to ([#190](https://github.com/krizaka/krizaka-build/issues/190)) ([5625e06](https://github.com/krizaka/krizaka-build/commit/5625e06479400fa21ddcc637ebcc3b30a390aaa3))

## [0.7.0](https://github.com/krizaka/krizaka-build/compare/v0.6.1...v0.7.0) (2026-11-01)


### Features

* **users:** krizaka-users joins the reactor with its history (ADR-021) ([#179](https://github.com/krizaka/krizaka-build/issues/179)) ([2435e55](https://github.com/krizaka/krizaka-build/commit/2435e5514b06bd8d8f59e874855deca4f9467646))

## [0.6.1](https://github.com/krizaka/krizaka-build/compare/v0.6.0...v0.6.1) (2026-11-01)


### Bug Fixes

* **build:** the platform kit inherits the in-repo parent at the family version ([#182](https://github.com/krizaka/krizaka-build/issues/182)) ([7e94f31](https://github.com/krizaka/krizaka-build/commit/7e94f3105c157f024d0871113c7de3c7263c8e11))

## [0.6.0](https://github.com/krizaka/krizaka-build/compare/v0.3.5...v0.6.0) (2026-11-01)


### Features

* **platform-kit:** import krizaka-platform-kit as a module with its history (ADR-021) ([9d16c23](https://github.com/krizaka/krizaka-build/commit/9d16c234ee64621a3f1a970521a23dcdf0b85cc1))
* **platform-kit:** the kit is a module of the reactor at the family version (ADR-021, krizaka-internal[#1878](https://github.com/krizaka/krizaka-build/issues/1878)) ([0f7c94f](https://github.com/krizaka/krizaka-build/commit/0f7c94fb93619b6fa13ebf7c43eaf6619f9b4dd9))


### Bug Fixes

* **bom:** krizaka-users 0.3.0, the version the repository carries on main ([#173](https://github.com/krizaka/krizaka-build/issues/173)) ([8a63fc8](https://github.com/krizaka/krizaka-build/commit/8a63fc83852aa4091275dc5eee84ab24303b495f))


### Miscellaneous Chores

* **release:** the family releases at 0.6.0, above every block it replaces (ADR-021) ([4bcce29](https://github.com/krizaka/krizaka-build/commit/4bcce29635f8cc92fac8c2a03f2127f624ac9c73))

## [0.3.5](https://github.com/krizaka/krizaka-build/compare/v0.3.4...v0.3.5) (2026-10-31)


### Bug Fixes

* **bom:** krizaka-platform-kit 0.5.0 ([#170](https://github.com/krizaka/krizaka-build/issues/170)) ([6beecde](https://github.com/krizaka/krizaka-build/commit/6beecdedf4b8ffa765bf8fa9242a4a584cecc56a))

## [0.3.4](https://github.com/krizaka/krizaka-build/compare/v0.3.3...v0.3.4) (2026-10-31)


### Bug Fixes

* **bom:** krizaka-users 0.2.1 (0.2.0 was never published) ([#168](https://github.com/krizaka/krizaka-build/issues/168)) ([c583c58](https://github.com/krizaka/krizaka-build/commit/c583c58f7b931f81db66b235dbc9d896914c49eb))

## [0.3.3](https://github.com/krizaka/krizaka-build/compare/v0.3.2...v0.3.3) (2026-10-31)


### Bug Fixes

* **ci:** krizaka-users has its tag, off the not-yet-released list ([#166](https://github.com/krizaka/krizaka-build/issues/166)) ([a94de68](https://github.com/krizaka/krizaka-build/commit/a94de688cc3ed49049d0dbca222572dd17c2719d))
* **ci:** maven.yml pin moves to be08be83 so the tag deploy can write packages ([#164](https://github.com/krizaka/krizaka-build/issues/164)) ([d1b5a25](https://github.com/krizaka/krizaka-build/commit/d1b5a2521f316a0c2cbb25089c88e628bb360ba6))

## [0.3.2](https://github.com/krizaka/krizaka-build/compare/v0.3.1...v0.3.2) (2026-10-31)


### Bug Fixes

* **bom:** the BOM names krizaka-platform-kit 0.4.3, the first kit on GitHub Packages (krizaka-internal[#1879](https://github.com/krizaka/krizaka-build/issues/1879)) ([#160](https://github.com/krizaka/krizaka-build/issues/160)) ([e65a67e](https://github.com/krizaka/krizaka-build/commit/e65a67e144f79b61de253bd006195832372021b7))
* **ci:** the consumers job compiles the service against the parent just installed ([#163](https://github.com/krizaka/krizaka-build/issues/163)) ([c7bc9a4](https://github.com/krizaka/krizaka-build/commit/c7bc9a4a86544782ff520756b832a1ee1c8dcd36))
* **ci:** the consumers job points the kit and the service at the parent just installed (release PR) ([#162](https://github.com/krizaka/krizaka-build/issues/162)) ([e7fd81c](https://github.com/krizaka/krizaka-build/commit/e7fd81c73dd2e388a5f0e117dc058d15ebff3879))

## [0.3.1](https://github.com/krizaka/krizaka-build/compare/v0.3.0...v0.3.1) (2026-10-31)


### Bug Fixes

* **release:** the first version published to GitHub Packages is 0.3.1 (0.3.0 deploy was refused, krizaka-internal[#1879](https://github.com/krizaka/krizaka-build/issues/1879)) ([#157](https://github.com/krizaka/krizaka-build/issues/157)) ([ca3c4c4](https://github.com/krizaka/krizaka-build/commit/ca3c4c408de2a59996e15725f97877581f8c06f2))

## [0.3.0](https://github.com/krizaka/krizaka-build/compare/v0.2.0...v0.3.0) (2026-10-31)


### Features

* **bom:** krizaka-app-config and its starter, with their guide on java.krizaka.com ([#40](https://github.com/krizaka/krizaka-build/issues/40)) ([6ccbdf0](https://github.com/krizaka/krizaka-build/commit/6ccbdf0f090f0f94125a8321fbbc76041c665d7c))
* **bom:** manage krizaka-idempotency and krizaka-spring-boot-starter-idempotency ([#67](https://github.com/krizaka/krizaka-build/issues/67)) ([7f19850](https://github.com/krizaka/krizaka-build/commit/7f198502d1f66c5bae55d6e2a88a4a06587d5587)), closes [#65](https://github.com/krizaka/krizaka-build/issues/65)
* **bom:** manage krizaka-persistence and krizaka-spring-boot-starter-persistence ([#69](https://github.com/krizaka/krizaka-build/issues/69)) ([f7bef7a](https://github.com/krizaka/krizaka-build/commit/f7bef7a51498f37350de8e7d1eeeb573d09c7743))
* **bom:** manage krizaka-ratelimit; docs(site): rate limiting guide, matrix partial (in memory, per instance) ([#72](https://github.com/krizaka/krizaka-build/issues/72)) ([91d70ab](https://github.com/krizaka/krizaka-build/commit/91d70ab06775d348d6f1dec3870967cdae65ca87)), closes [#65](https://github.com/krizaka/krizaka-build/issues/65)
* **bom:** manage krizaka-webhooks and its starter; docs(site): webhooks guide, matrix shipped ([#71](https://github.com/krizaka/krizaka-build/issues/71)) ([932b07e](https://github.com/krizaka/krizaka-build/commit/932b07e968821ef605f4cba8797ca0a90f083cef)), closes [#65](https://github.com/krizaka/krizaka-build/issues/65)
* **build:** OpenRewrite in krizaka-parent, ArchitectureRules + FrozenRules in krizaka-test-support ([#32](https://github.com/krizaka/krizaka-build/issues/32)) ([34dedc2](https://github.com/krizaka/krizaka-build/commit/34dedc20c1f070ffd2b4eddb64a49e7f14233513))
* **changelog:** /changelog (EN/FR) from the GitHub Releases (release-please) + RSS; footer version -&gt; /changelog#&lt;version&gt;; @krizaka/ui 3.25 (krizaka-ui[#307](https://github.com/krizaka/krizaka-build/issues/307)) ([#46](https://github.com/krizaka/krizaka-build/issues/46)) ([7ed7e28](https://github.com/krizaka/krizaka-build/commit/7ed7e28e219494b8fd3b053e410aded80acddafa))
* **parent:** PMD + SpotBugs/find-sec-bugs in verify with per-module baselines ([#105](https://github.com/krizaka/krizaka-build/issues/105)) ([#107](https://github.com/krizaka/krizaka-build/issues/107)) ([90c3ecc](https://github.com/krizaka/krizaka-build/commit/90c3ecc9b66b6fb8011ebc463ce21e0614615626))
* **payments:** krizaka-payments for Java — the port of @krizaka/payments over stripe-java ([#145](https://github.com/krizaka/krizaka-build/issues/145)) ([fec9eaa](https://github.com/krizaka/krizaka-build/commit/fec9eaa1d803a3e6bf53e13c6badd9cdc043c025))
* **quality:** ADR-015 build rules — NullAway profile, PMD limits and XPath rules, ArchUnit AuditRules (krizaka-build[#120](https://github.com/krizaka/krizaka-build/issues/120)) ([#121](https://github.com/krizaka/krizaka-build/issues/121)) ([01d73e0](https://github.com/krizaka/krizaka-build/commit/01d73e0feb58664cb988ac698c9a850b6d6c34d0))
* **release:** publish the building blocks to GitHub Packages, registry switchable (ADR-019) ([#153](https://github.com/krizaka/krizaka-build/issues/153)) ([a5e8caf](https://github.com/krizaka/krizaka-build/commit/a5e8caf9d8f595bf93781364fe023c22119d7baf))
* **seo:** java.krizaka.com — AI crawlers, dated sitemap, social image, JSON-LD back to Krizaka, llms with the family ([#28](https://github.com/krizaka/krizaka-build/issues/28)) ([d2ac8c3](https://github.com/krizaka/krizaka-build/commit/d2ac8c3daa2f2d2e9e639a21dd33c0030f72fe3c))
* **site:** « start a project » generator in the hero and the guide (krizaka-ui[#406](https://github.com/krizaka/krizaka-build/issues/406) c) ([#60](https://github.com/krizaka/krizaka-build/issues/60)) ([9308853](https://github.com/krizaka/krizaka-build/commit/9308853d1e16dbfd958e5802fdcadbdedb23b73f))
* **site:** a real home for java.krizaka.com ([#49](https://github.com/krizaka/krizaka-build/issues/49)) ([243e00c](https://github.com/krizaka/krizaka-build/commit/243e00c55ad31c467f998a3b494afc40a9887358))
* **site:** java.krizaka.com in the Cahier — the new faces, @krizaka/ui 3.37, no caps or round cards (krizaka-ui[#417](https://github.com/krizaka/krizaka-build/issues/417)) ([#64](https://github.com/krizaka/krizaka-build/issues/64)) ([f0041ae](https://github.com/krizaka/krizaka-build/commit/f0041aecb2dbced672384eff8482c5d103fbcaf6))
* **test-support:** EventContractTest — events checked against the JSON Schema their -api publishes ([#7](https://github.com/krizaka/krizaka-build/issues/7)) ([164282a](https://github.com/krizaka/krizaka-build/commit/164282a23b4266b3f52eb9888e5e65ee2f8546a4))
* **test-support:** security rules of ADR-013 ([#102](https://github.com/krizaka/krizaka-build/issues/102), [#103](https://github.com/krizaka/krizaka-build/issues/103)) ([#106](https://github.com/krizaka/krizaka-build/issues/106)) ([439285d](https://github.com/krizaka/krizaka-build/commit/439285dd1da165c7a4a1c8143f0ac35c752e1d26))


### Bug Fixes

* **bom:** krizaka-platform-kit 0.4.0, the release its main carries ([#152](https://github.com/krizaka/krizaka-build/issues/152)) ([7978d9b](https://github.com/krizaka/krizaka-build/commit/7978d9b1234f0e66abf6ffcdc6b8190601f7d2f5))
* **bom:** krizaka-platform-kit 0.4.1; the guard refuses a BOM behind a released version ([#155](https://github.com/krizaka/krizaka-build/issues/155)) ([fb25b91](https://github.com/krizaka/krizaka-build/commit/fb25b91d3d01077a901afa9643440f9a9d11adce))
* **bom:** krizaka-platform-kit.version 0.3.0 — the release that carries the starters ([#99](https://github.com/krizaka/krizaka-build/issues/99)) ([42a629c](https://github.com/krizaka/krizaka-build/commit/42a629c922709c6b72f6c998b9609692adc6945f))
* **build:** OpenRewrite — drop TestsShouldIncludeAssertions (wraps tests in assertDoesNotThrow: an aborted assumption becomes a failure) ([#35](https://github.com/krizaka/krizaka-build/issues/35)) ([f46417c](https://github.com/krizaka/krizaka-build/commit/f46417ce42a2bffd9bf007022174d5f113dd6bd9))
* **build:** OpenRewrite — keep @Bean methods public ([#33](https://github.com/krizaka/krizaka-build/issues/33)) ([b2c1fce](https://github.com/krizaka/krizaka-build/commit/b2c1fce75030f11eb8dc2706955b5a432d669984))
* **changelog:** a release's version is shown as written — main's static build failed (Invalid time value) on the week-date reading ([#47](https://github.com/krizaka/krizaka-build/issues/47)) ([6570d3e](https://github.com/krizaka/krizaka-build/commit/6570d3e2a6321d7c4cfee4261f12c144aae8c261))
* **deploy:** repo spec matches the live app (drift check) ([#74](https://github.com/krizaka/krizaka-build/issues/74)) ([aaa6063](https://github.com/krizaka/krizaka-build/commit/aaa6063ee4cafda783c97f4b3a219917a3eed374))
* **deps:** declare commons-compress 1.28.0 (Scorecard Vulnerabilities: Testcontainers' 1.24) ([#57](https://github.com/krizaka/krizaka-build/issues/57)) ([daca9ec](https://github.com/krizaka/krizaka-build/commit/daca9ec2c770f6136689f2144e5dc58a952a0df6))
* **parent:** a module without spotbugs-exclude.xml is strict, not a build error; canary consumer in CI ([#105](https://github.com/krizaka/krizaka-build/issues/105)) ([#108](https://github.com/krizaka/krizaka-build/issues/108)) ([6796c08](https://github.com/krizaka/krizaka-build/commit/6796c08d510bb293770e94c37f716894e34d1a65))
* **parent:** OpenRewrite uses an explicit Java import layout, never an autodetected one ([#100](https://github.com/krizaka/krizaka-build/issues/100)) ([#101](https://github.com/krizaka/krizaka-build/issues/101)) ([dc95627](https://github.com/krizaka/krizaka-build/commit/dc95627787e46a5fd0fd0ffd84bdb0ec3dcd8783))
* **parent:** spotbugs failOnError is a property, the baseline script can measure ([#109](https://github.com/krizaka/krizaka-build/issues/109)) ([c724cb5](https://github.com/krizaka/krizaka-build/commit/c724cb5033df2b98bb4b284fb295b5b5a11138e9))
* **parent:** Spring Boot 4.0.8 — 4.1 broke the dead-letter contract downstream; Boot 4.1 becomes a tracked move ([#26](https://github.com/krizaka/krizaka-build/issues/26)) ([6b25f3f](https://github.com/krizaka/krizaka-build/commit/6b25f3f8b88e74d8a8acc1014e4422c11d4c86a4))
* **payments:** pass the consumers' PMD rules (complexity, nesting) ([#147](https://github.com/krizaka/krizaka-build/issues/147)) ([9e1631a](https://github.com/krizaka/krizaka-build/commit/9e1631a618299e81af109ace4805a2b8d1282ac4))
* **quality:** quality-baseline finds nested modules (starters/, examples/) ([#110](https://github.com/krizaka/krizaka-build/issues/110)) ([d2671af](https://github.com/krizaka/krizaka-build/commit/d2671af4278ca93fb28dfcde330e93b8e36dd924))
* **security:** xmlText double-unescape; least-privilege release-please token ([#36](https://github.com/krizaka/krizaka-build/issues/36)) ([160d569](https://github.com/krizaka/krizaka-build/commit/160d5693242e8396849af1bb3dc854763f5b20f1))
* **site:** DocPage JSON-LD escapes &lt; &gt; & (jsonLd) ([#39](https://github.com/krizaka/krizaka-build/issues/39)) ([63af7da](https://github.com/krizaka/krizaka-build/commit/63af7daae738725518e024f742ef3b3d8c8084d7))
* **site:** no family switcher; footer links back to krizaka.com only (3.43.0) ([#77](https://github.com/krizaka/krizaka-build/issues/77)) ([0cb3d27](https://github.com/krizaka/krizaka-build/commit/0cb3d27df3b3a1ba5031f6ba6b0e4121f16664c9))
* **site:** regenerate package-lock.json (npm 11) so the App Platform build passes ([#44](https://github.com/krizaka/krizaka-build/issues/44)) ([d4b548e](https://github.com/krizaka/krizaka-build/commit/d4b548e4574c0cfadc10445a1ceb8ed82a19f31b))
* **sonar:** the gate — 5 workflow findings and 3 sort bugs (krizaka/.github[#377](https://github.com/krizaka/krizaka-build/issues/377)) ([#117](https://github.com/krizaka/krizaka-build/issues/117)) ([8f15d13](https://github.com/krizaka/krizaka-build/commit/8f15d130aeb1714138e12656dc837bf84da61486))
* **test-support:** ArchitectureRules no longer call allowEmptyShould (Orazaka GOV-006 refuses a rule that can pass over nothing) ([#34](https://github.com/krizaka/krizaka-build/issues/34)) ([9fa18b7](https://github.com/krizaka/krizaka-build/commit/9fa18b7b5f867d1d2810810037f219a898d714b8))
* **test-support:** PMD baseline for krizaka-test-support (red in every workspace build) ([#129](https://github.com/krizaka/krizaka-build/issues/129)) ([6a1a0c2](https://github.com/krizaka/krizaka-build/commit/6a1a0c28c1169248953277a7c613aab589681977))
* **test-support:** SpotBugs baseline (ALTER ROLE cannot be parameterised) ([#130](https://github.com/krizaka/krizaka-build/issues/130)) ([2bd81c6](https://github.com/krizaka/krizaka-build/commit/2bd81c6acfe4facc70cb1cdc2e07213da387d616))
* **test-support:** the 3 critical Sonar findings on new code (layer-name constants, reachable() split) (krizaka/.github[#377](https://github.com/krizaka/krizaka-build/issues/377)) ([#118](https://github.com/krizaka/krizaka-build/issues/118)) ([fe38501](https://github.com/krizaka/krizaka-build/commit/fe38501c8ae3a2143590f842dfb21b93708c25bd))
* **ui:** the steel blue, one button shape, a quiet hover — @krizaka/ui 3.31.0 (krizaka-ui[#393](https://github.com/krizaka/krizaka-build/issues/393)) ([#59](https://github.com/krizaka/krizaka-build/issues/59)) ([a9d075c](https://github.com/krizaka/krizaka-build/commit/a9d075c64318ebb72b91f029f47d0d4776445bfd))


### Documentation

* **adr:** ADR-001 — Krizaka Java gap audit and roadmap ([#66](https://github.com/krizaka/krizaka-build/issues/66)) ([52fd10b](https://github.com/krizaka/krizaka-build/commit/52fd10b753478b550b4728fb78bd9137ba2ac5d1)), closes [#65](https://github.com/krizaka/krizaka-build/issues/65)
* **agents:** a bug means a missing guard ([#95](https://github.com/krizaka/krizaka-build/issues/95)) ([20d7545](https://github.com/krizaka/krizaka-build/commit/20d75451d764ab2bee3d239189d3365bec00c7ff))
* **agents:** a self-review checklist before opening a pull request ([#87](https://github.com/krizaka/krizaka-build/issues/87)) ([ab5daeb](https://github.com/krizaka/krizaka-build/commit/ab5daebc8078a4152536d2c8d6edadd78915c1f9))
* **agents:** no test, not done — every feature and fix ships with its tests ([#23](https://github.com/krizaka/krizaka-build/issues/23)) ([301d478](https://github.com/krizaka/krizaka-build/commit/301d478733996518a7353f5f7104fac5a8c30653))
* **readme:** Krizaka footer and dead tour image removed ([#31](https://github.com/krizaka/krizaka-build/issues/31)) ([3db9977](https://github.com/krizaka/krizaka-build/commit/3db99773d37cf8df46cdc10a182f5929e8700fdd))
* **readme:** link the trademark policy under License (.github[#298](https://github.com/krizaka/krizaka-build/issues/298)) ([#96](https://github.com/krizaka/krizaka-build/issues/96)) ([25d0814](https://github.com/krizaka/krizaka-build/commit/25d0814d20ed03b4fa88a8d44eb4ae3bd74cc8f6))
* **readme:** OpenSSF Scorecard badge ([#22](https://github.com/krizaka/krizaka-build/issues/22)) ([207f820](https://github.com/krizaka/krizaka-build/commit/207f820fa5d6bdc8dcb77a6fcc07c86eead09ff5))
* **readme:** SonarCloud badges ([#92](https://github.com/krizaka/krizaka-build/issues/92)) ([3580a73](https://github.com/krizaka/krizaka-build/commit/3580a7300bf49f412e9a50f938c0f11941283a42))
* **readme:** the BOM is 0.2.0; declare the domain blocks at 0.1.0 until BOM 0.3.0 ([#11](https://github.com/krizaka/krizaka-build/issues/11)) ([#12](https://github.com/krizaka/krizaka-build/issues/12)) ([96d58c2](https://github.com/krizaka/krizaka-build/commit/96d58c2c49cd372d2d23a4bf3f73fa30ff4c5640))
* **readme:** the new Krizaka mark, light and dark, from the organisation profile ([#9](https://github.com/krizaka/krizaka-build/issues/9)) ([41984c6](https://github.com/krizaka/krizaka-build/commit/41984c6b37cc8d4a914d12fa4593e0ea52643036))
* **site:** « Features » (honest matrix, checked against the code) and « Configuration » (generated from the metadata), idempotency guide ([#68](https://github.com/krizaka/krizaka-build/issues/68)) ([788253f](https://github.com/krizaka/krizaka-build/commit/788253f9e83e438116f9c6ab93c169efc04a342f)), closes [#65](https://github.com/krizaka/krizaka-build/issues/65)
* **site:** java.krizaka.com — the documentation of Krizaka Java, a static site built from this repository ([#27](https://github.com/krizaka/krizaka-build/issues/27)) ([472d811](https://github.com/krizaka/krizaka-build/commit/472d8115c114ba5a76769e9c0a88c910d444be66))
* **site:** persistence guide, the outbox the kit now ships, three health indicators; matrix: health and the outbox store shipped ([#70](https://github.com/krizaka/krizaka-build/issues/70)) ([08863ce](https://github.com/krizaka/krizaka-build/commit/08863ce1995d138fd6f54d4f951ebbe33963cedc)), closes [#65](https://github.com/krizaka/krizaka-build/issues/65)
* the building blocks missing from Maven Central, the check and the plan ([#82](https://github.com/krizaka/krizaka-build/issues/82)) ([#84](https://github.com/krizaka/krizaka-build/issues/84)) ([1239e0c](https://github.com/krizaka/krizaka-build/commit/1239e0c66dcea8bba5d4849ae58f2e7c7253ff29))

## [0.2.0](https://github.com/krizaka/krizaka-build/compare/v0.1.0...v0.2.0) (2026-10-09)


### Features

* BOM with krizaka-observability and the starters, japicmp, releases by release-please ([#2](https://github.com/krizaka/krizaka-build/issues/2)) ([2635231](https://github.com/krizaka/krizaka-build/commit/2635231ff26d02c6d9ff78e7ee85005a4eb26bd6))
* **test-support:** AbstractContainerIntegrationTest — shared PostgreSQL and RabbitMQ for integration tests ([#1](https://github.com/krizaka/krizaka-build/issues/1)) ([9761832](https://github.com/krizaka/krizaka-build/commit/9761832039d4303587f98620713fd8275daf52cb))


### Bug Fixes

* **parent:** japicmp lets a never-released artifact through ([#5](https://github.com/krizaka/krizaka-build/issues/5)) ([a803000](https://github.com/krizaka/krizaka-build/commit/a803000a995505eb2305a10bf1135c577a5520df))
* **release:** release-please skips the -SNAPSHOT pull request ([#4](https://github.com/krizaka/krizaka-build/issues/4)) ([f21163e](https://github.com/krizaka/krizaka-build/commit/f21163ec67940eda5f43c724a4debd6b9fe86255))

## Before release-please

Written by hand, in the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format.

### Unreleased when release-please took over (shipped in the first release below it)

#### Added

- `krizaka-bom`: `krizaka-observability` and the four starters (`krizaka-spring-boot-starter-web`, `-security`,
  `-rabbitmq`, `-observability`); one version property per repository (`krizaka-platform-kit.version`,
  `krizaka-users.version`, `krizaka-notifications.version`, `krizaka-billing.version`, defaulting to the BOM's own
  version).
- `krizaka-parent`: `japicmp-maven-plugin` in the `release` profile — every jar is compared with its latest release on
  Maven Central (resolved by `build-helper:released-version`) and a binary-incompatible change fails the build
  (`japicmp.breakBuildOnBinaryIncompatibleModifications`, `japicmp.skip`).
- Releases by release-please and Conventional Commits pull request titles (`commitlint`).
- `krizaka-test-support`: `AbstractContainerIntegrationTest` — one PostgreSQL (`postgres:16-alpine`) and one RabbitMQ
  (`rabbitmq:4-management-alpine`) per JVM, shared by every integration test of the run and handed to Spring through
  `@DynamicPropertySource` (`spring.datasource.*`, `spring.rabbitmq.*`).

### Version 0.1.0

#### Added

- `krizaka-parent`: Maven Central metadata, Java 21 build conventions (google-java-format, Surefire/Failsafe, JaCoCo,
  enforced toolchain with pinned plugins), and the `release` profile (sources, javadoc, Bouncy Castle signature, Central
  Portal upload with manual publication by default).
- `krizaka-bom`: `krizaka-security`, `krizaka-messaging`, and the `krizaka-users`, `krizaka-notifications` and
  `krizaka-billing` contracts and clients.
- `krizaka-test-support`: generic ArchUnit code rules, source rules, configuration-binding rules, the `infra/initdb`
  locator and Testcontainers helpers (PostgreSQL, RabbitMQ).
