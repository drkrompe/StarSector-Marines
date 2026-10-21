# Mission flow

Status: IN PROGRESS

Written: 2026-10-21

Closes the `garrisonables.md` open question **Mission flow**: the procedural
mission defenders (Raid, Assault, Sabotage, Extraction, through
`MissionGarrisons`) man only what a squad can reach while pressure lasts and
have no staging tier to move the reserve toward pressure.

## Scope

- A flow tier inside `MissionGarrisons`, side-neutral and place-general:
  pressured blocks cluster into areas (`GarrisonFlow.areas`, shared with
  Conquest); an area with a warm place to man asks for squads by
  `GarrisonFlow.demand`; free **roving** offers are staged for it, pickets
  before massing, within the staged cap and the reserve floor; staged squads
  stay offered and are drawn first by the area's own manning; a quiet area
  gives them back after a dwell.
- The staging cell is measured **toward the places the area presses**, not
  along a command axis: the missions have none, and the line from the threat
  to what it threatens is the defender's side by construction. Shared as
  `GarrisonFlow.towardPlaces`, so Conquest's axis staging could later use it.
- **Not every fight is into prepared positions** (law, 2026-10-21): an area's
  staged squads alternate **back** (at the places, to man them) and
  **forward** (a short standoff from the attack toward the places, an area
  defence in the street). A forward squad is never sent to man; it meets the
  attack. `-Dbattle.mission.flow.forward=false` stages everyone back.
- Mission duties plan first; only roving offers are staged. Duty posts,
  authored garrisons, Extraction's interdiction, machines, crews, demolition
  squads are never staged.
- `-Dbattle.mission.flow=false` is the control: reactive manning as before.

Conquest's flow is left as it is: it already shares `GarrisonFlow`'s areas,
demand, order and cap; its rally/reservation table and axis staging are
Conquest-only and its figures must not move.

## Evidence

- Per run (`ReactiveDefenceEvidence`): stage and forward orders, stage
  releases, manning arrivals before the pressing contact reached the place
  against after it, and the **fight mix**: defender squad-samples in contact
  on a post (a fighting-position cell or inside a held building) against in
  the open, and in the open while moving.
- `commanderEvidence` for Raid, Assault, Sabotage, Extraction at six seeds
  each, flow on against `-Dbattle.mission.flow=false` (and reactive off for
  the mix baseline). A drop of the open share toward zero is a regression.
- Scene `mission-flow`: a reserve stages toward a pressed area before manning,
  stays free (offered, no place), mans from the staging, and is given back
  when the area is quiet.

## Acceptance

Green: mission-defence, mission-envelopes, machine-guard, sally-door,
place-counterattack, reactive-belt, attacker-holds-ground, mission-flow; unit
tests under a second; noun doc updated; story folded on ship.
