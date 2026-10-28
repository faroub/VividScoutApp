# Implementation Plan - Elevation Profile Sparkline in RoutePlannerSheet

Implement an elevation profile sparkline chart within `RoutePlannerSheet` that leverages cached path points to visualize walking intensity, climbing effort, and descents across Lisbon's steep hills and calçadas.

## User Decisions & Clarifications
- **Location**: Dedicated card positioned between the route metrics badges and the turn-by-turn directions list.
- **Styling**: Smooth sparkline curve with gradient stroke—BrandCoral for inclines/climbs transitioning to BrandTeal for descents and flat ground, plus a subtle vertical under-curve fill and min/max elevation labels.

---

## Proposed Changes

### 1. Offline Topography & Elevation Data Model
#### `app/src/main/java/com/example/engine/VectorWalkGraph.kt` & `LisbonElevationModel.kt`
- Add realistic topographic elevation coordinates (in meters above sea level) to all `WalkNode` entries in Lisbon's walking graph:
  - Riverfront (Cais das Colunas, Praça do Comércio, Ribeira das Naus): 2m - 8m
  - Baixa valley (Rua Augusta, Rossio, Praça da Figueira): 12m - 18m
  - Chiado (Rua Garrett, Armazéns do Chiado, Praça Luís de Camões): 38m - 52m
  - Carmo (Largo do Carmo): 46m - 54m
  - Bairro Alto & Santa Catarina (Calçada do Combro, Miradouro): 65m - 75m
  - Miradouro de São Pedro de Alcântara & Glória: 80m - 92m
  - Alfama slope & Sé Cathedral: 24m - 46m
  - Miradouro de Santa Luzia & Portas do Sol: 68m - 76m
  - Castelo de São Jorge summit: 104m
- Create `LisbonElevationModel`:
  - `getElevationForCoordinate(lat: Double, lng: Double): Double`: Inverse-distance weighted interpolation from nearest known topographic points.
  - Generates continuous elevation samples along `trail.pathPoints`.

#### `app/src/main/java/com/example/engine/OfflineRouteEngine.kt`
- Define data classes:
  - `ElevationPoint(val distanceMeters: Double, val elevationMeters: Double, val slopeGradePct: Double)`
  - `RouteElevationProfile(val points: List<ElevationPoint>, val totalAscentMeters: Double, val totalDescentMeters: Double, val minElevationMeters: Double, val maxElevationMeters: Double, val intensityLabel: String)`
- Attach `val elevationProfile: RouteElevationProfile?` to `OfflineWalkingTrail`.
- Automatically compute elevation profile when building the A* walking path.

---

### 2. UI Layer: `ElevationProfileSparkline.kt` (New Composable)
- **Canvas Sparkline Rendering**:
  - Draws cubic Bézier smoothed sparkline path mapping distance $x$ to elevation $y$.
  - Dual-color gradient stroke: Coral (`BrandCoral`) on climbs, Teal (`BrandTeal`) on flats and downhill stretches.
  - Semi-transparent gradient area fill beneath the curve (`BrandCoral` to transparent).
  - Min/Max elevation labels (e.g. `14 m` min, `88 m` peak).
  - Start (Origin) and End (Destination) elevation anchor dots.
- **Walking Intensity & Metric Badges**:
  - Total Ascent: `↑ 42 m` (Coral pill).
  - Total Descent: `↓ 16 m` (Teal pill).
  - Intensity classification tag:
    - "Gentle" (ascent < 15m)
    - "Moderate Incline" (15m - 35m)
    - "Challenging Calçada Climb" (ascent > 35m, stone stairs warning)
  - Interactive touch scrubber to inspect elevation and incline grade at any point along the walk.

---

### 3. RoutePlannerSheet Integration
#### `app/src/main/java/com/example/ui/components/RoutePlannerSheet.kt`
- Insert `ElevationProfileCard(elevationProfile = plannedRoute.elevationProfile)` directly between the route metric chips and the expandable turn-by-turn directions.
- Seamlessly collapses/expands with the bottom sheet state.

---

## Verification Plan

### Automated Tests
1. **`ElevationProfileTest.kt`** (Robolectric / Unit Test):
   - Test elevation calculation along known route (e.g., Praça do Comércio at ~5m to Miradouro de São Pedro de Alcântara at ~85m).
   - Verify `totalAscentMeters` is positive and accurately reflects Lisbon's incline.
   - Verify intensity classification labels ("Challenging Calçada Climb" vs "Gentle").
2. Execute tests: `gradle :app:testDebugUnitTest`.

### Compilation & Manual Verification
1. Run `compile_applet` to verify clean build.
2. Open RoutePlannerSheet for a route from Baixa to Bairro Alto / Castelo.
3. Confirm the elevation sparkline card displays between metrics and step directions with coral-to-teal gradient and ascent/descent stats.
