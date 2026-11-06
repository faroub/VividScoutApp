/**
 * Test Suite for scripts/configure-shared-trips-ttl.sh
 *
 * The TTL policy is configured outside the repository (server-side Firestore
 * feature, not firebase.json), so this test guards the deployment artifact
 * itself: the script must exist, be executable, and configure + verify the
 * shared_trips.expireAt TTL policy with the supported gcloud commands.
 */

const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

const SCRIPT_PATH = path.join(__dirname, "..", "configure-shared-trips-ttl.sh");

test("TTL configuration script exists and is executable", () => {
  assert.ok(fs.existsSync(SCRIPT_PATH), "configure-shared-trips-ttl.sh must exist in scripts/");
  const mode = fs.statSync(SCRIPT_PATH).mode;
  assert.ok(mode & 0o111, "configure-shared-trips-ttl.sh must be executable");
});

test("TTL script configures and verifies the shared_trips.expireAt policy", () => {
  const script = fs.readFileSync(SCRIPT_PATH, "utf8");

  assert.match(script, /TTL_FIELD="expireAt"/, "TTL must target the expireAt field");
  assert.match(script, /COLLECTION_GROUP="shared_trips"/, "TTL must target the shared_trips collection group");
  assert.match(script, /fields ttl update "\$\{TTL_FIELD\}"/, "script must run the TTL update command");
  assert.match(script, /collection-group="\$\{COLLECTION_GROUP\}"/, "update must scope the collection group");
  assert.match(script, /fields ttl describe "\$\{TTL_FIELD\}"/, "script must verify the active policy");
});
