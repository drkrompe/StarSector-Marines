# AI stories

Status: ACTIVE — squad doctrine, contact reasoning, and tactical AI extensions remain here; strategic mission-command work has its own feature board.

Written: 2026-08-23

Updated: 2026-09-03 — the navigation substrate's derivations are being tiled so
a one-cell breach stops costing a whole-map sweep.

Earlier 2026-09-02 — the allied faction shipped and left the board.

| Story | State | Intent |
|---|---|---|
| `tiled-navigation-derivations.md` | IN PROGRESS | Tile the clearance mask, its component labels, and the greedy mesh so a breach recomputes the tiles it touched instead of all 188,160 cells. |
| `contact-reaction-doctrine.md` | IN PROGRESS | Close and validate the live contact-initiative gap, then finish doctrine, formation-tempo, and acquisition acceptance. |
| `commander-field-analysis.md` | DRAFT | Add optional read-only frontline/bulge diagnostics over faction-local snapshots without granting assignment authority. |
| `retire-mech-combatant-behavior.md` | DRAFT | Remove the obsolete mech behavior shell while preserving the shared mech firing contract under an honest name. |
| `squad-range-quorum.md` | DRAFT | Make `IN_RANGE_OF_TARGET` a quorum instead of an any-member read, without the approach cost the first attempt measured. |
| `clear-zone-vantage-probe.md` | DRAFT | Decide whether ClearZone should ask the vantage probe before dropping a target; measured as a one-fixture-each-way trade. |

See `stories.md` in the Mission Command feature for foundation, evidence,
Conquest, Sabotage, Assault, Raid, Extraction, intervention, and faction-command
work.
