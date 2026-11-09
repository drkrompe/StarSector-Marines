# Ruins expedition battle

Status: IN PROGRESS — slices 1, 2, 6 and 7 built (objective, disclosure,
deeper caches, briefing); slice 3 deferred, slice 8 dropped. The family
plays in battle on
interim parts. Slice 3 of
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
existing map until ruins ground lands (`mapgen-nouns.md`, Ruins ground), against a scavenger crew sized by
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

   *Deferred* (owner: planning the company's run is a waste for now). In a
   played battle the player commands the company, and the ground is still
   changing, so `ExtractionCommand` stays the company's commander for
   `MissionType.RUINS`; it is enough for headless evidence. Take this slice up
   only once ruins ground (`mapgen-nouns.md`, Ruins ground) has landed, and only if evidence needs more
   than `ExtractionCommand`. If it is taken up, it reads the layers and the
   vault route from what setup publishes, never open streets: a layer may be
   inside, behind a threshold or seal door.

   *Shared fix kept from this work:* `EscortPresence` crashed on a Marine
   emplacement, which has no movement; it now skips one.
4. **The crew commander.** The scavengers' strategy: posted squads at the camp
   and the dig, a patrol pool and the alarm, borrowed from the generic Extraction
   defenders, answering for the dig first and the stash second. The pirate
   ground roster, strength by grade as a budget this slice tunes. The stash is
   one extra cache at the camp.

   *Built.* `RuinsCrew` sizes the crew from the grade alone: a budget in
   `BattleForceScore` units (12, 20, 30 and 60 from scattered to vast, a Hound
   at vast) spent on its heavy first, then infantry at the grade's share of
   regulars, on the pirate roster whoever owned the world; no emplacements and
   no air. The setup posts a third of the infantry at the dig (the vault) and a
   fifth at the camp, which stands on the vault route 30% out from the landing
   but at least 24 cells and never past halfway; the rest walk patrol from
   points along the route, the heavy with them. `RuinsCrewCommand` replaces
   `ExtractionDefenderCommand` for the family. It borrows the Extraction
   defenders' picture, alarm limit, fresh-contact rule, rally ring and
   garrisonable manning as shared code. It answers for the dig first: one squad
   always walks it, an alarm there takes all three, trouble at the camp alone
   sends two to stand round the camp, and both at once split two and one.
   Trouble means a fresh contact within rifle reach (26 cells) of the place.
   The stash is one Supplies find at the camp (`BattleSimulation.occupantStash`),
   beside the grade's caches. A derelict site keeps its own security instead.
   Played on the ruins ground (`ruins-scavengers-extensive-v1`): both patrols
   stood to at the camp as the company passed, the dig post held the vault
   until the company's lead squads came in force, and the dig alarm rose each
   time the company pressed it. The camp stands on the vault route, not yet in
   a layer's side room the ground publishes. The budgets are untuned against a
   player.
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
8. **Evidence.** *Dropped by the owner (2026-11-09).* No session runs the
   mission headless to judge it, not even whether it ends: the autopilot
   cannot play these maps as a player would, so its outcomes say nothing about
   the mission or its balance. Rules are proven by unit tests where they are
   decided; whether the ground builds is the ground's own evidence
   (`mapgen-nouns.md`, Ruins ground); balance is a played battle. The
   harness built before the ruling is removed; the per-grade scavenger
   fixtures stay for played battles (`playBattle -Pbattle=<fixture>`).

## Constraints

- What the family borrows from Extraction is shared code, not a copy. A change
  the family needs in a shared rule is raised against `extraction-command.md`
  first.
- The vault's haul stays vanilla's. The battle reports won or lost and field
  finds, nothing else.
- Do not touch the fork's rules or `MarineRuinsCMD` beyond lifting the gate and
  reading the outcome the commit path already expects.

## Done

All slices but the dropped slice 8 pass their tests, and the owner has played
an expedition on each grade. Fold the family into `ruins-expedition.md` and its row
in `mission-command-nouns.md`, add a row to `shipped.md`, and delete this story.
