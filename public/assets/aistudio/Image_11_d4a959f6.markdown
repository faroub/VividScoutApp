---
name: VividScout
colors:
  surface: '#f9f9ff'
  surface-dim: '#d3daef'
  surface-bright: '#f9f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f1f3ff'
  surface-container: '#e9edff'
  surface-container-high: '#e1e8fd'
  surface-container-highest: '#dce2f7'
  on-surface: '#141b2b'
  on-surface-variant: '#5a403f'
  inverse-surface: '#293040'
  inverse-on-surface: '#edf0ff'
  outline: '#8e706f'
  outline-variant: '#e2bebc'
  surface-tint: '#b52330'
  primary: '#b52330'
  on-primary: '#ffffff'
  primary-container: '#ff5a5f'
  on-primary-container: '#61000e'
  inverse-primary: '#ffb3b0'
  secondary: '#006a63'
  on-secondary: '#ffffff'
  secondary-container: '#57faec'
  on-secondary-container: '#007169'
  tertiary: '#8433c4'
  on-tertiary: '#ffffff'
  tertiary-container: '#be6fff'
  on-tertiary-container: '#460074'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffdad8'
  primary-fixed-dim: '#ffb3b0'
  on-primary-fixed: '#410007'
  on-primary-fixed-variant: '#92001b'
  secondary-fixed: '#57faec'
  secondary-fixed-dim: '#2addd0'
  on-secondary-fixed: '#00201d'
  on-secondary-fixed-variant: '#00504b'
  tertiary-fixed: '#f2daff'
  tertiary-fixed-dim: '#e0b6ff'
  on-tertiary-fixed: '#2e004e'
  on-tertiary-fixed-variant: '#6a0baa'
  background: '#f9f9ff'
  on-background: '#141b2b'
  surface-variant: '#dce2f7'
typography:
  display-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 40px
    fontWeight: '800'
    lineHeight: 48px
    letterSpacing: -0.03em
  display-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '800'
    lineHeight: 38px
    letterSpacing: -0.025em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 34px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 22px
    fontWeight: '700'
    lineHeight: 28px
    letterSpacing: -0.015em
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '700'
    lineHeight: 24px
    letterSpacing: -0.01em
  title-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 22px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '700'
    lineHeight: 18px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 10px
    fontWeight: '700'
    lineHeight: 12px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-sm: 0.75rem
  margin: 1.25rem
  margin-mobile: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style
The design system establishes a high-energy, spontaneous, and tactile travel discovery experience engineered specifically for modern mobile interaction. Drawing heavily from contemporary expressive UI and energetic neomorphism, the visual language communicates wanderlust, immediacy, and delight without sacrificing informational density or legibility.

Key emotional anchors include:
- **Spontaneous Discovery:** High-saturation accents spark visual interest, encouraging touch and exploration.
- **Punchy Contrast:** Vibrant accents sit atop clean off-white canvases and pure white containers, ensuring glanceable contrast under direct sunlight.
- **Tactile Softness:** Fluid contours, pillow-like interactive elements, and responsive micro-elevations deliver physical feedback native to touch gestures.

## Colors
The color architecture relies on a crisp high-contrast base with hyper-saturated functional accents.

### Core Foundation
- **Canvas Base:** Off-White (`#F7F9FA`) serves as the global canvas to prevent harsh mobile screen glare.
- **Card Container:** Pure White (`#FFFFFF`) isolates cards, sheets, and dynamic modules from the canvas.
- **Text & Hierarchy:** Deep Slate Black (`#111827`) provides maximum contrast for headlines and body text; muted subtitles utilize neutral slate (`#4B5563`); subtle borders use tinted borders (`#E5E7EB`).

### Brand Accents
- **Primary Accent (`#FF5A5F` - Energetic Coral):** Reserved strictly for high-conversion CTAs, bookmarking/favorite states, quick booking actions, and critical notifications.
- **Secondary Accent (`#00D1C4` - Electric Teal):** Applied to geo-radar states, live map indicators, secondary badges, and real-time social sharing toggles.

### Contextual Category Semantics
Category markers and filter chips dynamically assign specific color pairs to establish immediate visual recognition:
- **Sights & Nature:** Neon Emerald (`#00E676` surface tint / `#059669` text & iconography)
- **Dining & Drinks:** Fiery Amber (`#FF9100` surface tint / `#D97706` text & iconography)
- **History & Culture:** Vivid Purple (`#9D4EDD` surface tint / `#7C3AED` text & iconography)

## Typography
Plus Jakarta Sans is utilized system-wide across headlines, body copy, and UI badges. Its geometric construction, wide apertures, and friendly rounded terminals provide contemporary swagger while maintaining extreme optical clarity at compact mobile scales.

- **Display & Headlines:** Heavy weights (`700`, `800`) with tight letter-spacing impart confidence and editorial weight to travel destination titles.
- **Body:** Standard weights (`400`, `500`) provide comfortable readability with optimized line heights for scanning opening hours, itineraries, and reviews on the go.
- **Labels & Tags:** Uppercase or small tracking-boosted weights (`600`, `700`) balance legibility against vivid category backgrounds.

## Layout & Spacing
The layout adheres to an Android-first fluid 4-column grid for mobile screens, expanding to 8 columns on foldables and tablets.

- **Screen Margins:** Fixed `16px` (`margin-mobile`) horizontal canvas margin to maximize screen real estate, increasing to `20px` (`margin`) on larger form factors.
- **Gutters:** Standardized `16px` (`gutter`) gaps between multi-column cards, reduced to `12px` (`gutter-sm`) for compact horizontal carousels.
- **Vertical Rhythm:** Multiples of 4px/8px control element pacing. High-density content lists utilize `space-sm` (8px) internal gaps, while major module separations use `space-xl` (32px).
- **Safe Areas:** Standard Android navigation bars and system status bars maintain dynamic system insets plus a minimum bottom clearance of 80px to accommodate floating actions and navigation bars.

## Elevation & Depth
Elevation avoids standard muddy drop shadows in favor of warm, vibrant ambient micro-shadows tinted slightly toward the brand tone.

- **Level 0 (Flat):** Canvas background (`#F7F9FA`) and recessed inset input wells.
- **Level 1 (Resting Cards):** `0 4px 16px -2px rgba(17, 24, 39, 0.05)`, used on resting white discovery cards, destination lists, and search inputs.
- **Level 2 (Interactive & Active):** `0 8px 24px -4px rgba(17, 24, 39, 0.08)`, used on pressed cards and bottom sheets.
- **Level 3 (Floating Controls):** `0 12px 32px -4px rgba(255, 90, 95, 0.25)` for primary coral action buttons, providing an energetic colored halo that indicates tap viability. Floating discovery bars use `0 10px 30px -6px rgba(17, 24, 39, 0.12)`.

## Shapes
The design system embraces an ultra-rounded, friendly silhouette with radii scaling proportionally to element size:
- **Base Surface (`rounded-md` - 12px to 16px):** Standard inputs, small badges, and modal dialogs.
- **Cards & Sheets (`rounded-lg` / `rounded-xl` - 16px to 24px):** Destination cards, content wrappers, and expandable bottom drawers.
- **Tactile Pills (`rounded-full`):** Category tags, action buttons, search bars, and floating discovery indicators strictly employ full 9999px border-radius pill shapes for maximum ergonomic touchability.

## Components

### Buttons
- **Primary Action (Energetic Coral):** Full pill (`rounded-full`) geometry, background `#FF5A5F`, text `#FFFFFF`, bold typography (`label-lg`), with coral-tinted ambient glow shadow. Active state initiates a 96% scale press feedback.
- **Secondary Action (Electric Teal):** Background `#00D1C4`, text `#FFFFFF`, full pill geometry. Used for directional triggers, route navigation, and instant share sheets.
- **Ghost/Tertiary:** High-contrast neutral text `#111827` inside an off-white or transparent pill, with an active background tint of `#F3F4F6`.

### Chips & Tags
- **Category Discovery Pills:** Borderless pill chips with dynamic dual-tone colors. 
  - *Sights:* Background `#E8FDF2`, text `#059669`.
  - *Restaurants:* Background `#FFF5E6`, text `#D97706`.
  - *History:* Background `#F6ECFC`, text `#7C3AED`.
- **Selected States:** Filled with pure solid category color and white text, accompanied by an animated scale bounce.

### Cards
- **Discovery Cards:** Pure white (`#FFFFFF`) containers, `20px` border-radius (`rounded-xl`), Level 1 ambient shadow. Media previews bleed edge-to-edge across the top with a bottom content padding of `16px`. Top-right features a floating coral bookmark circle button.

### Floating Discovery Bar & Inputs
- **Search Header:** Floating pill bar lifted by Level 3 elevation, featuring a subtle border (`#E5E7EB`), magnifying glass icon in `#00D1C4`, placeholder styled in `body-md`, and voice/filter action on the trailing end.
- **Input Fields:** Recessed light neutral fill (`#F3F4F6`), `16px` border-radius, zero border at resting state, transitioning to a `2px` solid `#00D1C4` outline on focus.

### Mobile Navigation (Tab Bar)
- **Bottom Navigation Dock:** Floating rounded capsule suspended 16px above the Android navigation bar. Background `#FFFFFF` with glassmorphic backdrop-blur (`blur(16px)` at 90% opacity), carrying active icon indicators bathed in Energetic Coral (`#FF5A5F`) with micro-indicator dots underneath.