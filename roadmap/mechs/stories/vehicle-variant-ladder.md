# Vehicle variant ladder

Status: PLANNED
Written: 2026-10-16

Direction: `heavy-equipment-progression.md` (Vehicles: variants, not fittings).
Models: `mechs-nouns.md` (bay, vehicles on gantries, patterns), `convoy-nouns.md`
(vehicles). Supply and scout roles stay with `vehicle-variants.md`.

## Outcome

A vehicle line has a short chain of fixed variants the company learns one card
at a time: technical; heavy APC → up-armored APC → fire-support APC; breakthrough
tank → heavy-gun tank or dozer breacher. Upgrading is fabricating the better
variant, never fitting the old one.

## Scope

- New `VehicleType` variants with distinct stats, turret and lift class:
  up-armored APC (more armor, slower, Medium lift), fire-support APC (a mortar or
  autocannon turret, no seats), heavy-gun tank and dozer breacher (Heavy lift).
- Each variant is a `VEHICLE` pattern at an authored tier (Advanced or Prestige),
  offered through the existing pattern-card channels; faction weights for a
  faction's own variant.
- Fabrication bills scale with the variant; the Mech Lab's read-only vehicle view
  lists them.
- Art: each variant needs a hull (or a decal/turret swap on an existing one)
  before it ships; a variant without art is not offerable, as a loadout card
  without presentation is not.

## Constraints

- No vehicle fitting doll, sockets or heat. The optional single overhaul kit is a
  later story, if at all.
- A variant earns its place by a role or trade-off, never by being strictly
  better than the one before at the same lift.

## Acceptance

- Unit: patterns, tiers, offerability gated on art, fabrication bills.
- A scene with the fire-support APC shelling a position the troop APC unloads
  beside, frames at 1920x1080 / 100%.
