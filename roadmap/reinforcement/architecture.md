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

### System

`ReinforcementSystem` owns:
- Pluggable list of `Trigger` (each: `boolean check(sim, side)` +
  request builder).
- Ordered list of `ReinforcementMeans` — priority for fallback.
- Per-side request queue.

Each tick:
1. For each trigger, if `check` fires, build a request and enqueue.
2. Drain the queue. For each request, iterate the means list in
   priority order; first provider that returns `canFulfill = true`
   wins and `dispatch`es. If none, drop and log (see open questions).

The system is stateless across requests — once dispatched, the means
provider owns the in-flight spawn. The system doesn't track "this
convoy is in transit"; that lives on the spawned `Vehicle` /
`Shuttle` / squad like any other unit.

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

Smallest end-to-end slice that proves the abstraction:

- One trigger: `GarrisonDepletedTrigger`
- One means: `ConvoyMeans` (the V1+polish path, gated on road graph)
- Strength: ignored at first — convoy always spawns 1 truck
- Rally: nearest tactical node to the depleted compound

Replaces `DEBUG_SPAWN_TEST_CONVOY` entirely. The interfaces (request,
trigger, means) all exist with only one implementation each — adding
the next trigger or means lands as a follow-up that doesn't touch the
abstraction.

## Open questions

- **Side neutrality.** Both attackers and defenders post requests, or
  defender-only at first? Conquest is a defender-reinforcement story;
  attacker reinforcement waits for a mission type that justifies it
  (Assault?). Keep the side field on `ReinforcementRequest` from day
  one so the constraint is data, not type.
- **Cooldown / budget per side.** The defender garrison-depleted
  trigger could fire repeatedly. Per-side cooldown (one request every
  N sim-seconds) or a global "reinforcement budget" set at mission
  start (defender has 3 reinforcement events, then nothing)?
- **Failure handling.** If no means returns `canFulfill = true`, what
  happens? Silent drop, log, or queued for retry next tick when
  conditions change? Probably log and drop — re-firing the trigger is
  the trigger's responsibility.
- **Diegetic surfacing.** The player should know reinforcements
  arrived (or are about to). Briefing tags? In-battle commander
  chatter? Defer until the message bus / commander UI lands.
- **Commander-tier integration.** Once the squad-of-squads commander
  ([[mission_type_flavors]]) exists, deboarded reinforcement squads
  should hook into objective assignment. Wiring TBD; the spawned
  squad is just a normal squad, so the commander needs to be told
  "new squad available."

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
