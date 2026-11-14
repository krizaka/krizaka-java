/* The browser loop the site's Playwright checks share: every page at every width in every theme, on the built site.
   `inspect(page, { path, width, theme })` returns the problems it found (strings); Playwright is resolved from --playwright
   or the package. Returns the problems, with the page, width and theme in front of each. */

import { mkdirSync } from "node:fs";
import { createRequire } from "node:module";

export const flagOf = (args, name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);

export async function inspectViews({ args, pages, widths, themes, inspect }) {
  const base = flagOf(args, "--base", "http://localhost:3000").replace(/\/$/, "");
  const { chromium } = createRequire(import.meta.url)(flagOf(args, "--playwright", "playwright"));
  const browser = await chromium.launch();
  const problems = [];
  for (const theme of themes) {
    for (const width of widths) {
      const context = await browser.newContext({ viewport: { width, height: 900 }, reducedMotion: "reduce", colorScheme: theme });
      await context.addInitScript((t) => localStorage.setItem("kz-theme", t), theme);
      const page = await context.newPage();
      for (const path of pages) {
        await page.goto(`${base}${path}`, { waitUntil: "networkidle" });
        await page.evaluate(() => document.fonts.ready);
        for (const issue of await inspect(page, { path, width, theme })) problems.push(`${path} @${width} ${theme}: ${issue}`);
      }
      await context.close();
    }
  }
  await browser.close();
  return problems;
}

/** Runs a check from the command line: `--base`, `--playwright`, `--shots <dir>` (given to `inspect` as `shots`, created). */
export async function runCheck({ name, success, pages, widths, themes, inspect }) {
  const args = process.argv.slice(2);
  const shots = flagOf(args, "--shots", null);
  if (shots) mkdirSync(shots, { recursive: true });
  const problems = await inspectViews({ args, pages, widths, themes, inspect: (page, view) => inspect(page, { ...view, shots }) });
  if (problems.length) {
    console.error(problems.map((p) => `${name}: ${p}`).join("\n"));
    process.exit(1);
  }
  console.log(`${name}: ${success}`);
}
