# 27 — Read-only commander influence substrate

**Shipped 2026-08-19 in `4e7089d0`.**

## Player-visible contract

This slice does not change squad orders. It gives each side an honest,
battle-transient tactical picture derived from what that side owns and what
its squads believe. Debug heatmaps make the picture inspectable before any
commander is allowed to act on it.

Marine and defender fields are independent. A hidden unit contributes to its
own side's friendly channel, but cannot appear in the opposing hostile channel
until an opposing squad has a source-linked belief for it. Anonymous explosion
bearings stay squad-local in this slice because they do not carry an event id
that the commander can safely deduplicate across reports.

## Ownership and cadence

A battle-scoped `CommanderInfluenceService` owns one immutable snapshot for
`MARINE` and one for `DEFENDER`. It refreshes immediately on the first sim tick
and every 15 ticks thereafter (2 Hz at the 30 Hz simulation rate), after squad
awareness publishes belief and before the existing commander phase.

Mission commanders do not own or mutate the field. `BattleView` exposes the
read-only snapshot so later commander slices can consume it without learning
about the service implementation.

## Threat aggregation

For one faction:

- `friendly` seeds include every living combatant of that faction, including
  squadded infantry, mechs, turrets, hubs, and drones;
- each friendly combatant emits `1.0` presence in this first structural slice;
- `hostile` seeds are the union of every living squad's identified
  `BelievedContact` reports for that faction;
- reports deduplicate by hostile unit id, choosing highest confidence, then
  newest observation tick, then lowest reporter squad id;
- the winning contact emits its confidence at its believed cell;
- no live hostile registry scan is permitted while building the hostile set.

Unit-strength weighting, commander-authored contacts, and anonymous-signal
fusion remain later tuning/contracts.

## Tactical grid and propagation

The published field uses 8×8-world-cell tactical blocks. Each channel is the
sum of its propagated seeds. A transition to an adjacent tactical block
multiplies influence by `0.85`; propagation stops below `0.05`.

Topology is derived from fine navigation connectivity, including reciprocal
edge passability. Walkable components inside the same 8×8 block remain
distinct during propagation, so a wall slicing a coarse block cannot leak
influence from one side to the other. Components connect only through real
walkable boundary edges. Values are aggregated to one scalar per tactical
block only after propagation.

A seed on a non-walkable source cell projects to every equally-near walkable
component and divides its magnitude between them. This covers wall-mounted
turrets and uncertain remembered cells without multiplying their strength.

## Diagnostics

The ordinary debug menu exposes four independent overlays:

- Marine friendly influence;
- Marine hostile belief;
- Defender friendly influence;
- Defender hostile belief.

Each renders one rectangle per non-zero tactical block, normalized only for
display. Internal values remain unnormalized and queryable. Debug rectangles
use the existing highlight draw-list pipeline; `CellHighlight` gains a
backward-compatible width/height footprint, and source alpha now actually
scales fill/outline opacity.

## Acceptance coverage

- Marine and defender snapshots refresh at the fixed cadence and remain
  independent.
- A faction's friendly channel includes all and only its combatants.
- Hostile influence contains a believed hidden enemy but not an unobserved
  live enemy.
- Duplicate squad reports merge deterministically without double emission.
- A solid wall prevents propagation; a real opening permits it.
- Direct versus audio confidence produces correspondingly different hostile
  seed strength.
- Coarse debug rectangles cover their full tactical block without changing
  existing one-cell highlight callers.

## Out of scope

- Frontline contours, bulge/breakthrough analysis, or assignment changes.
- Commander briefings pushed back into squad belief.
- Training/difficulty modifiers or mech/weapon combat-power weights.
- Anonymous bearing fusion, objective pull, casualty, supply, and cover
  channels.
- Persistence or a versioned debug schema.

## Verification

- `CommanderInfluenceServiceTest` covers independent faction pictures,
  belief-only hostile sources, combatant-only friendly sources, deterministic
  report merging, fixed cadence, snapshot immutability, and safe world bounds.
- `InfluenceFieldBuilderTest` proves that a fine-grid wall inside one tactical
  block cannot leak into the next block, while a real opening propagates with
  the contracted attenuation.
- `CellHighlightTest` protects existing one-cell callers and the new coarse
  rectangle footprint.
- Focused influence/highlight tests and the full `gradlew.bat test` suite
  passed before documentation closeout.
