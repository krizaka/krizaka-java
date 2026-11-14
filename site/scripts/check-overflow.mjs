#!/usr/bin/env node
/* No horizontal overflow, measured in a real browser (Playwright) on the built site, at every width people read it at
   (phone 360 to desktop 1440), in both languages and both themes. A page overflows when the document is wider than the
   window, or when a visible element sticks out of it without sitting in a scroller made for that (a code block, a table).
   The home is the page that must never overflow; the documentation pages are checked too.

   Usage: node scripts/check-overflow.mjs --base http://localhost:3000 [--playwright <path>] [--shots <dir>]
   With --shots, a screenshot of the home is saved per width and theme (390 and 1440 are the ones a pull request shows). */

import { mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { flagOf, inspectViews } from "./browser-views.mjs";

/** The widths the site is read at, from the narrowest phone to a wide desktop. */
export const WIDTHS = [360, 390, 768, 1024, 1440];
/** The pages checked: the home in both languages first, then a guide, the map and the changelog. */
export const PAGES = ["/", "/fr/", "/start/", "/fr/start/", "/map/", "/web/", "/changelog/"];
export const THEMES = ["dark", "light"];

/** Runs in the page: the elements that stick out of the window's width, outside any horizontal scroller. */
function measure() {
  const width = document.documentElement.clientWidth;
  const out = [];
  if (document.documentElement.scrollWidth > width + 1) out.push(`the document is ${document.documentElement.scrollWidth}px wide in a ${width}px window`);
  const scrolls = (el) => {
    for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
      const o = getComputedStyle(p).overflowX;
      if (o === "auto" || o === "scroll" || o === "hidden" || o === "clip") return true;
    }
    return false;
  };
  for (const el of document.body.querySelectorAll("*")) {
    const r = el.getBoundingClientRect();
    if (!r.width || !r.height || r.right <= width + 1 && r.left >= -1) continue;
    const style = getComputedStyle(el);
    if (style.visibility === "hidden" || style.display === "none" || style.position === "fixed" || style.clipPath !== "none") continue;
    if (scrolls(el)) continue;
    out.push(`<${el.tagName.toLowerCase()}${el.className && typeof el.className === "string" ? "." + el.className.trim().split(/\s+/)[0] : ""}> spans ${Math.round(r.left)}→${Math.round(r.right)} in a ${width}px window`);
    if (out.length > 6) break;
  }
  return out;
}

async function main() {
  const args = process.argv.slice(2);
  const shots = flagOf(args, "--shots", null);
  if (shots) mkdirSync(shots, { recursive: true });
  const problems = await inspectViews({
    args,
    pages: PAGES,
    widths: WIDTHS,
    themes: THEMES,
    inspect: async (page, { path, width, theme }) => {
      if (shots && (path === "/" || path === "/fr/") && (width === 390 || width === 1440)) {
        await page.screenshot({ path: `${shots}/home${path === "/" ? "" : "-fr"}-${width}-${theme}.png`, fullPage: true });
      }
      return page.evaluate(measure);
    },
  });
  if (problems.length) {
    console.error(problems.map((p) => `check-overflow: ${p}`).join("\n"));
    process.exit(1);
  }
  console.log(`check-overflow: ${PAGES.length} pages × ${WIDTHS.length} widths × ${THEMES.length} themes, no horizontal overflow`);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) await main();
