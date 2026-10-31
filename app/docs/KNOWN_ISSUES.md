# Known Issues & Technical Notes

## Summary
The application has successfully completed all verification passes, full gradle build (`assembleDebug`, `assembleRelease`), and 100% test suite execution (106 unit tests passing).

## Known Technical Notes / Limitations
1. **Experimental Coroutines API**:
   - `OfflineMapDownloadService.kt` uses experimental coroutines flow operators which trigger compiler warning notes. These are stable in production execution.
2. **Android Lifecycle & Material Deprecations**:
   - Minor deprecation warnings exist for older Compose/Lifecycle APIs (e.g., `LocalLifecycleOwner`, `LinearProgressIndicator` parameter overloads, `Divider` renamed to `HorizontalDivider`), which will be migrated in future upstream updates.
3. **Room Database Backup Exclusion**:
   - As per privacy and security requirements, `vivid_scout_database` and associated WAL, SHM, and shader files are strictly excluded from Android cloud backups and device-to-device transfers to prevent sensitive location history leaks. Non-sensitive UI preferences remain included.
