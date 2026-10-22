# Swarm movement cost

Status: ACTIVE

Written: 2026-10-21
Updated: 2026-10-22 — melee spikes removed: bounded own paths (`battle.swarm.localPathBudget`)

What moving the swarm costs by map size and swarm size, with and without its
flow fields (`colony-breach-nouns.md`, Swarm goal), and what that says about
how large a Colony Breach map and swarm can be. Re-measure with
`gradlew.bat profileSwarmFlow`; the numbers below are one Windows workstation
run and are to be read against each other, not as a budget.

## The question

The owner wants a ~400x240 colony and asked whether Conquest scale (560x336) is
affordable. Per-body pathing makes strategic movement cost bodies x path length,
and path length grows with the map; a shared field per goal makes it one
integration per goal refresh, whatever the swarm's size.

## How it was measured

A generated city at each size (BSP, seed 42), three columns of eight Marines
walking their own 60-cell routes out and back (so the goal moves the whole
time), the swarm spawned at least 80 cells away, so the 450 measured ticks are
the approach rather than a melee. Marines unarmed and healed: the question is
movement. Serial scheduler, one battle at a time, after 90 warm-up ticks. Two
replicas of each lever, side by side in one JVM, replayed byte-identical.

## Readings (ms per tick)

Over = ticks of 450 slower than 10 ms. With the field off, swarms below 300 live
units path with A* per body; at 300 and above they use the existing shared
reverse fields keyed by each target's cell.

| Map | Bodies | Field | Median | p90 | p99 | Max | Over |
|---|---|---|---|---|---|---|---|
| 280x168 | 150 | off | 0.17 | 0.39 | 38.9 | 66.3 | 30 |
| 280x168 | 150 | on | 0.32 | 0.66 | 3.9 | 16.3 | 1 |
| 280x168 | 1000 | off | 0.97 | 2.08 | 8.7 | 14.1 | 3 |
| 280x168 | 1000 | on | 1.04 | 1.97 | 4.3 | 10.4 | 1 |
| 400x240 | 150 | off | 0.14 | 0.28 | 34.2 | 37.1 | 30 |
| 400x240 | 150 | on | 0.20 | 1.32 | 2.8 | 3.2 | 0 |
| 400x240 | 600 | off | 0.50 | 1.02 | 13.8 | 27.8 | 30 |
| 400x240 | 600 | on | 0.54 | 1.72 | 3.8 | 4.9 | 0 |
| 400x240 | 1000 | off | 0.63 | 0.82 | 11.5 | 11.8 | 30 |
| 400x240 | 1000 | on | 0.72 | 1.91 | 3.9 | 7.3 | 0 |
| 560x336 | 64 | off | 0.09 | 0.27 | 57.7 | 81.4 | 30 |
| 560x336 | 64 | on | 0.08 | 2.17 | 2.6 | 5.3 | 0 |
| 560x336 | 150 | off | 0.15 | 0.35 | 139.8 | 190.4 | 30 |
| 560x336 | 150 | on | 0.14 | 2.07 | 2.4 | 5.9 | 0 |
| 560x336 | 300 | off | 0.23 | 0.36 | 19.5 | 21.0 | 30 |
| 560x336 | 300 | on | 0.24 | 2.23 | 2.8 | 3.2 | 0 |
| 560x336 | 600 | off | 0.61 | 0.94 | 26.0 | 27.8 | 30 |
| 560x336 | 600 | on | 0.64 | 3.46 | 4.5 | 5.6 | 0 |
| 560x336 | 1000 | off | 0.72 | 1.15 | 22.2 | 24.6 | 30 |
| 560x336 | 1000 | on | 1.18 | 4.23 | 5.9 | 9.1 | 0 |

## What it says

- **Where the tick went was never the median.** Median swarm cost is under a
  millisecond with or without fields. It went into spikes: every ~15 ticks
  (half a second) one tick paid for a long A* to a distant, moving target, or
  for a whole-map reverse field keyed on the cell a Marine had just stepped
  into. On 560x336 those were 20-190 ms — four to six dropped frames, twice a
  second, at every swarm size measured.
- **With fields, map size stops mattering to the tail.** No per-body swarm path
  is run outside local pursuit. The integration (about 8 ms for the whole
  560x336 map) is spread over three ticks at a fixed cell budget, which moves
  about 2 ms onto the p90 and takes it off the worst tick.
- **The swarm moves no worse.** Mean distance to the Marines at the end of the
  window matches the control within a cell at every size, nobody stalls, and in
  the `swarm-chokepoint` scene the field gets a swarm through a door and round
  a block 2% faster than per-body paths.

**Answer: yes, 560x336 is affordable for swarm movement at 1000 bodies** —
worst tick 9 ms, none over 10 ms, against per-body pathing that misses frames
twice a second at any size on that map. 400x240 is comfortable at 1000. The
next costs at scale are not movement: target selection and melee scale with
bodies (about 0.5 µs a body here, before contact), and a real battle adds fire,
LOS and rendering, which this harness leaves out on purpose.

## Packs (`-Dbattle.swarm.pack`)

Pack movement (`colony-breach-nouns.md`, Pack movement) adds a serial plan per
pack (a 14-cell descent from its anchor at most every 15 ticks, a few
clearance reads beside it, the hunt phase) and moves each member through the
traffic seam with an offset instead of the plain path follower. The harness
spawns Runners in clusters of six, each stamped as a pack, on every lever, and
the `pack` lever moves them as packs; `on` is its control.

560x336, 1000 bodies, one run (ms per tick, median / p99 / max; ticks over 10 ms):

| Lever | Tick | Over | Swarm behaviour | Mean dist |
|---|---|---|---|---|
| on (control) | 1.67 / 4.0 / 4.5 | 0 | 0.35 / 0.7 / 0.7 | 154.7 |
| pack | 1.96 / 5.0 / 5.5 | 0 | 0.51 / 0.8 / 1.0 | 164.2 |

About 0.3 ms on the median and a millisecond on the worst tick: still
affordable at 1000 bodies, and two replicas side by side stay identical. The
spread between runs on this machine is as large as the effect (the `off` row
moved by a millisecond between two runs of the same code), so read a pack
change against its own run's `on` row. A pack travels about 6% slower than a
lone body, because the anchor runs at 92% so the fan can keep its places: the
price of running as a pack, shown in the mean distance still to go after 540
ticks.

The first cut descended every pack's lane every 6 ticks and re-pathed every
member each time: median +0.6 ms and two ticks over 10 ms. The lane is now
taken again only once the anchor runs off its head, or at most every 15 ticks.

## The press and sub-packs (`-Dbattle.swarm.packSplit`)

The press (`colony-breach-nouns.md`, The press, sub-packs) runs only once a
pack is on its quarry, which the default harness never reaches: its swarm
spawns 80 cells out and the window is the approach. `-Dbattle.swarmFlow.spawnDistance=20`
brings the swarm into contact inside the window. 560x336, `pack` lever, one
run each with the split on and off (ms per tick, median / p99 / max):

| Window | Bodies | Split | Tick | Swarm behaviour |
|---|---|---|---|---|
| approach | 1000 | on | 3.59 / 11.0 / 14.1 | 0.91 / 3.3 / 4.3 |
| approach | 1000 | off | 2.47 / 6.3 / 8.5 | 0.62 / 1.1 / 2.5 |
| melee | 1000 | on | 3.10 / 380 / 498 | 0.79 / 376 / 493 |
| melee | 1000 | off | 2.86 / 393 / 632 | 0.72 / 389 / 627 |

On the approach the press does not run, and the gap is the machine: the
split-on run's own `on` rows (no packs at all) were a millisecond slower than
the split-off run's, and pack against `on` is +0.25 ms in one and +0.22 in the
other. In melee the press costs about 0.07 ms of swarm behaviour on the median
and nothing on the tail. Replicas stay identical.

**The melee tail is not the press, and it is large.** Both runs, and the
`on` lever without packs, spike to 140-630 ms once contact is made, all of it
in `SWARM_PATHFIND`: the local pursuit's per-body paths, which the fields do not
cover. Measured and removed below.

## Melee: bounded own paths (`-Dbattle.swarm.localPathBudget`)

**Where it went.** Not repeated A* and not crowds re-planning together: one to
seven path calls a tick, each of them a whole-map answer. At 300 live units and
above a body's own path is served from a shared reverse field keyed on the goal
cell (`SharedGoalPolicy`), which pays off when many movers share one fixed goal.
In melee nobody shares one: the victim steps into a new cell every few ticks, a
pack member's place round its quarry is its own cell, and each new goal cell is
a fresh reverse Dijkstra over all 188,160 cells of 560x336, about 15-20 ms
apiece. The field-build time and the path time in the table below are the same
number. Below 300 units the same call is a plain A*, which floods the map round
a goal it cannot reach.

**The fix.** A body's own path is a crowd-aware A* with an expansion cap
(`SwarmMovement.ensurePath`): 1,089 pops (the 33x33 box round it) for a cell
within 16 cells, 4,096 for a farther one. A victim the capped search cannot
reach is not close: the body goes back to the prey field, which goes round to
it, and a pack member whose place cannot be reached runs its own pursuit
instead. Only callers with no field to fall back on (a scavenger to a far dead,
a breach approach, a civilian quarry) still take the whole-map answer after a
miss. `-Dbattle.swarm.localPathBudget=false` is the control.

**Target:** p99 under 10 ms and the worst tick under one 60 Hz frame (16.7 ms)
in a melee window. 560x336, `spawnDistance=20`, 900 measured ticks, two runs of
the same commit, one with the control switch (ms per tick, median / p99 / max;
ticks over 10 ms):

| Bodies | Lever | Control tick | Over | Bounded tick | Over | Contact (ctl / bnd) | Mean dist (ctl / bnd) |
|---|---|---|---|---|---|---|---|
| 300 | on | 0.80 / 65.8 / 94.9 | 92 | 1.13 / 5.9 / 14.1 | 1 | 18 / 24 | 116.4 / 116.5 |
| 300 | pack | 1.00 / 188.5 / 324.0 | 166 | 0.90 / 4.3 / 10.5 | 1 | 12 / 14 | 127.6 / 127.7 |
| 600 | on | 1.35 / 146.3 / 193.2 | 116 | 1.23 / 5.9 / 8.7 | 0 | 34 / 33 | 118.4 / 118.5 |
| 600 | pack | 2.17 / 459.4 / 660.8 | 305 | 1.34 / 4.1 / 5.7 | 0 | 29 / 30 | 128.1 / 128.2 |
| 1000 | on | 2.22 / 171.7 / 217.6 | 173 | 2.18 / 8.2 / 14.8 | 4 | 58 / 82 | 125.2 / 125.1 |
| 1000 | pack | 2.84 / 653.6 / 849.0 | 362 | 2.51 / 6.3 / 12.3 | 1 | 50 / 52 | 134.4 / 134.4 |

Swarm path time, p99 / max: control 62-647 / 92-842 ms; bounded 0.1-2.8 /
0.3-7.0 ms. The swarm arrives as it did (mean distance within 0.1 cell, contact
as high or higher bar one body, stuck bodies within one of the control), and two
replicas side by side stay byte-identical. The remaining ticks over 10 ms are
the prey field's own refresh (about 2-5 ms) landing on a tick that also carries
several capped searches; none reaches a frame.

A first cut capped only cells within 16 and let a farther miss take the whole
map: the plain swarm came right, but a pack's places round a quarry it was
still running in on missed the cap together and one tick paid 164 ms. A miss now
falls back to the field wherever there is one.

## Rejected: crowd avoidance in the descent

Weighting a field step by the occupancy map was tried at 2 to 20 units per
occupant. Every weight slowed the swarm's arrival in step with its size (up to
15%) and freed no body that the plain descent left stuck; the occupancy map
marks where paths end, not where bodies stand, so it is the wrong crowd signal.
A real crowd term would need body positions and belongs to a local steering
pass, not to the field.
