/* The home's families of building blocks — structure only (no I/O, so a test can read it): which artifacts, which guide, which
   bird. The words are messages (home.families.<id>). lib/home.ts checks them against the POMs' model. */

export const FAMILY_IDS = ["web", "security", "messaging", "observability", "appConfig", "testing"] as const;
export type FamilyId = (typeof FAMILY_IDS)[number];

/** A family: the library module, the starter that switches it on (none for the test kit), its guide, the bird whose promise it is (if one is). */
export const FAMILIES: Record<FamilyId, { module: string; starter: string | null; guide: string; bird: "falcon" | "pigeon" | null }> = {
  web: { module: "krizaka-web", starter: "krizaka-spring-boot-starter-web", guide: "/web", bird: null },
  security: { module: "krizaka-security", starter: "krizaka-spring-boot-starter-security", guide: "/security", bird: "falcon" },
  messaging: { module: "krizaka-messaging", starter: "krizaka-spring-boot-starter-rabbitmq", guide: "/messaging", bird: "pigeon" },
  observability: { module: "krizaka-observability", starter: "krizaka-spring-boot-starter-observability", guide: "/observability", bird: null },
  appConfig: { module: "krizaka-app-config", starter: "krizaka-spring-boot-starter-app-config", guide: "/app-config", bird: null },
  testing: { module: "krizaka-test-support", starter: null, guide: "/build", bird: null },
};
