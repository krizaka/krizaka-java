import { test } from "node:test";
import assert from "node:assert/strict";
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";

/* One footer, one contentinfo: FamilyFooter of @krizaka/ui, its content (socials, legal, source, licence) from
   footerFor("java") of @krizaka/org, rendered once by the root after the page. No local footer, no typed URL or social. */
const read = (f) => readFileSync(new URL(`../${f}`, import.meta.url), "utf8");
const walk = (dir) =>
  readdirSync(new URL(`../${dir}/`, import.meta.url)).flatMap((name) => {
    const rel = `${dir}/${name}`;
    return statSync(new URL(`../${rel}`, import.meta.url)).isDirectory() ? walk(rel) : [rel];
  });

test("the root renders FamilyFooter once, from footerFor('java'), after the page", () => {
  const root = read("app/components/SiteRoot.tsx");
  assert.equal(root.match(/<FamilyFooter\b/g)?.length, 1);
  assert.match(root, /footer=\{footerFor\("java"\)\}/);
  assert.ok(root.indexOf("{children}") < root.indexOf("<FamilyFooter"));
  assert.doesNotMatch(root, /<DocsLayout/, "the sidebar belongs to the documentation, not to the home");
});

test("no local footer comes back: no footer component, no other <footer>, no SiteFooter, no typed social", () => {
  assert.equal(existsSync(new URL("../app/components/SiteFooter.tsx", import.meta.url)), false);
  assert.equal(existsSync(new URL("../app/components/FamilyFooterView.tsx", import.meta.url)), false);
  const offenders = walk("app")
    .filter((f) => /\.tsx?$/.test(f) && !/SiteRoot\.tsx$/.test(f))
    .filter((f) => /<FamilyFooter\b|<SiteFooter\b|<footer\b|@krizaka\/ui\/site-footer|instagram\.com|linkedin\.com|youtube\.com|facebook\.com|central\.sonatype|search\.maven\.org/.test(read(f)));
  assert.deepEqual(offenders, []);
});
