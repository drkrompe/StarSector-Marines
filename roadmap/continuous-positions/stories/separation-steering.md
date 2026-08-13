# Separation steering — soft collision between ground units

The first deferred follow-up of the continuous-positions migration
(`overview.md` § Stories, "unit-unit separation steering"). Units are
continuous points with a per-type `UnitType.radius`; nothing stops two units
from standing on the same point. This story adds a post-movement relaxation
pass that gently pushes overlapping ground units apart — soft collision, not
hard blocking.

## Goal

- Overlapping live ground units drift apart until their radii no longer
  intersect, over a few ticks (relaxation, not instant pop).
- Big units displace small ones: a mech wades through marines; marines part
  around it.
- Nobody ever gets pushed into a non-walkable cell — units slide along walls.
- Pathing, arrival semantics, occupancy, and fog are untouched. This is a
  position-space nudge, invisible to planning.

## Non-goals (v1)

- Hard collision / mutual exclusion. Two units *may* still overlap
  transiently (doorway squeezes, spawn bursts); the system relaxes it, never
  forbids it.
- Predictive avoidance (velocity-obstacle / RVO steering). Purely reactive
  overlap resolution.
- Air units, vehicles, drones — they have their own kinematics
  (`AirBody`, `GroundBody`) and stay out of scope.
- Parallelizing the pass. Single-threaded v1; profile first
  (`TickProfile` phase makes the cost visible from day one).

## Design

### New system: `sim/SeparationSystem`

Stateless consumer in the Services/Systems shape — its only fields are
reusable scratch buffers (a `LongBucket` and per-dense-slot `float[]`
impulse accumulators, grown to roster capacity). Lives next to
`MovementService` in `battle/sim/`.

### Tick slot

`BattleSimulation.tick()`, immediately after `flushPendingOccupancyDeltas()`
(currently ~:1005) and before `flushPendingSpawns()`:

- **After UPDATE_UNITS** — that phase is a parallel dispatch; all
  `advanceAlongPath` position writes have landed and we can do neighbor
  reads + position writes safely on one thread.
- **Before APPEARANCE** — `facingSystem`/`mechLocomotionSystem` read final
  POSITION + VEL, so poses reflect post-separation state.

New `TickProfile.Phase` constant (`SEPARATION`) + `lap` call so the debug
panel stays honest.

### Participants

All live entities in the roster's grid-occupant set with `radius > 0`,
partitioned:

- **Movable** — infantry, mechs, civilians (anything with a mover).
- **Immovable** — `UnitRole.STRUCTURE` + turrets: they push, are never
  pushed (infinite mass). Emplaced units keep their anchor.

Dead units never participate (`gather` already skips `!isAliveById`).

### Algorithm — two-phase, order-independent

**Phase 1: accumulate.** For each movable unit `a`:

1. `getUnitIndex().gather(x, y, QUERY_RADIUS, scratch)` for candidates.
   The index is a tick-start snapshot, so **re-read live `world.x/y`** for
   both ends of every pair; the snapshot only prunes. `QUERY_RADIUS`
   = 2 × max radius + per-tick motion margin ≈ **1.5 cells**.
2. For each candidate `b` with `b != a`, alive, radius > 0: let
   `d = pos(a) − pos(b)`, `overlap = r(a) + r(b) − |d|`. If `overlap ≤ 0`,
   skip.
3. Split the correction by an inverse-mass weight `w = m(b) / (m(a) + m(b))`
   with `m = radius²` (mech ≈ 4× marine mass → marines do ~80% of the
   yielding). Immovable `b` ⇒ `w = 1` (a yields fully); immovable `a`
   accumulates nothing.
4. Accumulate `w · overlap · STIFFNESS · (d/|d|)` into `a`'s impulse slot
   (indexed by dense roster index). Each unit gathers its own neighbors, so
   every pair is naturally evaluated from both sides — no half-pair
   bookkeeping.
5. **Coincident-point tiebreak:** if `|d| < 1e-4`, derive a deterministic
   unit vector from the pair's entity ids (hash → angle) so stacked spawns
   fan out identically every run. Never use RNG here.

**Phase 2: apply.** For each movable unit with a non-zero impulse:

1. Clamp magnitude to `MAX_PUSH_SPEED × dt` (relaxation cap — a full
   marine-on-marine stack resolves in ~0.4 s, no teleport pop).
2. Compute `(nx, ny)`; walkability guard via `sim.getGrid()`:
   `isWalkable(floor(nx), floor(ny))` — on failure try the X-only slide,
   then the Y-only slide, else drop the impulse. Units slide along walls
   instead of jamming or clipping through. (If diagonal corner-cutting
   shows up in playtests, tighten with `isEdgePassableAt` on the crossed
   edge — deferred until observed.)
3. `world.setPos(id, nx, ny)` and **fold the applied displacement into
   `MOVEMENT_VEL_X/Y` additively** (it's "velocity applied this tick";
   `FacingSystem` derives pose from it, so shoved units animate instead of
   ghost-sliding). If idle-unit pose twitch shows up, add a deadband —
   tuning knob, not v1 structure.

### Constants (initial, all on `SeparationSystem`)

| Constant | Value | Why |
|---|---|---|
| `QUERY_RADIUS` | 1.5f cells | 2 × mech radius (0.6) + margin |
| `STIFFNESS` | 0.5f | half the overlap per tick before clamp |
| `MAX_PUSH_SPEED` | 1.5f cells/sec | < walk speed (2.0); separation never outruns intent |

### Why the existing pieces make this cheap

- `UnitType.radius` shipped in phase-1-storage-flip; per-type values
  already tuned (marine 0.3, mech 0.6, turret 0.45).
- `UnitSpatialIndex.gather` is the zero-alloc radius query; rebuilt at
  tick start (`rebuildSpatialIndices`, `BattleSimulation` ~:946).
- Arrival pin (`advanceAlongPath` :188-194) means a shoved settled unit
  *stays* shoved — crowd relaxation around a shared destination composes
  with the existing destination-reservation occupancy penalty for free.
- Occupancy is rebuilt from positions at next tick start, so the nudge
  feeds back into A* costs automatically.

## Known interactions / risks

- **`atCell` gates:** a unit shoved > `ARRIVE_RADIUS` (0.35) off a post it
  was holding will fail `atCell` and its behavior will re-path back —
  correct (it reclaims the post) but could ping-pong against a persistent
  crowd. `MAX_PUSH_SPEED < moveSpeed` bounds this: the holder wins the
  tug. Watch for oscillation in the crowd test.
- **Chokepoint flow:** units queuing through a 1-cell door will compress
  (overlap allowed) and relax on the far side — intended v1 behavior.
- **Emplacement anchors** (`battle_tactical_node_anchor_contract`):
  garrison units near non-walkable anchor structures must not be pushed
  onto the anchor cell — the walkability guard covers this since anchors
  are non-walkable.
- **Swarm balance:** SWARM_RUNNER melee packs currently converge on one
  point; separation will spread the pack and may change effective DPS-on-
  target. Flag for the swarm-tuning pass (already deferred to playtest).

## Test plan (headless, JUnit)

1. **Stack relax:** spawn 8 marines on one point in open ground; tick 2 s;
   assert every pair distance ≥ r+r − ε and displacement was gradual
   (no single-tick jump > `MAX_PUSH_SPEED × dt` + ε).
2. **Wall slide:** stack 4 marines in a cell adjacent to a wall run; tick;
   assert all end on walkable cells, none crossed the wall line.
3. **Mass asymmetry:** mech + marine overlapped; assert the marine's total
   displacement ≈ 4× the mech's.
4. **Immovables:** marine overlapping a turret; turret never moves, marine
   resolves fully.
5. **Determinism:** identical setup twice (incl. coincident points) →
   bit-identical final positions.
6. **Non-interference:** a moving unit crossing an empty map has
   bit-identical trajectory with the system on vs off.

## Slices

1. **S1 — system + tick slot:** `SeparationSystem` (accumulate/apply,
   walkability guard, tiebreak), `TickProfile.SEPARATION`, wiring in
   `BattleSimulation.tick()`. Tests 1, 2, 5, 6.
2. **S2 — mass + immovables:** radius² weighting, structure/turret
   immovability, VEL fold-in for facing. Tests 3, 4.
3. **S3 — playtest knobs pass:** deadband if pose twitch observed,
   chokepoint/oscillation eyeballing, swarm note handed to the tuning
   backlog. (Manual; may ship as "no change needed".)

## Shipped (2026-08-13, branch `worktree-separation-steering`)

**S1 + S2 are implemented, critiqued, and green** (1441-test full suite):

- `f2fc6ab0` — S1: `SeparationSystem` (two-phase accumulate/apply, id-hash
  coincident tiebreak, wall-slide walkability guard), `TickProfile.SEPARATION`,
  tick wiring, tests 1/2/5/6.
- `acda9fc7` — S2: radius² inverse-mass weighting, immovable emplacements,
  velocity fold-in (via `entityWorld` — `MovementService.setVelocity` is
  private), tests 3/4 — **plus fixes for all 8 findings of a 3-lens
  adversarial critique pass**, notably:
  - **Immovability keys on `UnitType.isStatic()`**, not the story's literal
    "`UnitRole.STRUCTURE` + turrets": nothing ever stamps the STRUCTURE
    role, so drone hubs (role `DRONE_HUB`, static type, spawned **without**
    a MOVEMENT component) would have been movable — shoved off their anchor
    and then crashing the tick in the velocity fold-in. The role check stays
    for forward-compat; a hub regression test now exists.
  - The wall-slide test's original 4-marine stack relaxed without ever
    reaching the wall (verified by bit-exact replication) — the guard was
    unexercised; now 8 marines + a pressed-against-the-wall assertion.
  - Added a production-tick-loop integration test (drives
    `BattleSimulation.advance()`, not `separation.tick()` directly) and
    velocity fold-in assertions; reworded a false tick-slot Javadoc
    invariant.

**Deviations from plan:** none in the algorithm/constants; participant
predicate uses `!world.hasKinematics(id)` to exclude drones (they ARE dense-
roster ground units, contrary to this doc's assumption that they'd need a
role check — their position is slaved to an `AirBody` each tick).

**Test-authoring gotcha discovered:** a bare `EntitySpec(UnitType.TURRET, …)`
spawns with hp=0 (placeholder stat block `MapTurret#create` normally
overwrites) — `isAliveById` false, silently participates in nothing. Headless
tests must set `.health(…)` explicitly on TURRET/hub specs.

**S3 progress (2026-08-13):** playtest confirmed the predicted pose twitch —
settled units vibrated body rotation from residual jostle. Fixed with the
planned deadband, placed on the *consumer* side:
`FacingSystem.MIN_TRAVEL_SPEED = 0.5` cells/sec — applied velocity below it
derives no travel delta (unit reads as standing), catching any small-velocity
source, not just separation, while staying under the slowest genuine mover
(mech, 1.15). **Remaining in S3:** chokepoint/oscillation eyeballing,
swarm-spread balance note. Move this doc to `complete/` once those resolve.

## Cross-refs

- `overview.md` — coordinate convention, radius provenance.
- `complete/phase-2c-worklist.md` — follow-ups list this story came from.
- Memory: `feedback_scored_over_binary_gates` (scalar impulses, no sticky
  gates), `battle_services_systems`, `spatial_unit_index`.
