# Hull extraction — the shared core

> The technical heart of the [`air/`](overview.md) category: turning one vanilla
> (or modded) hull id into a sim entity — kinematics **and** geometry. Fighters
> and ships are both just `AirBody`s fed by this pipeline; they differ only in
> the behavior layered on top.

## Where the data lives (and why we don't parse CSVs)

Vanilla splits a hull's definition across files, all keyed by hull / wing id:

| File | Carries | Our use |
| --- | --- | --- |
| `data/hulls/ship_data.csv` | kinematics (speed/accel/decel/turn/turnAcc) + hp/armor/flux/mass | the kinematic profile |
| `data/hulls/<hull>.ship` | geometry: `bounds` (collision polygon), `collisionRadius`, `engineSlots`, `weaponSlots`, `shieldRadius` | collision shape + FX positions |
| `data/hulls/wing_data.csv` | wing composition: `num`, `formation`, `range`, `attackRunRange`, `role` | fighter wing size + behavior |

**Runtime source of truth = `ShipHullSpecAPI`, not the CSV/file directly.** The
game has already parsed all of this (core + every enabled mod) by the time our
sim runs. Two accessors give us both halves of an entity from one hull id:

```java
ShipHullSpecAPI hull = Global.getSettings().getHullSpec(hullId);

// --- kinematics ---
EngineSpecAPI eng = hull.getEngineSpec();
eng.getMaxSpeed();          // su/sec
eng.getAcceleration();      // su/sec²
eng.getDeceleration();      // su/sec²
eng.getMaxTurnRate();       // deg/sec
eng.getTurnAcceleration();  // deg/sec²

// --- geometry ---
float     cr    = hull.getCollisionRadius();              // su, circumscribes bounds
JSONObject ship = Global.getSettings().loadJSON(hull.getShipFilePath());
JSONArray  bnds = ship.getJSONArray("bounds");            // flat [x0,y0,x1,y1,…] pixel pairs
List<WeaponSlotAPI> mounts = hull.getAllWeaponSlotsCopy(); // turret positions, if needed
```

Why this route beats re-parsing `ship_data.csv` / globbing `.ship` files:

- **Sandbox-safe.** Mod code can't touch `java.io.File` / `java.nio` — see
  [[starsector_script_sandbox]]. `getHullSpec` + `SettingsAPI.loadJSON` need no
  raw disk I/O from us; `loadJSON` walks the merged core+mods resource path.
- **Mods for free.** Any hull the game loaded is in the spec registry, and
  `getShipFilePath()` resolves to the mod's own `.ship`. We never special-case a
  mod.
- **Stat-accurate.** `getEngineSpec()` reflects hullmod/skill/config changes
  vanilla applies; a raw CSV read would miss them.

The scraped numbers in [`vanilla-kinematics-reference.md`](vanilla-kinematics-reference.md)
are **offline design reference only** (tuning the scale factor, sanity-checking
feel) — not a runtime data path.

## The collision polygon

`bounds` is a flat `[x0,y0,x1,y1,…]` list of points in **sprite-pixel** coords,
relative to the hull's `center` pivot, +x sprite-right / +y sprite-up. The
polygon **may be concave** (per `BoundsAPI`: "can be either concave or convex").
Frigates already run 16–18 points (wolf 16, lasher 18); fighters are simpler
(talon is 4). `collisionRadius` circumscribes the polygon and is the broad-phase
reject before any per-segment test.

Convert each point with the existing pixel→cell transform from
[[vanilla_ship_spec_scraping]] (`our.x = -vy/px, our.y = vx/px`) — the same one
already used for engine and weapon slots, so engine-FX positions and bounds land
in one coordinate space.

**Runtime caveat.** The live `BoundsAPI` (with `update(loc, facing)` baked in)
only materializes on a `CombatEntityAPI` inside a running `CombatEngine`. Our
headless/UI sim has no combat engine, so we can't lean on `getExactBounds()` —
we parse the raw `bounds` array and rotate it ourselves each tick (which
`AirBody` already does for its own facing).

## Kinematics → `AirBody` / `AirHandling`

The kinematic seam already exists: `battle/air/AirBody` (composed state +
`tickToward`) governed by an `AirHandling` profile. Shuttles fly on it today.
`AirHandling`'s shape nearly matches vanilla's `EngineSpecAPI`:

| Vanilla (`EngineSpecAPI`) | `AirHandling` | Notes |
| --- | --- | --- |
| `getMaxSpeed()` | `maxSpeed()` | × global `SCALE` (su → cells) |
| `getAcceleration()` | `accel()` | × `SCALE` |
| `getDeceleration()` | `brakingAccel()` | × `SCALE` |
| `getMaxTurnRate()` | `maxTurnRateDegPerSec()` | deg/sec — unit-invariant, no scale |
| `getTurnAcceleration()` | *(no equivalent)* | AirBody rate-limits the slew but has no accel ramp; `angVelDegPerSec` is currently informational. **Gap to close** if turn-ramp feel matters. |
| `mass` | *(steering: unused)* | collision/ramming momentum only — see ship collision in [`ships.md`](ships.md) |
| — | `lateralDriftDamping()` | **no vanilla equivalent** — our atmosphere flavor knob |
| — | `stationDamping()` | our flavor knob (hover settle) |

**Two intentional deltas from vanilla**, both flavor decisions:

- **`turnAcceleration` is unmodeled.** Vanilla ramps angular velocity to the
  cap; `AirBody` snaps to a rate-limited slew. If the "heavy hull takes a moment
  to swing its nose" feel matters (more so for ships than fighters), extend
  `AirBody` to ramp `angVelDegPerSec` toward `maxTurnRate` at `turnAcceleration`
  rather than clamping instantly. Decide per playtest — don't build it
  speculatively ([[feedback_ship_then_optimize]]).
- **Lateral drift damping has no vanilla analogue.** Vanilla space is
  frictionless; our atmosphere isn't. This is the primary "feel" dial that
  separates our craft from the source model.

## Scale — three decoupled factors

A hull's vanilla `su` are **arena units, not meters** (sized so a fleet reads at
fleet-camera zoom). Don't import `su` as physical size. Instead, three scales are
kept **independent**:

1. **Silhouette / shape** ← the vanilla `bounds` polygon, *normalized*. Faithful
   proportions; no scale baked in.
2. **Absolute footprint** ← a real-world size class per hull → meters → cells, at
   the anchor **1 cell = 1 m**. A fighter ≈ a few cars (~15 cells), a frigate ≈
   an office building (~60–80 cells). This is **not** derived from `su`. The
   normalized bounds polygon is re-stretched to the class length. Full
   calibration table + the on-map class cutoff live in [`ships.md`](ships.md).
3. **Kinematic feel** ← `getEngineSpec()` × a kinematic `SCALE`, entirely
   separate from footprint. Vanilla numbers are tuned for a zoomed-out arena, so
   at ground scale you want lower absolute cells/sec while **preserving inter-hull
   ratios** — the contrast (interceptor turn 150–225 vs bomber 30–90; capital
   sluggishness vs frigate agility) *is* the gameplay. One factor on speed + both
   accels; turn rates are angular and unscaled.

## Atmosphere leeway

Layered on top of the kinematic profile, uniformly:

- **Linear drag / lateral damping.** Atmosphere bleeds momentum — the cheapest,
  highest-leverage atmosphere knob.
- **Turn-rate nudges for readability** at a close ground camera. Flavor only;
  keep small.
- **Banking visual** derived from `angVelDegPerSec` — cosmetic, zero sim cost.
- **Altitude** is a real camera-shared **Z**, not a render-small trick — see
  [`ships.md`](ships.md) § "Scale & altitude" and the render layer's planned
  camera-Z upgrade.

## Open questions (shared)

- Do we model `turnAcceleration` (turn-ramp), or is rate-limited slew enough?
  (Defer to playtest; more likely needed for ships.)
- Where does `SCALE` live — one battle-tier constant, or per-map (different feel
  at "high altitude" vs "low pass")?
- One enriched record per hull vs separate kinematic / geometry records? See the
  loadout-vs-kinematics split in [`fighters.md`](fighters.md).
