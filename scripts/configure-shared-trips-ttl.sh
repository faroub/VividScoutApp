#!/usr/bin/env bash
#
# Configures and verifies the Firestore TTL policies that physically delete
# expired shared-trip data:
#
#   shared_trips.expireAt               — offset 0 (documents expire at expireAt)
#   shared_trip_publish_requests.createdAt — offset 90d (idempotency markers are
#                                           retained well beyond the 30-day trip
#                                           lifetime so that late retries of a
#                                           publish request still replay instead
#                                           of publishing a duplicate trip)
#
# TTL policies are a server-side Firestore feature. They are NOT part of
# firebase.json (which only deploys rules and indexes), so physical deletion of
# expired shared trips must be configured once per Firebase project with the
# gcloud CLI (or the Google Cloud Console) — this script automates that.
#
# gcloud prerequisites:
#   * Install the gcloud CLI: https://cloud.google.com/sdk/docs/install
#   * Authenticate:  gcloud auth login
#   * The account needs IAM permissions on the project:
#       - datastore.indexes.admin (or roles/datastore.owner) to update TTL
#         policies, since Firestore TTL management uses the App Engine/
#         Cloud Datastore IAM surface
#       - roles/datastore.viewer to list/verify the policy
#     (roles/datastore.user is NOT sufficient for TTL administration)
#
# Important: Firestore TTL deletion is asynchronous and only removes documents
# whose TTL timestamp is in the past. Access control does NOT depend on it:
# firestore.rules deny reads of shared_trips as soon as expireAt <=
# request.time, even while the document still physically exists pending TTL
# processing. The app must never rely on immediate deletion.
#
# Usage:
#   ./scripts/configure-shared-trips-ttl.sh [PROJECT_ID]
#
# PROJECT_ID is optional; the active gcloud project is used when omitted.
# The Firestore database can be overridden with FIRESTORE_DATABASE_ID
# (same convention as the Cloud Functions and the Android app; empty or
# unset means "(default)"). The script always passes it to gcloud explicitly
# so the policy is never configured against a different database by accident.

set -euo pipefail

PROJECT_ID="${1:-}"
DATABASE_ID="${FIRESTORE_DATABASE_ID:-}"

TRIP_COLLECTION_GROUP="shared_trips"
TRIP_TTL_FIELD="expireAt"

REQUEST_COLLECTION_GROUP="shared_trip_publish_requests"
REQUEST_TTL_FIELD="createdAt"
# 90 days: idempotency markers must outlive any plausible client retry window
# (trips themselves live 30 days), but should not accumulate forever.
REQUEST_TTL_OFFSET="2160h"

if [[ -z "${DATABASE_ID}" ]]; then
  DATABASE_ID="(default)"
fi

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
echo "Database:             ${DATABASE_ID}"
echo "Trip TTL:             ${TRIP_COLLECTION_GROUP}.${TRIP_TTL_FIELD}"
echo "Request TTL:          ${REQUEST_COLLECTION_GROUP}.${REQUEST_TTL_FIELD} (+${REQUEST_TTL_OFFSET})"
echo

# 1. Verify authenticated access to the project before mutating anything.
if ! gcloud firestore list --project="${PROJECT_ID}" >/dev/null 2>&1; then
  echo "ERROR: cannot access Firestore of project '${PROJECT_ID}'." >&2
  echo "Authenticate first (gcloud auth login) and make sure the project id is correct." >&2
  echo "The account also needs the datastore.indexes.admin / datastore.viewer roles" >&2
  echo "to manage and verify TTL policies." >&2
  exit 1
fi

enable_ttl() {
  local collection_group="$1"
  local field="$2"
  local offset="${3:-}"

  echo "Configuring TTL policy on ${collection_group}.${field} ..."
  local -a cmd=(gcloud firestore fields ttls update "${field}"
    --collection-group="${collection_group}"
    --database="${DATABASE_ID}"
    --project="${PROJECT_ID}"
    --enable-ttl)
  if [[ -n "${offset}" ]]; then
    cmd+=(--expiration-offset="${offset}")
  fi

  if ! "${cmd[@]}"; then
    echo "ERROR: failed to enable the TTL policy on ${collection_group}.${field}." >&2
    echo "The command output above shows the gcloud error. Common causes:" >&2
    echo "  - the account lacks datastore.indexes.admin on the project" >&2
    echo "  - the field does not exist or is not a timestamp field" >&2
    echo "  - another field is already the TTL field of this collection group" >&2
    return 1
  fi
}

# Verifies a TTL policy is present and enabled (ACTIVE or CREATING) for the
# given collection group + field, using the supported listing command.
verify_ttl() {
  local collection_group="$1"
  local field="$2"

  local output
  output="$(gcloud firestore fields ttls list \
      --collection-group="${collection_group}" \
      --database="${DATABASE_ID}" \
      --project="${PROJECT_ID}" \
      --format=json 2>/dev/null || true)"

  if [[ -z "${output}" || "${output}" == "[]" ]]; then
    echo "ERROR: no TTL policy found for ${collection_group}.${field}." >&2
    return 1
  fi

  if ! printf '%s' "${output}" | grep -q "fields/${field}\b"; then
    echo "ERROR: TTL listing does not contain ${collection_group}.${field}:" >&2
    echo "${output}" >&2
    return 1
  fi

  if printf '%s' "${output}" | grep -q '"NEEDS_REPAIR"'; then
    echo "ERROR: TTL policy for ${collection_group}.${field} is in NEEDS_REPAIR state." >&2
    echo "See https://cloud.google.com/firestore/docs/ttl#monitor_a_ttl_policy" >&2
    return 1
  fi

  if ! printf '%s' "${output}" | grep -q '"ACTIVE"'; then
    echo "ERROR: TTL policy for ${collection_group}.${field} is not enabled." >&2
    echo "The policy has not reached the ACTIVE state; re-run this script once the" >&2
    echo "long-running operation completes, or inspect the policy in the Cloud Console." >&2
    echo "${output}" >&2
    return 1
  fi
}

enable_ttl "${TRIP_COLLECTION_GROUP}" "${TRIP_TTL_FIELD}"
enable_ttl "${REQUEST_COLLECTION_GROUP}" "${REQUEST_TTL_FIELD}" "${REQUEST_TTL_OFFSET}"

echo
echo "Verifying TTL policies ..."
if ! verify_ttl "${TRIP_COLLECTION_GROUP}" "${TRIP_TTL_FIELD}"; then
  exit 1
fi
if ! verify_ttl "${REQUEST_COLLECTION_GROUP}" "${REQUEST_TTL_FIELD}"; then
  exit 1
fi

echo
echo "TTL policies active. Expired documents are deleted asynchronously;"
echo "rules already deny reads at expireAt, so expired links are inaccessible immediately."
echo "Note: ${REQUEST_COLLECTION_GROUP} documents are deleted 90 days after creation,"
echo "which is far beyond the 30-day trip lifetime, so late publish retries still replay."
