# Layered alien claw swipe — shipped

Shipped in `20d3bcb0`.

## What landed

- Generated and retained one transparent, axially neutral forearm/claw source
  with built-in ImageGen, then normalized it to a 96 px-high runtime layer with
  the existing reproducible alien asset script.
- Reuses that layer twice at runtime: both claws tuck beneath the carapace at
  rest, preserving a complete creature silhouette without adding sprite-sheet
  frames or per-entity arm state.
- Contact fire lifts one alternating claw above the body, moves it forward
  beside the head, and smoothly retracts it over the existing 0.09-second
  firing pose. The three talons remain visible throughout the impact pose.
- Treats the claw as part of the all-or-nothing xeno family. A missing layer
  leaves live aliens on the held legacy sheet instead of producing a partial
  modular creature.
- Retains the exact generation prompt and source under the modular alien asset
  directory and emits idle, impact, and retraction previews for visual tuning.

## Verification

- Visually inspected the complete idle and impact compositions, including
  transparency, shoulder registration, foreground occlusion, and talon
  readability.
- `gradlew.bat :test --tests
  com.dillon.starsectormarines.battle.appearance.LayeredAppearanceTest` passed.
- `gradlew.bat build` passed, including the root and asset-pipeline suites.

## Still manual

Confirm swipe scale and 0.09-second readability at normal battle zoom during a
dense swarm. Roster size and post-quarter-HP kill cadence remain in the shared
manual feel queue.
