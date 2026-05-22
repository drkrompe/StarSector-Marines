# Reinforcement system

> "Adding more to the fight" as a concept. The battle sim's universal
> hook for any source of trigger that should bring fresh units onto the
> map, via any delivery means. The system stays generic; the convoy /
> shuttle / walk-in providers do the work.

## Motivation

We started this design discussion from convoy `stage2.md` item 1
(Conquest reinforcement integration), which would have hardcoded
"morale-broken garrison spawns a truck." That collapses two
independent axes — **why** reinforcements arrive and **how** they
arrive — into one path. Splitting them gives us:

- Many trigger sources (garrison depletion, objective lost, scripted
  timer, captain skill, mission-briefing flag) feeding one event.
- Many delivery means (walk-in, convoy, shuttle) consuming the event
  and answering whichever way the map and side can.
- A natural fallback ladder — a road-less map quietly substitutes
  walk-in for convoy instead of failing.

The design philosophy is [[feedback_world_reactive_over_expressive]]:
the world has many reasons to push back, not one hardcoded one, and the
city uses whatever it has.

## Architecture

### Request

```
ReinforcementRequest(
    int side,            // attacker / defender / faction id
    Reason reason,       // enum — diegetic, not a behavior switch
    Strength strength,   // SMALL / MEDIUM / LARGE
    int rallyX, rallyY,  // optional; -1,-1 → dispatcher picks
    long postedTick
)
```

The `Reason` is informational — it shows up in briefings / after-action
reports / commander chatter, never as a code branch. Means providers
must work the same way regardless of which reason posted the request.

### Means provider interface

```
interface ReinforcementMeans {
    boolean canFulfill(BattleSimulation sim, ReinforcementRequest req);
    void    dispatch  (BattleSimulation sim, ReinforcementRequest req);
}
```

`canFulfill` is the feasibility check: convoy needs a road graph and
a reachable rally; shuttle needs a clear LZ; walk-in needs a perimeter
cell on the right side of the map. `dispatch` actually posts the
spawn(s) — same hooks the existing `ShuttleAssignment` /
`ConvoyAssignment` paths use today.

### Service

Naming: this is a **`ReinforcementService`**, not a `*System`. Per
[[battle_services_systems]] the project convention is *Service =
stateful, constructor-injected into `BattleSimulation`*, *System =
stateless tick consumer*. We own the queue, the trigger list, the
means list, and per-side ticket counts — that's state — so Service is
the right name. `GroundSystem` / `AirSystem` stay `*System` because
they're stateless consumers of `Vehicle` / `Shuttle` data.

`ReinforcementService` owns:
- Pluggable list of `Trigger` (each: `boolean check(sim, side)` +
  request builder).
- Ordered list of `ReinforcementMeans` — priority for fallback.
- Per-side request queue.
- Per-side ticket budget (see "Ticket budget" below).

Each tick:
1. For each trigger, if `check` fires, build a request and enqueue.
2. Drain the queue. For each request: if the side has tickets, iterate
   the means list in priority order; first provider that returns
   `canFulfill = true` wins, `dispatch`es, and decrements that side's
   ticket count. If no provider fulfills, log the request as a
   bugged-map diagnostic and drop it.

The service tracks requests, not in-flight spawns — once dispatched,
the means provider's output (a `Vehicle` / `Shuttle` / squad) lives in
the normal sim lists and ticks like any other unit.

### Ticket budget (per side, mission-driven)

Per-side ticket count is the simple cooldown / budget model. Each
successful `dispatch` decrements the requesting side's count; at zero,
triggers still fire but the service skips dispatch (and logs nothing —
this is expected, not a failure).

**Mission config carries the shape.** Refill rules belong to the
mission, not the service. Conquest-sized matches want large mid-/late-
battle waves; Assault-sized matches want a small attacker drip; raid
maps want defender-only one-shot reinforcement. The service reads:

```
ReinforcementBudget(
    int defenderStartingTickets, int defenderRefillPerSec, int defenderMax,
    int attackerStartingTickets, int attackerRefillPerSec, int attackerMax
)
```

…off the mission config at battle start. Refill cadence is a simple
per-second accumulator; "waves" emerge from per-tick refill clamped to
`max`. More elaborate scripted wave shapes can ship later as a
`ScriptedBudget` variant; the simple-counter form covers most missions.

The attacker side already has shuttle drops modeled with something
like this today — folding it into `ReinforcementService` is the
natural unification (see Decisions §1).

### Triggers (v1 set)

| Trigger | Reason posted | Notes |
|---|---|---|
| `GarrisonDepletedTrigger` | `GARRISON_DEPLETED` | Defender squad strength on a tactical-node compound (COMMAND_POST / BARRACKS / ARMORY) drops below threshold. Rally hint = that compound. |
| `ObjectiveLostTrigger` | `OBJECTIVE_LOST` | Conquest zone flips to attacker. Defender posts; rally hint = adjacent contested zone. |
| `ScriptedTimerTrigger` | `SCRIPTED_TIMER` | Mission briefing config: `{ atSimSeconds, strength, side }`. Lets briefings flag "expect reinforcements at minute 4." |

### Means (v1 set)

Priority order: convoy > shuttle > walk-in. Cheaper / more readable
means run first; walk-in is the always-feasible floor.

| Means | Feasibility | Fulfillment |
|---|---|---|
| `ConvoyMeans` | Map has road graph + reachable rally cell | Spawn N trucks (see strength) at a perimeter trunk exit; uses the V1+polish path (bicycle + pure pursuit + Reeds-Shepp dock). |
| `ShuttleMeans` | Map has a clear LZ near rally | Spawn shuttle(s); existing `ShuttleAssignment` infra. |
| `WalkInMeans` | Always (perimeter has at least one walkable cell on the requesting side) | Squad spawns on a side-appropriate perimeter cell, paths to rally. |

### Rally point selection

When the request carries a rally hint, use it. Otherwise the
dispatcher picks: nearest most-depleted tactical-node compound
(COMMAND_POST / BARRACKS / ARMORY) for that side. This reuses the
graph already wired by [[tactical_linker_compound_fallback]] for
retreat — same nodes serve as reinforcement targets. Symmetry that
reads in-fiction: defenders pull back to the strongest garrison they
have, and reinforcement pushes toward the most-depleted one.

### Strength scaling

| Strength | Walk-In | Convoy | Shuttle |
|---|---|---|---|
| SMALL  | 1 squad  | 1 truck            | 1 shuttle |
| MEDIUM | 2 squads | 2 trucks (stagger) | 1 shuttle (full) |
| LARGE  | 3 squads | 3 trucks (stagger) | 2 shuttles |

Convoy stagger is the same axis as `stage2.md` item 2 (multi-truck
spacing); the reinforcement layer asks for "MEDIUM" and the convoy
provider produces a 2-truck staggered convoy.

## v1 cut

**Status: landed.** Package
`com.dillon.starsectormarines.battle.reinforcement` holds the
service + interfaces + the first trigger and means. `BattleSimulation`
owns a `ReinforcementService` field and ticks it before the air/ground
systems; `BattleSetup.installReinforcementLayer` registers the v1
pair on every mission. The legacy `DEBUG_SPAWN_TEST_CONVOY` method
is no longer called (kept in place as an emergency rollback knob
until playtest confirms the service path).

Smallest end-to-end slice that proves the abstraction:

- One trigger: `GarrisonDepletedTrigger` — fires once per defender
  COMMAND_POST / BARRACKS / ARMORY compound when aggregated squad
  strength drops below 0.5; rally = compound center.
- One means: `ConvoyMeans` (the V1+polish path, gated on road graph)
- Strength: read but ignored — convoy always spawns 1 truck regardless
- Rally: trigger always supplies it; means honors it
- Tickets: not modeled — every request gets dispatch attempts. Budget
  plumbing comes in the second slice once existing shuttle-drop
  accounting is folded in (see Decisions §1)

## v2 cut

**Status: landed.** Second slice — expands both axes by one. Triggers
go from one to two; means go from one to two with walk-in as the
always-feasible floor under convoy.

- New trigger: `ObjectiveLostTrigger` — defender-side zone-flip
  detector. Maintains a `wasDefenderHeld` set built incrementally as
  defenders are observed; fires once per zone that flips to
  marines-present + zero-defenders. Rally = lost zone's centroid
  (retake attempt). One-shot per zone, no recovery story yet.
- New means: `WalkInMeans` — spawns 3 MILITIA defenders on the
  side-appropriate perimeter edge (axis-aware: NORTH for SOUTH_TO_NORTH
  defender, EAST for WEST_TO_EAST, stable default for null-axis).
  Anchors `Squad.assignedNode` to the nearest COMMAND_POST / BARRACKS
  / ARMORY within 6 cells of rally so `PatrolRoute` pulls them off the
  perimeter; falls through to free-agent ambient engagement if no
  compound is close.
- `installReinforcementLayer` now takes a nullable `TraversalAxis`
  threaded through from `createConquest`; Sabotage/Assault pass null.
- Still unmodeled: ticket budget, strength scaling (walk-in always
  spawns SQUAD_SIZE=3 regardless), attacker-side triggers/means.

## v3 cut

**Status: landed.** Third slice — `ShuttleMeans` ports the existing
shuttle-drop infrastructure into the reinforcement layer, slotting
between convoy (most readable) and walk-in (floor).

- New means: `ShuttleMeans` — mints a single-cycle `Shuttle` flying in
  from the side-appropriate off-map edge (defender = end side of the
  axis, marine = start side; null-axis defaults to north for stable
  fallback). Picks the LZ via BFS for the first walkable cell within 8
  cells of rally. Reuses the existing `AirSystem` state machine end to
  end — `Shuttle.totalCycles = 1`, no marine loadout (deboards plain
  `COMBATANT` `UnitType.MARINE` units, inheriting their default stats),
  no turret kit (skips HOVER_STATION and departs immediately after the
  capacity drops).
- Priority order is now: convoy → shuttle → walk-in. A road-less map
  with a walkable rally interior gets a shuttle drop. A clogged interior
  with no LZ within range yields to walk-in.
- Narrative: shuttle reinforcement reads as elite quick-response strike
  team. Convoy = ground militia trucked in (cheapest readable). Walk-in
  = patrols rerouting from the rear (the floor).
- Side neutrality remains untested — `canFulfill` still hard-rejects
  non-defender requests since no attacker-side trigger exists yet.
  Attacker-side gating moves to a follow-up alongside the first
  attacker trigger.

## Decisions

### 1. Side neutrality + shuttle-drop refactor

Both attackers and defenders post requests. The `side` field on
`ReinforcementRequest` is data, not type — so the same service handles
defender garrison-depleted convoys and attacker shuttle drops with no
code branch. The existing attacker shuttle-drop infrastructure (with
its informal ticket modeling) is a natural refactor candidate: port it
to `ShuttleMeans` + the `ReinforcementService` ticket budget. Not v1
scope — but the v1 design already accommodates it.

### 2. Ticket budget lives on the service; refill rules on the mission

Per-side ticket count is service state; the refill cadence /
starting amount / max comes from `ReinforcementBudget` on the
mission config (see "Ticket budget" above). Conquest can configure
large mid-/late-battle waves; raids configure defender-only
one-shot; the service code stays uniform.

### 3. Failure = bugged-map diagnostic

If no means returns `canFulfill = true`, the request is dropped and
logged as a map-gen issue. Convoy needing a road graph or shuttle
needing an LZ shouldn't fail — walk-in is the always-feasible floor,
so an unfulfillable request means walk-in's perimeter check itself
failed, which is a map problem worth surfacing.

### 4. Diegetic surfacing — player-side status UI; enemy reads naturally

Pre-fog-of-war the player sees enemy reinforcements arrive directly —
the trucks / shuttles / walk-ins are visible on the map. For the
player's own side, ship a small status UI showing **remaining tickets
+ inbound** (whatever's been dispatched and is still in transit). This
turns the budget into a visible resource the player can plan around,
not a hidden timer. Fog of war later changes the enemy-side read but
not the player-side UI.

### 5. Commander-tier integration deferred

Once the squad-of-squads commander ([[mission_type_flavors]]) lands,
deboarded reinforcement squads need to register with it so they get
objective assignment. For v1 the spawned squad enters the same
"free agent" pool the commander will pick up; no special wiring
needed in the reinforcement service itself.

## Cross-refs

- [`../convoy/stage2.md`](../convoy/stage2.md) item 1 — the convoy
  provider implementation lives there; this doc owns the orchestration
  layer above it.
- [`../convoy/v1-polish.md`](../convoy/v1-polish.md) — the V1 truck
  stack the convoy means rides on (bicycle + pure pursuit + Reeds-
  Shepp dock + road reservation).
- [[mission_type_flavors]] — Conquest is the first mission surface;
  Assault and others will follow with their own trigger flavors.
- [[tactical_linker_compound_fallback]] — rally points reuse the
  COMMAND_POST / BARRACKS / ARMORY graph the retreat fallback already
  uses.
- [[feedback_world_reactive_over_expressive]] — the design philosophy
  motivating the multi-trigger / multi-means split.
- [[battle_services_systems]] — the *Service (stateful, constructor-
  injected) vs *System (stateless tick consumer) convention this
  service follows.
