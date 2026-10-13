# Story — Aircraft against ships (gap G3)

Status: ACTIVE

Written: 2026-10-13

Read `air-nouns.md` (**Overhead ships**) and `warship-behaviours.md` (gap table, AirThreatPicture, Decided: ship class parameter biases) first.

## Scope

Owner approved G3: strike aircraft can hurt a warship. Out of scope: G4 (ships as targets for ship mounts, ship-vs-ship, escorts) and G5 (ground defences hurting ships). A ship stays a non-entity; the route is narrow.

## Decisions

- **Who may attack a hull.** Any hostile strike sortie (`mission.strikeSortie`) whose `AirOrdnance` gets through that hull's armour at all, by the hull's own armour rule (`Warship.effectiveDamage > 0`). Gun and bomb fighters qualify against most hulls; a beam that glances off a battleship's plate does not, and is neither sent at the hull nor counted as a threat to it. Shuttles and boats are never attackers.
- **How a hull is chosen.** A hull is a candidate beside the densest ground concentration (`EnemyConcentration`), in both dispatchers (`AirStrikeSystem` field sorties, `AirCoverSystem` wings). If an eligible hull exists it comes first; the ground is the fallback. Eligible: hostile, not destroyed or departing, hurtable by the craft that would fly, and fewer than two of the side's craft already working it. The cap keeps one capital from absorbing every sortie. No posture or mission input: the least invasive rule that does not invent new AI.
- **How damage arrives.** No new route. The sortie flies the ordinary attack run at the hull's centre; its rounds land as ordinary detonations and `WarshipSystem.onDetonation` already applies shield (flux on the pool) then armour. Nothing about aircraft weapons changes.
- **Threat picture.** `AirThreatPicture` counts an armed craft only if its ordnance can hurt that ship. Anything else about the score is unchanged.
- **Shield type in the weights (owner decision).** Armed-craft weight scales by shield: none 1.0 (identity, the tuned row), front 0.85, omni 0.7; phase judged as front. Declared and hostile-ship weights untouched.
- **Ship response.** Already built: point defence, ship-against-air fire under the air-superiority posture, shield auto-raise.

## Acceptance

- Unit: target choice (hostile only, glancing ordnance refused, two-attacker cap), weight ordering by shield type, picture counting by ordnance.
- Scene `warship-air-strike`: control with no wing; the same wing against a shield-down hull (hull loses points, at most two on it at once); against a shielded hull (shield takes flux, hull loses less); a doctrine hull (air-superiority with armed craft as reason, flak kills some); beam fighters against an Onslaught (flying, but not sent at the hull, not a threat).

## Plan

One slice: `HullStrike` / `HullTarget`, the two dispatcher hooks, `BattleSimulation.hostileHulls`, picture and weights changes, scene and tests, fold into `air-nouns.md` and `warship-behaviours.md`.
