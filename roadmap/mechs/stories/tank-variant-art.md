# Tank variant art

Status: PLANNED — waiting on art; the variants' stats, patterns and bills exist.
Written: 2026-10-16

Models: `mechs-nouns.md` (vehicle variants, art gate), `convoy-nouns.md`
(vehicle presentation). Direction: `heavy-equipment-progression.md`.

## Outcome

The heavy-gun tank and the dozer breacher become offerable pattern cards. Both
already exist as `VehicleType` variants with stats, lift class, turret, a Prestige
pattern, faction weight and a fabrication bill; `presentationDeclared` is false
for each, so no channel draws them.

## Scope

- **Heavy-gun tank.** A turret sheet frame (live and wreck) for one long heavy gun
  on the breakthrough hull's roof bearing, registered like the twin breach cannon
  (`art-source/vehicles/tank/`). The hull may stay the breakthrough tank's if a
  turret swap alone reads as a different tank; otherwise a hull with its own
  markings. Muzzle registration for `structure.tank-heavy-gun`.
- **Dozer breacher.** A breakthrough hull with a dozer blade (live and wreck) and a
  small pintle MG turret frame in place of the twin cannon. The blade is the
  variant's reason to exist, so it needs a breaching behaviour — pushing through
  rubble, barricades or a wall section — before it is offered; until then it is a
  slower, cannonless tank and would break the trade rule in practice.
- Flip `presentationDeclared` for each once its art (and, for the dozer, the
  breach) exists.

## Acceptance

- Unit: both variants offerable once declared; registered sprite frames.
- A snapshot of each hull live and wrecked at 1920x1080 / 100%; for the dozer, a
  scene in which the blade opens a route a troop APC then drives through.

Generating the art goes through the `codex-imagegen` skill and the geometry-first
workflow (`art-source/geometry-first-imagegen.md`); ask the owner first.
