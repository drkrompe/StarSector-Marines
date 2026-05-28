# Story: driving-feel tuning pass

**Queued (finishing pass).** The V1-polish controller passed the "drives
roads sensibly" bar. After watching dozens of convoys across map types,
do a deliberate tuning pass. Most of these are 1-line changes to
`GroundSystem` constants or the HEAVY_APC handling profile, and they drop
in alongside playtest feedback rather than as a standalone sprint.

## Things to watch for

- Does the truck commit to corners convincingly, or does the brake formula
  make it too cautious?
- Does the look-ahead occasionally pick a "too far" target on near-aligned
  waypoints and cause the truck to clip corners?
- Should the truck back up when its goal is briefly behind it (a U-turn at
  a dead-end), or stop and re-plan? The Hybrid A* reverse segments +
  fresh-outbound pathing already cover most of this — confirm it holds at
  awkward junctions.

## Adjacent: terrain-cost steering

The road-preferred / off-road-penalty layer (extension point identified in
`VehicleFootprint`, see [`../complete/v1-polish.md`](../complete/v1-polish.md))
is a richer version of the same "feel" knob. Not needed until a mission
features off-road traversal; tune binary-walkability feel first.
