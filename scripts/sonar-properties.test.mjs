// The billing scripts are PostgreSQL: the Oracle PL/SQL rule plsql:CharVarchar must not fail the Sonar gate on them (the import PR went red on CHAR(3)).
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const properties = readFileSync(new URL("../sonar-project.properties", import.meta.url), "utf8");

test("plsql:CharVarchar is ignored on SQL files", () => {
  assert.match(properties, /ignore\.multiCriteria\.postgresSql\.ruleKey=plsql:CharVarchar/);
  assert.match(properties, /ignore\.multiCriteria\.postgresSql\.resourceKey=\*\*\/\*\.sql/);
});

// The Maven scanner ignores sonar-project.properties: the same ignore must be in the root pom.
test("plsql:CharVarchar is ignored on SQL files in the root pom (Maven scanner)", () => {
  const pom = readFileSync(new URL("../pom.xml", import.meta.url), "utf8");
  assert.match(pom, /<sonar\.issue\.ignore\.multicriteria\.postgresSql\.ruleKey>plsql:CharVarchar</);
  assert.match(pom, /<sonar\.issue\.ignore\.multicriteria\.postgresSql\.resourceKey>\*\*\/\*\.sql</);
});

// krizaka-internal#3553: the CI scripts are measured, not excluded; their lcov reaches the analysis through the shared Maven workflow.
test("scripts/ is covered: no coverage exclusion, the lcov path is read and uploaded by ci.yml", () => {
  const ci = readFileSync(new URL("../.github/workflows/ci.yml", import.meta.url), "utf8");
  assert.doesNotMatch(properties, /^sonar\.coverage\.exclusions=/m);
  assert.match(properties, /^sonar\.javascript\.lcov\.reportPaths=target\/scripts\.lcov$/m);
  assert.match(ci, /extra-coverage-command: node --test --experimental-test-coverage .*--test-reporter-destination=target\/scripts\.lcov scripts\/\*\.test\.mjs/);
  assert.match(ci, /sonar-coverage-paths: \|\n(?: +\S.*\n)* +target\/scripts\.lcov\n/);
  assert.match(properties, /scripts\/\*\*\/\*\.test\.mjs/);
});
