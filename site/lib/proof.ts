/* The proof the home states, read at build from the code (scripts/gen-proof.mjs → lib/generated/proof.json): the platform
   (Java, Spring Boot — from the parent POM), the tests and how many start a real PostgreSQL or RabbitMQ, and the Sovkora
   services that depend on a Krizaka Java artifact. Never typed in a page or a message. */

import data from "./generated/proof.json";

export interface Proof {
  platform: { java: string; springBoot: string; springBootMinor: string };
  tests: { total: number; realInfrastructure: number };
  /** The Sovkora services built on Krizaka Java, with the artifacts each one uses. */
  services: { id: string; artifacts: string[] }[];
}

export function proof(): Proof {
  if (!data.tests.total) throw new Error("proof: lib/generated/proof.json is empty — run `npm run gen`");
  return { platform: data.platform, tests: { total: data.tests.total, realInfrastructure: data.tests.realInfrastructure }, services: data.sovkora.services };
}

/** 2164 → 2 100: a count told as "more than", rounded down to the hundred (a figure that is never above the truth). */
export const roundedDown = (n: number, step = 100) => Math.floor(n / step) * step;
