#!/usr/bin/env node
/* The home's hero, measured in a real browser (Playwright) on the built site: at 1440 px every fact of the specimen tag is
   on one line, and the h1 starts level with the picker (within 40 px). With --shots <dir>, a screenshot of the first screen
   is saved per theme and width (1440 and 360).

   Usage: node scripts/check-hero.mjs --base http://localhost:3100 [--playwright <path>] [--shots <dir>] */

import { mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { flagOf, inspectViews } from "./browser-views.mjs";

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
  const shots = flagOf(args, "--shots", null);
  if (shots) mkdirSync(shots, { recursive: true });
  const problems = await inspectViews({
    args,
    pages: PAGES,
    widths: [1440, 360],
    themes: THEMES,
    inspect: async (page, { path, width, theme }) => {
      const m = await page.evaluate(measure);
      const issues = [];
      if (!m.hasTag) issues.push("no specimen tag");
      if (m.wrapped.length) issues.push(`a fact wraps: ${m.wrapped.join(" | ")}`);
      if (width === 1440 && (m.h1Top === null || m.pickerTop === null || Math.abs(m.h1Top - m.pickerTop) > TOP_TOLERANCE)) {
        issues.push(`the h1 top (${m.h1Top}) is not within ${TOP_TOLERANCE}px of the picker top (${m.pickerTop})`);
      }
      if (shots && path === "/") await page.screenshot({ path: `${shots}/hero-${theme}-${width}.png` });
      return issues;
    },
  });
  if (problems.length) {
    console.error(problems.map((p) => `check-hero: ${p}`).join("\n"));
    process.exit(1);
  }
  console.log("check-hero: the facts are on one line and the h1 is level with the picker");
}

if (process.argv[1] === fileURLToPath(import.meta.url)) await main();
