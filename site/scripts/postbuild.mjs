#!/usr/bin/env node
/* After `next build`: llms.txt and llms-full.txt (the documentation as plain text for assistants), written next to the pages
   in out/. Both are rendered from content/docs (English), with the versions Maven Central lists. */
import { mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const out = join(root, "out");
const SITE = "https://java.krizaka.com";
const versions = JSON.parse(readFileSync(join(root, "lib/generated/maven-versions.json"), "utf8")).maven;

const frontmatter = (src) => {
  const m = src.match(/^---\n([\s\S]*?)\n---\n/);
  const field = (k) => m?.[1].match(new RegExp(`^${k}:\\s*(.+)$`, "m"))?.[1].trim().replace(/^["']|["']$/g, "") ?? "";
  return { title: field("title"), description: field("description"), body: m ? src.slice(m[0].length) : src };
};
const plain = (body) =>
  body
    .replace(/%(krizaka-[a-z0-9-]+)%/g, (_, id) => versions[id] ?? "")
    .replace(/^<(BuildingBlocksMap|PendingRelease|ComingSoon)\b[^>]*\/>\s*$/gm, "")
    .replace(/\n{3,}/g, "\n\n")
    .trim();

const dir = join(root, "content/docs");
const order = JSON.parse(readFileSync(join(dir, "meta.json"), "utf8")).pages.filter((p) => !p.startsWith("---"));
const pages = order.map((slug) => {
  const { title, description, body } = frontmatter(readFileSync(join(dir, `${slug}.mdx`), "utf8"));
  return { slug, title, description, url: `${SITE}/${slug}/`, text: plain(body) };
});
const known = new Set(readdirSync(dir).filter((f) => /^[a-z-]+\.mdx$/.test(f)).map((f) => f.replace(".mdx", "")));
const missing = [...known].filter((s) => !order.includes(s));
if (missing.length) throw new Error(`postbuild: content/docs/meta.json does not list ${missing.join(", ")}`);

const head = `# Krizaka Java\n\n> The Spring Boot building blocks of every Krizaka service — published on Maven Central under com.krizaka (Apache-2.0), Java 21, Spring Boot 4. A Krizaka open-source project (https://www.krizaka.com). French pages are under ${SITE}/fr/.\n`;
/* The way back to the family: the company, the sibling open-source project, the products, the source. */
const family = `## The Krizaka family\n\n- [Krizaka](https://www.krizaka.com): the company, open-source software studio in Montréal\n- [Krizaka UI](https://ui.krizaka.com): the design system (React and React Native)\n- [Sovkora](https://dev.sovkora.com): sovereign, on-premise AI orchestration\n- [Verikora](https://dev.verikora.com): rental verification, it reads leases and screens applications\n- [Folioviv](https://dev.folioviv.com): bookkeeping and tax-return preparation for small businesses\n- [Source](https://github.com/krizaka/krizaka-java): this repository, Apache-2.0\n\nSitemap: ${SITE}/sitemap.xml\n`;
mkdirSync(out, { recursive: true });
writeFileSync(join(out, "llms.txt"), `${head}\n## Guides\n\n${pages.map((p) => `- [${p.title}](${p.url}): ${p.description}`).join("\n")}\n\n${family}`);
writeFileSync(join(out, "llms-full.txt"), `${head}\n${pages.map((p) => `\n---\n\n# ${p.title}\n\nURL: ${p.url}\n\n${p.text}`).join("\n")}\n\n---\n\n${family}`);
console.log(`postbuild: llms.txt, llms-full.txt (${pages.length} pages)`);
