# 17 — Garrison Zone-Clear Scoping (AABB-gated SecureCompound plans)

**Shipped** — the scoping fix, both command-side mirror bugs (0a/0b), and the
richer multi-building garrison behavior all landed. Originally a
`SecureCompoundGoal` plan-synthesis bug where a squad tasked to secure/hold a
compound charges the whole map; grew into the garrison-area primitive and the
`GarrisonCompound` patrol behavior it unlocked.

## What shipped

**Scoping fix** (`2b31af4`) — the AABB size+containment gate as a filter on the
BFS-path plan: `synthesizeSecurePlan` emits a transit `EnterZone` for every
route zone but only a `ClearZone` for zones passing `isGarrisonZone` (size:
`cellCount ≤ 1.25 × bbox`; containment: `≥50%` inside). The outdoor flood fails
the size gate (O(1) field read), so `ClearZone[outdoor]` is gone and `HoldZone`
is reachable. (`SecureCompoundGoalTest`.)

**Garrison-area primitive** (`87cf47c`, `94e3060`) — the gate lifted into a
stateless `GarrisonArea` (`isGarrisonZone` + `garrisonZones`, 1-cell doorway
micro-zones filtered). `TacticalNode` gained a persisted `compoundBounds` (the
gen-time compound union bbox, stamped by `MilitaryBaseFiller`; defaults to the
node's own bbox), so a garrison knows the whole base, not one building.

**Bug 0a** (`4cebcb8`) — `ConquestCommand`/`AssaultCommand` cache the exterior
zone (largest by cells, only when it dominates ≥2× the next) and never hand it
out as a `CLEAR_ZONE` target.

**Bug 0b + richer garrison** (`8e73d0d`, `7fc2415`) — `HOLD_NODE` had no
consumer goal, so captured compounds were ungarrisoned. New `GarrisonCompound`
goal + `GarrisonPatrol` action: re-clear any contested room (delegating to
`ClearZone` for the no-chase-across-boundary property), else round-robin patrol
the room interiors (leader-gated dwell) with opportunistic fire. Serves both
the marine `HOLD_NODE` holder and the defender base garrison; for a
multi-building defender compound only the **primary** node patrols (others hold
their building via `GuardPost`, which yields only for the primary).
(`GarrisonCompoundTest`, `GarrisonAreaTest`.)

**Who holds — born-holding garrison** (`08ef31e`) — the holder is the dedicated
squad shipped in by `CompoundGarrisonSystem`, not whoever captured the place.
The garrison shuttle carries its compound node; `AirSystem` stamps the deboarded
squad with `HOLD_NODE` at mint (`Squad.assignHoldNode`), so it's born into
`GarrisonCompound`. `ConquestCommand` no longer assigns `HOLD_NODE` at all
(Pass 0 removed) — it only *respects* an existing one (Pass 1/2 skip), so the
capturing assault squad is re-tasked to keep advancing instead of being pinned.
The garrison stays on its compound for the battle (re-clears on counter-attack);
release-when-quiet is unimplemented. (`CompoundGarrisonSystemTest`,
`ConquestCommandTest`.)

## Remaining follow-ups

- **Courtyard engagement.** `GarrisonPatrol` re-clears garrison *rooms* and
  fires opportunistically, but an enemy standing on the courtyard/parade ground
  (inside the footprint but in a too-large, gate-excluded zone) isn't actively
  moved-to-engage — only shot at if already in LoS. A "defend the footprint AABB"
  move-to-engage step would close this.
- **SecureCompound off-path rooms.** The capture-time `synthesizeSecurePlan`
  still clears only rooms on the BFS route to the anchor. Now lower priority:
  `GarrisonCompound` re-clears every room once the compound flips `MARINE_HELD`.
- **Release-when-quiet.** A born-holding garrison stays on its compound for the
  rest of the battle. If garrisons should rejoin the advance after the area's
  been quiet for a while, that's an added rule (clear `HOLD_NODE` on a quiet
  timer so the commander re-tasks the squad).

## The bug (observed)

A CONQUEST reinforcement squad (SQ-87 in the captured dump) was assigned
`SECURE_COMPOUND → ARMORY → targetZone 36`, goal `SecureCompound`, but its
plan was:

```
0  EnterZone[0]
1  ClearZone[0]   ← stuck here forever
2  EnterZone[29]
3  ClearZone[29]
4  EnterZone[36]
5  ClearZone[36]
6  HoldZone[36]    ← the "defend the compound" step it never reaches
```

`SecureCompoundGoal.synthesizeSecurePlan()` runs a zone-graph BFS from the
squad's zone to the compound zone and emits an `EnterZone` + `ClearZone`
pair for **every zone on the path** (`SecureCompoundGoal.java:106-127`).
The path ran through **zone 0 — the entire outdoor flood-fill** (one zone
covers the whole exterior; see `memory/flat_edges_between_kinds.md`).

`ClearZone[0]` only reports SUCCESS when `ZoneQueries.zoneClear(0, DEFENDER)`
is true — i.e. *no living defender stands anywhere outdoors*
(`ZoneQueries.java:106`). The dump's `clearZoneReachability` listed ~150
defenders scattered map-wide, all in zone 0, all reachable. So the squad
parks on step 1 and chases outdoor defenders across the whole map, never
advancing to `HoldZone[36]`. The Story-K "don't chase across portals" guard
(`ClearZone.java:108`) is inert here — there are no portals to cross *within*
the outdoors, so every outdoor enemy is a legal in-zone pursuit target.

## Root cause

The plan treats every zone on the route as a clearable room. That's correct
for a tight indoor sweep but catastrophic for the open exterior zone, which
is unbounded. There is no notion of "is this zone part of the compound I'm
supposed to secure, or just open ground I'm crossing?"

## Fix: AABB-gated clear scoping

`TacticalNode` already carries the compound's box (`left, top, right, bottom`,
`anchorX/Y`, `centerX/Y()` — `TacticalNode.java:116-163`). `NavigationZone`
gives `getCellCount()` (O(1)) and `getCellIndices()` (cell = `y*width + x` —
`NavigationZone.java:31-32`). That's enough to decide, per zone, whether a
`ClearZone` belongs in the plan. Two gates, cheap one first:

**1. Size gate (O(1)) — "is this zone meaningfully larger than the garrison area?"**
```
compoundArea = (right-left+1) * (bottom-top+1)
if zone.getCellCount() > K * compoundArea  →  transit only, no ClearZone
```
`getCellCount()` is a field read, so this rejects zone 0 instantly without
ever iterating its thousands of cells. K ≈ 1.25 absorbs door/edge cells that
spill just outside the box.

**2. AABB containment (O(cells), only on zones that survive gate 1).**
```
inside = count of zone cells where left ≤ x ≤ right && top ≤ y ≤ bottom
if inside / cellCount ≥ T  →  ClearZone is legitimate   (T ≈ 0.5)
else                       →  transit only
```
The size gate guarantees the per-cell loop only ever runs on small zones,
never the outdoor flood. Point-in-rect per cell is the AABB test.

This composes the two competing requirements:

- **Compound made of multiple zones** (interior rooms): each room sits inside
  the building box and is small → all clear. This is the *desired* behavior —
  a multi-room compound should clear each room.
- **Outdoor flood**: fails the size gate → transit-only `EnterZone`.

### Plan reshape (not just a filter)

Today's plan clears only zones that happen to lie on the BFS path to the
anchor — a multi-room compound never gets its off-path rooms cleared. The
AABB gate lets us enumerate the compound's *own* zones directly instead:

```
garrisonZones = zones where cellCount ≤ K*area AND insideFraction ≥ T
plan = EnterZone(toward compound)
       for each garrisonZone: ClearZone
       HoldZone(anchor)
```

Transit zones (including the outdoor crossing) get a bare `EnterZone` to move
through; only the compound's constituent rooms get `ClearZone`.

### Tuning knob: garrison area vs building box

`TacticalNode` stores only the *building* bbox. The perimeter wall / parade
ground — where `CompoundGarrisonSystem` actually drops the troops
(`CompoundGarrisonSystem.java:107-126`) — sits outside it. If the courtyard
should be cleared/held too, expand the AABB by a margin; if only the interior
matters, the raw box is fine. The margin is the main knob.

## Follow-ups surfaced by this work

- **Assault-side mirror of the same bug.** `ConquestCommand`'s Pass 2
  `nearestDefenderZoneInStrip` can hand a squad `CLEAR_ZONE[0]` whenever the
  outdoor zone has a defender in its strip (`ConquestCommand.java:363-389`)
  → `ClearAssignedZoneGoal` charges the whole map the same way. The AABB gate
  doesn't apply (no compound box); this path wants a separate "never enqueue a
  full clear against the open exterior zone" guard. Same hazard sits in
  `AssaultCommand`'s sector sweep.
- **Garrison never actually holds (HOLD_NODE gate too strict).**
  `ConquestCommand` Pass 0 assigns `HOLD_NODE` only when the squad centroid is
  *inside the compound's zone* (`ConquestCommand.java:142-157`), but garrison
  troops land on the outdoor parade ground (zone 0 ≠ building zone). So a fresh
  garrison fails the gate, falls through to Pass 1, and picks up
  `SECURE_COMPOUND` instead. Relax the gate (squad in the compound zone *or*
  within N cells of the anchor / inside the perimeter AABB), or have
  `CompoundGarrisonSystem` stamp `HOLD_NODE` at spawn rather than waiting for
  the commander to detect it by zone. This is *why* a garrison squad was
  running a charge objective at all.

## Richer follow-on: GarrisonCompound goal

The AABB gate gives a clean `garrisonZones` set. A dedicated garrison behavior
can cycle that set instead of holding one anchor: patrol the compound's
sub-zones, re-`ClearZone` on counter-attack, fall back to `HoldZone` when
clear. This is the "intelligent garrison that patrols a segmented area and
clears the encapsulated rooms" shape — sits on top of this story's scoping
primitive. Reuses the existing garrison vocabulary (`GuardPost` / `HoldPost`,
`GarrisonCordon`, `RoutinePatrol`).

## Observable behavior (after fix)

- A squad assigned `SECURE_COMPOUND` walks across the exterior to the
  compound (transit `EnterZone`, no map-wide clear), clears the building's
  rooms, then holds.
- Debug dump: plan has no `ClearZone[<outdoor zone>]` step; `HoldZone` is
  reachable.

## Cross-references

- [Squad-of-squads design](12-squad-of-squads.md) — commander tier that writes
  the assignments
- [Assault commander](16-assault-command.md) — shares the `CLEAR_ZONE[0]`
  hazard
- [Story bank](10-tactical-stories.md) — Story K (room-clear sweep) is the
  tactical layer being scoped here
- `memory/flat_edges_between_kinds.md`, `memory/zone_graph_ignores_edges.md` —
  why the exterior is one giant zone
