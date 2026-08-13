# Ballistics — next session handoff

## State of play (2026-08-13)

- **S1 SHIPPED and merged to main** (fast-forward to `76a7f1be`,
  2026-08-13): core `85ec50e6`, merge of main's separation steering +
  counterattack `b3ea049c`, radius reconciliation `382b0ee6`, docs
  `76a7f1be`. Full record:
  [`complete/s1-resolver-core.md`](complete/s1-resolver-core.md).
  1450+ tests green post-merge; the `ballistics-s1` worktree/branch are
  retired.
- **S2 SHIPPED** on `worktree-ballistics-s2s3`, commit `d1664bd8` (not yet
  merged to main). Time-domain unit-contact solve, shooter lead, and
  per-weapon `MarineWeapon.roundVelocity` (`flightSec` removed). Landed
  as specced, no material deviations. Full record:
  [`complete/s2-moving-targets.md`](complete/s2-moving-targets.md).
  1482 tests green.
- Design record: [`overview.md`](overview.md). Owner decisions all
  resolved (friendly fire 0.5×, path-proximity near-miss, 0.35 incidental
  graze). NOTE one design-doc drift, corrected in the complete/ record:
  ballistics uses the pre-existing `UnitType.radius` as its contact
  circle (shared with SeparationSystem/Detonations/WorldPicker), NOT a
  new per-type stat.

## Next up: S3 — stylized visible rounds

Story doc already written: `stories/s3-visible-rounds.md`. Presentation
only (sim side is done as of S2) — primaries stop drawing as a full
static tracer line and become a traveling bolt on the round's real
flight clock (`ShotFx.Bolt`, new `ShotRenderService` sweep). Depends on
S2's `roundVelocity` values for pacing — the SMG's 45 c/s round is the
showcase for a visibly slow bolt.

Manual playtest is worth doing before S3: friendly-fire feel
(`FRIENDLY_FIRE_DAMAGE_MULT = 0.5`, `FRIENDLY_MUZZLE_CLEARANCE = 2.0`),
suppression feel under path-proximity near-miss, and how visible the S2
lead/extrapolation reads at the tuned per-weapon velocities before
investing in bolt-sweep presentation on top of it.

## Where things live (post-S2)

- `battle/combat/BallisticResolver.java` — the fire-time ray walk,
  solved in the time domain against each candidate's extrapolated
  motion; shooter lead lives inside `resolve()`. All tuning constants
  live here as the tuning surface (including `MAX_MOVER_SPEED_CELLS`,
  the S2 gather-margin scaling term).
- `battle/sim/MovementService.java` — `velX`/`velY` by-id getters read
  `MOVEMENT_VEL_X/Y` for the resolver's extrapolation.
- `battle/infantry/MarineWeapon.java` — `roundVelocity` is the real
  per-weapon cells/sec stat now (`flightSec` retired).
- `ShotService.PendingImpact` / `tickImpacts` — flight-clock damage,
  drained in `BattleSimulation`'s serial SHOTS phase (sink guards
  `isAliveById`).
- `UnitSpatialIndex.gatherAlongSegment` — ray gather; call-local dedupe,
  parallel-safe. `DoodadService.getDoodadLevelOnCell` — own-cell doodad
  level for crossings.
- `CoverAccuracyResolver` still lives — mech (`HeavyWeapons`) and
  infantry `fireSecondary` paths use it until S4 unifies direct fire.
