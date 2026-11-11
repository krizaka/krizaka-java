import { test } from "node:test";
import assert from "node:assert/strict";
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { extract, FOLDERS, locate, REPOSITORIES } from "../scripts/gen-building-blocks.mjs";
import { countTests } from "../scripts/gen-proof.mjs";

// ADR-021: the service kit, users and billing are folders of krizaka-java. Their former repositories
// (krizaka-service-kit, krizaka-users: deleted 2026-11-06) are never cloned, never linked.
const SITE = new URL("..", import.meta.url).pathname;

test("every source is a folder of this checkout: nothing is cloned", () => {
  const dirs = locate();
  assert.deepEqual([...dirs.keys()], REPOSITORIES);
  for (const dir of dirs.values()) assert.ok(existsSync(join(dir, "pom.xml")), `${dir} has no pom.xml`);
});

test("an artifact is listed once, under the folder that holds it, and its repository links krizaka-java", () => {
  const data = extract(locate());
  const ids = data.modules.map((m) => m.id);
  assert.equal(new Set(ids).size, ids.length, "an artifact is read twice (the root reactor and its folder)");
  for (const m of data.modules) {
    if (m.repository !== "krizaka-java") assert.ok(m.path.startsWith(`${m.repository}/`), `${m.id}: ${m.path} is not in ${m.repository}/`);
  }
  for (const r of data.repositories) assert.match(r.url, /^https:\/\/github\.com\/krizaka\/krizaka-java(\/tree\/main\/[\w-]+)?$/);
});

test("the tests of a folder are not counted again under the root", () => {
  const dirs = locate();
  const root = dirs.get("krizaka-java");
  const all = countTests(root).tests;
  const own = countTests(root, { skip: FOLDERS }).tests;
  const folders = FOLDERS.map((name) => countTests(dirs.get(name)).tests);
  assert.ok(folders.every((n) => n > 0));
  assert.equal(own + folders.reduce((a, b) => a + b, 0), all);
});

test("no page, script or message names a deleted repository", () => {
  const files = [];
  const walk = (dir) => {
    for (const name of readdirSync(dir)) {
      if (["node_modules", ".next", ".cache", "generated", "tests"].includes(name)) continue;
      const p = join(dir, name);
      if (statSync(p).isDirectory()) walk(p);
      else if (/\.(mdx?|json|mjs|tsx?)$/.test(name) && name !== "package-lock.json") files.push(p);
    }
  };
  for (const dir of ["app", "content", "lib", "messages", "scripts"]) walk(join(SITE, dir));
  for (const file of files) {
    const text = readFileSync(file, "utf8");
    assert.doesNotMatch(text, /github\.com\/krizaka\/(krizaka-users|krizaka-service-kit|krizaka-accounts)(?![-\w])/, `${file} links a deleted repository`);
  }
});
