/* The roles of the map and the shape of what it draws — no I/O, so the client component can import it. */

export const ROLES = ["service", "adapter", "domain", "contract", "starter", "platform", "build"] as const;
export type RoleId = (typeof ROLES)[number];

export interface MapModule {
  id: string;
  role: RoleId;
  repository: string;
  repositoryUrl: string;
  path: string;
  packaging: string;
  /** The version the POM declares. */
  version: string;
  description: string | null;
}
export interface MapDependency {
  from: string;
  to: string;
  /** Why: compile, runtime, provided, test, parent, import. */
  kind: string;
  /** Not a plain dependency: a parent POM, a BOM import, a test scope. */
  indirect: boolean;
}
export interface MapData {
  modules: MapModule[];
  dependencies: MapDependency[];
}
