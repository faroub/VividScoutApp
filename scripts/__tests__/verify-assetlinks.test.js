/**
 * Test Suite for scripts/verify-assetlinks.js
 *
 * Verifies the deployed Digital Asset Links file contract: the JSON document must
 * target the production Android application id and carry valid SHA-256 certificate
 * fingerprints. Tests cover document parsing (pure helpers) plus an end-to-end CLI
 * run against a local fixture HTTP server, so the fetch + validation + exit-code
 * path is exercised, not just string checks.
 */

const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const os = require("node:os");
const http = require("node:http");
const path = require("node:path");
const { execFile } = require("node:child_process");
const { promisify } = require("node:util");

const execFileAsync = promisify(execFile);

const SCRIPT_PATH = path.join(__dirname, "..", "verify-assetlinks.js");
const { validateAssetLinksDocument, applicationIdFromBuildFile } = require(SCRIPT_PATH);

const EXPECTED_PACKAGE = "com.aistudio.vividscout.trvjxq";

function validEntry(overrides = {}) {
  const { target: targetOverride = {}, ...rest } = overrides;
  return {
    relation: ["android.intent.action.VIEW", "android.intent.category.BROWSABLE"],
    target: {
      namespace: "android_app",
      package_name: EXPECTED_PACKAGE,
      sha256_cert_fingerprints: ["AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"],
      ...targetOverride
    },
    ...rest
  };
}

test("applicationIdFromBuildFile reads the production application id", () => {
  const applicationId = applicationIdFromBuildFile();
  assert.strictEqual(applicationId, EXPECTED_PACKAGE);
});

test("a valid asset links document passes", () => {
  const raw = JSON.stringify([validEntry()]);
  const problems = validateAssetLinksDocument(raw, EXPECTED_PACKAGE);
  assert.deepStrictEqual(problems, []);
});

test("a document targeting another package fails", () => {
  const raw = JSON.stringify([
    validEntry({ target: { package_name: "com.other.app" } })
  ]);
  const problems = validateAssetLinksDocument(raw, EXPECTED_PACKAGE);
  assert.ok(problems.length === 1);
  assert.ok(problems[0].includes("No asset links entry targets"));
});

test("an entry without fingerprints fails", () => {
  const raw = JSON.stringify([validEntry({ target: { sha256_cert_fingerprints: [] } })]);
  const problems = validateAssetLinksDocument(raw, EXPECTED_PACKAGE);
  assert.ok(problems.length === 1);
  assert.ok(problems[0].includes("sha256_cert_fingerprints"));
});

test("an entry with a malformed fingerprint fails", () => {
  const raw = JSON.stringify([validEntry({ target: { sha256_cert_fingerprints: ["not-a-fingerprint"] } })]);
  const problems = validateAssetLinksDocument(raw, EXPECTED_PACKAGE);
  assert.ok(problems.length === 1);
  assert.ok(problems[0].includes("malformed SHA-256 fingerprint"));
});

test("an entry missing the VIEW relation fails", () => {
  const raw = JSON.stringify([validEntry({ relation: ["android.intent.category.BROWSABLE"] })]);
  const problems = validateAssetLinksDocument(raw, EXPECTED_PACKAGE);
  assert.ok(problems.length === 1);
  assert.ok(problems[0].includes("android.intent.action.VIEW"));
});

test("malformed JSON fails with a clear message", () => {
  const problems = validateAssetLinksDocument("<html>not json</html>", EXPECTED_PACKAGE);
  assert.ok(problems.length === 1);
  assert.ok(problems[0].includes("not valid JSON"));
});

test("a non-array document fails", () => {
  const problems = validateAssetLinksDocument(JSON.stringify({ target: {} }), EXPECTED_PACKAGE);
  assert.ok(problems.length === 1);
  assert.ok(problems[0].includes("must be a JSON array"));
});

test("an empty document fails", () => {
  const problems = validateAssetLinksDocument("[]", EXPECTED_PACKAGE);
  assert.ok(problems.length === 1);
  assert.ok(problems[0].includes("no app association is declared"));
});

test("entries for other packages are tolerated alongside the app entry", () => {
  const raw = JSON.stringify([
    { relation: ["delegate_permission/common.get_account"], target: { namespace: "web", site: "https://example.com" } },
    validEntry()
  ]);
  const problems = validateAssetLinksDocument(raw, EXPECTED_PACKAGE);
  assert.deepStrictEqual(problems, []);
});

async function withFixtureServer(handler) {
  const server = http.createServer(handler);
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address();
  return {
    url: `http://127.0.0.1:${port}/.well-known/assetlinks.json`,
    close: () => new Promise((resolve) => server.close(resolve))
  };
}

async function runScript(args) {
  try {
    const { stdout, stderr } = await execFileAsync(process.execPath, [SCRIPT_PATH, ...args], {
      env: { ...process.env, NODE_OPTIONS: "" },
      timeout: 15000
    });
    return { code: 0, stdout, stderr };
  } catch (e) {
    return { code: e.code ?? 1, stdout: e.stdout ?? "", stderr: e.stderr ?? "" };
  }
}

test("CLI end-to-end: a valid deployed file exits 0", async () => {
  const fixture = await withFixtureServer((req, res) => {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify([validEntry()]));
  });
  try {
    const result = await runScript(["--url", fixture.url, "--expect-package", EXPECTED_PACKAGE]);
    assert.strictEqual(result.code, 0, `Expected exit 0, got ${result.code}: ${result.stderr}`);
    assert.ok(result.stdout.includes("Asset links verified"));
  } finally {
    await fixture.close();
  }
});

test("CLI end-to-end: a wrong package exits 1", async () => {
  const fixture = await withFixtureServer((req, res) => {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify([validEntry({ target: { package_name: "com.other.app" } })]));
  });
  try {
    const result = await runScript(["--url", fixture.url, "--expect-package", EXPECTED_PACKAGE]);
    assert.strictEqual(result.code, 1);
    assert.ok(result.stderr.includes("No asset links entry targets"));
  } finally {
    await fixture.close();
  }
});

test("CLI end-to-end: an unreachable domain exits 1 with a deployment hint", async () => {
  // Port 1 is not listening on loopback: fetch must fail, not succeed silently.
  const result = await runScript([
    "--url", "http://127.0.0.1:1/.well-known/assetlinks.json",
    "--expect-package", EXPECTED_PACKAGE
  ]);
  assert.strictEqual(result.code, 1);
  assert.ok(result.stderr.includes("Failed to fetch"));
  assert.ok(result.stderr.includes("vividscout.app"));
});

test("CLI end-to-end: an unexpected fingerprint exits 1", async () => {
  const fixture = await withFixtureServer((req, res) => {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify([validEntry()]));
  });
  try {
    const result = await runScript([
      "--url", fixture.url,
      "--expect-package", EXPECTED_PACKAGE,
      "--expect-fingerprint", "00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF"
    ]);
    assert.strictEqual(result.code, 1);
    assert.ok(result.stderr.includes("expected release SHA-256 fingerprint is NOT"));
  } finally {
    await fixture.close();
  }
});

test("CLI end-to-end: a matching fingerprint exits 0", async () => {
  const fingerprint = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99";
  const fixture = await withFixtureServer((req, res) => {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify([validEntry()]));
  });
  try {
    const result = await runScript([
      "--url", fixture.url,
      "--expect-package", EXPECTED_PACKAGE,
      "--expect-fingerprint", fingerprint
    ]);
    assert.strictEqual(result.code, 0, `Expected exit 0, got ${result.code}: ${result.stderr}`);
    assert.ok(result.stdout.includes("fingerprint match CHECKED"));
  } finally {
    await fixture.close();
  }
});
