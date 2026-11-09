# Ruins expedition battle

Status: IN PROGRESS — slices 1 to 3, 6 and 7 built (objective, disclosure, delve
commander, deeper caches, briefing), and the family plays
in battle on interim parts. Slice 3 of
`ruins-expedition-fork.md`.

Written: 2026-11-09

Updated: 2026-11-09 — the owner made the expedition a mission family of its own
(pick `ruins-battle-shape` c), with its own commander pair.

Read `ruins-expedition.md` in full first (Shape of the battle, Occupants,
Briefing), then `mission-command-nouns.md` (Command duel, Perspective and
disclosure), `extraction-command.md` (what the family borrows) and
`loot-nouns.md` (Optional field caches).

## Goal

The fork's "send the company" plays a real expedition: land, delve to the vault,
hold it through the breach time, get the haul to a lift point. It plays on an
existing map until `ruins-ground.md` lands, against a scavenger crew sized by
the ruins grade. The fork story's commit path reads its won or lost. When this
story's battle plays, it lifts the fork's development-only gate
(`MarineRuinsCMD.EXPEDITION_BUILT`), as the fork story's slice 1 says.

## Slices

1. **The family's objective.** The Ruins family's own objective domain: the
   vault as its one source, the grade's breach time (and the powered time, read
   from a flag the works story will set), the escorted haul with its leash,
   loss and abandonment, lift points (the landing area and any held landing
   court), and the terminal outcomes. It publishes the shared payload
   projection's shape, so the rail and map show the haul like any package.
   Reuse Extraction's source-hold and leash rules as code where they are
   separable; never fork them. It has no employer, no allies and no employer
   support, and is never offered as a contract. Unit-test the grade mapping,
   the lift-point rule and each terminal on the objective's own inputs.

   *Built.* `RuinsVaultObjective` publishes the payload projection
   (`ExtractionPayloadObjective`, kind PACKAGE) with the vault as its source
   and the bound lift point as its egress. Extraction's hold and escort rules
   now live in shared `UncontestedHold`, `EscortedRoute` and `EscortPresence`,
   which `ExtractionObjective` and the vault both use; Extraction's behaviour
   is unchanged. A landing court becomes a lift point once the company holds
   it for `COURT_HOLD_SECONDS`; until the haul moves it is bound for the
   available lift point with the shortest route, then keeps it. The works set
   the powered breach through `setVaultPowered`. `RuinsDelve` maps the grade
   to layers, breach times and caches. A wiped-out or withdrawn company loses
   through the battle's ordinary defeat.

   *The family plays in battle.* `MissionType.RUINS` carries the grade on the
   mission (`Mission.ruinsGrade`); `RuinsBattleFixture` (kind RUINS) builds it
   through `BattleSetup.createRuins`, and `MissionLaunch` launches it. Until
   slices 2 to 5 replace them, the vault and its route are Extraction's source
   pick (`ExtractionPayloadLayout`), the landing area is the one lift point,
   Extraction's commanders play both sides, and the rail shows the package
   wording. The site is abandoned: no civilians, staffed buildings, ground
   crew or reinforcements.
2. **Disclosure.** The family's mission disclosure: the company sees the vault,
   its lift points, the haul's state and escort; the occupant's side sees what
   its own perspective allows; the occupant's layout is never in the company's
   frame.

   *Built.* `RuinsObjectiveDisclosure` freezes `RuinsObjectiveFacts` per side.
   The company sees the vault, the approach from the landing area, every lift
   point with its route length and whether it is open, the haul, its escort,
   and the field caches its own sight has found. The occupant sees its vault
   and whether the company has reached it, as Extraction's defenders see their
   source. The objective keeps the payload kind PACKAGE, so the generic
   defenders still read it until slice 4. The rail and map have the family's
   own card (`MissionObjectiveProjection.vaultHaul`): Reach the vault,
   Breaching with the grade's breach time (and the powered wording), Escorting
   haul to the named lift point, Boarding. Every lift point is a marker, and an
   unheld court reads "hold to open". The steps read Breach > Escort > Board.
3. **The delve commander.** The company's headless strategy: a layer at a time
   with a lead element, cache and works detours when a layer is quiet, the
   breach hold, then the haul screened to the nearest held lift point. Played
   battles yield to the player as every mission does.

   *Built.* `RuinsDelveCommand` replaces `ExtractionCommand` on the company's
   side for `MissionType.RUINS`. It cuts the approach into the grade's layers.
   A lead element attack-moves to the next layer's end while the body holds the
   last one, and the layer clears once the lead stands there out of contact.
   The lead moves on only when the body has settled behind it, and a lead in
   contact draws the body up. On a quiet layer, one body squad detours to a
   found cache within 24 route cells, and the lead waits for it; contact calls
   it back. At the vault one squad holds the breach and the rest screen it. A
   landing court whose route is shorter than the bound lift point's gets a
   holder while three squads or more remain. The haul then moves in
   Extraction's escort formation, now shared as `PayloadEscortFormation`. Squads under
   the player's order are left alone. Works detours wait for `ruins-works.md`,
   which has no works yet. A headless `battleProbe` on a Widespread fixture
   shows the delve leading and supporting, then a two-squad company lost at
   the first layer: tuning belongs to slices 4 and 8. Shared fix:
   `EscortPresence` crashed on a Marine emplacement (it has no movement); it
   now skips one.
4. **The crew commander.** The scavengers' strategy: posted squads at the camp
   and the dig, a patrol pool and the alarm, borrowed from the generic Extraction
   defenders, answering for the dig first and the stash second. The pirate
   ground roster, strength by grade as a budget this slice tunes. The stash is
   one extra cache at the camp.
5. **Stand-in ground.** The existing map nearest a ruin (decivilized or
   low-density, `SettlementLink.NONE`), the vault at the far end by route
   length, one landing court placed by hand. A ruins map that cannot place a
   reachable vault is a setup error, not a fallback.
6. **Deeper caches.** The grade's cache count (2 to 5) on the existing
   field-cache placement, weighted to the far half of the route.
7. **Briefing.** The assessment, the grade's strength band and the scavengers'
   readings pool (`ruins-expedition.md`, Briefing); the other pools are
   `ruins-occupants.md`'s.

   *Slices 6 and 7 built.* A Ruins battle hands the field-cache placement a
   `SalvagePlacement.Delve`: the grade's `RuinsDelve.fieldCaches` and the vault
   route. Two in three finds (rounded up) go on the route's vault half first,
   the rest anywhere legal, closer spaced than the one-per-resource scatter;
   resources take turns, and scarce ground yields fewer. The briefing is the
   mission's dialogue: the stakes, then the orbital survey (`RuinsSurvey`): the
   assessment with the grade's band (light to severe) read through the
   occupant's instruments, then two or three readings seeded by the market id.
   `RuinsSurvey.poolFor` maps an occupant to its pool; only the scavengers have
   one, and an occupant without one reads as the scavengers its battle still
   fields.
8. **Evidence.** `commanderEvidence -Pmission=ruins` with a fixture per grade on
   the stand-in ground, headless: the haul boards, or the expedition loses by
   one of the family's terminals. That proves the mission works, not that it is
   hard enough; balance is a played battle (`playBattle -Pbattle=<source>`).

## Constraints

- What the family borrows from Extraction is shared code, not a copy. A change
  the family needs in a shared rule is raised against `extraction-command.md`
  first.
- The vault's haul stays vanilla's. The battle reports won or lost and field
  finds, nothing else.
- Do not touch the fork's rules or `MarineRuinsCMD` beyond lifting the gate and
  reading the outcome the commit path already expects.

## Done

All slices pass their tests and evidence, and the owner has played an
expedition on each grade. Fold the family into `ruins-expedition.md` and its row
in `mission-command-nouns.md`, add a row to `shipped.md`, and delete this story.
