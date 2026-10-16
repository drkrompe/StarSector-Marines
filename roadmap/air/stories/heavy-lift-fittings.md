# Heavy lift fittings

Status: PLANNED
Written: 2026-10-16

Direction: `heavy-equipment-progression.md`. Model: `air-nouns.md`,
`company-view-nouns.md` (Boat Deck, fitting), `mechs-nouns.md`.

## Outcome

Machines are lifted by carriers the company actually has. Every payload declares
a lift class (Light, Medium, Heavy) and every carrier a lift rating. A third boat
fitting slot, LIFT, lets bus-class company boats take Light or Medium machines at
the cost of their seats, so lift becomes an upgrade path of its own.

## Scope

- Lift class on `MechVariant` and `VehicleType` (table in the direction doc).
  `relativeMass` stays a manual-drive inertia value; lift class is separate.
- Lift rating on `ShuttleType` (zero for small boats, Heavy for Valkyrie) and a
  `BoatFittingSlot.LIFT` with standard / cradle (Light) / heavy cradle (Medium)
  fittings, each with a material bill and a seat cost. `FittedBoat` freezes it.
- Mech Support and Armour Support stop spawning a synthetic Valkyrie when the
  company has a qualifying carrier: the drop is flown by a committed company boat
  or ship, and its loss is that boat's loss through ordinary settlement. The
  synthetic carrier remains only where a mission or employer supplies the lift.
- Briefing and Mech Lab say when a lance cannot be lifted and by what.

## Constraints

- A cradled boat flies a machine-only sortie; deboarding stays single-kind.
- Fitting never changes turnaround or deboard cadence (`air-nouns.md`).
- Hullmod and Valkyrie entitlements to the powers stay where `PowerCatalog` puts
  them; this story changes who flies, not who may call.

## Acceptance

- Unit: lift admission per class and rating; fitting migration (old boats read
  as standard lift).
- A scene in which a cradled Buffalo delivers a Hound and a Bulwark is refused
  without Heavy lift, with frames at 1920x1080 / 100%.
