#!/usr/bin/env node
/* The proof the home states, read from the code — never typed in a page.

   - the platform: Java and Spring Boot, from this repository's parent POM;
   - the tests of the Krizaka Java repositories (every @Test and @ParameterizedTest under src/test), and how many of them
     start a real PostgreSQL or RabbitMQ (Testcontainers) instead of a mock;
   - the Sovkora services built on Krizaka Java: the deployable modules of the Sovkora service repositories that depend on a
     `com.krizaka` artifact (a shallow, sparse clone of their POMs, or <KRIZAKA_REPOS_DIR>/<repository>).
   Writes the git-ignored lib/generated/proof.json. When a repository cannot be read the previous file is kept; without one
   the build fails: a page must not state a fact it could not read. Usage: node scripts/gen-proof.mjs */

import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { FOLDERS, REPOSITORIES, locate } from "./gen-building-blocks.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const SITE = join(here, "..");
const OUT = join(SITE, "lib", "generated", "proof.json");
const read = (p) => (existsSync(p) ? readFileSync(p, "utf8") : "");

function walk(dir, pred) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir).flatMap((name) => {
    if (["node_modules", "target", ".git", ".cache", "out"].includes(name)) return [];
    const p = join(dir, name);
    return statSync(p).isDirectory() ? walk(p, pred) : pred(p) ? [p] : [];
  });
}

/** Java and Spring Boot, from the parent POM's properties. */
export function platformOf(pom) {
  const java = pom.match(/<java\.version>([^<]+)<\/java\.version>/)?.[1].trim();
  const springBoot = pom.match(/<spring-boot\.version>([^<]+)<\/spring-boot\.version>/)?.[1].trim();
  if (!java) throw new Error("gen-proof: the parent POM has no java.version");
  if (!springBoot) throw new Error("gen-proof: the parent POM has no spring-boot.version");
  return { java, springBoot, springBootMinor: springBoot.split(".").slice(0, 2).join(".") };
}

const REAL = /AbstractContainerIntegrationTest|PostgreSQLContainer|RabbitMQContainer|@Testcontainers/;
/** The tests under src/test of a tree, and those of them whose class starts a real PostgreSQL or RabbitMQ. */
export function countTests(dir, { skip = [] } = {}) {
  let tests = 0;
  let realInfrastructure = 0;
  const skipped = skip.map((name) => `${join(dir, name)}/`);
  for (const file of walk(dir, (p) => p.endsWith(".java") && p.includes("/src/test/") && !skipped.some((s) => p.startsWith(s)))) {
    const src = read(file);
    const n = (src.match(/@(?:Test|ParameterizedTest)\b/g) ?? []).length;
    tests += n;
    if (REAL.test(src)) realInfrastructure += n;
  }
  return { tests, realInfrastructure };
}

/** The Sovkora repositories that hold a service (each one a repository of the `krizaka` organisation). */
export const SOVKORA_SERVICE_REPOSITORIES = ["sovkora-automation-service", "sovkora-conversation-service", "sovkora-edge-service", "sovkora-job-service", "sovkora-knowledge-service", "sovkora-studio-service"];

/** The deployable modules of the given trees that depend on a Krizaka Java artifact, with the artifacts they use. */
export function sovkoraServices(dirs) {
  const services = [];
  for (const file of dirs.flatMap((dir) => walk(dir, (p) => p.endsWith("/pom.xml")))) {
    const pom = read(file);
    const own = pom.replace(/<parent>[\s\S]*?<\/parent>/, "").replace(/<build>[\s\S]*?<\/build>/g, "").replace(/<dependencyManagement>[\s\S]*?<\/dependencyManagement>/g, "");
    const id = own.match(/<artifactId>([\w.-]+)<\/artifactId>/)?.[1];
    if (!id || /<packaging>pom<\/packaging>/.test(own) || /-(api|client)$/.test(id)) continue;
    const artifacts = [...own.matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)]
      .filter((m) => /<groupId>com\.krizaka<\/groupId>/.test(m[1]))
      .map((m) => m[1].match(/<artifactId>([^<]+)<\/artifactId>/)?.[1]?.trim())
      .filter(Boolean);
    if (artifacts.length) services.push({ id, artifacts: [...new Set(artifacts)].sort() });
  }
  return services.sort((a, b) => a.id.localeCompare(b.id));
}

/** Where the Sovkora service repositories are: a folder named by the caller, else a sparse clone of their POMs under .cache/.
 *  Repositories that are inaccessible (archived, private) are silently skipped; the caller receives only the dirs that exist. */
function locateSovkora(base) {
  return SOVKORA_SERVICE_REPOSITORIES.map((name) => {
    if (base) return join(base, name);
    const dir = join(SITE, ".cache", "repos", name);
    if (!existsSync(join(dir, ".git"))) {
      mkdirSync(dirname(dir), { recursive: true });
      try {
        execFileSync("git", ["clone", "--quiet", "--depth", "1", "--filter=blob:none", "--sparse", `https://github.com/krizaka/${name}.git`, dir], { stdio: ["ignore", "inherit", "inherit"] });
        execFileSync("git", ["-C", dir, "sparse-checkout", "set", "--no-cone", "**/pom.xml"], { stdio: ["ignore", "inherit", "inherit"] });
      } catch {
        return null;
      }
    }
    return dir;
  }).filter(Boolean);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  try {
    const dirs = locate();
    const repositories = REPOSITORIES.map((name) => ({ name, ...countTests(dirs.get(name), { skip: name === REPOSITORIES[0] ? FOLDERS : [] }) }));
    const proof = {
      platform: platformOf(read(join(SITE, "..", "krizaka-parent", "pom.xml"))),
      tests: {
        total: repositories.reduce((n, r) => n + r.tests, 0),
        realInfrastructure: repositories.reduce((n, r) => n + r.realInfrastructure, 0),
        repositories,
      },
      sovkora: { services: sovkoraServices(locateSovkora(globalThis.process.env.KRIZAKA_REPOS_DIR)) },
    };
    if (!proof.tests.total) throw new Error("no test found — check the repositories");
    if (!proof.sovkora.services.length && globalThis.process.env.KRIZAKA_REPOS_DIR) throw new Error("no Sovkora service found — check the Sovkora repositories");
    mkdirSync(dirname(OUT), { recursive: true });
    writeFileSync(OUT, JSON.stringify(proof, null, 2) + "\n");
    console.log(`gen-proof: Java ${proof.platform.java}, Spring Boot ${proof.platform.springBoot}, ${proof.tests.total} tests (${proof.tests.realInfrastructure} on real infrastructure), ${proof.sovkora.services.length} Sovkora services`);
  } catch (error) {
    if (!existsSync(OUT)) {
      console.error(`gen-proof: ${error.message} and no previous file — cannot state the proof`);
      process.exit(1);
    }
    console.warn(`gen-proof: ${error.message} — keeping the previous lib/generated/proof.json`);
  }
}
