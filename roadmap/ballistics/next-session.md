# Ballistics — next session handoff

## State of play (2026-08-13)

- **S1 SHIPPED** on branch `worktree-ballistics-s1` (worktree
  `.claude/worktrees/ballistics-s1`): core `85ec50e6`, merge of main's
  separation steering + counterattack `(merge commit)`, radius
  reconciliation `382b0ee6`. Full record:
  [`complete/s1-resolver-core.md`](complete/s1-resolver-core.md).
  1450+ tests green post-merge.
- **Branch not yet merged to main** — owner call. The worktree branch
  contains ONLY ballistics commits on top of main (post-merge), so the
  merge back is `git merge worktree-ballistics-s1` from the main checkout.
- Design record: [`overview.md`](overview.md). Owner decisions all
  resolved (friendly fire 0.5×, path-proximity near-miss, 0.35 incidental
  graze). NOTE one design-doc drift, corrected in the complete/ record:
  ballistics uses the pre-existing `UnitType.radius` as its contact
  circle (shared with SeparationSystem/Detonations/WorldPicker), NOT a
  new per-type stat.

## Next up: S2 — moving targets

Per overview: velocity extrapolation in the contact solve
(`MOVEMENT_VEL_X/Y` columns; solve closest approach on relative motion
`R(t) = (U₀−O) + (w−v·d)t`) + shooter lead (aim at predicted position,
`t ≈ dist/v`), and round velocity becomes a real per-weapon stat
(S1 derives it: `dist/flightSec` for projectile-sprite weapons, else
`BallisticResolver.DEFAULT_ROUND_VELOCITY = 60`). Write
`stories/s2-moving-targets.md` first.

Manual playtest before S2 is worth it: friendly-fire feel
(`FRIENDLY_FIRE_DAMAGE_MULT = 0.5`, `FRIENDLY_MUZZLE_CLEARANCE = 2.0`),
suppression feel under path-proximity near-miss, and whether
misses-fly-on reads well with line tracers (S3 owns the visible-round
polish).

## Where things live (post-S1)

- `battle/combat/BallisticResolver.java` — the fire-time ray walk; all
  tuning constants live here as the tuning surface.
- `ShotService.PendingImpact` / `tickImpacts` — flight-clock damage,
  drained in `BattleSimulation`'s serial SHOTS phase (sink guards
  `isAliveById`).
- `UnitSpatialIndex.gatherAlongSegment` — ray gather; call-local dedupe,
  parallel-safe. `DoodadService.getDoodadLevelOnCell` — own-cell doodad
  level for crossings.
- `CoverAccuracyResolver` still lives — mech (`HeavyWeapons`) and
  infantry `fireSecondary` paths use it until S4 unifies direct fire.
