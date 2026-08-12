# Modular variant prompt study

This directory tests independent ImageGen prompts for swappable true-overhead layers.
Every delivered PNG is one sprite, never a sprite sheet.

Armor families:

- `armor/armorless/` — cloth fatigues/webbing plus a separate bare head.
- `armor/charcoal/` — baseline dark marine body and head.
- `armor/blue-scout/` — rugged dark field-blue/naval infantry body and head.
- `armor/red-heavy/` — rugged elite oxblood veteran body and head.
- `armor/outlaw/` — asymmetric repaired red/brown salvage armor.
- `armor/army-green/` — standardized faded olive-drab campaign armor.
- `armor/militia/` — civilian-industrial plates, carrier, and helmet.

Weapons:

- `weapons/rifle.png`
- `weapons/rocket-launcher.png` — oversized rugged near-future anti-armor launcher.
- `weapons/laser-gun.png`
- `weapons/smg.png` — compact light-machine-gun silhouette.
- `weapons/dmr.png` — extended precision rail/marksman silhouette.
- `weapons/grades/surplus/rifle.png` — battered field-maintained grade-I exemplar.
- `weapons/grades/masterwork/dmr.png` — fleet-printed grade-IV exemplar.

The head is separate from the shoulder/body mass and can rotate independently around its
center. Each body has an empty helmet socket. `previews/` demonstrates family pairings,
head-look rotation, and one intentionally mixed armor/head combination.

## Shoulder-width anatomy shorthand

`1 sw` is the opaque alpha-bounds width of the body/shoulder sprite. The current reference
body is 150 px wide, but authored measurements are ratios resolved with
`pixels = round(sw * shoulderWidthPixels)`. This keeps equipment and attachment anatomy
consistent if unit scale or armor silhouette changes.

Current landmarks:

- Head width: `0.48 sw`.
- Standard rifle/laser length: `0.6533 sw`.
- SMG length: `0.52 sw`.
- DMR length: `0.82 sw`.
- Standard aimed offset: `(0.1733 sw, -0.12 sw)`.
- Rocket launcher size: `(0.4267 sw, 1.04 sw)`.
- Rocket aimed offset: `(0.3333 sw, -0.12 sw)`, placing its centerline approximately
  two-thirds of the way from the spine to the right shoulder edge.

`build_variants.py` uses the `sw(value)` helper as its source of truth. `variants.json`
records proportional `*Sw` values alongside compiled reference-pixel values for consumers
that have not yet adopted shoulder-relative layout.

## Weapon occlusion

Rifles and carried weapons normally render beneath the body. The rocket launcher switches
to an over-shoulder firing layer: render `body`, then `weapon`, then `head`, followed by
firing effects. This makes the launcher occlude the right shoulder while keeping the
independently rotating helmet visually above it. The carried/aimed launcher remains under
the body until the shouldering transition reaches its firing pose.

The successful prompt pattern is: one isolated object, strict zenith projection, a fixed
north orientation, an explicit empty socket or operator-free constraint, a flat chroma
background, and an existing accepted layer used only as geometry/style reference.

`build_variants.py` normalizes the retained alpha originals in `sources/` and rebuilds
these runtime sprites and previews. Chroma-key intermediates are deliberately discarded.
