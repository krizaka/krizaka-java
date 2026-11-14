"use client";

/* « Start a project »: the building blocks as chips, the BOM import and the dependencies they make, for Maven or Gradle,
   ready to copy. The choice lives in the address (`?blocks=web,testing`), so a link shares it. A block the published BOM
   does not offer at the released version is shown, dimmed, with the reason — never printed. Every word is a message;
   the versions and what is offered come from the server (StartProject, read at build). */

import { Chip } from "@krizaka/ui/chip";
import { CodeBlock, CodeTabs } from "@krizaka/ui/code-block";
import { format } from "@krizaka/i18n";
import { useEffect, useState } from "react";
import { type BlockId, gradleSnippet, mavenSnippet, type OfferedBlock, parseSelection, selectionQuery } from "@/lib/project";

export interface ProjectWords {
  label: string;
  pick: string;
  blocks: Record<BlockId, { title: string; hint: string }>;
  states: { pending: string; unpublished: string };
  legend: string;
  tabs: string;
  maven: string;
  gradle: string;
  copy: string;
  copied: string;
  summary: string;
  none: string;
  share: string;
  more: string;
}

export function ProjectGenerator({ bomVersion, offered, words, guideHref }: { bomVersion: string; offered: OfferedBlock[]; words: ProjectWords; guideHref: string }) {
  const [chosen, setChosen] = useState<BlockId[]>(() => parseSelection("", offered));
  // The address may carry a choice (a shared link): read it once mounted, so the first paint matches the server's.
  useEffect(() => {
    if (new URLSearchParams(window.location.search).has("blocks")) setChosen(parseSelection(window.location.search, offered));
  }, [offered]);

  const toggle = (id: BlockId, on: boolean) => {
    const next = offered.filter((b) => (b.id === id ? on : chosen.includes(b.id))).map((b) => b.id);
    setChosen(next);
    window.history.replaceState(null, "", `${window.location.pathname}${selectionQuery(next)}${window.location.hash}`);
  };
  const picked = offered.filter((b) => chosen.includes(b.id));
  const copy = { copyLabel: words.copy, copiedLabel: words.copied };

  return (
    <div className="jh-gen" role="group" aria-label={words.label}>
      <p className="jh-gen-pick">{words.pick}</p>
      <ul className="jh-gen-blocks">
        {offered.map((b) => {
          const w = words.blocks[b.id];
          const why = b.state === "ready" ? w.hint : words.states[b.state];
          return (
            <li key={b.id} data-state={b.state}>
              <Chip size="sm" selected={chosen.includes(b.id)} onSelectedChange={(on) => toggle(b.id, on)} disabled={b.state !== "ready"} title={why} aria-describedby={`jh-gen-${b.id}`} data-block={b.id}>
                {w.title}
              </Chip>
              <span id={`jh-gen-${b.id}`} className="jh-gen-why">
                {why}
              </span>
            </li>
          );
        })}
      </ul>
      {offered.some((b) => b.state !== "ready") && <p className="jh-gen-legend">{words.legend}</p>}
      <p className="jh-gen-summary" aria-live="polite">
        {picked.length ? format(words.summary, { count: picked.length, version: bomVersion }) : words.none}
      </p>
      <CodeTabs
        label={words.tabs}
        tabs={[
          { id: "maven", label: words.maven, content: <CodeBlock code={mavenSnippet(bomVersion, picked)} language="xml" file="pom.xml" {...copy} /> },
          { id: "gradle", label: words.gradle, content: <CodeBlock code={gradleSnippet(bomVersion, picked)} language="kotlin" file="build.gradle.kts" {...copy} /> },
        ]}
      />
      <p className="jh-gen-foot">
        <span>{words.share}</span> <a href={guideHref}>{words.more} →</a>
      </p>
    </div>
  );
}
