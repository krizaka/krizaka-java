import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createGet, generate, MAX_ATTEMPTS } from "../scripts/gen-registry-versions.mjs";

const BASE = "https://repo1.maven.org/maven2/com/krizaka/";
const POM = `<project><version>1.0.0</version><dependencyManagement><dependencies><dependency><groupId>com.krizaka</groupId><artifactId>krizaka-bom</artifactId><version>\${project.version}</version></dependency></dependencies></dependencyManagement></project>`;
const BODIES = {
  [BASE]: '<a href="krizaka-bom/">krizaka-bom/</a>',
  [`${BASE}krizaka-bom/maven-metadata.xml`]: "<metadata><release>1.0.0</release><versions><version>1.0.0</version></versions></metadata>",
  [`${BASE}krizaka-bom/1.0.0/krizaka-bom-1.0.0.pom`]: POM,
};
const res = (status, body = "", headers = {}) => ({ ok: status >= 200 && status < 300, status, text: async () => body, headers: new Headers(headers) });

/** A fake Central: `script` lists the statuses served first (per call, any URL); then the real bodies. */
function fakes(script = []) {
  const calls = [];
  const sleeps = [];
  const logs = [];
  const fetchImpl = async (url) => {
    calls.push(url);
    const next = script.shift();
    if (next instanceof Error) throw next;
    if (next !== undefined) return typeof next === "number" ? res(next) : next;
    return res(200, BODIES[url]);
  };
  const get = createGet({ fetchImpl, sleep: async (ms) => void sleeps.push(ms), log: (l) => logs.push(l) });
  return { get, calls, sleeps, logs };
}
const out = () => join(mkdtempSync(join(tmpdir(), "regver-")), "maven-versions.json");

test("a first 404 then 200 builds the file", async () => {
  const f = fakes([404]);
  const file = out();
  await generate({ get: f.get, out: file });
  assert.equal(JSON.parse(readFileSync(file, "utf8")).maven["krizaka-bom"], "1.0.0");
  assert.deepEqual(f.sleeps, [1000]);
  assert.match(f.logs[0], /krizaka\/.*404, retry 1\/3/);
});

test("three 503 then 200 builds the file with a 1 s / 2 s / 4 s backoff", async () => {
  const f = fakes([503, 503, 503]);
  const file = out();
  await generate({ get: f.get, out: file });
  assert.ok(existsSync(file));
  assert.deepEqual(f.sleeps, [1000, 2000, 4000]);
  assert.equal(f.logs.length, 3);
});

test("a 404 on every attempt fails with the URL and the status, within the sleep bound", async () => {
  const f = fakes([404, 404, 404, 404]);
  await assert.rejects(f.get(BASE), (e) => e.message === `${BASE} answered 404`);
  assert.equal(f.calls.length, MAX_ATTEMPTS);
  assert.ok(f.sleeps.reduce((a, b) => a + b, 0) <= 7000);
});

test("a 403 fails at once, with no retry", async () => {
  const f = fakes([403]);
  await assert.rejects(f.get(BASE), /answered 403/);
  assert.equal(f.calls.length, 1);
  assert.deepEqual(f.sleeps, []);
});

test("Retry-After is honoured, capped at 4 s", async () => {
  const f = fakes([res(429, "", { "retry-after": "2" }), res(429, "", { "retry-after": "120" })]);
  assert.equal(await f.get(BASE), BODIES[BASE]);
  assert.deepEqual(f.sleeps, [2000, 4000]);
});

test("a network error and a timeout are retried", async () => {
  const timeout = Object.assign(new Error("timed out"), { name: "TimeoutError" });
  const f = fakes([new TypeError("fetch failed"), timeout]);
  assert.equal(await f.get(BASE), BODIES[BASE]);
  assert.match(f.logs[0], /network error, retry 1\/3/);
  assert.match(f.logs[1], /timeout, retry 2\/3/);
});

test("Central down with no previous file fails the build; with one it is kept", async () => {
  const file = out();
  await assert.rejects(generate({ get: fakes([500, 500, 500, 500]).get, out: file }), /no previous file/);
  await generate({ get: fakes().get, out: file });
  const before = readFileSync(file, "utf8");
  await generate({ get: fakes([500, 500, 500, 500]).get, out: file });
  assert.equal(readFileSync(file, "utf8"), before);
});
