import { getDictionary, localizedPath, type Locale } from "@/lib/i18n";
import { offeredBlocks } from "@/lib/project";
import { publishedBom } from "@/lib/versions";
import { ProjectGenerator } from "./ProjectGenerator";

/* The generator with what the last released BOM offers (read at build) and the words of the page's language:
   the home's hero and the Get started guide (`<StartProject />` in MDX). Server Component. */
export default function StartProject({ locale }: { locale: Locale }) {
  const bom = publishedBom();
  return <ProjectGenerator bomVersion={bom.version} offered={offeredBlocks(bom)} words={getDictionary(locale).home.project} guideHref={localizedPath(locale, "/build")} />;
}
