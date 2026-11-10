# The Long Survey

Status: IN PROGRESS — authored fallen-hull arc: four story chapters on owned
seeds, built in the Khettara Bloom's pattern (`wellhollow-bloom-arc.md`). The
campaign scaffolding and The Fall are built (What is built); chapters 2 to 4,
the hull rules and the hull's ground are open in `stories.md`.

Written: 2026-11-10

The owner (2026-11-10): build the derelict capital-ship crash as "a set of
story missions with a controlled seed so that we can get the feel for the
mission down", before any campaign phenomenon. Read `fallen-hull-nouns.md` for
the hull, its stakes and its laws; this doc owns only the arc: world, cast,
chapters, seeds, gates and replay. Voice follows `derelict-lore-guide.md`
(Voice and names): the watch does not speak.

## Premise

An Explorarium survey mothership parked in a high survey orbit for centuries.
A salvage consortium licensed to tow it in for breaking woke its station-keeping
and lost it; it came down in a district's foundry country, ploughing a trench
through a mining town (pick `hull-fall-cause`; recommended: the botched tow,
because it gives the arc a human cause, a guilty party with salvage papers and
a reason the world's owner pays mercenaries rather than its own fleet). Within
a night its survey program was running: flocks over the town, rounds walkers
on the trench, strippers cutting up the headframes.

The world's garrison holds the district's capital and the roads. The company
is hired for what the garrison cannot reach, and later fights beside it.

## World and cast

- **The world** (pick `long-survey-world`). Recommended: **Chicomoztoc**, the
  Hegemony's industrial world in Aztlan: foundry country is feedstock
  everywhere, and the garrison is the Hegemony's ground army, so the allied
  columns of chapters 3 and 4 are the Hegemony armour line
  (`hegemony-lore-guide.md`). Played down to one district, as the Khettara is
  (`wellhollow-bloom-arc.md`, Premise): the core's regulars are elsewhere; the
  district has a garrison battalion, its militia and hired outfits.
  Alternatives: Kazeron (Persean League; exotic-metal mines; the plant that fed
  the Domain's terraforming, which the survey program would recognise), or
  Asharu again (the APDF and Orens; cheapest, but the same world twice).
- **The garrison commander** (name and portrait the owner's): the district's
  ground commander, hiring and briefing every chapter, as Orens does.
- **The consortium's man** (optional): the salvage factor whose tow brought it
  down, wanting the hull intact for its papers; a voice in chapter 4 against
  wrecking the forge.
- **Castle**, the company's operations voice.

Map places take one naming scheme for the world chosen; the hull is "the
Surveyor" to everyone on the ground.

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
| 3 | **The Line** | Cordon (the Line variant: hold redoubts; `cordon-nouns.md`) | The claim has reached the district road. The garrison holds a redoubt line; established stakes send walker trains and flocks at it in surges. The company is the moving hand: strike haul roads, cut a chain, plug the line where walkers break it. Large-scale walkers and flocks against the garrison's armour. | Several established stakes in a chain; haul roads live |
| 4 | **The Surveyor** | Broad Front (the heart variant), the hull as its heart, then the hull's interior as a delve to the autoforge | The garrison's columns go in on the trench. Cut the outer chain to strand the claim, break into the hull, and put the autoforge out in its forge hall, guarded by the vault frame and a hall frame. | The full claim; the contingency on the forge's breach |

The order runs as the Khettara's does: the company alone, then a sabotage
raid, then beside the garrison on a line, then the all-in finale. Each chapter
adds one fallen-hull mechanic to the shipped watch:

| Chapter | New to the player |
| --- | --- |
| The Fall | The hull, its bays, strippers cutting up a town |
| Stakes | Stake trains, planted stakes and their countdown, the lattice |
| The Line | Haulers and haul roads, established stakes, the relay chain and stranding |
| The Surveyor | The autoforge, the build queue read off the rail, the hull interior |

## Seeds

Each chapter owns one seed from the home pool of its family, with a patch that
places the hull and its claim (`story-map-pools.md`, The owned seed). The first
attempt, retries, replays and the debug player all play it. Recommended
starting points, for the ground session to vet or replace:

- The Fall: the owned `colony-breach` seed 38, the hull's trench patched in
  where its deep bore stands, so the shelter, lift and town are the vetted ones.
- Stakes: a Sabotage seed whose three sites can be lattice points.
- The Line: the owned `cordon-line` seed, the claim laid beyond its redoubts.
- The Surveyor: the owned `heart-front` seed, the hull in the heart's place.

## Unlocks and replay

As the Khettara (`wellhollow-bloom-arc.md`, Unlocks and Replay): a separate
contact on the world's board; a victory unlocks the next chapter; a loss leaves
it on offer with a retry line and no fee; a won chapter stays on the board as a
replay on the same seed at half fee with no unlocks; story source, its own
ledger, never the event gate; no clock. When the arc opens is pick
`long-survey-opens` (recommended: alongside the Khettara, after A Working
Ship). The debug mission player lists the four chapters (LS1 to LS4) on their
seeds with the challenge force.

## Where the narrative lives

Briefings as `MissionDialogue` exchanges between the garrison commander and
Castle, a retry and a replay line each, a board row with "Survey 2 of 4", the
debrief's story line, a portrait for the commander and one location banner per
chapter (pick through the art session), voiced later as the Khettara is.

## What is built

- **The ledger.** `LongSurveyProgress` (`CampaignState.longSurvey`): the four
  chapters in order, each unlocked by the last one's victory; a loss leaves it
  on offer with its retry line and no fee; a won chapter replays at half fee
  with no unlock; one active attempt, settled once by its own receipt. A
  chapter not yet built (`Chapter.built`) is never matched, offered or launched.
- **The contact.** `LongSurveyCampaign`: the district garrison commander on the
  arc's world's board (`SYSTEM`, `PLANET_ID`: Chicomoztoc in Aztlan meanwhile,
  one constant for the `long-survey-world` pick), opening when Morrow departs at
  the end of A Working Ship, as the Khettara does. Rows read "Survey N of 4";
  never the event gate, no clock. Briefings are unvoiced stub exchanges between
  the commander and Castle (`longSurvey*` in `strings.json`), with a retry and a
  replay line; the commander's name and portrait wait on the owner.
- **The Fall.** `HullFallBattleFixture` builds `BattleSetup.createHullFall`:
  the Colony Breach rescue on the owned `colony-breach` seed (`THE_FALL_MAP`,
  its pool's owned seed, 38), with no swarm, eruption or bores. The derelict
  watch stands in their place: `DerelictSecurity.install` on a route (the
  route-and-source overload, no ruins vault) from where the colony's deep bore
  would stand (the eruption plan's mother site) toward the shelter, at
  `THE_FALL_GRADE` (EXTENSIVE, UNTUNED) in the Domain livery. Every attempt,
  retry, replay and debug run plays that one seed. When the hull site lands,
  `THE_FALL_MAP` and the install move to the hull-site seam; the forward-bay
  bonus objective follows the hull rules.
- **The debug player.** LS1 to LS4 (`DebugMissionCatalog.Scenario.LONG_SURVEY_*`)
  under Chicomoztoc, each on its chapter's seed with the challenge force (a
  Veteran Company, seven squads, four machines); a chapter is listed once
  built, so only LS1 shows yet.

## Open picks

`hull-fall-cause`, `long-survey-world`, `long-survey-opens`, and the nouns
doc's `hull-spread-law`, `hull-lineage` and `stripper-hauler`. All are built on
the recommended option meanwhile; they go on the arc's picks page as picture
cards once the art session has pictures for them.
