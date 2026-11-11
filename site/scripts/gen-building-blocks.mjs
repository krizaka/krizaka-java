#!/usr/bin/env node
/* The building blocks map of Krizaka Java, read from the POMs — never written by hand.

   Every artifact of the Krizaka Java repositories (the build — parent POM, BOM, test kit — included) with its version and
   the real relations between them:
     compile / runtime / provided / test   a <dependency> on another Krizaka artifact (its scope)
     parent                                the module inherits that POM (through its repository's reactor)
     import                                the module resolves its versions from that BOM (a <scope>import</scope>)
   Reactor-only POMs (a repository's root, starters/) are not artifacts anyone uses: a module that inherits one is drawn
   inheriting the first real parent above it, and what the reactor imports (the BOM) is imported by its modules.

   Sources: this repository (krizaka-java, one level above site/): its own modules, then the folders of the service kit,
   users and billing (ADR-021, lib/sources.json). Nothing is cloned.
   Writes the git-ignored lib/generated/building-blocks.json. `--check` fails when the file is missing or stale.
   Usage: node scripts/gen-building-blocks.mjs [--check] */

import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { xmlText } from "./xml-text.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const SITE = join(here, "..");
const OUT = join(SITE, "lib", "generated", "building-blocks.json");

const sources = JSON.parse(readFileSync(new URL("../lib/sources.json", import.meta.url), "utf8"));
/** The folders of this repository that were repositories of their own (ADR-021). */
export const FOLDERS = sources.folders;
/** The sources of Krizaka Java, in the order the map reads them: the build's own modules, then each folder. */
export const REPOSITORIES = [sources.repository, ...FOLDERS];
/** Where a source lives on GitHub: this repository, or one of its folders. */
export const sourceUrl = (name) => `https://github.com/krizaka/${sources.repository}${FOLDERS.includes(name) ? `/tree/main/${name}` : ""}`;

export const ROLES = [
  { id: "service", rule: "a module with a main (SpringApplication.run): a deployable Krizaka service" },
  { id: "adapter", rule: "a `*-client` or `*-persistence` module: typed HTTP client, JPA adapter" },
  { id: "domain", rule: "a `*-core` module" },
  { id: "contract", rule: "a `*-api` module — interfaces, records, events" },
  { id: "starter", rule: "a module of krizaka-service-kit/starters — one dependency that switches a module on" },
  { id: "platform", rule: "a library module of krizaka-service-kit" },
  { id: "build", rule: "a module of krizaka-java: the parent POM, the BOM, the test kit" },
];

const read = (p) => (existsSync(p) ? readFileSync(p, "utf8") : "");
/** A pom without its <parent>, <build>, <dependencyManagement> and profiles: what the module itself declares. */
const ownPom = (pom) =>
  pom
    .replace(/<parent>[\s\S]*?<\/parent>/, "")
    .replace(/<build>[\s\S]*?<\/build>/g, "")
    .replace(/<dependencyManagement>[\s\S]*?<\/dependencyManagement>/g, "")
    .replace(/<profiles>[\s\S]*?<\/profiles>/g, "");

function walk(dir, pred) {
  if (!existsSync(dir)) return [];
  const out = [];
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (["node_modules", "target", ".git"].includes(name)) continue;
    if (statSync(p).isDirectory()) out.push(...walk(p, pred));
    else if (pred(p)) out.push(p);
  }
  return out;
}

/** Where each source is: this checkout's root for the build's own modules, then one folder each. Nothing is cloned. */
export function locate() {
  const root = join(SITE, "..");
  return new Map(REPOSITORIES.map((name) => [name, FOLDERS.includes(name) ? join(root, name) : root]));
}

export function extract(dirs) {
  const poms = [];
  const visit = (dir, repo, root) => {
    const pom = read(join(dir, "pom.xml"));
    if (!pom) return;
    const own = ownPom(pom);
    const id = own.match(/<artifactId>([\w.-]+)<\/artifactId>/)?.[1];
    const parent = pom.match(/<parent>[\s\S]*?<artifactId>([\w.-]+)<\/artifactId>[\s\S]*?<\/parent>/)?.[1] ?? null;
    const parentVersion = pom.match(/<parent>[\s\S]*?<version>([^<]+)<\/version>[\s\S]*?<\/parent>/)?.[1] ?? null;
    const head = own.replace(/<(dependencies|properties|repositories|pluginRepositories|reporting|modules)>[\s\S]*?<\/\1>/g, "");
    let version = head.match(/<version>([^<]+)<\/version>/)?.[1] ?? parentVersion;
    if (version?.startsWith("${")) version = parentVersion;
    const children = [...pom.matchAll(/<module>([^<]+)<\/module>/g)].map((m) => m[1].trim());
    const imports = [...(pom.match(/<dependencyManagement>([\s\S]*?)<\/dependencyManagement>/)?.[1] ?? "").matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)]
      .filter((m) => /<scope>\s*import\s*<\/scope>/.test(m[1]))
      .map((m) => m[1].match(/<artifactId>([^<]+)<\/artifactId>/)?.[1]?.trim());
    const deps = [...(own.match(/<dependencies>([\s\S]*?)<\/dependencies>/)?.[1] ?? "").matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)].map((m) => ({
      artifactId: m[1].match(/<artifactId>([^<]+)<\/artifactId>/)?.[1]?.trim(),
      scope: m[1].match(/<scope>([^<]+)<\/scope>/)?.[1]?.trim() ?? "compile",
      optional: /<optional>\s*true\s*<\/optional>/.test(m[1]),
    }));
    poms.push({
      id, dir, repo, parent, version, imports, deps,
      path: relative(root, dir).replaceAll("\\", "/") || ".",
      // A reactor POM lists modules; the parent POM and the BOM list none and are published artifacts.
      reactor: /<packaging>pom<\/packaging>/.test(pom) && children.length > 0,
      packaging: pom.match(/<packaging>(\w+)<\/packaging>/)?.[1] ?? "jar",
      description: xmlText(own.match(/<description>([\s\S]*?)<\/description>/)?.[1] ?? "") || null,
    });
    // The root reactor lists the folders as modules: they are read once, as their own source.
    for (const child of children) if (!(repo === REPOSITORIES[0] && root === dir && FOLDERS.includes(child))) visit(join(dir, child), repo, root);
  };
  // krizaka-java's own reactor inherits krizaka-parent by relativePath: its modules are visited through <modules>.
  for (const repo of REPOSITORIES) visit(dirs.get(repo), repo, dirs.get(repo));

  const byId = new Map(poms.filter((p) => p.id).map((p) => [p.id, p]));
  const artifacts = poms.filter((p) => p.id && !p.reactor && !p.path.split("/").includes("examples"));
  const known = new Set(artifacts.map((a) => a.id));
  if (known.size < 10) throw new Error(`gen-building-blocks: only ${known.size} Krizaka artifacts found — check the repositories`);

  const roleOf = (a) => {
    if (a.repo === "krizaka-java") return "build";
    // krizaka-notifications-api / -service live in the kit since 2026-10-30: they keep the role of a domain block, not "platform".
    if (a.repo === "krizaka-service-kit" && !a.id.startsWith("krizaka-notifications-")) return a.path.split("/").includes("starters") ? "starter" : "platform";
    if (walk(join(a.dir, "src/main/java"), (p) => p.endsWith(".java")).some((f) => /SpringApplication\.run\(/.test(read(f)))) return "service";
    if (a.id.endsWith("-api")) return "contract";
    if (a.id.endsWith("-core")) return "domain";
    return "adapter";
  };
  /** The first real artifact up the parent chain, and every BOM the chain imports. */
  const lineage = (a) => {
    let parent = null;
    const imports = new Set(a.imports);
    for (let p = byId.get(a.parent), guard = 0; p && guard < 10; p = byId.get(p.parent), guard++) {
      for (const i of p.imports) imports.add(i);
      if (!parent && !p.reactor) parent = p.id;
    }
    return { parent: parent ?? (known.has(a.parent) ? a.parent : null), imports: [...imports].filter((i) => known.has(i)) };
  };

  const order = new Map(ROLES.map((r, i) => [r.id, i]));
  const modules = artifacts
    .map((a) => ({ id: a.id, path: a.path === "." ? a.repo : `${a.repo}/${a.path}`, repository: a.repo, role: roleOf(a), packaging: a.packaging, version: a.version, description: a.description }))
    .sort((x, y) => order.get(x.role) - order.get(y.role) || REPOSITORIES.indexOf(x.repository) - REPOSITORIES.indexOf(y.repository) || x.id.localeCompare(y.id));

  const dependencies = [];
  const add = (d) => {
    if (d.from !== d.to && !dependencies.some((x) => x.from === d.from && x.to === d.to && x.kind === d.kind)) dependencies.push(d);
  };
  for (const a of artifacts) {
    const { parent, imports } = lineage(a);
    if (parent) add({ from: a.id, to: parent, kind: "parent" });
    for (const bom of imports) add({ from: a.id, to: bom, kind: "import" });
    for (const d of a.deps) if (known.has(d.artifactId)) add({ from: a.id, to: d.artifactId, kind: d.scope, ...(d.optional ? { optional: true } : {}) });
  }
  dependencies.sort((x, y) => x.from.localeCompare(y.from) || x.to.localeCompare(y.to) || x.kind.localeCompare(y.kind));
  return {
    repositories: REPOSITORIES.map((name) => ({ name, url: sourceUrl(name) })),
    roles: ROLES,
    modules,
    dependencies,
  };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const data = JSON.stringify(extract(locate()), null, 2) + "\n";
  if (process.argv.includes("--check")) {
    if (read(OUT) !== data) {
      console.error("gen-building-blocks: lib/generated/building-blocks.json is missing or stale — run `npm run gen`");
      process.exit(1);
    }
    console.log("gen-building-blocks: up to date");
  } else {
    mkdirSync(dirname(OUT), { recursive: true });
    writeFileSync(OUT, data);
    const d = JSON.parse(data);
    console.log(`gen-building-blocks: ${d.modules.length} artifacts, ${d.dependencies.length} relations`);
  }
}
