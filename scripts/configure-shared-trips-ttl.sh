#!/usr/bin/env bash
#
# Configures and verifies the Firestore TTL policy that physically deletes expired
# shared-trip documents (shared_trips.expireAt).
#
# TTL policies are a server-side Firestore feature. They are NOT part of
# firebase.json (which only deploys rules and indexes), so physical deletion of
# expired shared trips must be configured once per Firebase project with the
# gcloud CLI (or the Google Cloud Console) — this script automates that.
#
# Important: Firestore TTL deletion is asynchronous. Access control does NOT
# depend on it: firestore.rules denies reads of shared_trips as soon as
# expireAt <= request.time, even while the document still physically exists
# pending TTL processing. The app must never rely on immediate deletion.
#
# Usage:
#   ./scripts/configure-shared-trips-ttl.sh [PROJECT_ID]
#
# If PROJECT_ID is omitted, the active gcloud project is used.

set -euo pipefail

PROJECT_ID="${1:-}"
COLLECTION_GROUP="shared_trips"
TTL_FIELD="expireAt"

if ! command -v gcloud >/dev/null 2>&1; then
  echo "ERROR: gcloud CLI is not installed or not on PATH." >&2
  echo "Install it from https://cloud.google.com/sdk/docs/install, then re-run this script." >&2
  exit 1
fi

if [[ -z "${PROJECT_ID}" ]]; then
  PROJECT_ID="$(gcloud config get-value project 2>/dev/null || true)"
fi

if [[ -z "${PROJECT_ID}" ]]; then
  echo "ERROR: no Firebase/GCP project selected." >&2
  echo "Pass the project id explicitly: ./scripts/configure-shared-trips-ttl.sh YOUR_PROJECT_ID" >&2
  echo "or select one first: gcloud config set project YOUR_PROJECT_ID" >&2
  exit 1
fi

echo "Project:              ${PROJECT_ID}"
echo "Collection group:     ${COLLECTION_GROUP}"
echo "TTL field:            ${TTL_FIELD}"
echo

# 1. Verify authenticated access to the project before mutating anything.
if ! gcloud firestore list --project="${PROJECT_ID}" >/dev/null 2>&1; then
  echo "ERROR: cannot access Firestore of project '${PROJECT_ID}'." >&2
  echo "Authenticate first (gcloud auth login) and make sure the project id is correct." >&2
  exit 1
fi

# 2. Configure the TTL policy: documents with expireAt in the past are deleted by Firestore.
echo "Configuring TTL policy on ${COLLECTION_GROUP}.${TTL_FIELD} ..."
gcloud firestore fields ttl update "${TTL_FIELD}" \
  --collection-group="${COLLECTION_GROUP}" \
  --project="${PROJECT_ID}"

# 3. Verify the policy is active.
echo
echo "Verifying TTL policy ..."
if ! gcloud firestore fields ttl describe "${TTL_FIELD}" \
     --collection-group="${COLLECTION_GROUP}" \
     --project="${PROJECT_ID}"; then
  echo "ERROR: TTL policy verification failed for ${COLLECTION_GROUP}.${TTL_FIELD}." >&2
  echo "Check the Google Cloud Console (Firestore > TTL policy) or the command output above." >&2
  exit 1
fi

echo
echo "TTL policy active. Expired ${COLLECTION_GROUP} documents are deleted asynchronously;"
echo "rules already deny reads at expireAt, so expired links are inaccessible immediately."
