# The Long Survey

Status: IN PROGRESS — authored fallen-hull arc: four story chapters on owned
seeds, built in the Khettara Bloom's pattern (`wellhollow-bloom-arc.md`). The
campaign scaffolding, The Fall, Stakes and The Line are built (What is built);
The Surveyor is open in `stories.md`.

Written: 2026-11-10

The owner (2026-11-10): build the derelict capital-ship crash as "a set of
story missions with a controlled seed so that we can get the feel for the
mission down", before any campaign phenomenon. Read `fallen-hull-nouns.md` for
the hull, its stakes and its laws; this doc owns only the arc: world, cast,
chapters, seeds, gates and replay. Voice follows `derelict-lore-guide.md`
(Voice and names): the watch does not speak.

## Premise

An Explorarium survey mothership parked in a high survey orbit over Garnir for
centuries. One of the free port's salvage crews tried to tow it in for breaking,
woke its station-keeping and lost it (owner, `hull-fall-cause` a: the botched
tow, a human cause and a guilty crew that still wants its prize). It came down
on the ice through a mining town, ploughing a trench. Within a night its survey
program was running: flocks over the town, rounds walkers on the trench,
strippers cutting up the headframes.

The port's own crews hold the port and its roads. The company is hired for
what they cannot reach, and later fights beside them.

## World and cast

- **The world** (owner, 2026-11-11): **Garnir** in Corvus (`corvus_IIIa`), the
  pirate free port, airless and cold, volatiles plentiful, mines and ground
  defences. The hirer and the allied
  columns of chapters 3 and 4 are the port's crews: pirate armour and militia
  (`pirates-lore-guide.md`). Played down to the port and its mining country,
  as the Khettara is to its district.
- **The defence chief** (name and portrait the owner's): the free port's
  defence chief, hiring and briefing every chapter, as Orens does. Speaks in
  shares, grudges, reputation and practical detail; names the crews, never
  "the pirates". Hires the company as a merc outfit for hire in the system,
  with no shared history: nobody on Garnir knows who the company is (owner,
  2026-11-11, `garnir-sable`: there is no sector-wide memory of who did what).
  The port's general dislike of the Hegemony may colour the voice.
- **The salvage crew's captain** (optional): whose tow brought it down, wanting
  the hull intact as the crew's prize; a voice in chapter 4 against wrecking
  the forge.
- **Castle**, the company's operations voice.

Map places take one naming scheme for Garnir; the hull is "the Surveyor" to
everyone on the ground.

## Chapters, in order

Each chapter is one battle of a shipped mission family on an **owned seed**
(`story-map-pools.md`): one map, its patch placing the hull, the lattice and
the stakes, played on every attempt, retry, replay and debug run. Fees and
force recommendations are UNTUNED; the arc is end-game challenge work like the
Khettara (seven squads, four machines).

| # | Chapter | Family | The beat | Hull state at start |
| --- | --- | --- | --- | --- |
| 1 | **The Fall** | Civilian Rescue (Extraction adapter, the derelict director in place of the swarm director) | The night it came down. The town on the trench's lip is sealed in its shelter; flocks over the streets, rounds walkers in the trench, strippers on the headframes. Lift the people out. Bonus: blow the hull's forward bay so it cannot launch again. | The hull only: no stakes, a fixed first wake, the forge's first cycles running |
| 2 | **Stakes** | Sabotage (exactly three named sites) | The forge has printed its first stakes and trains are carrying them out across the mine country. Three are planted on the lattice and counting down. Kill them planted; a stake that rises is a zone to fight through. | The hull; three stakes planted; one raised beyond them |
| 3 | **The Line** | Broad Front at city density (`heart-nouns.md`; the city push below), the claim's stakes in the mothers' place | The claim has reached the port city. The port's armour and militia and the company push the watch out of the city street by street toward the trench, each lane taking its next stake; heavy walkers and the columns' guns bring the city down round them. Cut the chains and the haul roads as the lanes go. Win: every stake in the city down or stranded. | Established stakes chained through the city to the hull; haul roads live; trains running |
| 4 | **The Surveyor** | Broad Front (the heart variant), the hull as its heart, then the hull's interior as a delve to the autoforge | The port's crews go in on the trench. Cut the outer chain to strand the claim, break into the hull, and put the autoforge out in its forge hall, guarded by the vault frame and a hall frame. | The full claim; the contingency on the forge's breach |

The order runs as the Khettara's does: the company alone, then a sabotage
raid, then beside the port's crews through the city, then the all-in finale.
Each chapter has one authored map, designed around its objective first and
then filled to serve it, and The Line is the arc's ground war through a
populated city, there to show off destruction (owner, 2026-11-11). Each chapter
adds one fallen-hull mechanic to the shipped watch:

| Chapter | New to the player |
| --- | --- |
| The Fall | The hull, its bays, strippers cutting up a town |
| Stakes | Stake trains, planted stakes and their countdown, the lattice |
| The Line | Haulers and haul roads, established stakes, the relay chain and stranding |
| The Surveyor | The autoforge, the build queue read off the rail, the hull interior |

## Seeds

Each chapter owns one seed with a patch that lays the hull, its trench and its
starting stakes over its family's ground (`story-map-pools.md`, The owned seed;
`fallen-hull-nouns.md`, The ground), one pool file each, so no shipped map
moves. The first attempt, retries, replays and the debug player all play it;
`LongSurveyGround` builds each as its family's setup generates its map.

| Chapter | Map | Ground | The hull |
| --- | --- | --- | --- |
| The Fall | `long-survey-fall` | `colony-breach` seed 38, its lift, bores and holdouts as placed | In the desert south-east of the town, broken in three, bow west, a short trench ploughed west off the bow along the town's southern edge (vetted over the mother bore's place, which is built up); no stakes |
| Stakes | `long-survey-stakes` | The owned Raid mine's works on its own seed (`RaidOwnedMap`, the mining site), seed 2, laid out frozen and airless (`long-survey-maps.md`, Chapter 2) | Down in the open ground east of the works, broken in three, bow west; a trunk south to the head works' and the ore line's planted stakes through a raised junction, and a chain down the east side to the pit's rim |
| The Line | `long-survey-line` | Its own: the district capital, Broad Front's city (`heart-front-city`'s plan) on seed 2 (`long-survey-maps.md`, Chapter 3) | Down across the city's north edge in the heart's place, broken in three, bow west; two chains of stakes running south into the city and branching along the boulevard, established nearest the hull, raised and planted at the three lanes' heads |
| The Surveyor | `long-survey-surveyor` | `heart-front` seed 5, its airbases placed, with two works districts either side of the approach, laid out frozen and airless | In the heart's place between the works, broken in three, bow south, its trench pointing down the supply road the columns come up; six established stakes round it, three raised and two planted at the claim's edge |

## The Line as a city push

Proposed (2026-11-11), built on once the ground's map plan for the chapter is
agreed; the family is pick `line-family`.

- **Family: Broad Front at city density.** It is the shipped family with an
  allied push and armour: allied columns, each a combined-arms lane advancing
  screened to its next objective and re-routed when it falls, infantry
  screening the streets and armour the open ground, guns and an air wing
  behind (`heart-allied-commander.md`, `heart-allied-forces.md`), and a city
  form already exists (`HeartCountry.Density.CITY`). Assault's allied
  garrison holds ground but does not advance; Conquest takes compounds in a
  city with no allied columns; Cordon holds a line rather than pushing one.
- **Objective and win.** The heart's mothers become the claim's stakes in the
  city: each lane's objective is its next stake, and the battle is won when
  every stake on the map is scrapped or stranded (its chain to the hull cut,
  `fallen-hull-nouns.md`, Relay chain). Stranding counts, so cutting a chain
  near the trench can win a lane without fighting every block. The hull and its
  forge are out of reach; that is chapter 4. Lost as every chapter is: the
  company eliminated. Allies never end the battle.
- **What the adapter needs.** A Broad Front setup on the chapter's owned city
  map with the hull patch; `FallenHull.install` in place of the bores and the
  swarm director, its queue printing trains toward the city so the haul roads
  are live; the lane objectives read from `FallenHull.stakes()` (nodes of raised
  and established stakes, pylons of planted ones) where the heart reads its
  bores; the win condition above as the heart's "every mother collapsed"
  replaced; beats and radio keyed `stake-N` in place of `mother-N`; and the
  allied commander fighting flocks and walkers (anti-air on flocks, anti-armour
  on frames, targeting stakes), which is the board's "garrison against the
  watch" row.
- **Destruction.** The city is the shipped city form; the heavy frames crush
  ordinary walls and props on their way (`mechs-nouns.md`, Wall crush), the
  columns' guns and the company's demolitions do the rest.

## Unlocks and replay

As the Khettara (`wellhollow-bloom-arc.md`, Unlocks and Replay): a separate
contact on the world's board; a victory unlocks the next chapter; a loss leaves
it on offer with a retry line and no fee; a won chapter stays on the board as a
replay on the same seed at half fee with no unlocks; story source, its own
ledger, never the event gate; no clock.

**The end-game branch** (owner, 2026-11-11: "branch to open up these end game
missions so the player can go to Asharu OR they can go to fight this"): the arc
opens with the Khettara Bloom when Morrow departs at the end of A Working Ship,
two end-game branches in one system. Both are offered at once, the player takes
up either first, and neither arc's progress opens, closes or gates the other
(`EndGameBranches`). The debug mission player lists the four chapters (LS1 to LS4) on their
seeds with the challenge force.

## Where the narrative lives

Briefings as `MissionDialogue` exchanges between the defence chief and
Castle, a retry and a replay line each, a board row with "Survey 2 of 4", the
debrief's story line, a portrait for the commander and one location banner per
chapter (pick through the art session), voiced later as the Khettara is.

## What is built

- **The ledger.** `LongSurveyProgress` (`CampaignState.longSurvey`): the four
  chapters in order, each unlocked by the last one's victory; a loss leaves it
  on offer with its retry line and no fee; a won chapter replays at half fee
  with no unlock; one active attempt, settled once by its own receipt. A
  chapter not yet built (`Chapter.built`) is never matched, offered or launched.
- **The contact.** `LongSurveyCampaign`: Garnir's defence chief on the port's
  board (`SYSTEM`, `PLANET_ID`: Corvus, `corvus_IIIa`), opening with the
  Khettara when Morrow departs at the end of A Working Ship
  (`EndGameBranches.open`, the one rule both arcs read). The chapters are fought
  on Garnir as `LongSurveyCampaign.world()` reads it: the frozen surface with no
  atmosphere (vanilla's `no_atmosphere`), so towns are sealed and nothing is
  farmed in the open; each owned map is laid out on its pool's home palette and
  skinned for it. Whether The Fall's town is sealed (one lock on its avenue,
  every civilian route through it): sealed (owner, `garnir-sealed` a). Rows read "Survey N of 4";
  never the event gate, no clock. Briefings are unvoiced stub exchanges between
  the defence chief and Castle (`longSurvey*` in `strings.json`), with a retry
  and a replay line; the chief's name and portrait wait on the owner.
- **The Fall.** `HullFallBattleFixture` builds `BattleSetup.createHullFall`:
  the Colony Breach rescue on the `long-survey-fall` owned seed (38, the
  colony with the Surveyor down beside the town; `THE_FALL_MAP`), with no
  swarm, eruption or bores. The hull's own claim stands in their place:
  `FallenHull.install` on the ground's site (`LongSurveyGround.rulesSite`),
  printing `BattleSetup.FALL_QUEUE` (workers and walkers, never a stake, so the
  claim does not spread in chapter 1) from `FALL_FEEDSTOCK`, with
  `FALL_WALKERS` (a line frame, a rounds walker, two strippers) already out at
  the bay mouth, and `FALL_PILES` of the town's buildings on the claim as
  salvage for the strippers (`TownSalvage`). The hull is awake from the
  start (`install(..., awake)`): its zone reaches `FALL_REACH` off the plates,
  over the whole town, its flock ranges the town's places (one building in each
  `FALL_RANGE_CELLS` square) and its walkers are out on the watch's tasking to
  the trench and the town's edge, contesting the rescue's routes with no
  special case for the shelter or lift. All UNTUNED. Ground with no hull site (no pool installed)
  falls back to a site network laid by `DerelictSecurity.install`'s route
  overload at `THE_FALL_GRADE`. Every attempt, retry, replay and debug run plays
  that one seed. Open: the forward-bay bonus objective.
- **Chapters 2 to 4.** `HullChapterBattleFixture` builds `LongSurveyBattle`:
  the chapter's owned ground (`LongSurveyGround.ground`, on its pool's owned
  seed, `LongSurveyCampaign.storyMap`), the company landing on the ground's
  pads under the player's command (no autonomous commander), its machines
  called down on them, the hull's claim installed (`FallenHull.install`) and
  the chapter's win as a `HullClaimObjective` read from the hull's own rules:
  a stake is down when it is scrapped or stranded. Lost as every chapter is,
  the company eliminated.
  - **Stakes.** The owned mine country with the hull east of the works; the
    Sabotage family's three named sites are the three stakes the ground
    planted (`LongSurveyBattle.planted`), won when each is down. The hull
    prints the survey program (`STAKES_QUEUE`, stakes escorted by rounds
    walkers) from `STAKES_FEEDSTOCK`, `STAKES_WALKERS` out at the mouth. All
    UNTUNED.
  - **The Line.** The port city with the hull across its north edge; won when
    every stake on the map is down. Broad Front's allied lanes
    (`BloomHeartSetup.alliedLanes`) step off from the port yards with the
    claim's stakes as their targets (`HeartLanes.targets`,
    `LongSurveyBattle.stakeTargets`: `stake-N` at its seat, an established
    stake a mother any lane goes for, a younger one its band's), installed
    with no heart (`BattleSimulation.installAlliedPush`, ticked where the
    heart ticks its lanes; their beats are not voiced yet). A target with no
    bore behind it is advanced on and fought round, never assaulted as a bore.
    The hull prints `LINE_QUEUE` (haulers, escorted stakes, line frames).
    The win reads `FallenHull.claimDown`. Open: `GarrisonTasking`'s orders
    (anti-armour on frames, rifles on flocks, the stranding kill first) issued
    to the lanes' squads, which the lanes command themselves today (`stories.md`,
    The garrison against the watch); the lanes' vehicle park, tool shed and air
    wing; and the beats keyed `stake-N`.
- **The debug player.** LS1 to LS4 (`DebugMissionCatalog.Scenario.LONG_SURVEY_*`)
  under Garnir, each on its chapter's seed with the challenge force (a
  Veteran Company, seven squads, four machines); a chapter is listed once
  built (`Chapter.built`): LS1 to LS3 show.

## Open picks

`line-family` (recommended: Broad Front at city density, above; alternatives:
Conquest in the port city, or Cordon's Line kept; owner, 2026-11-11: a), with a consequence for
chapter 4: with The Line on Broad Front, The Surveyor would repeat the family,
so its outer push is best kept short and the chapter weighted on the delve into
the hull (the Ruins family's interior, the autoforge as its vault).
`hull-fall-cause` (a), `long-survey-world` (Garnir) and `long-survey-opens`
(the end-game branch) are answered (owner, 2026-11-11), as are the nouns doc's
`hull-lineage`, `stripper-hauler`, `autoforge-look`, `hull-spread-law`,
`stake-look`, `hull-painting-res` (b, higher resolution) and `stake-composed`
(a, separate parts with construction animation to come), `garnir-sealed` (a,
sealed), `garnir-sable` (neither: no shared history, above) and
`fall-hunt-knowledge` (a, the hull hunts what its flocks see).

Still open, built on the recommended option meanwhile: `sealed-town-wall` (the rectangle; `battle.sealedTown.laying`) and
`garrison-interior` (packed).
