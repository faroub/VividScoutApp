# Implementation Plan: Recent Searches Strip for Gemini-Driven Discovery

Implement a 'Recent Searches' horizontal scroll strip positioned directly above the map in `RadarScreen`, persisting the last 5 Gemini-driven discovery queries in Room database with AI sparkle chips, individual delete buttons, a "Clear All" option, and instant query re-execution.

---

## User Review Required

> [!NOTE]
> Based on your preferences from clarifying questions:
> 1. **Visual Style**: Each search chip presents an **AI sparkle icon** (`Icons.Default.AutoAwesome`) alongside the query text, with clean Material 3 styling and subtle tonal badges.
> 2. **Persistence**: Search queries are persisted across app restarts in the **Room database** (`vivid_scout_database`) with a strict capacity cap of 5 most recent unique queries.
> 3. **Management**: Each chip features an **individual remove ('✕') button** with accessible 48dp touch targets, alongside a dedicated **"Clear All"** action button.

---

## Proposed Changes

### 1. Room Database Layer: Entity & DAO

#### `app/src/main/java/com/example/data/local/RecentSearchEntity.kt`
- Define Room entity `@Entity(tableName = "recent_searches")`:
  - `id: String` (Primary Key, unique identifier or UUID)
  - `queryText: String` (Search query or thematic discovery prompt, e.g. "Azulejo viewpoints", "Historic bakeries")
  - `category: String` (Associated category filter, e.g. "all", "sights", "eats")
  - `timestamp: Long` (Timestamp for LRU ordering)

#### `app/src/main/java/com/example/data/local/RecentSearchDao.kt`
- Create Room DAO interface:
  - `@Query("SELECT * FROM recent_searches ORDER BY timestamp DESC LIMIT 5") fun getRecentSearches(): Flow<List<RecentSearchEntity>>`
  - `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSearch(search: RecentSearchEntity)`
  - `@Query("DELETE FROM recent_searches WHERE id = :id") suspend fun deleteSearchById(id: String): Int`
  - `@Query("DELETE FROM recent_searches") suspend fun clearAllSearches(): Int`
  - `@Query("DELETE FROM recent_searches WHERE id NOT IN (SELECT id FROM recent_searches ORDER BY timestamp DESC LIMIT 5)") suspend fun trimOldSearches(): Int`
  - `@Query("SELECT * FROM recent_searches WHERE LOWER(queryText) = LOWER(:queryText) LIMIT 1") suspend fun findByQueryText(queryText: String): RecentSearchEntity?`

#### `app/src/main/java/com/example/data/local/AppDatabase.kt`
- Add `RecentSearchEntity::class` to database `entities` list.
- Bump database version from `2` to `3`.
- Expose `abstract fun recentSearchDao(): RecentSearchDao`.
- Pre-populate 2 initial curated discovery queries (e.g. "Secret Azulejo Viewpoints", "Artisanal Pastel Pastries") on fresh DB creation so the strip is immediately discoverable.

---

### 2. Network & Repository Layer

#### `app/src/main/java/com/example/data/network/GeminiSpotService.kt`
- Add optional `customQuery: String = ""` parameter to `discoverGems(userLat, userLng, category, radiusMeters, customQuery)`.
- If `customQuery.isNotBlank()`, inject contextual search directive into Gemini 3.5 Flash-Lite / Gemini 3.8 Flash prompt:
  - `"User search inquiry / theme: '$customQuery'. Focus discoveries strictly on real, named landmarks matching this inquiry."`

#### `app/src/main/java/com/example/data/repository/ScoutGemRepository.kt`
- Inject `recentSearchDao: RecentSearchDao` into `ScoutGemRepository`.
- Expose `val recentSearches: Flow<List<RecentSearchEntity>> = recentSearchDao.getRecentSearches()`.
- Add `suspend fun recordRecentSearch(queryText: String, category: String = "all")`:
  - Check for existing search with same text (case-insensitive); update timestamp or insert new.
  - Trim table to keep only the latest 5 records.
- Add `suspend fun removeRecentSearch(id: String)` and `suspend fun clearAllRecentSearches()`.
- Update `syncLiveOsmGems` to accept optional `customQuery: String = ""` and automatically record non-blank searches.

---

### 3. ViewModel Layer

#### `app/src/main/java/com/example/ui/viewmodel/ScoutViewModel.kt`
- Expose `recentSearches: StateFlow<List<RecentSearchEntity>>` via `recentSearchDao` / `gemRepository`.
- Expose `searchQuery: StateFlow<String>` (already existing).
- Implement:
  - `fun executeRecentSearch(search: RecentSearchEntity)`: sets `searchQuery`, selects category if applicable, records the search with updated timestamp, and triggers `scrapeOsmGems(userLat, userLng, customQuery = search.queryText)`.
  - `fun submitGeminiDiscoveryQuery(queryText: String)`: triggers Gemini discovery for custom text inquiry and records to Room.
  - `fun deleteRecentSearch(id: String)`: deletes specific query chip.
  - `fun clearAllRecentSearches()`: purges all recent search history.

---

### 4. UI Layer: Recent Searches Horizontal Scroll Strip

#### `app/src/main/java/com/example/ui/components/RecentSearchStrip.kt`
- Create a dedicated Jetpack Compose component:
  - Horizontal scrolling `LazyRow` styled to sit smoothly above the map.
  - **Chip styling**:
    - Rounded pills with subtle surface container background (`SurfaceContainerLowest`) and subtle teal/primary border.
    - **AI Sparkle Icon** (`Icons.Default.AutoAwesome` in `BrandTeal`) at the start.
    - **Query text** with clear typography and max character truncation.
    - **Individual Dismiss Button**: Small `IconButton` with `Icons.Default.Close` (`14.dp` icon with minimum 48x48dp interactive touch target).
    - `Modifier.testTag("recent_search_chip_${search.id}")` and `Modifier.testTag("delete_recent_search_${search.id}")`.
  - **Clear All action**:
    - Compact text/icon button at the tail: "Clear" with `Icons.Default.DeleteSweep` or `Clear All` chip.
  - **Inline Prompt Input**:
    - Optional small "+" or custom search pill allowing travelers to type a quick discovery prompt right from the strip.

#### `app/src/main/java/com/example/ui/screens/RadarScreen.kt`
- Add `recentSearches: List<RecentSearchEntity>`, `onSelectRecentSearch: (RecentSearchEntity) -> Unit`, `onDeleteRecentSearch: (String) -> Unit`, and `onClearAllRecentSearches: () -> Unit` to `RadarScreen` parameters.
- Place `RecentSearchStrip` directly above the map container (above item 3: Main Visual Component Map/Radar).

#### `app/src/main/java/com/example/MainActivity.kt`
- Collect `recentSearches by viewModel.recentSearches.collectAsState()`.
- Pass `recentSearches`, `onSelectRecentSearch = { viewModel.executeRecentSearch(it) }`, `onDeleteRecentSearch = { viewModel.deleteRecentSearch(it) }`, and `onClearAllRecentSearches = { viewModel.clearAllRecentSearches() }` into `RadarScreen`.

---

## Verification Plan

### Automated Unit Tests
- Add unit tests in `app/src/test/java/com/example/data/local/RecentSearchDaoTest.kt`:
  - Verify inserting queries caps at 5 records and respects LRU ordering (most recent first).
  - Verify duplicate query update re-elevates to top without duplicating rows.
  - Verify deleting single query by ID.
  - Verify clearing all recent searches.
  - Run via `gradle :app:testDebugUnitTest`.

### Build Verification
- Execute `compile_applet` to verify compilation and resource binding.
- Verify smooth rendering in Streaming Android Emulator.
