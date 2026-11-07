#!/usr/bin/env node
/**
 * Verifies the deployed Digital Asset Links file for the VividScout Android App Link.
 *
 *   https://vividscout.app/.well-known/assetlinks.json
 *
 * The manifest (app/src/main/AndroidManifest.xml) declares an autoVerified App Link
 * for https://vividscout.app/trip/{tripId}. Association is proven ONLY by the asset
 * links file deployed at the domain itself — it must contain the production Android
 * application id and the SHA-256 fingerprint of the release signing certificate.
 *
 * This script checks the DEPLOYED file. It never invents or assumes fingerprints:
 * it compares against --expect-fingerprint only when one is supplied.
 *
 * Usage:
 *   node scripts/verify-assetlinks.js
 *   node scripts/verify-assetlinks.js --url https://example.com/.well-known/assetlinks.json
 *   node scripts/verify-assetlinks.js --expect-fingerprint AB:CD:...
 *
 * Exits 0 when the deployed file matches, 1 on any failure (connectivity, malformed
 * JSON, wrong package, missing fingerprints).
 *
 * The file is maintained by the web hosting deployment of vividscout.app (outside
 * this repository); this script is the verification contract between the two.
 */

const fs = require("node:fs");
const path = require("node:path");

const DEFAULT_URL = "https://vividscout.app/.well-known/assetlinks.json";
const BUILD_GRADLE = path.join(__dirname, "..", "app", "build.gradle.kts");

function applicationIdFromBuildFile() {
  const gradle = fs.readFileSync(BUILD_GRADLE, "utf8");
  const match = gradle.match(/applicationId\s*=\s*"([^"]+)"/);
  return match ? match[1] : null;
}

function parseArgs(argv) {
  const args = { url: DEFAULT_URL, expectPackage: null, expectFingerprint: null };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--url") args.url = argv[++i];
    else if (arg === "--expect-package") args.expectPackage = argv[++i];
    else if (arg === "--expect-fingerprint") args.expectFingerprint = argv[++i];
    else if (arg === "--help" || arg === "-h") {
      console.log(`Usage: node scripts/verify-assetlinks.js [--url URL] [--expect-package ID] [--expect-fingerprint SHA256]`);
      process.exit(0);
    }
  }
  if (!args.expectPackage) args.expectPackage = applicationIdFromBuildFile();
  return args;
}

/**
 * Validates the raw asset links document against the expected Android application id.
 * Returns an array of human-readable problems (empty = valid).
 *
 * Given the expected package, the document must contain at least one entry whose
 * target is that Android app and which carries at least one SHA-256 certificate
 * fingerprint. Entries for OTHER packages or websites are tolerated (a domain may
 * host several), but a document with no entry for the app is an association failure.
 */
function validateAssetLinksDocument(rawText, expectedPackage) {
  const problems = [];
  let entries;
  try {
    entries = JSON.parse(rawText);
  } catch (e) {
    return [`The asset links document is not valid JSON: ${e.message}`];
  }
  if (!Array.isArray(entries)) {
    return ["The asset links document must be a JSON array."];
  }
  if (entries.length === 0) {
    return ["The asset links document is an empty array: no app association is declared."];
  }

  const appEntries = entries.filter(
    (entry) =>
      entry &&
      entry.target &&
      entry.target.namespace === "android_app" &&
      entry.target.package_name === expectedPackage
  );
  if (appEntries.length === 0) {
    const declared = entries
      .map((entry) => (entry && entry.target && entry.target.package_name) || "?")
      .filter((name, index, names) => name !== "?" || names.indexOf(name) === index);
    return [
      `No asset links entry targets the Android application id "${expectedPackage}" (declared: ${JSON.stringify(declared)}).`
    ];
  }

  for (const entry of appEntries) {
    const fingerprints = entry.target.sha256_cert_fingerprints;
    if (!Array.isArray(fingerprints) || fingerprints.length === 0) {
      problems.push(
        `The asset links entry for ${expectedPackage} carries no sha256_cert_fingerprints: the release certificate association is missing.`
      );
    } else if (!fingerprints.every((fp) => typeof fp === "string" && /^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){31}$/.test(fp))) {
      problems.push(
        `The asset links entry for ${expectedPackage} contains a malformed SHA-256 fingerprint: ${JSON.stringify(fingerprints)}.`
      );
    }
    if (!Array.isArray(entry.relation) || !entry.relation.includes("android.intent.action.VIEW")) {
      problems.push(
        `The asset links entry for ${expectedPackage} does not declare an android.intent.action.VIEW relation.`
      );
    }
  }
  return problems;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (!args.expectPackage) {
    console.error("Could not determine the expected application id from app/build.gradle.kts; pass --expect-package.");
    process.exit(1);
  }

  let response;
  try {
    response = await fetch(args.url, { redirect: "follow" });
  } catch (e) {
    console.error(`Failed to fetch ${args.url}: ${e.message}`);
    console.error("The Digital Asset Links file must be deployed by the web hosting of vividscout.app.");
    process.exit(1);
  }
  if (!response.ok) {
    console.error(`Fetching ${args.url} returned HTTP ${response.status} ${response.statusText}.`);
    process.exit(1);
  }
  const rawText = await response.text();

  const problems = validateAssetLinksDocument(rawText, args.expectPackage);
  for (const problem of problems) console.error(`Asset links verification failed: ${problem}`);

  if (args.expectFingerprint) {
    let entries;
    try {
      entries = JSON.parse(rawText);
    } catch (e) {
      console.error("Asset links verification failed: the document is not valid JSON.");
      process.exit(1);
    }
    const fingerprints = (Array.isArray(entries) ? entries : [])
      .filter((entry) => entry && entry.target && entry.target.package_name === args.expectPackage)
      .flatMap((entry) => (entry.target.sha256_cert_fingerprints || []).map((fp) => String(fp).toUpperCase()));
    const normalized = args.expectFingerprint.trim().toUpperCase();
    if (!fingerprints.includes(normalized)) {
      console.error(
        `Asset links verification failed: the expected release SHA-256 fingerprint is NOT among the fingerprints for ${args.expectPackage}.`
      );
      process.exit(1);
    }
  }

  if (problems.length > 0) {
    console.error(`Asset links verification failed for ${args.url} and package ${args.expectPackage}.`);
    process.exit(1);
  }

  console.log(
    `Asset links verified: ${args.url} associates the domain with the Android application id ${args.expectPackage} ` +
      `(fingerprint match${args.expectFingerprint ? " CHECKED" : " not requested; deployment must be compared manually"}).`
  );
  process.exit(0);
}

if (require.main === module) {
  main().catch((e) => {
    console.error(`Unexpected error: ${e.message}`);
    process.exit(1);
  });
}

module.exports = { validateAssetLinksDocument, applicationIdFromBuildFile, parseArgs, DEFAULT_URL };
