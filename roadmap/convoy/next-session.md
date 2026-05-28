# Convoy — Next Session

Read [`overview.md`](overview.md) first for concept + architecture.
Shipped work is sealed in [`complete/`](complete/).

## Commit chain so far

```
76fe54d  Convoy V1: RoadGraph + ground-vehicle layer + debug spawn   (2026-05-20)
b5227e1  convoy: bicycle model + pure pursuit replace AirBody hover
8eedc63  convoy: Reeds-Shepp LZ docking + VehicleFootprint collision check
5f9dd43  mapgen: road reservation — stampers respect the road graph
2703184  battle: Reeds-Shepp CCC family — tight U-turns
7f958fd  battle: APC overwatch timer — 20s fire support then depart
a6e4fc2  battle: remove MILITIA_TRUCK — APC is the sole convoy vehicle
3e7dfa9  convoy: wall constraint + roadmap refresh
1315241  battle: resource blackboard — compound-driven reinforcement tickets
11b2c9f  battle/compound: trigger + means gating on compound state
11b012f  battle: ConvoyMeans avoids stacking trucks at the same junction
b1c405a  convoy: Hybrid A* planner, vehicle debug tools, reactive wall recovery
7202a08  convoy: direct pose playback, fresh outbound pathing, three-tier refinement
4096a1d  battle: filter convoy entry to defender side + halve reinforcement rate  ← latest
```

(Abbreviated — `06ad3ac` APC sprite facing, `2112bb6` spawn dumper,
`0f63a2e` debug toggle widget, `ef4cfeb` per-faction deboard roster also
belong to this stack. Full per-section mapping in
[`complete/v1-polish.md`](complete/v1-polish.md).)

## State of play

- **V1 PoC + V1-polish maturation: shipped.** Bicycle kinematics,
  Reeds-Shepp (CSC + CCC) docking, road reservation, wall constraint,
  Hybrid A* planner, direct pose playback, vehicle debug tools.
- **HEAVY_APC** is the sole vehicle — 4-cap, roof HEAVY_MG turret, 20s
  overwatch, `army-apc.png`. MILITIA_TRUCK retired.
- **Conquest reinforcement integration: shipped** as `ConvoyMeans`
  (Stage 2 item 1). `DEBUG_SPAWN_TEST_CONVOY` is legacy/unused. LZ
  separation between concurrent dispatches is in.
- Build green; `ReedsSheppTest` passes (CSC + CCC cases).

## Active stories (priority order)

1. **[`multi-truck-convoys`](stories/multi-truck-convoys.md)** — same-road
   staggered following (LZ separation already shipped). Next visual
   upgrade.
2. **[`truck-infantry-interaction`](stories/truck-infantry-interaction.md)** —
   marines dodge / trucks squash / hybrid. Makes trucks feel real.
3. **[`vehicle-damage`](stories/vehicle-damage.md)** — `Vehicle.hp`,
   anti-vehicle fire, wrecks-as-doodads. Unblocks air↔ground later.
4. **[`vehicle-variants`](stories/vehicle-variants.md)** — supply truck +
   light scout (content slice).
5. **[`driving-feel-tuning`](stories/driving-feel-tuning.md)** — finishing
   pass; drops in alongside playtest feedback, not a standalone sprint.

Parked design ideas (BSP-perim infiltration, tanks, air↔ground,
deboarded-squad → commander wiring) live in [`overview.md`](overview.md)
§ Parked.

## Sanity check before resuming

- `gradlew.bat compileJava` clean.
- `gradlew.bat test` — `ReedsSheppTest` green.
- `git log --oneline -5` should show `4096a1d` or your own recent work at
  the top.
