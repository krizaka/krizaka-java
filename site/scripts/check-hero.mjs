#!/usr/bin/env node
/* The home's hero, measured in a real browser (Playwright) on the built site: at 1440 px every fact of the specimen tag is
   on one line, and the h1 starts level with the picker (within 40 px). With --shots <dir>, a screenshot of the first screen
   is saved per theme and width (1440 and 360).

   Usage: node scripts/check-hero.mjs --base http://localhost:3100 [--playwright <path>] [--shots <dir>] */

import { mkdirSync } from "node:fs";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

export const TOP_TOLERANCE = 40;
export const PAGES = ["/", "/fr/"];
export const THEMES = ["light", "dark"];

/** Runs in the page: the heights of the tag's fields against one line, and the h1's top against the picker's. */
function measure() {
  const tag = document.querySelector(".jh-specimen");
  const h1 = document.querySelector("h1");
  const picker = document.querySelector(".kz-hero-stage, [data-hero-stage]") ?? document.querySelector(".kz-tpl-split > :nth-child(2)");
  const lineOf = (el) => parseFloat(getComputedStyle(el).lineHeight) || el.getBoundingClientRect().height;
  const cells = tag ? [...tag.querySelectorAll("dt, dd")] : [];
  return {
    hasTag: Boolean(tag),
    wrapped: cells.filter((el) => el.getBoundingClientRect().height > lineOf(el) * 1.5).map((el) => el.textContent),
    h1Top: h1?.getBoundingClientRect().top ?? null,
    pickerTop: picker?.getBoundingClientRect().top ?? null,
  };
}

async function main() {
  const args = process.argv.slice(2);
  const flag = (name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);
  const base = flag("--base", "http://localhost:3000").replace(/\/$/, "");
  const shots = flag("--shots", null);
  const { chromium } = createRequire(import.meta.url)(flag("--playwright", "playwright"));
  if (shots) mkdirSync(shots, { recursive: true });
  const browser = await chromium.launch();
  const problems = [];
  for (const theme of THEMES) {
    for (const width of [1440, 360]) {
      const context = await browser.newContext({ viewport: { width, height: 900 }, reducedMotion: "reduce", colorScheme: theme });
      await context.addInitScript((t) => localStorage.setItem("kz-theme", t), theme);
      const page = await context.newPage();
      for (const path of PAGES) {
        await page.goto(`${base}${path}`, { waitUntil: "networkidle" });
        await page.evaluate(() => document.fonts.ready);
        const m = await page.evaluate(measure);
        const where = `${path} ${theme} ${width}px`;
        if (!m.hasTag) problems.push(`${where}: no specimen tag`);
        if (m.wrapped.length) problems.push(`${where}: a fact wraps: ${m.wrapped.join(" | ")}`);
        if (width === 1440 && (m.h1Top === null || m.pickerTop === null || Math.abs(m.h1Top - m.pickerTop) > TOP_TOLERANCE)) {
          problems.push(`${where}: the h1 top (${m.h1Top}) is not within ${TOP_TOLERANCE}px of the picker top (${m.pickerTop})`);
        }
        if (shots && path === "/") await page.screenshot({ path: `${shots}/hero-${theme}-${width}.png` });
      }
      await context.close();
    }
  }
  await browser.close();
  if (problems.length) {
    console.error(problems.join("\n"));
    process.exit(1);
  }
  console.log("check-hero: the facts are on one line and the h1 is level with the picker");
}

if (process.argv[1] === fileURLToPath(import.meta.url)) await main();
