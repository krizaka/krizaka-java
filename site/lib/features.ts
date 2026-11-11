/* The features matrix (lib/features.json): what Krizaka Java ships, partly ships or plans, each shipped line backed by files
   scripts/check-features.mjs finds on main of their repository. Words in messages/<locale>.json (features.items.<id>). */

import data from "./features.json";
import sources from "./sources.json";

export type FeatureStatus = "shipped" | "partial" | "planned";
export type Feature = { id: string; status: FeatureStatus; page?: string; issue?: string; evidence?: { repository: string; path: string }[] };

export const features = data.features as Feature[];
export const roadmapUrl = data.roadmap;

export function featureCounts(): Record<FeatureStatus, number> {
  const counts = { shipped: 0, partial: 0, planned: 0 };
  for (const f of features) counts[f.status]++;
  return counts;
}

/** The file on GitHub that proves a line: a path of krizaka-java, whose folders were repositories of their own (ADR-021). */
export function evidenceUrl(e: { repository: string; path: string }): string {
  const folder = sources.folders.includes(e.repository) ? `${e.repository}/` : "";
  return `https://github.com/krizaka/${sources.repository}/blob/main/${folder}${e.path}`;
}
