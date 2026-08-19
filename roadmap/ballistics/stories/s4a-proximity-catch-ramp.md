# S4a — proximity catch ramp

> Reduce probabilistic obstruction and friendly-fire catches near the muzzle,
> then smoothly restore their full authored chance downrange. A unit firing
> from immediately behind cover should reliably clear its own position; cover
> near the target or across the field should retain its full value.

Parent design: [`../overview.md`](../overview.md) §4–6.
Depends on shipped S4 (`../complete/s4-direct-fire-unification.md`).

## Why

The resolver currently has one close-range exception: friendly unit contacts
inside two cells are skipped outright. Immediately beyond that boundary, a
friendly jumps to the full 35% incidental graze chance. Probabilistic doodad
and directional edge-cover catches have no muzzle-distance relief at all.

That makes firing from behind a nearby crate or low barricade less reliable
than it should be, and a squadmate just beyond the hard clearance radius is as
likely to catch a round as one standing halfway across the battlefield.

## Shared distance ramp

Use the distance the round has traveled from its source when the event occurs:

- zero catch scale through **2 cells**;
- smooth cubic rise from 2 to **8 cells**;
- full authored chance at 8 cells and beyond.

For the transition, normalize `x = clamp((distance - 2) / 6, 0, 1)` and use
smoothstep `x²(3 - 2x)`. This avoids a new discontinuity at either boundary.
The tuning lives as named constants plus one package-visible pure helper on
`BallisticResolver` so tests pin the curve directly.

Static doodad events already store contact time, and unit/edge-cover events
use the time-domain contact solve. In both cases `distance = eventTime ×
roundVelocity`, so moving friendlies are attenuated at their predicted contact
distance rather than their fire-tick position.

## Where the ramp applies

- **Doodad crossing:** `base block chance × proximity scale` after the existing
  vertical-silhouette gate.
- **Directional edge-cover clip:** `base block chance × proximity scale` after
  its existing catch-height gate.
- **Friendly incidental unit contact:** `35% × incoming-accuracy multiplier ×
  proximity scale`.

Zero-scaled contacts consume no random roll. This preserves deterministic event
queues and makes the two-cell muzzle zone a true safe region for both friendly
bodies and probabilistic cover.

## Deliberately unchanged

- Structural walls remain full-height hard stops at every distance.
- Enemy incidental contacts retain the full 35% graze chance; close enemies do
  not receive friendly-fire protection.
- The locked target's committed accuracy outcome is unchanged.
- Base cover chances remain 15% / 30% / 45% once the ramp reaches full value.
- Friendly-fire damage remains 0.5× when a friendly contact succeeds.
- Height gates, event ordering, lead, spread, and high/low/wide trajectories do
  not change.

## Tests

- The pure ramp is 0 at/below 2, smoothstep values inside the interval, and 1
  at/after 8 cells.
- A nearby doodad does not consume a roll or block; the same doodad level at
  full distance uses its unchanged base chance.
- A transition-distance doodad and directional cover clip use the scaled
  probability boundary.
- A nearby friendly above the old hard boundary has a reduced graze chance,
  while a distant friendly retains the full chance.
- A nearby enemy still uses the full incidental chance.
- Structural walls remain absolute.
- Full suite stays green.

## Non-goals

- Per-weapon, skill, stance, or faction-specific clearance curves.
- Scaling explosion splash or friendly-fire damage after a contact.
- Changing the visual trajectory to physically rise above nearby cover; the
  existing target-plane path remains authoritative, while the probability
  models the shooter's ability to clear gaps and their own firing position.
