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
- **S3a SHIPPED** on `session/ballistics-fx-families`, commit `9c8a642f`.
  `ShotFx.Bolt` now carries texture path + explicit length/width: pulse keeps
  the custom tintable bolt, DMR reuses vanilla's gauss shell as a rail needle,
  and drone pulse reuses the small flechette as a compact cyan dart. No new
  bitmap and no sim/balance changes. Full record:
  [`complete/s3a-weapon-fx-families.md`](complete/s3a-weapon-fx-families.md).
  1513 tests green.
- **S3b SHIPPED** on `session/ballistics-target-plane-accuracy`, implementation
  commit `cd6094fd`. Intended accuracy now commits once into a visible
  lateral/elevation path; per-type vertical silhouettes gate unit contact;
  overshoots expire without false impacts. Full record:
  [`complete/s3b-target-plane-accuracy.md`](complete/s3b-target-plane-accuracy.md).
  Post-merge: 1536 tests green (1535 root + 1 asset-pipeline).
- **S3c SHIPPED** on `session/ballistics-obstacle-heights`, implementation
  commit `139fcb3b`. Doodad JSON now authors ballistic half-heights;
  `DoodadService` preserves stacked profiles by cover level; directional
  wall-edge cover carries a paired catch band. The resolver rolls either
  obstacle only after vertical overlap while structural walls stay full-height.
  Full record:
  [`complete/s3c-obstacle-catch-heights.md`](complete/s3c-obstacle-catch-heights.md).
  1541 tests green.
- Design record: [`overview.md`](overview.md). Owner decisions all
  resolved (friendly fire 0.5×, path-proximity near-miss, 0.35 incidental
  graze). NOTE one design-doc drift, corrected in the complete/ record:
  ballistics uses the pre-existing `UnitType.radius` as its contact
  circle (shared with SeparationSystem/Detonations/WorldPicker), NOT a
  new per-type stat.

## Next: contract S4 — direct-fire unification

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
lead/extrapolation and S3a projectile silhouettes read at the tuned per-weapon
velocities. Treat those as tuning observations, not a reason to reopen the
completed S3 structure.

## Where things live (post-S3c)

- `battle/combat/BallisticResolver.java` — the fire-time ray walk,
  solved in the time domain against each candidate's extrapolated
  motion; shooter lead and the target-plane XY/Z path live inside `resolve()`.
  `TargetPlaneAim` owns the single intended accuracy commit and miss clearance.
  All tuning constants live here as the tuning surface (including
  `MAX_MOVER_SPEED_CELLS`, the S2 gather-margin scaling term).
- `battle/sim/MovementService.java` — `velX`/`velY` by-id getters read
  `MOVEMENT_VEL_X/Y` for the resolver's extrapolation.
- `battle/infantry/MarineWeapon.java` — `roundVelocity` is the real
  per-weapon cells/sec stat now (`flightSec` retired); tracer-colored
  primaries derive their bolt tint from the same declaration.
- `ShotService.PendingImpact` / `tickImpacts` — flight-clock damage,
  drained in `BattleSimulation`'s serial SHOTS phase (sink guards
  `isAliveById`).
- `UnitSpatialIndex.gatherAlongSegment` — ray gather; call-local dedupe,
  parallel-safe. `DoodadDef.ballisticHalfHeight` carries the authored prop
  silhouette; `DoodadService.getDoodadLevelOnCell(x, y, z)` resolves stacked
  own-cell profiles for crossings. `NavigationGrid` pairs directional cover
  levels with edge-clip catch half-heights.
- `CoverAccuracyResolver` still lives — mech (`HeavyWeapons`) and
  infantry `fireSecondary` paths use it until S4 unifies direct fire.
- `ops/battleview/ShotFx.java` — `Bolt` texture/length/width recipes + shared
  `travels()` arrival semantic. `ShotRenderService` owns bolt kinematics and
  projects ShotEvent Z through tracers, bolts, and sprites; `BattleSprites`
  loads the derived set of mod/vanilla textures into the path-keyed projectile
  cache. Both presentation bridges suppress impact FX for resolver overshoots.
