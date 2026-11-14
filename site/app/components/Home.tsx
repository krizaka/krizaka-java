import Link from "next/link";
import { CheckIcon, ExternalIcon, ForwardIcon, GlobeIcon, MessageIcon, PackIcon, SettingsIcon, ShieldIcon, EyeIcon, StudioIcon, BookmarkIcon, LinkIcon, ClockIcon } from "@krizaka/icons";
import { Button } from "@krizaka/ui/button";
import { ArgumentCard } from "@krizaka/ui/argument-card";
import { CardGrid } from "@krizaka/ui/card-grid";
import { ComparisonTable } from "@krizaka/ui/comparison-table";
import { LandingPage } from "@krizaka/ui/landing-page";
import { LinkCard } from "@krizaka/ui/link-card";
import { Mascot } from "@krizaka/ui/mascots";
import { MascotQuote } from "@krizaka/ui/mascot-quote";
import { SpecimenTag } from "@krizaka/ui/specimen-tag";
import { NumberTicker } from "@krizaka/ui/number-ticker";
import { Section } from "@krizaka/ui/page";
import { format, getDictionary, localizedPath, type Locale } from "@/lib/i18n";
import type { FamilyId } from "@/lib/families";
import { counts, families } from "@/lib/home";
import { proof, roundedDown } from "@/lib/proof";
import { proofState } from "@/lib/proof-state";
import { RELEASES_URL, REPOSITORY_URL } from "@/lib/site";
import { repositoryVersion } from "@/lib/model";
import StartProject from "./StartProject";

/* The home of java.krizaka.com, on the landing template of @krizaka/ui: a split hero (the promise, and the « start a
   project » generator — pick the building blocks, copy the BOM import and the dependencies for Maven or Gradle), the facts
   read at build, why (three pillars), the families of building blocks (what each service no longer writes), the duck, the
   proof (who runs on it, the tests), an honest comparison, the ways on. Every figure is data (the POMs, the
   repositories' tests); every word is a message. */

const ICONS: Record<FamilyId, typeof GlobeIcon> = { web: GlobeIcon, security: ShieldIcon, messaging: MessageIcon, observability: EyeIcon, appConfig: SettingsIcon, testing: CheckIcon };
const COMPARE_ROWS = ["errors", "security", "messaging", "observability", "freedom"] as const;
const LICENSE = "Apache-2.0";
const UI_URL = "https://ui.krizaka.com";

export default function Home({ locale }: { locale: Locale }) {
  const dict = getDictionary(locale);
  const t = dict.home;
  const path = (p: string) => localizedPath(locale, p);
  const bom = repositoryVersion("krizaka-bom");
  const { platform, tests, services } = proof();
  const { modules } = counts();
  const blocks = families();
  const state = proofState(services);
  const proofWords = state.showServices ? t.proof : { ...t.proof, ...t.proof.testsOnly };

  return (
    <main id="nd-page" className="jh-home">
      <LandingPage
        eyebrow={t.hero.eyebrow}
        title={t.hero.title}
        lead={t.hero.lead}
        actions={
          <>
            <Button asChild variant="primary" size="lg">
              <Link href={path("/start")}>
                {t.hero.start} <ForwardIcon size={16} aria-hidden />
              </Link>
            </Button>
            <Button asChild variant="secondary" size="lg">
              <Link href={path("/map")}>{t.hero.map}</Link>
            </Button>
            <SpecimenTag
              className="jh-specimen"
              mount="pin"
              titleAs="p"
              code={bom}
              title={t.specimen.title}
              fields={[
                { label: t.specimen.modules, value: modules },
                { label: t.specimen.java, value: platform.java },
                { label: t.specimen.boot, value: platform.springBootMinor },
                { label: t.specimen.license, value: LICENSE },
              ]}
              footer={
                <>
                  <Link href={path("/start")}>{t.specimen.repository}</Link> · <a href={REPOSITORY_URL}>{t.specimen.source}</a>
                </>
              }
            />
          </>
        }
        visualLabel={t.project.label}
        visual={<StartProject locale={locale} />}
        featuresHeading={{ eyebrow: t.pillars.eyebrow, title: t.pillars.title, lead: t.pillars.lead }}
        features={[
          { id: "bom", size: "lg", icon: <PackIcon size={22} />, title: t.pillars.bom.title, text: t.pillars.bom.text },
          { id: "proven", size: "md", icon: <CheckIcon size={22} />, title: t.pillars.proven.title, text: t.pillars.proven.text },
          { id: "capabilities", size: "md", icon: <StudioIcon size={22} />, title: t.pillars.capabilities.title, text: t.pillars.capabilities.text },
        ]}
        closing={{
          title: t.closing.title,
          body: t.closing.body,
          actions: (
            <>
              <Button asChild variant="primary" size="lg">
                <Link href={path("/start")}>{t.closing.start}</Link>
              </Button>
              <Button asChild variant="secondary" size="lg">
                <a href={REPOSITORY_URL}>
                  {t.closing.source} <ExternalIcon size={16} aria-hidden />
                </a>
              </Button>
            </>
          ),
        }}
      >
        <Section id="families" eyebrow={t.families.eyebrow} title={t.families.title} lead={t.families.lead} reveal divider>
          <CardGrid min={290} columns={3}>
            {blocks.map((f) => {
              const Icon = ICONS[f.id];
              const words = t.families[f.id];
              return (
                <ArgumentCard
                  key={f.id}
                  id={`family-${f.id}`}
                  icon={<Icon size={22} aria-hidden />}
                  title={words.title}
                  meta={f.starter ?? f.module}
                  mark={f.bird ? <Mascot bird={f.bird} size={40} animated={false} /> : undefined}
                  hook={{ label: t.families.hookLabel, text: words.hook }}
                  facts={[{ label: format(t.families.version, { version: f.version }), tone: "ok" }]}
                  more={{ href: path(f.guide), label: t.families.details }}
                  linkAs={Link}
                />
              );
            })}
          </CardGrid>
          <div className="jh-duck">
            <MascotQuote bird="duck" mood="welcome" variant="callout" label={t.quote.label} data-reveal="">
              {t.quote.text}
            </MascotQuote>
          </div>
        </Section>

        <Section id="proof" eyebrow={t.proof.eyebrow} title={proofWords.title} lead={proofWords.lead} reveal divider>
          <div className="jh-proof">
            <dl className="jh-figures">
              <div>
                <dt>{t.proof.tests}</dt>
                <dd>
                  <NumberTicker value={roundedDown(tests.total)} locale={locale} />+
                </dd>
              </div>
              <div>
                <dt>{t.proof.real}</dt>
                <dd>
                  <NumberTicker value={roundedDown(tests.realInfrastructure, 10)} locale={locale} />+
                </dd>
              </div>
              {state.showServices && (
                <div>
                  <dt>{t.proof.services}</dt>
                  <dd>
                    <NumberTicker value={services.length} locale={locale} />
                  </dd>
                </div>
              )}
            </dl>
            {state.showServices && (
              <ul className="jh-services" aria-label={t.proof.listLabel}>
                {services.map((s) => (
                  <li key={s.id}>
                    <code>{s.id}</code>
                    <span>{s.artifacts.length === 1 ? t.proof.usesOne : format(t.proof.uses, { count: s.artifacts.length })}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <p className="jh-more">
            {t.proof.domain} <Link href={path("/map")}>{t.proof.domainLink} →</Link>
          </p>
        </Section>

        <Section id="compare" eyebrow={t.compare.eyebrow} title={t.compare.title} lead={t.compare.lead} reveal divider>
          <ComparisonTable
            label={t.compare.label}
            topicLabel={t.compare.topic}
            highlight="krizaka"
            columns={[
              { id: "plain", label: t.compare.plain },
              { id: "krizaka", label: t.compare.krizaka },
            ]}
            rows={COMPARE_ROWS.map((id) => ({
              id,
              topic: t.compare.rows[id].topic,
              answers: { plain: t.compare.rows[id].plain, krizaka: t.compare.rows[id].krizaka },
            }))}
          />
        </Section>

        <Section id="go" eyebrow={t.go.eyebrow} title={t.go.title} reveal divider>
          <CardGrid min={260} columns={3}>
            <LinkCard href={path("/start")} linkAs={Link} icon={<StudioIcon size={22} aria-hidden />} title={t.go.guides.title} description={t.go.guides.text} />
            <LinkCard href={path("/map")} linkAs={Link} icon={<LinkIcon size={22} aria-hidden />} title={t.go.map.title} description={t.go.map.text} />
            <LinkCard href={path("/changelog")} linkAs={Link} icon={<ClockIcon size={22} aria-hidden />} title={t.go.changelog.title} description={t.go.changelog.text} />
            <LinkCard href={REPOSITORY_URL} icon={<BookmarkIcon size={22} aria-hidden />} title={t.go.source.title} description={t.go.source.text} />
            <LinkCard href={RELEASES_URL} icon={<PackIcon size={22} aria-hidden />} title={t.go.maven.title} description={t.go.maven.text} />
            <LinkCard href={UI_URL} icon={<GlobeIcon size={22} aria-hidden />} title={t.go.ui.title} description={t.go.ui.text} />
          </CardGrid>
        </Section>
      </LandingPage>
    </main>
  );
}
