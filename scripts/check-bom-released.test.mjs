import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { behind, managedBlocks, releasedSince, unreleased } from "./check-bom-released.mjs";

const bom = readFileSync(new URL("../krizaka-bom/pom.xml", import.meta.url), "utf8");

test("reads the building blocks the BOM manages", () => {
  const blocks = managedBlocks(bom);
  assert.ok(!("krizaka-users" in blocks), "users is a module of this repository (ADR-021): the BOM manages it at project.version");
  assert.ok(!("krizaka-notifications" in blocks), "notifications is a module of this repository (ADR-021): the BOM manages it at project.version");
  assert.ok(!("krizaka-service-kit" in blocks), "the service kit is a module of this repository (ADR-021): the BOM manages it at project.version");
  assert.ok(!("krizaka-billing" in blocks), "billing is a module of this repository (ADR-021): the BOM manages it at project.version");
  assert.ok(!("jspecify" in blocks));
});

test("a managed version with no release tag is refused", () => {
  const blocks = { "krizaka-service-kit": "0.4.0", "krizaka-users": "0.3.0" };
  const tags = { "krizaka-service-kit": ["v0.4.0", "v0.3.0"], "krizaka-users": ["v0.2.0"] };
  assert.deepEqual(unreleased(blocks, tags), [{ block: "krizaka-users", version: "0.3.0" }]);
});

test("every managed version released passes; a block with no tags at all fails", () => {
  assert.deepEqual(unreleased({ "krizaka-billing": "0.2.0" }, { "krizaka-billing": ["v0.2.0"] }), []);
  assert.equal(unreleased({ "krizaka-billing": "0.2.0" }, {}).length, 1);
});

test("a block waiting for its first release is tolerated, then must leave the list once tagged", () => {
  const blocks = { "krizaka-users": "0.2.0" };
  assert.deepEqual(unreleased(blocks, {}, ["krizaka-users"]), []);
  assert.deepEqual(releasedSince(blocks, {}, ["krizaka-users"]), []);
  assert.deepEqual(releasedSince(blocks, { "krizaka-users": ["v0.2.0"] }, ["krizaka-users"]), ["krizaka-users"]);
});

test("a BOM left behind its repository's newest release is refused", () => {
  const tags = { "krizaka-service-kit": ["v0.3.0", "v0.4.1", "v0.4.0", "v0.10.0-rc"] };
  assert.deepEqual(behind({ "krizaka-service-kit": "0.4.0" }, tags), [{ block: "krizaka-service-kit", version: "0.4.0", latest: "v0.4.1" }]);
  assert.deepEqual(behind({ "krizaka-service-kit": "0.4.1" }, tags), []);
  assert.deepEqual(behind({ "krizaka-users": "0.2.0" }, {}), []);
});
