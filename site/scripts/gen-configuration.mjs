#!/usr/bin/env node
/* The configuration reference of Krizaka Java, read from the metadata Spring Boot's configuration processor generates —
   never written by hand.

   Each repository whose artifacts declare `krizaka.*` properties commits `docs/configuration-metadata.json`, which a test of
   that repository (`ConfigurationReferenceTest`) holds equal to the generated `META-INF/spring-configuration-metadata.json`
   and refuses when a property has no description or no default. This script reads those files from the clones
   gen-building-blocks.mjs uses (or KRIZAKA_REPOS_DIR), checks their shape and writes the git-ignored
   lib/generated/configuration.json. `--check` fails when the file is missing or stale.
   Usage: node scripts/gen-configuration.mjs [--check] */

import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { locate, sourceUrl } from "./gen-building-blocks.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const OUT = join(here, "..", "lib", "generated", "configuration.json");

/** The repositories whose artifacts have properties, in the order the reference lists them. */
export const CONFIG_REPOSITORIES = ["krizaka-service-kit", "krizaka-users", "krizaka-billing"];
export const METADATA = "docs/configuration-metadata.json";

const fail = (message) => {
  throw new Error(`gen-configuration: ${message}`);
};

/** One repository's reference, checked: the shape the site renders and the rules the repository's test enforces. */
export function checkReference(reference, repository) {
  if (reference.repository !== repository) fail(`${repository}/${METADATA} names the repository ${reference.repository}`);
  if (!Array.isArray(reference.modules) || reference.modules.length === 0) fail(`${repository}/${METADATA} lists no module`);
  const names = new Set();
  for (const module of reference.modules) {
    if (!/^krizaka-[a-z0-9-]+$/.test(module.artifact ?? "")) fail(`${repository}: a module without an artifact id`);
    if (!Array.isArray(module.properties)) fail(`${module.artifact}: no properties`);
    for (const p of module.properties) {
      if (!/^krizaka\.[a-z0-9.-]+$/.test(p.name ?? "")) fail(`${module.artifact}: « ${p.name} » is not a krizaka.* property`);
      if (names.has(p.name)) fail(`${p.name} is declared twice`);
      names.add(p.name);
      if (!p.type) fail(`${p.name} has no type`);
      if (!p.description || p.description.includes("{@")) fail(`${p.name} has no description written for the reader`);
    }
    for (const hint of module.hints ?? []) {
      if (!names.has(hint.name)) fail(`${module.artifact}: a hint for ${hint.name}, which is not a property of the module`);
    }
  }
  return reference;
}

export function extract(dirs) {
  return {
    repositories: CONFIG_REPOSITORIES.map((name) => {
      const file = join(dirs.get(name), METADATA);
      if (!existsSync(file)) fail(`${name} has no ${METADATA} — its ConfigurationReferenceTest writes it`);
      const reference = checkReference(JSON.parse(readFileSync(file, "utf8")), name);
      return { name, url: sourceUrl(name), modules: reference.modules };
    }),
  };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const data = JSON.stringify(extract(locate()), null, 2) + "\n";
  if (process.argv.includes("--check")) {
    if ((existsSync(OUT) ? readFileSync(OUT, "utf8") : "") !== data) {
      console.error("gen-configuration: lib/generated/configuration.json is missing or stale — run `npm run gen`");
      process.exit(1);
    }
    console.log("gen-configuration: up to date");
  } else {
    mkdirSync(dirname(OUT), { recursive: true });
    writeFileSync(OUT, data);
    const d = JSON.parse(data);
    const count = d.repositories.flatMap((r) => r.modules).flatMap((m) => m.properties).length;
    console.log(`gen-configuration: ${count} properties in ${d.repositories.length} repositories`);
  }
}
