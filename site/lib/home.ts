/* The home's structure — which artifacts each family of building blocks is, and where its guide is. The words are
   messages (home.families.<id>); the artifacts are checked against the model generated from the POMs, so a family that
   names an artifact the POMs do not have fails the build. Versions are the repository's, never typed. */

import { FAMILIES, FAMILY_IDS, type FamilyId } from "./families";
import { buildingBlocksMap, repositoryVersion } from "./model";

export interface Family {
  id: FamilyId;
  module: string;
  starter: string | null;
  guide: string;
  bird: "falcon" | "pigeon" | null;
  /** The version the repository declares for the module. */
  version: string;
}

/** The families with the version of the repository, each artifact checked against the POMs' model. */
export function families(): Family[] {
  const known = new Set(buildingBlocksMap().modules.map((m) => m.id));
  return FAMILY_IDS.map((id) => {
    const f = FAMILIES[id];
    for (const artifact of [f.module, f.starter]) if (artifact && !known.has(artifact)) throw new Error(`home: the family ${id} names ${artifact}, which is not an artifact of the POMs`);
    return { id, ...f, version: repositoryVersion(f.module) };
  });
}

/** The modules a starter switches on, read from the model: the starters with the library module each pulls (compile). */
export function starterModules(): { starter: string; module: string }[] {
  const { modules, dependencies } = buildingBlocksMap();
  return modules
    .filter((m) => m.role === "starter")
    .map((s) => ({ starter: s.id, module: dependencies.find((d) => d.from === s.id && d.kind === "compile" && modules.find((m) => m.id === d.to)?.role === "platform")?.to ?? "" }))
    .filter((x) => x.module);
}

/** The figures of the hero: the library modules and starters, and every artifact the POMs declare. */
export function counts() {
  const { modules } = buildingBlocksMap();
  const count = (role: string) => modules.filter((m) => m.role === role).length;
  return { modules: count("platform"), starters: count("starter"), artifacts: modules.length };
}
