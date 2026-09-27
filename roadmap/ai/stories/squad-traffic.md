# Local squad traffic experiment

Status: EXPERIMENT

Written: 2026-09-26

Updated: 2026-09-26 — terrain-safe handback remains member-owned after steering expires.

## Question

Can nearby infantry squads spread laterally on open ground, compress before a
constrained passage, and fan out afterward without choosing new goals or
repeatedly searching routes? `ai-nouns.md` owns execution and authority;
`continuous-positions-nouns.md` owns physical movement and terrain legality.

## Boundaries

- An optional movement preference, not rigid squad collision or a new planner.
- Quiet objective travel only; contact, survival, authored posts, direct
  control, Mechs, vehicles, and drones keep their existing movement authority.
- Freeze local squad inputs before unit dispatch. Workers read hints without
  acquiring cross-squad locks. Spatial enumeration and terrain probes are bounded.
- Preserve paths, destination/arrival semantics, and repath throttles. Waiting
  for traffic is not a route failure. No new path search from the traffic layer.
- Compression anticipates nearby terrain; retained preference must not push a
  member through walls or a closed shared edge. Yielding is soft and bounded.
- A displaced member drops stale steering on new intent, but keeps swept
  cell-path following until a route completes, including combat handback.

## Experiment

Available behind `battle.squad.traffic`, off by default until evidence supports
adoption. `battle.squad.trafficYield=false` isolates spreading without the speed
preference. The default experiment retains at least 85% of ordinary speed.
Compare subject and control on the same build through the shared
opt-in scene harness. Measure open-ground lateral separation, corridor progress,
re-expansion, longest no-progress interval, route work, and added update cost.
Exercise convergence, crossing traffic, and terrain-edge refusal, including
mid-tick path replacement and contact authority. Add focused small-input tests
for the solver and movement seam; whole-world evidence stays outside `test`.

Adoption requires useful lateral spread without starvation, illegal terrain
motion, a repathing feedback loop, or a material throughput regression. Timing
figures are local observations, never cross-machine test thresholds.

Run `sceneEvidence -Pscene=squad-traffic` for paired open, passage, and crossing
verdicts; `createSnapshots -Psnapshot=squad-traffic` renders those same loops
with navigation barriers and squad-centroid labels. The two controls are
constructed explicitly, independent of the outer traffic switch. The recorded
arrival proxy is all squad centroids within six cells, not plan completion.
Use the production `profileConquestTail` with the traffic switch in both states
for runtime evidence; small-scene timings are not proof of Conquest improvement.

Remaining adoption questions: true opposing flows, narrower doorways and
distributed membership, effects on capture/contact behavior, and late-battle
route amplification. A movement layer cannot repair the shared-field coverage,
synchronous-fallback budget, or fixed-goal occupancy-search defects. Changes
here must not be credited with solving those independent routing problems.
