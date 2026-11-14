import { test } from "node:test";
import assert from "node:assert/strict";
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { publicationTarget, registryOf } from "../lib/publication.ts";

/* The site states where the artifacts are only from the build's real publication target (ci.yml `registry:`). While that is
   not Central — founder, 2026-11-14 — « Maven Central » is rendered nowhere: messages, guides, sources, and the pages of
   out/ when the site was built. */
const root = new URL("../", import.meta.url).pathname;
const ci = readFileSync(join(root, "..", ".github/workflows/ci.yml"), "utf8");
const CLAIM = /maven\s+central/i;
/* Past is not a claim: the changelog tells what each release did, Central included. The family registry compiled into
   @krizaka/ui (out/_next/static) is that package's data, to be corrected there; nothing of this site's own is exempt. */
const PAST_OR_UPSTREAM = /(^|\/)changelog\/|^out\/_next\/static\//;

const walk = (dir, keep) =>
  readdirSync(dir).flatMap((name) => {
    const file = join(dir, name);
    return statSync(file).isDirectory() ? (["node_modules", ".next", "generated"].includes(name) ? [] : walk(file, keep)) : keep(file) ? [file] : [];
  });

test("the publication target is read from the CI's registry line", () => {
  assert.equal(registryOf("      registry: none   # a tag run deploys nowhere"), "none");
  assert.equal(registryOf("registry: central"), "central");
  assert.equal(registryOf("with:\n  threads: 2"), "none");
  assert.equal(publicationTarget("registry: central"), "central");
  assert.equal(publicationTarget("registry: spaces"), "private");
  assert.equal(publicationTarget(ci), publicationTarget());
});

test("no « Maven Central » claim while the publication target is not Central", () => {
  if (publicationTarget(ci) === "central") return;
  const files = [
    ...walk(join(root, "messages"), (f) => f.endsWith(".json")),
    ...walk(join(root, "content"), (f) => f.endsWith(".mdx") || f.endsWith(".json")),
    ...walk(join(root, "app"), (f) => /\.(tsx?|css)$/.test(f)),
    ...walk(join(root, "lib"), (f) => /\.(ts|json)$/.test(f) && !f.endsWith("publication.ts")),
    ...walk(join(root, "scripts"), (f) => f.endsWith(".mjs") && !f.endsWith(".test.mjs")),
    // The rendered pages, when the site was built (`npm run build`): what a reader actually sees.
    ...(existsSync(join(root, "out")) ? walk(join(root, "out"), (f) => /\.(html|txt|xml|json)$/.test(f)) : []),
  ];
  const claims = files
    .map((f) => f.slice(root.length))
    .filter((f) => !PAST_OR_UPSTREAM.test(f) && CLAIM.test(readFileSync(join(root, f), "utf8")));
  assert.deepEqual(claims, [], "these files claim Maven Central, but the build publishes nowhere there");
});
