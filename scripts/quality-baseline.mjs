#!/usr/bin/env node
// Measures and shrinks the per-module PMD / SpotBugs baselines (ADR-013, krizaka-java#105). Dependency-free.
//
//   node quality-baseline.mjs init   <repository-root>...   measure every module of the repository as it is and write the two files
//   node quality-baseline.mjs shrink <repository-root>...   re-measure; refuse any violation the baseline lacks, drop the fixed ones
//   node quality-baseline.mjs check  <repository-root>...   same refusal, writes nothing
//
// A baseline is never typed: it is read from the reports (target/pmd.xml, target/spotbugsXml.xml) of a build
// that excludes nothing. Files: pmd-baseline.properties (`pkg.Class=Rule,Rule`, the format of maven-pmd-plugin's
// excludeFromFailureFile) and spotbugs-exclude.xml (a <Match> per class and bug pattern).
import { execFileSync } from "node:child_process";
import { existsSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";

/** Code-unit order, the default of `sort()` made explicit: the baseline files are byte-stable. */
const compareText = (a, b) => {
  if (a < b) return -1;
  return a > b ? 1 : 0;
};

export function pmdViolations(xml) {
  const out = new Set();
  for (const f of xml.matchAll(/<file\b[^>]*>([\s\S]*?)<\/file>/g)) {
    for (const v of f[1].matchAll(/<violation\b([^>]*)>/g)) {
      const a = v[1];
      const cls = /\bclass="([^"]*)"/.exec(a)?.[1];
      const pkg = /\bpackage="([^"]*)"/.exec(a)?.[1];
      const rule = /\brule="([^"]*)"/.exec(a)?.[1];
      if (cls && rule) {
        const prefix = pkg ? `${pkg}.` : "";
        out.add(`${prefix}${cls}=${rule}`);
      }
    }
  }
  return out;
}

export function spotbugsViolations(xml) {
  const out = new Set();
  for (const b of xml.matchAll(/<BugInstance\b([^>]*)>([\s\S]*?)<\/BugInstance>/g)) {
    const type = /\btype=["']([^"']*)["']/.exec(b[1])?.[1];
    const cls = /<Class\b[^>]*\bclassname=["']([^"']*)["']/.exec(b[2])?.[1];
    if (type && cls) out.add(`${cls}=${type}`);
  }
  return out;
}

export function parsePmdBaseline(text) {
  const out = new Set();
  for (const line of text.split("\n")) {
    const m = /^\s*([^#=\s][^=]*)=(.*)$/.exec(line);
    if (m) for (const r of m[2].split(",")) if (r.trim()) out.add(`${m[1].trim()}=${r.trim()}`);
  }
  return out;
}

export function parseSpotbugsBaseline(xml) {
  const out = new Set();
  for (const m of xml.matchAll(/<Match>\s*<Class name="([^"]*)"\s*\/>\s*<Bug pattern="([^"]*)"\s*\/>\s*<\/Match>/g)) out.add(`${m[1]}=${m[2]}`);
  return out;
}

const byClass = (set) => {
  const m = new Map();
  for (const e of [...set].sort(compareText)) {
    const i = e.lastIndexOf("=");
    const k = e.slice(0, i);
    m.set(k, [...(m.get(k) ?? []), e.slice(i + 1)]);
  }
  return m;
};

export function renderPmd(set) {
  const head = "# PMD baseline (krizaka-java#105): measured by scripts/quality-baseline.mjs, never typed. It only shrinks.\n";
  return head + [...byClass(set)].map(([c, r]) => `${c}=${r.join(",")}`).join("\n") + (set.size ? "\n" : "");
}

export function renderSpotbugs(set) {
  const head = '<?xml version="1.0" encoding="UTF-8"?>\n<!-- SpotBugs baseline (krizaka-java#105): measured by scripts/quality-baseline.mjs, never typed. It only shrinks. -->\n<FindBugsFilter>\n';
  const body = [...set].sort(compareText).map((e) => {
    const i = e.lastIndexOf("=");
    return `    <Match>\n        <Class name="${e.slice(0, i)}"/>\n        <Bug pattern="${e.slice(i + 1)}"/>\n    </Match>\n`;
  });
  return `${head}${body.join("")}</FindBugsFilter>\n`;
}

/** Returns { added, removed, next } of a fresh measure against a baseline. */
export function compare(baseline, measured) {
  const added = [...measured].filter((e) => !baseline.has(e));
  const removed = [...baseline].filter((e) => !measured.has(e));
  return { added, removed, next: new Set([...measured].filter((e) => baseline.has(e))) };
}

/** Runs one build of the repository (all its modules) that excludes nothing and keeps going on findings. */
function build(root) {
  const mvn = existsSync(join(root, "mvnw")) ? "./mvnw" : "mvn";
  execFileSync(
    mvn,
    ["-q", "verify", "-Dkrizaka.quality.phase=verify", "-DskipTests", "-DskipITs", "-Dpmd.failOnViolation=false", "-Dkrizaka.spotbugs.failOnError=false",
      "-Dkrizaka.pmd.baseline=/dev/null", "-Dkrizaka.spotbugs.exclude=krizaka-quality/spotbugs-exclude-empty.xml"],
    { cwd: root, stdio: "inherit" },
  );
}

/** The modules of a repository that have main sources: the root itself and every folder below it, however nested (starters/, examples/). */
export function modulesOf(root) {
  const has = (d) => existsSync(join(d, "pom.xml")) && existsSync(join(d, "src/main/java"));
  const skip = new Set(["src", "target", "node_modules"]);
  const found = [];
  const walk = (dir) => {
    if (has(dir)) found.push(dir);
    for (const e of readdirSync(dir, { withFileTypes: true })) {
      if (e.isDirectory() && !e.name.startsWith(".") && !skip.has(e.name)) walk(join(dir, e.name));
    }
  };
  walk(root);
  return found;
}

function measure(dir) {
  const read = (f) => (existsSync(join(dir, f)) ? readFileSync(join(dir, f), "utf8") : "");
  return { pmd: pmdViolations(read("target/pmd.xml")), spotbugs: spotbugsViolations(read("target/spotbugsXml.xml")) };
}

function main([mode, ...dirs]) {
  if (!["init", "shrink", "check"].includes(mode) || !dirs.length) {
    console.error("usage: quality-baseline.mjs init|shrink|check <repository-root>...");
    return 2;
  }
  let status = 0;
  const roots = dirs.map((x) => resolve(x));
  for (const r of roots) build(r);
  for (const d of roots.flatMap(modulesOf)) {
    const m = measure(d);
    const pf = join(d, "pmd-baseline.properties");
    const sf = join(d, "spotbugs-exclude.xml");
    if (mode === "init") {
      writeFileSync(pf, renderPmd(m.pmd));
      writeFileSync(sf, renderSpotbugs(m.spotbugs));
      console.log(`${d}: baseline PMD ${m.pmd.size}, SpotBugs ${m.spotbugs.size}`);
      continue;
    }
    const pb = existsSync(pf) ? parsePmdBaseline(readFileSync(pf, "utf8")) : new Set();
    const sb = existsSync(sf) ? parseSpotbugsBaseline(readFileSync(sf, "utf8")) : new Set();
    const p = compare(pb, m.pmd);
    const s = compare(sb, m.spotbugs);
    for (const e of [...p.added, ...s.added]) { console.error(`NEW (not in the baseline): ${e}`); status = 1; }
    if (status === 0 && mode === "shrink") {
      writeFileSync(pf, renderPmd(p.next));
      writeFileSync(sf, renderSpotbugs(s.next));
    }
    console.log(`${d}: PMD ${pb.size} -> ${p.next.size}, SpotBugs ${sb.size} -> ${s.next.size}`);
  }
  return status;
}

if (import.meta.url === `file://${process.argv[1]}`) process.exit(main(process.argv.slice(2)));
