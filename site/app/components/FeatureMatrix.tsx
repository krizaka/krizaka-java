import { getDictionary, localizedPath, type Locale } from "@/lib/i18n";
import { evidenceUrl, features, featureCounts, roadmapUrl, type FeatureStatus } from "@/lib/features";

const ORDER: FeatureStatus[] = ["shipped", "partial", "planned"];

/* What Krizaka Java ships, partly ships and plans — honest by construction: every shipped line names files the build finds
   on main (scripts/check-features.mjs), every other line its issue. Server Component. */
export function FeatureMatrix({ locale }: { locale: Locale }) {
  const t = getDictionary(locale).features;
  const counts = featureCounts();
  return (
    <div className="not-prose" style={{ margin: "20px 0 28px" }}>
      <p className="kz-props-component">
        {ORDER.map((s) => `${counts[s]} ${t.status[s]}`).join(" · ")} · <a href={roadmapUrl}>{t.roadmap}</a>
      </p>
      <div className="kz-table-scroll">
        <table className="kz-table kz-features">
          <thead>
            <tr>
              <th scope="col">{t.capability}</th>
              <th scope="col">{t.statusLabel}</th>
              <th scope="col">{t.note}</th>
              <th scope="col">{t.proof}</th>
            </tr>
          </thead>
          <tbody>
            {ORDER.flatMap((status) => features.filter((f) => f.status === status)).map((f) => {
              const words = t.items[f.id as keyof typeof t.items];
              return (
                <tr key={f.id} id={f.id} data-status={f.status}>
                  <th scope="row">{f.page ? <a href={localizedPath(locale, f.page)}>{words.name}</a> : words.name}</th>
                  <td><span className="kz-status" data-status={f.status}>{t.status[f.status]}</span></td>
                  <td>{words.note}</td>
                  <td>
                    <span className="kz-constraints">
                      {(f.evidence ?? []).map((e) => (
                        <a key={`${e.repository}/${e.path}`} href={evidenceUrl(e)}>
                          <code>{e.path.split("/").pop()}</code>
                        </a>
                      ))}
                      {f.issue && <a href={f.issue}>{t.issue} #{f.issue.split("/").pop()}</a>}
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
