import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

import { normalizeBody, releasesFromGithub } from "../lib/changelog.ts";

// /changelog (EN/FR): the releases of Krizaka Java (release-please) on the Changelog template, a feed, the footer's version link.
const read = (file) => readFileSync(new URL(`../${file}`, import.meta.url), "utf8");
const messages = (lang) => JSON.parse(read(`messages/${lang}.json`));

const BODY = `## [0.2.0](https://github.com/krizaka/krizaka-java/compare/v0.1.0...v0.2.0) (2026-10-09)


### Features

* **users:** add API keys ([#2](https://github.com/krizaka/krizaka-java/pull/2)) ([7dd3f1a](https://github.com/krizaka/krizaka-java/commit/7dd3f1a))
* BOM with the starters ([#3](https://github.com/krizaka/krizaka-java/pull/3)) ([0aa1b2c](https://github.com/krizaka/krizaka-java/commit/0aa1b2c))

### Bug Fixes

* **service-kit:** XSS in the error page ([#4](https://github.com/krizaka/krizaka-java/pull/4)) ([1bb2c3d](https://github.com/krizaka/krizaka-java/commit/1bb2c3d))

### Miscellaneous Chores

* bump the parent ([#5](https://github.com/krizaka/krizaka-java/pull/5)) ([2cc3d4e](https://github.com/krizaka/krizaka-java/commit/2cc3d4e))
`;

test("a release-please body keeps its news sections, names the module and links the PR", () => {
  assert.equal(normalizeBody(BODY), [
    "* feat: users — add API keys https://github.com/krizaka/krizaka-java/pull/2",
    "* feat: BOM with the starters https://github.com/krizaka/krizaka-java/pull/3",
    "* fix: service-kit — XSS in the error page https://github.com/krizaka/krizaka-java/pull/4",
  ].join("\n"));
});

test("the releases are tagged, dated, newest first; drafts and empty releases are left out", () => {
  const releases = releasesFromGithub([
    { tag_name: "v0.1.0", draft: false, published_at: "2026-09-01T10:00:00Z", html_url: "u1", body: "### Features\n\n* first ([#1](https://github.com/krizaka/krizaka-java/pull/1))" },
    { tag_name: "v0.2.0", draft: false, published_at: "2026-10-09T10:00:00Z", html_url: "u2", body: BODY },
    { tag_name: "v0.3.0", draft: true, published_at: null, html_url: "u3", body: BODY },
    { tag_name: "v0.0.1", draft: false, published_at: "2026-08-01T10:00:00Z", html_url: "u0", body: "### Miscellaneous Chores\n\n* nothing" },
  ]);
  assert.deepEqual(releases.map((r) => r.version), ["0.2.0", "0.1.0"]);
  assert.deepEqual(releases[0].changes.map((c) => [c.tag, c.pr?.number]), [["feature", 2], ["feature", 3], ["security", 4]]);
});

test("both languages have the page, the feed and the sitemap entry", () => {
  for (const dir of ["app/(en)/(docs)/changelog", "app/fr/(docs)/changelog"]) {
    assert.ok(existsSync(new URL(`../${dir}/page.tsx`, import.meta.url)), `${dir}/page.tsx`);
    assert.match(read(`${dir}/page.tsx`), /<ChangelogView/);
    assert.match(read(`${dir}/feed.xml/route.ts`), /changelogFeed/);
  }
  assert.match(read("app/components/ChangelogView.tsx"), /await loadReleases\(\)/);
  assert.match(read("app/sitemap.ts"), /"\/changelog"/);
  for (const lang of ["en", "fr"]) assert.ok(messages(lang).changelog.title && messages(lang).changelog.kinds.breaking, lang);
});

test("the footer's version (the BOM) links /changelog#<version>; the page owns that anchor", () => {
  assert.match(read("app/components/FamilyFooterView.tsx"), /`\$\{localizedPath\(locale, "\/changelog"\)\}#\$\{version\}`/);
  assert.match(read("app/components/SiteFooter.tsx"), /ui=\{uiVersion\(\)\}/);
  assert.match(read("app/components/ChangelogView.tsx"), /version=\{mavenVersion\("krizaka-bom"\)/);
  assert.match(read("app/components/ChangelogBody.tsx"), /<span id=\{version\}>/);
});

test("a release's version is shown as written (vX.Y.Z), never parsed as a week's date", () => {
  const body = read("app/components/ChangelogBody.tsx");
  assert.match(body, /`v\$\{r\.version\}`/);
  assert.doesNotMatch(body, /when\(r\.version\)/);
});
