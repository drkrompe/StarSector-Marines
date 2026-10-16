# Player vehicle bay

Status: PLANNED — depends on `mech-pattern-cards.md` (pattern learning) and
`heavy-lift-fittings.md` (lift classes).
Written: 2026-10-16

Direction: `heavy-equipment-progression.md`. Model: `convoy-nouns.md`,
`vehicle-as-commandable-unit.md`; closes the `v0.1-alpha.md` "Vehicle lance
company" gap.

## Outcome

The company owns vehicles. A campaign vehicle bay holds named vehicles of learned
patterns (heavy APC first, breakthrough tank last), fabricated from materials like
a mech chassis. Armour Support drops the bay's active vehicle instead of the
hardcoded `HEAVY_APC`, and a vehicle lost in battle is lost to the company.

## Scope

- `CampaignVehicle` (id, callsign, `VehicleType`) and a vehicle bay on the roster
  with an active lance, persisted the way `MechBay` is.
- Vehicle patterns through the pattern-card registry; fabrication bills in the
  existing material currency. Tank pattern Prestige, APC Common.
- `VehicleSupportPayload` takes the frozen vehicle list; the lift rule decides
  which can be dropped.
- A minimal bay surface (list, callsign, active toggle, fabricate) beside the
  Mech Lab; no fitting doll yet.
- Casualty writeback: a destroyed vehicle leaves the bay.

## Constraints

- A vehicle never uses a mech chassis, socket, heat or Mech Lab authority.
- Whether the 2-8 lance cap is per roster or total stays the open
  `v0.1-alpha.md` question; do not decide it here silently.

## Acceptance

- Unit: bay persistence and migration; payload freeze; loss writeback.
- A scene dropping a company APC and a tank onto open ground and ordering them,
  frames at 1920x1080 / 100%.
