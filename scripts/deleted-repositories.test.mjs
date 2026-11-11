// krizaka-internal#2656: krizaka-platform-kit, krizaka-users, krizaka-notifications, krizaka-billing, krizaka-payments and krizaka-accounts
// were deleted once their code joined this repository (ADR-021). A workflow that checks one out, a script that publishes through
// one, or a POM whose url/scm names one fails at the next run or ships a dead link to the registries: nothing here names them.
import test from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

export const DELETED = /krizaka\/krizaka-(platform-kit|users|notifications|billing|payments|accounts)(?![\w-])/;

const root = new URL("..", import.meta.url).pathname;
const SKIP = new Set(["node_modules", "target", ".git", "site"]);

/** Files CI and the release read: the workflows, the scripts and every POM. */
function files(dir = root, out = []) {
  for (const name of readdirSync(dir)) {
    if (SKIP.has(name)) continue;
    const path = join(dir, name);
    if (statSync(path).isDirectory()) files(path, out);
    else if (/\.(ya?ml|mjs|sh)$/.test(name) || name === "pom.xml") out.push(path);
  }
  return out;
}

test("the pattern names the deleted repositories only", () => {
  assert.match("repository: krizaka/krizaka-users\n", DELETED);
  assert.match("https://github.com/krizaka/krizaka-platform-kit.git", DELETED);
  assert.doesNotMatch("krizaka/krizaka-users-api", DELETED);
  assert.doesNotMatch("https://github.com/krizaka/krizaka-java/tree/main/krizaka-users", DELETED);
});

test("no workflow, script or POM names a deleted repository", () => {
  const self = new URL(import.meta.url).pathname;
  const offenders = files()
    .filter((path) => path !== self)
    .flatMap((path) =>
      readFileSync(path, "utf8")
        .split("\n")
        .map((line, i) => (DELETED.test(line) ? `${relative(root, path)}:${i + 1}: ${line.trim()}` : null))
        .filter(Boolean),
    );
  assert.deepEqual(offenders, []);
});

// 2026-11-06 (#229): the folders' POMs and READMEs point at this repository, never at a deleted one (krizaka-billing is gone too).
const GONE = "krizaka-users|krizaka-platform-kit|krizaka-notifications|krizaka-accounts|krizaka-billing";
const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

test("the POMs and READMEs of the folders point at this repository", () => {
  for (const folder of ["krizaka-service-kit", "krizaka-users", "krizaka-billing"]) {
    for (const file of ["pom.xml", "README.md"]) {
      assert.doesNotMatch(read(`${folder}/${file}`), new RegExp(`github\\.com[/:]krizaka/(${GONE})(?![-\\w])`), `${folder}/${file}`);
    }
  }
});
