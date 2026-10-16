# Vehicles in the mech bay

Status: PLANNED — after `mech-pattern-cards.md`; lift admission from
`heavy-lift-fittings.md`.
Written: 2026-10-16
Updated: 2026-10-16 — vehicles share the mech bay's gantries rather than a separate vehicle bay.

Direction: `heavy-equipment-progression.md`. Models: `mechs-nouns.md` (bay,
gantry, Mech Lab), `convoy-nouns.md` and `vehicle-as-commandable-unit.md`
(vehicles). Closes the `v0.1-alpha.md` "Vehicle lance company" gap.

## Outcome

A gantry in the mech bay holds either a mech or a vehicle. The company learns
vehicle patterns the way it learns mech patterns, fabricates a heavy APC or a
tank onto a gantry from materials, and brings it down through the same drop
choice as its mechs (`armor-drop-selection.md`). A vehicle lost in battle is lost
to the company.

## Scope

- A `VEHICLE` pattern kind with weight classes: heavy APC light (Common), escort
  APC and technical medium (Advanced), breakthrough tank heavy (Prestige).
- The bay's gantry entry generalises to "a mech or a vehicle"; a vehicle keeps
  its `VehicleType`, callsign and identity. Save migration leaves every existing
  gantry a mech.
- Fabrication bills in the existing material currency; the Mech Lab shows a
  vehicle on its gantry with read-only stats (no fitting doll yet).
- Armour Support stops dropping a hardcoded `HEAVY_APC`; vehicles come down
  through the shared drop choice.
- Casualty writeback: a destroyed vehicle leaves its gantry.

## Constraints

- Sharing the bay does not make a vehicle a mech: no mech chassis, sockets, heat
  or weapon-component authority. The gantry is the shared thing, nothing else.
- Turret or hardware swaps wait for `mech-lab-data-authored-dolls.md`.

## Acceptance

- Unit: mixed gantry persistence and migration; vehicle patterns gate
  fabrication; loss writeback.
- A scene dropping a company APC and a Hound from one bay, frames at 1920x1080 /
  100%.
