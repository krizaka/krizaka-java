import { getDictionary, type Locale } from "@/lib/i18n";
import { RELEASES_URL, REPOSITORY_URL } from "@/lib/site";
import { uiVersion } from "@/lib/platform";
import { repositoryVersion } from "@/lib/model";
import { FamilyFooterView } from "./FamilyFooterView";

/* The footer (NAV-SYS) is @krizaka/ui's SiteFooter: this site's own column (where the artifacts and the code are), the
   Krizaka family in columns, the signature, the release the pages were built from (the BOM of the repository). */
export default function SiteFooter({ locale }: { locale: Locale }) {
  const t = getDictionary(locale).footer;
  return (
    <FamilyFooterView
      locale={locale}
      aria={t.aria}
      version={repositoryVersion("krizaka-bom")}
      ui={uiVersion()}
      license={t.license}
      links={[
        { label: t.releases, href: RELEASES_URL },
        { label: t.source, href: REPOSITORY_URL },
      ]}
    />
  );
}
