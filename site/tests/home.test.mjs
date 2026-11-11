import { test } from "node:test";
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { FAMILIES, FAMILY_IDS } from "../lib/families.ts";

/* The home (app/(en)/page.tsx, app/fr/page.tsx): what it states is read from the POMs, Maven Central and the repositories —
   never typed — and every word is a message in both languages. */
const read = (p) => readFileSync(new URL(`../${p}`, import.meta.url), "utf8");
const json = (p) => JSON.parse(read(p));
const en = json("messages/en.json");
const fr = json("messages/fr.json");
const model = json("lib/generated/building-blocks.json");
const central = json("lib/generated/maven-versions.json").maven;
const ids = new Set(model.modules.map((m) => m.id));

test("every family names artifacts of the POMs; the ones not on Maven Central are the ones the home must say so for", () => {
  for (const id of FAMILY_IDS) {
    const f = FAMILIES[id];
    for (const artifact of [f.module, f.starter].filter(Boolean)) {
      assert.ok(ids.has(artifact), `${id}: ${artifact} is not an artifact of the POMs`);
    }
  }
  // The home says "Maven Central" only for what is there: the unpublished ones carry no version.
  const unpublished = FAMILY_IDS.filter((i) => !central[FAMILIES[i].module]);
  assert.ok(unpublished.every((i) => FAMILIES[i].module !== "krizaka-web"), "the web module is published");
});

test("a starter's module is the one the family says (the model's compile relation)", () => {
  for (const id of FAMILY_IDS.filter((i) => FAMILIES[i].starter)) {
    const { starter, module } = FAMILIES[id];
    assert.ok(model.dependencies.some((d) => d.from === starter && d.to === module && d.kind === "compile"), `${id}: ${starter} does not pull ${module}`);
  }
});

test("every family has its words in both languages: a hook of 90 characters at most, a guide that exists", () => {
  const guides = new Set(readdirGuides());
  for (const id of FAMILY_IDS) {
    for (const [lang, dict] of [["en", en], ["fr", fr]]) {
      const f = dict.home.families[id];
      assert.ok(f?.title && f?.hook, `${lang}: home.families.${id}`);
      assert.ok(f.hook.length <= 90, `${lang}: the hook of ${id} has ${f.hook.length} characters`);
    }
    assert.ok(guides.has(FAMILIES[id].guide.slice(1)), `${id}: the guide ${FAMILIES[id].guide} does not exist`);
  }
});

test("the birds keep their promise: a family wears one only where the bird's promise is the family's", () => {
  assert.deepEqual(Object.fromEntries(FAMILY_IDS.filter((i) => FAMILIES[i].bird).map((i) => [i, FAMILIES[i].bird])), { security: "falcon", messaging: "pigeon" });
});

test("the duck's promise is the brand's, word for word", () => {
  assert.equal(en.home.quote.text, "The duck builds, line by line, in the open.");
  assert.equal(fr.home.quote.text, "Le canard construit, ligne après ligne, en open source.");
});

test("the proof is generated: the platform from the parent POM, the tests counted, the Sovkora services listed", () => {
  const proof = json("lib/generated/proof.json");
  assert.match(proof.platform.java, /^\d+$/);
  assert.match(proof.platform.springBoot, /^\d+\.\d+\.\d+$/);
  assert.ok(proof.tests.total > 0 && proof.tests.realInfrastructure > 0 && proof.tests.realInfrastructure <= proof.tests.total);
  assert.ok(Array.isArray(proof.sovkora.services));
  for (const s of proof.sovkora.services) for (const a of s.artifacts) assert.ok(ids.has(a), `${s.id} uses ${a}, which is not an artifact of the POMs`);
});

test("no version, count or platform is typed in the home's messages or components", () => {
  const messages = JSON.stringify([en.home, fr.home]);
  assert.doesNotMatch(messages, /\b\d+\.\d+\.\d+\b/, "a version in a message");
  assert.doesNotMatch(messages, /Java 21|Spring Boot 4/, "the platform in a message: it comes from the parent POM");
  const code = ["app/components/Home.tsx", "app/components/ProjectGenerator.tsx", "app/components/StartProject.tsx", "lib/project.ts"].map(read).join("\n");
  assert.doesNotMatch(code, /["'`>]\s*\d+\.\d+\.\d+/, "a version in a component");
});

test("the home is the root, and the former root article is /start", () => {
  assert.ok(existsSync(new URL("../app/(en)/page.tsx", import.meta.url)) && existsSync(new URL("../app/fr/page.tsx", import.meta.url)));
  assert.ok(existsSync(new URL("../content/docs/start.mdx", import.meta.url)) && existsSync(new URL("../content/docs/start.fr.mdx", import.meta.url)));
  assert.ok(!existsSync(new URL("../content/docs/index.mdx", import.meta.url)), "the docs have no index any more");
});

test("the sitemap lists the home and the former root article, in both languages", () => {
  const sitemap = read("app/sitemap.ts");
  assert.match(sitemap, /HOME = "\/"/);
  assert.match(sitemap, /LOCALES/);
});

function readdirGuides() {
  return JSON.parse(read("content/docs/meta.json")).pages.filter((p) => !p.startsWith("---"));
}
