# Ruins expedition fork

Status: IN PROGRESS — slice 1 built and unit-tested; development builds only until slice 3.

Written: 2026-11-09

Updated: 2026-11-09 — slice 1 (fork and preference) built.

Read `ruins-expedition.md` in full first, then `raid-resolution.md`, whose
fork this mirrors.

## Goal

When the player selects Explore the ruins, offer the company or the salvage
team. A won expedition commits through vanilla's own ruins salvage, and a lost
one leaves the site unexplored. The first slices fight on an existing map so the
fork and commit path can ship before ruins mapgen exists.

## Slices

1. **Fork and preference.** A `rules.csv` row on `DialogOptionSelected`,
   `$option == salExplore`, the market has ruins and is not a gas giant, scored
   just ahead of vanilla's. It shows Send the company / Send a salvage team,
   each with an "always" variant, plus an "Ask again" planet-menu row when the
   preference is set, as the raid fork has. The standard path re-fires
   `salExplore` with a one-shot bypass. Unit-test the route decision the way
   the raid fork's route is tested.

   *Built.* `MarineRuinsCMD` and the `smRuins*` rows. Until the expedition
   battle exists (`MarineRuinsCMD.EXPEDITION_BUILT`), the fork is offered only
   when `DevConfig.DEV_MODE` is on (`runStarsector` passes it), and choosing the
   company says the battle is not built yet and returns to the choice. A
   player's install sees Explore the ruins unchanged. Slice 3 removes the gate.
   The fork's Back rebuilds the planet menu itself, since vanilla's salvage
   flow has no never-mind to reuse. `playBattle`'s planet menu plays it on
   Ossuary, a stand-in ruins world (`build-and-run.md`), with
   `-Dmarines.dev=true`.
2. **Commit path.** Results records won or lost once. On a win, closing the
   panel re-fires `salExplore` through the bypass, so `SalvageGenFromSeed` and
   `PostSalvagePerform` run as vanilla's and the site is marked explored. A
   loss or withdrawal returns to the planet menu with the site unexplored.
   Backing out of the briefing returns to the fork.
3. **Stand-in battle.** The Ruins Expedition mission on an existing map against
   scavengers sized by grade: `ruins-expedition-battle.md`.
4. **Ruins ground.** The ruined-settlement delve for mapgen
   (`SettlementLink.NONE`): `ruins-ground.md`.
5. **Occupants.** The seeded occupant per site and its briefing line:
   `ruins-occupants.md`, with derelict security in
   `derelict-security-network.md`.
6. **Live pass.** In the game, on each grade: standard path unchanged; won
   expedition shows vanilla's loot and marks the site explored; lost one leaves
   it unexplored; a hostile fleet nearby still disables the option; derelict
   ship salvage never shows the fork; preference and "Ask again" work.

## Constraints

- Slices 1 and 2 touch only the campaign layer, with no battle rule changes.
  `playBattle`'s planet menu can play the rules against the real `rules.csv`.
- No file access or reflection in shipped code (`verifyScriptSandbox`).
- The salvage spec, seed and drop groups stay vanilla's. The mod never rolls
  the vault's haul itself.

## Done

Every live-pass item passes. Then fold this story into `ruins-expedition.md`
(Status ACTIVE), add a row to `shipped.md`, and delete it.
