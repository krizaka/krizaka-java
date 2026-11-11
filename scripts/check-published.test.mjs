import assert from "node:assert/strict";
import test from "node:test";
import { missing, publishable, versionsFromMetadata } from "./check-published.mjs";

const poms = {
  "r/pom.xml": `<project><parent><artifactId>krizaka-parent</artifactId></parent><artifactId>root</artifactId><properties><maven.deploy.skip>true</maven.deploy.skip></properties><modules><module>a</module><module>kit</module></modules></project>`,
  "r/a/pom.xml": `<project><parent><artifactId>root</artifactId></parent><artifactId>a</artifactId><properties><maven.deploy.skip>false</maven.deploy.skip></properties></project>`,
  "r/kit/pom.xml": `<project><artifactId>kit</artifactId><properties><maven.deploy.skip>false</maven.deploy.skip></properties><modules><module>web</module><module>svc</module></modules></project>`,
  "r/kit/web/pom.xml": `<project><artifactId>web</artifactId></project>`,
  "r/kit/svc/pom.xml": `<project><artifactId>svc</artifactId><properties><maven.deploy.skip>true</maven.deploy.skip></properties></project>`,
};
const read = (p) => poms[p];
const exists = (p) => p in poms;

test("the reactor is walked and the modules that do not ship are dropped", () => {
  assert.deepEqual(publishable("r", read, exists).map((m) => m.artifactId), ["a", "kit", "web"]);
});
test("a module that never reached the registry, or lacks the version, is reported", async () => {
  const versions = { a: ["0.6.0"], kit: ["0.5.1"], web: null };
  const gaps = await missing([{ artifactId: "a" }, { artifactId: "kit" }, { artifactId: "web" }], "0.6.0", async (id) => versions[id]);
  assert.deepEqual(gaps.map((g) => g.artifactId), ["kit", "web"]);
  assert.equal(gaps[1].why, "package not found");
});
test("nothing is reported when every module has the version", async () => {
  assert.deepEqual(await missing([{ artifactId: "a" }], "0.6.0", async () => ["0.6.0", "0.5.1"]), []);
});

test("the workflow opens no issue (public repo: internal issues live in krizaka-internal)", async () => {
  const { readFileSync } = await import("node:fs");
  const yml = readFileSync(new URL("../.github/workflows/published-check.yml", import.meta.url), "utf8");
  assert.doesNotMatch(yml, /gh issue|issues:\s*write/);
});

test("Central's maven-metadata.xml gives the versions (Maven Central is the one registry, 2026-11-06)", () => {
  const xml = "<metadata><versioning><latest>0.12.0</latest><versions><version>0.1.0</version><version>0.2.0</version><version>0.12.0</version></versions></versioning></metadata>";
  assert.deepEqual(versionsFromMetadata(xml), ["0.1.0", "0.2.0", "0.12.0"]);
  assert.deepEqual(versionsFromMetadata("<metadata/>"), []);
});

test("no script, workflow or POM of this repository names the GitHub Packages Maven registry", async () => {
  const { readFileSync } = await import("node:fs");
  for (const file of [".github/workflows/ci.yml", ".github/workflows/release-please.yml", ".github/workflows/published-check.yml", "krizaka-parent/pom.xml", "scripts/check-published.mjs"]) {
    assert.doesNotMatch(readFileSync(new URL(`../${file}`, import.meta.url), "utf8"), /maven\.pkg\.github\.com|registry: github|packages: read/, file);
  }
});
