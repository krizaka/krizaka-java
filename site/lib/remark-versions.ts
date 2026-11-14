/* Replaces the version tokens of a page — `%krizaka-bom%`, `%krizaka-users-client%` (an artifact id between percent signs)
   — by the last release the registry lists (lib/versions.ts), in text, inline code and code blocks. An unknown token fails
   the build: a page never states a version nobody read. */

import type { Root } from "mdast";
import { mavenVersions } from "./versions.ts";

const TOKEN = /%(krizaka-[a-z0-9-]+)%/g;

function replace(value: string, versions: Record<string, string>, where: string): string {
  return value.replace(TOKEN, (whole, artifactId: string) => {
    if (!(artifactId in versions)) throw new Error(`${where}: ${whole} — the registry lists no com.krizaka:${artifactId}`);
    return versions[artifactId];
  });
}

export function remarkVersions() {
  return (tree: Root, file: { path?: string }) => {
    const versions = mavenVersions();
    const visit = (node: { type: string; value?: string; children?: unknown[] }) => {
      if (typeof node.value === "string" && ["text", "inlineCode", "code"].includes(node.type)) node.value = replace(node.value, versions, file.path ?? "page");
      for (const child of node.children ?? []) visit(child as never);
    };
    visit(tree as never);
  };
}
