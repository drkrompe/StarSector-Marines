# AI stories

Status: ACTIVE — squad doctrine, contact reasoning, and tactical AI extensions remain here; strategic mission-command work has its own feature board.

Written: 2026-08-23

Updated: 2026-09-03 — the tiled navigation derivations shipped and left the
board; two whole-map costs measured on the same day's Conquest are rows: the
zone graph's 26 ms rebuild and the shared-goal field's 20 ms regrow every
fifteen ticks.

| Story | State | Intent |
|---|---|---|
| `contact-reaction-doctrine.md` | IN PROGRESS | Close and validate the live contact-initiative gap, then finish doctrine, formation-tempo, and acquisition acceptance. |
| `commander-field-analysis.md` | DRAFT | Add optional read-only frontline/bulge diagnostics over faction-local snapshots without granting assignment authority. |
| `retire-mech-combatant-behavior.md` | DRAFT | Remove the obsolete mech behavior shell while preserving the shared mech firing contract under an honest name. |
| `squad-range-quorum.md` | DRAFT | Make `IN_RANGE_OF_TARGET` a quorum instead of an any-member read, without the approach cost the first attempt measured. |
| `clear-zone-vantage-probe.md` | DRAFT | Decide whether ClearZone should ask the vantage probe before dropping a target; measured as a one-fixture-each-way trade. |
| `zone-graph-tiled-rebuild.md` | DRAFT | Scope `ZoneGraph.rebuild()` to the cells that changed; a 26 ms whole-map rebuild on the cell-less dirty path is the largest number left on a breach tick. |
| `shared-goal-field-cadence.md` | DRAFT | Stop regrowing every shared-goal reverse field on a fifteen-snapshot clock; a 20 ms whole-map Dijkstra per goal per fifteen ticks is the early-battle spike and half of a serial replay's simulation time. |

See `stories.md` in the Mission Command feature for foundation, evidence,
Conquest, Sabotage, Assault, Raid, Extraction, intervention, and faction-command
work.
