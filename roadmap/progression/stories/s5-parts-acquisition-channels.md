# S5 — Parts acquisition channels

> Today there is exactly one way to get fabrication feedstock: win. That is
> not an economy, it is a scoreboard.

**Status:** not started. No hard dependencies; pairs with
[S6](s6-unlock-ladder-expansion.md) — income without a ladder and a ladder
without income are each half a feature.

## Problem

`MarineArmory.fabricationMaterials` has a single source:
`recordVictory(materialReward, highRisk)` at LOW 2 / MEDIUM 4 / HIGH 7 per
won mission. There is no market conversion, no battlefield loot, no
breakdown of recovered gear, and no reward channel. The player cannot spend
money on their marines, cannot be rewarded with materiel by a patron, and
gains nothing from clearing a map beyond the objective.

There is also only **one** currency, which means the buyable floor and the
chase ceiling are the same resource. That is the structural reason
Masterwork can never be made properly rare.

## Goal

Five income channels, and a two-currency split that makes the top of the
ladder chase-worthy.

## Design commitment: two currencies

| Currency | Source | Gates |
| --- | --- | --- |
| **Common parts** | Market conversion, victories, routine loot, gear breakdown | Surplus / Service / Milspec printing, tier 1-3 armor |
| **Advanced components** | Battlefield loot and special-mission rewards **only** | Masterwork printing, tier 4 armor, high-end recipe unlocks |

Advanced components are **never purchasable**. Per the track commitment,
the chase requires an operation, not a shopping trip. This also means a
rich player cannot buy their way past the progression curve, which matters
given how much money a mid-campaign Starsector fleet generates.

## Slice 1 — Market conversion

Convert a vanilla cargo commodity into common parts, from the Armory
screen.

- Candidate feedstock: `metals` and `heavy_machinery` are the natural
  reads. `supplies` is tempting but is already the fleet's lifeblood and
  competing with it creates a miserable trade.
- Conversion consumes real cargo from the player fleet and is capacity
  aware, matching the pattern the shipped cargo-backed enlistment route
  already established.
- Rate should be **deliberately unflattering** — the market is a floor that
  keeps a company from being hard-stuck, not a ladder
  ([[feedback_hard_failure_preference]]).

Optional and worth considering: conversion efficiency scaled by something
diegetic in the player's fleet, in the spirit of how
[`../../command-powers/`](../../command-powers/overview.md) sources powers
from committed ships. A fabrication-capable hull or hullmod improving the
rate would make fleet composition matter here too.

## Slice 2 — Battlefield loot

Extend the shipped [`../../campaign/loot/`](../../campaign/loot/overview.md)
manifest with parts entries rather than building a parallel drop system.

- Common parts scale with mission risk and with what was actually cleared —
  not just with the objective.
- **Advanced components** drop rarely, weighted toward high-risk operations
  and hardened sites (military bases, armories, C2). This is the primary
  advanced-component faucet.
- Existing salvage entitlement and recovery-modifier machinery
  (`LootRecoveryModifiers`, `Trait.SALVAGE_EXPERT`) should apply, so the
  one already-wired captain trait gets more to do.

## Slice 3 — Gear breakdown

Recovered enemy weapons and armor break down into common parts.

- Rewards clearing a map rather than rushing the objective — a direct
  answer to "why fight the rest of the base".
- Yield keys off what the defenders were actually carrying, which makes the
  risk-scaled defender rosters legible as loot value.
- Natural interaction with S3 telemetry: the marines who did the fighting
  produced the salvage.

## Slice 4 — Reward channels

- **Contract rewards** — patrons pay in materiel as well as cash. This is
  also a lever for patron archetype flavor: a Corporate house pays in
  fabrication feedstock, a Feudal one in hand-made gear.
- **Special/story missions** — the explicit advanced-component and recipe
  reward path. A one-shot operation that ends with a crate of masterwork
  components is exactly the kind of discoverable, participation-driven
  reward the campaign narrative track is already built around
  ([[feedback_patron_narrative_discoverable]]).
- **Victories** — the existing channel, retained but rebalanced downward
  now that it is no longer the only one.

## Out of scope

- What the currencies unlock — [S6](s6-unlock-ladder-expansion.md).
- Selling parts back, or a parts market between players/factions.
- Wages, upkeep, or personnel costs. The company money loop lives in
  [`../../campaign/economy.md`](../../campaign/economy.md) and this story
  must not fork it.

## Acceptance

- All five channels produce parts in a real campaign, verified end to end.
- Advanced components are unobtainable by purchase through any path,
  including conversion chains.
- Cargo mutation is exact and capacity-aware; no path creates or destroys
  vanilla commodities off-book.
- Deterministic and replay-safe: recomputing a mission outcome does not
  double-pay.
- Legacy saves migrate — existing `fabricationMaterials` becomes common
  parts, advanced components start at zero.
- The total income curve is stated explicitly in the story record: roughly
  how many parts a player should hold at mission 5, 15, and 30. Without a
  named target, S6's ladder cannot be priced.

## Open questions

- Is the market conversion a **cargo action** on the Armory screen, or a
  proper **submarket** on friendly planets? Submarket is more diegetic and
  more Starsector-native; the screen action is far less work. Leaning:
  screen action first, submarket if it earns it.
- Should advanced components be a **cargo item** the player can see, carry,
  and lose with a ship — rather than an abstract counter? Leaning yes; a
  physical crate that can be lost to a raid is much more in keeping with
  the rest of the design, and makes the chase legible.
