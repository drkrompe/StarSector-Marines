# Ruins vault prize

Status: IN PROGRESS. Slices 1 and 2 are built, and so are the rules for slices 3
and 6. Owner picks `ruins-find-tiers` a, `ruins-prize-forced` a and
`ruins-theme-defences` a are decided; `ruins-vault-prize` and
`ruins-prize-hint` are open, and the work is built on their recommended options.

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
2. **The tell in the briefing.** *Built.* The survey ends on the theme's tell
   (`RuinsVaultPrizes.tellKeys`, one string per theme). For a core it adds the
   hinted tier. `MarineRuinsCMD` passes the sector seed, and `ruins-briefing`
   shows one tell for each family. Still owed: wording the tell in the
   occupant's own instruments.
3. **The prize at the vault.** *Rules built* (`RuinsVaultPayout`,
   `RuinsTechCache`, `RuinsFindTier`).
   - Powered at the breach, a core or colony item comes out whole. Forced, it
     breaks into stake + 1 tech caches.
   - Goods pay three supply bundles; tech pays two tech caches.
   - Still owed: the wiring. The battle records whether the vault was powered
     at the breach (`RuinsVaultObjective.vaultPowered` when `breached()` turns
     true). The won expedition's settlement then pays:
     - A colony item as vanilla's special item.
     - A core as its commodity.
     - Each tech cache by rolling its drop group through vanilla's salvage
       generator.
   - Tech caches in sealed stores, guardian rooms and the vault store's deepest
     layer need the same settlement. That opens `loot-nouns.md`'s
     equipment-cache gate for ruins.
4. **Stakes read by pressure and guardians.** `ruins-pressure.md`'s reserve and
   stir rate, and the vault warden's tier, read `RuinsVaultPrize.stakes`. That
   is the pressure and mini-boss sessions' work; this slice only publishes the
   stake on the mission.
5. **Themed ground and works.** The ruins ground lays the theme's interior set
   and the works' twist (heat valves, magnet gates, coolant junctions), one
   theme at a time, through picks with look shots. That is mapgen and art.

6. **The old colony's batteries.** *Rules built* (`RuinsBatteries`: the count
   by grade on a core or item site, and dark, awake or cut by the hall and their
   branch). Still owed, all through the parent and the owning sessions:
   - Batteries on the approach, from mapgen.
   - A neutral gun that fires on anyone.
   - A consumer on the batteries' branch in `PowerGraph`.
   - Their fit with `ruins-pressure.md` and the mini-boss design.

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
