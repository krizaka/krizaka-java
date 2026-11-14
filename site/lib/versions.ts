/* The versions read from the registry at build (scripts/gen-registry-versions.mjs → lib/generated/maven-versions.json). */

import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

import type { PublishedBom } from "./project";

const FILE = path.join(process.cwd(), "lib", "generated", "maven-versions.json");

interface Generated {
  maven: Record<string, string>;
  bom?: PublishedBom;
}

let cache: Generated | null = null;

function generated(): Generated {
  if (!cache) {
    if (!existsSync(FILE)) throw new Error("lib/generated/maven-versions.json is missing — run `npm run gen`");
    cache = JSON.parse(readFileSync(FILE, "utf8")) as Generated;
  }
  return cache;
}

/** Every `com.krizaka` artifact on the registry with its latest release. */
export const mavenVersions = (): Record<string, string> => generated().maven;

/** The BOM published in the registry: what it manages, at which version, and whether Central has that version. */
export function publishedBom(): PublishedBom {
  const bom = generated().bom;
  if (!bom) throw new Error("lib/generated/maven-versions.json has no `bom` — run `npm run gen` (the generator offers only what the published BOM manages)");
  return bom;
}

/** The release of one artifact in the registry; `null` when it was never published. */
export const mavenVersion = (artifactId: string): string | null => mavenVersions()[artifactId] ?? null;
