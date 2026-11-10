# Raid mission: what the player came to do

Status: ACTIVE — owner picks: the objective follows the raid's intent; break is "wreck the works" (`raid-break`); steal is graded by security (`raid-steal-scaling`), and every prize stands in a compound with a buffer in its own district; the map is an authored site per industry (`raid-mapgen`: site templates), and each site is one owned map laid against a reference market and skinned for the planet (The owned map; the owner picked the guarded size-6 reference, one map per site and 400x240). The slices are `raid-wreck-the-works.md` and `raid-site-templates.md`.

Written: 2026-11-09

Read `raid-command.md` (how the two commanders play a Raid), `raid-resolution.md`
(the raid fork and how a led raid commits) and `mission-tier-nouns.md` first.
This document owns what a Raid is to the player: its intent, its objective, and
how it scales. `raid-command.md` keeps the command laws.

## What a raid is to the player

The player chose the raid from the market's Take Hostile Action menu, and
chose its purpose there: take the market's valuables, or break one of its
industries for a while. The battle is about that choice. The player expects:

- the thing they chose on the map, looking like what it is;
- a short trip in, because the company lands inset into the town
  (`precincts.md`, owner pick `raid-landing`);
- pressure that builds: the alarm goes up, and the garrison comes;
- a choice between greed and safety, where what they do changes what they take
  home;
- a way out that matters;
- results that match the battle.

A Raid is never a conquest: the company does not clear the town, and it
succeeds without eliminating the defender.

## Vocabulary

- **Intent** is what the raid is for. **Steal** is a raid for valuables
  (`MarketCMD.RaidType.VALUABLE`): the goods the player picked, each a raid
  objective with its kind (commodity, ship weapons, blueprint, AI core, special
  item, credits) and the industry it comes from. **Break** is a raid to disrupt
  an industry (`RaidType.DISRUPT`): the one industry the player named. The intent
  is read from the raid record's type and nothing else. A contract's Strike is a
  break on the industry its contract names. A story chapter states its own
  objective and keeps it.
- The **works** is the named industry laid on the map as a place of its own:
  its buildings and yards, its **machines** and its way in. A refinery's works
  is its tank farm, a mine's its headframes and mills, a factory's its lines.
- A **machine** is one part of the works a charge can wreck. Wrecking a machine
  takes a settled Marine combatant holding its pocket uncontested for its
  charge time; it cannot be undone.
- The **stores** are where a market keeps its goods: warehouses for
  commodities, the depot for weapons, the laboratory for blueprints, the
  admin vault for AI cores and credits. The **bonded vault** is one walled,
  guarded store that holds everything worth stealing.
- A **load** is one picked good made portable: carried by a squad, slowed, and
  taken out by the boat.
- **Security** is how hard the market keeps its prize (below).
- **Lift** is the company's boats: one Hermes to start, 12 seats, one squad a
  trip, back for the next; berths 2 and 3 once bought (`fleet-facilities.md`).
  A mission never draws more boats than the company has.

## Security: one scaling rule

The market already exposes how well it is defended
(`TargetProfile`): `defenseLevel` (0 to 7, a blend of ground defences, heavy
batteries, the orbital station's tier, high command and a planetary shield),
whether it has the ground-defence network and heavy batteries, and its
garrison estimate's strength, quality and readiness.

**Security** grades that into three steps:

| Security | Reads | Steal | Break |
|---|---|---|---|
| **Open** | `defenseLevel` 0-1, no ground defences | goods in their own stores, scattered: strip what you can reach | the works stands open: yards, fences, a few posted guards |
| **Guarded** | `defenseLevel` 2-4, or ground defences | bulk goods (commodities, weapons) in the stores; the best (AI cores, blueprints, special items) in the vault | the works is walled with gatehouses; guards posted at the machines |
| **Fortified** | `defenseLevel` 5+, or heavy batteries | everything in the bonded vault: one hard score | walled, with the market's own emplacements inside, and the garrison's precinct beside it |

One rule covers both intents: **security sets how concentrated and how walled
the prize is.** A soft market spreads its prize out to be picked over; a hard
one gathers it behind a wall. The owner picked this graded steal row
(`raid-steal-scaling`, 2026-11-09); the break row follows it.

## Districts and compounds (owner)

A government does not store its valuables next to people's homes. The owner
added this to the graded pick: the prize stands in a **compound**, in a
**district** of its own, behind a **buffer** of open ground.

- **Districts.** The map is laid by function: homes, government, port,
  industry and the garrison. Each prize sits in the district that owns it: the
  works in industry, the bonded store at the port's cargo terminal, the vault
  in the government compound. Homes stand apart from all of them.
- **Compounds.** Each prize is a walled or fenced compound with its gates
  facing the roads. Its **buffer** is cleared ground (yard, apron or killing
  ground) between the compound's edge and the next block, so the approach is
  open to its guards.
- **Security grades the compound**, never moves it:

| Security | Edge | Buffer | Inside |
|---|---|---|---|
| Open | fence, wide gates | narrow | goods in their own stores |
| Guarded | wall, gatehouses, guards on the gates | moderate | bulk in the stores, the best in the vault |
| Fortified | thick wall, corner towers, emplacements | wide and cleared | everything in the bonded vault |

Each industry's Raid site (Site templates) lays these districts and compounds.
An industry with no site yet keeps the derived map, where the works stands on
the map's own industrial ground.

Security never changes how many defenders there are: that is the tier's share
of the garrison (`raid-resolution.md`, law 7). It changes where they stand.

## Tier: how big the raid is

The tier comes from the garrison's whole headcount, no lower than Raid's floor
(`MarketRaidMissionFactory.tierFor`); a contract states its own.

| Tier | Map (`MapScale.forRaid`) | Squads recommended | Trips, one Hermes | Machines in the works |
|---|---|---|---|---|
| First contract | 200x112 | 2 | 2 | 3 |
| Established | 280x168 | 3 | 3 | 4 |
| Veteran | 280x168 | 4 | 4 | 4 |
| Reinforced | 400x240 | 6 | 6 (2 a boat with three) | 5 |
| Full strength | 400x240 | 8 | 8 (3 a boat with three) | 5 |

- **Defenders** are the tier's share of the market's garrison, posted at their
  stations and in a held mobile pool (`raid-command.md`).
- **The works' own guard.** On a site, a squad stands at each of the works'
  guard posts from the first tick, out of the garrison's share and never the
  mobile pool, so the machines are held when the raid lands and the garrison
  beside the works answers the alarm (owner pick `raid-works-guard` a;
  `RaidWorksGuard`). Riflemen hold the posts while any are left; a mech does
  not stand in a guard room. The routine guard still walks to the machine.
- **Alarm waves.** The first charge or the first store broken raises the
  alarm. Each wave is at most three mobile squads; a bigger garrison has more
  waves, not bigger ones, so a company that lingers meets more of them.
- **The landing is a cadence, not a block.** With one boat the company comes
  down a squad at a time; a bought berth halves the wait. Squads recommended
  rise with the tier faster than berths can be bought, so a full-strength raid
  with one boat is a long build-up: buying berths is what makes big raids
  fast. The way out is the same in reverse.
- **Machines** rise slowly with the tier; each one is a fixed share of the
  disruption, so a bigger industry takes more work to break fully.

The map's shape at each size is the industry's site (Site templates).

## Site templates (owner pick)

The owner picked an authored Raid site per industry (`raid-mapgen`,
2026-11-09). A **site** (`RaidSiteTemplate`) states where the landing, the
homes, the industrial quarter, the works and the garrison stand relative to each
other, as regions of the map, and the generator grows the roads and blocks
between them.

- **Drawn once, from the landing.** The company comes down in the homes, the
  near town, at the landing edge. The industrial quarter stands between the homes
  and the works, so no house stands against the compound, and the district's
  yards stand beyond the works. The homes' streets reach a shorter way than a
  settlement's, so the town keeps to its district. The works compound is at the deep end,
  in its industrial district, and the garrison is at the deep end on the works'
  other flank, so the alarm comes from beside the prize, not from behind the
  boat.
- **The works is a compound.** A square place walled by security, with its
  buffer of open ground (the glacis) as wide as security says: 3, 5 or 8 cells,
  open to fortified. Its wall is the security's rung (picket, garrison,
  stronghold), no harder than the operation's ceiling. Its program is one
  machine hall per machine, a generator hall, a stores and two guard posts,
  each a building of its own on yard enough to stand apart (a detached
  program); the raid's machines are its halls.
- **Varied by the seed:** the whole site mirrored east to west and north to
  south, and every place seeded anywhere in its region.
- **Varied by the market:** homes grow a town's streets from size 5, a
  hamlet's below; from size 6 an outskirts suburb stands behind the homes on
  the landing side. Map size follows the tier as before.
- **Raid only, industry by industry.** A Raid on an industry with no site
  keeps the derived map. Every other mission's map is unchanged; the glacis is
  the standard 3 cells for every place but a Raid site's works.

Each industry is a class of its own (`RaidIndustry`, listed in `RaidIndustries`):
its works program, where its works and district stand, the ground it lays
beside the works (a mine's pit), and how it dresses the built works
(`RaidWorksDressingStage`, before the landscape pass). `RaidSiteTemplate` is
the seam they share: the landing, homes, garrison, roads, walls and the seed's
turn of the map, the same for every industry. The plan names its industry
(`PrecinctPlan.raidIndustry`).

The sites so far, each the same districts with the industry's own works:

| Industry | Works (its machines first) | Industry round it |
|---|---|---|
| Heavy industry (`heavyindustry`, `orbitalworks`) | machine halls, a generator hall, stores (`HeavyIndustryWorks`, `FortressProgram.works`) | a dense industrial quarter and yards |
| Mining (`mining`, `techmining`) | the head works as an ore-processing chain: the receiving house the pit's ore line lands on, the stockpile shed, a mill line per machine, the smelter, the loadout, stores (`MiningWorks`) | sparse ore yards and a railhead |
| Refining (`refining`, `fuelprod`) | the planet's one cracking plant laid as its process (`RefiningWorks`): the receiving manifold at the rear, where the crude and volatiles pipelines land from the feeders; a unit per machine (its heater and pump house, the charge's place, with its distillation and cracking columns on a pad beside it); the product pump house and control house toward the front; no yards district. Dressed by `RefineryGround`: pipe racks on gantries in from the map's edges beside the feeders, between the units in the order the stock passes, and out over the haul road; the tank farm, held ground before the works (`RefiningWorks.TANK_FARM`), its tanks in bunds with stair gaps and walkways (`TankFarm`), the loading lane and rack with tankers on the haul-road side; a flare stack off the works' deep corner; spills | a dense industrial quarter, set back toward the homes |
| Light industry (`lightindustry`) | the planet's fabrication works laid as its process (`LightIndustryWorks`): receiving dock and parts stores at the rear by the feeders, a fabrication shop per machine, assembly and a canteen in the yard, finishing and two dispatch sheds at the frontage by the haul road; its yard dressed by `FabricationYard` (an overhead parts line hall to hall in process order, the feeders' trucks at the dock, the work outside each hall's doors (lathes and mills at the shops, a roller line at assembly, racks at the stores, cased pallets at dispatch), stock yards of pallets on the open ground, the shifts' walkways from the canteen kept clear) | a dense industrial quarter |
| Farming (`farming`, `aquaculture`) | the farm plant: a silo per machine, the packing shed, stores (`FarmingWorks`) | farmsteads (`PrecinctCharacter.FARMSTEAD`) |
| Spaceport (`spaceport`, `megaport`) | the planet's only orbital lift, laid as the cargo moves: the customs hall at the gate the town roads come in by (haulers queued outside), a bonded shed per machine, the tower and pump house at the rear; out of a rear gate, the lighter apron held at the deep end as a mine's pit is: container staging under a gantry crane, the fuel main from the farm, three to six pads by market size with blast walls between, scorched, one freighter mid-turnaround on a `PORT_LIGHTER` stand no company lands on (`PortWorks`, `PortApron`) | a dense industrial quarter |
| Military (`militarybase`, `patrolhq`, `highcommand`) | the motor pool: a motor shed per machine, the magazine (`MilitaryWorks`); the barracks are the garrison beside it | depots |

The owner accepted heavy industry's site and asked for the rest in order
(2026-11-09); every industry a raid can name now has a site, each awaiting the
owner's verdict (`raid-site-templates.md`).

## The owned map

The owner, 2026-11-10: "All of these should use a single seed, that we own and
make good. They should be skinned for the planet and make sense for the
planet." A campaign Raid does not lay its map from the raided market. Each
Raid map **owns one seed** (`story-map-pools.md`), in
`mod/data/world/pools/raid*.pool.json`, generated on its pool's home palette
(ARID) against a fixed **reference market**, then re-dressed for the raided
world by the planet skin (`PlanetSkin`), which moves no cell (`RaidOwnedMap`).

- **The maps.** One per site (`raid-heavy-industry`, `raid-mining`,
  `raid-refining`, `raid-light-industry`, `raid-farming`, `raid-spaceport`,
  `raid-military`), and the generic `raid` for a raid for valuables, a
  contract's Strike, and a disrupting raid on an industry with no site.
  `RaidOwnedMap.mapFor` chooses; `BattleSetup.raidSeed` gives the campaign the
  owned seed (`MissionLaunch`), and any other seed plays the old unvetted,
  market-derived path, which is how a fixture or a replacement is tried.
- **The reference market** (`RaidOwnedMap.reference`): size 6, so the homes
  are a town's streets with an outskirts suburb; defence 3 with ground
  defences, so security is guarded (a wall and gatehouses, a 5-cell glacis);
  a spaceport and a market; the site's own industry, and heavy industry on the
  generic map so a works on an industry with no site has industrial ground;
  no climate, so no storm bank. Its faction is independent, which only the
  emplacements' style reads.
- **Tier and scale.** Every owned map is 400x240 (`RaidOwnedMap.SCALE`), its
  fortification read at Established, with four machines in the works whatever
  the raid's tier (`BattleSetup.raidMachines`). The tier sizes the force,
  never the ground. 280x168 was too small to skirt the base (owner: "Probably
  larger than this. How is the player supposed to skirt the base?"), and the
  owner picked 400x240 (`raid-owned-scale` a) from the maps with the landing,
  the routes, the objective and the defenders drawn on them.
- **What the raided market keeps.** Everything that is not ground: the
  faction that defends, the tier's share of its garrison, its air, mechs and
  support kit, and the world the skin dresses the map for.
- **Acceptance** (`StoryMapAcceptance`, run by `storyMapPools`): on its first
  generation the map stands a raid up; a site's map holds a works with all
  four machines reachable; the generic map also stands a raid for a target.
  An owned Raid entry takes no patch yet.

Given up, for now, by owning the map: the market's security no longer grades
the compound's wall and glacis (every owned map is guarded), the town no
longer grows or shrinks with the market, and a storm world's bank is gone.
The owner kept the guarded size-6 reference (`raid-owned-reference`) and one
map per site (`raid-owned-sites`). Seeds were screened over 2 to 7 on the home
at 400x240 for a landing clear of the garrison, a shortest walk that runs past
it and a skirting walk that gets round it (2 for the generic, heavy-industry,
mining and farming maps, 6 for the rest; each pool file's note gives its
figures).

`createSnapshots -Psnapshot=planet-skin-look -Dsnapshot.planetSkin.maps=raid,...`
frames each owned map on every world; `raid-owned-look` frames candidate seeds
and the owned seeds, whole and close on the objective, with how each would play
drawn on it.

## Break: wreck the works (owner pick)

- The works stands where the industry's site puts it (Site templates), walled
  by its security. For an industry with no site yet, the map's own industrial
  ground stands in.
- Its **machines** are the objective. Each takes a charge: a settled Marine
  combatant holding the machine's pocket, uncontested, for the charge time. An
  interrupted charge starts again. The first charge raises the alarm.
- **Leaving is the player's call.** Once one machine is wrecked the way out is
  open: a Marine combatant settled at the Hermes' berth ends the raid. The player
  can stay to wreck more or pull out with what they have done.
- **What it is worth.** The raid command's own disruption
  (`DisruptIndustryRaidObjectivePluginImpl`: days from the marines it was set
  up with, times the raid's effectiveness) is committed at the effectiveness
  the command computed, times the **share wrecked** (machines wrecked over
  machines in the works). Every machine wrecked adds days. Wrecking none is a
  loss: the raid is on record and breaks nothing.
- It is loud, unlike Sabotage (`sabotage-command.md`): no stealth, no field
  presence limit beyond Raid's own, and the alarm is up from the first charge.

## Steal

Graded by security (owner pick `raid-steal-scaling`): open security leaves the
goods in their own stores, guarded moves the best into the vault, fortified
gathers everything in the bonded vault; each store and vault stands in its
compound (Districts and compounds). Each picked good is a load in its store or
vault; a squad loads it, carries it
slowed and the boat takes it up between squad trips; the take is the value
share of the loads that reach the boat, committed as the raid's effectiveness
times that share.

## Open questions

- Whether leaving needs every surviving squad aboard, or one at the berth as
  today. Today's law (one settled survivor at egress completes) stands for the
  first slice.
- How a contract's Strike reads intent beyond the industry it names.
