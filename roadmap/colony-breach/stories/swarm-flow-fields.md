# Swarm flow fields

Status: READY

Written: 2026-10-21

Model: `colony-breach-nouns.md` (Swarm goal).

## The question

The swarm today moves body by body (`SwarmPressureBehavior`, crowd handling in
`SwarmAvoidanceSystem`). Colony Breach wants a bigger swarm on a ~400x240 map,
and the owner wants to know whether Conquest scale (560x336) is affordable. A
shared flow field per swarm goal — one integration field the whole swarm
descends — should make strategic movement roughly O(cells) per goal refresh
instead of O(bodies x path).

## Scope

1. **Measure first.** Profile today's swarm strategic movement at 64, 150 and
   300 runners on 280x168, 400x240 and 560x336 maps (headless, serial
   scheduler, the house evidence rules: a control run with the lever off,
   medians, spread reported). Report where the tick goes.
2. **Flow field.** A deterministic per-goal flow field over the navigation
   grid (shelter, lift, Marine main body; goals are what the swarm may know per
   the noun doc), rebuilt incrementally or on a bounded cadence when topology
   changes (breaches, bores opening) or the goal moves. Owned by the navigation
   grid so concurrent simulations stay isolated (see the `LosCache` lesson in
   `CLAUDE.md`). Local target pursuit (a sensed Marine within a few cells) stays
   on the existing short-range logic.
3. **Switch.** `SwarmPressureBehavior`'s strategic move goes through the field
   behind `-Dbattle.swarm.flowField` (default on once evidence agrees), so the
   control run is one flag. Every swarm strain uses the same call.
4. **Evidence.** An opt-in task or a profile switch on an existing one
   (prefer extending an existing performance task's shape over a new top-level
   pattern) reporting tick cost by swarm size and map size, lever on and off,
   plus a determinism check (two replicas byte-identical, side by side).

## Out of scope

Strain behaviour, bores, director cadence.

## Acceptance

- Swarm reaches the same goals with the field on as off in a behaviour scene
  (no stuck bodies at chokepoints, no wall-hugging regressions).
- Evidence table shows the cost curve and answers "is 560x336 affordable at N
  bodies"; report the answer to the owner.
- Deterministic and isolated across concurrent sims.
- Unit tests under a second; anything that stands up a map is evidence.
