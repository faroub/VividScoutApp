# ScoutGemRepository Offline-First Architecture

Design and implementation plan for `ScoutGemRepository`, providing offline-first data orchestration with Room as the single source of truth and background OpenStreetMap Overpass API sync.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Confirmed Decision**: **Room is the single source of truth**. `ScoutGemRepository` exposes a reactive `Flow<List<ScoutGem>>` directly backed by Room database queries (`ScoutDao`). Live OpenStreetMap Overpass web scraping executes asynchronously in the background, mapping OSM nodes into `ScoutSpotEntity` rows and inserting them into Room. The app remains 100% functional offline even without network connectivity.

---

## 1. Overview & Core Concept

- **Goal**: Implement `ScoutGemRepository` to manage offline-first reads, live web scraping from Overpass API mirrors, and local user bookmarking / spot discovery persistence.
- **Key Value**: Guarantees zero UI freezing or missing map pins during network outages or slow API responses. As soon as live OSM nodes are scraped, Room emits updated `ScoutGem` lists automatically to the UI.

---

## 2. User Experience & Visual Design

- **Offline-First Flow**:
  1. On launch or screen load, `ScoutGemRepository.observeGems(userLat, userLng)` emits pre-seeded or cached Room database gems immediately.
  2. The user can view, search, filter, and bookmark gems offline.
  3. When online, invoking `syncLiveOsmGems(userLat, userLng)` triggers an asynchronous Overpass API query fetching viewpoints, bakeries, shortcuts, and historic ruins around current GPS coordinates.
  4. Scraped OSM items are inserted into Room with `isOfflineCached = true`, automatically updating the vector map canvas and list views.

---

## 3. Key Product Decisions & Trade-Offs

- **Chosen Approach**:
  - **`ScoutGemRepository.kt`**:
    - `observeGems(userLat, userLng): Flow<List<ScoutGem>>`: Subscribes to `ScoutDao.getAllSpotsFlow()`, transforming `ScoutSpotEntity` items into `ScoutGem` instances.
    - `syncLiveOsmGems(userLat, userLng): Result<Int>`: Fetches nodes via `OverpassRetrofitClient.fetchNearbyNodes()`, maps nodes to `ScoutSpotEntity`, and upserts into `ScoutDao`.
    - `toggleBookmark(gemId: String)`: Toggles `isSaved` in Room.
    - `markDiscovered(gemId: String)`: Sets `isDiscovered = true` in Room.
- **Why**: Keeps UI reactive, ensures seamless offline persistence, and eliminates race conditions.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                      ScoutMapViewModel                      │
│   • Subscribes to ScoutGemRepository.observeGems()          │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    ScoutGemRepository                       │
│   • Source of Truth: Room ScoutDao.getAllSpotsFlow()         │
│   • Background Sync: OverpassRetrofitClient                 │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
     Read/Write│                              │Background Sync
               ▼                              ▼
┌──────────────────────────────┐┌─────────────────────────────┐
│      Room Database           ││   Overpass API (OSM)        │
│  (scout_spots Table)         ││   (Live Viewpoints/Shops)   │
└──────────────────────────────┘└─────────────────────────────┘
```

### Implementation Checklist
1. **`ScoutGemRepository.kt`**: Create repository class in `com.example.data.repository`.
2. **`ScoutViewModel.kt` & `ScoutMapViewModel.kt`**: Wire `ScoutGemRepository` for reactive updates.
3. **Verify Build**: Run `compile_applet` and unit tests.
