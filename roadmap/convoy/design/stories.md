# Convoy — Open Stories

Status: ACTIVE

Written: 2026-08-23

`convoy-nouns.md` is the canonical model. Routing and navigation are
implementation subtracks of this one noun, not separate domains.

| Story | State and dependency |
| --- | --- |
| `convoy-proof-admission.md` | **IN PROGRESS.** Bound raw snapshot startup and prepared proof count before the existing search budget. |
| `route-and-motion-acceptance.md` | **READY — manual acceptance.** Exercise cost-field routing, continuous cornering, docking, recovery, and off-map departure in one eyes-on convoy run. |
| `slice-3-retire-roadgraph.md` | **Ready for a bounded audit/cleanup.** `RoadGraph` remains live generator and drop-off-selection data, but the old `ConvoyPlanner` route expansion and dead debug-spawn path are concrete retirement candidates. Preserve graph consumers that serve map generation, reservation, validation, preview/debug, or selection; do not delete the graph merely because routing no longer follows it. |
| `slice-3-recovery-ladder.md` | **Partially shipped.** Reverse recovery, proactive impossible-turn detection, turn-aware route rejection, cumulative avoidance re-routing, and safe on-grid planner failure are live. Decide and implement only the terminal no-route policy. Depends on the route playtest. |
| `slice-4-tuning-feel.md` | **Partially shipped; playtest-led.** Speed-scaled lookahead and the corner governor landed. Tune controller and cost/clearance knobs together from real runs; cumulative avoidance is correctness substrate, not a conditional feel fix. |
| `multi-truck-convoys.md` | **Queued.** LZ separation is shipped; add one dispatch's staggered same-route vehicles and following distance after the single-vehicle route/control behavior passes playtest. Extend planner-budget evidence to simultaneous vehicles. |
| `slice-5-perf-budget.md` | **In progress.** Bound fresh synchronous recovery work; distinguish physical overlap from padding-only planner refusal before changing escape behavior. |
| `truck-infantry-interaction.md` | **Queued.** Define authoritative moving-vehicle occupancy/collision before choosing yielding, impact, or hybrid behavior; current vehicles are intentionally outside infantry grid collision. |
| `vehicle-variants.md` | **Queued.** Supply and scout roles need their own payload authority and acceptance cases; a supply delivery must integrate with reinforcement supply rather than merely reuse troop deboarding. |

The completed routing and controller slices are listed in `shipped.md`.
