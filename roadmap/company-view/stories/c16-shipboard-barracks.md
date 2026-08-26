# C16 — Shipboard Barracks

Status: COMPLETE

Written: 2026-08-25

Updated: 2026-08-25 — completed the furnished habitation deck and shared
battle-owned ambient task choreography for leisure, workshop work, and range use.

## Outcome

Give the player an ordinary, atmospheric place to browse line squads while the
company is aboard ship between operations. Barracks owns no equipment or personnel
mutation; deliberate billet inspection and loadout editing remain in Fleet Armory.

## First slice

- Add Barracks as a real planet-free destination in the persistent shipboard shell.
- Present a scalable line-squad selector, one selected squad's room, and a complete
  twelve-billet muster.
- Compose the quarters from the battle renderer's indoor grid, props, camera, and
  real layered marine equipment appearances.
- Show only ready personnel physically aboard ship as room actors. Preserve WIA,
  stationing, and vacancies as explicit roster-backed absence states.
- Keep campaign state read-only. Room actors may use the battle-owned ambient task
  seam, but create no combat, recovery, stationing, roster, or equipment mutation.
- Produce deterministic headless evidence without requiring Starsector or OpenGL.
- Put ready room actors in the bounded `BattleSimulation`, and collect the same
  battle-renderer command passes for live and headless output.
- Furnish a recognizable sleeping deck, commons, lockers, and isolated practice range
  from the same data-defined doodads used by generated buildings.
- Cycle real marine entities through berths, leisure, inspection, and range stations;
  practice-fire presentation uses each marine's issued primary weapon.

## Acceptance

- Company HQ, Barracks, Armory, and Mech Lab route directly through the same shell.
- Selecting a squad changes both the room population and muster without rebuilding
  campaign organization.
- A full selected squad inhabits twelve assigned berths and shared leisure/range
  stations with its actual issued armor, primary weapon, grade, and carried special
  equipment.
- WIA marines remain named with RTD time but do not appear as ready room actors.
- Stationed squads leave the room empty and say why.
- The Barracks wide snapshot is deterministic and the existing UI suite remains green.
- The snapshot contains the actual room simulation's tiles, props, camera projection,
  and layered marine actors rather than a tooling-only reconstruction.

## Follow-up questions

- Should selecting a marine in the room open a read-only personnel dossier, or should
  all detailed inspection remain behind the Armory transition?
- How should several owned companies map onto physical flagship habitation decks once
  the campaign gains a real multi-company authority?
