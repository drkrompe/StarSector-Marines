# Story — Defense intensity from the target market (bridge + first consumer)

> The first slice of the [campaign → battle bridge](../overview.md): build the
> `TargetProfile` pipe end-to-end and ride it in with one consumer —
> `OverwatchTowerStage` scaling its overwatch line off the target world's
> planetary defenses. "This world is fortified" becomes something the player
> reads off the ground they land on.

**Status:** in progress.

## Scope

Two things land together, deliberately:

1. **The pipe (built whole).** `TargetProfile` + extraction + threading, sized
   for *all* future consumers even though only one reads it now. The full market
   read (`marketSize`, `stability`, `defenseLevel`, `spaceportTier`,
   `factionId`) is extracted up front so the extraction is stable; later
   consumers opt into fields without touching the resolver again.
2. **One consumer (`defenseLevel` → `OverwatchTowerStage`).** Chosen first
   because its seam was pre-built (`CELLS_PER_TOWER` was already documented as
   "the multiply-point for a campaign-driven defense intensity") and the whole
   change stays inside a single stage — smallest blast radius.

Out of scope (captured as bridge consumers 2/3 in the overview): urban
composition (district weighting), hard installations as map features.

## The pieces

- **`battle.world.gen.TargetProfile`** — immutable record, primitives + interned
  faction id, no game API. `NEUTRAL` constant = baseline world; every field
  reads as "no campaign signal," so generation matches the pre-bridge output
  exactly. This is the contract that keeps `gen` campaign-free.
- **`ops.detachment.TargetProfileResolver`** — stateless static, mirrors
  `DetachmentResolver`'s economy-scan-by-planet-name. `resolve(targetPlanetName)`
  → profile, `NEUTRAL` when the name is null / no sector / no market matches.
  `defenseLevel` sums weighted defensive industries:
  heavy batteries (2) / ground defenses (1) + star fortress (3) / battlestation
  (2) / orbital station (1) + high command (1) + planetary shield (1).
- **`MapGenerator.generate(w, h, seed, axis, TargetProfile)`** — new overload,
  defaults to the 4-arg form. `BspCityGenerator` makes it canonical and binds
  the profile on `GenContext` under `BspKeys.MARKET_PROFILE` (NEUTRAL when null).
- **`BattleSetup.createConquest`** — gains a `TargetProfile` param, forwards it
  to `generate`. `MissionLaunch` resolves the profile (beside the existing
  `enemyHasHeavyArmor` extraction) and passes it in. Sabotage/placeholder paths
  are *not* threaded this slice — they run no `OverwatchTowerStage`; they get the
  param when their recipes gain a consumer (keeps blast radius to conquest).
- **`OverwatchTowerStage`** — reads the profile (NEUTRAL default):
  - **budget multiply:** `budget = clamp(round(area / CELLS_PER_TOWER × (1 +
    GAIN × defenseLevel)))`. `MAX_TOWERS` raised so a max-fortified large map can
    field a long line before the ceiling bites; physical site supply +
    `MIN_SEPARATION` remain the real limiter on dense worlds.
  - **turret-tier escalation:** `VULCAN` (light, baseline) → `ARBALEST` (mid) →
    `HEPHAESTUS` (heavy) by `defenseLevel` band. NEUTRAL → VULCAN, so the
    default path is unchanged.

## Verification

- **Headless** (no campaign): `OverwatchTowerStageTest` gains a case driving the
  5-arg `generate` directly with a constructed fortified profile vs `NEUTRAL` on
  the same seed — asserts the fortified map fields **≥** the neutral tower count
  *and* mounts the escalated turret kind. Supply-independent half (turret kind)
  proves the bridge wired even if dense-map supply caps the count.
- NEUTRAL-path determinism + the existing taxonomy/validation suite stay green
  (byte-identical generation when no profile is supplied).

## Follow-ups (not this slice)

- **Unify the two economy scans.** `TargetProfileResolver.resolve` and
  `DetachmentResolver.planetHasHeavyArmaments` both scan the econ by planet
  name; the profile's `defenseLevel`/industry read can subsume the heavy-armor
  boolean. Left alone now to keep the defender-roster path untouched.
- **Fortress-wall toggle / heavier hand-placed towers** off `defenseLevel`
  (touches `FortressWallStamper` — a second stage, deferred to hold the 1-stage
  line).
- **Tune `CELLS_PER_TOWER`, `GAIN`, the tier bands** after eyeballing in-game.
- Bridge consumers 2 (urban composition) + 3 (hard installations).
