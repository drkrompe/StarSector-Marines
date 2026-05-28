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

### Resource-gated dispatch (BattleResources)

**Status: landed.** Dispatch is gated by `BattleResources` — a
per-faction float-pool blackboard keyed by `(Faction, ResourceType)`.
Each alive compound produces tickets at a fixed rate; dispatch debits
a ticket before committing a spawn.

```
BattleResources:
  float[][] pools  — indexed by [Faction.ordinal()][ResourceType.ordinal()]
  produce(faction, type, amount)
  tryConsume(faction, type, cost) → boolean
  getBalance(faction, type) → float

ResourceType:
  REINFORCEMENT  — produced by alive ARMORYs, consumed by ReinforcementService
  AIRSTRIKE      — produced by alive COMMAND_POSTs, reserved for future
```

**Production is compound-driven, not mission-configured.** Each alive
ARMORY generates `REINFORCEMENT_PER_ARMORY_PER_SEC = 0.05` tickets/sec
(= 1 ticket per 20s per compound). As compounds fall, the production
rate degrades proportionally — a natural attrition curve that needs no
per-mission authoring. `supplyFaction()` maps compound ownership state
to the producing faction (DEFENDER_HELD/CONTESTED → DEFENDER,
MARINE_HELD → MARINE).

**Tick order:** compound capture → `BattleResources.tick` →
`ReinforcementService.tick`. Each layer sees consistent state within the
same sim tick.

`ReinforcementService.dispatch()` calls `tryConsume(side,
REINFORCEMENT, 1.0)` before iterating means providers. Insufficient
balance re-queues the request (deferred list added back to pending).
No means can fulfill: ticket refunded via `produce()`, request dropped
as before.

Future extension: `AIRSTRIKE` tickets (COMMAND_POST-produced) gate a
future air-strike dispatch layer. The `BattleResources` API is
type-generic — new resource categories are a one-enum-constant addition.

The earlier planned `ReinforcementBudget` (mission-config starting
tickets + per-sec refill + max) is superseded by compound-driven
production. Mission-level overrides could still layer on top as initial
pool seeding or rate multipliers, but the compound-driven base rate
covers the Conquest use case without per-mission authoring.

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
- One means: `ConvoyMeans` (the V1+polish path, gated on road graph).
  Now dispatches `HEAVY_APC` (MILITIA_TRUCK retired).
- Strength: read but ignored — convoy always spawns 1 vehicle regardless
- Rally: trigger always supplies it; means honors it
- Resource gating: **landed.** `BattleResources` compound-driven ticket
  production gates dispatch — each alive ARMORY produces 0.05
  REINFORCEMENT/sec; dispatch debits 1.0 per spawn. Insufficient
  balance re-queues the request.

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

### 2. Compound-driven production replaces mission-configured budget

The original plan (per-side `ReinforcementBudget` on the mission config)
was superseded by `BattleResources` — compound-driven ticket production
tied to alive ARMORYs / COMMAND_POSTs. This naturally degrades as
compounds fall and needs no per-mission authoring for the Conquest case.
Mission-level overrides (initial pool seeding, rate multipliers) can
layer on top if raid or scripted missions need different curves.

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

- [`../convoy/complete/reinforcement-integration.md`](../convoy/complete/reinforcement-integration.md)
  — the convoy provider (`ConvoyMeans`) implementation lives there; this
  doc owns the orchestration layer above it.
- [`../convoy/complete/v1-polish.md`](../convoy/complete/v1-polish.md) —
  the truck stack the convoy means rides on (bicycle + pure pursuit +
  Reeds-Shepp dock + road reservation + Hybrid A* pose playback).
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
