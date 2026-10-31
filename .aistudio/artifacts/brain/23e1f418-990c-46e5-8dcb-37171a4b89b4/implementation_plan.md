# Global City Consistency & Engine Refactoring

Unify the city model and engine components behind clean, profile-aware abstractions so non-Lisbon global cities do not silently fall back to Lisbon elevation models, curated landmarks, or demo seed data, while keeping coordinate-based Overpass/AI spot discovery fully functional worldwide.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed based on your choices:

- **Confirmed Decision 1**: Store the global city catalog as a typed JSON asset at `res/raw/global_cities.json` and parse it via Moshi inside `GlobalCityCatalog`.
- **Confirmed Decision 2**: Display explicit "Not available for this city yet" notices and disable/annotate elevation and landmark controls when an unsupported city (like Berlin or Tokyo) is selected.
- **Confirmed Decision 3**: Provide city profiles and interfaces through a central `CityProfileRegistry` managed by `AppContainer` and injected into feature ViewModels.

---

## 1. Overview & Core Concept

Currently, selecting a global city outside Lisbon in the UI still uses `LisbonElevationModel` and `LisbonLandmarkCatalog`. This refactoring introduces a `CityProfile` abstraction, extracts interfaces `ElevationModel` and `LandmarkCatalog`, and loads global cities dynamically from JSON.

- **Supported Profile (Lisbon)**: Full feature set including local elevation profiles, curated Lisbon landmarks, and rich seed data.
- **Unsupported Global Cities (Berlin, Tokyo, Paris, etc.)**: Coordinate-based Overpass & Gemini AI discovery continue working seamlessly, while city-specific features (elevation graph, curated landmarks) show an honest "Not available for this city yet" state.

---

## 2. User Experience & Visual Design

- **Global City Selector**: Loads city entries dynamically from `res/raw/global_cities.json`.
- **Elevation Sparkline & Route Planner**: When a route is planned in Lisbon, elevation profiles render as usual. In an unsupported city, the elevation card displays an informative banner: `"Elevation profile not available for [City Name] yet"`.
- **Landmarks & Sights**: In Lisbon, curated historical landmarks display with custom imagery and lore. In unsupported cities, generic/OSM landmarks are discovered via Overpass/AI, while curated lists clearly communicate that curated local landmark packs are Lisbon-only.

---

## 3. Key Product Decisions & Trade-Offs

- **Abstraction via Interfaces (`ElevationModel`, `LandmarkCatalog`)**:
  - *Chosen Approach*: `LisbonElevationModel` implements `ElevationModel`, `LisbonLandmarkCatalog` implements `LandmarkCatalog`. `NullElevationModel` and `EmptyLandmarkCatalog` provide safe null-object fallbacks for unsupported cities.
  - *Why*: Prevents null pointer exceptions and avoids hardcoded `if (city == "Lisbon")` checks scattered across Composables and ViewModels.
- **JSON Asset Storage (`res/raw/global_cities.json`)**:
  - *Chosen Approach*: Move ~260 lines of hardcoded Kotlin objects into a clean JSON array resource parsed by Moshi at runtime.
  - *Why*: Reduces codebase bloat, enables easy addition of new cities without recompiling Kotlin code, and improves startup performance.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                    res/raw/global_cities.json               │
└──────────────────────────────┬──────────────────────────────┘
                               │ Moshi Json
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                   GlobalCityCatalog                         │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                  CityProfileRegistry                        │
│  - Lisbon -> LisbonCityProfile (LisbonElevation, Landmark)  │
│  - Other -> GenericCityProfile (NullElevation, EmptyLandmark)│
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                   AppContainer / ViewModels                 │
│  (ExploreViewModel, OfflineMapsViewModel, MainViewModel)    │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                Jetpack Compose UI Screens                   │
│  (Displays "Not available for this city yet" when null)    │
└──────────────────────────────┴──────────────────────────────┘
```

### Component & Entity Changes

1. **New Interfaces & Null Implementations**:
   - `interface ElevationModel`: `buildElevationProfile(pathPoints: List<Pair<Double, Double>>): RouteElevationProfile?`
   - `object NullElevationModel : ElevationModel`: returns `null`.
   - `class LisbonElevationModel : ElevationModel`
   - `interface LandmarkCatalog`: `getLandmarks(): List<LandmarkEntity>`
   - `object EmptyLandmarkCatalog : LandmarkCatalog`: returns empty list.
   - `class LisbonLandmarkCatalog : LandmarkCatalog`
2. **`CityProfile` Data Model & Registry**:
   - `data class CityProfile(val id: String, val name: String, val country: String, val centerLat: Double, val centerLng: Double, val elevationModel: ElevationModel, val landmarkCatalog: LandmarkCatalog, val isCuratedSupported: Boolean)`
   - `class CityProfileRegistry(context: Context)`
3. **JSON Asset**:
   - `app/src/main/res/raw/global_cities.json` containing city definitions.
4. **Unit Test Coverage**:
   - `app/src/test/java/com/example/CityProfileRegistryTest.kt`: Asserts Lisbon profile provides non-null elevation profile and non-empty landmarks, while an unknown city (e.g. Berlin) returns `isCuratedSupported = false`, `null` elevation profile, and empty landmark catalog.
