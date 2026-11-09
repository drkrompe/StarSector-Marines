# Ruins vault prize

Status: PLANNED — slice 1 (the draw) built, built on the recommended options
while the owner's picks `ruins-vault-prize`, `ruins-prize-hint` and
`ruins-prize-forced` are open.

Written: 2026-11-09

Read `ruins-expedition.md` (The vault and its prize, Works, Briefing) first,
then `ruins-pressure.md` and `loot-nouns.md`.

## Goal

The owner: "vault theming around what the pickup will be as a hint. Example
industrial upgrades for planets or gamma/beta/alpha cores". Each vault holds one
prize beside vanilla's haul, and the site is built round it, so a player reads
the ruins and guesses what is inside before committing the company.

## Slices

1. **The draw.** *Built* (`RuinsVaultPrizes`, `RuinsVaultPrize`,
   `RuinsVaultTheme`). It is a pure function of the sector seed, the market,
   the grade and the occupant, so a site holds the same prize every time:
   - Family weights by grade, and the occupant's lean.
   - A core's tier by grade; a colony item by vanilla's `rare_tech` weights.
   - The stake, and the core hint (true three in four, else one step off).

   Unit-tested in `RuinsVaultPrizesTest`.
2. **The tell in the briefing.** The survey adds the theme's tell
   (`RuinsVaultTheme.tellKey`) and, for a core, the hinted tier, worded in the
   occupant's instruments. This adds the strings.
3. **The prize at the vault.** The won expedition's settlement adds the prize
   beside the vault store:
   - A colony item as vanilla's special item.
   - A core as its commodity.
   - Tech as a tech cache from the site's drop groups.
   - Goods as a larger bundle.

   Whether the vault was powered decides whether a core or item breaks into
   tech caches (pick `ruins-prize-forced`). Unit-test the settlement.
4. **Stakes read by pressure and guardians.** `ruins-pressure.md`'s reserve and
   stir rate, and the vault warden's tier, read `RuinsVaultPrize.stakes`. That
   is the pressure and mini-boss sessions' work; this slice only publishes the
   stake on the mission.
5. **Themed ground and works.** The ruins ground lays the theme's interior set
   and the works' twist (heat valves, magnet gates, coolant junctions), one
   theme at a time, through picks with look shots. That is mapgen and art.

## Constraints

- The haul stays vanilla's (Law 4). The prize is an extra and comes out only on
  a win.
- The tell never lies about the family.
- Nothing about the prize is stored; the site's finds memory covers it once
  taken (Law 6).

## Done

The owner has read a site's tell and found the prize it promised. Fold the
model into `ruins-expedition.md`, add a row to `shipped.md`, and delete this
story.
