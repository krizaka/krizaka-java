import { CHANGELOG_REPO, loadReleases } from "@/lib/changelog";
import { getDictionary, type Locale, localizedPath } from "@/lib/i18n";
import { jsonLd } from "@/lib/json-ld";
import { uiVersion } from "@/lib/platform";
import { articleJsonLd } from "@/lib/seo";
import { repositoryVersion } from "@/lib/model";
import { ChangelogBody } from "./ChangelogBody";

/** `/changelog/` and `/fr/changelog/` (changelog template), inside the docs layout: the releases of Krizaka Java, read from GitHub at build. */
export async function ChangelogView({ locale }: { locale: Locale }) {
  const t = getDictionary(locale);
  return (
    <>
      {articleJsonLd(locale, "/changelog", t.changelog.metaTitle, t.changelog.metaDescription).map((data, i) => (
        <script key={i} type="application/ld+json" dangerouslySetInnerHTML={{ __html: jsonLd(data) }} />
      ))}
      <ChangelogBody
      c={t.changelog}
      releases={await loadReleases()}
      version={repositoryVersion("krizaka-bom")}
      ui={uiVersion()}
      back={{ href: localizedPath(locale, "/"), label: t.site.name }}
      feedHref={`${localizedPath(locale, "/changelog")}feed.xml`}
      pullsUrl={`https://github.com/${CHANGELOG_REPO}/releases`}
    />
    </>
  );
}
