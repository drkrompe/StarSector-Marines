# Story 18 — Turret-emplacement area patrol

## Problem

Defender squads pinned to a turret emplacement ran `GuardPost` → `HoldPost`,
a *static* hold: every member leashed to its `homeCell` within a hardcoded
`HOLD_RADIUS = 6`, repositioning only to get a firing angle. Meanwhile the
attacker-side garrison upgrade (Story 17) gave captured-compound holders a real
area patrol (`GarrisonCompound` → `GarrisonPatrol`) that wanders the compound
footprint and re-clears rooms. The two postures had drifted apart: the turret
defenders just stood there.

`DefensePostKind` already carried a per-tier `patrolRadius` (LIGHT 4 → LARGE 8,
ARTILLERY 3), and `BattleSetup.linkGuardpostSquads` already set
`squad.patrolRadius` + `squad.defensePost` — but **`HoldPost` ignored
`patrolRadius` entirely**, so the field had no live consumer while a post stood.
The `DefensePostKind` / `DefensePostStamper` docs even *claimed* these squads
"patrol a small ring around the post until every turret is destroyed" — the
behavior was documented but never built.

## What shipped

The open-terrain counterpart to `GarrisonPatrol`. Where the compound garrison
rotates through indoor *rooms*, a turret emplacement sits on a beach / port /
embankment with no rooms, so the new posture wanders an **axis-aligned bounding
box** centred on the post anchor with half-extent `squad.patrolRadius`. The
radius encodes the tier flavour: a tight LIGHT/ARTILLERY box reads as "sit on
the post", a wide LARGE box as a loose perimeter sweep.

- **`GuardPostPatrol`** (`battle/infantry/`) — non-singleton action carrying the
  box (anchor + radius) resolved at replan time. Branches each tick:
  - **FALLBACK** — retreating to a new post: members walk home (mirrors `HoldPost`).
  - **ENGAGE** — target acquired: engage with the firing cell clamped to the box
    (full box is the engagement leash, per design decision — the squad masses on
    the emplacement, never chases an intruder out across the map). Mirrors
    `HoldPost`'s engagement, anchored to the post box instead of the tight home ring.
  - **INVESTIGATE** — SUSPICIOUS with a last-seen cell: lean toward the noise,
    clamped to the box.
  - **QUIET** — unaware: round a squad-scoped waypoint sampled inside the box
    (leader-gated dwell, mirroring `PatrolRoute`), firing opportunistically.
- **`GuardPost.customPlan`** now routes: `defensePost != null` (turrets standing)
  → `GuardPostPatrol`; else (released post, or a non-turret garrison post) →
  `HoldPost`. The existing `TurretDemolitionSystem` release path is unchanged —
  it clears `defensePost`, so the next replan drops back to `HoldPost`.

This finally gives `patrolRadius` a live consumer while the post stands.

## Tests

`GuardPostPatrolTest` — (1) `GuardPost.customPlan` picks `GuardPostPatrol` for a
live post and `HoldPost` once `defensePost` is cleared; (2) the QUIET wander
keeps its waypoint inside the `anchor ± radius` box across 200 advances.

## Post-commit fix

The critique pass caught a real bug: `Squad.patrolWaypointX/Y` is shared state
written by every patrol posture (`PatrolRoute`, `GarrisonPatrol`,
`GuardPostPatrol`) and nothing resets it on a posture switch, so a turret squad
inheriting a far waypoint would walk *outside* its box until it arrived there.
Fixed by treating an out-of-box waypoint as "needs re-roll" (`needsNewWaypoint`)
— the squad re-rolls in-box or holds, never walks to the stale cell.
Regression-covered by `inheritedOutOfBoxWaypointIsRerolledNotWalkedTo`.

## Shared patrol motion (landed)

The dwell-gated waypoint walk + static fire/move helpers were triplicated across
`PatrolRoute` (district node route), `GarrisonPatrol` (compound room round-robin),
and `GuardPostPatrol` (emplacement box). Extracted to **`PatrolMotion`**: a
stateless helper owning the leader-gated dwell, the lock-guarded waypoint write,
the arrival test, and the path/hold/opportunistic-fire primitives. The three
postures now differ on exactly two axes, both passed into `PatrolMotion.advance`:

- a **`WaypointSource`** (functional interface) — `next(member, squad, sim)` for
  the strategy, plus a `needsNew(squad)` default (no-waypoint / arrived) that
  `GuardPostPatrol` widens with its out-of-box staleness check;
- a **`fireWhilePatrolling`** flag — garrison/guard fire opportunistically while
  walking; the plain district patrol does not.

Dwell/arrival constants moved from `PatrolRoute` (`PATROL_DWELL_SECONDS`,
`PATROL_ARRIVAL_RADIUS`) to `PatrolMotion` (`DWELL_SECONDS`, `ARRIVAL_RADIUS`) —
their natural home now that the loop lives there. `DEFAULT_DISTRICT_RADIUS` stays
on `PatrolRoute` (route-specific). Behavior is unchanged — the full infantry +
turret suites pass, including `GarrisonPatrolTest`, `GuardPostPatrolTest`, and
`TurretDemolitionSystemTest`.
- **Release semantics.** A released turret squad (`defensePost == null`) still
  carries `holdsFireUntilKillZone`, so it falls to `HoldPost` rather than the
  search-and-destroy `RoutinePatrol` the `TurretDemolitionSystem` doc implies.
  Pre-existing; untouched here. Worth revisiting if "released → roam" is wanted.
