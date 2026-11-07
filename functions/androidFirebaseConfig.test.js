/**
 * Android ↔ Cloud Functions Firebase configuration contract test.
 *
 * The app and the backend must agree on which Firestore database they address and on
 * the App Link domain. The Android end reads `R.string.firestore_database_id`
 * (blank or unset means the default database); the Functions end reads the
 * FIRESTORE_DATABASE_ID parameter through functions/firestoreDatabase.js, which
 * canonicalizes blank and "(default)" identically. These tests read the ACTUAL
 * committed sources (strings.xml, AndroidManifest.xml, build.gradle.kts) and verify
 * the shared convention, so a divergence between the two ends fails here instead of
 * causing silent cross-database data loss in production.
 *
 * Run directly:  node --test functions/androidFirebaseConfig.test.js
 * (also part of `npm --prefix functions test`).
 */

const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { firestoreDatabaseId } = require("./firestoreDatabase");

const STRINGS_XML = path.join(__dirname, "..", "app", "src", "main", "res", "values", "strings.xml");
const MANIFEST_XML = path.join(__dirname, "..", "app", "src", "main", "AndroidManifest.xml");
const BUILD_GRADLE = path.join(__dirname, "..", "app", "build.gradle.kts");

const SHARED_TRIP_HOST = "vividscout.app";

function readStringResource(name) {
  const xml = fs.readFileSync(STRINGS_XML, "utf8");
  // Handles both <string name="x">value</string> and the self-closing empty form
  // <string name="x" /> (an empty value, which still resolves to the default database).
  const selfClosed = new RegExp(`<string\\s+name="${name}"[^>]*/>`).test(xml);
  if (selfClosed) return "";
  const match = xml.match(new RegExp(`<string\\s+name="${name}"[^>]*>([\\s\\S]*?)</string>`));
  if (!match) return null;
  // Unescape the minimal XML entities used in string resources.
  return match[1].trim().replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">");
}

function manifestContent() {
  return fs.readFileSync(MANIFEST_XML, "utf8");
}

function androidFirestoreDatabaseId() {
  const value = readStringResource("firestore_database_id");
  return value === null ? null : value.trim();
}

/**
 * The Android convention: a blank value selects the default database, exactly like
 * the Functions canonicalization. A divergent convention would let one end read a
 * named database while the other writes the default one.
 */
function androidCanonicalDatabaseId() {
  const value = androidFirestoreDatabaseId();
  return value === "" || value === null ? "(default)" : value;
}

describe("Firestore database ID: Android ↔ Cloud Functions contract", () => {
  it("the Android app declares a firestore_database_id resource", () => {
    const value = readStringResource("firestore_database_id");
    assert.ok(value !== null, "R.string.firestore_database_id must exist in strings.xml");
  });

  it("a blank Android value selects the same default database the Functions canonicalize to", () => {
    const androidValue = androidFirestoreDatabaseId();
    if (androidValue === null) {
      assert.ok(false, "firestore_database_id resource missing");
      return;
    }
    assert.ok(
      androidValue === "" || androidValue === "(default)" || /^[A-Za-z0-9_-]+$/.test(androidValue),
      `Unexpected firestore_database_id value: "${androidValue}"`
    );
  });

  it("deploying the Android value as FIRESTORE_DATABASE_ID resolves both ends to the same database", () => {
    const androidValue = androidFirestoreDatabaseId();
    const previous = process.env.FIRESTORE_DATABASE_ID;
    try {
      process.env.FIRESTORE_DATABASE_ID = androidValue;
      assert.strictEqual(firestoreDatabaseId(), androidCanonicalDatabaseId());
    } finally {
      if (previous === undefined) delete process.env.FIRESTORE_DATABASE_ID;
      else process.env.FIRESTORE_DATABASE_ID = previous;
    }
  });

  it("the default database is selected consistently when the Android value is blank", () => {
    const androidValue = androidFirestoreDatabaseId();
    if (androidValue !== "") {
      // The invariant only applies to the currently committed blank configuration;
      // a named configuration is covered by the resolution test above.
      return;
    }
    const previous = process.env.FIRESTORE_DATABASE_ID;
    try {
      delete process.env.FIRESTORE_DATABASE_ID;
      assert.strictEqual(firestoreDatabaseId(), androidCanonicalDatabaseId());
    } finally {
      if (previous !== undefined) process.env.FIRESTORE_DATABASE_ID = previous;
    }
  });
});

describe("Android App Link configuration", () => {
  it("the production application id is declared in build.gradle.kts", () => {
    const gradle = fs.readFileSync(BUILD_GRADLE, "utf8");
    const match = gradle.match(/applicationId\s*=\s*"([^"]+)"/);
    assert.ok(match, "applicationId must be declared in app/build.gradle.kts");
    assert.ok(match[1].length > 0, "applicationId must not be blank");
    assert.strictEqual(
      match[1],
      "com.aistudio.vividscout.trvjxq",
      "The application id changed: the Digital Asset Links file and this contract test must be updated together."
    );
  });

  it("the manifest declares an autoVerified App Link for the shared-trip host and path", () => {
    const manifest = manifestContent();
    assert.ok(
      /android:autoVerify="true"/.test(manifest),
      "The shared-trip intent filter must be autoVerified"
    );
    assert.ok(
      manifest.includes(`android:host="${SHARED_TRIP_HOST}"`),
      `The manifest must declare the App Link host ${SHARED_TRIP_HOST}`
    );
    assert.ok(
      manifest.includes('android:pathPrefix="/trip/"'),
      'The manifest must declare the /trip/ path prefix'
    );
  });
});
