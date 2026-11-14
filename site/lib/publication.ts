/* Where the build publishes, read from the build itself — never typed here. The repository's CI (.github/workflows/ci.yml)
   passes `registry:` to the organisation's Maven pipeline: `none` (nothing is deployed), `central`, or a repository once it
   exists (krizaka-internal#3345). Founder, 2026-11-14: nothing is published to Maven Central; a public release is announced. */

import { readFileSync } from "node:fs";
import path from "node:path";

export type PublicationTarget = "none" | "central" | "private";

const CI = path.join(process.cwd(), "..", ".github", "workflows", "ci.yml");

/** The value of the `registry:` line of a CI workflow; `none` when the line is missing. */
export function registryOf(ciText: string): string {
  const line = ciText.split("\n").map((l) => l.trim()).find((l) => l.startsWith("registry:"));
  return line?.slice("registry:".length).split("#")[0].replaceAll(/["']/g, "").trim() || "none";
}

/** What the site may say about where the artifacts are. */
export function publicationTarget(ciText: string = readFileSync(CI, "utf8")): PublicationTarget {
  const registry = registryOf(ciText);
  return registry === "central" ? "central" : registry === "none" ? "none" : "private";
}

