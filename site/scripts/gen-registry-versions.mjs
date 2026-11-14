#!/usr/bin/env node
/* The versions the site states, read from the registry at build time — one source, never typed in a page.

   Every artifact under com/krizaka on repo1 (its directory listing), then each maven-metadata.xml's <release> — the
   authoritative file, not the search index (which lags). Then the published BOM's own POM: what it manages, at which
   version, and whether Central has that exact version (the « start a project » generator offers only those). Writes the
   git-ignored lib/generated/maven-versions.json:
   { maven: { "krizaka-bom": "0.2.0", … }, bom: { version, manages: { "<artifactId>": { version, onCentral } } }, fetchedAt }.
   When Central cannot be reached the previous file is kept; without one the build fails: a page must not state a
   version it could not read. Usage: node scripts/gen-registry-versions.mjs */

import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { bomManaged } from "./bom-managed.mjs";

const OUT = join(dirname(fileURLToPath(import.meta.url)), "..", "lib", "generated", "maven-versions.json");
const BASE = "https://repo1.maven.org/maven2/com/krizaka/";

/* A passing 404, a 429 or a 5xx from Central must not freeze the repository (krizaka-internal#3354): retry with a bounded
   backoff. Worst case: 4 × 4 s timeouts + 1 + 2 + 4 s of sleep (Retry-After capped at 4 s) = 29 s, under 30 s. */
export const MAX_ATTEMPTS = 4;
export const BACKOFF_MS = [1_000, 2_000, 4_000];
export const MAX_RETRY_AFTER_MS = 4_000;
export const ATTEMPT_TIMEOUT_MS = 4_000;
const RETRYABLE = (status) => status === 404 || status === 429 || status >= 500;

const realSleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const retryAfterMs = (res) => {
  const header = res.headers?.get?.("retry-after");
  const seconds = header ? Number(header) : NaN;
  return Number.isFinite(seconds) && seconds >= 0 ? Math.min(seconds * 1000, MAX_RETRY_AFTER_MS) : undefined;
};

/** The one read of Central: fetch and sleep are passed in so tests need no network and no clock. */
export function createGet({ fetchImpl = fetch, sleep = realSleep, log = (line) => console.error(line) } = {}) {
  return async function get(url) {
    let failure;
    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      let wait;
      try {
        const res = await fetchImpl(url, { headers: { "user-agent": "krizaka-java-site-build" }, signal: AbortSignal.timeout(ATTEMPT_TIMEOUT_MS) });
        if (res.ok) return await res.text();
        failure = new Error(`${url} answered ${res.status}`);
        if (!RETRYABLE(res.status)) throw Object.assign(failure, { fatal: true });
        wait = retryAfterMs(res);
        failure.reason = String(res.status);
      } catch (error) {
        if (error.fatal) throw error;
        failure = Object.assign(new Error(`${url} failed: ${error.message}`), { reason: error.name === "TimeoutError" ? "timeout" : "network error" });
      }
      if (attempt === MAX_ATTEMPTS) break;
      log(`registry-versions: ${url} ${failure.reason}, retry ${attempt}/${MAX_ATTEMPTS - 1}`);
      await sleep(wait ?? BACKOFF_MS[attempt - 1]);
      failure = undefined;
    }
    throw failure;
  };
}
const get = createGet();

/** Every artifact with its latest release and every versiin the registry lists. */
async function mavenMetadata(get) {
  const listing = await get(BASE);
  const artifacts = [...listing.matchAll(/href="([a-z0-9-]+)\/"/g)].map((m) => m[1]);
  const entries = await Promise.all(
    artifacts.map(async (a) => {
      const xml = await get(`${BASE}${a}/maven-metadata.xml`).catch(() => "");
      const release = xml.match(/<release>([^<]+)<\/release>/)?.[1] ?? xml.match(/<latest>([^<]+)<\/latest>/)?.[1];
      return [a, { release, versions: [...xml.matchAll(/<version>([^<]+)<\/version>/g)].map((m) => m[1]) }];
    }),
  );
  return Object.fromEntries(entries.filter(([, v]) => v.release));
}

/** What the published BOM manages, each artifact with the version it resolves to and whether Central has it. */
async function bomOf(version, metadata, get) {
  const managed = bomManaged(await get(`${BASE}krizaka-bom/${version}/krizaka-bom-${version}.pom`));
  if (!Object.keys(managed).length) throw new Error(`krizaka-bom ${version} manages nothing readable`);
  return { version, manages: Object.fromEntries(Object.entries(managed).map(([id, v]) => [id, { version: v, onCentral: Boolean(metadata[id]?.versions.includes(v)) }])) };
}

/** Reads Central through `get` and writes `out`; when Central stays unreachable the previous file is kept. */
export async function generate({ get, out = OUT } = {}) {
try {
  const metadata = await mavenMetadata(get);
  const maven = Object.fromEntries(Object.entries(metadata).map(([id, m]) => [id, m.release]));
  if (!maven["krizaka-bom"]) throw new Error("krizaka-bom is not listed");
  const bom = await bomOf(maven["krizaka-bom"], metadata, get);
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, JSON.stringify({ maven, bom, fetchedAt: new Date().toISOString() }, null, 2) + "\n");
  const ready = Object.values(bom.manages).filter((m) => m.onCentral).length;
  console.log(`registry-versions: ${Object.keys(maven).length} Maven artifacts (krizaka-bom ${maven["krizaka-bom"]}, manages ${Object.keys(bom.manages).length}, ${ready} in the registry at that version)`);
} catch (error) {
  if (!existsSync(out)) {
    throw new Error(`registry-versions: the registry unreachable (${error.message}) and no previous file — cannot state a version`);
  }
  console.warn(`registry-versions: the registry unreachable (${error.message}) — keeping ${JSON.parse(readFileSync(out, "utf8")).fetchedAt}`);
}
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) await generate({ get }).catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
