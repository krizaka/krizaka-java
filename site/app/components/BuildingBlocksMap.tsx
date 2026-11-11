"use client";

/* ─────────────────────────────────────────────────────────────────────────
   BUILDING BLOCKS MAP — every artifact of Krizaka Java, from the POMs
   Every artifact of lib/model.ts in one band per role (services, adapters, domain core, contracts, starters, service kit,
   build), labels in full, never truncated. Select one: what it depends on and what depends on it stay lit, joined by
   orthogonal lines drawn under the chips through the gutters between bands (a parent POM, a BOM import or a test scope is
   dashed). Mobile: one column, no lines — the relations are listed in the detail panel.
   A11y: chips are buttons (aria-pressed), Escape clears, a live region names the selection, and the "Text version" holds
   every artifact and relation as a table.
   ───────────────────────────────────────────────────────────────────────── */

import { Fragment, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { AiIcon, AutomationIcon, CloseIcon, LinkIcon, LockIcon, PackIcon, SearchIcon, ServerIcon, SettingsIcon } from "@krizaka/icons";
import { format } from "@krizaka/i18n";
import { cn } from "@krizaka/ui/cn";
import { ROLES, type MapData, type MapModule, type RoleId } from "@/lib/roles";
import { useI18n } from "./I18nProvider";
import s from "./blocks-map.module.css";

const ROLE_ICON: Record<RoleId, typeof AiIcon> = {
  service: ServerIcon,
  adapter: LinkIcon,
  domain: AiIcon,
  contract: LockIcon,
  platform: PackIcon,
  starter: AutomationIcon,
  build: SettingsIcon,
};

type Rel = "uses" | "usedBy" | "both";
interface Edge {
  from: string;
  to: string;
  dashed: boolean;
}
interface Relation {
  id: string;
  via: string | null;
  dashed: boolean;
}

/** An orthogonal path through the points, with rounded corners. */
function roundedPath(points: [number, number][], r = 8) {
  let d = `M${points[0][0]},${points[0][1]}`;
  for (let i = 1; i < points.length - 1; i++) {
    const [x0, y0] = points[i - 1], [x1, y1] = points[i], [x2, y2] = points[i + 1];
    const k1 = Math.min(r, Math.hypot(x1 - x0, y1 - y0) / 2), k2 = Math.min(r, Math.hypot(x2 - x1, y2 - y1) / 2);
    const a: [number, number] = [x1 - Math.sign(x1 - x0) * k1, y1 - Math.sign(y1 - y0) * k1];
    const b: [number, number] = [x1 + Math.sign(x2 - x1) * k2, y1 + Math.sign(y2 - y1) * k2];
    d += ` L${a[0]},${a[1]} Q${x1},${y1} ${b[0]},${b[1]}`;
  }
  const last = points[points.length - 1];
  return `${d} L${last[0]},${last[1]}`;
}

export default function BuildingBlocksMap({ data, focus, compact = false }: { data: MapData; focus?: string; compact?: boolean }) {
  const { t } = useI18n();
  const text = t.map;
  const roleText = text.roles as Record<RoleId, { title: string; summary: string }>;

  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<string | null>(focus ?? null);
  const [hovered, setHovered] = useState<string | null>(null);

  const byId = useMemo(() => new Map(data.modules.map((m) => [m.id, m])), [data.modules]);
  const { dependencies } = data;

  const relationsOf = (id: string): { out: Relation[]; in: Relation[] } => ({
    out: dependencies.filter((d) => d.from === id).map((d) => ({ id: d.to, via: d.indirect ? d.kind : null, dashed: d.indirect })),
    in: dependencies.filter((d) => d.to === id).map((d) => ({ id: d.from, via: d.indirect ? d.kind : null, dashed: d.indirect })),
  });

  const activeId = hovered ?? selected;
  const active = activeId ? byId.get(activeId) ?? null : null;
  const pinned = selected ? byId.get(selected) ?? null : null;

  const relState = new Map<string, Rel>();
  if (active) {
    const r = relationsOf(active.id);
    for (const o of r.out) relState.set(o.id, "uses");
    for (const i of r.in) relState.set(i.id, relState.get(i.id) === "uses" ? "both" : "usedBy");
  }

  const q = query.trim().toLowerCase();
  const matches = (m: MapModule) => !q || m.id.includes(q) || m.repository.includes(q) || (m.description ?? "").toLowerCase().includes(q);

  const chipState = (m: MapModule): string => {
    if (q) return matches(m) ? (active?.id === m.id ? "selected" : "normal") : "dim";
    if (!active) return "normal";
    if (m.id === active.id) return "selected";
    return relState.get(m.id) ?? "dim";
  };

  // ── Edge geometry, measured from the DOM (desktop only; the CSS hides the layer on narrow screens) ──
  const boardRef = useRef<HTMLDivElement>(null);
  const chipRefs = useRef(new Map<string, HTMLButtonElement>());
  const bandRefs = useRef(new Map<RoleId, HTMLDivElement>());
  const [geometry, setGeometry] = useState<{ w: number; h: number; paths: { d: string; dashed: boolean; role: RoleId }[] }>({ w: 0, h: 0, paths: [] });
  const [layoutTick, setLayoutTick] = useState(0);

  const edges: Edge[] = (() => {
    if (!active || q) return [];
    const r = relationsOf(active.id);
    return [
      ...r.out.map((o) => ({ from: active.id, to: o.id, dashed: o.dashed })),
      ...r.in.map((i) => ({ from: i.id, to: active.id, dashed: i.dashed })),
    ];
  })();
  // The geometry is measured again when the set of edges changes (compared by value: the list is rebuilt each render).
  const edgesKey = edges.map((e) => `${e.from}>${e.to}:${e.dashed ? 1 : 0}`).join("|");

  useEffect(() => {
    const board = boardRef.current;
    if (!board) return;
    const ro = new ResizeObserver(() => setLayoutTick((n) => n + 1));
    ro.observe(board);
    return () => ro.disconnect();
  }, []);

  useLayoutEffect(() => {
    const board = boardRef.current;
    if (!board) return;
    const origin = board.getBoundingClientRect();
    const rect = (el: Element) => {
      const r = el.getBoundingClientRect();
      return { x: r.left - origin.left, y: r.top - origin.top, w: r.width, h: r.height };
    };
    const bandOf = (id: string) => bandRefs.current.get(byId.get(id)!.role);
    const paths = edges.flatMap((e) => {
      const a = chipRefs.current.get(e.from), b = chipRefs.current.get(e.to);
      const ba = bandOf(e.from), bb = bandOf(e.to);
      if (!a || !b || !ba || !bb || !a.offsetParent || !b.offsetParent) return [];
      const A = rect(a), B = rect(b), BA = rect(ba), BB = rect(bb);
      const ax = A.x + A.w / 2, bx = B.x + B.w / 2;
      let pts: [number, number][];
      if (BB.y > BA.y) {
        // Down: leave A by its bottom, run along the gutter under A's band, drop into B from above.
        const g = BA.y + BA.h + 11;
        pts = [[ax, A.y + A.h], [ax, g], [bx, g], [bx, B.y]];
      } else if (BB.y < BA.y) {
        const g = BA.y - 11;
        pts = [[ax, A.y], [ax, g], [bx, g], [bx, B.y + B.h]];
      } else {
        const g = BA.y + BA.h + 11;
        pts = [[ax, A.y + A.h], [ax, g], [bx, g], [bx, B.y + B.h]];
      }
      if (Math.abs(ax - bx) < 1) pts = [pts[0], pts[pts.length - 1]];
      return [{ d: roundedPath(pts), dashed: e.dashed, role: byId.get(e.to)!.role }];
    });
    const r = board.getBoundingClientRect();
    setGeometry({ w: r.width, h: r.height, paths });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `edges` is read through `edgesKey`, its value
  }, [edgesKey, layoutTick, byId]);

  // Escape clears the selection.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setSelected(null);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const select = (id: string) => setSelected((cur) => (cur === id ? null : id));
  const jump = (id: string) => {
    setSelected(id);
    chipRefs.current.get(id)?.focus({ preventScroll: false });
  };

  const pinnedRel = pinned ? relationsOf(pinned.id) : null;
  const announce = pinned && pinnedRel ? format(text.announce, { module: pinned.id, uses: pinnedRel.out.length, usedBy: pinnedRel.in.length }) : "";
  const relTag = (state: string | undefined) => (state === "uses" ? text.rel.uses : state === "usedBy" ? text.rel.usedBy : state === "both" ? text.rel.both : null);

  const legend = [
    { swatch: s.swatchLine, label: text.legend.dependency },
    ...(dependencies.some((d) => d.indirect) ? [{ swatch: s.swatchAmqp, label: text.legend.indirect }] : []),
  ];

  return (
    <section className={s.root} aria-label={text.label}>
      <div className={s.toolbar}>
        <div className={s.search}>
          <SearchIcon size={16} />
          <input type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder={text.search} aria-label={text.searchLabel} />
        </div>
      </div>
      <p className={s.hint}>{text.hint}</p>
      <ul className={s.legend} aria-hidden="true">
        {legend.map((l) => (
          <li key={l.label}><span className={l.swatch} /> {l.label}</li>
        ))}
      </ul>
      <p aria-live="polite" className={s.srOnly}>{announce}</p>

      <div ref={boardRef} className={s.board} onMouseLeave={() => setHovered(null)}>
        <svg className={s.edges} width={geometry.w} height={geometry.h} aria-hidden="true">
          <defs>
            <marker id="dg-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
              <path d="M0,1 L9,5 L0,9 z" fill="context-stroke" />
            </marker>
          </defs>
          {geometry.paths.map((p, i) => (
            <path
              key={i}
              d={p.d}
              fill="none"
              className={s[`g-${p.role}`]}
              stroke="var(--g)"
              strokeWidth={1.6}
              strokeDasharray={p.dashed ? "5 5" : undefined}
              strokeLinecap="round"
              markerEnd="url(#dg-arrow)"
              opacity={0.9}
            />
          ))}
        </svg>

        {ROLES.map((role) => {
          const mods = data.modules.filter((m) => m.role === role);
          if (!mods.length) return null;
          const Icon = ROLE_ICON[role];
          return (
            <Fragment key={role}>
              <div className={cn(s.band, s[`g-${role}`])} ref={(el) => { if (el) bandRefs.current.set(role, el); else bandRefs.current.delete(role); }}>
                <div className={s.bandHead}>
                  <span className={s.bandIcon}><Icon size={17} /></span>
                  <div>
                    <h3 className={s.bandTitle}>
                      {roleText[role].title}
                      <span>{mods.length}</span>
                    </h3>
                    {!compact && <p className={s.bandSub}>{roleText[role].summary}</p>}
                  </div>
                </div>
                <div className={s.chips}>
                  {mods.map((m) => {
                    const state = chipState(m);
                    const tag = state !== "selected" && state !== "dim" ? relTag(relState.get(m.id)) : null;
                    return (
                      <button
                        key={m.id}
                        type="button"
                        ref={(el) => { if (el) chipRefs.current.set(m.id, el); else chipRefs.current.delete(m.id); }}
                        className={cn(s.chip, s[`g-${m.role}`])}
                        data-state={state}
                        aria-pressed={selected === m.id}
                        onClick={() => select(m.id)}
                        onMouseEnter={() => setHovered(m.id)}
                        onFocus={() => setHovered(null)}
                      >
                        <span className={s.chipName}>{m.id}</span>
                        <span className={s.chipMeta}>
                          <b>{m.version}</b>
                          {m.central && m.central !== m.version && <span>{format(text.detail.central, { version: m.central })}</span>}
                        </span>
                        {tag && <span className={s.relTag}>{tag}</span>}
                      </button>
                    );
                  })}
                </div>
              </div>
              {pinned && pinned.role === role && pinnedRel && (
                <Detail module={pinned} roleTitle={roleText[pinned.role].title} rel={pinnedRel} byId={byId} onJump={jump} onClose={() => setSelected(null)} />
              )}
            </Fragment>
          );
        })}
      </div>

      <details className={s.textVersion}>
        <summary>{text.textVersion} — {format(text.count, { count: data.modules.length })}</summary>
        <div className={s.tableWrap}>
          <table className={s.table}>
            <caption className={s.caption}>{text.table.modulesCaption}</caption>
            <thead>
              <tr>
                <th scope="col">{text.table.module}</th>
                <th scope="col">{text.table.role}</th>
                <th scope="col">{text.table.repository}</th>
                <th scope="col">{text.table.version}</th>
                <th scope="col">{text.table.dependsOn}</th>
                <th scope="col">{text.table.usedBy}</th>
              </tr>
            </thead>
            <tbody>
              {data.modules.map((m) => (
                <tr key={m.id}>
                  <th scope="row"><code>{m.id}</code></th>
                  <td>{roleText[m.role].title}</td>
                  <td><code>{m.repository}</code></td>
                  <td>{m.version}{m.central && m.central !== m.version ? ` (${format(text.detail.central, { version: m.central })})` : ""}</td>
                  <td>{dependencies.filter((d) => d.from === m.id).map((d) => (d.indirect ? `${d.to} (${d.kind})` : d.to)).join(", ") || "—"}</td>
                  <td>{dependencies.filter((d) => d.to === m.id).map((d) => d.from).join(", ") || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </section>
  );
}

function Detail({
  module: m,
  roleTitle,
  rel,
  byId,
  onJump,
  onClose,
}: {
  module: MapModule;
  roleTitle: string;
  rel: { out: Relation[]; in: Relation[] };
  byId: Map<string, MapModule>;
  onJump: (id: string) => void;
  onClose: () => void;
}) {
  const { t } = useI18n();
  const text = t.map;
  const list = (items: Relation[]) =>
    items.length ? (
      <ul className={s.relList}>
        {items.map((it) => (
          <li key={it.id}>
            <button type="button" className={cn(s.linkButton, s[`g-${byId.get(it.id)?.role ?? "contract"}`])} onClick={() => onJump(it.id)}>
              <i aria-hidden="true" />
              {it.id}
              {it.via && <small>{it.via}</small>}
            </button>
          </li>
        ))}
      </ul>
    ) : (
      <span className={s.none}>{text.detail.none}</span>
    );
  return (
    <div className={cn(s.detail, s[`g-${m.role}`])} role="region" aria-label={m.id}>
      <div>
        <div className={s.detailHead}>
          <div style={{ minWidth: 0 }}>
            <h4 className={s.detailTitle}>{m.id}</h4>
            <p className={s.detailRole}>{roleTitle}</p>
          </div>
          <button type="button" className={s.linkButton} onClick={onClose} aria-label={text.detail.close} style={{ alignSelf: "flex-start" }}>
            <CloseIcon size={14} />
          </button>
        </div>
        {m.description && <p className={s.detailText}>{m.description}</p>}
        <ul className={s.facts}>
          <li>
            {text.detail.repository}{" "}
            <a href={m.repositoryUrl} target="_blank" rel="noreferrer">{m.repository}</a>
          </li>
          <li>{format(text.detail.version, { version: m.version })}</li>
          {m.central && <li>{format(text.detail.central, { version: m.central })}</li>}
        </ul>
      </div>
      <div className={s.relations}>
        <div>
          <h4>{text.detail.uses}</h4>
          {list(rel.out)}
        </div>
        <div>
          <h4>{text.detail.usedBy}</h4>
          {list(rel.in)}
        </div>
      </div>
    </div>
  );
}
