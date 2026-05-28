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
   once their rear is fully covered — a natural triage. The frontline
   filter below caps this fall-through at the contested edge: it never
   reaches a slice the marines have fully conceded.

### Frontline eligibility — contested slices only (no expiry)

Recapture targets do **not** expire on a timer. Instead, a target is
only eligible while its biome slice is part of the **contested
front** — a slice where the defender still has a presence. As the
marines advance and fully overrun a slice (zero alive defenders left
in the band), that slice is **conceded**: its lost nodes drop out of
the eligible set entirely. The defender doesn't keep throwing squads
at positions deep behind the marine line — the front simply moves
forward and the conceded slices fall off the back.

Concretely, each dispatch cycle bins alive defender units into biome
slices. A recapture target is eligible only if its slice still has
≥1 alive defender. This is what bounds the round-robin's marine-side
fall-through (step 4): the fall-through traverses still-contested
slices but never reaches a conceded one, because conceded slices hold
no eligible targets. It also matches the two-coordinate delivery model
— a slice with no defenders has no safe LZ to stage from, so
reinforcing into it was never coherent.

The result is an emergent frontline: the band of slices between the
defender's rear and the marine spearhead, where both sides overlap.
Lost nodes on that band get re-manned; lost nodes behind it are
written off.

The offensive inverse of this filter — a staged counterattack that
deliberately dispatches *into* a conceded slice to push the frontline
back — is the follow-on
[`biome-counterattack.md`](biome-counterattack.md).

### Overflow → patrol (deferred)

> **Deferred** past the initial implementation. The front-line trigger
> simply posts nothing when no eligible targets exist; ambient patrol
> is already covered by the existing WalkInMeans free-agent fallback.
> The biome-constrained overflow patrol below is a later enhancement.

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

### Landing/dropoff scoring — a reusable tool

The rally is only a **hint** (a region seed, e.g. the defender-rear of
the chosen slice); it is *not* the literal landing cell. A naive fixed
offset off the objective would routinely seed a building interior or
unwalkable terrain. The means must never deboard troops inside a
building or on impassable ground — so final delivery-cell selection
goes through a shared **landing/dropoff scorer** rather than each
means re-rolling its own ad-hoc BFS.

A cell is a **viable dropoff** when it is:
- walkable (`NavigationGrid.isWalkable`),
- not inside a building footprint (`CellTopology.getBuildingId` unset),
- not water / impassable ground (`CellTopology` ground kind), and
- on the requesting side's territory (biome-slice / axis side).

The scorer ranks viable cells by openness (prefer STREET/COURTYARD),
clearance (a few free neighbours — matters for air drops), and
proximity to the hint. Candidate *generation* stays per-means (convoy
walks road-graph junctions, walk-in scans the map edge, shuttle/front-
line delivery ring-search out from the hint); candidate *validation
and ranking* is the shared tool. This both fixes the "land in a
building" hazard and removes the duplicated selection logic across the
three means.

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

### Slice 1: ReinforcementRequest two-coordinate split — ✓ shipped `1e0d388`

Add `objectiveX/Y` fields to `ReinforcementRequest` alongside the
existing `rallyX/Y`. Rally becomes the delivery hint (means use it
for LZ/entry), objective becomes the squad assignment target.
Existing triggers set objective = rally (backwards compatible).
Means continue to use rally for delivery; dispatch layer reads
objective to assign the deboarded squad. No behavior change yet —
just the data plumbing.

### Slice 2: Recapture target tracking — ✓ shipped `e4a49b9` (hardened `ae5a1bd`, renamed `e4a2ed6`)

`RecaptureTargetService` (per-tick, owns state) that tracks all
defender tactical nodes and their garrison state:
- Populated at sim init from TacticalMap + BiomeMap
- Groups nodes by biome slice at init time
- Open-state derived each tick from squad→node assignment
  (zero alive assigned = open)
- Bins alive defender units into biome slices to compute the
  contested set, debounced over `PRESENCE_DEBOUNCE_TICKS` both ways,
  seeded once a defender is actually observed
- Exposes: eligible targets (`open && !dispatched && contested`),
  mark-dispatched (de-dup), re-open on replacement-squad wipe

### Slice 3a: Landing/dropoff scorer (prerequisite for 3 + 4)

New reusable tool that validates and ranks a candidate dropoff cell —
walkable, not inside a building (`CellTopology.getBuildingId`), not
water, defender-side — scored by openness + clearance + proximity to
the hint. Candidate generation stays per-means; this owns validation
and ranking. Standalone unit-testable.

### Slice 3: FrontLineReinforcementTrigger

New trigger (replaces or wraps `GarrisonDepletedTrigger`) that:
- Reads `RecaptureTargetService` for eligible targets (open,
  undispatched, in contested slices)
- Picks nearest-to-defender slice (biome ordinal FORTRESS > CITY >
  PORT > BEACH) with eligible targets
- Round-robins within the slice for the objective (flat, unweighted),
  one dispatch per tick; calls `markDispatched`
- Rally = a defender-rear **region hint** in that slice (a search
  seed); the means + scorer (3a) resolve the actual viable LZ
- Overflow → patrol is **deferred**: when no eligible targets exist
  the trigger posts nothing (no budget-draining patrol spam). The
  existing WalkInMeans free-agent fallback covers ambient patrol;
  biome-constrained overflow patrol is a later enhancement.

### Slice 4: Means delivery via scorer + squad post-deboard assignment

After means dispatches and troops deboard, the dispatch layer
reads the request's objective coordinates and assigns the squad:
- Means LZ/entry selection routes through the landing/dropoff scorer
  (3a) so troops never deboard in a building or on impassable ground;
  optionally retrofit Convoy/Shuttle/WalkIn candidate selection.
- If objective is set: HOLD_NODE at the recapture target's
  tactical node. Squad advances from delivery LZ to the position.
- If objective is null (overflow): patrol behavior in the slice.

**Contract — assign at deboard, not on arrival.** The squad's
`assignedNode` must be set to the recapture target the moment it
deboards at the LZ, *before* it walks to the position — it's assigned
to the node while advancing, not once it gets there. This is what lets
`RecaptureTargetService` re-open a target whose reinforcement is wiped
*en route*: the registry sees the squad's `assignedNode` count drop to
zero and clears the dispatch suppression. If `assignedNode` were only
set on physical arrival, a squad killed crossing the front would leave
the target `open && dispatched` forever — silently un-reinforced.

### Slice 5: Wire + tune

- Register `FrontLineReinforcementTrigger` + `RecaptureTargetService`
  in `installReinforcementLayer`
- Retire or gate old `GarrisonDepletedTrigger` (compound-only
  variant) so requests don't duplicate
- Tune fire cadence and delivery-zone selection
- Playtest reinforcement spread across biome bands

## Decisions (resolved open questions)

- **Round-robin is not weighted by node priority.** Flat rotation
  across unfulfilled targets in the selected slice — a HEAVY_TOWER and
  an MG_NEST in the same slice get equal reinforcement priority. This
  avoids starvation where low-priority nodes never get help. Priority
  weighting is held as a future tuning knob, not built now; revisit
  only if playtest shows key positions falling too easily.
- **Recapture targets do not expire on a timer.** Replaced by the
  frontline eligibility filter (see "Frontline eligibility — contested
  slices only"): a target stays open as long as its slice is still
  contested, and drops out the moment the slice is conceded (no alive
  defenders left in the band). This handles the "don't reinforce deep
  behind the marine line" concern without an arbitrary timeout.

## Cross-refs

- [`biome-counterattack.md`](biome-counterattack.md) — follow-on: the
  staged "bulge" that inverts this filter to retake conceded slices.
- [`compound-spread.md`](compound-spread.md) — biome-spread compounds
  create the tiered map depth this system contests.
- [`../central-keep.md`](../central-keep.md) — compound-as-supply
  gates remain the delivery constraint.
- `GarrisonDepletedTrigger` — the trigger this story expands.
- `ObjectiveLostTrigger` — zone-level detection, stays separate.
- `BiomeMap` — biome classification for slice grouping.
