import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { familyLabels, siteById } from "@krizaka/ui/sites";

const read = (f) => readFileSync(new URL(`../${f}`, import.meta.url), "utf8");

test("the bar has no family switcher and no hand-made links to other sites; the footer is java's", () => {
  const bar = read("app/components/TopBar.tsx");
  assert.doesNotMatch(bar, /NavBar\.Family/, "no family switcher: krizaka.com is the only way back (krizaka-ui#485)");
  assert.match(bar, /<NavBar\.Badge version=/);
  assert.match(bar, /<NavBar\.Repo /);
  assert.match(bar, /<NavBar\.Language /, "the family's language switch (@krizaka/ui 3.26), not a hand-made button");
  assert.match(bar, /<NavBar\.MobilePreferences /);
  assert.doesNotMatch(bar, /KRIZAKA_URL|UI_URL|OPEN_SOURCE_URL|GITHUB_ORG_URL/);
  assert.match(read("app/components/FamilyFooterView.tsx"), /current="java"/);
});

test("the family data knows this site and both languages", () => {
  assert.equal(siteById("java").url, "https://java.krizaka.com");
  // The repository was krizaka-build until 2026-11-06 (GitHub redirects it); @krizaka/org names krizaka-java from its 1.0.
  assert.match(siteById("java").repo, /\/krizaka-(java|build)$/);
  assert.ok(familyLabels.en.family && familyLabels.fr.family);
});
