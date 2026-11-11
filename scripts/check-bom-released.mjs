#!/usr/bin/env node
// Guard (krizaka-internal#1878): krizaka-bom manages only RELEASED versions of the building blocks.
//
// A building block's version in the BOM (`<krizaka-service-kit.version>`…) must be a version that has a `v<version>` tag in its
// repository: a bump that points at a version nobody released makes every consumer fail to resolve it, and a BOM left behind
// makes every Sovkora PR fail check-krizaka-versions. The check reads the tags of krizaka/<block> from the GitHub API
// (GITHUB_TOKEN when set); the building blocks are published to GitHub Packages from those same tags (ADR-019).
//
//   node scripts/check-bom-released.mjs [krizaka-bom/pom.xml]
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

/** { "krizaka-service-kit": "0.4.0", … } from the `<krizaka-*.version>` properties of the BOM. */
export function managedBlocks(pomXml) {
  const blocks = {};
  for (const [, name, version] of pomXml.matchAll(/<(krizaka-[a-z-]+)\.version>([^<$]+)<\/\1\.version>/g)) blocks[name] = version.trim();
  return blocks;
}

/**
 * Blocks whose first release is still to come (their BOM property was set ahead of it: krizaka-internal#1879 publishes them to
 * GitHub Packages next). A ratchet: it only shrinks. Once a block has the tag, the guard fails until it is removed from here.
 */
export const NOT_YET_RELEASED = [];

/** The blocks whose managed version has no `v<version>` tag: [{ block, version }]. */
export function unreleased(blocks, tagsByBlock, grandfathered = []) {
  return Object.entries(blocks)
    .filter(([block, version]) => !grandfathered.includes(block) && !(tagsByBlock[block] ?? []).includes(`v${version}`))
    .map(([block, version]) => ({ block, version }));
}

/** Grandfathered blocks that now have their tag: the list must shrink. */
export function releasedSince(blocks, tagsByBlock, grandfathered = NOT_YET_RELEASED) {
  return grandfathered.filter((block) => blocks[block] && (tagsByBlock[block] ?? []).includes(`v${blocks[block]}`));
}

const semver = (tag) => tag.replace(/^v/, "").split(".").map(Number);
const newer = (a, b) => { const [x, y] = [semver(a), semver(b)]; for (let i = 0; i < 3; i += 1) {
    if (x[i] !== y[i]) return x[i] > y[i];
  }
  return false;
};

/** The blocks whose repository has released a version newer than the one managed: a BOM left behind fails every consumer's check-krizaka-versions. */
export function behind(blocks, tagsByBlock, grandfathered = []) {
  return Object.entries(blocks)
    .filter(([block]) => !grandfathered.includes(block))
    .map(([block, version]) => ({ block, version, latest: (tagsByBlock[block] ?? []).filter((t) => /^v\d+\.\d+\.\d+$/.test(t)).reduce((a, t) => (a && !newer(t, a) ? a : t), null) }))
    .filter(({ version, latest }) => latest && newer(latest, version));
}

async function tagsOf(block, token) {
  const headers = { Accept: "application/vnd.github+json", ...(token ? { Authorization: `Bearer ${token}` } : {}) };
  const names = [];
  for (let page = 1; page <= 5; page += 1) {
    const response = await fetch(`https://api.github.com/repos/krizaka/${block}/tags?per_page=100&page=${page}`, { headers });
    if (!response.ok) throw new Error(`cannot read the tags of krizaka/${block}: HTTP ${response.status}`);
    const batch = await response.json();
    names.push(...batch.map((t) => t.name));
    if (batch.length < 100) break;
  }
  return names;
}

const BOM_POM = "krizaka-bom/pom.xml";

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const blocks = managedBlocks(readFileSync(BOM_POM, "utf8"));
  const tags = {};
  for (const block of Object.keys(blocks)) tags[block] = await tagsOf(block, process.env.GITHUB_TOKEN);
  const missing = unreleased(blocks, tags, NOT_YET_RELEASED);
  for (const block of releasedSince(blocks, tags)) { console.error(`::error::${block} is released now: remove it from NOT_YET_RELEASED in scripts/check-bom-released.mjs`); process.exitCode = 1; }
  for (const { block, version } of missing) console.error(`::error::krizaka-bom manages ${block} ${version}, which is not released (no tag v${version} in krizaka/${block})`);
  for (const { block, version, latest } of behind(blocks, tags, NOT_YET_RELEASED)) { console.error(`::error::krizaka-bom manages ${block} ${version} but ${latest} is released: set the version in krizaka-bom/pom.xml`); process.exitCode = 1; }
  if (missing.length) process.exit(1);
  if (process.exitCode) process.exit(process.exitCode);
  const managed = Object.entries(blocks).map(([b, v]) => b + " " + v).join(", ");
  console.log(`krizaka-bom manages released versions only: ${managed}`);
}
