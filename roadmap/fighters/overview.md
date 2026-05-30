# Fighter kinematics

> Long-form companion / design doc. Open this when picking up the
> fighter-kinematic-model work cold. **Design stage** — nothing here has
> shipped yet.

## What this is

A `.ship`-driven kinematic model for fighter / drone craft in the battle
tier, scaled and re-flavored for ground-scale combat *in atmosphere*. The
goal is twofold:

1. **Recognizable feel.** Fighters should move with the role contrast players
   already know from vanilla Starsector — interceptors are twitchy, bombers
   are sluggish, drones are cheap and nimble. We borrow the vanilla *ratios*
   rather than inventing numbers from scratch.
2. **Maximal mod support.** The model reads vanilla's own per-hull data at
   runtime, so any modded fighter that ships standard hull data flies
   correctly with zero per-mod work on our side.

This is the **data-model foundation** the backlog item *"Flyby fighters as
real air entities"* is blocked on. Today's `battle/flyby/` fighters are a
scripted cosmetic overlay (heading-based weave + strafing runs, a single sim
coupling via `BattleSimulation#applyExternalDamage`); they do **not** fly on
`AirBody` and carry no kinematic profile. The package move of `flyby/` into
`air/` was deliberately deferred until this model exists — see
`roadmap/backlog.md` § "Flyby fighters as real air entities". Build the model
first; relocate when flyby has *become* an air entity.

## Where the data lives (and why we don't parse CSVs)

Vanilla splits a hull's definition across three files, all keyed by hull / wing
id:

| File | Carries | Our use |
| --- | --- | --- |
| `data/hulls/ship_data.csv` | kinematics (speed/accel/decel/turn/turnAcc) + hp/armor/flux/mass | the kinematic profile |
| `data/hulls/<hull>.ship` | geometry: `collisionRadius`, `bounds`, `engineSlots`, `weaponSlots`, `shieldRadius` | engine-FX positions (already scraped) |
| `data/hulls/wing_data.csv` | wing composition: `num`, `formation`, `range`, `attackRunRange`, `role` | wing size + behavior |

**Runtime source of truth = `ShipHullSpecAPI`, not the CSV.** The game has
already parsed all three files (core + every enabled mod) by the time our sim
runs. The kinematic fields are exposed directly:

```java
ShipHullSpecAPI hull = Global.getSettings().getHullSpec(hullId);
EngineSpecAPI eng = hull.getEngineSpec();
eng.getMaxSpeed();          // su/sec
eng.getAcceleration();      // su/sec²
eng.getDeceleration();      // su/sec²
eng.getMaxTurnRate();       // deg/sec
eng.getTurnAcceleration();  // deg/sec²
hull.getCollisionRadius();  // su
```

This route is strictly better than re-parsing `ship_data.csv`:

- **Sandbox-safe.** Mod code can't touch `java.io.File` / `java.nio` — see
  [[starsector_script_sandbox]]. `getHullSpec` needs no disk I/O from us.
- **Mods for free.** Any modded fighter the game loaded is already in the spec
  registry; we never special-case a mod.
- **Stat-accurate.** It reflects hullmod / skill / config changes vanilla
  applies, which a raw CSV read would miss.

The scraped numbers in [`vanilla-kinematics-reference.md`](vanilla-kinematics-reference.md)
are for **offline design reference only** (tuning the scale factor, sanity-
checking the feel) — they are not a runtime data path.

Engine-slot geometry is *already* scraped from `.ship` at runtime by
`battle/air/engine/EngineSlotResolver`, keyed off the `hullId` field on
`battle.flyby.FighterProfile`. Same hull-id key threads the kinematic lookup —
see [[vanilla_ship_spec_scraping]].

## Mapping vanilla → our model

The kinematic seam already exists: `battle/air/AirBody` (composed state +
`tickToward`) governed by an `AirHandling` profile. Shuttles fly on it today;
fighters compose the same body. `AirHandling`'s field shape nearly matches
vanilla's:

| Vanilla (`EngineSpecAPI`) | `AirHandling` | Notes |
| --- | --- | --- |
| `getMaxSpeed()` | `maxSpeed()` | × global `SCALE` (su → cells) |
| `getAcceleration()` | `accel()` | × `SCALE` |
| `getDeceleration()` | `brakingAccel()` | × `SCALE` |
| `getMaxTurnRate()` | `maxTurnRateDegPerSec()` | deg/sec — unit-invariant, no scale |
| `getTurnAcceleration()` | *(no equivalent)* | AirBody rate-limits the slew but has no accel ramp; `angVelDegPerSec` is currently informational. **Gap to close** if turn-ramp feel matters. |
| `mass` | *(unused)* | collision/ramming momentum only; irrelevant to steering. Wire only if fighters collide. |
| — | `lateralDriftDamping()` | **no vanilla equivalent** — our atmosphere flavor knob |
| — | `stationDamping()` | our flavor knob (hover settle) |

**Two genuine deltas from vanilla**, both intentional:

- **`turnAcceleration` is unmodeled.** Vanilla ramps angular velocity up to
  the cap; AirBody snaps to a rate-limited slew. For ground/atmosphere this
  reads fine, but if the "heavy bomber takes a moment to get its nose around"
  feel matters, extend `AirBody` to ramp `angVelDegPerSec` toward
  `maxTurnRate` at `turnAcceleration` rather than clamping instantly. Decide
  per playtest; don't build it speculatively (ship-then-optimize,
  [[feedback_ship_then_optimize]]).
- **Lateral drift damping has no vanilla analogue.** Vanilla space is
  frictionless; our atmosphere isn't. This is the primary "feel" dial that
  separates our fighters from the source model.

## Scale & atmosphere leeway

Keep the **vanilla ratios** as the backbone — that contrast (interceptor turn
150–225 vs bomber 30–90) *is* the gameplay. Apply divergence uniformly on top:

- **Global `SCALE` factor (su → cells).** Vanilla speeds are tuned for a
  zoomed-out fleet arena; at ground-squad scale you want lower absolute
  cells/sec while preserving inter-hull ratios. One factor, applied to
  speed + both accels (turn rates are angular, so unscaled).
- **Linear drag / lateral damping.** Atmosphere bleeds momentum — pushes the
  drift-on-decel feel and shrinks the effective dogfight arena. The cheapest,
  highest-leverage atmosphere knob.
- **Turn-rate nudges for readability.** At a close ground camera, bump
  interceptor turn up / bomber turn down past vanilla for a more arcadey,
  legible dogfight. Flavor only — keep it small.
- **Banking visual (cosmetic).** Derive a roll/lean from `angVelDegPerSec`
  for the atmosphere read. Zero sim cost; pure render.

## Relationship to `battle.flyby.FighterProfile`

`FighterProfile` (in `flyby/`) is the **loadout** half — sprite, weapon class,
tracer/burst/projectile tuning, audio, faction pool. It is *not* kinematic.
The kinematic profile proposed here is the **movement** half. They share the
`hullId` key. Two viable shapes (decide in S1):

- **Separate records** — `FighterProfile` (loadout) + `KinematicProfile`
  (movement), joined on hull id. Clean separation of concerns.
- **One enriched record** — fold a kinematic block into `FighterProfile`.
  Fewer types, but couples cosmetic + sim.

Lean toward separate records: the kinematic profile is sim-tier and the
loadout is render/flyby-tier, and the eventual `flyby/`→`air/` fold should not
drag the loadout schema into the kinematic model.

## Decomposition into stories

Design-stage; sequence not yet committed.

- **S1 — Kinematic profile + loader.** A `KinematicProfile` record sourced
  from `ShipHullSpecAPI.getEngineSpec()` keyed on hull id, with the global
  `SCALE` applied at construction. Resolve the "separate vs enriched record"
  question. Unit-test against a couple of known vanilla hulls (Talon, Trident)
  to lock the scale factor.
- **S2 — `FighterType` / `AirHandling` adapter.** Adapt `KinematicProfile`
  into an `AirHandling` so an `AirBody` can fly a fighter. Layer the
  atmosphere knobs (drag, turn nudges). Mirrors `ShuttleType`'s relationship
  to `AirHandling`.
- **S3 — Wing composition.** Read `num` / `formation` / `range` / `role` from
  `wing_data.csv` (via the runtime spec registry) to drive wing size and
  formation behavior.
- **S4 — Flyby fighters become real air entities** (the backlog item). Rebuild
  `flyby/` fighters on `AirBody`: spawn from map edges, take fire, get shot
  down, optionally land at bases. Fold `flyby/` into `air/`. Blocked on S1–S2.

## Open questions

- Do we model `turnAcceleration` (turn-ramp), or is rate-limited slew enough
  for the ground-scale feel? (Defer to playtest.)
- Where does `SCALE` live — a single battle-tier constant, or per-map
  (different feel at "high altitude" vs "low pass")?
- Do fighters collide (needs `mass`) or pass through each other like the
  current flyby overlay? Collisions are a much bigger sim commitment.

## Cross-references

- `roadmap/backlog.md` § "Flyby fighters as real air entities" — the parent
  backlog item this model unblocks.
- `roadmap/convoy/overview.md` — the ground-vehicle kinematics sibling
  (`GroundBody`/bicycle model); same "data-driven body + steering" shape.
- `roadmap/command-powers/` — fighter cover is surfaced to the player as a
  command power (`s2-slice3-fighter-cover-opt-in`); this model is the sim
  substrate those committed wings would eventually fly on.
- Memory: [[air_vehicle_kinematics]], [[air_unit_render_sync]],
  [[vanilla_ship_spec_scraping]], [[starsector_script_sandbox]],
  [[ground_vehicle_kinematics]].
