/* The « start a project » generator: pick building blocks, get the BOM import and the dependencies for Maven or Gradle.
   Pure (no I/O: tests/project.test.mjs runs it with Node's type stripping). What it may offer comes from the BOM published
   in the registry (scripts/gen-registry-versions.mjs → lib/generated/maven-versions.json, `bom`): a block is « ready »
   only when that BOM manages its artifact at a version Central has, so every snippet it prints resolves. */

export type BlockId = "web" | "security" | "messaging" | "observability" | "appConfig" | "usersClient" | "billingClient" | "testing";

export interface ProjectBlock {
  id: BlockId;
  /** The artifact a project adds (a starter switches its module on). */
  artifact: string;
  scope: "compile" | "test";
  /** The guide of the block (a page of this site). */
  guide: string;
}

/** The blocks, in the order the generator shows them: the starters, the domain clients, the test kit. */
export const PROJECT_BLOCKS: readonly ProjectBlock[] = [
  { id: "web", artifact: "krizaka-spring-boot-starter-web", scope: "compile", guide: "/web" },
  { id: "security", artifact: "krizaka-spring-boot-starter-security", scope: "compile", guide: "/security" },
  { id: "messaging", artifact: "krizaka-spring-boot-starter-rabbitmq", scope: "compile", guide: "/messaging" },
  { id: "observability", artifact: "krizaka-spring-boot-starter-observability", scope: "compile", guide: "/observability" },
  { id: "appConfig", artifact: "krizaka-spring-boot-starter-app-config", scope: "compile", guide: "/app-config" },
  { id: "usersClient", artifact: "krizaka-users-client", scope: "compile", guide: "/users" },
  { id: "billingClient", artifact: "krizaka-billing-client", scope: "compile", guide: "/billing" },
  { id: "testing", artifact: "krizaka-test-support", scope: "test", guide: "/build" },
];

/** What a fresh visitor starts from: a web service, observed, tested. */
export const DEFAULT_SELECTION: readonly BlockId[] = ["web", "observability", "testing"];

export interface PublishedBom {
  version: string;
  manages: Record<string, { version: string; onCentral: boolean }>;
}

/** ready — managed by the published BOM at a version Central has; pending — managed, but Central does not have that
    version yet; unpublished — not in the published BOM. */
export type BlockState = "ready" | "pending" | "unpublished";
export type OfferedBlock = ProjectBlock & { state: BlockState; version: string | null };

export function offeredBlocks(bom: PublishedBom): OfferedBlock[] {
  return PROJECT_BLOCKS.map((block) => {
    const managed = bom.manages[block.artifact];
    const state: BlockState = !managed ? "unpublished" : managed.onCentral ? "ready" : "pending";
    return { ...block, state, version: managed?.version ?? null };
  });
}

const ready = (blocks: readonly OfferedBlock[]) => blocks.filter((b) => b.state === "ready");

/** The pom.xml lines: the BOM imported in dependencyManagement, then the blocks without a version (the BOM manages it). */
export function mavenSnippet(bomVersion: string, blocks: readonly OfferedBlock[]): string {
  const deps = ready(blocks)
    .map((b) => `  <dependency>\n    <groupId>com.krizaka</groupId>\n    <artifactId>${b.artifact}</artifactId>${b.scope === "test" ? "\n    <scope>test</scope>" : ""}\n  </dependency>`)
    .join("\n");
  return `<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.krizaka</groupId>
      <artifactId>krizaka-bom</artifactId>
      <version>${bomVersion}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
${deps}
</dependencies>`;
}

/** The build.gradle.kts lines: the BOM as a platform for each configuration used, then the blocks without a version. */
export function gradleSnippet(bomVersion: string, blocks: readonly OfferedBlock[]): string {
  const platform = `platform("com.krizaka:krizaka-bom:${bomVersion}")`;
  const chosen = ready(blocks);
  const lines: string[] = [];
  const main = chosen.filter((b) => b.scope === "compile");
  const test = chosen.filter((b) => b.scope === "test");
  if (main.length) lines.push(`    implementation(${platform})`, ...main.map((b) => `    implementation("com.krizaka:${b.artifact}")`));
  if (test.length) lines.push(`    testImplementation(${platform})`, ...test.map((b) => `    testImplementation("com.krizaka:${b.artifact}")`));
  return `dependencies {\n${lines.join("\n")}\n}`;
}

/** The selection from the address (`?blocks=web,testing`): ready blocks only, in the generator's order. No `blocks`
    parameter → the default; an empty one → nothing chosen. */
export function parseSelection(search: string, offered: readonly OfferedBlock[]): BlockId[] {
  const params = new URLSearchParams(search);
  const readyIds = ready(offered).map((b) => b.id);
  if (!params.has("blocks")) return DEFAULT_SELECTION.filter((id) => readyIds.includes(id));
  const asked = new Set((params.get("blocks") ?? "").split(",").map((s) => s.trim()));
  return readyIds.filter((id) => asked.has(id));
}

/** The query string that keeps a selection in the address. */
export const selectionQuery = (ids: readonly BlockId[]): string => `?blocks=${ids.join(",")}`;
