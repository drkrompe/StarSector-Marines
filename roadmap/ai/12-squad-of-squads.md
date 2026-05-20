# 12 — Squad-of-Squads (Commander Tier)

**Parked design doc.** Captures the architectural shape of the
higher-order planner that sits *above* per-squad GOAP. Not scheduled
yet — Tier 2 story implementations don't require it, and the per-squad
slice has room to grow without it. Document so the shape is committed
before any story implementation accidentally bakes a "squad picks its
own objective" assumption into the codebase.

## Why this exists

Per-squad GOAP picks *tactical* actions ("engage this enemy", "approach
this firing position"). It does not pick *strategic* objectives ("Squad
A clears the lab; Squad B holds the comms tower flank"). Today every
squad runs `EliminateEnemiesGoal` and converges on the same nearest
contact — fine for one-squad-vs-one-squad arenas, **wrong for
multi-squad missions with spatially distinct objectives**.

The triggering missions:

- **Conquest** — per-zone progressive objective taking. Multiple charge
  sites scattered across a 100×50 map. Without a commander, marine
  squads dogpile the nearest defender cluster while the rest of the map
  is unattended.
- **Assault** — search-and-destroy on a wider map. No fixed objective
  sequence; squads need to sweep distinct sectors to find and eliminate
  enemy contacts. Identical dogpile problem.

Both missions are *fundamentally multi-squad coordination* problems.
The story bank (`10-tactical-stories.md`) is nine per-squad stories;
none of them solve this. They live one layer below where this problem
needs to be solved.

## Three-tier architecture

```
Tier C — Commander / MissionCommand        (slow cadence: ~2–5s + event-driven)
  Owns: per-faction strategic state. Decides which squad goes where.
  Outputs: each squad's assignedObjective.

Tier B — Squad GOAP                         (existing — ~2s replan)
  Owns: per-squad tactical state. Plans actions to satisfy assignment.
  Inputs: squad.assignedObjective from Tier C.

Tier A — Unit execution                     (existing — every tick)
  Owns: per-unit action tick. Executes the current plan step.
```

Tier C runs at the **commander cadence** — slower than tactical replan,
plus event triggers (a zone flips, a squad gets wiped, a charge site
explodes). This is also a parallelism candidate: one Commander per
faction, the two run independently.

## Conquest commander shape

> **First pass shipped (`ConquestCommand`).** Marine-side, wired into
> `BattleSetup.createConquest`. The full per-zone status tracking + scored
> objective registry below queues behind doc 15's influence map landing;
> the v1 shape is a lateral-strip partition with sticky squad assignment
> and forward-most-defender targeting. See *First-pass shape: lateral
> strips* below for what's actually in code, and *Improvement path* for
> what it grows into.

### First-pass shape: lateral strips

`ConquestCommand` partitions the map into `STRIP_COUNT = 3` equal-width
strips perpendicular to the `TraversalAxis`. At first tick:

1. Bucket every walkable zone into a strip by its centroid's lateral
   coordinate (x for SOUTH_TO_NORTH, y for WEST_TO_EAST).
2. Cache each zone's forward-axis centroid in a primitive `float[]`
   indexed by zone id for cheap nearest-zone lookups per slow tick.

Per slow tick — per marine squad:

1. Look up the squad's strip. If unset, classify by the squad's current
   centroid's lateral coord and memoize.
2. Walk the strip's zones, find the *nearest* defender-occupied zone
   by forward-axis distance to the squad centroid, with a positive-
   forward bias (forward defenders preferred over equally-close backward
   ones — backward is the fallback when no forward defender exists).
3. If the nearest defender zone IS the squad's current zone, write
   `null` and let `EliminateEnemiesGoal` handle the in-zone fight. Same
   if no defender zone exists.
4. Otherwise write `squad.assignedObjective = clearZone(squad.id,
   targetZone)` — idempotent (same record kept across ticks when the
   chosen zone is unchanged, so an in-flight squad plan stays stable).

The slow-tick cycle then iterates the squad through defender positions
one zone at a time: clear the nearest forward defender zone → next
slow tick → next nearest forward defender zone → and so on.

**Why nearest, not deepest.** The first pass picked the strip's
*forward-most* (deepest) defender zone, which produced a "drop off and
drive inland" bug — squads pointed at fortress-deep zones got `customPlan`
BFS paths that took the shortest *open* route, walking past LZ defenders
without entering their zones. Nearest-zone targeting keeps each slow tick
to a single-zone-hop plan, so the squad actually fights through the
strip front-to-back instead of teleporting past it.

Squad → strip is **sticky for v1.** A squad's strip is fixed at first
observation and doesn't change even if the squad drifts laterally. That
keeps the partition behavioral signal clean — if we let squads migrate
between strips on every centroid drift, the partition stops meaning
anything.

This is a *coarse approximation* of the proper frontline-with-bulges
treatment. It's enough to visibly spread marines across the frontage
(no more dogpile-on-nearest-defender) but it doesn't react to bulges,
doesn't pull squads from clear strips into engaged ones, and treats
every defender position as equal-priority within a strip.

### Improvement path

The strip partition is the durable shape; what grows is the read off
the field and the reallocation logic. Each step is independently
shippable.

**Stage A (next): contour-aware target picking.** Once doc 15's
influence map lands, replace "forward-most defender-occupied zone in
strip" with "the strip's segment of the `friendly - hostile`
zero-crossing contour, projected back to the nearest zone." Same
shape, much sharper read — squads stop chasing a lone holdout deep in
their strip when the bulk of defender pressure is somewhere else.

**Stage B: cross-strip reallocation on bulge detection.** When the
contour shows a concavity in our line (defenders pushing back into our
territory), pull squads from neighboring strips whose own segments are
stable into the bulging strip. Mobility eligibility = "your assigned
target zone is `zoneClear`" — a squad with no live work in its lane is
the cheapest to redirect. This is where the "fixed strips in v1"
constraint gets retired.

**Stage C: dynamic strip count / non-uniform widths.** Today
`STRIP_COUNT = 3` regardless of map size or squad count. Stage C
derives it from squad count + map lateral extent, possibly with
biome-aware boundaries (a strip that runs through the fortress
courtyard is narrower than a strip through open beach).

**Stage D: defender-side `ConquestDefenseCommand`.** Mirror shape for
the defender — anchor garrisons by strip, pull from neighbors when a
strip is breached. Needs the perception layer (doc 15) genuinely
landed because a defender commander reading marine ground truth is
exactly the omniscience cheat doc 15 calls out.

### Legacy shape — for reference only

State the *eventual* Conquest Commander tracks per faction (parked
until Stage A onwards starts asking for it):

- **Zone status** — for each known zone: `UNCONTESTED` / `CONTESTED` /
  `OURS` / `THEIRS`. Read via `ZoneQueries.zoneClear` (already shipped)
  plus a "did anyone of mine die here recently" heuristic.
- **Objective registry** — list of mission objectives (charge sites,
  capture points). Each carries a zone id, a priority, and a status
  (pending / in-progress / done).
- **Squad assignments** — `Map<squadId, ObjectiveAssignment>`. The
  assignment is the link Tier C hands down to Tier B.

`ObjectiveAssignment` shape sketch (subject to refinement):

```java
record ObjectiveAssignment(
    int squadId,
    AssignmentKind kind,          // CLEAR_ZONE / HOLD_NODE / RUSH_OBJECTIVE / SUPPORT
    int targetZoneId,             // -1 if not zone-scoped
    TacticalNode targetNode,      // null if not node-scoped
    int objectiveId               // mission objective backref; -1 if pure tactical
) {}
```

### Per-tick (slow) commander loop

1. Refresh zone statuses (one pass over alive units + ZoneQueries).
2. For each pending mission objective, check if a squad is assigned and
   alive. If not, re-assign.
3. Score (squad, objective) pairs — distance, current alert level,
   squad health. Use `RoleAssigner` for the matching (it already
   handles N-slot scored assignment).
4. Write `assignedObjective` onto each squad.
5. For unassigned squads (more squads than objectives), pick a support
   role — patrol toward a contested zone, hold a flank.

### Per-squad GOAP picks up the assignment

`EliminateEnemiesGoal` stays as the ambient default. New goals come in
that read `squad.assignedObjective` and report relevance based on
"are we doing this objective?":

- `ClearAssignedZoneGoal` — relevance ~1.0 when `assignment.kind ==
  CLEAR_ZONE` and the zone isn't yet clear. `MISSION` priority bucket
  (overrides `EliminateEnemies` automatically per Tier 1's goal-priority
  system).
- `HoldAssignedNodeGoal` — relevance ~1.0 when `kind == HOLD_NODE`.
  `MISSION`.
- `RushAssignedObjectiveGoal` — relevance gated by distance to the
  objective; spawns Story J's sabotage cordon flow once the squad is
  in the right zone.

Plans get built from Stage 2 tactical actions (Stories J/K/L/G/etc.) as
those land. The commander tier doesn't author tactical actions — it
just *assigns the context* the tactical layer plans inside.

## Assault commander shape

Different state model:

- **Sector grid** — partition the map into ~4-9 sectors at battle
  start. Each sector has a "last known contact" timestamp.
- **Squad → sector** assignment. As sectors get cleared (no contact in
  N seconds), the commander shrinks the active-sectors set and re-
  assigns squads to overlap unresolved sectors.
- **Contact convergence** — when one squad spots multiple enemies, the
  commander may pull a second squad in for support before the contact
  escalates.

Same `assignedObjective` shape (`AssignmentKind` adds `SWEEP_SECTOR`
and `CONVERGE_ON_CONTACT`). Same per-squad goal pickup mechanism. The
"sweep a sector" tactical implementation reuses Story K's room-clear
sweep — applied to the cells inside the sector instead of a building.

## What Tier 2 stories assume from the commander

A clean contract so we don't build stories that fight the future
commander:

- **Stories J / K / L (zone/portal-aware).** Assume the squad's
  *current target zone* is set elsewhere — by the commander in the
  future, by a manual test fixture today. The story actions read
  `squad.assignedObjective.targetZoneId` (or a Stage-2-introduced
  field like `squad.currentZoneTarget`); they don't compute their own
  zone from "nearest contact's cell."
- **Stories B / H (goal-priority dependent).** Already use
  `Goal.Priority` bucket — `MISSION`-bucket assigned-objective goals
  outrank `SURVIVAL` / `ENGAGEMENT`. The commander assigns the
  MISSION-bucket goal indirectly via the assignment.
- **Stories C / F (per-member coordination).** Self-contained within a
  squad. Commander-orthogonal.
- **Stories D / E (cross-squad emergence).** D is the *closest* story
  to commander territory but stays per-squad — patrol intercept emerges
  from alert spread, not from explicit commander orders. A commander
  could elevate it ("converge on contact in sector X"), but the story
  works without one.

## Squad-level stubs for development

While Tier 2 stories ship before the commander, each gets a manual-
assignment stub:

```java
// In a test fixture / debug menu / one-time call site:
squad.assignedObjective = new ObjectiveAssignment(
    squad.id, AssignmentKind.CLEAR_ZONE,
    /* zone */ 7, null, -1);
```

`assignedObjective = null` is the default. Goals that need an assignment
read it and report `relevance() = 0` when null — they're inert without
a commander. The story tests instantiate assignments directly. Live
gameplay before the commander ships just won't trigger those goals;
the squad falls back to `EliminateEnemies`, which is the Stage 1
behavior (with the Tier 1 target-picker / pursuit-gate upgrades).

## Out of scope here

- **Cross-faction commander coordination.** Each faction has its own
  commander; they don't talk. Story D's flank-angle emergence is the
  cheapest way to get "two squads cooperating" without explicit
  coordination state.
- **Player override.** No "issue order" UI for the player to override
  the commander. The commander is autonomous for AI factions and not
  used for player-controlled squads (player squads run on player
  inputs from the briefing/loadout layer + per-squad GOAP as today —
  no commander needed unless the player explicitly delegates).
- **Resource economy / reinforcement.** Battles are transient; no
  spend-from-pool logic at the commander level. If a squad gets wiped,
  it's gone; commander reallocates the rest.
- **Detailed scoring weights.** Commander assignment scoring is a Stage
  2.5 / 3 problem; this doc is shape-only. Will need its own subagent
  task with playtest tuning.

## Status

**Active — Stage 1 spine + first two commanders shipped.** The commander
layer landed on top of Tier 1 (no Slice 2 dependency in the end — the
spine is mission-agnostic).

What's in code:

1. ✅ `command.ObjectiveAssignment` record + `Squad.assignedObjective`
   field. Convenience factories: `clearZone`, `holdNode`,
   `rushObjective`, `support`.
2. ✅ `command.AssignmentKind` enum (`CLEAR_ZONE`, `HOLD_NODE`,
   `RUSH_OBJECTIVE`, `SUPPORT`).
3. ✅ `command.MissionCommand` interface + `NOOP` default; commander
   slow-tick at `BattleSimulation.COMMANDER_TICK_PERIOD` (2.5s).
4. ✅ `ai.goap.goals.ClearAssignedZoneGoal` — MISSION-priority, reads
   `squad.assignedObjective.kind == CLEAR_ZONE`. Composes with Story K's
   `EnterZone` + `ClearZone` via the shared
   `ZoneQueries.synthesizeZonePushPlan` helper (also used by
   `SecureObjectiveZone`).
5. ✅ `command.SabotageCommand` — marine-side commander for SABOTAGE.
   Objective-cluster partition: routes non-planter squads to the closest
   unfinished `ChargeSiteObjective` zone.
6. ✅ `command.ConquestCommand` — marine-side commander for CONQUEST.
   Lateral-strip partition: each squad sticky-assigned to one of
   `STRIP_COUNT=3` strips perpendicular to `TraversalAxis`; per slow
   tick gets `CLEAR_ZONE` pointed at the forward-most defender-
   occupied zone in its strip.

What's not yet in code (queued behind playtest + doc 15):

- `HoldAssignedNodeGoal` (Story H — last-stand `HoldPosition` on
  `MUST_HOLD` nodes is still the gating tactical story).
- `RushAssignedObjectiveGoal` for non-zone objectives.
- `AssaultCommand` for code-`MissionType.ASSAULT` (search-and-destroy
  sectoring). Pattern is a third partition shape — uniform sweep
  tiles, distinct from SabotageCommand's objective clusters and
  ConquestCommand's lateral strips.
- Defender-side commanders. Gated on doc 15's perception layer so
  the defender can't read marine ground truth.
- The "richer commander shape" originally sketched in this doc
  (per-zone status tracking, scored objective registry, dynamic
  re-assignment) — see *Improvement path* under each commander
  section above for what individual stages of that look like.

## Cross-references

- [Story bank](10-tactical-stories.md) — per-squad tactical moments
- [Tier 1 foundation](11-stage2-foundation.md) — the planner pieces
  this builds on
- Memory: `[[mission_type_flavors]]`, `[[long_term_vision_sub_game]]`
