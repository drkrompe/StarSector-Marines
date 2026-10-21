# Colony Breach — Nouns

Status: ACTIVE

Written: 2026-10-21

Updated: 2026-10-21 — the shelter stands in a civil-defence compound, a square walled place in the colony plan dressed with towers, gate guns, slits and floodlights; a holdout claims a block behind works (barricade, firing step, gate, gun) with a line team that falls back into the building; the company lands in open fields outside the colony and the lift comes down on them; bores, tiers, surges, spore colonies and the bore-sourced director as built.

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
the horizon: it erupts out of bores inside the colony, and every bore left open
gets worse. The player can spend force to collapse bores and buy quiet, or
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
- **It recurs.** Outbreaks are a sector phenomenon: they show up at colonies
  across the campaign, are never "defeated" for good, and a collapsed mother
  bore ends an outbreak, not the weapon.
- **Who made it is a question, not an answer.** Briefings and radio may
  speculate (Domain denial programme, a Tri-Tachyon-era leak, a sporeship payload,
  something older);
  no text states the maker as fact.

Names, the life cycle in in-world terms, each faction's view and the tone and
vocabulary for briefings, radio and item text are in `swarm-lore-guide.md`.

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
clear landing field; a map that lacks any re-rolls the seed. Lower risks and the
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
mans the civil-defence compound round the shelter (see *Redoubt*).

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

**Heavy assets are what the fleet brought.** The company's mechs and vehicles
reach the battle through its own Mech Support power, which every battle
honours; the rescue adds nothing to that. A mech-lance allowance for the
Marine commander is honoured only where the mission declares one, never derived
from the company's mechs, because an allowance is a separate payment and would
field the same machines twice.

**Shelter, cohort, lift.** As in Civilian Rescue (see
`extraction-command.md`): the cohort is sealed in the shelter until a Marine
reaches it, then walks to the lift under escort and boards only once a shuttle
is physically down. On the colony the cohort is spread over several redoubts,
and the **lift is on the landing field**: the shuttles taking the survivors off
come down where the company came down, for the same reason, at the clear pickup
site on the field nearest the shelter and clear of the berths. Land in the
fields, fight in to the redoubts, bring the survivors back out.

**Redoubt.** A place the cohort is holed up in. The **main redoubt** is the
shelter, with the largest share of the cohort and the sealed, hidden shelter
guard; it is redoubt 1. On the colony map the shelter stands inside the
colony's **civil-defence compound** (below). The colony has one to three **holdouts** besides
(seeded, so two to four redoubts in all): a militia post with three or four of
the cohort inside, deeper in than the fireteam's firing cells.

A holdout **claims a block, not a building**. Its people have thrown up
**works** round the building they hold, a few cells out, so a yard lies
between:

- the **barricade**, a ring of industrial fence broken by stacked crates and
  scrap, closed to movement and open to sight, with a parked truck dragged
  across each street it crosses. Wherever another building or a wall already
  stands on the ring, that structure closes the side itself;
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

A holdout's ground is its works' rectangle. A Marine at its **gate** relieves
it, and the people walk out through the gate.
`-Dbattle.colonyBreach.redoubtWorks=false` is the control: every holdout is the
bare building with aprons.

The **civil-defence compound** is the main redoubt's, and it is a different
kind of thing from a holdout's works. A frontier colony that always knew
something might come out of the wilderness built it when the colony was laid
out, so it is a **place in the colony's plan**, not works raised round a house:
a walled precinct on the town's far side from the fields, laid out square, with
a shelter hall of bunks, a sick bay, a canteen and a store round a muster
yard, each furnished as what it is. Because it is planned
with the colony, the town's streets run up to it, its gates are where they
cross its wall, and its neighbours are built against it: it fits the layout by
construction. The colony re-rolls a map that did not build it. The shelter is
always its hall.

It is a proper work, but a civil one, partway between a house with sandbags and
a Conquest fort and copied from neither. Its program owes nothing military (no
keep, barracks, armoury or guard post, each of which would publish a node of
the defender's, and on this map the defender is the swarm), and its wall is
colony-grade, light enough for a Brute, a charge or a shell to breach. The
militia make the rest of it when the battle is set up:

- a squat **tower** on each outer corner of the wall;
- a **gun tower** beside each gate: a heavy machine gun on the line of the
  wall behind slits. Those guns are its whole armament: no layers, no bunkers,
  no anti-air;
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
the cohort — overrun, they are the swarm's (harvest's biomass, once harvest
exists) — and the holdout is no longer anyone's to relieve. The shelter is
sealed and does not fall. That asymmetry is the triage: the main cohort is
safe while it waits, the holdouts are not, and which door the company reaches
first decides who is left to bring out. With one redoubt the rescue plays
exactly as a single-shelter rescue. `-Dbattle.colonyBreach.redoubts=false` is
the control: the posts hold militia only and the whole cohort is in the
shelter.

Where redoubts meet the stories still open: a fallen holdout's people are the
harvest's easiest biomass, and what a runner does with them is harvest's to
decide; whether creep under a redoubt weakens its aprons or its garrison is
creep's to say; and a hive posture that sends strike detachments competes with
relief for the same squads, so its readiness gate should count pending holdouts
as unmet need.

**Breach.** The mission-level event of the swarm coming up through the colony.
The breach has a shape over time (see *Flow*) driven by the **breach sequence**,
the one story sequence the battle runs.

**Breach clock.** The breach sequence's own time. It starts at the cohort's
release: the **tremor** follows four seconds later, the first wave of
eruptions opens over the minute after that (the first six seconds in, then
every fifteen), and the mother bore three minutes after the tremor. The clock
is relative to the release and nothing else, so the same colony breaks open on
the same schedule however long the company took to reach the shelter.

**Eruption site.** Where a bore will come up, chosen once at setup and fixed
for the battle (`ColonyEruptionPlan`). A site is the open ground outside a
colony building's door — a place, not a cell picked off the map — and is never
inside the shelter's clearance, the lift's safe ring or a marine landing pad's
clearance, never closer to another site than an eighth of the map's short side,
and always on ground the swarm can walk from to the shelter. The first wave is
four sites: up to half on the corridor between shelter and lift, one on each
flank of it, the rest from whatever is left; the mother site is the eligible
place furthest from the lift. The first wave is numbered in eruption order from
1, corridor sites first; the mother is named rather than numbered. Sites and
their scheduled ticks are published in the breach snapshot from the start, and
each eruption as its beat, whether or not anything yet stands up on the site.
Eruption is the seam: the sequence opens a site at its tick and hands it to the
installed eruption handler, which is where a bore is built.

**Seepage.** The thin perimeter pressure before the tremor. The swarm director
restores only a fraction of its population floor while the colony is seeping,
and its full floor from the tremor on.

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

**The feed seam.** Biomass reaches a bore through one call,
`SwarmBores.feed(sim, bore, amount)`, with `amount` in colonists' bodies; the
bore to deliver to is found with `nearestLiving`. A feed buys growth at once
(a handful of bodies takes a fissure to bore, roughly twice that to maw), adds
to the bore's fresh biomass, and brings its next surge forward. A feed into a
collapsed bore is refused and changes nothing: the delivery is lost.

**Harvest.** The swarm's economy. Colonists are food. A runner that brings down
a colonist (ambient colony civilians, militia, and in the end Marines) does not
just kill it: it seizes the body and drags it back toward the nearest living
bore, slowed, as a visible carrier. A carried body delivered into a bore is
**biomass**: it advances that bore's growth and buys the director a burst of
spawn capacity there. A carrier killed on the way drops the body, and the
delivery is lost. Laws:

- Biomass is the only fast path to growth. A starved bore stays a fissure for
  a long time; a fed one reaches maw quickly.
- Every colonist the company gets out, or keeps alive, is biomass the hive
  never gets. Rescue and hive pressure are the same fight seen from two sides.
- Carriers are readable and interceptable: a stream of bodies being dragged to
  a bore is the player's signal that a bore is about to grow.
- Evacuees in the cohort are never harvested while their escort lives. A lost
  evacuee may be.
- Harvest is deterministic and traced: deliveries per bore appear in the
  director snapshot.
- **A dragged body is safe from its own side's direct fire** (owner,
  2026-10-21). Fire at a carrier, a miss that strays into its load and a round
  that would go through the carrier never hurt the body being dragged, Marine,
  militia or colonist; the ray passes it. Area weapons still reach it, behind a
  switch while the owner decides. It matters once the load can be alive: a
  Runner can impale a living body and drag it off, and killing the carrier
  frees it (`runner-impale-drag.md`).

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
the director's population target, so a bore whose bodies are still alive on
the field stays quiet. A released pulse leaves the mouth as a stream, one body
every couple of ticks from the open cells around the hole, each body's strain
drawn from the tier's table; a bore that collapses mid-pulse releases no
more.

**Mother bore.** The one bore whose collapse ends spawning from its precinct
cluster and is the target of the optional hive objective. It erupts later than
the others, starts at bore tier and is the only bore that can produce a
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

Bores and colonies are grown, not built, and look it: the runner's chitin
and amber in every body, standing on the ground with no hardstanding pad,
their amber nodes breathing. Nothing about them is regular: they erupted, so
they are lopsided. A bore is a slanted shaft torn up through the ground, one
side heaved up into reared jaw-plates, tusks and thrown paving, the other
collapsed into the funnel; the mother bore is a wider, deeper, terraced one.
A colony is an uneven cluster round one dominant, leaning sac. Its grade shows
in its body as well as its numbers: a lone sac with a bud at fissure grade,
two lesser sacs pressed against it at bore grade, three and a plated, spined
flank at maw grade, each larger than the last. Each body carries several
paintings chosen per structure, so a map of them does not look stamped, and
their depth (the lit far wall of a shaft, shadows cast plate on plate and onto
the ground) is lit by the battle's own default sun, so the paintings are
never rotated or mirrored. They are drawn somewhat larger than their bodies
(hit radius and footprint) so that they read as structures among a mass of
creep and bodies.

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

Colonies stand on the bore's **growth sites**, a seeded ring of open cells
round the mouth (`BoreGrowthSites`). Today only spore colonies use it; a
later ground colony roots on the same sites (`hive-creep.md`).

**Swarm director.** The non-human pressure owner (today
`SwarmReinforcementSystem`). It decides how many, from which bore, and of which
strain, against a population target that scales with the number and tier of
living bores. It publishes an immutable pressure snapshot for evidence and
diagnostics. It never reads hidden Marine state the swarm could not sense.

Until the tremor it restores a perimeter floor (a fraction of it while the
colony seeps). From the tremor on it is **bore-sourced**: the perimeter is
closed for good, the population target is the sum of the living bores'
capacities by tier, and bodies enter only as surges from living bores. Between
the tremor and the first eruption nothing spawns; once every bore has
collapsed, nothing spawns again. Every swarm strain counts toward the
population. The snapshot carries each bore that has opened: its tier, whether
it lives, integrity, growth, biomass fed, surges, bodies released, next surge,
its living spore colonies and the ticks it opened and collapsed.

**Swarm strain.** A kind of swarm body with its own stats, appearance family and
behaviour. A strain is a designed stage of the one organism (see *Origin*), not
another animal: every strain is the Runner's body plan and chitin specialised
for one job in the cycle, and reads that way on screen.

- **Runner** — the shipped 20 HP melee body. Volume.
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
  bodies and emplacements where a Runner's slash only scratches.
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
  damage model has none for ground bodies.
- **Matriarch** — one per mother bore at maw tier: large, slow, very durable,
  wrecks emplacements. Answered by mechs, heavy weapons or a lot of satchels.

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
as parts, so a carrier (`swarm-harvest.md`) moves the colonist's corpse with
it and draws it as one more attached sprite; neither the strains nor their
corpse sheets assume a body stays where it fell.

**Creep.** The biological growth living bores spread across the colony:
streets, walls, buildings. It is what makes an ordinary settlement look
unmistakably wrong, and it is the only ground the hive's static growths stand on
away from a bore's mouth. A ground colony (anti-infantry, anti-emplacement)
roots only on creep. Creep recedes when its bore collapses. Mobile strains never
gain ranged attacks to answer fortifications: static growth on creep does that.
In `hive-creep.md`.

- **Creep is a field the battle owns** (`CreepField`): per cell, how much
  growth stands there, whether it is alive, and how far it is from the source
  that reached it (the pulse runs along that). It belongs to one simulation and
  is deterministic in the seed.
- **Sources spread it.** A source is declared at a cell with a growth (reach
  per second, the reach it stops at; bore tiers have presets) and may be fed,
  which speeds it for a while. It claims what lies within its reach by path
  cost along walkable ground, weighted by seeded noise so the front runs out in
  fingers, dearer on pavement; walls beside claimed ground are climbed, never
  crossed. Until bores stand up on eruption sites, each opened site is itself a
  source that never dies.
- **Removing a source withers what it alone held**, from the outer edge back to
  the hole over a visible interval: the growth dies first, then clears. Ground a
  living source also reaches stays, and a living source reaching dying ground
  revives it.
- **The look is the field drawn, never a second model.** One ground-level
  layer, under the bodies and over the props it overgrows, shades the field
  from a painted greyscale material whose values the palette colours: dark
  veins, thin film the town still shows through, nodules, a lumpy edge, a
  pulse running toward the source, climbed walls as dark ridges, and dead
  growth as a grey husk. Colour is data: the swarm owns no red (the owner's
  chitin direction), so the default creep is grey-umber, duller than the
  paving and lighter than the near-black bodies, with sparse dim amber.

**Swarm goal.** A place the swarm is drawn toward: the shelter, the lift, the
nearest Marine body. Swarm movement toward a goal is a shared **flow field** per
goal rather than a path per body, which is what lets the swarm be large on a
large map.

- **Prey** is the goal a hunting body descends when its target is far off: every
  combatant the swarm is hostile to, less the shelter guard while the shelter is
  sealed. It is gathered from the battle each refresh, so "nearest" means
  nearest by the way the ground runs, not as the crow flies. Civilians are never
  prey sources; a body that has found one chases it directly.
- **Shelter** and **lift** are disclosed, so the mission declares their cells;
  the swarm knows where they are without sensing them, as the noun's law allows.
- **Bore** is the goal of a body carrying biomass home: the nearest living
  bore. The swarm's own holes, so their cells are declared and redeclared as a
  bore opens or collapses.
- **A field makes a stream.** The descent is the same from every cell and has
  no crowd term, so bodies released together follow one lane down it, which is
  the river a spawning surge needs; they break apart where local pursuit takes
  over. Nothing separates swarm bodies from each other today (Marines steer
  away from them, not they from one another), so a later separation pass must
  leave a surging body on its lane to keep the stream.
- **Strategic movement and local pursuit.** A body whose target is more than a
  few cells away moves down its goal's field; within that range it runs its own
  short path to the one victim it has chosen. Every strain moves through the
  same call, so a change to how the swarm travels reaches all of them at once.
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

**Hive posture.** The player's pre-launch answer to the optional hive objective,
frozen into the Marine command envelope at launch:

- **Evacuate** — bores are obstacles; the commander engages one only when it
  sits on the route.
- **Opportunistic** — the commander may send a strike detachment at a bore when
  the readiness gate is open.
- **Purge** — the mother bore is a real objective; the readiness gate is
  relaxed.

**Readiness gate.** The commander's rule for opting in to a bore strike, read
from what it can see: (a) the lift zone is hardened (built defences, no losses
on the lift line for a sustained window), (b) the strength left behind meets
the escort's need, (c) a detachment with demolition or heavy weapons exists.
Purge relaxes (a) and (b). When the gate closes the detachment is recalled. The
player can always order any squad onto a bore; posture governs only what the
AI does unprompted.

**Strike detachment.** The squads (and lance, if one is deployed) the commander
sends against a bore. Stable membership, traced like any other command role.

## Flow

1. **Landfall.** The company lands in the fields outside the colony's edge
   (*Landing field*) and walks in across open ground. A thin perimeter seepage
   of runners is the only pressure. Militia hold their buildings.
2. **Shelter.** A Marine reaches the shelter; the main cohort releases. This is
   the breach sequence's first trigger: **tremor**, then the first bores erupt
   over the following minute at precincts between the shelter and the lift and
   on the flanks. Seepage stops. The holdouts are relieved one at a time, before
   or after the shelter as the commander chooses, and any of them may fall
   while it waits.
3. **Dig in.** The lift countdown runs. A called-down flying tender can be
   on station and building. Bores grow. The director's target climbs with every living bore
   and tier.
4. **Mother bore.** Later in the battle the mother bore erupts away from the
   lift, already at bore tier, with its spore colonies already sprouted.
5. **Lift.** Shuttles land; the cohort boards. The battle can end on the lift
   (the rescue win) with or without the mother bore collapsed. Collapsing it is
   the hive bonus, settled through ordinary mission outcome.

The breach sequence owns the clock and the triggers; beats are what it
publishes. Beats are presentation and evidence, never inputs to the simulation.

**Lift timing.** The lift has no countdown: the pickup craft comes down at the
start of the battle and waits, so `lift_inbound` and `lift_down` normally fire
before the shelter is reached. Harvest is not built yet, so nothing feeds a
bore in a live battle and bores grow on their base clock alone.

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
| `tremor` | PLAYER | Breach begins (before the first eruption) |
| `bore_erupted_N` | PLAYER | Bore N opens |
| `bore_grew_N_bore` / `bore_grew_N_maw` | PLAYER | Bore N reaches that tier |
| `mother_bore_grew_maw` | PLAYER | The mother bore reaches maw (it opens at bore tier) |
| `bore_collapsed_N` | PLAYER | Bore N destroyed |
| `mother_bore_erupted` | PLAYER | Mother bore opens |
| `mother_bore_collapsed` | PLAYER | Mother bore destroyed |
| `matriarch_emerged` | PLAYER | A matriarch spawns |
| `first_harvest` | PLAYER | First body seized and dragged toward a bore |
| `bore_fed_N` | OBSERVER_ONLY | Biomass delivered into bore N |
| `surge_N` / `surge_mother` | OBSERVER_ONLY | Bore N (or the mother) starts a surge; published at every surge, not once |
| `tender_down` | PLAYER | A called-down tender is on station (no producer yet) |
| `hardpoint_established` | PLAYER | First tender-built defence completes at the lift |
| `strike_committed_N` | OBSERVER_ONLY | Commander sends a detachment at bore N |
| `strike_recalled_N` | OBSERVER_ONLY | Readiness gate closed, detachment recalled |
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
surging and collapsing, and breathes a tier ambience while it lives. A swarm
body cries when it takes new quarry, and is heard striking, ramming a wall,
breaking it and dying; a Brute's charge, a Runner's impale and a seized body
being dragged have their own sounds, posted as cues by the stories that add
those acts. Each strain's sound is admitted under its own gap so a pack reads as
a pack, and the swarm is heard only on ground the player has seen. The Spitter
has no voice.

## Laws

- **Spawning has a source.** After the first eruption, every swarm body comes
  out of a living bore. No edge spawning in the breach phase.
- **Collapse is permanent.** A collapsed bore never reopens and never hands its
  capacity to another bore.
- **Bores are fought, not scripted.** A bore dies to ordinary damage through
  ordinary durability; nothing collapses a bore by fiat.
- **Biomass drives growth.** A bore that is not fed grows slowly; a bore that
  is fed grows fast. Saving colonists starves the hive.
- **Surges, not trickles.** Bores release bodies in timed pulses that move as
  a stream; the quiet between pulses is real.
- **Strains by tier.** Which strains spawn is a function of the bore's tier and
  the director's budget, deterministic under the battle seed.
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
- Where and how often outbreaks appear in the campaign: the Civilian Rescue
  campaign event (`CivilianRescueSpawnSystem`), extended by
  `sector-outbreaks.md`.
