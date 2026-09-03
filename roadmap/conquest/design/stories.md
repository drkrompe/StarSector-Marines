# Conquest stories

Status: ACTIVE — Conquest objective and territorial work remains here; its paired commander acceptance is tracked by Mission Command.

Written: 2026-08-23

Updated: 2026-09-02 — `conquest-lanes.md` shipped and was folded into
`precincts.md` and `conquest-nouns.md`; the beachhead is now a marine-held
compound and the open question below is what losing it costs.

Earlier 2026-09-01 — added `conquest-lanes.md`: resistance in depth along the
tracks, the layers a Conquest grind is missing on the 560x336 map.

The territorial model, compound loop, and Conquest victory law are implemented.
`conquest-command.md` owns the attacker/defender strategy and its active live
acceptance stories. Reinforcement mechanics remain owned by the Reinforcement
feature:

| Story | State | Intent |
|---|---|---|
| `conquest-560-contact.md` | PROPOSED | Three decisions the 560x336 matrix surfaced: the standoff is measured from the wrong side of the beachhead and correcting it alone measures worse, arrival lift does not scale with committed seats, and an open compound's capture zone is the outdoors. |
| `lift-conversion.md` | IN PROGRESS | The seat-sized lift lands the force early and converts none of it: a derivation that can return less lift than the ferry, one zone target absorbing 89% of the live force, and an adjacency claim that reaches the whole outdoors. |
| *(lane preference)* | PROPOSED | `STRIP_COUNT = 3` and `stripFor` reading the landing lateral put 100% of full-strength-west's squads on track 1, so two of the three lanes are advanced by nobody. |
| *(open question)* | UNDECIDED | The beachhead is now a compound the marines hold and can lose, and losing it currently costs nothing but the marker. What it should cost — the shuttle cycle, the rejoin lift, the victory law, or nothing at all — wants deciding before a rule is written for it. |
| `progressive-reinforcement.md` | PARKED | Manually verify defender frontline response, safe delivery, and supply degradation across a Conquest push. |
| `biome-counterattack.md` | PARKED | Manually tune and verify the telegraphed defender counterattack as a territorial swing. |

Do not create duplicate Conquest stories for those systems. Their standing
contracts live in `reinforcement-nouns.md`; this feature supplies the
territorial context in `conquest-nouns.md`, and Mission Command adapts that
context through `conquest-command.md`.
