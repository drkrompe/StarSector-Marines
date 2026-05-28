# Campaign infrastructure — buildings

> The mitigation side of the scale-inefficiency curve from
> [`../economy.md`](../economy.md). Buildings the player (and houses)
> invest in to modulate garrison default rates and per-house power.
> Reads the data model in [`../mechanics.md`](../mechanics.md); per
> [`../architecture.md`](../architecture.md) this is most likely a
> passive modifier table read by multiple Systems rather than a System
> of its own.

## Concept

Per-planet and per-region buildings with a build cost, a monthly
maintenance sink, and a defined effect on the campaign data model.
Two effect families seeded so far:

- **Defensive infrastructure** — reduces the Garrison-contract default
  roll on the protected market (the random monthly default chance from
  [`../contracts/overview.md`](../contracts/overview.md) §"Default /
  breach mechanics"). Makes Stationing contracts at a built-up market
  safer to hold.
- **Intel infrastructure** — surfaces hidden pretenders / displaced
  claims sooner (the hidden-heir layer in
  [`../mechanics.md`](../mechanics.md#hidden-heirs-and-displaced-claims)).
  Turns information asymmetry into a buildable advantage.

Both feed `housePower` and the default-rate math, so the natural shape
is a modifier table keyed by market/region that the relevant Systems
read on their daily tick.

## To specify

- Build cost + monthly maintenance per building, and how maintenance
  participates in the sink ledger from [`../economy.md`](../economy.md).
- Exact mitigation magnitudes (how much a defensive tier shaves off the
  default roll; how much an intel tier accelerates pretender reveal).
- Stacking rules — do tiers stack, cap, or replace?
- Who can build — player-only, or do houses build infra that the
  player's missions can target / destroy?

## Related

- [`../economy.md`](../economy.md) — scale inefficiency this mitigates;
  the maintenance sink.
- [`../mechanics.md`](../mechanics.md) — `housePower`, default-rate math,
  hidden-pretender layer.
- [`../contracts/overview.md`](../contracts/overview.md) — Garrison
  default mechanics infra modulates.
- [`../architecture.md`](../architecture.md) — passive-modifier-table vs
  System decision.
