# Story: truck vs. infantry interaction

Status: PARTIAL — repulsion, ramming and hull contact shipped; navigation occupancy, pinned crushing and predictive avoidance remain.

Written: 2026-05-28

Updated: 2026-10-23 — ram damage against the other side and hull-on-hull contact shipped (`convoy-nouns.md`).

Read `convoy-nouns.md` first. A hull is still not a grid occupant, but it now
repels bodies physically (contact and bow wave; see the noun doc), so marines no
longer stand inside trucks and a moving truck shoves people aside. Thunder Run's
column driver also slows for friendly infantry in the road. What remains:

- **Planning.** Infantry still path through hulls and are pushed back out each
  tick; a parked hull on a defend post produces visible churn. A hull's
  footprint as a soft path cost or GOAP world-state input is the next step.
- **Squash.** A hostile body on a moving bow is rammed for impact damage by mass
  and closing speed (`HullRam`); a body pinned against a wall is still simply
  passed rather than crushed.
- **Yielding for every vehicle.** Slowing for friendlies is a Thunder Run
  driver behaviour, not yet a chassis-level rule for delivery and ordered
  vehicles.

## Options

- **Marines dodge trucks.** Predictive avoidance — marines whose GOAP path
  crosses a truck's projected position re-plan. Reads as "civilians
  scattering."
- **Trucks squash marines.** A truck driving over a marine cell applies
  impact damage. Reads as a serious threat, but ugly when the truck
  flattens its own deboarded militia.
- **Hybrid.** Trucks slow (or honk) when marines are in their path;
  marines yield to friendly trucks, dodge enemy ones.

Hybrid is the right answer but most expensive. Likely start with "marines
dodge" and add the squash variant later.

## Notes

- The deboard loadout already routes through the per-faction roster described by
  `reinforcement-nouns.md`, so squashed/ejected militia would draw from the same
  lookup.
- Predictive avoidance leans on the GOAP re-plan triggers described by the AI
  feature — a truck's projected
  occupancy is a new world-state input rather than a bespoke dodge
  behavior.
