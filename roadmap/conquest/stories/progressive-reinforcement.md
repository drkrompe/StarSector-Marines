# Progressive reinforcement — biome-slice round-robin

> Defender reinforcements should contest the front line, not just
> react to compound health. When a garrison is wiped at any tactical
> node (turrets, guardposts, towers, gates — not just compounds),
> that node becomes a recapture target. Dispatch cycles round-robin
> through the nearest-to-defender biome slice with lost positions,
> spreading squads across the front rather than dogpiling one node.
> Overflow squads patrol the slice. Delivery and objective are
> separate coordinates: means land troops in safe defender territory,
> then squads advance to the contested position on foot.

## Why

The current reinforcement model is compound-centric:
`GarrisonDepletedTrigger` fires when a compound's garrison drops
below 50%, `ObjectiveLostTrigger` fires when a zone flips. Both
rally reinforcements to compound centers or zone centroids. This
means:

- Turrets, guardposts, and towers that get overrun are never
  re-garrisoned. Once the original squad is wiped, those positions
  stay empty permanently.
- Reinforcement waves stack at the same compound rally, creating
  a dogpile at one position while the rest of the front is
  uncontested.
- No sense of a defensive "line" that the defender tries to hold —
  just point-defense at supply structures.

With compounds now spread across PORT → CITY → FORTRESS, the map
has real depth. The reinforcement model should match: defenders
contest territory in bands, not individual buildings.

## Design

### Hidden recapture targets

Every defender-faction tactical node (GUARDPOST, HEAVY_TOWER,
MG_NEST, GATE, FORWARD_BUNKER, plus compound nodes) starts with a
garrison assigned by `BattleSetup`. When the node's original garrison
squad is wiped (zero alive members), the node becomes a **recapture
target** — eligible for reinforcement dispatch.

Recapture targets are not visible to the player as capture points
(no marker, no HUD strip). They're internal state in the
reinforcement layer — the defender "remembers" where it had
positions and tries to re-man them.

A recapture target is **fulfilled** when a reinforcement squad is
dispatched to it. It stays fulfilled until that squad is also wiped,
at which point it becomes eligible again. This prevents duplicate
dispatches to the same position.

### Biome-slice round-robin dispatch

When the reinforcement service needs a rally point for a new
dispatch:

1. Group all unfulfilled recapture targets by biome slice
   (PORT, CITY, FORTRESS — read from BiomeMap at the node's anchor).
2. Select the **nearest-to-defender** slice that has unfulfilled
   targets. "Nearest-to-defender" = closest to the defender rear on
   the traversal axis (FORTRESS before CITY before PORT). The
   defender reinforces their own front line — the forward edge of
   territory they still hold — not positions deep behind the marine
   advance. Dropping troops behind the player's lines is
   tactically incoherent and frustrating.
3. Within that slice, **round-robin** across unfulfilled targets.
   Each dispatch picks the next target in rotation, spreading squads
   across the slice's positions instead of stacking on one.
4. If the selected slice has no unfulfilled targets (all positions
   either still garrisoned or already have a reinforcement squad
   dispatched), fall through to the next slice toward the marine
   side. This means the defender only reinforces deep positions
   once their rear is fully covered — a natural triage.

### Overflow → patrol

When a reinforcement request fires but every recapture target in
every slice is already fulfilled (rare — means every lost position
already has a replacement squad en route or holding):

- The squad spawns with **patrol** behavior in the nearest-to-defender
  biome slice. No specific node anchor — they patrol the band,
  engaging targets of opportunity.
- This is the existing WalkInMeans free-agent fallback, but with
  a biome-constrained patrol zone instead of ambient wander.

### Delivery vs. objective — the two-coordinate split

Today the rally point serves double duty: it's where troops land AND
what they defend. With progressive reinforcement those diverge:

- **Objective** (owned by the trigger): the recapture target node
  the squad should retake/garrison after arrival. Selected by the
  biome-slice round-robin picker.
- **Delivery zone** (owned by the means): a safe LZ/entry in
  defender-held territory near the front. The means picks this
  independently from the objective — shuttle finds an LZ in the
  defender's nearest biome slice, convoy picks a road entry on the
  defender side, walk-in spawns on the defender edge as today.

After deboarding, the squad receives the recapture target as its
assignment (HOLD_NODE or a new RETAKE_NODE kind) and advances on
foot from the delivery zone to the contested position. This is the
natural behavior: reinforcements stage behind friendly lines, then
push forward to contest.

`ReinforcementRequest` gains a second coordinate pair:
`objectiveX/Y` alongside the existing `rallyX/Y`. The rally becomes
the **delivery hint** (where the means should try to land); the
objective becomes the **squad assignment** (where the deboarded squad
should go). Means use rally for LZ/entry selection. The dispatch
layer sets the squad's assigned node from the objective after
deboard.

For convoy and shuttle this is the key fix: the truck drives to a
safe interior junction (rally = defender-side road node), deboards,
and the squad walks to the contested turret (objective = recapture
target anchor). No more dropping troops directly into a firefight
or behind enemy lines.

### Expanding existing triggers

This isn't a new trigger — it's expanding `GarrisonDepletedTrigger`
to detect a broader set of tactical nodes. Today it only watches
compound-kind nodes (COMMAND_POST, BARRACKS, ARMORY). The expansion:

- Watch ALL defender-faction tactical nodes with `garrisonSize > 0`
- Fire when the node's assigned squad drops to zero alive (not 50%
  threshold — fully wiped, since partial losses at a turret aren't
  worth a reinforcement wave)
- Request carries both rally (delivery hint — safe zone in the
  defender's nearest slice) and objective (the recapture target
  anchor)
- The existing means priority chain (Convoy → Shuttle → WalkIn)
  uses the rally for delivery; the dispatch layer assigns the
  objective to the deboarded squad

`ObjectiveLostTrigger` (zone-level) stays as-is for compound-zone
detection. The node-level expansion on GarrisonDepletedTrigger covers
individual positions within zones.

### Interaction with compound-as-supply gates

The means supply gates are unchanged:
- ConvoyMeans requires alive ARMORY
- ShuttleMeans requires alive COMMAND_POST
- WalkInMeans requires alive BARRACKS

As compounds are captured, the delivery means naturally degrade. A
recapture target in the PORT biome might only be reachable via
walk-in if the PORT ARMORY has been captured (convoy is gated out).
This is the intended supply-chain pressure.

## Implementation slices

### Slice 1: ReinforcementRequest two-coordinate split

Add `objectiveX/Y` fields to `ReinforcementRequest` alongside the
existing `rallyX/Y`. Rally becomes the delivery hint (means use it
for LZ/entry), objective becomes the squad assignment target.
Existing triggers set objective = rally (backwards compatible).
Means continue to use rally for delivery; dispatch layer reads
objective to assign the deboarded squad. No behavior change yet —
just the data plumbing.

### Slice 2: Recapture target tracking

New `RecaptureTargetRegistry` that tracks all defender tactical
nodes and their garrison state:
- Populated at sim init from TacticalMap + BiomeMap
- Groups nodes by biome slice at init time
- Updated when squads are wiped (squad alive-members reaching zero)
- Exposes: unfulfilled targets by biome slice, mark-fulfilled on
  dispatch, re-open on replacement squad wipe

### Slice 3: FrontLineReinforcementTrigger

New trigger (replaces or wraps `GarrisonDepletedTrigger`) that:
- Reads `RecaptureTargetRegistry` for unfulfilled targets
- Picks nearest-to-defender slice with open targets
- Round-robins within the slice for the objective
- Picks a safe rally in the same slice (walkable cell near the
  defender rear of the slice) for delivery
- Falls back to patrol on overflow (objective = null, rally = slice
  centroid)

### Slice 4: Squad post-deboard assignment

After means dispatches and troops deboard, the dispatch layer
reads the request's objective coordinates and assigns the squad:
- If objective is set: HOLD_NODE at the recapture target's
  tactical node. Squad advances from delivery LZ to the position.
- If objective is null (overflow): patrol behavior in the slice.

### Slice 5: Wire + tune

- Register `FrontLineReinforcementTrigger` + `RecaptureTargetRegistry`
  in `installReinforcementLayer`
- Retire or gate old `GarrisonDepletedTrigger` (compound-only
  variant) so requests don't duplicate
- Tune fire cadence and delivery-zone selection
- Playtest reinforcement spread across biome bands

## Open questions

- **Should the round-robin be weighted by node priority?** A
  HEAVY_TOWER (high priority) might deserve reinforcement before a
  MG_NEST (low priority) in the same slice. Counter: round-robin
  avoids priority starvation where low-priority nodes never get help.
- **Should recapture targets expire?** If a position has been lost
  for 60+ seconds, maybe the defender gives up on it and focuses
  forward positions. Prevents reinforcement waste on positions deep
  behind the marine front line.

## Cross-refs

- [`compound-spread.md`](compound-spread.md) — biome-spread compounds
  create the tiered map depth this system contests.
- [`../central-keep.md`](../central-keep.md) — compound-as-supply
  gates remain the delivery constraint.
- `GarrisonDepletedTrigger` — the trigger this story expands.
- `ObjectiveLostTrigger` — zone-level detection, stays separate.
- `BiomeMap` — biome classification for slice grouping.
