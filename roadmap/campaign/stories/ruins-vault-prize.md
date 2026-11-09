# Ruins vault prize

Status: IN PROGRESS. Every owner pick is decided (`ruins-vault-prize` a,
`ruins-prize-hint` a, `ruins-find-tiers` a, `ruins-prize-forced` a,
`ruins-theme-defences` a). These are built and folded into
`ruins-expedition.md` (The vault and its prize):
- The draw and the tell in the briefing.
- The payout and find-tier rules.
- The batteries.
- The prize carried on the mission and fixture, its stake read by the pressure
  and named on the battle for the guardians.

The slices below are what is left.

Written: 2026-11-09

Read `ruins-expedition.md` (The vault and its prize, Works, Briefing) first,
then `ruins-pressure.md` and `loot-nouns.md`.

## Goal

The owner: "vault theming around what the pickup will be as a hint. Example
industrial upgrades for planets or gamma/beta/alpha cores". Each vault holds one
prize beside vanilla's haul, and the site is built round it, so a player reads
the ruins and guesses what is inside before committing the company.

## Slices

1. **The prize at the vault.** The payout rules are built (`RuinsVaultPayout`,
   `RuinsTechCache`, `RuinsFindTier`); the wiring is owed.
   - The battle records whether the vault was powered at the breach
     (`RuinsVaultObjective.vaultPowered` when `breached()` turns true).
   - The won expedition's settlement then pays:
     - A colony item as vanilla's special item.
     - A core as its commodity.
     - Each tech cache by rolling its drop group through vanilla's salvage
       generator.
   - Tech caches in sealed stores, guardian rooms and the vault store's deepest
     layer need the same settlement. That opens `loot-nouns.md`'s
     equipment-cache gate for ruins.

   Unit-test the settlement.
2. **The batteries, finished.**
   - A side hostile to everyone, so the guns fire on the occupant too.
   - A powered-down look, so dark and awake read apart.
3. **The tell in the occupant's voice.** Each tell is one line today; word it
   through the occupant's own instruments (`RuinsSurvey`).
4. **Themed ground and works.** The ruins ground lays the theme's interior set
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
