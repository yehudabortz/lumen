# Changelog

## Unreleased

- The drawn backgrounds are now ten grainy gradients (Horizon, Sorbet, Beam, Ember, Aurora, Dusk, Lagoon, Midnight,
  Citrus, Rose) in place of the Alpine, Forest, Dunes and Coast landscapes. A saved landscape moves to the nearest
  gradient, and the screensaver's fallback option is now called Gradients.

## 1.0

- Top Shelf card size and card text options; the Labels setting also applies to All Apps.
- Wider dock; the clock and weather hide in All Apps, and the dock's gradient overlay is gone.
- Rearrange apps in All Apps. Left and Right now move across rows.
- Top Shelf cards fade at the dock edge and slide off the real screen edge, so their shadows no longer get cut off.
- Tighter motion timing across Home and the screensaver, plus a crossfade from the screensaver to Home.
- The weather condition shows as a glyph in Settings and on the screensaver.
- Refined Settings dropdown; the selection no longer flashes white as it moves.
- The dock's highlight stays still and soft while focus is elsewhere.

## 0.3 — unreleased / experimental

- Native Android TV home screen with drawn landscapes, clock, city-based weather and a glass app dock.
- Mixed Top Picks from apps' Watch Next and channels, All Apps, tile actions and settings.
- Live scenic NASA Earth photos with bounded caching, photo credits and a drifting screensaver.
- NASA photos are the fresh-install default; focused apps and recommendations have a contrasting outline.
- Settled focus on Home return and wrapped settings notes.
- Native emulator screenshot fixtures using public sample content.
- MIT licensing, source notices, privacy/contribution/security documentation and CI checks.
- Top Shelf view options: Standard, Large or Compact cards, and title-and-details, title-only or no card text.
- Weather attribution links in Settings; removed the unused Unsplash integration and API-key embedding.
- Full-width dock with roomier tiles; the Top Shelf and All Apps share its columns, and the clock and weather lift away in All Apps.
- All Apps can be rearranged with *Move* from the tile menu; the order is kept, and new apps join at the end.

Known limitations: cinematic mode, selectable tile sizes and bundled Inter remain planned. Device vendors
differ in default-launcher, TV-listings and screensaver behavior. NASA's metadata and exposure filtering
cannot guarantee subjective visual quality. The current target SDK is 34; Google Play submission requirements
are a separate task from source publication. Emulator screenshots do not establish real-TV performance.
