import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { bomManaged } from "../scripts/bom-managed.mjs";
import { DEFAULT_SELECTION, PROJECT_BLOCKS, gradleSnippet, mavenSnippet, offeredBlocks, parseSelection, selectionQuery } from "../lib/project.ts";

/* The « start a project » generator (lib/project.ts, ProjectGenerator): it offers a block only when the BOM published on
   Maven Central manages it at a version Central has — a snippet it prints always resolves. */

const BOM_POM = `<project>
  <parent><groupId>com.krizaka</groupId><artifactId>krizaka-parent</artifactId><version>0.2.0</version></parent>
  <artifactId>krizaka-bom</artifactId>
  <version>0.2.0</version>
  <properties>
    <krizaka-service-kit.version>0.2.0</krizaka-service-kit.version>
    <krizaka-users.version>0.2.0</krizaka-users.version>
  </properties>
  <dependencyManagement>
    <dependencies>
      <dependency><groupId>com.krizaka</groupId><artifactId>krizaka-test-support</artifactId><version>\${project.version}</version></dependency>
      <dependency><groupId>com.krizaka</groupId><artifactId>krizaka-spring-boot-starter-web</artifactId><version>\${krizaka-service-kit.version}</version></dependency>
      <dependency><groupId>com.krizaka</groupId><artifactId>krizaka-users-client</artifactId><version>\${krizaka-users.version}</version></dependency>
      <dependency><groupId>org.example</groupId><artifactId>not-ours</artifactId><version>1.0</version></dependency>
    </dependencies>
  </dependencyManagement>
</project>`;

test("the BOM's managed artifacts, with their versions resolved from its properties and its own version", () => {
  assert.deepEqual(bomManaged(BOM_POM), {
    "krizaka-test-support": "0.2.0",
    "krizaka-spring-boot-starter-web": "0.2.0",
    "krizaka-users-client": "0.2.0",
  });
});

test("a BOM without a version of its own takes its parent's for `${project.version}`", () => {
  const inherited = BOM_POM.replace("<version>0.2.0</version>\n  <properties>", "<properties>").replace("<version>0.2.0</version></parent>", "<version>0.3.0</version></parent>");
  assert.equal(bomManaged(inherited)["krizaka-test-support"], "0.3.0");
});

test("a comment never counts, even one that holds a version", () => {
  const commented = BOM_POM.replace("<artifactId>krizaka-bom</artifactId>", "<!-- <version>9.9.9</version> the old way --><artifactId>krizaka-bom</artifactId>");
  assert.equal(bomManaged(commented)["krizaka-test-support"], "0.2.0");
});

test("an unresolvable version is left out, never guessed", () => {
  assert.deepEqual(bomManaged(BOM_POM.replace("${krizaka-users.version}", "${nowhere}")), { "krizaka-test-support": "0.2.0", "krizaka-spring-boot-starter-web": "0.2.0" });
});

const BOM = {
  version: "0.2.0",
  manages: {
    "krizaka-spring-boot-starter-web": { version: "0.2.0", onCentral: true },
    "krizaka-spring-boot-starter-security": { version: "0.2.0", onCentral: true },
    "krizaka-spring-boot-starter-rabbitmq": { version: "0.2.0", onCentral: true },
    "krizaka-spring-boot-starter-observability": { version: "0.2.0", onCentral: true },
    "krizaka-users-client": { version: "0.2.0", onCentral: false },
    "krizaka-test-support": { version: "0.2.0", onCentral: true },
  },
};

test("a block is offered only when the published BOM manages it at a version Maven Central has", () => {
  const offered = Object.fromEntries(offeredBlocks(BOM).map((b) => [b.id, b.state]));
  assert.equal(offered.web, "ready");
  assert.equal(offered.testing, "ready");
  assert.equal(offered.usersClient, "pending", "managed at 0.2.0, Central has an older one");
  assert.equal(offered.appConfig, "unpublished", "not managed by the published BOM");
});

test("Maven: the BOM imported once, the chosen blocks without versions, the test kit in test scope", () => {
  assert.equal(
    mavenSnippet("0.2.0", offeredBlocks(BOM).filter((b) => ["web", "testing"].includes(b.id))),
    `<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.krizaka</groupId>
      <artifactId>krizaka-bom</artifactId>
      <version>0.2.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>com.krizaka</groupId>
    <artifactId>krizaka-spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>com.krizaka</groupId>
    <artifactId>krizaka-test-support</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>`,
  );
});

test("Gradle (Kotlin DSL): the platform, then implementation / testImplementation", () => {
  assert.equal(
    gradleSnippet("0.2.0", offeredBlocks(BOM).filter((b) => ["web", "testing"].includes(b.id))),
    `dependencies {
    implementation(platform("com.krizaka:krizaka-bom:0.2.0"))
    implementation("com.krizaka:krizaka-spring-boot-starter-web")
    testImplementation(platform("com.krizaka:krizaka-bom:0.2.0"))
    testImplementation("com.krizaka:krizaka-test-support")
}`,
  );
});

test("the selection lives in the address: only ready blocks are kept, in the generator's order; nothing valid → the default", () => {
  const offered = offeredBlocks(BOM);
  assert.deepEqual(parseSelection("?blocks=testing,web,usersClient,nope", offered), ["web", "testing"]);
  assert.deepEqual(parseSelection("", offered), DEFAULT_SELECTION.filter((id) => offered.find((b) => b.id === id)?.state === "ready"));
  assert.deepEqual(parseSelection("?blocks=", offered), []);
  assert.equal(selectionQuery(["web", "testing"]), "?blocks=web,testing");
});

test("every block names an artifact of the POMs and has its words in both languages", () => {
  const model = JSON.parse(readFileSync(new URL("../lib/generated/building-blocks.json", import.meta.url), "utf8"));
  const ids = new Set(model.modules.map((m) => m.id));
  const en = JSON.parse(readFileSync(new URL("../messages/en.json", import.meta.url), "utf8"));
  const fr = JSON.parse(readFileSync(new URL("../messages/fr.json", import.meta.url), "utf8"));
  for (const b of PROJECT_BLOCKS) {
    assert.ok(ids.has(b.artifact), `${b.id}: ${b.artifact} is not an artifact of the POMs`);
    for (const dict of [en, fr]) assert.ok(dict.home.project.blocks[b.id]?.title && dict.home.project.blocks[b.id]?.hint, `${b.id}: words`);
  }
});

test("the generated file records what the published BOM manages", () => {
  const data = JSON.parse(readFileSync(new URL("../lib/generated/maven-versions.json", import.meta.url), "utf8"));
  assert.equal(data.bom.version, data.maven["krizaka-bom"]);
  assert.ok(Object.keys(data.bom.manages).length > 0);
});
