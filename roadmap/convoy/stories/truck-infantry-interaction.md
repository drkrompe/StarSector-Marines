# Story: truck vs. infantry interaction

Status: PLANNED — moving-vehicle occupancy and collision authority are not yet defined.

Written: 2026-05-28

Updated: 2026-08-23 — reframed around the current world-actor/grid-combatant boundary.

Read `convoy-nouns.md` first. Today the vehicle is a continuous world actor the
infantry grid does not treat as an occupant: marines and trucks can pass through
one another. This story makes that interaction explicit.

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
  `convoy-nouns.md`, so squashed/ejected militia would draw from the same
  lookup. See `faction-roster.md`.
- Predictive avoidance leans on the GOAP re-plan triggers described by the AI
  feature — a truck's projected
  occupancy is a new world-state input rather than a bespoke dodge
  behavior.
