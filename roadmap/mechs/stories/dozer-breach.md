# Dozer breach

Status: PLANNED — the dozer breacher is drawn and offered; its blade does nothing yet.
Written: 2026-10-16

Models: `mechs-nouns.md` (vehicle variants, trade rule), `convoy-nouns.md`
(vehicle presentation, tank variants). Direction: `heavy-equipment-progression.md`.

## Outcome

The dozer breacher's blade opens ground: it pushes through rubble, barricades or a
wall section in its path, so the hull earns its place against the breakthrough tank
by making a route other vehicles can use, not only by its plate.

## Scope

- A breach behaviour for `VehicleType.DOZER_BREACHER`: what the blade can clear
  (rubble, barricade doodads, a wall cell under a damage threshold), at what rate,
  and how route proof treats ground the dozer can open.
- The cleared cells become ordinary passable ground for every vehicle and unit.
- No change to the hull or blade art (shipped with the variant).

## Acceptance

- Unit: the blade clears exactly the cells its rule allows, and nothing else.
- A scene in which the blade opens a route a troop APC then drives through,
  rendered at 1920x1080 / 100%, with a control where the APC alone cannot pass.
