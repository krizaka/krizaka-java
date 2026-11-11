// ci.yml: a v* tag publishes the whole reactor to Maven Central, the one registry of com.krizaka (founder, 2026-11-06).
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const ci = readFileSync(new URL("../.github/workflows/ci.yml", import.meta.url), "utf8");
const release = readFileSync(new URL("../.github/workflows/release-please.yml", import.meta.url), "utf8");

test("the org pipeline publishes to Maven Central only, signed", () => {
  assert.match(ci, /\n {6}registry: central\b/);
  for (const secret of ["MAVEN_CENTRAL_USERNAME", "MAVEN_CENTRAL_PASSWORD", "MAVEN_GPG_KEY", "MAVEN_GPG_PASSPHRASE"]) {
    assert.match(ci, new RegExp(`${secret}: \\$\\{\\{ secrets\\.${secret} \\}\\}`), secret);
  }
});

test("nothing is left of the GitHub Packages republish (no deploy plan, no packages permission, no publish token)", () => {
  assert.doesNotMatch(ci, /deploy-plan|deploy-modules|deploy-repository|packages: (read|write)|MAVEN_PUBLISH_TOKEN/);
  assert.doesNotMatch(release, /packages: read|MAVEN_PUBLISH_TOKEN/);
});

test("the release waits for the tag's deploy, then checks every module on Maven Central", () => {
  assert.doesNotMatch(release, /release-follow-ups/);
  assert.match(release, /gh run watch "\$id"[^\n]*--exit-status/);
  assert.ok(release.indexOf("gh run watch") < release.indexOf('node scripts/check-published.mjs "$TAG"'));
  assert.match(release, /Every module of the release is on Maven Central/);
});
