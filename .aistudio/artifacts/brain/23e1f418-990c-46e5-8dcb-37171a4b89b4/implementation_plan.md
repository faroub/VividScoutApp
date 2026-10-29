# Auto-Cleanup Service in ScoutGemRepository

Implement a background auto-cleanup service within `ScoutGemRepository` and `ScoutDao` that maintains a lean local Room database by periodically pruning transient cached gems older than 30 days and removing un-saved POIs situated beyond a 100km radius from the user's active GPS position.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following requirements were confirmed during planning:

- **Confirmed Decision 1 (Data Protection)**: Strictly safeguard all user-authored and curated records (`isSaved == 1`, `isDiscovered == 1`, and `isOfflineDraft == 1`). Auto-cleanup applies solely to transient cached/scouted spots (`isSaved == 0 AND isDiscovered == 0 AND isOfflineDraft == 0`).
- **Confirmed Decision 2 (Home Location Anchor)**: Calculate the 100km radius boundary dynamically against the user's latest recorded GPS coordinates (with Lisbon as default fallback if GPS is not yet acquired).
- **Confirmed Decision 3 (Trigger Mechanism)**: Execute auto-cleanup silently in the background on application startup within `ScoutViewModel`, with automatic re-evaluation if the user relocates substantially (>50km).

---

## 1. Overview & Architecture

Over time, exploring different districts, cities, or running repeated Gemini AI and OpenStreetMap queries can accumulate hundreds of transient POI entries in the SQLite database. 

The **Auto-Cleanup Service** provides:
1. **Time-Based Cache Eviction**: Prunes transient un-saved entries whose `timestamp` is older than 30 days (`System.currentTimeMillis() - 30.days`).
2. **Geographic Proximity Pruning**: Computes spherical distance from the user's current GPS location and eliminates transient un-saved spots beyond 100 kilometers.
3. **Zero Data Loss Guarantee**: Preserves user bookmarks, verified achievements, and pending drafts regardless of age or distance.
4. **Telemetry & Observability**: Returns a structured `AutoCleanupReport` detailing the number of expired spots and distant spots pruned, exposed via `StateFlow` and Logcat.

---

## 2. Technical Implementation Details

### Database Layer (`ScoutDao.kt`)
- Add query:
  ```kotlin
  @Query("DELETE FROM scout_spots WHERE isSaved = 0 AND isDiscovered = 0 AND isOfflineDraft = 0 AND timestamp < :cutoffTimestamp")
  suspend fun deleteExpiredSpots(cutoffTimestamp: Long): Int
  ```
- Add batch deletion query by ID:
  ```kotlin
  @Query("DELETE FROM scout_spots WHERE isSaved = 0 AND isDiscovered = 0 AND isOfflineDraft = 0 AND id IN (:spotIds)")
  suspend fun deleteSpotsByIds(spotIds: List<String>): Int
  ```

### Repository Layer (`ScoutGemRepository.kt`)
- Define `AutoCleanupReport`:
  ```kotlin
  data class AutoCleanupReport(
      val timestamp: Long = System.currentTimeMillis(),
      val expiredCount: Int = 0,
      val distantCount: Int = 0,
      val totalRemoved: Int = 0,
      val anchorLat: Double = 0.0,
      val anchorLng: Double = 0.0
  )
  ```
- Implement `performAutoCleanup(userLat: Double, userLng: Double, maxAgeDays: Int = 30, maxRadiusKm: Double = 100.0): AutoCleanupReport`:
  1. Calculate `cutoffTimestamp = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)`.
  2. Execute `deleteExpiredSpots(cutoffTimestamp)`.
  3. Retrieve un-saved candidates and filter those where geodesic distance to `(userLat, userLng)` exceeds `100,000` meters.
  4. Prune distant IDs via `deleteSpotsByIds`.
  5. Update `_lastCleanupReport` StateFlow.

### ViewModel & Startup Integration (`ScoutViewModel.kt`)
- In `ScoutViewModel.init`:
  - Launch non-blocking background coroutine (with 2-second delay to prioritize initial UI render).
  - Execute `gemRepository.performAutoCleanup(coords.first, coords.second)`.
  - Listen to `userLocation` updates to trigger distant cleanup when moving to a new region (>50km shift).

---

## 3. Verification & Testing

1. **Unit & Robolectric Testing**:
   - Add test case verifying expired spots (>30 days old) with `isSaved = 0` are deleted.
   - Verify spots >30 days old with `isSaved = 1` or `isDiscovered = 1` are NOT deleted.
   - Verify spots >100km away with `isSaved = 0` are removed, while nearby spots (<100km) remain intact.
2. **Build Validation**:
   - Compile using `compile_applet`.
   - Run tests using `gradle :app:testDebugUnitTest`.
