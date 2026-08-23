# Convoy — Open Stories

Status: ACTIVE

Written: 2026-08-23

`convoy-nouns.md` is the canonical model. Routing and navigation are
implementation subtracks of this one noun, not separate domains.

| Story | State and dependency |
| --- | --- |
| `route-and-motion-acceptance.md` | **READY — manual acceptance.** Exercise cost-field routing, continuous cornering, docking, recovery, and off-map departure in one eyes-on convoy run. |
| `slice-3-retire-roadgraph.md` | **Ready for a bounded audit/cleanup.** `RoadGraph` remains live generator and drop-off-selection data, but the old `ConvoyPlanner` route expansion and dead debug-spawn path are concrete retirement candidates. Preserve graph consumers that serve map generation, reservation, validation, preview/debug, or selection; do not delete the graph merely because routing no longer follows it. |
| `slice-3-recovery-ladder.md` | **Partially shipped.** Reverse recovery, proactive impossible-turn detection, and avoidance re-routing are live. Decide and implement the terminal no-route policy, then address turn-aware route/approach validation only if playtest shows static clearance repeatedly choosing unturnable approaches. Depends on the route playtest. |
| `slice-4-tuning-feel.md` | **Partially shipped; playtest-led.** Speed-scaled lookahead and the corner governor landed. Tune controller and cost/clearance knobs together from real runs; decide whether residual recovery pauses or repeated reroutes justify cumulative avoidance or another focused fix. |
| `multi-truck-convoys.md` | **Queued.** LZ separation is shipped; add one dispatch's staggered same-route vehicles and following distance after the single-vehicle route/control behavior passes playtest. This is the trigger for real planner-budget measurement. |
| `slice-5-perf-budget.md` | **Deferred behind multi-truck convoys.** Profile live simultaneous planners first; only then add the cache, scheduling, or reuse that measured frame cost requires. |
| `truck-infantry-interaction.md` | **Queued.** Define authoritative moving-vehicle occupancy/collision before choosing yielding, impact, or hybrid behavior; current vehicles are intentionally outside infantry grid collision. |
| `vehicle-damage.md` | **Queued.** Add vehicle damage, destruction, wreck blocking, and passenger fate as one combat-authority slice. It unlocks meaningful air-to-ground attacks. |
| `vehicle-variants.md` | **Queued.** Supply and scout roles need their own payload authority and acceptance cases; a supply delivery must integrate with reinforcement supply rather than merely reuse troop deboarding. |

The completed routing and controller slices are listed in `shipped.md`.
