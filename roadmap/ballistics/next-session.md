# Ballistics — next session handoff

## State of play (2026-08-19)

- **S1 SHIPPED and merged to main** (fast-forward to `76a7f1be`,
  2026-08-13): core `85ec50e6`, merge of main's separation steering +
  counterattack `b3ea049c`, radius reconciliation `382b0ee6`, docs
  `76a7f1be`. Full record:
  [`complete/s1-resolver-core.md`](complete/s1-resolver-core.md).
  1450+ tests green post-merge; the `ballistics-s1` worktree/branch are
  retired.
- **S2 SHIPPED and merged to main** (`d1664bd8`, merge `07eaebf0`).
  Time-domain unit-contact solve, shooter lead, and
  per-weapon `MarineWeapon.roundVelocity` (`flightSec` removed). Landed
  as specced, no material deviations. Full record:
  [`complete/s2-moving-targets.md`](complete/s2-moving-targets.md).
  1482 tests were green at its commit boundary.
- **S3 SHIPPED** on `codex/ballistics-s3`, commit `ebc3023d`. Tracer-colored
  primaries now render as traveling tinted bolts on their real S2 flight
  clocks; field-rifle/SMG shells stay explicit sprites. A shared
  `ShotFx.travels()` semantic also corrected arrival-timed impact FX in both
  standalone and hybrid presentation. The generated 64×256 white-base bolt
  has a strict dimensions/alpha/grayscale asset contract. Full record:
  [`complete/s3-visible-rounds.md`](complete/s3-visible-rounds.md).
  1507 tests green.
- Design record: [`overview.md`](overview.md). Owner decisions all
  resolved (friendly fire 0.5×, path-proximity near-miss, 0.35 incidental
  graze). NOTE one design-doc drift, corrected in the complete/ record:
  ballistics uses the pre-existing `UnitType.radius` as its contact
  circle (shared with SeparationSystem/Detonations/WorldPicker), NOT a
  new per-type stat.

## Active: S3a — weapon FX families

Contract: [`stories/s3a-weapon-fx-families.md`](stories/s3a-weapon-fx-families.md).
Reuse the vanilla railgun shell and small flechette sprites to give the DMR and
drone pulse distinct silhouettes while the pulse rifle keeps S3's custom
white-base bolt. Generalize `ShotFx.Bolt` with sprite path + explicit width;
load the derived path set through the shared projectile cache. No sim or balance
changes.

## After S3a: contract S4 — direct-fire unification

S4 is outlined in `overview.md` but does not yet have a story contract. Write
that contract before implementation. Scope: mech chaingun and turret direct-
fire spray kinds adopt `BallisticResolver`; retire their `ShotRaycast` paths
and the remaining abstract cover-accuracy application; preserve projectile
sprites/weapon FX while damage and incidental contacts use the modeled round
pipeline. Confirm which turret kinds are truly direct fire before sweeping —
mortar/LOCUST arcs are projectile/AoE paths, not resolver candidates.

Manual playtest remains useful before tuning S4: friendly-fire feel
(`FRIENDLY_FIRE_DAMAGE_MULT = 0.5`, `FRIENDLY_MUZZLE_CLEARANCE = 2.0`),
suppression feel under path-proximity near-miss, and how visible the S2
lead/extrapolation and S3 bolt lengths read at the tuned per-weapon
velocities. Treat those as tuning observations, not a reason to reopen the
completed S3 structure.

## Where things live (post-S3)

- `battle/combat/BallisticResolver.java` — the fire-time ray walk,
  solved in the time domain against each candidate's extrapolated
  motion; shooter lead lives inside `resolve()`. All tuning constants
  live here as the tuning surface (including `MAX_MOVER_SPEED_CELLS`,
  the S2 gather-margin scaling term).
- `battle/sim/MovementService.java` — `velX`/`velY` by-id getters read
  `MOVEMENT_VEL_X/Y` for the resolver's extrapolation.
- `battle/infantry/MarineWeapon.java` — `roundVelocity` is the real
  per-weapon cells/sec stat now (`flightSec` retired); tracer-colored
  primaries derive their bolt tint from the same declaration.
- `ShotService.PendingImpact` / `tickImpacts` — flight-clock damage,
  drained in `BattleSimulation`'s serial SHOTS phase (sink guards
  `isAliveById`).
- `UnitSpatialIndex.gatherAlongSegment` — ray gather; call-local dedupe,
  parallel-safe. `DoodadService.getDoodadLevelOnCell` — own-cell doodad
  level for crossings.
- `CoverAccuracyResolver` still lives — mech (`HeavyWeapons`) and
  infantry `fireSecondary` paths use it until S4 unifies direct fire.
- `ops/battleview/ShotFx.java` — `Bolt` composition + shared `travels()`
  arrival semantic. `ShotRenderService` owns bolt kinematics; `BattleSprites`
  loads `graphics/fx/round_bolt.png` into the path-keyed projectile cache.
