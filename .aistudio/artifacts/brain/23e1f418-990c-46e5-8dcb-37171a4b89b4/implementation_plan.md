# Global Spot Discovery & Dynamic Worldwide Photo Engine

Expand the app from a Lisbon-specific radar to a global location discovery experience that works anywhere in the world, automatically acquiring the user's GPS position or allowing global city search, paired with dynamic worldwide photo resolution from Wikipedia REST and OpenStreetMap APIs.

> [!IMPORTANT] User Preferences Confirmed in Phase 1
> - **Global Location Strategy**: Device GPS with global search bar and instant city selector (e.g., Paris, Tokyo, New York, Sydney, Rio, Lisbon).
> - **Worldwide Photo Engine**: Dynamic Wikipedia REST API (`/geosearch` and `/page/summary`) combined with OpenStreetMap tags globally, with smart fallback to category/landmark keywords.
> - **First-Launch Behavior**: Automatically detect the user's real-time GPS location on first launch and populate nearby spots dynamically anywhere on Earth.

---

## 1. Overview & Core Concept

- **What It Does**: Transforms the app into a global exploration companion. Users can auto-discover authentic points of interest, hidden spots, historic landmarks, and community updates near their current GPS coordinates anywhere in the world, or search and jump to any global metropolis.
- **Target Audience**: Explorers, urban travelers, local scouts, and photography enthusiasts worldwide.
- **Key Value**: Provides instant, authentic location photos, real geographic data, and localized community updates anywhere on Earth without hardcoded city limitations.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **First Launch / GPS Auto-Location**: App prompts for standard Android location permission (`ACCESS_FINE_LOCATION`). Upon grant, it fetches the user's real latitude/longitude and retrieves real nearby landmarks and spots via Wikipedia Geosearch & OpenStreetMap Overpass API.
  2. **Global City Search & Region Picker**: A prominent top bar allows typing any city or location (e.g., "Kyoto", "Berlin", "San Francisco"). Selecting a city smoothly centers the radar map and loads local spots for that destination.
  3. **Spot Detail & Authentic Worldwide Media**: Tapping any spot displays authentic high-resolution imagery resolved dynamically from Wikipedia page image APIs, spot description, geographic coordinates, and localized community status updates (with 24h decay).
  4. **Mesh Sync Community Feed**: View and submit community status reports (e.g., crowd level, scenic updates) embedded in spot detail or in the dedicated Community tab.

- **Visual Identity & Theme**:
  - **Aesthetic Direction**: Sleek, modern exploration radar with vibrant Material 3 dynamic styling.
  - **Color Palette**: Modern dark/light posture with deep night radar backgrounds (`#0B0F19`), neon cyan/emerald accents (`#00F5D4`, `#10B981`), and warm alert tones.
  - **Typography**: Clean high-contrast sans-serif hierarchy for maximum readability on outdoor mobile devices.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Global Location Provider**:
  - *Chosen Approach*: FusedLocationProviderClient (Android Location Services) + Nominatim/OpenStreetMap Search API for city search.
  - *Why*: Provides fast, power-efficient GPS fixes everywhere without requiring proprietary paid mapping API keys.

- **Decision 2: Dynamic Photo Engine**:
  - *Chosen Approach*: Dynamic Wikipedia REST API (`/w/api.php?action=query&list=geosearch` & `/api/rest_v1/page/summary/{title}`) combined with Wikidata/OSM image tags.
  - *Why*: Guarantees high-quality, authentic, public-domain photographs for millions of real-world points of interest globally.

- **Decision 3: Room Database & Disk Caching**:
  - *Chosen Approach*: Cache fetched spot details and photo URLs in local Room database paired with Coil disk caching.
  - *Why*: Enables seamless offline viewing, fast cold boot performance, and minimal network overhead.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Jetpack Compose UI                             │
│ ┌──────────────────────┐ ┌─────────────────────┐ ┌───────────────────┐ │
│ │ Global Radar View    │ │ City Search Bar     │ │ Spot Detail View  │ │
│ └──────────┬───────────┘ └──────────┬──────────┘ └─────────┬─────────┘ │
└────────────┼────────────────────────┼──────────────────────┼───────────┘
             │                        │                      │
             ▼                        ▼                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           Spot Discovery ViewModel                      │
│ - Coordinates & Current Selected Region                                 │
│ - StateFlow<LocationUiState> & StateFlow<SpotDetailUiState>             │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      Global Spot Repository                             │
│ ┌──────────────────────────────────┐ ┌────────────────────────────────┐ │
│ │  Wikipedia GeoSearch REST API    │ │  OpenStreetMap Overpass API    │ │
│ └─────────────────┬────────────────┘ └───────────────┬────────────────┘ │
└───────────────────┼──────────────────────────────────┼──────────────────┘
                    │                                  │
                    ▼                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      Room Database & Coil Cache                         │
│ - Local Spot Entity Storage & URL Persistence                           │
│ - Simulated Mesh Sync Community Status Storage                          │
└─────────────────────────────────────────────────────────────────────────┘
```

- **Data Models**:
  - `Spot`: ID, title, description, latitude, longitude, photoUrl, thumbnailUrl, distanceMeters, category, wikipediaPageTitle, lastUpdated.
  - `CommunityStatus`: ID, spotId, statusText, badgeType, timestamp, authorScoutHandle, decayHours (24h expiry).
  - `CityRegion`: Name, country, latitude, longitude.

- **Step-by-Step Implementation Plan**:
  1. **Permissions & Manifest**: Declare `INTERNET`, `ACCESS_FINE_LOCATION`, and `ACCESS_COARSE_LOCATION` in `AndroidManifest.xml`.
  2. **Location Engine**: Integrate Android FusedLocationProviderClient for real GPS coordinates with runtime permission handling.
  3. **Worldwide Geo API Services**: Implement Wikipedia REST Geosearch and Summary client to dynamically fetch real landmark titles, summaries, and high-res thumbnail image URLs anywhere on Earth based on lat/lng.
  4. **Global City Search**: Add city search repository using Nominatim geocoding API to query any city worldwide and center the radar map.
  5. **Room Caching & Mesh Sync**: Store fetched spots and community updates locally with 24-hour decay logic.
  6. **UI Updates**: Upgrade radar feed, top city search bar, spot detail bottom sheet/screen, and community feed tab.
  7. **Build & Verify**: Compile with `compile_applet` and verify zero build errors.
