# Slice 0 — economy substrate (shipped)

The shared dependency every economic district sits on: the campaign → battle
bridge now carries the target world's **economic function mix**, and both zoning
maps lean their theme selection toward it. No new districts — the point was to
prove the plumbing on the existing nine themes before any new content lands.

## What landed

- **`EconomicFunction`** (`battle.world.gen`) — campaign-decoupled enum of the
  eight roles: `HABITATION, COMMERCE, HEAVY_INDUSTRY, SPACEPORT, MINING,
  REFINING, AGRICULTURE, MILITARY`. The stable vocabulary the generator reads;
  vanilla `Industries.*` never crosses into `gen`.
- **`EconomicZoning.dominantTheme(Set<EconomicFunction>)`** — the single policy
  both maps consult. Returns the theme that best expresses a world's character,
  ranked by **distinctiveness, not frequency** so the near-universal
  `HABITATION` doesn't drown out a farming or trade read; `null` (no signal) for
  an empty mix. Slice-0 stand-in mapping onto existing themes:
  industry/refining/mining → `INDUSTRIAL`, agriculture → `OUTSKIRTS`, military →
  `MILITARY_FORT`, commerce → `CIVIC`, habitation → `RESIDENTIAL`; `SPACEPORT`
  contributes no theme (its tier drives structures).
- **`TargetProfile.functions`** — sixth record component, an unmodifiable
  `EnumSet<EconomicFunction>`. `NEUTRAL` carries the empty set. Resolver maps the
  market's present industries onto it (`TargetProfileResolver.functions`).
- **`BiomeMap`** (conquest) — new 5-arg constructor; the **CITY band** (35–75%
  of the axis, the urban bulk) resolves to `dominantTheme` instead of always
  `MIXED`. Every other band stays structural. Empty mix ⇒ `MIXED` (unchanged).
  This is the path that actually receives a real profile in-game.
- **`DistrictMap`** (legacy/preview) — new 4-arg constructor; interior districts
  are redirected to the economy theme with `ECON_BIAS_PROBABILITY = 0.55`. Edge
  districts keep their geographic character. **No extra rng draw when the mix is
  empty** — byte-identical to the pre-bridge roll.
- **`ZoningOverlayStage`** reads `BspKeys.MARKET_PROFILE` and threads
  `profile.functions()` into whichever map it builds.

## Invariant held

`NEUTRAL` / empty-mix output is byte-identical to pre-bridge:

- `DistrictMapEmptyMixIsByteIdentical` — empty-mix theme grid == no-arg grid
  across 5 seeds (proves the economy bias takes zero rng draws when silent).
- `BiomeMap` empty-mix CITY band == `MIXED`; non-CITY bands economy-independent.
- `OverwatchTowerStageTest` unchanged: neutral 60 towers (heavy 0), fortified 136
  (heavy 136) — same as the defense-intensity slice, so adding `functions` did
  not perturb the conquest rng stream.

Shift proven: `districtMapInteriorLeansIndustrialOnAHeavyIndustryWorld` (heavy >
neutral interior `INDUSTRIAL` count); `biomeCityBandFlexesWithEconomyButOtherBandsAreStructural`.

## Tests

`EconomicZoningTest` (new) + the existing `OverwatchTowerStageTest`,
`BiomeCompoundSeederTest`, `RecaptureTargetServiceTest` all green; full
`battle.world.gen.*` / `bsp.*` / `taxonomy.*` suites green.

## Follow-ups

- Per-function **weight** (industry size / upgrade tier), not just presence —
  a size-3 mining outpost vs. a size-7 mining hub should differ.
- Slices 1–4 replace the stand-in theme mappings in `EconomicZoning` with the
  real district kinds (`MINING_SITE`, `REFINERY`, `AGRI_FIELD`, sharpened
  spaceport) as each ships.
