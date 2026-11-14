import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { proofState } from "../lib/proof-state.ts";

/* The proof section has two states, decided by the data (never by hand): with no Sovkora service it claims nothing about
   services — no « 0 », no empty list — and says what is true: the figures are read from the tests. */
const read = (p) => readFileSync(new URL(`../${p}`, import.meta.url), "utf8");
const en = JSON.parse(read("messages/en.json"));
const fr = JSON.parse(read("messages/fr.json"));

test("no service: the proof shows the tests only, with its own title and lead", () => {
  assert.deepEqual(proofState([]), { kind: "tests", showServices: false });
});

test("one service or more: the proof keeps the services figure and the list", () => {
  assert.deepEqual(proofState([{ id: "sovkora-edge", artifacts: ["krizaka-web"] }]), { kind: "services", showServices: true });
});

test("the tests-only state has its words in both languages", () => {
  assert.equal(en.home.proof.testsOnly.title, "Proven on real infrastructure");
  assert.equal(fr.home.proof.testsOnly.title, "Éprouvé sur une vraie infrastructure");
  assert.equal(en.home.proof.testsOnly.lead, "The figures are read from the Krizaka Java repositories' tests at every build.");
  assert.equal(fr.home.proof.testsOnly.lead, "Les chiffres sont lus dans les tests des dépôts de Krizaka Java à chaque build.");
});

test("the home picks the state from the data, never from a flag", () => {
  const home = read("app/components/Home.tsx");
  assert.match(home, /proofState\(services\)/);
  assert.equal(home.match(/state\.showServices &&/g)?.length, 2, "the services figure and the list are both gated by the state");
});

test("French says « de Sovkora », never the elision of the old name", () => {
  assert.doesNotMatch(JSON.stringify(fr), /d['’]Sovkora/);
  assert.match(fr.home.proof.lead, /services de Sovkora/);
  assert.match(fr.home.proof.services, /^services de Sovkora/);
});

test("the theme follows the system by default, on the site and on its not-found page", () => {
  for (const file of ["app/components/SiteRoot.tsx", "app/global-not-found.tsx"]) {
    const src = read(file);
    assert.match(src, /<ThemeScript defaultMode="system"/, file);
    assert.match(src, /<ThemeProvider defaultMode="system"/, file);
    assert.doesNotMatch(src, /defaultMode="dark"/, file);
  }
});

test("each NumberTicker figure is read once: the animated copies are aria-hidden, one sr-only text carries the value", () => {
  const lib = read("node_modules/@krizaka/ui/dist/number-ticker.js");
  assert.equal(lib.match(/"aria-hidden": true/g)?.length, 3, "ghost, rolling digits and live copy are hidden from assistive technology");
  assert.equal(lib.match(/className: "sr-only"/g)?.length, 1, "one text for the screen reader");
});
