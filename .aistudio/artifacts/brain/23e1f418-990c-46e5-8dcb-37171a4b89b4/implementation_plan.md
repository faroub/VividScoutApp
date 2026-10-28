# Category Filtering Extension & Map Screen Category Chips

Extend `ScoutGemRepository` query logic to support category filtering across all major POI types (`all`, `touristic`, `dining`, `historical`, `events`, `gems`), update `OverpassRetrofitClient` to query category-specific OpenStreetMap nodes, and render a floating horizontal category chip ribbon on the main Radar map screen.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed in Phase 1:

- **Confirmed Decision 1 (Category Types)**: Supported category filter chips: **All**, **Touristic** (sights & attractions), **Dining** (bakeries & restaurants), **Historical** (monuments & ruins), **Events** (cultural venues & parks), and **Gems** (secret spots & shortcuts).
- **Confirmed Decision 2 (Map Screen Layout)**: Floating horizontal category chip ribbon positioned near the top of the Radar map view below the top bar.
- **Confirmed Decision 3 (Overpass Fetch Integration)**: Selecting a category chip updates map markers immediately and triggers a category-specific Overpass QL fetch targeted at the user's active GPS coordinates.

---

## 1. Overview & Core Concept

- **What It Does**: Expands VividScout into a complete tourist guide by categorizing both hidden gems and major tourist attractions, historic monuments, dining venues, cultural event spaces, and secret passes. Users can tap floating category chips directly on the main Radar map screen to isolate specific venue types and fetch live OSM data for that exact category.
- **Target Audience**: Tourists, visitors, and local explorers seeking immediate situational awareness of specific POI categories around their current location.
- **Key Value**: Delivers instant category-driven filtering on both the map view and the explore feed while leveraging real-time OpenStreetMap Overpass queries.

---

## 2. User Experience & Visual Design

- **Floating Category Chip Ribbon (Radar Screen)**:
  - Horizontal scrollable row of styled M3 Filter Chips floating gracefully near the top of the Radar map screen.
  - Active chip uses a vibrant container background with a high-contrast label and category icon; inactive chips use a semi-transparent surface container with subtle border elevation.
  - Icons per category:
    - **All**: `NearMe`
    - **Touristic**: `PhotoCamera` / `Attractions`
    - **Dining**: `Restaurant` / `Utensils`
    - **Historical**: `Castle` / `Explore`
    - **Events**: `Event` / `Celebration`
    - **Gems**: `Verified` / `Sparkles`
- **Dynamic Map Marker Filtering**:
  - Selecting a category chip instantly filters pin markers rendered on `MapCanvasView` / `GoogleMapView`.
  - Displays a brief pulse toast when scraping category-specific Overpass POIs (e.g., `"Scanning nearby Dining POIs on OpenStreetMap..."`).

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Category Mapping & Room Query Parameters**:
  - *Chosen Approach*: Add `category: String = "all"` parameter to `ScoutGemRepository.observeGems`, `ScoutRepository`, `ScoutDao.getSpotsByCategory`, and `OverpassRetrofitClient.buildCategoryQuery`.
  - *Why*: Allows seamless reactive flow filtering directly from Room DB into `StateFlow<List<ScoutSpotEntity>>`.

- **Decision 2: Overpass QL Category Query Expansion**:
  - *Chosen Approach*: Map category types to specific Overpass QL key-value filters:
    - `touristic`: `node["tourism"]`, `way["tourism"]`
    - `dining`: `node["amenity"="restaurant"]`, `node["shop"="bakery"]`, `node["amenity"="cafe"]`
    - `historical`: `node["historic"]`, `way["historic"]`
    - `events`: `node["amenity"="arts_centre"]`, `node["leisure"="park"]`, `node["tourism"="museum"]`
    - `gems`: `way["highway"="steps"]`, `node["tourism"="viewpoint"]`

---

## 4. Technical Architecture & Data Strategy

### System Architecture & Data Flow

```
┌────────────────────────────────────────────────────────┐
│           Radar Screen Category Chip Ribbon            │
│   [ All ] [ Touristic ] [ Dining ] [ Historical ] ...  │
└──────────────────────────┬─────────────────────────────┘
                           │ Category Selected ("dining")
                           ▼
┌────────────────────────────────────────────────────────┐
│                    ScoutViewModel                      │
│  • Updates _selectedCategory = "dining"               │
│  • Triggers scrapeOsmGems(lat, lng, category="dining") │
└──────────────┬───────────────────────────┬─────────────┘
               │                           │
               ▼                           ▼
┌──────────────────────────────┐ ┌───────────────────────┐
│     ScoutGemRepository       │ │  OverpassRetrofit     │
│   • DAO query filtering      │ │  • Category QL query  │
│   • 50km radius validation   │ │  • Map DTO -> Entities│
└──────────────┬───────────────┘ └───────────┬───────────┘
               │                            │
               └───────────────┬────────────┘
                               ▼
┌────────────────────────────────────────────────────────┐
│                Room DB (scout_spots)                   │
│        Filtered StateFlow -> Radar Map Pins            │
└────────────────────────────────────────────────────────┘
```

### File Modification Checklist

1. **`ScoutDao.kt`**:
   - Add `@Query("SELECT * FROM scout_spots WHERE category = :category ORDER BY timestamp DESC")`
     `fun getSpotsByCategory(category: String): Flow<List<ScoutSpotEntity>>`

2. **`OverpassRetrofitClient.kt`**:
   - Update `buildQuery(lat, lng, radiusMeters, category: String = "all")` to generate category-targeted Overpass QL filters.
   - Update `fetchNearbyNodes` and `generateNearbyFallbackSpots` to pass and apply category tags.

3. **`ScoutGemRepository.kt` & `ScoutRepository.kt`**:
   - Update `syncLiveOsmGems(userLat, userLng, radiusMeters, category: String = "all")` to query and process category-filtered POIs.

4. **`ScoutViewModel.kt`**:
   - Add `selectCategory(category: String)` method that updates `selectedCategory` and invokes `scrapeOsmGems(lat, lng, category)`.

5. **`RadarScreen.kt`**:
   - Insert floating horizontal `RadarCategoryFilterRibbon` near the top of the screen overlaid on the map canvas.
   - Bind chip clicks to `onCategorySelected`.

6. **Unit Tests (`ScoutRepositoryTest.kt` / `OverpassRetrofitClientTest.kt`)**:
   - Add unit tests verifying category QL query building and DAO category filtering.
