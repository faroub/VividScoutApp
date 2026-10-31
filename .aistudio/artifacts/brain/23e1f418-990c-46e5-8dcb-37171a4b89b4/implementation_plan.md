# Implementation Plan - Privacy Backup and Data Extraction Rules

## User Intent
Configure Android backup and data extraction rules (`backup_rules.xml` and `data_extraction_rules.xml`) to exclude the location-annotated Room database (`vivid_scout_database`, WAL, SHM, and shader journals) from cloud backups and device-to-device transfers, while allowing non-sensitive UI preferences (`sharedpref`) to be backed up.

---

## User Review Required

> [!IMPORTANT]
> - Room database `vivid_scout_database`, `vivid_scout_database-wal`, `vivid_scout_database-shm`, and `vivid_scout_database-shader` will be excluded from backups and device transfers.
> - Shared preferences will be included for seamless user setting restoration across device reinstalls.

---

## Proposed Changes

### 1. Update `app/src/main/res/xml/backup_rules.xml`
- Include shared preferences.
- Exclude Room database files (`vivid_scout_database`, WAL, SHM, shader).

### 2. Update `app/src/main/res/xml/data_extraction_rules.xml`
- Configure `<cloud-backup>` and `<device-transfer>` sections identically to include shared preferences and exclude Room database files.
- Resolve TODO comments.

---

## Verification Plan

### Automated Verification
1. Run `compile_applet` to verify XML syntax and build integrity.
2. Run `gradle :app:testDebugUnitTest` to verify all unit tests pass.
