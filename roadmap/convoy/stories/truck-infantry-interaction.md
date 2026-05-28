# Story: truck vs. infantry interaction

**Queued.** Today the truck is a fractional-position body the sim doesn't
know about for collision: marines walk through it, trucks drive through
marines. This makes trucks feel real in the battle.

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

- The deboard loadout already routes through the per-faction roster (see
  [`reinforcement-integration`](../complete/reinforcement-integration.md)),
  so squashed/ejected militia would draw from the same lookup. See
  [`../../reinforcement/faction-roster.md`](../../reinforcement/faction-roster.md).
- Predictive avoidance leans on the GOAP re-plan triggers in
  [`../../ai/overview.md`](../../ai/overview.md) — a truck's projected
  occupancy is a new world-state input rather than a bespoke dodge
  behavior.
