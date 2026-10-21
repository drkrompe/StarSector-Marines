# Swarm movement cost

Status: ACTIVE

Written: 2026-10-21

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

## Rejected: crowd avoidance in the descent

Weighting a field step by the occupancy map was tried at 2 to 20 units per
occupant. Every weight slowed the swarm's arrival in step with its size (up to
15%) and freed no body that the plain descent left stuck; the occupancy map
marks where paths end, not where bodies stand, so it is the wrong crowd signal.
A real crowd term would need body positions and belongs to a local steering
pass, not to the field.
