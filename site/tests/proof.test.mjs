import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { countTests, sovkoraServices, platformOf } from "../scripts/gen-proof.mjs";

function tree(files) {
  const dir = mkdtempSync(join(tmpdir(), "kz-java-proof-"));
  for (const [name, content] of Object.entries(files)) {
    mkdirSync(dirname(join(dir, name)), { recursive: true });
    writeFileSync(join(dir, name), content);
  }
  return dir;
}

test("the tests are counted, and the ones that start a real PostgreSQL or RabbitMQ apart", () => {
  const dir = tree({
    "a/src/test/java/PlainTest.java": "class PlainTest { @Test void a() {} @Test void b() {} @ParameterizedTest void c() {} }",
    "a/src/test/java/OutboxIT.java": "class OutboxIT extends AbstractContainerIntegrationTest { @Test void x() {} @Test void y() {} }",
    "a/src/test/java/BrokerIT.java": "@Testcontainers class BrokerIT { RabbitMQContainer r; @Test void z() {} }",
    "a/src/main/java/Main.java": "class Main { @Test void notATest() {} }",
    "a/.cache/repos/b/src/test/java/CloneTest.java": "@Test",
  });
  assert.deepEqual(countTests(dir), { tests: 6, realInfrastructure: 3 });
});

test("a main source is never a test, and an empty tree has none", () => {
  assert.deepEqual(countTests(tree({ "a/src/main/java/M.java": "@Test" })), { tests: 0, realInfrastructure: 0 });
});

test("Sovkora's services are the deployable modules that depend on a Krizaka artifact", () => {
  const dep = (a) => `<dependency><groupId>com.krizaka</groupId><artifactId>${a}</artifactId></dependency>`;
  const pom = (id, deps = "", packaging = "") => `<project><artifactId>${id}</artifactId>${packaging}<dependencies>${deps}</dependencies></project>`;
  const dir = tree({
    "sovkora-job-service/pom.xml": pom("sovkora-job-service", dep("krizaka-billing-client") + dep("krizaka-security")),
    "sovkora-edge/pom.xml": pom("sovkora-edge", dep("krizaka-web")),
    "sovkora-studio/sovkora-studio-client/pom.xml": pom("sovkora-studio-client", dep("krizaka-security")),
    "sovkora-studio/sovkora-studio-api/pom.xml": pom("sovkora-studio-api"),
    "sovkora-studio/pom.xml": pom("sovkora-studio", dep("krizaka-bom"), "<packaging>pom</packaging>"),
    "sovkora-lonely-service/pom.xml": pom("sovkora-lonely-service"),
  });
  assert.deepEqual(sovkoraServices([dir]), [
    { id: "sovkora-edge", artifacts: ["krizaka-web"] },
    { id: "sovkora-job-service", artifacts: ["krizaka-billing-client", "krizaka-security"] },
  ]);
});

test("sovkoraServices returns an empty array when no repository is accessible (all archived)", () => {
  // locateSovkora filters out repos that fail to clone; the build must not fail when all are inaccessible
  assert.deepEqual(sovkoraServices([]), []);
  assert.deepEqual(sovkoraServices([tree({})]), []);
});

test("the platform is read from the parent POM, never typed", () => {
  const pom = "<properties><java.version>21</java.version><spring-boot.version>4.0.8</spring-boot.version></properties>";
  assert.deepEqual(platformOf(pom), { java: "21", springBoot: "4.0.8", springBootMinor: "4.0" });
  assert.throws(() => platformOf("<properties/>"), /java\.version/);
});
