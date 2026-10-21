# Garrisonables

Status: ACTIVE — reactive manning is shipped for fighting-position runs, a stretch at a time, and for building envelopes, on both Conquest sides (the defender's own belts, compound aprons and compound buildings, the attacker's taken ones) and for a player's squads that opt into it, the player's machine assault on a believed-held garrisonable is built, and machines guard the places their side mans, runs and buildings alike (player on any map, both Conquest commanders on any ground they man), and the Raid, Assault, Sabotage and Extraction defenders man their maps' places reactively too, their lances guarding what they man, and a holder sorties from a walled place it holds through a sally door in its own wall; the compound perimeter and emplacements remain direction.

Written: 2026-10-13

Updated: 2026-10-21 — a sally door shuts again: its holder shuts it behind a sortie, in the face of an approaching enemy and by the player's bar, never on a body, and an open door of a place its holder lost stays open (sally doors, below); a holder sorties from a walled place it holds through a sally door in its own wall; the procedural missions' defender commanders man garrisonables reactively (mission defenders); machines guard manned buildings as they guard manned runs (the player's click on any map, the commanders' attachment to a manned envelope or a building its own garrison holds); building envelopes are garrisonables (apron, envelope, rooms as layers; a building's own garrison counts as manning it); runs are garrisoned a stretch at a time; the defender counter-attacks any lost place (a compound, a fort as one case) in proportion to what a probe observed holds it, and its reserve lends to its own place (`conquest-command.md`); places, not forts, is a law; compounds offer prepared ground, on the campaign's Conquest of places too; machines screen the places their side mans.

## Why this exists

Fighting positions shipped with a fort belt garrisoned from the first tick: every
belt nest seats a squad in its trench whether or not anybody ever comes near it.
On the fort control that took 120 defenders out of the patrol pool and left the
defender with no roving reserve at all. The number of places worth holding is
about to grow sharply — sandbag aprons at compounds, building shells with
windows, emplacements — and a model in which every such place is filled because
it exists will spend the whole defending force on ground nobody is attacking.

The direction here is that a place worth holding is **manned in answer to
threat**, not filled by default.

## Nouns

A **garrisonable** is a place that offers **posts** to a squad that mans it: a
set of stand cells, each with the cover it gives and the directions it covers.
It is a kind of place, not a kind of order. Kinds, in the order they are
expected to arrive:

- a **fighting-position run** — trench and parapet cells (built; see
  `ai-nouns.md` and prepared ground in `mapgen-nouns.md`): a fort's belt, a
  story map's dug line, and a compound's **sandbagged door**, the apron laid
  in front of the entrance each compound place is attacked through; garrisoned
  a **stretch** at a time (below);
- a **building envelope** — the windows and doors of one building, its posts the
  interior stance cells its defense frontage derives (built; `ai-nouns.md`,
  frontage and standing to): every compound place (command post, barracks,
  armory) and every held place whose shell has a frontage. It is manned by
  holding the building, so the squad on it stands to at the apertures by the
  frontage laws and walks the rooms when they are quiet or entered;
- a **compound perimeter** — the outer envelope at compound scope, which frontage
  also already derives;
- an **emplacement** — a crewed structure, later and outside this direction's
  first slices.

**Manning** a garrisonable is squad-tier: which member takes which post, claims,
digging in, facing the believed threat, and falling back. **Assignment** is
commander-tier: which squad mans which garrisonable, and when it stops. The two
tiers must stay separate — a squad never decides to go and man a trench nobody
sent it to, and a commander never chooses a marine's cell.

A **stretch** is the part of a run that is one garrisonable. A run a squad's
frontage long or shorter is one stretch; a longer one — a fort's belt, wrapping
the corners of its works — is cut along its length into stretches of about that
frontage, each grown connected from the end the run is measured from, so a ring
round the works or a branching trench is never one stretch on two sides of the
fort. Stretches of one run that touch are **neighbours**. A stretch has its own
posts (the run's cells within the circle a squad mans it through), its own
covered influence blocks and so its own pressure, and its own manning, release
and lockout; the order that mans it still names its run, so claims stay on the
trench. The cut is derived from the run alone and rebuilt only when the run
changes, the same for every run whether a fort's or not. (A line order's
fire-team stretch is a different thing: a share of a drawn line, not a place.)

Garrisonables may be **linked into layers**: a belt in front of a fort's works,
a compound perimeter around its buildings, a building shell around its rooms. A
layer is what a squad falls back to when the one in front of it is lost. A
compound place has three: its sandbagged door, the building's envelope behind
it, and the rooms behind that.

The registry holds the place, never its frontage. Which apertures stand open,
which are manned and whether a breach has dissolved the envelope are read live
by the squad holding it, as for any held building; the registry offers each
building once, against the map as it stands when first asked, so a later breach
neither renumbers nor drops it.

## Laws

- **Places, not forts.** The game is much larger than forts (owner,
  2026-10-20). Every mechanism here is built for places in general —
  compounds and their objectives, held buildings, compound aprons, trench
  stretches, capture points, story-mission sites — with a fort as one case
  among many, and is measured across several missions and mission types, never
  only on the Conquest fort trial ground. A mechanic that only a fort can use is
  a gap to close, not a design; a place is valued by what it means to its side,
  not by being a fort. Retaking a lost place is the counter-attack's
  (`conquest-command.md`, Defender command), for every kind of place.
- **Reactive manning.** A garrisonable is manned in proportion to believed
  pressure on it, read from the side's own faction influence, never from hostile
  truth. A quiet garrisonable is empty and its would-be garrison stays in the
  mobile pool, except for a token picket where a place must be watched to be
  defended at all: pressure is belief, and an empty place nobody can see raises
  none. A fort keeps a small picket at its main gate, and its guns report what
  they engage, so even an unmanned fort raises the alarm that brings its
  garrison. This generalises what standing to already does for a held
  building's apertures, from "which windows" to "which places".
- **Hysteresis.** Manning starts above one pressure level and ends below a lower
  one, after a dwell, so a squad is not marched in and out of a trench on every
  command pulse. Dig-in is lost on leaving, which is the cost that makes churn
  visible.
- **Declared holds stay held.** A place the mission says must be held — a keep,
  a capture objective's garrison — is born manned as today. Reactivity is for
  everything else. A building whose own garrison still stands in it (the
  defender's born garrison, the squad a captured compound is sent) counts as
  manned, so pickets before massing sends it a reactive squad only as its
  second, under the pressure that wants one, and re-garrisons it once that
  garrison is gone.
- **Pickets before massing.** Across garrisonables as across apertures: every
  threatened place gets a squad before any gets a second, and the reserve
  yields to that rule rather than outranking it.
- **Fall back through layers.** A squad whose place is breached (enemy inside
  it), destroyed below its useful size, or untenable falls back to the next
  layer in if one is linked; with none, it releases to the commander. A fall
  back is squad-tier movement under an existing assignment, not a new order.
  A breach is read against the whole run, not the stretch: an enemy a few cells
  along the same trench, past the stretch's end, is inside it. A building's
  envelope is breached by a believed enemy in its footprint (or on one of its
  posts) near the squad; its garrison then holds the rooms, as an area defence
  inside the building, until the building is quiet or the compound is taken.
- **Hold the stretch facing the threat.** A place is pressed only where a
  believed contact is within reach of it; the influence field's spread alone,
  which carries a company's weight across most of a fort, presses nothing. A
  pressed stretch reads belief over its own ground and its neighbours', so the
  stretch an attack is coming toward along the trench is manned before the
  attack is on it, and of stretches pressed alike the one most pressed on its
  own ground is served first. A garrison whose own stretch the attack has left,
  beside an unmanned neighbour of the same run whose own ground the attack now
  presses, **shifts** onto the neighbour rather than waiting out the dwell and
  being given back: squad-tier movement under the assignment it holds, not a new
  send, and never while it is in a fight. A squad shifts one stretch at a time
  and not again for a few pulses.
- **The alarm holds what belief cannot see.** A fort's alarm floor (place
  alarms, `ai-nouns.md`) holds the fort's stretches only while belief has woken
  none of them: an unseen attack keeps what the fort holds, and once the attack
  is seen anywhere on the fort belief says which stretches it presses and the
  rest cool. A squad still marching to a stretch is turned back by an enemy in
  that stretch or one beside it, not by one anywhere on a long belt.
- **Release when the threat passes.** A commander-assigned garrison returns to
  the pool when its pressure has decayed past the release level, so the pool is
  available for the next threatened band.
- **Scale by locality.** With many garrisonables, the commander considers only
  those inside or adjacent to threatened bands, on its own command cadence.
  Nothing scans every garrisonable every tick; candidate lookup is spatial.
- **The player's version is manual.** The occupy order is the player assigning
  a garrisonable by hand — the run under the click, within the defend circle
  round the click, which is a stretch by construction, so it is not cut again — and a dragged line order assigns every fighting
  position along a drawn frontage, one fire team per stretch, counting the
  forward-facing door and window posts of buildings the line crosses. A player's
  squad may opt into reactive manning with the GARRISON stance, never a default:
  it mans the pressed places round where it stands, by this doc's laws, and yields
  to any order the player gives (`ai-nouns.md`, under fighting positions). A machine given
  the same click on a place its side believes is enemy-held assaults it by fire
  from outside instead.

## Measuring cheaply

Pressure is the side's own hostile influence, already maintained in 8-cell
blocks and published as a snapshot; nothing new is computed per cell. Each
garrisonable (each stretch) records the few blocks it covers, and a block-to-garrisonables
lookup is derived beside it, rebuilt only when the prepared ground changes and
never per tick. On each command pulse the commander walks only the blocks its
believed contacts stand in, prices the garrisonables those blocks can press,
and re-prices only the ones already manned; a manned stretch no believed
contact is near reads no belief at all. The work grows with the size of the
fight, not with the number of garrisonables, and hysteresis state exists only
for manned places. Measured on the fort control it is a few hundredths of a
millisecond per pulse once warm.

## Flow, then local assignment

Manning answers pressure only if a squad can reach the place while the pressure
lasts, and a patrol sent from across the map usually cannot. So the commander
works in two tiers.

- **Flow** moves forces toward pressure before they are needed. Believed
  pressure and fighting in an area pull free squads from the mobile pool to a
  staging point on their own side of it, still in the pool and still free to
  be sent elsewhere; a quiet area gives them back after a long dwell. Flow is
  the anticipation tier and the only one that crosses the map.
- **Local assignment** mans garrisonables and does other local work only from
  squads that can arrive in time, estimated cheaply from distance and speed.
  With nobody local it sends nobody rather than a doomed march, and a squad
  already closing is not recalled by a lull.

Flow is what makes local assignment work: it changes who is local. Both run on
the command cadence from the same pressured-block walk.

Pressured blocks cluster into **areas**, each with a believed hostile weight.
An area asks for squads in proportion to that weight and to its pressed
garrisonables nobody holds, less the squads already local to it. Its staging
cell sits a standoff behind the area along the side's own rear, or at a held
fort's works when the area presses that fort's belt. Every area with nobody
local gets one squad before any gets a second, starting and keeping a post have
different thresholds, and an area that stays quiet returns its squads.

The track rally and flow are one **per-area reservation**. A threatened track
maps onto its heaviest area; when that area has something to man, the rally
goes to its staging cell (a fort's works for a fort's belt), and otherwise to
its old band point, so a map with nothing to man keeps the plain rally. Each
squad is reserved to one area at a time — rallied, staged or manning — and
counted once against its demand. Local assignment draws the area's own
reserved squads first, then the roving pool above its reserve floor. Areas with
nothing to man are not staged for.

A squad below useful strength (half its starting strength, and never fewer
than two) is never sent to man a place, and one that falls below it falls back: sending one only
for it to be released the next pulse was the main source of churn. A breach
that makes a garrison fall back is a believed enemy inside the place near the
squad, so one at the far end of a long run, or merely beside it, is not.

A track's rally does not swing back to the post it has just left for a few
pulses (`conquest-nouns.md`). When an area's pressure wavers about its
threshold, the rally otherwise alternated between the area's staging cell and the
track's band point, and between a remembered contact's band and an alarmed fort's
works; each swing was a new route for every rallied squad.

The assignment tier knows nothing of which side it serves. It is given a side's
own pressure picture, its free squads and its places, and answers which squad
mans which place and when it stops; the defender commander is its first caller.
That is the seam the attacking commander uses (below). A player's opted-in squad
answers the same question for itself, one squad and the places round it, with
the same thresholds (`ReactiveWatch`).

## Holding taken ground

The attacking commander mans the belt of a fort it **holds** — disclosed to
it, with no aspect held by a hostile side, which on the attacker is a fort it
has taken — through the same tier and its laws, from its lane pool and only
against a believed counter-attack. What differs is what it may spend and what
counts as pressure:

- **Momentum first.** Only lane squads are offered: never a company, a squad
  committed to a capture, a named officer's group, a crew, a machine or a
  demolition squad. At most a quarter of the offered pool holds at once (none
  when it has fewer than three squads, at least one otherwise), and in the
  convergence phases every holder is given back to the last push. The arrival
  bound means only a squad still near the works is sent; one that has moved on
  up the lane is not recalled. A squad already fighting the counter-attack is
  sent, since taking the trench is how it fights it, and a holder outranks a
  muster recruiting.
- **A counter-attack is a mover.** Only a believed hostile that is not an
  emplacement wakes a place, and a place is priced only where a believed mover
  is within reach. The nests an assault leaves standing beside a taken belt
  would otherwise press it for the rest of the battle.
- A holder driven off a run the enemy is believed inside falls back to the
  fort's works like one still standing on it.
- A taken compound's buildings are offered too, and the squad a captured
  compound is sent to hold it counts as manning its building, so the lane pool
  re-mans a taken building only once that garrison is gone or the
  counter-attack presses it hard.

The scene `attacker-holds-ground` plays it on a production fort handed to the
marines: a quiet belt left empty, a stretch pressed by a counter-attack manned
from the pool within its share, dug in and given back after it passes, and a
breached holder falling back to the works.

On the trial ground (nine on-path fixtures at the combined-arms force, seeds 41
to 49, against a same-build control with
`-Dbattle.conquest.attackerHoldsGround=false`) the fort was taken in all nine
and the hold fired in five: one to three squads at once,
between one and nine manning orders a battle where it fired, holders dug in
in three of the five, seven fall backs in all. Every battle was won in both arms and no fort was ever won back,
in either arm, so the hold was insurance the defender never tested. The keep
fell 84 ticks later on the mean of the five (from 960 earlier to 1,050 later)
and the marines lost 3 more on the mean (from 8 fewer to 18 more): inside seed
noise, so the share does not cost the attack its momentum, and it does not yet
buy anything measurable either. Every fixture in which it never fired (the
four other on-path seeds, the offset fort, which is never taken because the
company goes round it, and the rifles-only forces, which never take it) played
out exactly as its control. The defender now
counter-attacks any lost place, a taken fort or a taken compound, in proportion
to what it has observed holds it or not at all (`conquest-command.md`, Defender
command), and its reserve may man its own place's garrisonables only from what
it holds above the reserve law's share.

## How it fits what exists

The defender commander already raises a threatened track and forward band from
contact and rallies a bounded number of patrols into it (`conquest-nouns.md`).
A garrisonable inside that band is a better destination for a rally than a
point on the track: the rallied squad mans it and digs in instead of meeting the
attack in the open. That is the natural first consumer.

Standing to is reactive manning of a building envelope by the squad holding
it, and it stays exactly that: the commander tier now decides which squad holds
which building and when it stops, and the order it gives is the ordinary hold
of the building, so everything frontage decides is decided where it always was
— what an aperture may open onto, which layer a squad holds (a compound's
perimeter first for a squad holding the whole compound, its own shell
otherwise), the picket on every threatened facing before a second post, the
reserve, and the breach that dissolves an envelope and hands the fight to the
room-clearing behaviours. The player's occupy click on a held place and its
standing to are the same order and unchanged; the GARRISON stance gives that
order where it used to search held places of its own.

## Beyond Conquest

The tech is meant for story missions as much as for Conquest, and
`gradlew.bat garrisonEvidence` inventories what each story map offers at tick
zero: per held place, its windows, entrances and stand cells at building and
compound scope, and its fighting-position runs, plus the windows and runs no
held place covers. Three things it shows shape the work ahead:

- Buildings already carry windows in useful numbers; a large barracks or armory
  offers ten to twenty-five stand cells, a small post two to four, and the
  smallest guard posts and bunkers none.
- Story maps used to publish **no held places at all** (about half of them). Their buildings
  have windows, but nothing names a place a garrison could hold, so neither the
  defender commander nor a player's occupy order can use them. Held places on
  those maps are the precondition for everything in this doc.
- Fighting-position runs were almost absent outside forts. Relief digs one
  behind its gate, and compounds now lay a sandbagged door at each place's
  attack-facing entrance: on five Conquest seeds 98 aprons on 138 compound
  places, where there had been none, and three on Relay Nine where there were
  none. The campaign's Conquest of places laid none until it took the same stage
  (43 on 110 compound places over five fixture seeds with forts; none of them
  read as a fort's belt).
- Buildings were garrisonable only through a held place's occupy order. Every
  held place and compound place whose building has a frontage now offers its
  envelope: on the five Conquest seeds 110 of 110 compound places, with a median
  of four posts; on the story maps three to forty each; on the procedural
  missions every held place — Raid 33 (235 posts), Assault 44 (245), Sabotage
  36 (209), Extraction 41 (263), civilian rescue 36 (205). What offers none is
  what has no frontage (a beachhead, the smallest guard posts and bunkers) and
  the guard posts and gates that keep a garrison of their own
  (`gradlew.bat garrisonEvidence`, `CompoundEnvelopeEvidence`).

A story map closes the second gap by publishing its buildings as **held
places**: each building it already records becomes a held position on its own
footprint, entered at its interior anchor. A held place is not an objective — it
has no capture record, marker or supply gate — and it asks for no garrison, so
publishing one changes nothing until somebody is sent to it. That is
deliberate: a building is manned in answer to threat or by order, by the same
law as a trench. Every story map publishes them through `HeldPlaces`: authored
maps with a garrison layout keep their garrison nodes and add held places only
for the buildings those nodes do not already stand on, fort maps included. Procedural cities add them at setup: Silent Colony and civilian rescue civilian-held, Assault, Sabotage,
Raid (the Veteran's Job included) and Extraction defender-held, keeping their garrison nodes. A held place nobody is
posted to (`HeldPlaces.isUnposted`) is not somewhere the defence lives: defender allocation, the assault
commander's strongpoints, patrol waypoints and the garrison map feature all skip it. A story map with
no buildings (Missile AA) has none. The first consumer is the player's occupy
order. On any map that publishes held places, a click on a building holds it
at its windows (`ai-nouns.md`, occupy). The second is the GARRISON stance, which
mans those buildings under pressure by that same order on every such map
(scene `mission-envelopes`: Raid, Assault, Sabotage, Extraction, civilian
rescue and a story map). The third is the mission defenders' own commanders
(below).

## Mission defenders

The Raid, Assault, Sabotage and Extraction defender commanders man the
garrisonables of their maps reactively, through the same side-neutral tier and
laws as Conquest's defender (`MissionGarrisons`, one per commander). The answer
to the old open question is yes: a defender who meets a raid in the street when
the building beside it has windows is a defender left standing about, and the
fun of these missions is the target network holding out from its buildings.

- **The mission decides first.** Each commander plans its duties exactly as its
  own command doc says (`raid-command.md`, `assault-command.md`,
  `sabotage-command.md`, `extraction-command.md`) and then offers squads. Its
  held reserve is offered **roving**, and the reserve floor applies to it. A
  squad on a stationary mission post — the Raid target's guard and responders,
  an Assault routine post or responder, a Sabotage site post, the Extraction
  source guard and perimeter responders — is offered on **duty**: it may man only
  a place within a dozen cells of its post, so the post is held from a window
  rather than given up, and it is not the reserve the floor protects. A squad
  the mission stops offering — its alarm took it, its duty moved, its area
  changed — is forgotten that pulse: the mission outranks the place.
- **Never offered:** authored garrisons and declared holds (born manned, and a
  building whose born garrison stands in it counts as manned), external owners,
  Assault's contact response when it is not on a post, Extraction's
  interdiction, crews, demolition squads, machines (which guard instead).
- **A squad in a fight takes the cover beside it.** A squad in local contact
  may be sent only to a place within a few cells of it, and is not held to the
  reserve floor: taking the building is how it fights the fight it is in. A
  march across the map to a pressed building is never sent; the missions have
  no flow tier, so the arrival bound is what keeps manning local.
- **Layers:** off an apron into the envelope of the building it fronts, out of
  an envelope into its rooms until the building has been quiet for a while,
  then back to the mission.
- **Machines.** A free lance the mission holds in reserve, or one fighting near
  the place, is attached to a manned, pressed place by the machine guard's
  director and bounds, and stands down with the garrison; while it guards it
  carries no mission order.
- A mission's published picture keeps each squad's mission role and names the
  manning in its reason, so the mission's own memory of routine and response is
  read as before. `-Dbattle.mission.reactiveDefence=false` is the control and
  plays the battle as it was.

The Veteran's Job and Cold Account run these commanders and inherit it. The
bespoke story commanders give every defender squad an authored duty and are
left as they are (an open question below).

The scene `mission-defence` plays it on the Raid's and the Assault's production
maps with the mission's own commander: a building pressed by a party is manned
from the pool by the commander's envelope order, a holder stands to at its
windows, and the building is given back within the dwell once belief of the
party cools; in `raid-lances` a reserve lance guards the manned building from
outside it, within the bound, and stands down with the garrison.

Measured on six seeds per mission (the canonical fixture and five offsets)
against the same-build control, with `commanderEvidence`:

| mission | manning orders | releases | fall backs (rooms) | marine losses on / off | defender losses on / off | outcomes on / off |
|---|---:|---:|---:|---:|---:|---|
| Raid | 18 | 4 | 3 (3) | 123 / 106 | 125 / 115 | defender 4, marines 1, timeout 1 / 3, 2, 1 |
| Assault | 23 | 3 | 9 (9) | 351 / 370 | 242 / 237 | timeout 5, defender 1 / the same |
| Sabotage | 19 | 4 | 4 (4) | 78 / 68 | 92 / 106 | defender 4, marines 2 / 3, 3 |
| Extraction (alarm fixture) | 28 | 5 | 5 (5) | 141 / 133 | 196 / 188 | marines 6 / 6 |

One to five squads held at once; no reserve lance existed on these fixtures, so
no guard was attached outside the scene. Outcomes moved by a seed or two
either way, inside the noise of six seeds: the defence reads as buildings held
and given back rather than as a different balance. Sabotage's battles ran
shorter (18,086 ticks against 21,226 over the six) and Extraction's last seed
ran longer (4,549 against 2,585).

## Decided direction

Owner, 2026-10-20: where a choice is open, prefer the one that adds fun.

- **Attackers hold what they take.** Captured ground is a garrisonable like any
  other: the attacking commander uses the same reactive assignment to man a
  taken belt, trench or building against the counter-attack, not only manual
  orders. Built for taken fort belts (see Holding taken ground), and for the
  aprons and buildings of taken compounds (scene `taken-compound`).
- **A player's idle squads may opt into reactive manning.** It stays opt-in,
  never a default; the manual occupy and line orders remain the primary verbs.
  Built as the GARRISON stance: a per-squad switch, a watch ring the size of a
  defend area round the squad's anchor, the runs and held buildings on its own
  side's ground inside it, and the commander's own thresholds applied at squad
  scope (`ReactiveWatch`). The fun is the delegation: it reads as a squad given a
  corner to watch, which jumps into the trench or building the enemy leans on and
  walks back when the pressure passes.
- **Machines assault garrisons; they never man them.** Mechs and vehicles take
  no posts, but their weapons make them the natural answer to a held place: an
  order onto an enemy-held building or position is an assault by fire — engage
  the believed occupants at their apertures and posts, and, where the weapons
  can, bring down the walls that shelter them — from a stand-off, never by
  entering. Infantry still does the clearing and the holding. The player's
  version is built: a mech, lance or armed vehicle clicking a run or held place
  its side believes is enemy-held gets ASSAULT (`ai-nouns.md`, machine assault
  under fighting positions). A commander's use of machines against a garrison
  is not.
- **Machines screen the places their side mans.** Owner, 2026-10-20 ("seems
  reasonable"): a mech, lance or armed vehicle stands beside a garrison as its
  heavy support — on a cell outside the place, off the garrison's lanes of
  fire, covering the believed threat axis with its own doctrine or turret, never
  entering — for the player as a GUARD order on a place the side mans, and for
  the commanders as a bounded attachment of a free machine to a manned, pressed
  place. Built (`ai-nouns.md`, machine guard under fighting positions; scene
  `machine-guard`): the fun is a tank and a rifle squad reading as one position,
  and a defending lance walking up beside the trench the attack leans on. A
  guard screens the stretch of a run under the click, not the whole belt, which
  is the same finer place the open question below asks for. A manned building
  draws a guard as a manned run does: the player's click on a held place or a
  compound building its side mans, on any map, and a commander's attachment to
  a building envelope it mans or a building its own garrison stands in, under
  the same bounds (scene `machine-guard`, loops `mission-building` and
  `mission-building-commander` on the Raid's map).

## Stretches on the trial ground

The question stretches answer was why, on the trial ground's fort fixtures,
about half of the belt-garrison samples taken while the company stood at a fort
found no marine within thirty cells. Split by what the garrison was doing, most
of that half was never a garrison left behind: it was squads still **marching**,
and on whole runs most of the marches went to ground no marine was near. The
reading that matters is the **stranded** share — a garrison arrived on a stretch
no marine is within thirty cells of, or marching to one.

Measured on 76 fixtures (40 fort-on-lane at the combined-arms force, seeds 41 to
80; 22 rifles-only on the lane; 14 offset, where the company goes round the fort
and almost nothing is manned) against a same-build control with
`-Dbattle.garrison.stretches=false`:

- stranded garrison samples fell from 41% of the samples to 10% (3,282 to 481),
  and garrisons sitting on a stretch nobody was near from 2,111 samples to 212;
- the coarse half-share itself fell from 55% to 45%; what is left of it is
  squads marching to the fight (1,737 of its 2,218 samples), which arrive;
- the defender spent 39% fewer squad-samples on garrisons (8,064 to 4,902) and
  its dug-in members sat mostly where the fight was: dug-in samples on
  a garrison no marine was near fell from 9,870 to 926;
- outcomes did not move: marines won 51 against 52, the defender held 25
  against 24, the keep fell at the same mean tick (9,122 against 9,103), and
  losses were level (marines 3,393 against 3,412, defenders 3,158 against
  3,128).

So the belt is now held where the attack is and the pool is not spent on quiet
trenches, but the battle's result is not decided by it on this ground. The scene
`reactive-belt` plays the case in its `moving-attack` loop: once the gate
stretch's garrison is dug in, the attack moves round the fort to a stretch
beyond reach of the first; the new stretch is held and the old one given back
within a few pulses. Its whole-run control holds the gate run for the rest of
the battle and never mans the new ground.

## Sally doors

A holder inside a walled place it holds (a fort's works, a walled compound or
yard, a building with few exits) reached the ground it owns beyond a gateless
face by walking out of its one gate and round its own walls. That was the
"marching late" reading below at its worst: a garrison in a fort's works walked
up to four times the straight line to a belt stretch beyond a gateless face, and
a stand-to sent round the walls arrived after the attack it was sent against.
The answer is a door the holder opens from inside, not a faster march.

A **sally door** is a short span of a walled place's own wall, two cells along
it and as deep as the wall (one or two cells), standing closed as wall. Two
cells is wide enough for every chassis that fights on foot, so the holder's
machines go out by it too. Where doors are:

- a fort authors a **sally port** on each face of its works with no gate
  (`forts.md`, sally ports);
- every other tactical node's building and compound get one per face where
  `SallyDoorSurvey` finds a clean span of built wall with the place's floor on
  one side and open ground on the other, nearest the face's middle, **and**
  opening it saves the holder at least twenty cells of walk from its anchor.
  Only a walled place qualifies: built wall along at least half the edge of
  the place's rectangle. Open ground with somebody else's wall at its edge (a
  landing zone beside a warehouse) gets none; neither does natural rock, a
  doorway, or a fort's works outside its authored ports.

**Laws.**

- A door serves the side that holds its place **whole**: a fort's side holding
  every aspect; a compound's side by its capture record, uncontested; any other
  place, the side with people inside it and nobody hostile inside. A sortie
  often empties a small place, so a place nobody stands in still serves the
  side that last held it whole until somebody hostile to that side comes in. A
  split or contested place opens and shuts nothing.
- It opens when that side has a squad or a machine ordered through it: a member
  inside the place ordered to ground beyond the door's face, outside the place
  and within reach of the mouth, or a member out on that ground ordered home
  into the place, by a commander or by the player, and only while the door is
  still ahead of the body and of its squad (a squad that has reached its ground
  does not hold its door open for a straggler on the works). Never while a
  hostile body stands near its mouth: a door is not opened in the face of the
  enemy.
- Opened, it is built passage (not rubble). The same side **shuts** it again:
  at once when a hostile body comes as near its mouth as would have kept it
  shut, and when the sortie has passed, nothing of the side's having wanted it
  or stood near it for five seconds. A sortie is therefore a choice taken for
  as long as it is needed, not a hole made for good: the attack is given a way
  in only while the holder is using it. A door that changed state does not
  open again for three seconds, so it cannot flap; shutting in the enemy's face
  is never held.
- **A door never shuts on a body.** Anybody of any side standing in or across
  its cells keeps it open; it shuts on the first look that finds it clear.
  Nothing is pushed out or crushed.
- **The holder losing the place shuts nothing.** A door left open when its
  side loses the place stays open while the place is split or contested, and
  then serves whoever holds it whole: the hole a sortie made is the attack's if
  the sortie goes wrong, which is its risk. Shutting on loss would hand the
  loser a free wall at the moment it lost and cut the assault's routes under it.
- Shut, it is wall with the wall's strength, breached as any wall is: an
  attacker forces a shut door by breaching it, with what any wall costs, and is
  never given a door it did not cut. A door is not made weaker than its wall,
  which would make every door the attack's way in, nor stronger, which would
  make it a keep. A door breached while shut is broken, a hole for good. A
  place that changes hands serves its new holder: marines who take a fort
  sortie by its ports and shut them behind them.
- **The player's bar.** The player bars or unbars a door of a place their side
  holds whole (key B over it). A barred door shuts now, or as soon as nobody
  stands in it, and nothing opens it; an unbarred door opens at once, the enemy
  near or not, and the side's reflexes leave it open. Either lasts until the
  player orders the door again or the place changes hands.

**Shutting is the one runtime write that closes ground.** It puts every door
cell back exactly as it stood before it opened (its wall, hit points, ground and
surface, and the cover it gave), and every derivation of the map sees the cell
close: the grid's revision and change log retire path components, line of sight,
the vehicle clearance mask and its labels, the greedy mesh, clearance searches,
route proofs and the commanders' frozen topology as they do for an opening; the
zone graph, whose incremental update only merges, is rebuilt whole at the tick's
flush; and every route that crosses the door is dropped, so nobody walks into
the wall and whoever meant to go through plans again.

**Measured** (`garrisonEvidence`: `HolderRouteEvidence`, `FortWorksRouteEvidence`;
map evidence, every door opened against none). From each node's anchor to the
garrisonable stretches on its ground and to the open ground just beyond each
face, the worst detour (walk minus straight line) fell on Relief from 51 cells
to 16 (the checkpoint's yard wall), on Counterattack from 77 to 24, on Relay
Nine from 22 to 21 with every face detour but one gone, on Junction from 20 to
17, and on Conquest's map from 71 to 53 (a walled armory's north face 71 to 1);
Salvage Rights and Cold Start had none and get none. A fort's garrison walks at
worst 3.0 times the straight line to its belt instead of 3.8 (battery fort) and
2.7 instead of 4.3 (shield fort). What is left is not a holder's own wall: map
edge pockets behind a diagonal perimeter, and walks across a town block where a
stretch stands in a neighbour's yard (Dust Ledger's ten, the Conquest keep's
block).

**In play**, against a same-build control with `-Dbattle.sallyDoors=false`
(76 trial-ground fixtures: 40 fort-on-lane at the combined-arms force, seeds 41
to 80; 22 rifles-only on the lane; 14 offset): the fort's ports opened in 68
places, 39 times for the defender's own garrison and 29 for marines who had
taken the fort and went on to the keep. Garrisons stranded on ground nobody was
near fell on the rifles-only fixtures from 121 samples to 53; on the
combined-arms fixtures, where the attack comes at the gate faces and little is
stranded, the marching share near a fight did not fall (424 to 489 samples
marching to the fight, most of them through a gate the port does not replace).
Readings: marines won 36 against 31 on the lane (defender holds 4 against 9),
of the five extra wins four came after the marines had opened a port of a fort
they had taken; marine losses 2,305 against 2,379, defender losses 2,226
against 2,164; the offset and rifles-only results were level. Relief,
Counterattack and Junction played byte for byte as their controls: their
defenders keep authored posts and never order a sortie beyond a doored face,
so their doors wait for a player's order (scene `sally-door`, taken yard) or a
reactive commander.

The scenes are `sally-door` (a walled yard that is not a fort: a sortie by
infantry, the walk round with doors closed, the enemy at the door, an attack on
the door, a taken yard where the player sends a squad and a Sirocco out, a
contested yard, the door shut behind a sortie, shut in the face of raiders
marching on it with no route left through it, barred by the player over a body
standing in it, and a round trip out and home) and `sally-port` (the fort, with
the defender's commander, and the port barred behind a sortie and in the face
of raiders come up to it). Every loop asks that no door shuts on a body.

## Open questions

- **Marching late.** A stretch is pressed only when an attack is near it, and
  the pool waits at the works, so a garrison often walks forty to sixty cells
  and arrives as the attack does: half of the garrison samples near a fight are
  squads still on the way. The walk round a gateless face is answered by sally
  doors (above); what remains is the walk out of a gate to the gate's own face,
  which is the march itself. Staging nearer the belt is the likely next step; a
  garrison stand-to at the work being lost, sent round the walls, was tried and
  cost more than it saved.
- **Alarm-held stretches.** What little stranding is left is mostly a fort's
  alarm holding stretches while the defender believes nothing anywhere near: an
  attack inside the works, unseen. Whether the floor should fall to the
  stretches nearest the works being lost is open.
- How a garrisonable is valued against an objective: a trench in front of the
  keep is worth more than one at the map edge under the same pressure.
- **Aprons on a Conquest of places.** Two in five compound places there get an
  apron, against seven in ten on a stock map: the places' circulation roads run
  close to their doors. Whether to let an apron stand on a circulation road the
  attack does not use is open.
- **Story commanders.** The bespoke story commanders (Junction, Relay Nine,
  Black Sluice and the rest) give every defender squad an authored duty and
  claim nothing else; a story map's unowned patrols could man its buildings
  under the same law, but a story commander reads its own issuer to know which
  squads are its own, so the claim needs a seam that does not enrol them.
- **Mission flow.** The procedural missions man only what a squad can reach
  while the pressure lasts, and have no staging tier to move the reserve toward
  pressure; whether they should is open.

## Stories


- `fort-sandbag-damage-art.md` — real art for the placeholder fort sandbag damage
  states.
