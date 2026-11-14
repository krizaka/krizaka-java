// ci.yml: a v* tag deploys nowhere (krizaka-internal#3345, #3554): `registry: none`, no Central secret. Maven Central only comes back explicitly (the monthly public release, #3343).
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const ci = readFileSync(new URL("../.github/workflows/ci.yml", import.meta.url), "utf8");
const release = readFileSync(new URL("../.github/workflows/release-please.yml", import.meta.url), "utf8");

test("a release deploys nowhere: ci.yml passes registry none, never central, and no Central or signing secret", () => {
  assert.match(ci, /\n {6}registry: none\b/);
  const registryLines = ci.split("\n").filter((line) => line.trimStart().startsWith("registry:"));
  for (const line of registryLines) assert.equal(line.split("#")[0].includes("central"), false, line);
  for (const secret of ["MAVEN_CENTRAL_USERNAME", "MAVEN_CENTRAL_PASSWORD", "MAVEN_GPG_KEY", "MAVEN_GPG_PASSPHRASE"]) {
    assert.doesNotMatch(ci, new RegExp(secret), secret);
  }
});

test("nothing is left of the GitHub Packages republish (no deploy plan, no packages permission, no publish token)", () => {
  assert.doesNotMatch(ci, /deploy-plan|deploy-modules|deploy-repository|packages: (read|write)|MAVEN_PUBLISH_TOKEN/);
  assert.doesNotMatch(release, /packages: read|MAVEN_PUBLISH_TOKEN/);
});

test("the release waits for the tag's run, then reports the registry state instead of going red when there is none", () => {
  assert.doesNotMatch(release, /release-follow-ups/);
  assert.match(release, /gh run watch "\$id"[^\n]*--exit-status/);
  assert.ok(release.indexOf("gh run watch") < release.indexOf('node scripts/check-published.mjs "$TAG"'));
  assert.match(release, /Every module of the release is on its registry/);
});
