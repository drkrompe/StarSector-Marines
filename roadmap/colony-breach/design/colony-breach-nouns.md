# Colony Breach — Nouns

Status: ACTIVE

Written: 2026-10-21

Updated: 2026-10-28 — the evac cordon is the defence force's staging post: its line dug in before landfall, a field HQ, aid station and supply dump with the company's cache inside the ring. Earlier: the evac cordon's line stands in its trench, four teams facing the town and one on the rear. Earlier: a pooled colony's patch may place its lift, bores and holdouts (Asharu's are the owner's). Earlier: the swarm strikes a standing vehicle from its side and claws one that bars its only way; its flow fields go round one where they can. Earlier: the colony plays from a pool of one map per biome. Earlier: holdouts are placed clear of the bores. Earlier: each bore surges into its own hold, a cap per strain stepped by tier (owner's pick). Earlier: feeding ramps slower: a harvested body pays a third of what it did and a recycled Rav under a quarter of that. Earlier: a maw may draw a Bombard, one living per bore; the bloom comes in from the outskirts: the first wave open at landfall either side of the fields, fissures forage into the town, fed bores bud further in, the mother on the deep outskirts five minutes in.

Updated: 2026-10-25 — raids go for survivors in the open: one snatches a colonist and comes home with the raid round the guns.

Updated: 2026-10-25 — the flier law counts any gun that tracks fliers, so the colony's heavy guns meet it; a tender builds flak first with fliers about.

Updated: 2026-10-25 — Harriers snatch colonists in the open alive and fly them into the bore.

Updated: 2026-10-25 — a fresh bloom grows its spires by eating: a satellite fed to maw raises one, root first.

Updated: 2026-10-25 — a growth's root leads: it digs out and thickens first, and the growth rises at its end.

Updated: 2026-10-25 — the brood spire's deep raids: four round the flak to a soft target behind the line, and home.

Updated: 2026-10-25 — Harriers carry feed: a pack with nothing to hunt flies the dead home into its bore's mouth.

Updated: 2026-10-25 — the brood spire's patrols: three off a quiet, full roost fly a loop over the nest and land.

Updated: 2026-10-25 — a fed body slides into the mouth, sinks and is swallowed with a gulp.

Updated: 2026-10-25 — the Bombard's sound: a wind-up groan and creaks, the whip, the burst, and a stinging haze hiss.

Updated: 2026-10-25 — the skitterer's own sound: patter, squeak, and a placed work loop.

Updated: 2026-10-25 — the nest: a crescent mouth, roots to its growths, the spire's roost; the skitterer, the nests' worker strain.

Updated: 2026-10-25 — the Matriarch and the Brute show damage as worn chitin, and each round as a puff or a glance.

Colony Breach is the refresh of Civilian Rescue, the alien-swarm colony mission.
It replaces Civilian Rescue in place: the same campaign event, mission factory,
fixture lineage and Marine rescue commander, with a colony worth fighting
through and a swarm that comes out of the ground rather than the map edge.

This doc owns the swarm side, the colony, the bores and the breach sequence.
The Marine corridor commander, the cohort, the shelter release and the lift
remain Mission Command authority in `extraction-command.md`; this doc names the
inputs Colony Breach adds to it and nothing more. Live pressure acceptance
inherits `rescue-pressure-acceptance.md`.

## The fantasy

A colony is being eaten from underneath. The company lands in the fields at its
edge, fights to the shelter where the survivors are sealed in, and has to hold a lift zone
open long enough for shuttles to take them off. The swarm does not walk in from
the horizon: it came up out of bores on the colony's outskirts, either side of
the fields, and is hunting into the town when the company comes down. Every
colonist it drags home feeds it, and a fed bore buds another further in, so
every bore left open gets worse. The player can spend force to collapse bores and buy quiet, or
harden the lift with a called-down flying tender so that the rest of the company is free
to go hunting. Air power can harass but cannot clear the map, because the bores
defend the sky over themselves.

## Origin (mod choice)

Evidence tier: **mod choice** (`faction-lore-nouns.md`). Nothing in Starsector
0.98a canon describes this organism; it is ours, and it must be written so that
it never contradicts canon.

The swarm is not an alien species that arrived from outside. It is a weapon. A
self-perpetuating biological weapon from the late Domain / AI War period,
engineered to deny worlds and outlast whoever released it. It does not need its
makers: it lies dormant underground in reservoirs and old seeding sites, and it
wakes when a colony grows dense enough above it to be worth eating. That is why
it surfaces across the Persean Sector from time to time as a recurring problem
rather than a war with a front: a mining colony goes quiet, a relief call comes
in, and somebody's company is paid to get people out.

**Name.** It is **the Rav Bloom** (owner, 2026-10-21): the weapon and an outbreak
of it. **Ravager Bloom** is the older, fuller form, rare. Spacers call an
outbreak **a bloom**, understated like weather ("There's been a bloom on
Asharu"), and the bodies are **ravs**, talked about as monsters rather than as a
weapon. "the swarm" stays the lowercase generic word in mission UI. The Bloom is
whispered but known: few have seen one, almost everyone knows of a colony that
went quiet. "Bloom" carries red-tide imagery without fixing a colour; art decides
that. A spacer rumour says it was a sporeship payload turned into a weapon; under
the maker rule below it stays rumour.

Consequences that the design keeps:

- **It is a system, not a society.** No diplomacy, no faction relations, no
  commander with intent. The swarm director is a process, and the strains are
  stages of a life cycle that someone designed.
- **It eats to grow.** Biomass is its economy. Colonists are its food (see
  *Harvest*), which is why the colony is the target and why rescue starves it.
- **It recurs.** Outbreaks are a sector phenomenon (*Campaign: blooms*): they
  show up at colonies across the campaign, are never "defeated" for good, and a
  collapsed mother bore ends an outbreak, not the weapon.
- **Who made it is a question, not an answer.** Briefings and radio may
  speculate (Domain denial programme, a Tri-Tachyon-era leak, a sporeship payload,
  something older);
  no text states the maker as fact.

Names, the life cycle in in-world terms, each faction's view and the tone and
vocabulary for briefings, radio and item text are in `swarm-lore-guide.md`.

## Campaign: blooms

On the campaign layer the Rav Bloom is a **sector phenomenon**, not a mission
type and not a second event family. A **bloom** is one outbreak at one market,
from its first seepage to the day it ends. It is the only thing that raises a
Civilian Rescue: the rescue is the bloom's **distress call**, raised through
the shared open-event gate and settled by the rescue's own exactly-once
lifecycle (`campaign-event-nouns.md`). A rescue with no bloom behind it would
be a Colony Breach with nothing coming out of the ground, so there is none.
The live system is `RavBloomSystem`; everything it does to a market goes
through a `BloomWorld`, so the rules run unchanged over a synthetic sector in
evidence. A bloom's calls are the **only missions the campaign seeds by
planet** (owner, 2026-10-24; `contracts-nouns.md`, Where missions come from):
everything else is story work.

**Spores travel with people** (owner, 2026-10-22). Dormant Bloom material rides
on cargo and arrives with people who do not know they carry it, all through
occupied space. It wakes where it lands in a colony busy and dense enough to
feed it. So a bloom can start on any colony, and where it starts follows the
traffic, not a map.

**Reservoir.** An old seeding site under a world, which makes a bloom there
likelier but is not needed for one. It is a hidden, fixed fact of the save,
hashed from the sector seed and the market, likelier on worlds with ruins and
on habitable or farmed worlds (the sporeship rumour), and never stored: nobody
holds the map.

**Waking.** Once a week the sector may wake one bloom, at a preset rate
(*frequency*), with at most two blooms running. Where it wakes is weighted by
the colony: trade traffic through its port and population inflow lead, then its
size (dense colonies are worth eating), a reservoir under it, and deep
workings. A colony younger than ninety days or smaller than size 3 cannot wake
one. The busy core therefore blooms most, and the Hegemony quarantines most of
what blooms. The draw is seeded by the save and the week, so a reload replays
the same week.

**Stages.**

| Stage | What is true | What the player hears |
| --- | --- | --- |
| Stirring | Seepage, a couple of weeks; no market effect | a deniable rumour in the Rav Bloom intel |
| Erupted | Bores inside the colony; the *Rav Bloom* market condition (stability and accessibility) | the distress call on the Distress Net, two weeks to commit relief; the Hegemony's quarantine notice where it polices |
| Dark | Nobody lifted enough out. At the first stage the colony is cordoned and the garrison fights the bores; at a harsher tier its workings shut, it loses a size at intervals and it can spread | a colony cordoned (or gone quiet), with the Church's and the Path's readings |
| Starved | Half or more of the cohort was lifted out: the bloom has too little to eat and burns out without spreading | the lifts got them out |
| Ended | The outbreak is over; the condition lifts and unrest lingers | how it ended, said honestly |

An erupted colony goes dark when its call is refused or expires, when the call
never got through the gate within its window, or forty-five days after
eruption with nobody lifted out. A dark bloom with nobody answering ends on
its own after the tier's time: **put down** by the world's own military at the
first stage, **burned out** at a harsher one.

**Ending one.** The player's tools are the existing ones: commit relief, fly
there, run the evacuation. The battle's report of evacuees and of the mother
bore (*Hive bonus*) feeds the bloom on its next day. A collapsed mother bore
**contains** the outbreak whatever the evacuation: it ends at once, the colony
keeps its size, and the reservoir rests three cycles. Lifting half or more out
**starves** it. Fewer than half changes nothing about the bloom, though the
people lifted out are saved. A call still open when its bloom ends is closed as
expired. The weapon is never ended: every end rests the reservoir (a cycle, or
three after containment) and never removes it.

**Spread.** At a harsher tier a dark bloom may also seed a new bloom on a
neighbour, in its own system first, else the nearest within six light-years: a
local version of the cargo vector. A seeded bloom skips seepage, and three
generations deep it seeds nothing further. The first stage seeds nothing; its
spread is the weekly wake alone.

**Factions.** The Hegemony quarantines a bloom on its own worlds and, while it
lives, anywhere in the core; the quarantine tightens the condition, and a
quarantined bloom contained earns the Hegemony's regard. The world's owner
regards evacuees (by the quarter of the cohort lifted) and resents a refused
call. The Luddic Church regards any evacuation as mercy and calls the bloom a
judgement on the Domain's pride; the Path calls it the Domain's sin come home;
scavengers call a dark world dead ground. No line names a maker or says the
weapon is gone.

**What stands without our state.** At a harsher tier a bloom pulls vanilla's
levers (a size lost, industries disrupted, recent unrest), so what it did to a
market survives if the ledger is lost; only the condition reads the ledger back, and a condition
with no bloom under it applies nothing. The ledger is one nullable object on
the campaign state, created on first read, so an older save starts with none.

**Severity is a tier, and the mod ships the first.** Every consequence of an
unanswered bloom (size lost, workings shut, unrest left behind, neighbours
seeded, a colony lost) is read from the severity tier (`BloomTuning`), so the
Bloom grows teeth by raising the tier, not by rewriting the system. The mod
ships **benign**, the first stage: an unanswered bloom costs its market the
condition while it lasts and nothing else, and the world's military puts it
down after sixty days. That is a limit of what the mod can model today, not a
statement about the weapon (owner, 2026-10-22): the consequential tiers (*scar*,
*dark and spread*, *deadly*: colonies eaten down, neighbours seeded, worlds
lost) stay runnable and are the intended direction once the campaign can hold
worlds lost or held. The owner's other picks: *frequency* frequent, so a
player who wants the job gets it often while the consequences stay minor (rare
and seasonal exist), *player colonies* exposed by the same rules
as every colony (also *after the first answered bloom* and *never*), where
later events are expected to land. `-Dmarines.bloom.severity`, `.frequency` and
`.playerColonies` select a preset for a control run. `gradlew.bat
bloomEvidence` plays every preset under three player policies over N cycles and
reports blooms a year, outcomes, spread and size lost. On the shipped
defaults the two-running cap holds *frequent* to about four and a half blooms a
year rather than its nominal six; about 7% land on player colonies; the player
is offered about four calls a year (calls never overlap, since one campaign
event is open at a time) and receives about twelve intel messages, never more
than five in any thirty days.

**Not yet.** The colony's industry does not yet pick the outlying installation
in the precinct plan (`bloom-installation-flavour.md`), and dead-ground
scavenger fleets, Tri-Tachyon's interest in samples and what containment pays
beyond regard are not built.

**Direction (owner, 2026-10-22; not work).** The Bloom is meant to grow into a
campaign arc, in three movements:

1. **The assaulted settlement.** The first response to a colony being eaten:
   Colony Breach, as built.
2. **Organising the defence.** Faction militaries respond in force: allied
   columns, large redoubts, and the company sallying out in a helping role on a
   wider front rather than carrying the fight alone.
3. **The heart of the infection.** A climax assault on the bloom's centre on
   the surface, in overgrown country.

The arc runs **per infected world** (owner, 2026-10-22): any bloom can carry
three missions in turn, each a single battle: Colony Breach, then the
defence (the Line or the Road), then the heart (Broad Front or The Burn), the
campaign picking each variant, each mission offered on its own and opted into, and the player may stop after any of them;
a world whose arc stops runs on the ordinary bloom clock. The authored
instance is built: **The Khettara Bloom** on Asharu
(`wellhollow-bloom-arc.md`) offers Colony Breach, the Road, The Burn and
Broad Front as story chapters in a fixed order, each unlocked by the last one's victory and replayable once won,
outside `RavBloomSystem` and the event gate.
`bloom-mission-arc.md` is the direction for movements 2 and 3 and the sector
threat: the Cordon (the Line and the Road as variants), the heart (Broad Front
and The Burn as variants), what each reuses, candidate
stories, how one beat feeds the next, and the clock and gate hooks the beats
need from this lifecycle.

## Vocabulary

**Colony.** The generated settlement the battle is fought in: a town plus an
outlying installation (mine head, refinery or pumping yard), declared through a
precinct plan rather than the stock city. About 400x240 cells. Places in the
colony are where bores erupt, so the precinct layout is gameplay, not set
dressing.

The colony is the campaign's high-risk rescue. It is laid out rather than
derived from the event's market — a town at the centre, a working
installation (depot character) to one side and a dormitory suburb to the
other, the side seeded — because the colony is the same kind of place whatever
world the event names, and a rescue fixture has no market to derive from. Its
map is mission-owned (`MapScale.COLONY`, 400x240) and must contain a
residential shelter, a clear lift footprint, enough buildings for the
eruption sites, enough held buildings with room indoors to be holdouts and a
clear landing field; a map that lacks any re-rolls the seed. In the campaign
the colony is not rolled at all: it plays from a story-map pool of one vetted
map per world biome while the mission is tuned (`story-map-pools.md`), on the
biome alone and with no re-roll. A pooled map's patch may put its lift, bores
and holdouts where a person placed them (owner, 2026-10-28, for Asharu: the
Asharu Placement Board in `tools/picks/pages.md`): the lift takes the clear
pickup site nearest its cell, each bore the doorstep or open ground nearest
its cell, each holdout the held building its cell falls in. The rules below
that choose them, clearances included, then do not apply, a side objective
never moves an authored bore, and a placement that cannot seat fails the map.
Lower risks and the
debug stress path keep their tier map and run the same breach on it, seating
whatever eruption sites that smaller map has room for.

**Landing field.** Where the company comes down: open fields outside the
colony's edge, never its streets. Shuttles cannot land amid an infested town:
bores open underfoot, spore colonies cover the sky over them, and every building
is cover for the swarm right up to the hull. So the company lands where it can
see them coming and fights its way in, and that is why it does not land next to
the colonists it came for. The field is a landing place of the `OPEN_FIELD`
kind (`precincts.md`): a band of farmland along the north or south edge (the
seed's choice; the town, the installation and the suburb run across the map's
long axis, so that is the colony's far side from every place a bore comes up
beside), with the town laid out on the rest of the map beyond it, never round
it. Nothing is built, walled or tall within forty cells of the field's centre,
and the berths stand within sixteen, so every berth has at least twenty-four
cells of open ground on every side, a rifle's reach. The walk into the town
starts across that open ground. The colony map must have it, clear, with every
arrival area on it and room for the lift, or the seed re-rolls;
`-Dbattle.colonyBreach.fieldLanding=false` is the control, which lands
wherever open ground is first found, as Civilian Rescue did, and keeps the
ordinary lift.

**Colony militia.** Allied, non-player defenders who survived the first night.
They hold a few buildings through ordinary garrison manning, crew whatever
colony turret emplacements still work, and may run a technical or two. They
are a resource the player inherits, not a force the player commands. The
shelter guard is one militia post among several; on the colony map a wall team
mans the civil-defence compound round the shelter (see *Redoubt*), and a
team mans each *Roadblock*.

On the colony map the other militia posts are the **holdouts** (see
*Redoubt*): buildings spread across the colony, each chosen as far as it can be
from the shelter, the lift and the posts already chosen (out to a third of the
map's short side, past which the seed decides), clear of the landing pads and
with a walk to the lift. Each fireteam holds its building's held place under
garrison authority, so it mans the building's windows and doors the way any
held building is manned; a holdout with works adds a second team on its
firing step (see *Redoubt*). The compound's guns stand beside its gates,
and a holdout with works has its heavy gun just inside its gate; of the posts
without, two keep a working
heavy-machine-gun emplacement outside the door. Two posts have a parked militia
technical. Placed hardware stands on open ground away from the cohort's walk to
the lift and outside every redoubt's works, because a gun or a truck in a
one-lane street, or in a yard, closes it. Lower risks
keep the shelter guard alone, and `-Dbattle.colonyBreach.militia=false` is the
control that drops the rest.

**Colony population.** The colonists outside the shelter: ordinary fleeing
civilians, not part of the cohort and not counted by the evacuation, spread
through the colony's buildings a few to a house (every house before any gets a
second), clear of the shelter and the lift. They are what *Harvest* feeds on, so
there are bodies at stake wherever a bore comes up. The count is drawn from the
seed between 48 and 64, and the breach snapshot records how many were placed
and how many are still alive. Lower risks keep the stock handful of ambient
civilians.

**Living field** (owner, 2026-10-22: "a more dynamic field… more scattered
allied colonist forces and civilians. A lot of these will be feed for the
packs"). The colony is not an empty board between the company and its
redoubts: people are scattered across it, fighting and dying away from the
company, so what the player sees is a colony being overrun. None of them is
the company's to save or the cohort's to count: they are the colony's own
**allies** (`ALLY`, under garrison authority, never in the player's command
pool, never ending the battle or touching the roster) and its **civilians**,
and every one of them is prey and harvest by the rules that already hold
(*Harvest*: a Runner hunts the colonists it senses while a bore lives; packs
descend the prey field to the nearest allied or Marine body; a Brute goes for
armed colonists and breaks into the house they hold). Laid out after the
militia and the sheltering colonists, so it stands on nobody's ground, kept
off the shelter's clearance, the protected zones, the pads, every redoubt and
sixteen cells of the map's edge, where the opening seepage comes in:

- **Homesteads**: houses on the colony's spread held by a few armed colonists
  with their family indoors behind them, held as any building is held (a
  hold-the-place order on the house). How they are armed is a choice: *militia*
  (four guns at the colony militia's grade, the default and the owner's pick) hold through the
  opening seepage and fall during the breach, where their bodies feed the
  bores; *farm* (three guns at the lowest grade) are mostly gone to the seepage
  before any bore opens.
- **Militia knots**: teams of four standing in the open where the first night
  left them, each defending its patch of ground. They are prey the packs come
  for on their own, so fights break out across the town that the company only
  hears.
- **Refugee families**: groups of three to five who leave their houses and walk
  out **toward the company**, across the landing field to the map edge beyond
  it, at a family's pace (a little over half their running speed). Two set out
  in the opening minute as the company lands; any other the moment a bore
  opens within forty cells of its house; one no bore has sent running a minute
  after the tremor, the rest half a minute apart. A walking refugee is afraid
  of the swarm only: it walks on past the company and the militia, runs from a
  rav it senses (fourteen cells, as a civilian hears gunfire) at full speed,
  and walks on to its exit once nothing is near. One who reaches the edge is
  off the field and **got away**. Because their way out is the company's way
  in, the packs heading for the company cross them.
- **Stragglers**: lone colonists out in the open, wandering as an ambient
  civilian does.

How much of it the colony carries is a **density**, counted as homesteads,
knots, families and stragglers: sparse (2, 2, 4, 4), standard (4, 3, 6, 8) or
**dense** (6, 5, 9, 12). The owner picked dense, a colony still full of
people (2026-10-22). Nothing here is scored: the win is
still the cohort, and what the field records (placed, alive, refugees set out,
got away) is a reading the breach evidence reports beside the harvest. It is
deterministic in the seed: places are drawn at setup, and the families'
departures and walks are decided in the serial tick. Lower risks and smaller
maps carry none of it. `-Dbattle.colonyBreach.livingField=false` is the control;
`.livingField.density` and `.livingField.arms` select a density and an arming.

**Heavy assets are what the fleet brought.** The company's mechs and vehicles
reach the battle through its own Mech Support power, which every battle
honours; the rescue adds nothing to that. A mech-lance allowance for the
Marine commander is honoured only where the mission declares one, never derived
from the company's mechs, because an allowance is a separate payment and would
field the same machines twice.

**Shelter, cohort, lift.** As in Civilian Rescue (see
`extraction-command.md`): the cohort is sealed in the shelter until a Marine
reaches it, then follows a squad of the company out to the lift (the squad leads, the people
follow it) and boards only once a shuttle
is physically down. On the colony the cohort is spread over several redoubts,
and the **lift is on the landing field**: the shuttles taking the survivors off
come down where the company came down, for the same reason, beside the company's
pads on the town side. The pickup is the clear site nearest the shelter whose
cordon (below) stands wholly in the field's open country and whose wall keeps a
berth's reach and more off the berths (`CivilianEvacuationPlacement`,
`CORDON_FIELD_CLEARANCE`); where no such site exists it falls back to the
nearest clear site on the field, then to the farthest reachable site. Land in
the fields, fight in to the redoubts, bring the survivors back out.

**Evac cordon** (owner, 2026-10-28: "an allied evac cordon which the player
needs to get the survivors back to"). The militia line and the mech standing
round the pickup (`RescuePickupSupportSystem`). It is allied, never the
company: the player does not command it, it is not counted among the Marines,
and its shuttles are neither a lift nor a drop point for the company. It
replenishes from its own pool, a shuttle of militia whenever casualties pull
the line under strength and a fresh mech when the mech falls, for as long as
any of the cohort is still out, so the cordon does not fall. The company's job
is to get the survivors to it.

On the colony the cordon fights from **works** thrown up round the pickup
(`PickupCordonWorks`; owner, 2026-10-28: "trenches and sandbag / doodad walls
with space for the players forces to come through"). They stand on an octagon
round the lift, just outside its protected square: a trench sixteen out, a
sandbag parapet on its lip facing out, and hard against it a wall of hedgehogs
with crate stacks and scrap piles eighteen out. Parapet and wall are closed to
movement and open to sight, the wall laid as barricade so the swarm tears it
down where the way round is long. Nothing walkable is left beside the bags, so
the trench is the only ground the line can man (owner, 2026-10-28: "defense
forces should be in the trenches"). Two **gates**, five cells wide through every ring, are cut where
the cohort's walk from the shelter and the company's way in from its nearest
pad cross the works: the company, its mechs and its vehicles come in by them
with the survivors (owner, 2026-10-28: "there will need to be an opening for a
walk in by the player and the colonists"). Nothing is set down on a pad, a door,
a building or a prop already there. The line is the defence force's own, dug in
before the company was called (owner, 2026-10-28: "a staging point the
available APDF has set up? We the mercs are brought in as the muscle"): each
team stands in its stretch of trench from the first tick, already ordered onto
its post, which it mans as a garrison mans a run. Only the mech and the reserve
come by air; a team whose post has no trench, or every team where no works went
up, flies in to its formation point as before. The line faces the town, where the swarm comes from
(owner, 2026-10-28: "mainly manning positions in directions where the ravs are
likely to come from"): four teams spread evenly over the half of the trench
facing the shelter, and one watches the rear opposite it. Posts go only on
stretches long enough to be a fighting position, never on a stub cut off
between the gates, and where one half has no trench all five share the other.
The mech keeps its patrol inside.
`-Dbattle.colonyBreach.cordonWorks=false` is the control: the line flies in
and holds the open ground round its posts.

Inside the ring the cordon is the defence force's **staging post**
(`CordonStagingPost`): three **stations** behind the line, on the half of the
ring away from the town. The **field HQ** (a tactical table, consoles, a radar
dish, a generator and a parked APC) stands straight back; the **supply dump**
(crates, pallets, drums and fuel) on the flank the company's way in comes
through, with a resupply cache for the company in front of it, three air
drops' worth, so falling back to the post re-arms the company; the **aid
station** (treatment tables and cots) on the other flank, where the survivors
are seen to on their way to the lift. Each station is set down whole, turned a
quarter if that is how it fits, inside the trench by a walkway, clear of the
lift's craft, of the line's landing points and of every lane in through a gate;
one with no room behind the line is left out rather than put in its way. Its
pieces are closed to movement and open to sight. Orens's Colony Breach briefing
names the post: his reservists dug it, they hold the ring, and the company is
the muscle that goes into town.

**Redoubt.** A place the cohort is holed up in. The **main redoubt** is the
shelter, with the largest share of the cohort and the sealed, hidden shelter
guard; it is redoubt 1. On the colony map the shelter stands inside the
colony's **civil-defence compound** (below). The colony has one to three **holdouts** besides
(seeded, so two to four redoubts in all): a militia post with three or four of
the cohort inside, deeper in than the fireteam's firing cells.

A holdout is **placed in relation to the bores** (owner, 2026-10-26: a holdout
sat between two first-wave bores was "a lost cause"). The breach is planned
first, and a holdout stands at least 56 cells from every planned bore site
(`ColonyRedoubts.BREACH_CLEARANCE`), so the first wave keeps the outskirts and
no hole opens at a holdout's door. No place within that ring is left for a
bore to bud on or to be re-seated at when a side objective clears its own ring.

A holdout **claims a block, not a building**. Its people have thrown up
**works** round the building they hold, a few cells out, so a yard lies
between. A holdout is therefore **chosen for its room**: the building is picked
first among those whose barricade and step lines, at full margin, lie mostly
on open ground (three quarters at least, measured by `RedoubtWorks.room`), and
not on a street's asphalt, another building, a wall or a door. Only a colony
without enough such blocks falls back to the cramped ones. Chosen without
asking, holdouts landed on buildings fronting streets and their works were
drawn in to the kerb with no yard at all. The works are:

- the **barricade**, a ring of industrial fence broken by stacked crates and
  scrap, closed to movement and open to sight, with a parked truck dragged
  across each street it crosses. Wherever another building or a wall already
  stands on the ring, that structure closes the side itself. The barricade
  **crosses streets but never runs along one**: a side whose barricade or step
  would lie lengthwise on a street's asphalt is drawn in toward the building,
  to the kerb if need be (barricade only, no step on that side), and whatever
  still lies on the asphalt is left open, so the street keeps its full width.
  Laid along a street the works ate its lanes and left a sliver of road beside
  the fence. A truck closes only a carriageway the barricade crosses, never
  paved plaza;
- the **firing step**, one course of sandbag just inside the barricade all the
  way round, which is a fighting-position run;
- one **gate**, on the side toward the lift, the way relief comes in and the
  people walk out, with the post's heavy gun just inside it beside the lane.

The works are the ordinary pieces with their ordinary rules: fence, crates and
sandbag are destructible cover props, so a charge, a shell, a dozer or a Brute
opens them as it opens any fence; the trucks do not break. They never stand on
the cohort's walk to the lift, a pad or a protected zone, and they are refused
outright, leaving the holdout with sandbag aprons across its doors (the
compound prepared-ground courses a held base gets), when no gate can be cut or
the building's door would be shut in. Nothing is invented for them. Nothing
stands on a roof, because the battle has no roof posts.

The building's own people have also **knocked loopholes** through its walls. A
colony building picked for its room may be a blank block, with nothing to
shoot from except its door. Every straight run of wall with the building's floor
behind it and open ground in front gets an opening every few cells, centred on
the run. An opening is an ordinary window, not a new kind of thing: a defender
stands in it and shoots through, and nobody walks through it. Existing windows
and doors are kept, and a short stub of wall between a door and a corner stays
solid. `-Dbattle.colonyBreach.loopholes=false` is the control.

A holdout with works has two militia teams. The **house** team holds the
building's held place and mans its windows and doors. The **line** team mans
the firing step, an area defence whose claims are kept to the step's run, the
order a commander gives to man a stretch of trench. The line **falls back**
into the building once a swarm body is inside the barricade or half the line
is down, so a perimeter that breaks is seen to be pushed back to the house.
Militia have no commander of their own, so the fall back is the mission's,
made once, in place of the commander-tier fall back through layers that
`garrisonables.md` describes. A Brute whose prey stands in the open behind a
light barricade breaks the nearest piece between them rather than walking
round to the gate the Runners crowd into.

**A militia barricade is something the swarm tears down, not a wall.** Every
breakable piece of the militia's works (a redoubt's fence and crates, a
roadblock's hedgehogs and crates) is a **barricade** to the swarm: the swarm's
flow field crosses it at the cost of about a dozen cells of walking, so where
the way round is short the swarm goes round and where it is long the swarm comes
on at the line. A body whose way on runs through a barricade stops at the piece
and claws at it on its strike cooldown (a Runner's blow is light, a Brute's
heavy), and the gap a broken piece leaves is where the pack pours through. A
prepared position is therefore assaulted head on rather than flanked, which is
what makes it read as one. A redoubt's barricade ring is works and takes many
times the blows a roadblock's does, and a charge rams it rather than bowling a
piece over (`combat-durability-nouns.md`, Fortified works). Ordinary yard fences
elsewhere in the town stay walls.

**A standing vehicle is ground the swarm goes round.** A stopped hull's
footprint, widened by a body's reach (the cells squad routes already keep off),
costs the swarm's flow fields the most a step can, as the heart of a fire does
(`SwarmStepCost`). Where there is a way round the swarm takes it; where a
parked hull plugs the only one the field still leads through, and the hull
holds the swarm against its side while it stands. Without the cost a body
heading home or for prey walked into the hull's side, was pushed back as fast
as it walked in, and replanned into the same cells every few seconds.

**A standing vehicle is struck from its side.** A hull's centre lies inside its
footprint, where its shove never lets a body stand, so a Runner whose target is
a standing hull measures its reach to the hull's side and closes on the ground
beside it nearest to itself (`HullReach`), and strikes there with its own blow;
a hull on the move is still met head on, in front of its bow, where it rams.
A stopped hull the prey field leads a Runner or a Brute into is one that bars
the only way, and there the body claws at it as at a barricade, on its own blow
and cooldown, holding its ground rather than pressing in. So a vehicle parked
across the only gap holds the swarm while its hull lasts, and no longer
(`vehicle-ram`, `runners-parked` and `parked-barricade`).
A damaged hedgehog stands bent with a girder knocked loose; a destroyed one lies
toppled flat and gives no cover. Trucks do not break.
`-Dbattle.swarm.barricadeAssault=false` is the control: barricades are walls,
and the swarm goes round.

A holdout's ground is its works' rectangle. A Marine at its **gate** relieves
it, and the people walk out through the gate.
`-Dbattle.colonyBreach.redoubtWorks=false` is the control: every holdout is the
bare building with aprons.

**Roadblock.** A line the militia throw **across a street**, not round a
building: works on an approach to the main redoubt, with nobody behind them to
relieve. A redoubt claims a block; a roadblock closes a street, and the swarm
tears its way through it unless the way round is short (see the barricade law
above). The colony has one to
three (seeded), each on its own approach:

- **where**: a straight run of a street that leads toward the compound
  (walking along it toward the shelter shortens the walk there; the far end is
  the threat side), its asphalt unchanged for four cells beyond the works
  either way so it never stands in a junction, between ten cells and a third of
  the map's short side out from the compound;
- **across**: from frontage to frontage, sidewalks included, because a line
  kerb to kerb leaves both pavements open round it. A street with an open yard
  on either side, nothing closing it within thirteen cells, takes no
  roadblock: a line there closes nothing;
- **the obstacle row**, on the threat side: hedgehogs broken by crate stacks,
  and on some a truck dragged across one lane, closed to walking and open to
  sight, and torn down by a swarm that comes on at it;
- **the course** a row in: one course of sandbag facing the threat, a
  fighting-position run its **team** mans under the same order a holdout's
  line has;
- **the gun** on the course's line at one end, on the pavement, so it fires
  out past its riflemen rather than over them, with a tripod floodlight behind it.

Where the cohort's walk to the lift crosses the line its lane is left open
straight through both rows; every other street is closed. The pieces are the
works' own pieces under the works' own rules and the same keep (the walk, the
pads, the protected zones, ground already claimed), and they stand on the
street's paving as anything set down on a road does. A site is refused rather
than half built: a hole in the line that is not the cohort's lane, a course
too short to seat the team, no end for the gun, or a line that would cut the
street's far end off from the shelter's side. There is no building to fall back
into, so the team holds where it stands. Roadblocks are raised after the
holdouts' works and before the loose guns and technicals, which step round
them. `-Dbattle.colonyBreach.roadblocks=false` is the control;
`-Dbattle.colonyBreach.roadblockSpan=kerb` throws each line across the asphalt
only.

The **civil-defence compound** is the main redoubt's, and it is a different
kind of thing from a holdout's works. A frontier colony that always knew
something might come out of the wilderness built it when the colony was laid
out, so it is a **place in the colony's plan**, not works raised round a house:
a walled precinct on the town's far side from the fields, laid out square, with
a shelter hall of bunks, a sick bay, a canteen and a store round a muster
yard, each furnished as what it is. Because it is planned
with the colony, the town's streets run up to it, its gates are where they
cross its wall, and its neighbours are built against it: it fits the layout by
construction, with a strip of cleared earth between its wall and the houses
round it. The colony re-rolls a map that did not build it. The shelter is
always its hall, and the hall has windows on every wall that faces open
ground, the last building its people hold and fight from. Its windows are
laid out before it is furnished, so the bunks stand round them instead of
against the wall behind them; a field redoubt's bunk dugout is laid out the
same way.

It is a proper work, but a civil one, partway between a house with sandbags and
a Conquest fort and copied from neither. Its program owes nothing military (no
keep, barracks, armoury or guard post, each of which would publish a node of
the defender's, and on this map the defender is the swarm), and its wall is
colony-grade, light enough for a Brute, a charge or a shell to breach, though the compound
is works and holds a Brute off several times longer than a house wall
(`combat-durability-nouns.md`, Fortified works). The
militia make the rest of it when the battle is set up:

- a squat **tower** on each outer corner of the wall;
- a **gun tower** beside each gate: a heavy machine gun on the line of the
  wall behind slits, a step further along the wall where a building stands
  against it. A compound with one way in mounts its second gun on that gate's
  other flank, so it always has two. Those guns are its whole armament: no
  layers, no bunkers, no flak. Its heavy machine guns track fliers, so they
  answer Harriers and meet the flier law (*Laws*);
- **firing slits** along the wall's straight runs;
- a militia **wall team** standing behind the slits, holding the yard. Nothing
  is piled against the wall: the wall is the cover, so the compound has no
  sandbag step, which is what tells it apart from a holdout's works;
- **floodlights** on tripods inside the corners and the gates, facing out.

The ground the wall and its yard cover is the main redoubt's ground. The wall
team falls back into the shelter once a swarm body is inside the wall or half
the team is down, so a fall reads as the wall breached and the fight pushed
back to the shelter. The shelter itself is still sealed and never falls, which
keeps the triage. `-Dbattle.colonyBreach.civilDefence=false` is the control:
the colony is laid out without the compound and the shelter is an ordinary
house.

Every redoubt's people are one cohort for the objective, the outcome and the
campaign. Each member belongs to one redoubt and waits behind its door until a
Marine reaches *that* redoubt (its door, or its gate where it has works);
then it walks to the lift under the ordinary
per-member escort law. A holdout is not hidden as the shelter is: its militia
are prey like any of the swarm's, and once the shelter has opened its people
are too. A holdout **falls** when its garrison is dead and a swarm body is at
its works (or its walls, without works), or when nobody behind its door is
still alive. Its survivors leave
the cohort — overrun, they are the swarm's, harvested like any colonist once
no escort is near them — and the holdout is no longer anyone's to relieve. The shelter is
sealed and does not fall. That asymmetry is the triage: the main cohort is
safe while it waits, the holdouts are not, and which door the company reaches
first decides who is left to bring out. With one redoubt the rescue plays
exactly as a single-shelter rescue. `-Dbattle.colonyBreach.redoubts=false` is
the control: the posts hold militia only and the whole cohort is in the
shelter.

Where redoubts meet the stories still open: whether creep under a redoubt
weakens its aprons or its garrison is creep's to say. A strike detachment
competes with relief for the same squads, so the readiness gate counts every
redoubt still waiting as unmet need (see *Readiness gate*).

**Breach.** The mission-level event of the swarm coming up through the colony.
The breach has a shape over time (see *Flow*) driven by the **breach sequence**,
the one story sequence the battle runs.

**Breach clock.** The breach sequence's own time. The bloom is already eating
the colony when the company comes down (owner, 2026-10-25), so the clock starts
with the battle: the **tremor** is its first tick, the first wave is open from
it, and the mother bore comes up five minutes in. Reaching the shelter releases
the cohort and is published, but no longer starts anything.

**Eruption site.** Where a bore comes up, chosen once at setup and fixed
for the battle (`ColonyEruptionPlan`). A site is the open ground outside a
colony building's door (a place, not a cell picked off the map) and is never
inside the shelter's clearance, the lift's safe ring or a marine landing pad's
clearance, never closer to another site than an eighth of the map's short side,
and always on ground the swarm can walk from to the shelter. The bloom came in
from the outskirts (owner, 2026-10-25: bores flank the landing zone and grow
inward): the **first wave** is four sites, two on each flank of the landing
field (a place at least two site spacings, a quarter of the map's short side,
off the line from the field in to the shelter, so the company walks in down a
street rather than between two bores; owner, 2026-10-25), nearest the field first, the seed
shuffling places within a site's spacing; a flank short of places is made up
from the other, then from the outskirts straight ahead. They are open when the
company comes down. The **mother site** is the place spaced from the rest
furthest out along a flank, the flank away from the lift when the lift stands
off to one side: the deep outskirts, never at the cohort's boats. The first wave is numbered from
1, nearest the field first; the mother is named rather than numbered. Every
place no site took is kept for the bores the fed ones bud (*Budding*). Sites
and their ticks are published in the breach snapshot from the start, and each
eruption as its beat. Eruption is the seam: the sequence opens a site at its
tick and hands it to the installed eruption handler, which is where a bore is
built.

**Budding** (owner, 2026-10-25: the ramp is the hive feeding). Each time a bore
has eaten `BUD_BIOMASS` of biomass (some nine colonists) since its last bud, it puts out another a
block or two further in (`SwarmBores.budInward`): of the kept places sixteen to
forty-eight cells from it and at least eight nearer the shelter than it is,
spaced from every bore, bore on its way and spire, the one nearest the shelter.
Its root digs out to the place and thickens over twelve seconds (*Roots grow*),
and a new fissure opens at its end, numbered after the first wave. A root dies
with the bore that put it out, and nothing buds while ten bores live or are on
their way. With fed maws raising spires (*Brood spire*), this is the battle's
ramp: a colony left to be eaten grows bores street by street toward the shelter
and puts Harriers in its sky, and a colony whose people are kept from it stays
a few fissures on its edge.

**Seepage.** The thin perimeter pressure before the tremor. The swarm director
restores only a fraction of its population floor while the colony is seeping,
and its full floor from the tremor on. Since the tremor is the battle's first
tick, Colony Breach no longer seeps; the director spawns only from its bores.

**Bore.** A physical hole in the ground that the swarm comes out of. A bore is a
destroyable structure on a cell footprint with durability, a death cascade and
a map decal. The swarm director spawns only from living bores (plus a thin
perimeter seepage in the opening, before the tremor). Collapsing a bore
removes its spawn capacity for the rest of the battle. Bores erupt at
authored-by-rule places in the colony (inside precincts, never inside the
shelter or lift protected zones, never on top of a Marine), not at the map
edge.

A bore's body is an ordinary structure on the swarm's side, the kind a fort
aspect core is: it is shot, satchelled and broken through ordinary durability
and dies through the ordinary death and demolition path, which leaves a smoking
wreck; a crater marks where it opened and a second where it collapsed. Its
plate is heavy, so small arms wear it slowly and satchels, heavy weapons and
mechs are what collapse it. Its mouth is one cell of open ground it does not
seal: the swarm climbs out of it and anything can cross the lip, so opening a
bore changes no route, and a sapper can stand at it to set a charge. A bore
opens on the first clear cell outward from its site (a few cells at most),
never with a body on or beside that cell and never close to another bore; a
site whose ground is not clear waits and opens as soon as it is. Its cells are
declared to the bore goal when it opens and withdrawn when it collapses.

A squad is pointed at a bore the way it is pointed at any structure: an order
naming the bore's cell as the thing to open (`phased-assault.md`, A breach is a
real act). The squad walks there and its satchel carriers set charges on the
body; nothing about a bore is special-cased in Marine fire or demolition.

**Bore tier.** A bore grows while it stays open: **fissure** (runners only, low
rate) → **bore** (adds brutes and spitters) → **maw** (adds heavy strains, higher
rate, another and stronger spore colony). Growth runs on a slow base clock from eruption,
is slowed by damage taken, and is **driven mainly by biomass fed to it** (see
*Harvest*). Tier is what makes leaving a bore alone a decision with a cost.

Growth is points toward fixed tier thresholds. The base clock runs at a seeded
per-bore rate a little either side of the common one, so first-wave bores do not
step up in lockstep, and it is scaled down by the share of durability the bore
has lost, to a floor: a battered bore still grows, slowly. An unfed fissure
takes minutes to become a bore and longer again to become a maw. A tier once
reached is never lost. The mother bore opens at bore tier.

**Hold** (owner, 2026-10-26: "a cap per hole for units or unit types per bore
… Each upgrade then becomes a new difficulty curve"). How many of each strain a
bore keeps living on the field at its tier (`BoreTier.hold`): a fissure ten
Runners; a bore fourteen Runners, three Brutes and three Spitters; a maw twenty
Runners, six Brutes, five Spitters and its one Bombard. The hold summed is the
tier's capacity, the bore's share of the director's population target. A body
belongs to the bore that released it until it dies; a collapsed bore's living
bodies are orphans, counted in the field but held by no bore, and the Matriarch
is outside every hold. Laws:

- A bore surges only into its own hold: a due pulse waits until half a pulse of
  its own bodies has died, so killing what a hole has put out is what wakes it,
  and a quiet hole is not woken by another's losses. A mother that owes her
  Matriarch sends her whatever her hold.
- A strain whose hold is full is out of the draw, and the roll falls on the
  others; a surge ends early when every strain is full. Fresh biomass still
  swells a pulse, only up to the free hold: feeding buys tier, buds and older
  bodies, never more bodies than the tier holds.
- Each tier-up is a step: the row the bore holds at is the difficulty a hole
  can field, one row per tier to tune.
- Because a hole waits for its own dead rather than filling headroom another
  bore left, the field runs under the summed target: on Colony Breach 10–25%
  fewer living Rav than without holds, and at most six Brutes alive from any one
  bore where a bore once kept up to fifteen.

**The feed seam.** Biomass reaches a bore through one call,
`SwarmBores.feed(sim, bore, amount)`, with `amount` in biomass; the
bore to deliver to is found with `nearestLiving`. A feed buys growth at once
(ten colonists take a fissure to bore, about twice that to maw), adds
to the bore's fresh biomass, and brings its next surge forward. A feed into a
collapsed bore is refused and changes nothing: the delivery is lost.

**Harvest.** The swarm's economy. Colonists are food. While a bore lives, a
Runner hunts the colonists it can sense as well as the company and its
militia, and a body a Runner brings down (a colonist, a militia fighter or a
Marine on foot) is not left where it fell: the Runner seizes it and becomes a
**carrier**, dragging the body home down the bore field to the nearest living
bore at four tenths of its own pace, slower than a walking Marine. The body
trails a little behind it, lying along the drag. A carrier does not strike. At
the bore's mouth the body goes into the hole: it is **biomass**, `PREY_BODY_BIOMASS` through the feed seam
(owner, 2026-10-26: the ramp was too fast), so the bore grows and its next surge
comes forward and swells. It counts the moment its carrier lets go at the
mouth, and is seen going down (owner, 2026-10-25): it slides from where it was
let go into the middle of the mouth in under half a second, then shrinks and
fades as it sinks out of sight over the next, with its blood, and the bore
swallows it with a wet gulp and the thud of it landing down inside
(`SwarmHarvest.feeding`). A carrier killed on the way drops the body where it
fell; the body lies **loose**, and the next Runner to come within reach takes
it up again. A carrier whose last bore collapses drops its load the same way.
Laws:

- Biomass is the only fast path to growth. A starved bore stays a fissure for
  a long time; a fed one reaches maw quickly.
- Every colonist the company gets out, or keeps alive, is biomass the hive
  never gets. Rescue and hive pressure are the same fight seen from two sides.
- Carriers are readable and interceptable: a stream of bodies being dragged to
  a bore is the player's signal that a bore is about to grow, and a carrier is
  an ordinary Runner to shoot.
- Only a Runner or a skitterer carries, and only what it killed itself or
  finds loose (a skitterer kills nothing, so it carries only the loose). A
  Brute's or a Spitter's kill, or a body shot down by the company, stays where
  it fell.
- **The swarm recycles its own dead.** A dead Runner lies loose, to be taken
  up and dragged home like any load. At the bore it is worth `RAV_BODY_BIOMASS`,
  under a quarter of a colonist (owner, 2026-10-26: a Rav body fed is worth
  less than a colonist), so a recycled Runner buys back well under one Runner, and it is counted apart (recycled), never as a seizure or a
  delivery, so it raises no harvest beat. A Brute or a Spitter is too heavy and
  is left to the creep.
- **Forager packs** (owner, 2026-10-25: the first pulses hunt into the city).
  A fissure is hungry: while any of the colony's people are left to find, what
  it puts out (other than a scavenger pack) is a forager pack, whose Runners
  each go after the nearest colonist anywhere in the town, fighting only a
  fighter already within their reach. With nobody left to find, a forager
  fights like any Runner. So a young bloom's pulses run past a landed company
  into the town, and only a bore that has grown sends its packs at the company.
  Scene: `bloom-outskirts`.
- **Attack packs and scavenger packs** (owner, 2026-10-21). The dead lie where
  the fighting is, where every Runner has something to fight, so a bore sends
  its surges out by turns as an attack pack and a scavenger pack, the latter
  only while at least three of the swarm's dead lie loose. A scavenger goes for
  the nearest of the dead anywhere on the field and drags it home, fighting only
  what is already within its reach; with nothing left to fetch it attacks like
  any Runner. Any Runner with nothing to fight also fetches the dead within
  eight cells. So the killing ground in front of a defence is cleared by a
  visible stream of carriers, each an ordinary Runner to shoot, and a pack out
  scavenging is a pack not attacking. No body is seized while no bore lives, because there is nowhere to
  take it.
- Never a power suit, a machine or a structure: harvest takes bodies on foot.
- Evacuees in the cohort are never harvested while their escort lives: one
  who falls with a Marine or militia fighter within eight cells stays where it
  fell. A lost evacuee, with nobody near, may be taken.
- Harvest is deterministic and traced: deliveries per bore appear in the
  director snapshot, and the breach snapshot counts bodies seized, delivered,
  dropped and still being dragged.
- `-Dbattle.colonyBreach.harvest=false` is the control: Runners leave the dead
  where they fall and do not hunt colonists.

**Impale.** Harvest's living version, which makes it a fight instead of a
funeral. Any blow a Runner lands has a one-in-twenty chance to go in and hold
instead of wounding: the Runner becomes a carrier and its load is a living
body, **held** on its blades. Chance is the only thing about the fight that
decides it, so a fresh Marine in the middle of the line can be taken; the draw
is keyed by the strike itself (tick, Runner, target) rather than taken from the
battle's shared stream, so turning impales off changes no other roll. Only a
body harvest may take is impaled (a Marine on foot, a militia fighter, a
colonist; never a power suit, never an escorted evacuee), only by a Runner
that is not already carrying, and only while a bore lives. There is no new
kind of casualty and no downed state: a held body is a living body that is
being held. Laws:

- **The impaling blow does no damage**, and a held body takes no harm from the
  swarm: it is nobody's prey but its carrier's. It does nothing while held (it
  does not move, fire or take orders) and moves with the carrier, drawn at the
  carrier's trail point; the drag pose is the Runner art's to draw.
- **Direct fire never touches a held body** (owner, 2026-10-21). Fire at a
  carrier, a miss that strays into its load and a round that would go through
  the carrier never hurt the body on the blades, Marine, militia or colonist:
  the ray passes it, though the body stays in the battle for everything else.
  A blast's direct payload is direct fire and never lands on it either.
- **Area weapons still reach it** (grenades, mortars, rocket blasts, ordnance
  from the air): calling a blast down on a carrier costs the victim too, and a
  blast aimed at the held body itself still splashes it.
  `-Dbattle.colonyBreach.impale.areaHitsHeld=false` is the other answer to
  the owner's open question, ready to flip. A body killed on the blades is
  dragged on as an ordinary harvest corpse.
- **Kill the carrier and the body is freed**: alive where the carrier fell,
  with a wound in each leg where the blades went through, and back to what it
  was doing after three seconds. A freed Marine goes back under orders and a
  medic treats them as any wounded squadmate; a militia fighter goes back to
  its post; a colonist flees again.
- **A carrier holding one of a squad's own Marines is that squad's priority
  target** while it is in sight: a bump to the target choice and nothing more.
  No squad chases a carrier on its own; going after one is the player's call,
  and medics never approach a held Marine.
- **At the bore the body is taken**: killed there and fed in as one body of
  biomass. A body still held when the battle ends counts as taken.
- **Morale**: impaling a Marine costs their fire team what a death would, at
  the moment of the impale and once. Being freed gives nothing back, and being
  taken costs nothing more.
- **Casualties**: a freed body is alive like anyone else. One killed while held
  falls and is rolled as usual. A taken Marine is fallen with a fixed killed in
  action, no roll and nothing for a sick bay to save: the mission outcome
  carries the taken beside the fallen. Taken militia and colonists are simply
  dead, and the breach snapshot counts impales, frees and takings of every
  kind.
- A carrier cannot strike and carries one body at most, at Harvest's carrier
  pace.
- `-Dbattle.colonyBreach.impale=false` is the control: every blow lands as an
  ordinary blow.

A Brute that carries a power suit off would be a rule of its own; suits are
beyond a Runner.

**Spawning surge.** How the swarm moves out of a bore. Bodies do not trickle
out one at a time: a bore **spawns**, pushing a dense pulse of bodies out of
its mouth that pours along the flow field in a tight stream (a river, not a
crowd), then breaks apart and fans out on contact or at the end of the run,
and the bore goes quiet until its next pulse. Surges are timed by the director
and sized by tier and fresh biomass, so a just-fed bore answers with a big
surge. A surge is what the player sees and hears coming; the gap between surges
is the window to push a bore.

Each bore keeps its own surge clock: the first pulse a few seconds after it
opens, then one per tier interval (shorter at higher tiers) swung by a seeded
jitter. A pulse is the tier's size plus a few bodies per unit of fresh biomass,
which the pulse spends. A due pulse waits while less than half of it fits under
the director's population target, or less than half a pulse is free in its own
*Hold*, so a bore whose bodies are still alive on the field stays quiet. A released pulse comes up out of the hole in clutches
(*Emergence*), each body's strain drawn from the tier's table; a bore that
collapses mid-pulse releases no more.

**Emergence.** How a body comes into the battle (`SwarmEmergence`; the owner,
2026-10-25: the packs "just pop out as a full pack"). Nothing of the swarm
appears whole. It is presentation and order only: a pulse's size, its bodies
and its surge tick are the director's as before, and so is the flier law.
- **Clutches.** A pulse breaks out of the hole one to three bodies at a time,
  seeded by the bore, the pulse and the clutch, with a seeded pause of a third
  of a second to most of a second between clutches. A pack of eight is out in
  about two seconds and a maw's whole pulse in four or five, where it was out
  in under one. The Matriarch comes out alone, first. A body breaks out on the
  hole or the ring round it, and only on the mouth's outer cells when those are
  all taken.
- **Climbing out.** A body is held in the hole for a moment: seven tenths of a
  second for a Runner, nine for a Spitter, eleven for a Brute, six for a
  skitterer, nearly two for the Matriarch (who roars once she is out). It does
  nothing while it is held (no step, no blow, no quarry), but it is on the
  field from its first tick, counted, sensed and **shot as any body is**, so a
  gun laid on a mouth kills them as they come up. Its picture climbs out of the
  dark of the shaft: drawn at three fifths of its size and nearly black, it
  grows and lights to full on an ease-out as it comes out, facing out of the
  hole, and casts its shadow only as it rises. The hole throws a ring of dust as
  each body breaks out.
- **Muster.** Out of the hole, a pack forms up before it moves. Each pack of a
  pulse has a muster four cells down its lane toward its prey; a Runner of it
  with nothing to fight walks to its own seeded spot round there and mills,
  until the pack's last body is out of the hole, and never longer than four
  seconds from the pack's first. Prey it senses breaks the muster at once. So a
  surge is seen boiling up out of the ground, spreading round the mouth and
  setting off together, rather than standing up as a finished block.
- **Fliers hatch.** A Harrier pack is hatched, not placed (`SwarmFliers.hatch`):
  one Harrier climbs out of its source now and the rest one by one, a quarter
  to over half a second apart, each low and close over the source with a puff
  of dust, and climbs out to the loiter ring. The pack does not commit to a
  dive until the last is out. A brood spire's pack hatches out of its crown, its roost emptying a hook at a time as they go; a
  Matriarch's brood hatches off her back as she climbs out of the mother, and
  the hatch follows her; a pack a heart beat raises against the guns hatches
  out of the nearest bore's mouth and flies out to its standoff, and a spore
  colony's pack out of the colony. A source that dies mid-hatch stops it: the
  rest never come.
- **Unseen.** Emergence is a rule of the battle, not of the camera: off-screen
  or in fog a body is held and musters exactly as on screen, and the fog hides
  it as it hides any swarm body. The dust and the climb are presentation:
  nothing in the battle reads them, and a replay plays the same drawn or not.
- `-Dbattle.swarm.emergence=false` is the control: bodies come out whole and go
  at once, one every couple of ticks, and Harrier packs appear whole. Look
  suite: `swarm-emergence` (before and after, a bore surge and a spire launch).

**Mother bore.** The one bore whose collapse ends spawning from its precinct
cluster and is the target of the optional hive objective. It erupts later than
the others, five minutes in, out on the deep outskirts at the far reach of a
flank (*Eruption site*), starts at bore tier and is the only bore that can produce a
matriarch.

**Spore colony.** The swarm's static air defence, after StarCraft's spore
colonies. A bore sprouts colonies on the ground round its mouth as it grows:
one as a fissure, another on reaching bore, another on reaching maw. Each is
its own small destroyable structure with its own body on the map, and fires
spore bursts at hostile aircraft in its reach through the ground anti-air
system, exactly as a flak position does: aimed bursts at the nearest airborne
craft, never at the ground. A bore's own body never fires. Each burst is a shot,
so it is heard and drawn through the weapon's own fire sound and effects. The
look follows the grade: the firing sac flares and spits, and the shot is a
lobbed spore clump (burst), a slower three-clot cluster that bursts into three
puffs (flak) or a fast white-amber streak (lance). It bursts in the air at the
craft's drawn height, hit or miss, in an amber flash, and leaves spore haze
drifting there; it never marks the ground beneath.

A spore colony **looks armed with nothing overhead**, since a breach rarely has
aircraft and a gun that never fires otherwise reads as scenery: spores lift off
its sacs all the time, amber motes in a faint lilac halo rising and drifting up
off the body, more of them the higher its grade (`ColonyThreatFx`; the owner's
pick over an exhaled haze, alone or with the motes). Its **reach is not drawn**
(owner): a ring round every colony, or round each while aircraft are up, was
offered and declined; the shots and the motes say where it stands.

Bores and colonies are grown, not built, and look it: they wear the same
Rav Bloom palette as the creatures (lilac and dusky-violet carapace over
crimson sinew, ivory bone, amber nodes breathing), standing on the ground with
no hardstanding pad. The earth a bore heaved up is creep, in the creep's own
colours, so the rim reads as where the creep pours out of the ground. Nothing about them is regular: they erupted, so
they are lopsided. A bore is a slanted shaft torn up through the ground, one
side heaved up into reared jaw-plates, tusks and thrown paving, the other
collapsed into the funnel. The mother bore must read at a glance as far more than
a bore, the root of the whole bloom: a ring of great bone fangs hooked in over a
wide gullet of ribbed crimson flesh that falls away to black, a faint amber glow
far down where the reservoir is, the colony's paving heaved up round the rim and
sinew roots running far out into the creep, drawn much larger than a satellite
(a jaw, picked over plates vaulted closed over the hole and a lopsided cave-in).
A colony is an uneven cluster round one dominant, leaning sac. A ground colony
must never read as one: it is low and pressed flat into the creep, with no sac
and no pore aimed at the sky, a plated core with an amber eye whose crimson
sinew arms sprawl out over the ground like a starfish's, studded with spines
raked back towards the core (the owner's pick of three body plans, over a
spined armoured hump and a flat urchin, because it alone is told from a spore
colony at a glance at play zoom). It has the colonies' paintings, wounds and
remains: dead, its arms shrivel flat and its spines snap to stumps. Its grade shows
in its body as well as its numbers: a lone sac with a bud at fissure grade,
two lesser sacs pressed against it at bore grade, three and a plated, spined
flank at maw grade, each larger than the last. Each body carries several
paintings chosen per structure, so a map of them does not look stamped, and
their depth (the lit far wall of a shaft, shadows cast plate on plate and onto
the ground) is lit by the battle's own default sun, so the paintings are
never rotated or mirrored. They are drawn somewhat larger than their bodies
(hit radius and footprint) so that they read as structures among a mass of
creep and bodies.

They show their **damage**. Below half its durability a bore or colony is
drawn **wounded**: plates torn out of their sockets over raw sinew, gashes
through the carapace, a sac burst, a spine snapped, its amber dimmed; the same
painting, hurt. A wounded body **bleeds**, as the Zerg's buildings did: a
wound is a puncture in a living membrane, and it pulses to a heartbeat: on
each beat the torn slit dilates, its flaps lift and a gout of ichor is pushed
out over the lip to run downhill as a glossy film, which drains and thins
before the next. A long wound (a gash, a torn plate's socket) is a **fissure**
instead and sprays on the same beat: its lips part and throw a fan of droplets
downhill, arcing with their shadows under them, landing as splats that soak
away before the next beat. The loops are pinned to the wounds its own
painting shows and running downhill off its body (into the pit from a bore's
lip). The first wound bleeds as the body turns wounded, and more open the
further it is hurt, every one by under a third of that; each wound bleeds at
its own phase so a row of bodies does not pulse in step. The dead do not bleed.
When it dies it leaves its **remains** on the ground for the
rest of the battle, under the living: a bore's shaft caved in and choked with
rubble, its plates slumped and drained to a dry husk; a colony's sacs burst
and deflated. The remains are of the painting the structure wore, so a
battlefield's dead bores are as varied as its living ones, and a dead bore's
berm takes the colour of withered creep.

A colony keeps the **grade** of the tier that grew it: a fissure-grade colony
threatens light craft at short reach, a bore-grade one reaches further and
bites harder, and a maw-grade one reaches furthest and penetrates enough to
hurt gunships and dropships. The grade is the colony's weapon (range, damage,
penetration), never a list of what it may shoot at, so whether a burst hurts a
craft is the shared armour law against that craft's skin. A large feed that
takes a bore across two tiers at once sprouts a colony of each.

Colonies **wither** when their bore collapses. Killing them one by one opens
the sky over a bore that still stands: a bore whose colonies are dead has no
air defence of its own until it grows another tier. Laws: no air sortie should
be able to collapse a bore that still has its colonies without taking losses;
air is for after the ground push, or for after the colonies.

Colonies stand on the bore's **growth sites**, a broken crescent of open cells
round the mouth (`BoreGrowthSites`; owner, 2026-10-25, over an even ring that
packed them into a knot). The crescent wraps part of the way round, each site
at its own seeded distance, and the rest of the circle is the den's open
approach, turned per bore; the back of the den fills first, then the two
horns, so a bore's first three colonies already read as a den with a way in.
Only spore colonies use them; a ground colony roots out on the creep instead,
against the gun it answers (Creep: Ground colony).

**Creep carries the umbrella outward** (`CreepOutposts`). A living bore also
seeds spore colonies of its current grade on its creep, away from the mouth:
its **outposts**. An outpost roots on living creep that stands a couple of
cells deep on every side (never on the ragged front), on open ground, once
that cell lies one and a half times the grade's spore reach from the bore,
every other living bore and every living spore colony, so neighbouring
umbrellas only just overlap; the cell must stay good for a while before
anything roots. A bore holds at most one living outpost at fissure, two at
bore and four at maw; a dead outpost frees its place and the creep grows
another elsewhere. Outposts wither with their bore like the mouth's colonies.
So an air sortie that waits watches the defended sky spread with the creep,
and a bore's creep is worth burning back before its outposts stand.

**Nest.** A bore and everything it grew, read as one organism rather than
growths scattered on the creep (owner, 2026-10-25: "a more interesting nest").
- **Its mouth is a den**: the crescent of growth sites above.
- **Roots** (`NestTrails`). A rope of sinew raised out of the creep runs from
  the bore's rim to each of its living colonies, outposts and its spire, bowed
  and meandering by a seeded amount and tapering out to the growth, ringed in
  segments, with the creep's pulse running out along it (the owner's pick of
  rope, over a worn furrow and a lit vein path). A root is drawn only: nothing
  walks, stops or fights by it, and it goes with its growth or its bore.
  `-Dbattle.render.nestTrails=false` is the control.
- **Roots grow, and the growth rises where they end** (owner: thinner veins
  "dig out and construct" from the bores, and the root must be the lead-up to
  the growth, not follow it). A growth begun in play is first only its root: a
  vein (a band of the creep's veins thickened and lit) digs out from the bore
  over the first half of its lead and thickens into rope over the second, and
  only when it has come does the colony or outpost rise at its end, throwing
  the creep up round it. An outpost's lead is its twenty-second hold on its
  cell (shortened as skitterers tend it), so it rises when it always did; the
  creep counts a colony on its way as standing, so a held cell is never lost to
  one rising. A mouth colony sprouts the moment a feed crosses a tier, which
  nothing foresees, so its five-second lead stands it that much later than the
  crossing. What stands at setup, and everything creep matured at setup grew,
  stands at once with its rope; a satellite opened late is a bore, not a
  growth, and erupts as one. Look: `nest-growth-look`.
- **Its spire's roost**: the Brood spire's waiting brood (below).
- **Its workers**: the skitterers it broods (Swarm strain: Skitterer).
- **Clutter** (`NestClutter`): egg clutches (glossy eggs with amber hearts in a
  cup of sinew), brood pods (low ribbed sacs bound down by sinew) and shed husks
  (dry split shells) lie on the creep, a piece every few cells beside each root
  once it is rope, never at its ends, and a few in the gaps round the mouth of a
  bore at bore tier (two) or maw (four), past its rim and clear of its colonies
  and roots. Drawn only, with the decals: nothing walks round, hides behind or
  shoots them. Geometry-first paintings, three of each, never turned.

**Brood spire.** The tall growth an old bloom raises on its creep, which
Harrier packs launch from (`BroodSpires`; the owner's idea, 2026-10-25: "some
kind of spire like spawner"). It is the swarm's answer to a line it cannot walk
through, made into a thing on the map the company can find and kill.
- **An old bloom has grown them; a fresh one grows them by eating** (owner,
  2026-10-25). Where a battle's creep is matured at setup as weeks old (the
  Cordon and the heart) its spires stand at once. In any battle, a fresh Colony
  Breach among them, a satellite bore that has reached maw having eaten `FED_BIOMASS` of
  biomass (a dozen colonists) raises one (`BroodSpires.raises`): once its creep holds ground
  for one by the rule below, the spire's root digs out and thickens over ten
  seconds and the spire rises at its end in a burst of creep, as a colony does
  (*Roots grow*). So in a fresh bloom the air comes from the hive feeding, and
  every colonist kept from it is a spire that never grows. A bore raises one
  spire in a battle, and one destroyed never grows back. Scene: `fed-spire`;
  look: `fed-spire-look`.
- **Where it stands.** One off each living satellite (non-mother) bore at maw,
  in opening order, on open ground inside the bore's creep between sixteen and
  twenty-eight cells from its mouth and five clear of its colonies, and never
  within sixty-four cells of another spire, so a map carries a few, not one per
  bore. It stands out on the creep apart from its bore's knot of colonies (owner,
  2026-10-25: the first cut packed them into one clump).
  The mother bores have none: their air is the Matriarch's brood.
- **When it launches.** With its bore's surge (surges, not trickles): each surge
  launches a pack of seven off it, provided a marine or an ally stands within a
  Harrier's sense of it, no pack of its own is still in the air, at least
  forty-five seconds have passed since its last, and the flier law holds. The
  pack hatches out of its crown one by one, low over it, and is seen climbing
  out to its loiter ring (*Emergence*). A sleeping bore's spire is quiet with it.
- **Its roost.** While it waits, its next brood clings to the hooks up its
  flanks, wings folded, now and then half-opening them: none while its own pack
  flies, then filling back one by one over the gap after a launch, so a full
  roost is a spire ready to launch again. Drawn only: they are not units until
  they rise (`SpirePerchRenderSystem`).
- **Its patrols.** A quiet spire (its roost full, nothing to hunt within a
  Harrier's sense of it, no pack of its own up, the flier law met) puts three of
  its roost up every thirty to sixty seconds, counted from the last patrol's
  return (`HarrierPatrol`; the owner's roving squads, 2026-10-25). They hatch
  off the hooks as a launch does, then wheel round a seeded loop over the bore,
  eighteen to thirty-two cells out and turning through just over half to most of
  a lap, and come home and land on the crown one by one, back on its hooks and
  off the field. On the loop a patrol only sees twenty-two cells, a scout's eye;
  what it spots it hunts as any pack does, and with its prey gone it picks the
  loop up where it left it. A patrol home short leaves the roost short, filling
  back as after a launch, which holds off the spire's next launch too. So a line
  that keeps clear of a spire is still found if it stands on the nest's beat,
  and flak that thins a patrol buys time against the next pack. Scene:
  `harrier-patrol`; look: `harrier-patrol-look`.
- **Its raids** (`HarrierRaid`; the owner's "deep behind lines raids",
  2026-10-25). On its own longer gap, ninety to a hundred and fifty seconds
  from the last raid's return, the same quiet spire sends four of its roost
  deep instead of a patrol: to the richest soft target fifty to a hundred and
  sixty cells off that no anti-air reaches (the radio a forward observer calls
  the air in with, an unarmed truck, a medic, a colonist out in the open to
  snatch, a soldier on his own, a gun, in that order), the nearer on a tie. Its way is bent round every air gun's reach with six
  cells to spare, straight when it can be, else by one or two turning points
  off the direct line; with no target or no such way it patrols, and looks
  again a few seconds on. Out and home it hunts nothing. Over the target it
  dives only on what is soft within sixteen cells of where it was sent, never
  the guns, two dives and it turns for home (sooner with nothing there to
  dive on), flies the same way back and lands as a patrol does. Sent at
  survivors, it dives on nothing there: one of the four snatches a colonist
  (*Snatching the living*), and the carrier keeps with the raid home by its
  way round the guns before it makes for the mouth, so a colony's people
  behind the line are taken from under its flak, not over it. One sortie of
  a spire's is up at a time. So the line's rear is never safe from a spire it
  has left standing, and flak placed to cover the rear, not just the front,
  is what closes the ways in. Scene: `harrier-raid`; look: `harrier-raid-look`.
- **What it is to fight.** A structure on the swarm's side, fought as a bore's
  body is: shot, satchelled and destroyed through the ordinary paths (650
  structure under 220 plate, rating 12, so rifles wear it slowly and satchels,
  heavy weapons and machines take it). It never fights. It withers with its
  bore, as colonies do, so collapsing the bore grounds its spire too.
- **Its look** follows the bores and colonies (`art-source/swarm-bores`): a
  geometry-first painting of the Runner's chitin, lit by the game's sun, its
  height told by its lean and a long shadow thrown down-sun, drawn half again
  its modelled size so it stands out as a landmark on a mass of creep. Brood
  pods glow amber where the fliers hatch. Its body plan is the **roost** (the
  owner's pick of roost, chimney and clutch, 2026-10-25): one great curved horn
  of stacked chitin collars leaning out of a plated footing, hooked bone perches
  up its flanks with brood pods hanging under them. Three paintings; wounded, its
  pods burst and its skirt torn; dead, the horn snapped and lying on the ground.
- `-Dbattle.swarm.broodSpires=false` is the control: no spires and no broods.
  Scene: `brood-spire`.

**Swarm director.** The non-human pressure owner (today
`SwarmReinforcementSystem`). It decides how many, from which bore, and of which
strain, against a population target that scales with the number and tier of
living bores. It publishes an immutable pressure snapshot for evidence and
diagnostics. It never reads hidden Marine state the swarm could not sense.

Until the tremor it restores a perimeter floor (a fraction of it while the
colony seeps). From the tremor on it is **bore-sourced**: the perimeter is
closed for good, the population target is the sum of the living bores'
holds by tier (*Hold*), and bodies enter only as surges from living bores. Between
the tremor and the first eruption nothing spawns; once every bore has
collapsed, nothing spawns again. Every swarm strain counts toward the
population but the skitterer, which a bore broods under its own cap (Swarm
strain: Skitterer). The snapshot carries each bore that has opened: its tier, whether
it lives, integrity, growth, biomass fed, surges, bodies released, next surge,
its living spore colonies and the ticks it opened and collapsed.

A battle with no rescue (`cordon-nouns.md`) runs the director **bore-only**:
bore-sourced from the first tick over bores setup opened, with no perimeter
and no rescue to end it. A mission may hold its field at a **pressure share**
of the living bores' capacities; in the bore-only form due bores are then
served fewest-surges first and a pulse is sized to the share, so the pressure
lies across every bore rather than on whichever is listed first. A rescue's
director is unchanged by either.

The heart (`heart-nouns.md`) runs it bore-only too. A mission may hold a bore
**asleep**: it grows and feeds but sends no surge until the mission wakes it
with a surge due at once, and a sleeping bore adds nothing to the population
target, so the bores awake never field bodies on its behalf. A woken surge
still waits for room under the target, as any surge does.

**Swarm strain.** A kind of swarm body with its own stats, appearance family and
behaviour. A strain is a designed stage of the one organism (see *Origin*), not
another animal: every strain is the Runner's body plan and chitin specialised
for one job in the cycle, and reads that way on screen.

A Runner, Brute or Matriarch casts one soft ellipse laid along its body rather
than the round blob every other body casts: its drawn mass seen from above
(`SwarmFootprint`, legs and spines left out), scaled by its growth stage, turned
with its heading and set down-sun so its near edge sits on the body's centre,
the same rule a round body's shadow keeps. One sprite a body, as every body
casts, so a swarm's shadows cost what a squad's do; two blobs at shoulders and
hips would double that and darken where they overlap, and a spine's bend does
not read in a soft shadow at play zoom.

- **Runner** — the shipped 20 HP melee body. Volume. It is drawn on its own
  skeleton (`RavRunnerRig`), not on the layered infantry composition: a hunched
  hound whose feet stay planted on the ground, galloping and trotting with the
  spine flexing and the shoulders and hips swaying against each other, with two
  jointed spike arms on its shoulders. Each blow is one arm, alternating, and
  is either a stab (the elbow bows out away from the body, then the point
  drives straight forward) or a side swipe. The body is in every blow: a stab
  draws the trunk back on its planted feet, then lunges it forward onto the
  point, and a swipe winds the striking shoulder back and twists the trunk
  through the cut, the hips countering. The wind-up runs off the attack
  cooldown, so the blow arrives on the tick its damage is dealt. The rig is
  presentation only: nothing in the simulation reads it, and the strike rule
  itself stays the ordinary melee hit. A carrier (*Harvest*, *Impale*) is
  drawn hauling: it turns to face the body it drags, so it backs toward the
  bore at a trot with its shoulders heaving, the spike that struck stays in
  and the jaws latch on beside it. The body it hauls, alive or dead, is drawn
  lying (its outfit's fallen pose), head toward the jaws, jolting with each
  heave. Through a turn the spine bows into a C toward the inside, the
  shoulders and the hips swinging the same way while the planted feet hold.
  A dead Runner bursts: the trunk splits at mid-spine and the halves slump
  apart, and the head and one or two limbs come away (the rest stay on their
  half), thrown a short way (mostly behind it), spinning, bouncing once and
  lying flat where they land, while its blood (red, as the Marines' and
  the Matriarch's) sprays into splats
  around a pool spreading under where it died. Seeded per Runner, so a replay
  bursts the same way; presentation only, like the rest of the rig. In a crowd it
  is shoved as the shape it is drawn as, a capsule along its facing (the
  separation shape in `continuous-positions-nouns.md`), so a pack queues nose to
  tail and packs flank to flank rather than stacking into one another; its
  0.3-cell footprint radius stays its size to everything else.
- **Brute** — slow, plated, heavy melee; the strain that makes a garrisoned
  building stop being a safe room. Its plate (20 points at rating 10) is what
  small arms struggle with: a DMR or rocket goes through it, an SMG barely
  scratches it. A sensed emplacement outranks infantry as its target. When its
  prey stands inside a building it is not in and no opening into that building
  is near, it makes one: it walks to the nearest *light* wall or window of that
  building, breaks it through the ordinary structural path, and goes in through
  the hole. A door is already an opening, so a Brute uses a near door rather
  than breaking the wall beside it. "Light" means a colony wall (at most 120 of
  wall left); fortified and fort walls are out of its reach until something
  else has worn them down. Its blow carries penetration, so it hurts plated
  bodies and emplacements where a Runner's slash only scratches. It strikes
  what is under its head, about a cell from its centre to the target's surface
  (a hull's footprint, not a circle round its middle), and its weight lands
  with the blow: a hull or a mech it strikes takes the same contact a charge
  gives, at a slower closing speed, so a pickup or a light mech is shoved about
  as the fight goes on while a tank only rocks. On infantry
  the blow is a **trample**: the body under it is knocked down for a moment and
  bowled a little way, and anyone on foot right beside it takes half the blow
  and goes down too.

  It has two speeds: it **walks** at its sim speed and **charges** at a much
  higher one. A **charge** is a committed straight run at one target, or at the
  light wall it means to break, taken only when the target is in a band of
  middle distance, the line to it is open ground (a light prop or a body in the
  way is allowed: it is what gets bowled), and the last charge's cooldown has
  run. It begins with a **tell** (it stands head-down, the player's warning),
  runs on a line it can turn only slowly, and ends on a hit that stops it, on a
  wall, or when it runs out. The hit is its blow, heavier, and what follows
  depends on what it hits: infantry is thrown aside and knocked down and the
  charge goes on through up to two bodies; a light prop is bowled over; a light
  wall takes one double-weight ram; a heavy wall or a tank's bulk stuns the
  Brute; a vehicle is stalled by its **weight class** (light / medium / heavy
  on `VehicleType`); a mech is struck in the legs and, unless heavy,
  staggered.

  Running into a hull is a **contact**, not a rule of thumb (`HullImpact`):
  the Brute is a point mass at charge speed, the hull a rectangle about its
  centre of gravity with its own mass, and the two exchange one impulse where
  the Brute meets the footprint, momentum conserved. So the hull moves by how
  heavy it is against the Brute and turns by how far from its centre it was
  struck, and the Brute rebounds with what the hull did not take and is hurt,
  through its plate, by whatever impulse its bulk could not shed. A pickup is
  pushed half a cell and swung round; an APC moves a little; a tank only rocks
  on its suspension, and the Brute comes off worse. The hull then **slides**
  (`HullSlide`): its tires or tracks scrub the velocity away as a fixed
  deceleration in the hull's own frame, so a wheeled hull struck end-on rolls
  on while one struck in the side stops short, tracks hold harder than wheels
  along the hull, and the spin is scrubbed by the same sideways grip. Nothing a
  charge strikes is moved in one step: a thrown or trampled body and a
  rebounding Brute also slide to rest over the next ticks. An explosive
  round's blast drives the same slide, knockdown and hull exchange
  (`mechs-nouns.md`, Blast impulse).

  Knocked down, stalled, staggered and stunned are one short **can't act**
  state (`StaggerService`): no move, no fire, for a fixed time, never a
  casualty state. The swarm's own bodies are never struck. Squad mates of a
  thrown body take a near-miss morale drain. The numbers (charge speed, band,
  cooldown, tell, masses, every hold time) are starting values in
  `BruteChargeRules`, to be felt out in play rather than settled on paper.

  The charge is shown two ways, all presentation over the rule (the tell
  itself is the Brute's own wind-up and its sound; nothing is drawn on the
  ground for it, because a charge cone is a game-UI tell the battle does not
  show, owner, 2026-10-22):
  a knocked-down body's picture lying as a fallen one does for that second
  while the sim keeps it upright; and the squad card's status word naming the
  cause (knocked down, stalled, staggered, stunned) ahead of engaged or
  wounded. Nothing is drawn on the ground under a body that can't act, no ring
  or arc on a Marine, a hull or a mech (owner, 2026-10-21): a body thrown and
  getting back up says what happened.

  Like the Runner it is drawn on its own skeleton (`RavBruteRig`): a plum,
  plated rhino-ram with a mace head on four boar's legs, the hooves under the
  body and the shanks over them. It walks, trots and gallops with the hips
  and shoulders swaying against each other; it plants its feet to stand, to
  brace for the tell and while it can't act, and gallops through a charge with
  its head down. Its back gives: the body is drawn as front and rear halves
  that meet in the middle of the back, and through a turn the spine bows into
  it, stiffer and slower than a Runner's, the halves hinged at the outer edge
  so they never open a gap. A blow is the whole body's, timed off the attack cooldown as
  the Runner's arms are: the weight goes back onto the hind legs and the
  forequarters rise, then it drives forward through the shoulders, the front
  hooves stamping down ahead and the head driving with them; a charge's hit
  plays the same drive. Stunned, it shows it in its body rather than with a
  mark: the head hangs and shakes slowly side to side and the shoulders
  wander. Dead, it bursts as a Runner does, through the same
  `RavBurst`: the body splits between the shoulders and the hips and the halves
  slump apart, the head and one or two legs come away, over the Runner's red
  ichor, at full colour.
- **Spitter** — fragile, keeps its distance, lobs acid. It backs away from any
  ground enemy that closes inside its charge radius and does not lob while it
  runs. Otherwise a low hostile craft in reach whose skin its acid can bite
  comes first (see *Air threat ladder*), then the sensed enemy with the most
  others inside the splash; the glob is a real arcing
  projectile whose splash goes through the ordinary detonation path, spares the
  swarm and is stopped by an intact roof. Acid on a low flier lands on the hull
  through the aimed-damage path; a craft at cruise altitude is out of reach.
  It stops advancing as soon as something is in range, so the Runners it came
  with close past it and it ends up behind them. No damage over time: the
  damage model has none for ground bodies. Its snout glows faint acid green
  through the brace before each lob, so a player reads the lob coming off the
  creature itself (owner, 2026-10-25).
- **Matriarch** — one per mother bore at maw tier: large, very durable, a slow
  walker that runs down infantry, wrecks emplacements. Answered by mechs, heavy
  weapons or a lot of satchels.
  The mother owes it the first time it releases at maw tier, and that body is
  the Matriarch whatever the strain roll; it is never drawn otherwise, and its
  emergence is the `matriarch_emerged` beat. About four and a half cells nose
  to tail, plated a mech's weight: rifle fire skips off. It comes out roaring,
  then hunts as a Brute does (an emplacement first), and every blow is an
  action that holds the body and lands part-way through, so it is seen coming.
  Infantry is reaped: one scythe sweeps across its front, right then left on
  the next, cutting a handful in the arc and throwing whoever lives through it
  down and across the sweep. An emplacement, a hull or a mech
  takes the crown. It breaks walls a Brute cannot (a fort's buildings, not its
  curtain until something has worn that down), from further off, and never
  walks round to a door. It is not staggered and does not charge.

  It has two paces: it **walks** at its sim speed, about two thirds of a cell a
  second, which a Marine on foot outpaces, and it **runs** at nearly five
  times that, well past a Marine (`MatriarchRunRules`). A **run** is taken only
  at infantry that has let it come within a band of about eight cells (past its
  reach) over open ground, and once the last run's cooldown has run, so
  standing off from it and walking away early stays a real answer, and walking
  away once it is close is not. It begins with a **tell**, the Brute's in its
  shape: it stands crown-down for a moment and bellows, the player's warning.
  Unlike a charge the run is not a committed line: it keeps hunting its quarry
  at the run's pace, and closes right in before it reaps rather than reaping at
  the edge of its reach, so the scythe lands on a body that kept walking
  through the wind-up. The run ends on that reap, on its time (a few seconds),
  or when it takes other quarry or has a wall to break, and a long cooldown
  follows, in which a thrown marine can get up and walk off again. A hull or a
  mech is walked down: nothing that heavy outpaces its walk by much, and its
  weight against a lance is what the lance loop measures. The `matriarch`
  scene's `walk-off` loop reads it: a marine inside the band walking away at a
  Marine's pace is run down and reaped; in `far-off` one already well out of
  it is never run at and gets away. The numbers are starting values in
  `MatriarchRunRules`, to be felt out in play.

  Its weight is set against a lance: three Bulwarks and one Matriarch at full
  health come out near even (the `matriarch` scene's `mech-lance` loop reads
  it: two Bulwarks lost, the Matriarch left on its last few points), so it is
  a boss a mech lance with support answers and a rifle squad does not.

  Drawn on its own skeleton (`RavMatriarchRig`): the charcoal Siege Crown,
  matte charcoal plates edged in dried-blood red over wine-red sinew, a fan
  of yellowed ivory crown over a straight-ahead head, the legs pillars under
  the body on a slow four-beat walk, which lengthens into a run on longer
  strides with less of each on the ground and the feet lifted higher. Walking,
  it looks slowly left and right; before a run the crown dips and the jaws
  part;
  a reap turns the head toward the blade and opens the jaws; the roar gapes
  and shakes it. The two scythes hang from under the crown's rim, a mirrored
  pair about the head, and are long enough that a reap reads past the crown
  (owner's pick over mounting them further out or turning them with the head).
  It dies with its back broken, in one of four poses of that death.

  Her **damage reads in her body**, never as a flash (owner: a flash reads as
  arcade). **Worn chitin** (`RavWear`) is worn plate made organic
  (`combat-durability-nouns.md`, Worn plate): as her plate is stripped her shell
  is scored and chipped bone-white, then bruised a sickly yellow and violet and
  cracked; as her health goes it is torn open in wet, weeping wounds over the
  sinew. The marks are painted in colours her healthy body never has, so they
  read on charcoal and wine-red at play zoom. It observes and never
  records: her marks are a pure function of her plate's and her health's
  fractions, laid on her body and her head in their own frames, so they build up
  in place, turn with her and grow with her through a moult (which keeps both
  fractions). Every **round that lands** sends up a puff off the rim facing the
  gun, sized by its weapon (`SwarmHitFeedback`): shell dust and a chip or two
  while plate is left after it, ichor once it reaches the flesh. A round her plate
  turns **glances**: a pinpoint spark and a chip of chitin skip off along its path
  mirrored about the rim. A glance is the armour law read, not a rule of its own:
  the share of a weapon's rounds drawn glancing is the share of her rating its
  penetration does not reach (a rifle's half, a squad automatic's two thirds, an
  anti-materiel rifle's or a heavy cannon's none), and a stripped body turns
  nothing. The Brute wears and shows its hits by the same law, on each half of
  its body and its head. The `rav-wear` look suite shows both. The Bombard, which
  has no plate, shows every round as an ichor puff, and its shell wears by the
  same law.
- **Harrier** — the swarm's flier, a winged strain that hunts in **packs** of
  six to twenty (`SwarmFliers`, `HarrierPack`). It is the swarm's answer to a line it cannot walk through: the ground
  swarm is channelled by ridges, passes, wire and redoubts, and a flier is not.
  - **What it is to a gun.** A roster body in the low air, as a drone is (it is
    shot, takes damage and dies by the ordinary laws), with two differences
    from a drone: it is **engaged only by a weapon that can track it**
    (`WeaponDef.tracksFliers`: small arms, machine guns, autocannon,
    point-defence lasers and anti-air; a heavy cannon, a mortar, a rocket or a
    grenade cannot, and its shooter never picks one), and **no ground blast
    reaches it**; only an anti-air burst does. Unplated, 28 health: two rifle
    hits or one flak hit.
  - **What it hunts.** What the line is there to protect, over the line
    itself: a gun or emplacement first, then the radio a forward observer carries, then a supply truck, then a medic,
    then a marine standing alone, and a rifleman in a squad last; an armed
    hull or a mech it only scratches. A target is worth less by every air gun
    whose reach covers it, so anti-air protects a line without shooting
    anything down (`HarrierTargeting`). It senses from the air, inside its
    sense range, as every swarm body senses rather than knows.
  - **How a pack fights.** A pack **loiters** on a ring out of its prey's
    reach (past a rifle's, a machine gun's and a flak mount's), on its own side
    of the prey. Once it has circled a while and gathered it **commits** to the
    place round the body it chose, and **spreads** over it: each member takes
    its own body there, the best one no packmate has claimed, and shares one
    only when there are more divers than bodies (`HarrierTargeting.assign`).
    The members go in **staggered**, each after its own seeded delay from
    wherever on the ring it has reached, so a dive comes from several bearings
    over a second or so rather than as one stack. Each dives low and fast and
    bites once as it passes; one whose body is gone gives its dive up. A bite
    kicks dust off the ground and strikes sparks off the plate, and on a body
    that bleeds throws blood on along the stroke (`ImpactFx.spawnClawStrike`,
    queued by the sim as presentation), and leaves a short-lived stain where it
    landed: a spatter of fine droplets thrown on along the stroke, drawn from
    the fallen-body pool art, that fades over twenty seconds (owner's pick).
    Rarely, a seeded share of bites on a
    marine who lives through them (`HarrierRules.LIFT_CHANCE`), it **lifts** the
    marine off its feet and throws it a couple of cells on along the dive
    (`HarrierLift`): up and down again in under a second, turning over, its
    shadow standing off on the ground, then hurt by the landing and knocked
    flat to skid on as a body a Brute threw is (`BruteChargeSystem.toss`). A
    marine against a wall, with no open ground to land on, is not lifted. Then the
    pack **pulls up**, flying on and climbing, and loiters
    again on the far side. That is the trade it offers the ground: a committed
    pack is tight and low, so one anti-air burst catches several (a hit bursts
    among the packmates round it); a pack that lost a third of itself on a dive
    **splits** in two, and the halves come on separately and bleed the line
    slowly. A pack escorting a Matriarch loiters over her with no prey in
    reach, and scatters into fliers of one when she dies.
  - **Carrying feed** (`HarrierCarry`; the owner's "some carry for feed",
    2026-10-25). A body a Harrier's bite kills lies loose, as a dropped load
    does (*Harvest*), for anything of the nest's to fetch. A pack with nothing
    to hunt, every member hatched, sends its member nearest the nearest loose
    body within twenty-two cells (a marine it killed, a colonist, or one of the
    swarm's own dead) to fetch it: never more than one in three of its number
    at once, and never a patrol already on its way home. The carrier swoops
    low, takes the body up, and hauls it low and slow (three cells a second, at
    about a third of its height, within reach of rifles) to the nearest living
    bore, where the harvest feeds it in down the mouth as any load; then it
    flies back up to its pack. A carrier never bites and is no part of its
    pack's dives, but it is still one of the pack: shot, drawn and heard, and a
    carrier shot down drops its load where it falls. So a line that kills the
    carriers keeps the nest from feeding on its dead. Scene: `harrier-carry`;
    look: `harrier-carry-look`.
  - **Snatching the living** (owner, 2026-10-25: Harrier bands picking off
    the colony's survivors). A colonist out in the open, no roof over them, is
    taken alive: a pack with room for another carrier sends its nearest
    member down on the nearest within twenty-two cells, whether or not it has
    prey of its own, ahead of any dead. The carrier snatches them off their
    feet and hauls them home as a dead load is hauled, and at the mouth they
    are taken as an impaled body is (`SwarmHarvest.snatchByFlier`). A
    Harrier never bites a colonist, and never takes a soldier or militia
    fighter alive, nor an evacuee under escort (*Harvest*). Shot down on the
    way, the carrier drops them alive where it falls, both legs run through,
    and they lie a moment before running again, untouchable by the air until
    they do. Scene: `harrier-snatch`; look: `harrier-snatch-look`.
  - **How it flies.** Its own steering, never the ground flow field, which is a
    map of walls a flier is not in: seek a goal, push off packmates, turn at a
    capped rate, move along the nose. Serial, in pack and slot order, with no
    random draw, so a replay flies the same; a pack of twenty costs four
    hundred distance checks a tick.
  - **Where it comes from** (`BroodSpires`). Only under the flier law: off a
    satellite's **brood spire** with its bore's surge (an old bloom's, or one a
    fed bore raised), and, in an old bloom, as a Matriarch's **brood**, a pack of six that hatches off her back as she
    emerges and flies as her escort (*Emergence*). So the satellites and the front bores
    send free hunters and the mothers send escorts.
  - **Look.** The lilac glider (`HarrierLook`, the owner's round-one pick):
    the Runner's lilac carapace with bat wings grown from its spike arms.
    It animates frame by frame (`HarrierPose`): an eight-frame wingbeat whose
    first frame is the glide, flown in short bursts while it circles and
    steadily, faster, as it climbs out; two frames sweeping the wings back into
    the dive as it goes down, and the same two opening them again as it pulls
    up. The dive keeps its wings showing, swept back along the flanks and still
    open (owner). The wing frames carry only the thorax; the rest of the body is
    laid on in segments that move with the flight (`HarrierBodyRig`): the waist
    bends through a turn by the same spine law as a Runner's or a Brute's back
    (`SpineBend`), three tail segments trail it, each lagging the one before and
    swaying with the stroke, the head scans about the prey while it circles and
    locks on what it dives at, and the whole body pumps a few percent with each
    stroke, settling on the upstroke and lifting on the downstroke. Drawn over
    the roofs, larger the higher it flies, with its own silhouette cast down-sun
    as its shadow, nearer under it as it dives. The height it is drawn and
    shadowed at is one smoothed value (`HarrierAltitude`) that follows the
    flight's own on a damped spring with its climb capped, so the shadow glides
    in under a diver rather than jumping; the pump is only a small wobble on it.
    Flak, reach and the dive read the flight's own height.
  - **How it dies.** A shot flier falls (`HarrierFall`): carried on the way it
    was flying while its speed bleeds off, dropping faster as it goes, tumbling
    through limp-winged fall frames, its shadow closing in under it until the
    two meet on the ground; hit higher, it falls longer. Where it lands its body
    lies, as one of two corpse drawings with wings sprawled and crumpled, in a
    Runner's ichor splat. From then it is a body like any other Rav's: the
    ordinary corpse of the unit that died, moved to where the fall landed and
    kept undrawn until then, so it pools, melts into the creep and counts
    against the body pile by the same laws.
- **Skitterer** — the nest's worker (`SwarmWorkers`, `SwarmWorkerBehavior`; the
  owner's idea, 2026-10-25: "not combat focused and more a slightly passive
  worker around the nest"). It gives a nest life between surges, and every job
  it does is one the company can see and stop by killing it. It never fights.
  - **What it is.** A small Runner-family body, about two thirds a Runner's
    length, drawn on the Runner's skeleton with its own bones: no spike arms,
    a pair of short pincer mandibles in their place, a broad back for loads.
    Unplated, 10 health (one rifle hit), a little slower than a Runner. It has
    no growth stage: every skitterer is an adult. Colonists call them ticks.
  - **Where it comes from.** A living bore **broods** skitterers out of its
    mouth on a clock of its own, never in a surge: one every twelve seconds
    while it has fewer than its **brood**, which is one at fissure, two at
    bore and three at maw, two more for a mother. The clock starts with the
    bore's first surge, so a fresh bore's first act is to fight. A sleeping
    bore broods too (a nest asleep is still busy). An old bloom's nests are
    full at setup: when the creep is matured each living bore stands with its
    whole brood round its mouth. A skitterer belongs to its bore; when that
    bore collapses it keeps working for the nearest living one, and with none
    left it only flees and mills.
  - **What it costs the swarm.** Nothing out of the fight. Skitterers are
    outside the director's population target and its live weight, and outside
    every surge's budget, so no combat body is held back for one; the brood cap
    is their whole price, and at most a handful stand round each bore.
  - **What it does**, decided serially for each skitterer about once a second,
    the first that applies:
    1. **Flee.** A marine or an ally on the ground within seven cells, or a
       hurt of its own in the last three seconds: it drops what it hauls and
       scatters, running directly away from the nearest threat for a few
       seconds, then back toward its bore once nothing is near. Nothing else it
       does is worth a shot; a nest under fire empties of workers and fills
       again when the fire stops.
    2. **Haul.** A loose body (*Harvest*: one of the swarm's dead or a load a
       carrier dropped) within its bore's creep reach, or sixteen cells of its
       bore where the bore has none: it goes to the body, takes it up as a
       carrier does and drags it to the nearest living bore at seven tenths of
       its own pace. Delivered, it feeds the bore through the feed seam as any
       carrier's load does and counts the same (a Runner is recycled, a
       colonist delivered). So Runners stay in the fight and the dead in front
       of a nest still go home; killing haulers starves the bore. A skitterer
       never seizes a kill (it makes none) and is never a scavenger.
    3. **Repair.** A structure of its nest below nine tenths of its structure:
       its bore, a spore colony or outpost of the bore, a ground colony the bore
       grew, or the bore's brood spire. It crawls onto the body and restores
       eight structure a second while it stays there, two skitterers at most
       on one body, the worst-hurt first. A half-killed structure therefore
       heals back over a minute or two unless its workers are cleared; it is
       drawn wounded again only while it stands under half. A collapsed bore
       and a dead growth are never mended.
    4. **Tend the spire.** Its bore's brood spire is waiting out its gap
       between packs: it carries a feed from the mouth to the spire. Each
       skitterer that reaches the spire takes eight seconds off the spire's
       wait, down to a floor of twenty seconds between packs, then walks back
       to the mouth for the next feed. A tended spire launches on more of its
       bore's surges.
    5. **Grow.** Ground its bore's creep still claims but fire has burnt
       (`fire-nouns.md`, Firebreak): it goes to the nearest such cell to its
       bore and works it for four seconds, which ends the refusal there and on
       the cells beside it, so the creep fills them again and they are fuel
       again. A burn left alone is crept back over from the nest outward, a
       few cells a worker, and holds only while it is burnt again or its
       workers are killed. A worker never works beside a cell still burning.
       Failing that, it **strips a tree** (owner, 2026-10-25: "harvest trees
       and other organics"): the nearest living tree in reach whose crown the
       creep has reached, two workers at most on one, each taking three
       points a second off what the creep still has to climb before the tree
       dies (*Trees die under it*), so a worked tree dies in about half the
       time; a worker there when its tree dies carries the pulp home, a
       quarter of a colonist's biomass through the feed seam. Failing that, a
       pending outpost of its bore (*Creep carries the umbrella outward*): it
       stands on the cell, and every second it does counts the outpost's hold
       twice over. Failing that, it mills round the mouth, feeding the bore's
       creep a second of growth for every ten it spends there, so a busy nest
       visibly spreads.
  - **Sound and look of the work.** A hauling skitterer draws as a Runner
    carrier does (backing toward the bore, the load latched in its mandibles);
    standing at a mend, a burn, a tree or an outpost it crouches with its
    head down and pecks at it, each body to its own rhythm; one going to the
    spire carries a glob of amber feed in its mandibles, and one coming home
    from a tree a wad of pulp (`load-feed`, `load-pulp`). It has no
    cry and no strike; it is heard small and busy (Sound, below): a quick
    chitin patter, a thin squeak when hurt or scattering, and a soft wet
    working bed while it is head-down at its work. Dead, it bursts as
    a Runner does and the creep digests it.
  - **How the company treats it.** An ordinary body on the swarm's side, shot
    as any other; clearing a nest of its workers is the player's call.
  - **Its look** (`art-source/rav-redesign/worker`): the hauler, a squat back
    with a cradle of ivory ribs where loads ride, one amber node and short
    hooked mandibles, painted geometry-first over the Runner's own body layer
    and cut to its bones (the owner's pick over a tender with an amber gut sac
    and a mite with a round pale shield; `swarm-worker-look`).
  - Control: `-Dbattle.swarm.workers=false` broods none. Scene: `swarm-workers`
    (a verdict per job, the speed-ups each against a control). Look:
    `swarm-worker-look`.

**Growth stage.** How old a rav is: **whelp**, **adult** or **elder**. Every
strain has the same three stages, so a stage is a property of one body, never
a strain of its own: a whelp Runner is a Runner, with the Runner's `UnitType`,
behaviour and rig, and no stage adds a `UnitType` or an appearance family. The
stages are about a fifth apart in size (0.80, 1.00 and 1.22 of the adult), so
they read at play zoom. Health, armour, damage and reach rise with stage and
speed falls; a Brute's mass rises with its volume, so its charge physics scales
without a rule of its own: the momentum it brings sets how far it throws and how
long it floors infantry, how hard it rams a light wall and how many bodies it
runs through (a whelp's charge stops on the first, an elder's goes through
three), and the weight class it meets shifts with its size (a whelp meets an
APC as an adult meets a tank and is stunned, an elder staggers a heavy mech).
A Runner's stage sets its harvest: a whelp never impales and drags slowest, an
elder impales most often and drags fastest. They are also marked beyond size: a whelp is pale
with nub spikes, an elder dark and scarred with long ones and a pale spine
ridge. A stage is issued at
spawn through `SwarmStrains`, like the Brute's plate, and fixed for the body's
life; the Matriarch is the one exception. Stage scales presentation only
through things the body already has: its rig's scale and stage-recoloured
sprites, and its voice pitched by its size. The numbers are in `SwarmStageStats`
and the reasons for each choice, with the first balance reading, in
`growth-stages.md`.
`-Dbattle.swarm.growthStages=false` is the control: every body an adult.

- **Age mix.** A surge's stages come from its bore, never from the director:
  each body draws a stage from a table keyed by the bore's tier (a fissure
  throws whelps, a maw throws adults and elders), shifted older by the biomass
  the bore has eaten in its life. So *Harvest* shows on the field: a bore that
  has been fed sends bigger, darker ravs, and fewer of them, because the
  director counts a body's stage weight against its target. The draw is keyed by the bore, the
  pulse and the body, so stages change no other roll.
- **Pack, pack leader.** A pack is the bodies of one pulse, at most eight: a
  bigger pulse splits into as few even packs as fit. Its first elder is
  its **pack leader** and is released at the head of the stream; a pack with
  no elder has no leader and none of a leader's bonuses, though it still runs
  together (*Pack movement*) round its first living body, its **anchor**, which
  is a place to move round and nothing more. While the leader lives, members on its eight-cell leash share its
  quarry when they have none of their own close by and have not been peeled
  off onto other prey (*The press, sub-packs*; local pursuit; the flow field is
  untouched), pool what any of them senses (still sensing, passed body
  to body, a tick behind), and strike 15% faster. The leader's cry on new
  quarry is the pack's: a member taking the leader's quarry stays quiet. When
  it dies the pack **breaks** for good, heard as one scatter shriek at the
  leader's stage: the bonuses go at once, members drop their quarry and for two
  seconds hunt only what they sense themselves, and its whelps bolt back down
  the bore field for four, dropping what they carry. A pack never elects a new
  leader; a body out of any led pack (a leaderless pack, a broken one) joins
  a leader whose leash it comes within if that pack has room for it, which is
  how two surges merge.
  `-Dbattle.swarm.packLeaders=false` is the control: no pack forms.
- **The Matriarch's stage** is set by how long the mother bore has been
  feeding (its lifetime biomass when she emerges), within a narrower range of
  size than the small strains'. Because she is fed from her mother, she steps
  up a stage in the field when the mother is fed past the next threshold.
  She leads every pack within her leash: an elder that comes within it defers
  to her and brings its pack, so killing her breaks them all at once.
- **Clearance.** Every stage of the small strains keeps a sim radius under half
  a cell, so the shared flow fields serve every body; an elder Brute is drawn
  and shoved larger than it navigates, and squeezes through a door it fills. The crowding capsule scales with stage.
  The Matriarch paths for herself at her stage's clearance.

**Air threat ladder.** What the swarm can do to what flies, rung by rung:

- **Light air: Spitters.** Mobile, everywhere the swarm is, and only good for
  light air. A drone is an ordinary unarmoured body and takes the ordinary lob.
  A craft takes acid only while it flies low and only if the acid can bite its
  skin: acid is a corrosive, not a penetrator, so its penetration is a third of
  an airframe's skin rating and an intact skin turns it. A Spitter does not
  waste lobs on a craft it cannot hurt, but finishes one whose skin something
  else has stripped.
- **Armoured air: spore colonies.** Gunships, dropships and warship-class
  overflights shrug Spitters off. What denies them is static: the colonies a
  bore sprouts, whose penetration and reach climb with the grade of the tier
  that grew them, so only a bore left to reach maw can hurt the heaviest craft.

The ladder is read from durability, never from a list of craft: whether a rung
reaches a flier is the shared armour law against that flier's own skin. Today
every airframe carries the same skin rating, fighters included, so a fighter is
armoured air until the air model gives light airframes a lighter skin; when it
does, they fall onto the Spitters' rung without this rule changing.

Strains are added to a bore's mix by tier, not chosen per wave by hand.
Every strain is the same swarm to everything outside it: one side, one
pressure role, one sensing law, one strategic move toward its quarry
(`SwarmPressureBehavior.pressToward`, the call a flow field speeds for all of
them at once). What differs is what a body does once it has a target. Every
strain body enters the battle through `SwarmStrains`, which issues the
strain's kit (the Brute's plate), so a director, a scene and a debug mission
spawn the same Brute.

A body is carried as itself. A corpse is an entity with its own position and
facing that the renderer reads every frame, and a layered swarm body is drawn
as parts, so a carrier (*Harvest*) moves the colonist's corpse by moving its
position, and the body draws where it is; neither the strains nor their
corpse sheets assume a body stays where it fell.

**Creep.** The biological growth living bores spread across the colony:
streets, walls, buildings. It is what makes an ordinary settlement look
unmistakably wrong, and it is the only ground the hive's static growths stand on
away from a bore's mouth. A ground colony (anti-infantry, anti-emplacement)
roots only on creep. Creep recedes when its bore collapses. Mobile strains never
gain ranged attacks to answer fortifications: static growth on creep does that.
Its look is the owner's pick (build 1 of the hard-edge studies) and is costed
against its own control in `renderEvidence`.

- **Creep is a field the battle owns** (`CreepField`): per cell, how much
  growth stands there, whether it is alive, and how far it is from the source
  that reached it (the pulse runs along that). It belongs to one simulation and
  is deterministic in the seed.
- **Sources spread it.** A source is declared at a cell with a growth (reach
  per second, the reach it stops at; bore tiers have presets) and may be fed,
  which speeds it for a while. It claims what lies within its reach by path
  cost along walkable ground, weighted by seeded noise so the front runs out in
  fingers, dearer on pavement. Until bores stand up on eruption sites, each opened site is itself a
  source that never dies.
- **The creep is slow enough to fight ahead of** (owner's pick). Every source
  walks its front out at half its tier preset's rate, and a fed bore at one and
  a half times that rather than three times (`-Dbattle.creep.spreadScale`, 0.5;
  `-Dbattle.creep.feedMultiplier`, 1.5; 1 and 3 are the old pace). In Colony
  Breach that is about four and a half minutes from the first bore until two
  fifths of the map has turned, against two and a half before, and it is the
  feeding that set the old pace: bores are fed nearly without pause and reach
  maw on biomass whatever the rate, so the base rate alone bought half a
  minute. The dials set how soon the ground turns, never how far the creep
  goes, how fast a claimed cell fills, or how long burnt ground refuses it.
  Matured creep is grown whole at setup, so on the Cordon and the heart they
  move only what grows in the battle: a bore's new tier, a satellite opened
  late.
- **Trees are grown over, never only climbed.** A tree's crown cells are growth
  the creep crosses, cell by cell at three times the ground's cost, so a grove
  is overgrown from its edge inward and its interior and far side are reached
  over it rather than left a green island the creep cannot enter. The battle
  declares its trees' crowns to the field when the field is made; rock
  formations and hills are climbed as walls are. A crown fills at a wall's rate,
  so its coverage is how long the creep has held the tree.
- **Creep can be matured.** A battle set weeks into a bloom (the Cordon,
  `cordon-nouns.md`) grows every source to its full reach at once at setup and
  fills it, walls crusted, as the clock would have (`CreepField.mature`); the
  trees under it die at once and the roofs it stands under cave in
  (`CreepRoofCollapse.matureOn`), as weeks under it would have brought them
  down. Nothing else about the field changes. The heart's country is grown this
  way too (`heart-nouns.md`).
- **Removing a source withers what it alone held**, from the outer edge back to
  the hole over a visible interval: the growth dies first, then clears. Ground a
  living source also reaches stays, and a living source reaching dying ground
  revives it.
- **Walls are held slowly.** A wall beside claimed ground is climbed, never
  crossed, and filled over about a minute and a half rather than at once: its
  coverage is its age, so a newly reached wall carries only veins and an old
  one is crusted over. It reads as a matter of time. An overgrown wall still
  reads as a barrier: its crust is lighter than the floor's, lit and shaded on
  its edges as a raised block is, and outlined dark where it meets the floor.
- **Raised ground stays readable under it.** The creep grows over plateaus and
  valley floors as over any ground, but it stands back from bare rock: a cliff
  face, a brook's bank or a ramp's cut walls (`CliffFaceMask`, the rock the cliff
  art draws). Its own beaded edge runs along the lip and the foot, the rock shows
  between with no wilt on it, and the wall-vein lattice and a thin membrane are
  laid back over the rock where creep stands on both sides, so the face reads
  as rock the creep is climbing (owner's pick, over bare rock and over creep
  covering the face). A ramp's floor is not rock: the creep runs up its steps
  between the bare cut walls, so a ramp shows as a creep-filled break in the
  cliff, a hint to where the way up is (owner).
- **It stops at the water's drawn edge.** Creep never grows on water, but the
  shore is drawn as a contour rather than the cells' squares (a **shore**,
  `battle-render-nouns.md`), so the creep's edge follows that contour instead:
  it runs over the corners the contour cuts to bank, stops short of those it
  floods, and leaves the wet band showing between it and the water
  (`ShoreWaterMask`).
  A ford is drawn as shallows over a stony bed, and the creep's body is kept
  off it like the water's; where the creep holds a ford it lays only faint
  veins and a thin film over the bed, as over a cliff face, while open water
  takes nothing (owner). That is the picture alone: the ford is still a
  firebreak.
- **Trees die under it** (`CreepVegetation`). A tree whose crown living creep
  has held to the tree's own seeded share of about a minute and a half of
  climbing (less what skitterers have stripped off it) is dead, and stays dead if the creep later withers. A dead tree has
  dropped its leaves, so its crown no longer hides or shelters anyone (sight
  passes and it gives no edge cover) while its trunk still does both and its
  tangle of bare limbs still stops a body walking through (owner, 2026-10-22,
  over a shrouded tree that went on blocking as the living one did). A crown cell two trees share opens
  only when both are dead, and never if it is either one's trunk. Felling still
  works on a dead tree, and a dead tree is undergrowth: a heavy tank pushes it
  over, trunk and all, where no living tree's crown holds its cells, so the
  infection turns a wood a tank could not enter into one it drives through
  (owner; `convoy-nouns.md`, heavy tracks). Creep therefore strips a wood of concealment the way it
  strips a building of its roof. `creep-vegetation` (`sceneEvidence`) holds the
  mechanics; `-Dbattle.swarm.creepTrees=false` is the control.
- **Hedges and crops are taken as grass is.** Vegetation in a battle is trees
  (props with a crown) and the green ground: grass with the plants dressed on
  it (shrubs and tufts), crop rows of grass beside turned earth, and the
  country's hedges, which are shrubs grown along a plot's grass headland. None
  of the green ground blocks a body or sight or gives cover, so creep changes
  how it looks and nothing about how it fights: it wilts ahead of the lip and is
  buried under it. A hedge that sheltered would be a natural prop with a crown,
  and creep would take it as it takes a tree.
- **A ground colony answers a gun the swarm cannot rush** (`GroundColonies`).
  A living bore grows one on its creep within reach of a hostile emplacement
  the creep has come up against: on living creep with creep round it, inside
  the bore's own creep reach, spaced from every bore and colony, once the cell
  has stood good a while, and up to a cap by tier (1, 2, 3). It is a gun of the
  swarm's side: spines driven up from below on whatever stands in its short
  reach, indirect, with a small blast that also breaks walls beside the target,
  so a gun behind a wall is not safe from it. It dies with its bore, and as soon
  as the creep under it is no longer living. **Its strike reads from end to
  end** though it travels underground: in the half second before its spines
  are ready, while it has a target, the body clenches and quivers and its eye
  comes up to full, and on the shot it lashes out; the run is a ripple heaving
  through the creep from the colony to the strike, opening a jagged crack lit
  by an amber seam behind it; at the strike the ground heaves and splits and a
  crown of bone spines, crimson at the root, bursts up, holds a moment and
  sinks back, and the crack and the heave fade over a second or so after. So
  the player sees which colony struck and where from, as well as where it
  landed. Its dust is the creep's violet, not concrete grey (`ColonyThreatFx`;
  `-Dbattle.render.colonyThreat=false` is the control for both colonies'
  looks, costed against it in `renderEvidence`). `ground-colony` (`sceneEvidence`)
  holds the mechanics; `-Dbattle.swarm.groundColonies=false` is the control.
- **The creep digests the swarm's dead** (`CreepDigestion`). A strain body
  lying on living creep sinks into it over about twenty-five seconds, drawn
  smaller and fainter after its first third, and is gone; a body off the creep
  keeps what it had digested and waits for the creep to reach it, and a body
  being dragged is lying nowhere. Only the swarm's own: Marines, militia and
  colonists are the harvest's. It is what keeps a long battle on the creep from
  piling up corpses, and it gives nothing back to the field.
- **Body pile cap: a safety net where nothing clears the dead** (`BodyPileCap`).
  Off the creep and out of a bore's reach — waves thrown at a defended redoubt —
  nothing takes the swarm's dead away, and every body lying is drawn every
  frame: a dead Runner is some thirty quads of burst pieces, so a pile of 1,200
  cost about seven milliseconds of frame (`renderEvidence`, `body pile`). The
  battle counts the bodies still lying by 8x8-cell area once a second; past 64
  in one area (about one a cell, a carpet) the oldest there settle and fade out
  over four seconds and are gone, and past 1,000 on the whole field the oldest
  anywhere go the same way. Every side's dead count, the oldest first by death
  tick and then id, so it is deterministic. A body on a carrier — dragged,
  impaled, being harvested — is never counted or taken, and one taken up
  mid-fade lies whole again; a retired Runner leaves the swarm's recycling with
  it, and the corpse itself stays for the battle's reckoning of the dead. The
  limits are generous on purpose: it is not a mechanic, and a battle the creep
  or the bores keep clean never reaches them. `-Dbattle.bodyPileCap=false` is
  the control; `body-pile` (`createSnapshots`) shows a pile capped against it.
- **A settled body is drawn from a recording of itself** (`SettledBodyMeshes`).
  A burst Runner or Brute stops moving once its burst has settled
  (`RavBurst.settled`, two and a half seconds after death): its pieces lie
  where they came down, its pool has spread, its ichor has landed. From then on
  its composition — the same quads the burst was drawn with, in the same order —
  is recorded once in the body's own frame and drawn every frame as one command
  (`QuadMesh`), scaled by the framing and by the melt or pile-cap shrink and
  faded by their alpha, so creep digestion and the cap's fade read exactly as
  before. Nothing about a body is decided by the recording; it only stops the
  renderer re-deriving thirty-odd placements a body per frame for a picture
  that cannot change. A body still bursting, and every other corpse, is drawn
  as it was. `-Dbattle.render.settledBodyMesh=false` is the control.
- **Creep favours the swarm and shelters no one** (`CreepEffects`). A swarm
  body runs a quarter faster on living creep; Marines and colonists are never
  slowed, so creep reads as the swarm's ground rather than mud. A fleeing or
  wandering colonist may cross creep but never stops on it: creep is no refuge.
- **Creep brings roofs down under its weight** (`CreepRoofCollapse`). A
  building's roof gives way where living creep has stood beneath it for a minute
  to a minute and a half: first beside a wall the creep has crusted, then inward
  a cell every few seconds from each roof already down (to creep or to a shell),
  each cell on its own seeded timing so a building sags open rather than falling
  in lockstep. A cave-in is the same one war damage makes: rubble on the floor,
  and the roof, sight and cover follow it. Creep that withers stops the count;
  a roof that fell stays fallen. The battle keeps its own clock per cell, so the
  creep field knows nothing of roofs. An infested building is no shelter twice
  over: no colonist stops in it, and given time it has no roof.
- **Fire burns creep and holds it back** (`fire-nouns.md`, Firebreak). Creep
  is fuel, living or dying; creep under a fire crisps away in seconds, and
  burnt ground refuses the creep for about three minutes before the sources
  that still claim it fill it again. Creep over a road makes the road burn.
- **The look is the field drawn, never a second model.** The creep is a hard
  organic edge, not a spill: an opaque surface of grown chitin plates over a
  membrane, rolled up into a raised, beaded lip where it stops, veined, with a
  pulse running toward the source and a short shadow of its own. It is drawn
  inside the ground's relief composite, so it moves with the ground's parallax
  and lies under the walls' shadows. Walls go through four stages with age:
  veins, then crust from the foot, with a dark crease where creep meets the
  wall. Dead growth is a grey husk. Trees go through four stages with the
  creep's hold on the crown, cross-faded so a tree turns rather than pops: veins
  reaching in from the crown's rim while the leaves start to turn; a crust
  rolling in from the rim with the creep's beaded lip while the leaves brown;
  the crown withering, its dead leaves falling from a bare skeleton; and the
  dead tree, a snag of weathered grey-brown limbs, fissured and silvered on
  their lit ridges, reaching unevenly from a crusted trunk toward the crown's
  outline, some snapped short, a few dead leaves caught in them and veins
  running out along them (bleached, smooth and evenly radial, they read as
  bone), drawn with a
  dark outline over a soft ground shadow so it stands up off the dark creep at
  play zoom. The skeleton is grown by the bake rather than taken from the
  damaged-crown art, whose few broken limbs vanished against the creep. The stages are a
  sheet baked from the canopy art and the creep's own surface and palette
  (`gradlew.bat bakeCreepBlight`), so the crust on a crown is the crust on the
  ground. A dead tree felled, by heavy tracks or by fire, lies flattened: the
  destroyed tree's stump and the butts of its limbs snapped off short, in the
  same dead bark, the stump crusted, so it reads as ground a vehicle drives over
  rather than a fallen tree still in the way; it is baked the same way from the
  destroyed-tree art (a sheet of its own, `forest-blight-felled`). A tree felled
  while it lived keeps its green felled art. On vegetation (grass, crops and the plants on them) the edge is not
  clean as it is on pavement: the creep's coverage spread a few cells out turns
  the grass a dry, greyed straw (the palette's wilt colour) and veins reach out
  into it ahead of the lip, so grass and crop rows are seen to be taken before
  they are covered; turned earth and pavement keep the hard edge. One shading model serves the game (a shader)
  and review frames (its CPU mirror), from one surface texture generated in
  code. Colour is data: the swarm owns no red, and the default creep is a dull,
  dark, desaturated violet-grey that sits under the lilac creatures without
  competing with them; a bore's heaved earth takes the same colours, so the
  bore reads as where the creep comes from.

**Swarm goal.** A place the swarm is drawn toward: the shelter, the lift, the
nearest Marine body. Swarm movement toward a goal is a shared **flow field** per
goal rather than a path per body, which is what lets the swarm be large on a
large map.

- **Prey** is the goal a hunting body descends when its target is far off: every
  combatant the swarm is hostile to, less the shelter guard while the shelter is
  sealed. It is gathered from the battle each refresh, so "nearest" means
  nearest by the way the ground runs, not as the crow flies. Civilians are never
  prey sources; a body that has found one chases it directly, and a Runner finds
  colonists while harvest is open.
- **Shelter** and **lift** are disclosed, so the mission declares their cells;
  the swarm knows where they are without sensing them, as the noun's law allows.
- **A barricade costs, it does not block.** A militia barricade piece is
  crossed by the field at the cost of about a dozen cells, cardinally only, and
  a descent stops in front of it; a body there strikes it. Close in, a body the
  field leads through a barricade follows the field rather than its own path,
  because its own path would take it round and the two would turn it back and
  forth.
- **Bore** is the goal of a body carrying biomass home: the nearest living
  bore. The swarm's own holes, so their cells are declared and redeclared as a
  bore opens or collapses.
- **A field charges for fire.** A step into a burning cell, or one beside it,
  costs the fire's route multiplier, so the swarm streams round a burning
  stand where it can and through it only where its goal lies beyond
  (`fire-nouns.md`, Pathing).
- **A field makes a stream.** The descent is the same from every cell and has
  no crowd term, so bodies descending it each for themselves fall into one lane
  and run it single file. That is the river a surge makes leaving its mouth,
  and it is also why, without packs, Runners crossed the map as beads on a
  string. Packs are what turn the stream into hunters once it is out.
- **Pack movement.** The field stays the strategic layer; a pack is how its
  bodies run it. The pack's **lane** is the prey field's descent from its
  anchor, and every member runs that lane at its own place in a loose fan:
  out to one side or the other by rank, swept back by rank, weaving about its
  place, each at its own stride and pace, so no two lope in step. The fan
  narrows to the room the lane has ahead of it, so a pack funnels to a file
  through a street or a door and opens again beyond it. Near its quarry
  (the anchor's target, once there is room to spread) the pack **hunts**:
  it **stalks**, the centre slowing to a standoff while the outer ranks swing
  wide to either side of the quarry; it **feints**, the centre darting in and
  falling back once or twice; then it **rushes**, staggered, flankers first
  from the outside in and the anchor last, each member from where it stands.
  Losing two members while it stalks or feints provokes the rush at once, and a
  member whose own victim is right in front of it goes for it. A member more
  than the leash from its anchor runs alone until it is back on it; a broken
  pack's members run alone until a leader takes them in. Pack movement is
  Runners'; Brutes, Spitters and the Matriarch keep their own gaits, though an
  elder of any strain can lead and anchor. `-Dbattle.swarm.pack=false` is the
  control: packs still form, lead and break, but every body moves alone
  (`-Dbattle.swarm.packLeaders=false` forms no packs at all, so nothing moves
  as one). The `swarm-pack` scene asserts the
  mechanic (spread across the pack's width, no long file on open ground,
  stalk-feint-rush in order, approaches from more than one side, the break on
  the leader's death); its cost is in `swarm-movement-cost.md`.
- **The press, sub-packs** (owner, 2026-10-22: "retain movement and possibly
  break off sub-packs onto other targets"). Once a pack is on its quarry it
  fights as a press rather than a heap. A body has room round it for only so
  many Runners to strike at once, its **strike slots**: the ring round its body
  a Runner's width apiece, four round a marine and more round anything
  bigger. Two packs on one body share its slots. The members holding them
  **hold**: they wheel round the quarry inside their reach, lunging in and
  easing out, biting as they pass; nothing in the press plants. The rest
  **wait**: they circle a wider ring the other way, weaving in and out. A
  holder's turn ends after a second and a half while others wait; then the
  longest-waiting darts in and the holder backs out to the ring. A member joins
  the press once it is committed to the rush, or whenever its victim is right in
  front of it. A pack with more on its quarry than the slots and one in reserve
  peels the surplus off as a **sub-pack** onto the nearest other prey it can see
  from there (another marine, a squad's flank, a gun), never fewer than two and
  no more than that quarry's slots and a reserve, with the member nearest that
  quarry as its **lead**, whose cry it is. A sub-pack stays in its pack
  (membership, leader and break are unchanged) and runs at its own quarry, not
  on the pack's lane or hunt; it **re-merges** the moment its quarry dies, is
  the pack's own quarry, or it is down to one body, and the pack may peel
  another off its surplus a second later. The press is a pack's: a Runner in
  no pack still plants on what it strikes. `-Dbattle.swarm.packSplit=false` is
  the control: every Runner in reach plants and the whole pack takes the
  leader's quarry, which is the pile-on the owner saw. The `swarm-pack-crowd`
  scene asserts the mechanic (the surplus on a saturated quarry hunting other
  prey within three seconds, no sub-pack of one, no Runner in the fight still
  for more than a second and a half, re-merge on the tick its quarry dies).
- **Strategic movement and local pursuit.** A body whose target is more than a
  few cells away moves down its goal's field; within that range it runs its own
  short path to the one victim it has chosen. Every strain moves through the
  same call, so a change to how the swarm travels reaches all of them at once.
- **A body's own path is a bounded search.** Close in, a body never pays for a
  whole-map answer: its path is a crowd-aware search capped at about the work of
  the box round it, and a victim that search cannot reach is not close, so the
  body goes back to the field, which goes round to it. A farther cell (a pack
  member's place round a quarry) gets a larger cap, and a miss there too falls
  back to the field wherever there is one; only a move with no field behind it
  (a far dead to fetch, a breach approach) still pays for the whole map. Melee is where every body re-paths to a victim that moves
  every tick; uncapped, that was whole-map searches by the dozen once a large
  swarm made contact. `-Dbattle.swarm.localPathBudget=false` is the control.
- **A field belongs to the battle's navigation grid.** It is derived from that
  grid's walls and retired by its topology changing, so two battles in one
  process can never read each other's fields (the `LosCache` lesson). It is
  rebuilt when its sources move or the walls change, at a bounded cadence and a
  bounded amount of work per tick, and published whole: a body always descends
  a complete field, an older one at worst, against the current walls. Fields
  are deterministic under the seed, so replays match byte for byte.
- `-Dbattle.swarm.flowField=false` is the control: every body paths for itself,
  as the swarm did before fields. What the fields buy, by map and swarm size, is
  in `swarm-movement-cost.md`.

**Hardpoint.** A position the player has fortified with the flying tender's
construction (Vulcans and whatever else the tender can build). A hardpoint is
how the player frees troops: a lift zone that can hold itself is a lift zone
that does not need the whole company.

**Tender call-down.** The flying tender, called through Ship on Station when
the fleet brought a Shepherd. It hovers over the ground the player names and
builds there by its stance director (Vulcans and whatever else the tender can
build) for its bounded visit. Not every mission brings every tool. A landed,
grounded tender is campaign-mission and set-dressing machinery, not part of
Colony Breach.

**Hive posture.** The player's pre-launch answer to the optional hive objective.
It is chosen in the mission briefing, where a Colony Breach rescue offers the
three postures in place of contract terms, carried on the mission, and frozen
into the Marine rescue commander when the battle is built; nothing changes it
afterwards. A mission that never chose one plays **Evacuate**, because the win
is the evacuation and the bore is a bonus.

- **Evacuate** — bores are obstacles; the commander engages one only when it
  sits on the walk out: within a few cells of the route from a walking group,
  or from a door still to be relieved, to the lift. It comes back once the
  bore is no longer on that walk. Evacuate never sends anyone off the route.
- **Opportunistic** — the commander may send a strike detachment at a bore when
  the readiness gate is open: the living bore nearest the lift, since that is
  the one pressing on the walk out.
- **Purge** — the mother bore is a real objective. Before she opens, Purge
  plays as Opportunistic against the first wave; once she is known, every
  strike goes at her, a detachment already on another bore is turned onto her,
  and against her the readiness gate is relaxed.

A bore is known to the commander from the moment it opens: an eruption tears a
hole in the colony and is announced, so no sighting is needed, and a collapse
is known the same way.

**Readiness gate.** The commander's rule for opting in to a bore strike, read
from what it can see at every command pulse:

- (a) **the lift is hardened**: the company's own built defences stand on the
  lift line, and nobody has been lost on that line since they stood there, for
  a sustained window (twenty seconds);
- (b) **the strength left behind meets the escort's need**: after the
  detachment leaves, the mobile force still has a squad for every group walking
  out and one screening their walk, a squad for every redoubt still waiting to
  be relieved (a pending holdout is unmet need, not spare strength), and one
  more at the lift; and the escort is holding, with no evacuee lost for the
  same window. A cohort that bleeds closes the gate and brings the detachment
  home;
- (c) **a detachment exists**: a squad free of escort duty that can hurt a bore,
  one carrying satchels, firing on structures, or a machine.

`-Dbattle.colonyBreach.strikes=false` is the control: the gate is read and
published, and nobody is ever sent.

Purge relaxes (a) and (b) against the mother bore and nowhere else; (c) is never
relaxed. Evacuate never asks about the lift, because the only bore it engages is
one already on the way, but it keeps the escort's strength. When the gate
closes the detachment is recalled, and the gate may not send anyone again for a
short cooldown, so a gate that flickers does not march a squad back and forth.
The player can always order any squad onto a bore; posture governs only what the
AI does unprompted. Built defences at the lift come from a tender called down in
play (*Hardpoint*); evidence may stand them up at setup instead, as a fixture
statement, to show the gate opening.

**Strike detachment.** The squads (and lance, if one is deployed) the commander
sends against a bore: one squad at an ordinary bore, two when Purge goes for the
mother, chosen from the capable squads free of escort duty, machines first, then
satchel carriers, then the nearest. Membership is stable: the same squads stay
on the strike until their bore collapses, the detachment is lost, or the gate
closes and they are recalled, and a squad on strike is never drawn as an
escort. A detachment squad is ordered at the bore exactly as the player would
order it (an attack move naming the bore's cell as the thing to open, see
*Bore*), so its sappers set charges and the rest fight the body through the
ordinary passes. The decision is decided before new escorts are drawn, so
escorts still come before relief and relief before strikes: people in the open
are exposed, and a bore is optional.

Every commit, recall, retarget and ending is a decision kept with the gate it
was read under (each condition met or not, and its inputs). The command trace
carries the posture, the gate, the detachment and the decisions of each pulse;
a commit is the `strike_committed_N` beat and a recall `strike_recalled_N`, where
`N` is the bore's number or `mother`. A collapse ends the strike under its own
`bore_collapsed` beat and a lost detachment under none.

**Hive bonus.** Collapsing the mother bore is recorded on the mission outcome
whatever the posture, through the ordinary outcome path: the battle reports
whether the mother collapsed beside the evacuation report. In the campaign it
contains the bloom behind the call (*Campaign: blooms*); anything it pays beyond
that and the Hegemony's regard is the owner's call and not yet settled.

**Side objectives.** Optional objectives (`optional-objectives.md`) the breach
offers beside the rescue, rolled at setup and advanced on the breach's own
clock; they succeed or fail only themselves, never the cohort or the lift. The
pool holds what the colony's set pieces can host (`set-piece-blocks.md`), and
one is rolled per battle (`battle.colonyBreach.objectives` forces one):

- **Secure the loading** (`staffed-buildings.md`): the colony's largest
  warehouse, worked by its own people, is loading the colony's stores onto a
  truck in its yard, and the company that holds the yard until the truck is away
  is paid out of what it carries. Needs a warehouse.
- **Strip the workshop**: three of the factory row's hands carry its largest
  workshop's gear across the service lane to a stack against the far side, and
  the company holds the lane, which the swarm comes down from both gates. Done
  at the count; lost when no hand is left. Needs a factory row.
- **Hold the muster**: the colony's people have gathered in the market square,
  and the company holds it for a hundred seconds against what comes in by
  its mouths. Lost the moment fewer than half the muster are alive. Needs a
  market square.
- **Empty the hall**: the people sheltering in the main street's civic hall
  come out two at a time and walk the street to the plaza, and the company keeps
  the street open against what comes in from its side streets and gaps. Done
  when everyone left has reached the plaza; lost when fewer than half are
  alive. Needs a main street with its hall.

The colony's people in the last two are brought for the objective, stand
together, run from the swarm when it comes within a few cells and come back
when it has gone; like a staffed crew they never count toward the rescue.

A side objective brings its own **pressure** rather than make the breach's
swarm aware of it (`set-piece-blocks.md`, Lanes and pressure): a held bore of
its own (numbered from 90, clear of the eruptions) opened on the street near
the set piece, whose surges the objective calls on its own beats, before the
tremor as after it, and whose broods it sends in down the set piece's lanes.
Collapsing that bore ends the pressure, which a guard may gamble on. When the
objective ends its survivors wander as the rest of the swarm. The breach keeps
out of the set piece's way: once the objective is rolled, any eruption site
within 80 cells of the set piece's middle (a first-wave bore's foraging pulse
overruns a yard from closer) is
re-seated at the nearest place outside that ring, keeping its number and time
(a first-wave site still off the walk in from the field where any such place
is left), and no bore buds inside it (`ColonyEruptionPlan.clearOf`), so what reaches the
set piece first is the pressure its beats pace. No runner of the opening swarm
starts nearer the set piece than its own bore can open (28 cells); one the
roster scattered there moves to the roster's next cell outside
(`SwarmDefenseRoster.clearOf`).

## Flow

1. **Landfall.** The company lands in the fields outside the colony's edge
   (*Landing field*) and walks in across open ground. The first wave of bores
   is already open on the outskirts either side of the fields, still fissures,
   and their pulses forage into the town after the colony's people (*Harvest*),
   so the company meets only the few that cross its path. Militia hold their
   buildings.
2. **Shelter.** A Marine reaches the shelter; the main cohort releases. The
   holdouts are relieved one at a time, before or after the shelter as the
   commander chooses, and any of them may fall while it waits. Meanwhile every
   colonist dragged home feeds a bore: fed ones grow, bud new bores further in
   (*Budding*) and, at maw, raise spires.
3. **Dig in.** The lift countdown runs. A called-down flying tender can be
   on station and building. Bores grow. The director's target climbs with every living bore
   and tier.
4. **Mother bore.** Later in the battle the mother bore erupts away from the
   lift, already at bore tier, with its spore colonies already sprouted.
5. **Lift.** Shuttles land; the cohort boards. The battle can end on the lift
   (the rescue win) with or without the mother bore collapsed. Collapsing it is
   the hive bonus, recorded on the ordinary mission outcome (*Hive bonus*).

The breach sequence owns the clock and the triggers; beats are what it
publishes. Beats are presentation and evidence, never inputs to the simulation.

**Lift timing.** The lift has no countdown: the pickup craft comes down at the
start of the battle and waits, so `lift_inbound` and `lift_down` normally fire
before the shelter is reached. Bores grow on their base clock and on what the
Runners drag into them (*Harvest*).

## Beats

All beats are prefixed `colony_breach.`. Numbered beats index first-wave bores
in eruption order from 1; the mother bore is not numbered. These ids are the contract between the sequence, the audio and
the evidence; change them here first.

| Beat | Audience | When |
| --- | --- | --- |
| `landfall` | PLAYER | First Marine on the ground |
| `militia_contact` | PLAYER | First Marine within sight of a militia post |
| `shelter_reached` | PLAYER | Cohort released (the main redoubt) |
| `redoubt_reached_N` | PLAYER | A Marine reaches redoubt N (1 is the shelter); several redoubts only |
| `redoubt_line_broke_N` | PLAYER | Holdout N's line is pushed off its works into the building |
| `redoubt_fell_N` | PLAYER | Holdout N falls |
| `tremor` | PLAYER | Breach begins: the battle's first tick, as the first wave opens |
| `bore_erupted_N` | PLAYER | Bore N opens |
| `bore_grew_N_bore` / `bore_grew_N_maw` | PLAYER | Bore N reaches that tier |
| `mother_bore_grew_maw` | PLAYER | The mother bore reaches maw (it opens at bore tier) |
| `bore_collapsed_N` | PLAYER | Bore N destroyed |
| `mother_bore_erupted` | PLAYER | Mother bore opens |
| `mother_bore_collapsed` | PLAYER | Mother bore destroyed |
| `matriarch_emerged` | PLAYER | A matriarch spawns |
| `first_harvest` | PLAYER | First body seized and dragged toward a bore |
| `bore_fed_N` / `bore_fed_mother` | OBSERVER_ONLY | A body delivered into bore N (or the mother); published at every delivery |
| `marine_impaled` | PLAYER | A Runner impales a Marine; at every impale (militia and colonist impales publish none) |
| `marine_freed` | PLAYER | A held Marine's carrier dies |
| `marine_taken` | PLAYER | A carrier delivers a living Marine into a bore |
| `surge_N` / `surge_mother` | OBSERVER_ONLY | Bore N (or the mother) starts a surge; published at every surge, not once |
| `tender_down` | PLAYER | A called-down tender is on station (no producer yet) |
| `hardpoint_established` | PLAYER | First tender-built defence completes at the lift |
| `strike_committed_N` | OBSERVER_ONLY | Commander sends a detachment at bore N (`mother` for the mother); at every commit |
| `strike_recalled_N` | OBSERVER_ONLY | Readiness gate closed (or, for Evacuate, the bore left the route), detachment recalled |
| `lift_inbound` | PLAYER | First shuttle committed |
| `lift_down` | PLAYER | First shuttle landed |
| `cohort_boarded` | PLAYER | Last surviving evacuee aboard |
| `colony_lost` | PLAYER | Cohort lost |

**Sound.** Beats are voiced from the Colony Breach narration catalog, which a
battle hears whenever it carries the breach sequence, whatever mission launched
it. A PLAYER beat with no line is silent: `redoubt_reached_1` is spoken as
`shelter_reached`, and observer-only beats never speak. Who speaks a beat
follows the lore guide's names: the company officer calls them ravs and names
strains and bores by number, the militia sergeant says ravs, holes, grabbers and
breakers, and the shelter administrator says the ground and only whispers the
bloom. A numbered beat has lines up to four; a higher number needs lines added.
Lines are generated from tracked prompts by `tools/generate_colony_breach_audio.py`
and registered like every other clip. A beat that adds a line names its speaker
by these rules, and a line that a later beat makes stale is superseded by it.

Bores and bodies sound through presentation cues the simulation writes and
never reads (`BoreCues`, `SwarmCues`), so a headless run is silent and nothing
heard reaches the battle. A bore is heard erupting, growing to each tier,
surging and collapsing, and breathes a tier ambience while it lives. Each clutch
it lets go is heard clawing up out of the hole (*Emergence*; `BoreAudio`): chitin
scraping on rock and wet earth tearing, one short take per clutch at the mouth,
read off the emergence rather than posted, pitched by the clutch's heaviest body
(the Matriarch deep and slow, a skitterer light) and a little louder for three
bodies than one. A surge's clutches overlap and layer by the crowd rule under a
budget of four, so emptying a maw is a churn under the surge cue, not a wall. Its
one take of its own is mixed from shipped ones (bore growth's tear, a Runner's
clatter lowered, the body drag's grit). A swarm
body is heard for what it does, not for the beat table: it cries when it takes
new quarry, strikes, rams a wall and breaks it, yelps or grunts when it is hurt
and lives, and dies. A Runner taking up a body is heard latching onto it, and
hauling it is heard as the load scraping over the ground in place of its gallop.
Its feet follow the ground it covers: a Runner's gallop and chitin clatter, a Brute's
hooves at every half cell, so a walking Brute plods and a charging one thunders.
Each phase of the Brute's charge has its own sound (the tell, the run, the hit
on infantry, a vehicle or a wall, the stun after a heavy wall or a tank), keyed
to the charge's own events; a Runner's impale has its own sound, posted as a cue
by the story that adds it. A ground colony makes no sound as it fires: its
spines travel under the ground, seen only as the ripple in the creep, and are
heard where they break out, as the weapon's impact. The creep itself is heard as a wet, slow spread bed, one
loop placed on the seen living creep nearest the camera and as loud as the
share of the ground round the camera the creep has taken (`CreepAudio`), so a
sliver at the edge of the view murmurs and a street drowned in it is a full
bed. **Rav Bloom sound re-uses rather than multiplies** (owner): a new sound
is first tried as an accepted take of another played at another pitch, which
also sets its tempo, and ships no audio of its own when that serves; the
creep bed is the bore-tier ambience slowed, the spine strike the Brute's
head-ram quickened and its wall hit slowed, drawn from one pool. **A crowd is heard by layering** (owner, 2026-10-24): several voices of one
sound play at once, up to that sound's voice count, each quieter the more are
sounding, so many bodies doing one thing are thicker and somewhat louder than
one without drowning the fire around them; two voices of one sound never start
on one tick (the second waits a tick or two), and one body does not repeat
itself inside its own gap (`VoicePool`). Footfalls come from the bodies nearest
the listener, so a pack reads as a pack (the Bombard's from its tripod landing, as its rig steps it, rather than
from ground covered), and the swarm is heard only on ground
the player has seen. A body fed to a bore is swallowed as it
reaches the mouth: a wet gulp (the Spitter's lob, slowed) near -29 LUFS and, a
beat later, its landing down inside (the body thud, deeper) near -33, pitched by
the fed body's size. **A thrown body lands with one thud** whatever threw it
(owner, 2026-10-24): a marine a Brute tramples, a Matriarch's reap throws or a
Harrier drops, and a shot Harrier reaching the ground, a little lighter; it is
the landing body's own sound, posted by that body. A body's growth
stage pitches everything it makes by the inverse of its size and sets its
loudness (a whelp about four semitones up and quieter, an elder deeper and
louder): one take heard three ways stays the same creature. A pack's break is
one scatter shriek, the leader's own cry at the leader's stage. The Spitter has
no voice. The Matriarch is the biggest of the Runner's and Brute's family and
sounds it: only her roar (out of the bore and at each moult) and the whoosh
and cut of a reap are her own takes; her bellow on new quarry, the tell before
her run (the Brute's charge tell), hoof, crown, wall breaking, pain and death
are the Brute's, played deeper, slower and
further apart. The Harrier is the other way round, lighter and quicker
(`HarrierAudio`): chitin wingbeats on the downstroke of every flier within
earshot, its one take of its own, cut short from the Runner's clatter and
levelled so a stroke is heard; the Runner's cry as a screech the moment each
member's wings fold for the dive, its strike as the bite and its impale as the
snatch of a marine it lifts (the throw lands with the body thud), the Runner's
yelp when flak hurts it, a Spitter's death shriek played lower as it is killed,
and the body thud, lighter, where its fall reaches the ground. It is read
off the pack and its falls and throws rather than posted by them, so the flier's
simulation stays silent. Its pack layers by the crowd rule above, a lone
flier's voice set low for the same reason, and each sound's level is set from
its borrowed takes' measured loudness, since those differ by some 25 dB. Timing
and jitter run on simulation time and a seed, so a replay
sounds the same and a review can mix its soundtrack from the same rules.
The brood spire it rises from ships no audio of its own either (`SpireAudio`,
read off the spire's launches and its body's death). A pack launching is a wet
split as the pods open (the spore vent's burst, lower), the brood's chitter (the
Runner's cry, higher) and three wingbeats as it takes up, a few ticks apart; a
Matriarch's brood rises with the same cue, quieter. A spire dying, shot down or
withered with its bore, creaks (the wingbeat, slowed), cracks (the Brute's
head-ram, deeper) and falls (the Brute's wall break, deeper).
The skitterer, the nests' worker, never cries or strikes and is heard small and
busy beside a Runner pack, all of it borrowed takes pitched up by its size: a
light chitin patter on every stride (the Harrier's wingbeat, played higher; a
Runner's feet fall on every other stride), the body drag while it hauls, a thin
squeak when it is hurt and when it scatters from the company (the Runner's yelp,
higher still; one hit that sends it running squeaks once), so a nest emptying
under fire is heard, and the Runner's death. Head-down at a mend, a burn, a tree
or an outpost it makes a soft wet working sound (the fissure's scratching
ambience, raised): a loop placed on the seen workers at work nearest the
camera, two at most, the second quieter by the crowd rule, so a busy nest
murmurs and never drones. Everything it makes sits a few dB under a Runner.
The Bombard (`BombardAudio`) is heard by its lob, deeper than the Spitter, a
Brute's size or more, and read off what the battle shows (its wind-up, its
glob's shot leaving and arriving, its death, the standing hazes) so its
simulation stays silent. As its tail curls it strains: a slow swelling groan
the length of the wind-up (the bore's growth) and four chitin creaks over it
(the Runner's clatter, deep) that tighten as the curl goes, so a throw is heard
coming. The throw is a whip and a crack, its one short take of its own, cut
from a Matriarch reap's whoosh, a clatter knock and a Spitter lob's wet slap;
it is heard where the launch gives the Bombard away even through fog. Where the
glob lands it pops, soft, heavy and wet (a spore vent's puff, low, over a body
thud), and a Bombard killed bursts its sac the same way, quieter and lower.
Under a standing haze is a low spore hiss, its other take, a seamless loop cut
from the vent's hissing cloud: placed on the two seen hazes nearest the camera,
each keeping its loop while it stays among them, swelling briefly at each
sting and fading as the haze thins.

**The swarm sits in the fight, not over it** (owner, 2026-10-25). Every
creature sound's level is set from its take's measured loudness against the
fire around it, never left at the take's own level: the generated takes run hot
and differ by some 30 dB. One adult voice lands near the gunfire's -24 to -28
LUFS: a Runner's cry, bite, pain and death and the Harrier's near -25, a
Brute's a few dB heavier, its charge tell over the guns so a charge is heard
coming, the Matriarch, the one boss, -15 to -21, and footfalls well under,
since many bodies make them. The skitterer's squeak sits near -30 and its
death -28, its drag -35, each work loop -36 and its patter -39. The Bombard's
wind-up, a tell, sits near -27 with its creaks a little under, its whip and
burst near -27, a death burst -32, and one standing haze -38, -33 at a sting. A Runner clutch's claw-up sits near
-28, three bodies' and the Matriarch's -26, and the surge they come up under
-22, a one-off a little over the guns. A spire's launch sums near -23, by a Harrier
screech; its collapse, a big one-off, near -19. The anti-air flak that meets them is a little
heavier than the line, its own deeper report and air-burst near -22 a voice
(owner, 2026-10-25: a deeper thump and pop), built from the mod's explosion
and rifle takes. A new sound or take is levelled the same way
before it ships. The `swarm-sound` and `spire-sound` look suites, run with
`-Dsnapshot.lookSound=true`, log what the battle screen would start
(`BattleSoundLog`) and mix it into their MP4s, so a review hears the levels
against the fire.

**Marines bark in the fight** (owner, 2026-10-25). When the Rav reach them the
marines are heard in the mix (`MarineBarks`): background shouts, cries and grunts,
never hero lines, so nothing is shown, subtitled or waited on. They are read off
what the battle already reports, as the swarm's sounds are: a squad that was clear
calls contact as a pack closes, a marine starting a reload with Rav near calls it,
a marine a Runner seizes or impales cries out, one thrown yells as it lands, the
marine nearest a Brute's charge tell or the Matriarch's run tell warns the rest,
a Rav dying near the line draws relief (more for a Brute or the Matriarch), a
marine dying with Rav near draws a squadmate's curse, and a marine hurt with a Rav
at arm's length grunts with a scuffle of boots and gear. Each marine keeps one of
six voices for the battle, the operation radio's cast heard dry rather than over
comms, a little pitched apart; the lines of a kind take turns so one is never heard
twice running. A squad has one voice at a time, save the cry of one being seized
or thrown, which waits on no one; a marine and a squad keep their gaps, the most urgent bark goes first, and the battle has at most three sounding
at once, so a big fight is a few overlapping shouts rather than a wall. They are
heard only on seen ground. Takes are generated from a tracked catalog
(`tools/generate_marine_barks.py`) and every take is levelled by its measured
loudness to its kind's level, a little under the gunfire: calls near -26 LUFS a
voice, cries and warnings -25, kills and reloads -28, grunts -29, the scuffle -31.
**The colony is heard too** (owner, 2026-10-25). Three casts bark, each in its
own voices and words from the lore guide's names: the marines (allied company
infantry with them), the colony militia, four voices of its own, who say ravs and
bugs, grabbers and breakers, and have no name for the Matriarch, only "the big
one"; and the colonists, four more, who make no soldier's calls: they scream as
the things close, cry out when seized, thrown or hurt, and grieve when someone
near them dies. Colonists outside any squad share one voice channel with the
colonists on their patch of ground, so a fleeing crowd is a few screams, not one
each. The enemy's soldiers do not bark.

**Reuse before new takes** (owner, 2026-10-22). The swarm is a minor faction
and its sound is kept small: a strain's sound is an accepted take of a sibling
strain played at another pitch and speed before it is a new file, and a new
take is made only where nothing reusable fits. The shift is the sound player's
own pitch argument at play time (which moves pitch and speed together), never
a baked copy, so a variant costs no file. Growth stage rides the same
argument.

## Laws

- **Spawning has a source.** After the first eruption, every swarm body comes
  out of a living bore. No edge spawning in the breach phase.
- **Collapse is permanent.** A collapsed bore never reopens and never hands its
  capacity to another bore.
- **Bores are fought, not scripted.** A bore dies to ordinary damage through
  ordinary durability; nothing collapses a bore by fiat.
- **Biomass drives growth.** A bore that is not fed grows slowly; a bore that
  is fed grows fast. Saving colonists starves the hive.
- **Surges, not trickles.** Bores release bodies in timed pulses that leave
  the mouth as a stream and cross the ground as packs; the quiet between pulses
  is real.
- **Strains by tier.** Which strains spawn is a function of the bore's tier and
  the director's budget, deterministic under the battle seed. A maw may draw a
  Bombard (`bombard.md`), at a small weight, at most one living per bore and
  three across the battle, at the bores nearest the line (`BombardQuota`). A
  strain whose *Hold* is full is out of the draw.
- **A flier pack flies only over a map that can answer it.** A mission
  releases a Harrier pack only where the marines or an ally have anti-air on
  the map: a seated gun that answers fliers (an anti-air position, or any gun
  whose weapon tracks them, as a colony's heavy machine guns and a flak
  emplacement do), or a hull whose turret does (`SwarmFliers.hasAirCover`,
  the same rule by which a pack senses the guns; `releaseUnderCover` refuses
  otherwise). So a Colony Breach meets it with the compound's gun towers and
  the militia's guns, and a tender meets it with flak. A scene's control may
  break this; a mission may not.
- **Air cannot clear the map.** Every living bore has spore colonies round it
  until they are killed; they die with the bore.
- **Posture is frozen, the gate is live.** The hive posture is set at launch;
  the readiness gate is evaluated from the commander's visible picture every
  command pulse.
- **The swarm senses, it does not know.** The director and strains act on what
  swarm bodies sense plus the disclosed goals (shelter, lift); the cohort stays
  hidden until contact, as in Civilian Rescue.
- **Deterministic.** Same seed, same breach: eruption places, timings, strains
  and growth are seeded. Evidence replays must match byte for byte.
- **One story.** The breach sequence is the battle's one story sequence.
- **No ground to take.** A rescue is fought for people, not ground, so it plays
  no compound layer (`BattleSimulation.dropCompoundLayer`): the installation's
  barracks and armouries and the company's landing field stay places on the
  tactical map, but none is a compound, so none shows a capture beacon, is
  taken or held, or produces supply. The capture points are conquest's.

## Boundaries

- Marine command, cohort, release, escort and lift: `extraction-command.md`.
- Precinct plans and map requirements: `precincts.md`, `mapgen-nouns.md`.
- Garrison manning for militia posts: `garrisonables.md`.
- Tender, its construction and build options: `tender-ship.md`.
- Mech lances and drop allowances: `mechs-nouns.md`.
- Layered bodies and appearance families: `battle-render-nouns.md`.
- Radio and narration: `battle-radio-nouns.md`; beats are consumed there, never
  the other way round.
- Structure durability: `combat-durability-nouns.md`.
- Setting and canon tiers: `faction-lore-nouns.md`; the swarm's own lore
  guide is `swarm-lore-guide.md`.
- The rescue's choice and exactly-once lifecycle: `campaign-event-nouns.md`;
  where and how often blooms raise it is *Campaign: blooms* above.
