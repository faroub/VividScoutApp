/**
 * Test Suite for scripts/configure-shared-trips-ttl.sh
 *
 * The TTL policy is configured outside the repository (server-side Firestore
 * feature, not firebase.json). The previous version of this test only grepped
 * the script for command strings, so it happily passed while the script used
 * the non-existent "gcloud firestore fields ttl" command group and a
 * non-existent "describe" subcommand.
 *
 * These tests place a fake `gcloud` executable earlier in PATH which records
 * its argv and returns scripted results, so the script's real behavior — exact
 * subcommands, flags, failure handling and exit codes — is exercised end to end.
 */

const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawnSync } = require("node:child_process");

const SCRIPT_PATH = path.join(__dirname, "..", "configure-shared-trips-ttl.sh");

const FAKE_GCLOUD = `#!/usr/bin/env bash
echo "$*" >> "\${GCLOUD_LOG}"
case "$1" in
  config)
    # gcloud config get-value project
    printf '%s\\n' "\${FAKE_PROJECT:-}"
    ;;
  firestore)
    case "$2" in
      list)
        [[ -n "\${FAKE_AUTH_OK:-}" ]] || exit 13
        ;;
      fields)
        case "$3" in
          ttls)
            case "$4" in
              update)
                [[ -n "\${FAKE_UPDATE_FAIL:-}" ]] && exit 1
                exit 0
                ;;
              list)
                # Emit the policy for the requested collection group.
                for arg in "$@"; do
                  case "$arg" in
                    --collection-group=shared_trips)
                      printf '%s\\n' "[{\\"name\\": \\"projects/fake/databases/\${FAKE_DB_OUT:-default}/collectionGroups/shared_trips/fields/expireAt\\", \\"ttlPolicy\\": {\\"state\\": \\"\${FAKE_TTL_STATE:-ACTIVE}\\", \\"expirationOffset\\": \\"0s\\"}}]"
                      exit 0
                      ;;
                    --collection-group=shared_trip_publish_requests)
                      printf '%s\\n' "[{\\"name\\": \\"projects/fake/databases/\${FAKE_DB_OUT:-default}/collectionGroups/shared_trip_publish_requests/fields/createdAt\\", \\"ttlPolicy\\": {\\"state\\": \\"\${FAKE_TTL_STATE:-ACTIVE}\\", \\"expirationOffset\\": \\"2160h\\"}}]"
                      exit 0
                      ;;
                  esac
                done
                printf '%s\\n' "\${FAKE_TTL_LIST:-[]}"
                ;;
            esac
            ;;
        esac
        ;;
    esac
    ;;
esac
exit 0
`;

let workDir;
let fakeDir;
let logPath;

function setupFakeGcloud() {
  workDir = fs.mkdtempSync(path.join(os.tmpdir(), "ttl-test-"));
  fakeDir = path.join(workDir, "bin");
  fs.mkdirSync(fakeDir);
  logPath = path.join(workDir, "gcloud-invocations.log");
  fs.writeFileSync(path.join(fakeDir, "gcloud"), FAKE_GCLOUD, { mode: 0o755 });
}

function runScript({ args = [], env = {} } = {}) {
  const childEnv = {
    ...process.env,
    PATH: `${fakeDir}${path.delimiter}${process.env.PATH}`,
    GCLOUD_LOG: logPath,
    ...env,
  };
  return spawnSync("bash", [SCRIPT_PATH, ...args], {
    env: childEnv,
    encoding: "utf8",
  });
}

function invocations() {
  if (!fs.existsSync(logPath)) return [];
  return fs
    .readFileSync(logPath, "utf8")
    .split("\n")
    .filter((line) => line.length > 0);
}

function updateInvocations() {
  return invocations()
    .map((line) => line.trim().split(/\s+/))
    .filter((argv) => argv[0] === "firestore" && argv[1] === "fields" && argv[2] === "ttls" && argv[3] === "update");
}

function listInvocations() {
  return invocations()
    .map((line) => line.trim().split(/\s+/))
    .filter((argv) => argv[0] === "firestore" && argv[1] === "fields" && argv[2] === "ttls" && argv[3] === "list");
}

test("script exists and is executable", () => {
  assert.ok(fs.existsSync(SCRIPT_PATH), "configure-shared-trips-ttl.sh must exist in scripts/");
  const mode = fs.statSync(SCRIPT_PATH).mode;
  assert.ok(mode & 0o111, "configure-shared-trips-ttl.sh must be executable");
});

test("script uses the supported fields ttls command group, not the broken fields ttl one", () => {
  const script = fs.readFileSync(SCRIPT_PATH, "utf8");
  assert.doesNotMatch(script, /fields ttl[^s]/, "must not use the non-existent 'fields ttl' group");
  assert.match(script, /fields ttls update/, "must enable TTL with 'fields ttls update'");
  assert.match(script, /fields ttls list/, "must verify with 'fields ttls list'");
});

test("success path: enables both TTL policies and verifies them with the exact subcommand", () => {
  setupFakeGcloud();
  const result = runScript({
    args: ["fake-project"],
    env: { FAKE_AUTH_OK: "1", FAKE_TTL_STATE: "ACTIVE" },
  });

  assert.strictEqual(result.status, 0, `script should succeed, stderr: ${result.stderr}`);

  const updates = updateInvocations();
  assert.strictEqual(updates.length, 2, "should run one update per policy");

  const tripUpdate = updates.find((argv) => argv.includes("--collection-group=shared_trips"));
  assert.ok(tripUpdate, "must configure the shared_trips collection group");
  assert.deepStrictEqual(
    tripUpdate.slice(0, 4),
    ["firestore", "fields", "ttls", "update"],
    "exact subcommand must be 'gcloud firestore fields ttls update'",
  );
  assert.ok(tripUpdate.includes("expireAt"), "shared_trips TTL field must be expireAt");
  assert.ok(tripUpdate.includes("--enable-ttl"), "TTL enablement must be requested explicitly");
  assert.ok(tripUpdate.includes("--project=fake-project"), "project id must be passed");

  const requestUpdate = updates.find(
    (argv) => argv.includes("--collection-group=shared_trip_publish_requests"),
  );
  assert.ok(requestUpdate, "must configure the shared_trip_publish_requests collection group");
  assert.ok(requestUpdate.includes("createdAt"), "request TTL field must be createdAt");
  assert.ok(
    requestUpdate.includes("--expiration-offset=2160h"),
    "request retention must outlive the 30-day trip lifetime (90d)",
  );

  const lists = listInvocations();
  assert.strictEqual(lists.length, 2, "should verify both policies");
  assert.ok(
    lists.every(
      (argv) =>
        argv.some((a) => a.startsWith("--collection-group=")) &&
        argv.some((a) => a.startsWith("--format=")),
    ),
    "verification must list the policy for each collection group",
  );
});

test("default database is passed explicitly as (default)", () => {
  setupFakeGcloud();
  const result = runScript({ args: ["fake-project"], env: { FAKE_AUTH_OK: "1" } });

  assert.strictEqual(result.status, 0, `stderr: ${result.stderr}`);
  const updates = updateInvocations();
  assert.ok(
    updates.every((argv) => argv.includes("--database=(default)")),
    "default database must be explicit so the policy is never applied to a surprise database",
  );
});

test("explicit FIRESTORE_DATABASE_ID is forwarded to gcloud", () => {
  setupFakeGcloud();
  const result = runScript({
    args: ["fake-project"],
    env: { FAKE_AUTH_OK: "1", FIRESTORE_DATABASE_ID: "shared-trips-db" },
  });

  assert.strictEqual(result.status, 0, `stderr: ${result.stderr}`);
  const updates = updateInvocations();
  assert.ok(
    updates.every((argv) => argv.includes("--database=shared-trips-db")),
    "named database id must be passed to both update and verification",
  );
});

test("missing gcloud CLI fails with a useful error", () => {
  workDir = fs.mkdtempSync(path.join(os.tmpdir(), "ttl-test-"));
  const emptyBin = path.join(workDir, "empty-bin");
  fs.mkdirSync(emptyBin);
  // PATH points only at an empty directory; resolve bash absolutely so the
  // test itself can still run.
  const result = spawnSync("/bin/bash", [SCRIPT_PATH, "fake-project"], {
    env: { ...process.env, PATH: emptyBin },
    encoding: "utf8",
  });

  assert.notStrictEqual(result.status, 0, "must exit non-zero");
  assert.match(result.stderr, /gcloud CLI is not installed/, "must explain the missing CLI");
});

test("missing project id fails with a useful error", () => {
  setupFakeGcloud();
  const result = runScript({ env: { FAKE_AUTH_OK: "1", FAKE_PROJECT: "" } });

  assert.notStrictEqual(result.status, 0, "must exit non-zero");
  assert.match(result.stderr, /no Firebase\/GCP project selected/, "must explain the missing project");
});

test("authentication failure fails with a useful error", () => {
  setupFakeGcloud();
  const result = runScript({ args: ["fake-project"] });

  assert.notStrictEqual(result.status, 0, "must exit non-zero");
  assert.match(result.stderr, /cannot access Firestore/, "must explain the auth failure");
  assert.deepStrictEqual(updateInvocations(), [], "must not attempt any update before auth succeeds");
});

test("TTL update failure fails with a useful error", () => {
  setupFakeGcloud();
  const result = runScript({
    args: ["fake-project"],
    env: { FAKE_AUTH_OK: "1", FAKE_UPDATE_FAIL: "1" },
  });

  assert.notStrictEqual(result.status, 0, "must exit non-zero");
  assert.match(result.stderr, /failed to enable the TTL policy/, "must explain the update failure");
  assert.deepStrictEqual(listInvocations(), [], "must not verify after a failed update");
});

test("policy absent from the listing fails verification", () => {
  setupFakeGcloud();
  // The fake returns a listing entry without any state for the requested
  // collection group, i.e. the policy does not exist there.
  const result = runScript({
    args: ["fake-project"],
    env: { FAKE_AUTH_OK: "1", FAKE_TTL_STATE: "MISSING" },
  });

  assert.notStrictEqual(result.status, 0, "must exit non-zero when the policy is missing");
  assert.match(
    result.stderr,
    /no TTL policy found|does not contain|not enabled/,
    "must name the missing policy",
  );
});

test("policy in NEEDS_REPAIR state fails verification", () => {
  setupFakeGcloud();
  const result = runScript({
    args: ["fake-project"],
    env: { FAKE_AUTH_OK: "1", FAKE_TTL_STATE: "NEEDS_REPAIR" },
  });

  assert.notStrictEqual(result.status, 0, "must exit non-zero");
  assert.match(result.stderr, /NEEDS_REPAIR/, "must report the needs-repair state");
});

test("policy that is not yet ACTIVE fails verification", () => {
  setupFakeGcloud();
  const result = runScript({
    args: ["fake-project"],
    env: { FAKE_AUTH_OK: "1", FAKE_TTL_STATE: "CREATING" },
  });

  assert.notStrictEqual(result.status, 0, "must exit non-zero when the policy is not enabled");
  assert.match(result.stderr, /not enabled/, "must explain that the policy has not reached ACTIVE");
});

test("script keeps access expiration independent of physical TTL deletion", () => {
  const script = fs.readFileSync(SCRIPT_PATH, "utf8");
  assert.match(
    script,
    /expireAt <=\n# request\.time/,
    "must document that security rules, not TTL deletion, enforce expiry",
  );
});
