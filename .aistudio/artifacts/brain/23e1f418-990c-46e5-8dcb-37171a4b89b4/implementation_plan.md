# Pre-Scout City Boundary Algorithm & Proactive Gem Prefetching

Implement an intelligent **Pre-scout Algorithm** that monitors the user's past search category frequencies (`RecentSearchEntity`) and saved bookmark history (`ScoutSpotEntity`), detects when the device enters a new city boundary or switches global regions (>10km movement), and proactively pre-caches **5 high-rated gems (rating >= 4.5)** within a **10km radius** directly into Room DB, featuring a non-intrusive top-bar notification ticker.

> [!IMPORTANT] User Preferences Confirmed in Phase 1
> - **Category Analysis Engine**: Ranks categories by combining frequency from past search history (`RecentSearchEntity`) and saved bookmark categories (`isSaved = true`), prioritizing the user's top-rated interests.
> - **Boundary Detection**: Triggers whenever the GPS moves to a new city/region boundary (>10km from last anchor) or when a new city is selected in the Global Destination picker.
> - **Notification Ticker**: Displays a sleek top-bar ticker (e.g. `✨ Pre-scouted 5 top gems in Paris!`) with a quick badge in Profile & TopBar.

---

## 1. Overview & Architectural Goal

- **Goal**: Ensure zero cold-boot wait times and instant offline availability of top tourist spots whenever traveling to a new city or switching regions.
- **Algorithm Strategy**:
  1. **Category Weight Evaluator**: Computes weight `W(cat) = 2 * count(recent_searches) + 3 * count(saved_bookmarks)`.
  2. **City Anchor Tracker**: Stores `lastPreScoutLat`, `lastPreScoutLng`, and `lastPreScoutCity`. If distance to current GPS or selected city > 10,000m, triggers Pre-scout.
  3. **High-Rated Prefetch Pipeline**: Queries Overpass API & Wikipedia REST / Gemini Services for 5 top-rated spots (>= 4.5 stars) matching top weighted categories within 10km radius.
  4. **Room DB Ingestion**: Saves entities with `statusBadge = "Pre-scouted Local"` and `offlineCached = true`.
  5. **UI Notification Ticker**: Displays ticker banner at top of Explore screen and updates top-bar pill with pre-scout count badge.

---

## 2. User Experience & Visual Design

- **Top-Bar Ticker Banner**:
  - Displays a vibrant gradient pill: `✨ Pre-scouted 5 top gems in Tokyo (Sights & Eats)`
  - Includes a "View Gems" tap target that filters Explore feed to newly pre-scouted places.
- **Pre-Scout Badge on Cards**:
  - Pre-scouted cards showcase a distinct teal/purple badge: `✨ PRE-SCOUTED 10KM`.

---

## 3. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                     PreScoutEngine (Coroutines)                        │
│ - Category Weight Evaluator: RecentSearches + Saved Spot Categories    │
│ - Geodesic City Anchor Listener (>10km delta or City Selection)        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      Proactive Gem Prefetcher                          │
│ - Fetches 5 high-rated gems (rating >= 4.5) within 10km radius         │
│ - Resolves authentic Wikipedia/Commons photos via LocationPhotoResolver│
│ - Inserts into Room DB (scoutDao.insertSpots)                          │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      Top-Bar Ticker & UI Flow                          │
│ - StateFlow<PreScoutBannerState> in ScoutViewModel                     │
│ - Animated ticker in VividScoutTopBar & ExploreScreen                  │
└────────────────────────────────────────────────────────────────────────┘
```

- **Step-by-Step Implementation Steps**:
  1. **Category Weight Evaluator (`PreScoutEngine.kt`)**: Implement class that queries `RecentSearchDao` and `ScoutDao` to calculate top preferred categories.
  2. **Pre-scout ViewModel Flow in `ScoutViewModel.kt`**:
     - Monitor `userCoordinates` and `selectedGlobalCity`.
     - When delta distance > 10,000m or new city selected: trigger `executePreScoutPipeline(lat, lng, cityName)`.
  3. **High-Rated Prefetch Query**: Query Overpass API & Wikipedia REST for 5 top-rated spots matching top categories around current coordinates.
  4. **Top-Bar Ticker UI**: Update `VividScoutTopBar.kt` and `ExploreScreen.kt` with the pre-scout notification ticker banner.
  5. **Verification**: Run `compile_applet`.
