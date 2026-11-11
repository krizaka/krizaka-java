import type { AnchorHTMLAttributes, ComponentProps } from "react";
import defaultMdxComponents from "fumadocs-ui/mdx";
import type { MDXComponents } from "mdx/types";
import { format, getDictionary, type Locale } from "@/lib/i18n";
import { buildingBlocksMap, checkFocus } from "@/lib/model";
import { checkArtifact } from "@/lib/configuration";
import { mavenVersion } from "@/lib/versions";
import BuildingBlocksMap from "./BuildingBlocksMap";
import { ConfigurationReference } from "./ConfigurationReference";
import { FeatureMatrix } from "./FeatureMatrix";
import { EventSchema } from "./EventSchema";
import StartProject from "./StartProject";

/** The components a page may use: `<BuildingBlocksMap focus="…" />` (generated from the POMs), `<PendingRelease />` (the
    note that says, from the data, when the services are not on Maven Central at the version the BOM manages) and
    `<ComingSoon issue="…" />` (a contract the services do not publish yet) and `<StartProject />` (the generator), `<ConfigurationReference artifact="…" />` (generated from the configuration
    metadata) and `<FeatureMatrix />` (shipped / partial / planned, checked against the code). */
export function getMdxComponents(locale: Locale): MDXComponents {
  const t = getDictionary(locale);
  const Anchor = defaultMdxComponents.a;
  return {
    ...defaultMdxComponents,
    // A link to another page of the documentation stays in the reader's language (/web → /fr/web).
    a: ({ href, ...props }: AnchorHTMLAttributes<HTMLAnchorElement>) => <Anchor href={href?.startsWith("/") && !href.startsWith("//") ? `${locale === "fr" ? "/fr" : ""}${href}` : href} {...props} />,
    BuildingBlocksMap: ({ focus }: { focus?: string }) => (
      <div className="not-prose" style={{ margin: "24px 0 32px" }}>
        <BuildingBlocksMap data={buildingBlocksMap()} focus={focus ? checkFocus(focus) : undefined} />
      </div>
    ),
    PendingRelease: () => {
      const map = buildingBlocksMap();
      const bom = mavenVersion("krizaka-bom");
      const client = map.modules.find((m) => m.id === "krizaka-users-client");
      const kit = mavenVersion("krizaka-users-client");
      // Said only while it is true: the POM declares a release Central does not have yet.
      if (!bom || !client || !kit || client.version === kit) return null;
      return (
        <aside className="kz-note" role="note">
          <strong>{t.pendingRelease.title}</strong>
          {format(t.pendingRelease.body, { bom, pom: client.version, kit })}{" "}
          <a href="https://github.com/krizaka/krizaka-java/issues/11">{t.pendingRelease.issue}</a>
        </aside>
      );
    },
    // The « start a project » generator: the blocks the published BOM offers, the snippet for Maven or Gradle.
    StartProject: () => (
      <div className="not-prose" style={{ margin: "20px 0 28px" }}>
        <StartProject locale={locale} />
      </div>
    ),
    // Every krizaka.* property, generated from the configuration metadata (all of them, or one artifact's).
    ConfigurationReference: ({ artifact }: { artifact?: string }) => <ConfigurationReference locale={locale} artifact={artifact ? checkArtifact(artifact) : undefined} />,
    // What ships, partly ships and is planned — every shipped line backed by files on main.
    FeatureMatrix: () => <FeatureMatrix locale={locale} />,
    EventSchema: (props: Omit<ComponentProps<typeof EventSchema>, "locale">) => <EventSchema {...props} locale={locale} />,
    ComingSoon: ({ issue }: ComponentProps<"a"> & { issue: string }) => (
      <aside className="kz-note" role="note">
        <strong>{t.comingSoon.title}</strong>
        {t.comingSoon.body} <a href={issue}>{t.comingSoon.issue}</a>
      </aside>
    ),
  };
}
