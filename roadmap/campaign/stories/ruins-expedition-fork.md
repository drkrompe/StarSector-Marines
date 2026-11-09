# Ruins expedition fork

Status: IN PROGRESS — slices 1 to 5 built and unit-tested; development builds only until the live pass.

Written: 2026-11-09

Updated: 2026-11-09 — the fork asks every time; the Settings page turns it off (no "always" answer).

Read `ruins-expedition.md` in full first, then `raid-resolution.md`, whose
fork this mirrors.

## Goal

When the player selects Explore the ruins, offer the company or the salvage
team. A won expedition commits through vanilla's own ruins salvage, and a lost
one leaves the site unexplored. The first slices fight on an existing map so the
fork and commit path can ship before ruins mapgen exists.

## Slices

1. **Fork and fork setting.** A `rules.csv` row on `DialogOptionSelected`,
   `$option == salExplore`, the market has ruins and is not a gas giant, scored
   just ahead of vanilla's. It shows Send the company / Send a salvage team and
   Go back, chosen every time; the Settings page's "Ask before exploring ruins"
   key turns the fork off, as the raid fork's key does. The standard path re-fires
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

   *Built.* `RuinsExpeditionLedger` and `RuinsExpeditionMissionKey` (the raid
   fork's two-step exactly-once pattern on their own `CampaignState` columns),
   `MissionSource.RUINS_EXPEDITION` recorded in `MissionResolver`, and
   `MarineRuinsCMD` opening the briefing. A won expedition rebuilds the planet
   menu, so vanilla's ruins row seeds its salvage as on arrival, then re-fires
   `salExplore` through the bypass; a pulled-out one commits as a loss does
   for the vault, with its own line. `RuinsExpeditionMissionFactory` builds
   the slice 3 battle, with no contract and no employer boats. Played in
   `playBattle` on Ossuary (with the earlier stand-in battle): backing out
   returns to the fork, a forced win goes through vanilla's salvage to
   explored, and a confirmed retreat leaves Explore the ruins on the menu.
3. **The battle.** The Ruins Expedition mission family against the site's
   occupant: built (`ruins-expedition.md`, The Ruins mission family, The
   entrance and the pull-out point, Pressing on or pulling out). Its deferred
   delve commander is an open question there.
4. **Ruins ground.** The ruined-settlement delve for mapgen
   (`SettlementLink.NONE`): `mapgen-nouns.md`, Ruins ground.
5. **Occupants.** The seeded occupant per site and its briefing line:
   built (`ruins-expedition.md`, Occupants and Briefing), with derelict security in
   `derelict-security-network.md`.
6. **Live pass.** In the game, on each grade: standard path unchanged; won
   expedition shows vanilla's loot and marks the site explored; lost one leaves
   it unexplored; a hostile fleet nearby still disables the option; derelict
   ship salvage never shows the fork; turning "Ask before exploring ruins" off
   sends the salvage team without asking, across save and reload.

   The owner plays an expedition on each grade (the battle's balance is
   judged only here). A pulled-out expedition brings its banked finds home
   and leaves the site unexplored. A second expedition to that site meets the
   same ground without the finds already taken. A won one also brings the
   vault store home. Once this pass is played, `MarineRuinsCMD.EXPEDITION_BUILT`
   lifts the development-only gate.

## Constraints

- Slices 1 and 2 touch only the campaign layer, with no battle rule changes.
  `playBattle`'s planet menu can play the rules against the real `rules.csv`.
- No file access or reflection in shipped code (`verifyScriptSandbox`).
- The salvage spec, seed and drop groups stay vanilla's. The mod never rolls
  the vault's haul itself.

## Done

Every live-pass item passes. Then fold this story into `ruins-expedition.md`
(Status ACTIVE), add a row to `shipped.md`, and delete it.
