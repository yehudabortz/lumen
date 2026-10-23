# Lumen — design

A calm, clean and minimal home screen for Android TV and Google TV: your apps on a glass dock, what you're watching
from those apps, the time and the weather, over a landscape that follows the time of day. No ads, ever.

Status: **design proposal**, not implemented yet. The interactive mockup is [`mockup.html`](mockup.html): open it in
a browser. The arrow keys act as the D-pad, Enter as OK (hold it for the tile menu), Esc as Back and S opens settings.

## Principles

1. **Your content, never ads.** Everything on screen comes from apps installed on the TV: what you were watching
   and what those apps suggest. Lumen talks to no ad, analytics or recommendation server, and has none of its own.
2. **Calm, not empty.** A landscape, the time, your apps and one row of content. Nothing autoplays, nothing blinks.
3. **Remote first.** Everything works with the D-pad, OK and Back. Long-press OK is the only "advanced" gesture.
4. **The default must be great.** A new user should never need settings. Options exist to refine, not to fix.
5. **Light and boring to maintain.** One activity, plain Android views and Canvas, no libraries.

## Layout (reference: 1920×1080 px; the TV draws at 960×540 dp, so 1 dp = 2 px)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                         ☀ 63°  │  2:48       │  weather · clock
│                                                                              │
│   Continue Watching · Northfilm                                              │  Top Shelf heading
│   ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐                        │
│   │ artwork  │ │          │ │          │ │          │                        │  content cards
│   │ ▬▬▬▬──── │ │          │ │          │ │          │                        │  (progress bar)
│   └──────────┘ └──────────┘ └──────────┘ └──────────┘                        │
│   S2 · E4 · 22 min left                                                      │
│     ╭────────────────────────────────────────────────────────────────╮       │
│     │ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐                    │       │  glass dock (Top Row)
│     │ │      │ │FOCUS │ │      │ │      │ │      │                    │       │
│     │ └──────┘ └──────┘ └──────┘ └──────┘ └──────┘                    │       │
│     ╰────────────────────────────────────────────────────────────────╯       │
│                Northfilm                                                     │  focused app's name
└──────────────────────────────────────────────────────────────────────────────┘
```

| Element | Spec |
|---|---|
| Safe area | 96 px left/right, 64 px top/bottom (TV overscan); content aligns to x = 185 px |
| Clock | Top-right, 40 px, Inter Medium, −2% tracking, tabular figures, 95% white. Optional date below, 21 px, 62% white |
| Weather | Left of the clock, separated by a 1.5 px hairline: a color glyph (34 px) and the temperature, 30 px Medium, 62% white |
| Top Shelf | Heading at y = 262 px: 25 px SemiBold, with the app's name after a dot in 62% white. Cards 376×211 px, radius 20, gap 28, then the episode/time line (21 px) |
| Dock | Glass (see Materials), centered, bottom edge at 81% of screen height. Padding 22 px; radius 42 px = tile radius + padding, so the curves are concentric |
| Tiles | 16:9, radius 20 px. Medium 288×162 px (default, five across), Small 232×130 (six), Large 376×211 (four) |
| Focus | Scale 1.09 (cards 1.07) over 240 ms, deep soft shadow, a top-light sheen and one glint that sweeps across. The dock's specular highlight slides to sit above the focused tile |
| Label | Focused tile only (default). 25 px SemiBold, 22 px under the dock |
| Legibility | A dark vignette behind the clock and along the bottom, plus a soft band behind the shelf that strengthens on bright daytime scenes |

## Screens

1. **Home.** Scene, weather and clock, the Top Shelf, the dock. Focus starts on the dock.
2. **Top Shelf.** It follows the focused app, as on a clean interface: *Continue Watching · Northfilm* shows that app's in-progress
   items (with progress bars), then what that app recommends. If the focused app publishes nothing, the shelf shows
   **Up Next**: in-progress items from all your apps, newest first, each with a small badge naming its app.
3. **Cinematic.** Up from the dock moves focus into the shelf. The focused item's artwork fills the screen behind a
   dark gradient, with the app name, the title, year · rating · genre, a two-line synopsis and *Resume*/*Play*.
   The dock slides away and the cards settle at the bottom. Back or Down returns home.
4. **All Apps.** Down from the dock: the shelf fades, the page scrolls up, the background blurs (64 px), saturates
   and dims to half, and a five-column grid aligned with the dock's tiles appears.
5. **Tile menu.** Long-press OK on a tile: *Move* · *Add to/Remove from Top Row* · *Show in Top Shelf* · *Hide* ·
   *Home Screen Settings*. *Move* makes the tile wiggle; Left/Right moves it, OK drops it.
6. **Settings.** A floating glass panel (36 px from the edges, radius 48 px) over the live home screen, so every
   change previews instantly. It scrolls to keep the focused row in view.
7. **Screensaver.** The same scene, full screen, drifting very slowly (a 90 s zoom-and-pan, back and forth), with
   a large clock (150 px SemiBold), the date and the weather at the bottom left. The text block moves to a new spot
   every few minutes so nothing burns into OLED panels. Any key wakes the TV straight back to Home.

## Content: where the shelf comes from

Android TV has a standard place where apps publish what you're watching and what they suggest: the **TV provider**
(`TvContract`). Apps write *Watch Next* items ("continue watching", "next episode", "watchlist") and *preview
channels* (their own recommendation rows). Lumen only reads that data. It never adds its own picks and has no
server.

- **Access.** With `READ_TV_LISTINGS` (a permission the user grants once from a prompt), a launcher can read every
  app's Watch Next items and preview programs marked searchable, which is the default. Without it Android shows only
  the launcher's own rows. Confirmed in the provider's source (`TvProvider.createSqlParams`).
- **Up Next order:** *Continue* items first (by last engagement time), then *Next* (next episode), then *Watchlist*.
- **Opening an item** fires the program's intent URI, so it lands on that title inside its app.
- **Per app:** every app can be turned off in *Apps in Top Shelf* or from its tile menu. Apps the user hid, or that
  are disabled, never appear.
- **Artwork** comes from the poster/thumbnail URIs the apps provide. They are fetched, decoded at card size and
  cached on disk (a few MB at most).
- **An honest limit:** on Google TV some apps send "continue watching" only to Google's servers and not to the
  on-device provider. Those apps simply have an empty shelf, and Up Next covers for them.

## Weather

- **Source:** [Open-Meteo](https://open-meteo.com): free, no key, no account. One request every 30 minutes while
  the TV is on (current temperature and condition, plus today's high and low for the screensaver).
- **Location:** *Automatic* uses the TV's time zone to pick its main city, so no location permission is needed.
  The user can set a city instead (typed once, geocoded once, stored on the TV).
- **Units:** follow the TV's region (°F in the US), or choose.
- **Offline:** the weather simply disappears; nothing shows an error.

## Scenes (backgrounds)

Landscapes drawn in code, so they cost no image files and no downloads and stay sharp at any resolution:

| Scene | What's in it |
|---|---|
| **Alpine** (default) | Four mountain ranges fading into haze, a pine shore, a lake with a soft reflection and a sun or moon glint, pines framing the edges |
| **Forest** | Six tree-covered ridges with mist between them |
| **Dunes** | Smooth layered dunes with a low sun |
| **Coast** | A fjord: headlands dropping into a sea lit at the horizon |
| **Graphite** / **Black** | Plain, for the quietest setup (Black is OLED-friendly) |

Each scene is lit by one of four palettes: **dawn**, **day**, **dusk**, **night** (stars and a crescent moon). With
*Follow time of day* on, the palette changes with the clock, crossfading over a few minutes. Far ranges take on the
horizon's color (atmospheric perspective); a 2% grain stops gradients from banding. The same scene generator makes
the placeholder artwork in the mockup.

## Typography

Typography uses a system sans-serif; Inter is the planned alternative.

| Use | Size | Weight | Tracking |
|---|---|---|---|
| Screensaver clock | 150 px | SemiBold | −4.5%, tabular figures |
| Cinematic title | 76 px | Bold | −3.5% |
| Panel title ("Home Screen") | 40 px | Bold | −3% |
| Clock | 40 px | Medium | −2%, tabular figures |
| Headings, menu names | 25–29 px | SemiBold | −2% |
| Rows, labels | 25 px | Medium / SemiBold | −1% |
| Secondary text | 19–23 px | Medium | 0, 62% or 40% white |

## Materials

One glass recipe, used by the dock, the tile menu, the settings panel and the Move hint:

| Layer | Recipe |
|---|---|
| Backdrop | Background blurred ~40 px (menus: 60 px), saturation ×1.45 (menus ×1.9, slightly darkened) |
| Tint | Dock: white 8.5% at the top fading to 2%. Menus: a dark charcoal tint (~65%) so text stays crisp |
| Specular rim | 1.5 px edge: bright white (≈78%) along the top, fading to 4% down the sides, a faint 20% bounce at the bottom |
| Sheen | White 7% → 0% across the upper 40% |
| Moving highlight | On the dock: a narrow bright line on the rim plus a soft glow beneath it, centered on the focused tile, gliding 320 ms |
| Shadow | 0 44 px 90 px (−24 px spread), 60% black, plus a tight contact shadow |
| Depth of field | When a menu or settings opens, the home screen behind softens (12 px blur) and dims 26% |

Selected rows in menus are near-white pills (94%) with dark text, as on a clean interface.

## Options (the whole list, on purpose)

| Section | Option | Choices | Default |
|---|---|---|---|
| Appearance | Background | Alpine, Forest, Dunes, Coast, Graphite, Black | **Alpine** |
| | Follow time of day | On / Off | **On** |
| | Motion | Full / Reduced (fades only; focus shown with an outline) | **Full** |
| Top Shelf | Top Shelf | Rich (follows the focused app) / Up Next only / Off | **Rich** |
| | Cinematic artwork | On / Off (Off keeps the scene behind the hero text) | **On** |
| | Apps in Top Shelf | Per-app toggles | all |
| Top Row | Tile size | Small / Medium / Large | **Medium** |
| | Labels | On focus / Always / Never | **On focus** |
| | Apps | Edit order (opens Move) | well-known video apps first, then A–Z, five apps |
| Clock & Weather | Clock | System (12/24 h) / 12-hour / 24-hour / Off | **System** |
| | Show date | On / Off | **Off** |
| | Weather | On / Off | **On** |
| | Location | Automatic (time zone) / a city | **Automatic** |
| | Units | Auto / °F / °C | **Auto** |
| Screensaver | Screensaver | Landscape / Black / System default | **Landscape** |
| | Screensaver info | Clock & weather / Clock / None | **Clock & weather** |
| Apps | Hidden apps | List with toggles | none |
| About | Version, source link, how to switch back to the original home screen | | |

## Technical shape (keeps it light)

- **Java, no Gradle, no AndroidX, no third-party libraries.** `build.sh` runs aapt2 → javac → d8 → zipalign →
  apksigner. Target APK size: under 400 KB, most of it the Inter subset.
  Needs build-tools 35: d8 8.2 (build-tools 34) crashes on a file that has both an anonymous class and a lambda.
- **minSdk 26 (Android 8), targetSdk 34** (current target, so Play Protect accepts the install).
- **Components:** one activity (`HOME` + `LEANBACK_LAUNCHER`) and one `DreamService` (the screensaver).
  Classes: `HomeActivity`, `AppStore` (apps, installs/removals), `ShelfStore` (TV provider queries, change
  observer), `ArtCache`, `Weather`, `Scene` (the landscape generator), `Glass`, `TileView`, `CardView`, `TileMenu`,
  `SettingsPanel`, `Screensaver`, `Prefs`.
- **Permissions, and why:** `INTERNET` (artwork the apps point to, and the weather), `READ_TV_LISTINGS` (the shelf;
  asked once, and the shelf simply hides if declined). Nothing else: no location, no storage, no accessibility
  service, no boot receiver, no analytics.
- **Glass without live blur.** Lumen draws the scene itself, so it renders a blurred copy once (when the scene or
  the time-of-day palette changes) and the dock samples it. Cinematic backdrops are blurred once per item. Menus
  blur a single snapshot taken as they open. Nothing is blurred per frame. Android 12+ uses `RenderEffect`; older
  versions use a small box blur on a downscaled copy.
- **The screensaver** is a standard Android `DreamService`. Google TV hides third-party screensavers in its own
  settings, so setup sets it once with `settings put secure screensaver_components` (documented in the README).
- **Memory target:** under 60 MB idle on Home. Artwork is decoded at card size; only the visible shelf is in memory.
  First measurement on a 2022 Sony BRAVIA (3 GB RAM, Android 12): 63 MB PSS with a shelf of artwork loaded,
  0.4% of frames over deadline while navigating. The backdrop and its legibility gradients are composited once per
  scene, and the glass dock is a single bitmap-shaded rounded rect, so a frame is one full-screen bitmap plus the UI.
- **Nothing personal in the repo.** The TV's address and the signing key live in a git-ignored `local.properties`
  and `*.keystore`.

## Gallery (from the mockup; app names, shows and artwork are placeholders)

| | |
|---|---|
| ![Home](screens/home.jpg) Home: Alpine at dusk, Northfilm's shelf | ![Up Next](screens/up-next.jpg) Up Next across apps (focused app has no shelf) |
| ![Cinematic](screens/cinematic.jpg) Cinematic: focus in the shelf | ![Screensaver](screens/screensaver.jpg) Screensaver at night |
| ![All Apps](screens/all-apps.jpg) All Apps | ![Tile menu](screens/tile-menu.jpg) Tile menu (long-press OK) |
| ![Settings](screens/settings.jpg) Settings over the live home screen | ![Forest by day](screens/forest-day.jpg) Forest by day |
| ![Dunes at dawn](screens/dunes-dawn.jpg) Dunes at dawn, Up Next | ![Coast at night](screens/coast-night.jpg) Coast at night, date on |
| ![Quiet](screens/quiet.jpg) Quiet: Black, Top Shelf off | |
