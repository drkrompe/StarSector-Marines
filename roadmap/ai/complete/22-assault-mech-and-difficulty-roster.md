# 22 — Assault mech and difficulty roster

**Shipped 2026-08-19 in `1ef74f23`.**

## Player-facing result

A low-risk contract teaches infantry combat without an unexpected defender
mech. Medium risk introduces one recognizable Bulwark. High-risk battlefields
field a complementary mech family: the Bulwark controls ground, the Sirocco
shapes it at range, and the Hound pushes through it as the moving point unit.

The doctrine is faction-neutral. A defender Hound counterattacks toward a
known contact, while a marine Hound executes the same assault movement from a
commander assignment. Story E's infantry screen consumes that moving friendly
mech in the following slice; it was deliberately not folded into this story.

## Shipped difficulty contract

Heavy-armor availability remains the campaign gate. Within that gate:

| Risk | Defender mech roster |
| --- | --- |
| LOW | none |
| MEDIUM | one Bulwark |
| HIGH SABOTAGE | one Hound, preserving the lone-contact mission flavor |
| HIGH ASSAULT / RAID / EXTRACTION | one Bulwark, one Hound, one Sirocco |
| HIGH CONQUEST | two complementary groups of Bulwark, Hound, Sirocco |

`DefenderRoster` now carries the immutable concrete `MechVariant` list instead
of making `BattleSetup` reinterpret a count. Production spawn applies each
profile before entity allocation and attaches its profile loadout/default
doctrine afterward. Lighter profiles replace existing mech entries; no tier
adds bodies beyond the old 0/1/3/6 ceiling.

## Shipped assault doctrine

- `MechRole.ASSAULT` is hardware-independent doctrine; Hound defaults to it.
- `AssaultAssignedObjectiveGoal` prefers a valid zone assignment and otherwise
  activates on a live/last-seen local contact.
- `BreachAndAssault` enters the assigned zone, refuses to chase a contact back
  out of it, and closes to a short standoff while firing installed weapons.
- Goal and action derive allegiance from the squad/entity; the same code and
  tests cover MARINE and DEFENDER mechs.
- `MORALE_BROKEN` disables the MISSION goal so `MechSurviveContact` wins.
- Rescue pickup mechs retain their authored perimeter mission.
- Mixed-role mech squads preserve doctrine within the shared assault step: LR
  Support delegates to overwatch and Armored Support delegates to backstop.
- Squad dumps expose each member's `mechVariant` and `mechRole` as ordinary
  contract fields, with no schema-version ceremony.

## Verification

- `DefenderRosterMechCompositionTest` pins every risk/mission composition,
  the heavy-armor gate, count/list agreement, and profile doctrine defaults.
- `AssaultAssignedObjectiveTest` pins marine assignment movement, defender
  local-contact movement, morale yield, and rescue precedence.
- Focused mech tests passed.
- Full `gradlew.bat build` passed before integration.

## Still out of scope

- Infantry following behind the mech or treating it as soft cover (Story E).
- `RECON`, Needle, target painting, and broad dynamic role reassignment.
- Player mech ownership, selection, salvage, or refit UI.
- Final chassis balance tuning; this slice adopts the existing family seeds.
