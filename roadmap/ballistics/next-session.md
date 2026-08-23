# Ballistics — next session handoff

## State of play (2026-08-22)

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
  Post-integration: 1593 tests green.
- **S4 SHIPPED** on `session/ballistics-direct-fire-unification`, implementation
  commit `b8c7e3e6`. Mech chaingun/SRM, handheld rockets, and ground
  Vulcan/Heavy-MG bursts use the shared resolver/source seam; contact-fused
  rounds detonate only at physical stops, while overshoots leave no phantom
  payload or impact FX. `CoverAccuracyResolver`, `ShotRaycast`, and their
  direct-fire flags/callers are retired; `ShotEndpoint` remains only for LRM
  artillery scatter. Full record:
  [`complete/s4-direct-fire-unification.md`](complete/s4-direct-fire-unification.md).
  Post-integration full suite: 1619 tests green.
- **S4a SHIPPED** on `session/ballistics-proximity-catch`, implementation commit
  `6ab24cbb`. Probabilistic doodad/edge-cover catches and friendly incidental
  contacts now smoothstep from zero at 2 cells to full chance at 8 cells;
  enemies, intended targets, structural walls, and downrange base odds remain
  unchanged. Full record:
  [`complete/s4a-proximity-catch-ramp.md`](complete/s4a-proximity-catch-ramp.md).
  Post-integration: all 35 focused ballistics/direct-fire tests pass; full
  suite 1643 tests green.
- **Swarm through-fire tuning SHIPPED** in `5f2c083d`. Secondary hostile
  contacts now use a 100% base catch chance, still multiplied by the victim's
  incoming-accuracy modifier, so a missed ray physically crossing another
  enemy transfers into it. Friendly contacts retain their 35% base chance,
  proximity ramp, and half damage. The full repository suite passes 1,787
  tests.
- **Civilian direct-fire protection SHIPPED** in `bf6fdd64`. Marine ballistic
  sources now omit civilian-faction bodies from intended and incidental unit
  contacts, while defender sources retain ordinary civilian targeting and
  collision. This is a direct-fire rule; it does not redefine civilians as a
  marine faction or suppress alien damage.
- **S4b SHIPPED** in `8881ff41`. Infantry primary rounds committed to a
  friendly victim now receive an experience-scaled trigger-discipline roll
  before emission: 20% Green, 50% Regular, 75% Veteran, 90% Elite. A hold
  spends cadence but produces no round, impact, noise, or fired telemetry, so
  training reduces friendly fire without reducing hostile DPS. Full record:
  `s4b-trigger-discipline.md`. All 2,106 repository tests pass.
- Design record: [`overview.md`](overview.md). Owner decisions all
  resolved (friendly fire 0.5×, path-proximity near-miss, 100% hostile / 35%
  friendly incidental base catches). NOTE one design-doc drift, corrected in
  the complete/ record:
  ballistics uses the pre-existing `UnitType.radius` as its contact
  circle (shared with SeparationSystem/Detonations/WorldPicker), NOT a
  new per-type stat.

## Active work

**S4b shipped in `8881ff41`:** `s4b-trigger-discipline.md`. Infantry primary
fire resolves its exact counterfactual trajectory first; only a result
committed to a friendly victim receives a Green/Regular/Veteran/Elite hold
roll. Successful holds emit no round but the caller still consumes cooldown
and burst cadence, so training cannot lower hostile DPS. All 2,106 repository
tests pass (2,105 root + 1 asset-pipeline).

No implementation story is active. The next useful ballistics step is an
in-game feel pass: watch units
fire from immediately behind low cover, dense squad firing lanes, and
midfield/target-side cover to calibrate whether the 2–8-cell transition reads
naturally without making downrange cover feel weak.

Fighter high/low/wide fire remains an explicit follow-up in air story 4f, after
fighter attacks compose the real air entity and airborne Z/roof/wall policy is
defined. Do not route current flyby-overlay attacks through the ground resolver.

Manual playtest remains useful after S4a: friendly-fire feel
(`FRIENDLY_FIRE_DAMAGE_MULT = 0.5`, proximity catch ramp 2–8 cells),
hostile through-fire strength in dense swarms and wide bursts,
suppression feel under path-proximity near-miss, and how visible the S2
lead/extrapolation and S3a projectile silhouettes read at the tuned per-weapon
velocities. Treat those as tuning observations, not a reason to reopen the
completed S3 structure.

## Where things live (post-S4)

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
- `BallisticResolver.Source` — explicit entity id, float XYZ origin, and
  faction for static/mounted callers. Ground sources use Z=0; the field is a
  deliberate future seam, not an airborne collision policy.
- `MarineSecondary.roundVelocity`, `MechWeapon.roundVelocity`, and
  `TurretKind.directRoundVelocity` derive direct-fire speeds from the former
  maximum-range presentation timings. LRM/grenade/LOCUST and aerial mounts
  retain their legacy scatter/projectile procedures.
- `ShotService.PendingImpact` / `tickImpacts` — flight-clock damage,
  drained in `BattleSimulation`'s serial SHOTS phase (sink guards
  `isAliveById`).
- `UnitSpatialIndex.gatherAlongSegment` — ray gather; call-local dedupe,
  parallel-safe. `DoodadDef.ballisticHalfHeight` carries the authored prop
  silhouette; `DoodadService.getDoodadLevelOnCell(x, y, z)` resolves stacked
  own-cell profiles for crossings. `NavigationGrid` pairs directional cover
  levels with edge-clip catch half-heights.
- `ops/battleview/ShotFx.java` — `Bolt` texture/length/width recipes + shared
  `travels()` arrival semantic. `ShotRenderService` owns bolt kinematics and
  projects ShotEvent Z through tracers, bolts, and sprites; `BattleSprites`
  loads the derived set of mod/vanilla textures into the path-keyed projectile
  cache. Both presentation bridges suppress impact FX for resolver overshoots.
