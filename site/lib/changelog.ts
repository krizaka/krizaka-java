import { newestFirst, parseReleaseBody, type Release } from "@krizaka/ui/changelog-page";

/* The changelog of Krizaka Java: the GitHub Releases of krizaka/krizaka-java, written by release-please (conventional
   commits, one section per kind, one scope per module), refreshed at build. Nothing is written by hand; when GitHub cannot
   be read the page says so. */

export const CHANGELOG_REPO = "krizaka/krizaka-java";
const API = `https://api.github.com/repos/${CHANGELOG_REPO}/releases?per_page=30`;

interface GithubRelease {
  tag_name: string;
  draft: boolean;
  published_at: string | null;
  html_url: string;
  body: string | null;
}

/** Sections of a release-please body that are news for a reader, with the conventional type they stand for. */
const SECTIONS: Record<string, string> = { Features: "feat", "Bug Fixes": "fix", "Performance Improvements": "perf", Security: "security", Reverts: "revert" };

/**
 * A release-please body as the adapter reads it: each bullet of a news section becomes `type: Module — text <PR url>`
 * (the `**module:**` scope names the module), commit-hash links are dropped, housekeeping sections are skipped.
 */
export function normalizeBody(body: string): string {
  const out: string[] = [];
  let type: string | null = null;
  for (const line of body.split("\n")) {
    const heading = /^### (.+)$/.exec(line);
    if (heading) {
      type = SECTIONS[heading[1].trim()] ?? null;
      continue;
    }
    const bullet = /^\* (.+)$/.exec(line);
    if (!bullet || !type) continue;
    const pr = /\(\[#\d+\]\((https:\/\/[^)]+)\)\)/.exec(bullet[1])?.[1];
    const text = bullet[1]
      .replace(/\s*\(\[[0-9a-f]{7,}\]\([^)]*\)\)/g, "")
      .replace(/\s*\(\[#\d+\]\([^)]*\)\)/g, "")
      .replace(/^\*\*([\w./-]+):\*\*\s*/, (_m, scope: string) => `${scope} — `);
    out.push(`* ${type}: ${text}${pr ? ` ${pr}` : ""}`);
  }
  return out.join("\n");
}

/** The releases as the changelog shows them: tag → version, body → tagged changes, newest first. */
export function releasesFromGithub(list: readonly GithubRelease[]): Release[] {
  return list
    .filter((r) => !r.draft && r.published_at)
    .map((r) => ({
      source: "Krizaka Java",
      version: r.tag_name.replace(/^v/, ""),
      date: (r.published_at ?? "").slice(0, 10),
      url: r.html_url,
      changes: parseReleaseBody(normalizeBody(r.body ?? "")),
    }))
    .filter((r) => r.changes.length > 0)
    .sort(newestFirst);
}

/** Every release worth showing, or an empty list when GitHub cannot be read. */
export async function loadReleases(): Promise<Release[]> {
  try {
    const res = await fetch(API, { headers: { Accept: "application/vnd.github+json" } });
    return res.ok ? releasesFromGithub((await res.json()) as GithubRelease[]) : [];
  } catch {
    return [];
  }
}
