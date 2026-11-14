/* Which proof the home tells. The data decides, never a hand switch: with no Sovkora service built on Krizaka Java the
   section claims nothing about services (no « 0 », no empty list) and keeps the test figures, which are true. */

export type ProofState = { kind: "services"; showServices: true } | { kind: "tests"; showServices: false };

export function proofState(services: readonly unknown[]): ProofState {
  return services.length > 0 ? { kind: "services", showServices: true } : { kind: "tests", showServices: false };
}
