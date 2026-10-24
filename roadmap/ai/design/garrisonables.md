# Garrisonables

Status: ACTIVE — reactive manning is shipped for fighting-position runs, a stretch at a time, and for building envelopes, on both Conquest sides (the defender's own belts, compound aprons and compound buildings, the attacker's taken ones) and for a player's squads that opt into it, the player's machine assault on a believed-held garrisonable is built, and machines guard the places their side mans, runs and buildings alike (player on any map, both Conquest commanders on any ground they man), and the Raid, Assault, Sabotage and Extraction defenders man their maps' places reactively too and stage their roving reserve toward pressure, half at the places and half forward in the street, their lances guarding what they man, and the story commanders offer their standing-duty squads to the same planner within a share, and a holder sorties from a walled place it holds through a sally door in its own wall; any party, an employer's constables or militia among them, mans places through the same planner by its own picture, and a mission speaks to a party through place directives (hold, lock, counter-attack) with generic triggers; the compound perimeter and emplacements remain direction.

Written: 2026-10-13

Updated: 2026-10-21 — a mission defender's duty squads are bound: a post takes the building beside it only under heavy pressure and at most a third at once, which brings Sabotage's fight mix back to within a few points of no manning (mission defenders, fight mix by mission); the Raid, Assault, Sabotage and Extraction defenders push for a place they lose, bounded, and read counter-attack directives (mission defenders, place directives); parties are sides of the garrison system, a mission speaks to any party's commander through place directives and their triggers, and a locked position is a place a party must take and hold (parties, place directives, locked positions, parties wired); the fight mix is measured on every mission against each garrison feature's control, and building envelopes are what move it (fight mix by mission); the trial-ground hold and mission-defender figures are refreshed on the current build; the mission defenders stage their roving reserve toward pressure, half waiting at the places and half meeting the attack in the street (mission defenders, flow); every story commander has a commander-evidence harness, A Small War's commander offers its posts clear of the stages and the other eight have no standing hostile duty to offer (story commanders, below); story commanders man reactively through a hosted planner and a holder share; a sally door shuts again: its holder shuts it behind a sortie, in the face of an approaching enemy and by the player's bar, never on a body, and an open door of a place its holder lost stays open (sally doors, below); not every fight is into prepared positions is a law; a holder sorties from a walled place it holds through a sally door in its own wall (sally doors, below); the procedural missions' defender commanders man garrisonables reactively (mission defenders); machines guard manned buildings as they guard manned runs (the player's click on any map, the commanders' attachment to a manned envelope or a building its own garrison holds); building envelopes are garrisonables (apron, envelope, rooms as layers; a building's own garrison counts as manning it); runs are garrisoned a stretch at a time; the defender counter-attacks any lost place (a compound, a fort as one case) in proportion to what a probe observed holds it, and its reserve lends to its own place (`conquest-command.md`); places, not forts, is a law; compounds offer prepared ground, on the campaign's Conquest of places too; machines screen the places their side mans.

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
  frontage laws and walks the rooms when they are quiet or entered. A fort's gate
  towers are such buildings: slit only where they look out of the works, so a
  squad holding one stands to in its slits over the approach (`forts.md`, scene
  `gate-tower`);
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
- **Not every fight is into prepared positions.** Always assaulting dug-in
  defenders is not fun; sometimes a brawl is (owner, 2026-10-21). Garrisoning
  is one texture of a battle, never its default shape. A defender that mans
  every place it can reach, or stages its whole reserve behind them, turns
  every contact into a siege. Commanders must leave room for fights in the
  open: meeting engagements in streets and yards, sorties, a reserve that
  meets the attack forward rather than waiting behind a parapet, defenders
  caught moving between places. A manning or staging rule is judged by the
  mix of fights it produces as well as by its losses, and its evidence reports
  that mix (for example, the share of contacts fought by squads on posts
  against squads in the open) across missions. A change that drives the
  open-ground share toward zero is a regression even when it wins battles.
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

Flow is side-neutral in the same way, and it need not have a command axis.
Conquest's defender stages along its track axis, behind an area toward its own
rear. A commander with places and no axis (the mission defenders, below)
measures the staging cell **toward the places the area presses** instead: the
line from a threat to what it threatens is the defending side's by
construction, so the squad waits at the places when they stand a standoff or
more from the attack and a standoff out from the attack, behind them, when they
stand nearer (`PlaceFlow`). Areas, demand, pickets before massing and the
cool-down are the same pieces Conquest uses (`GarrisonFlow`); Conquest keeps
its axis staging and its rally table, so its figures do not move, and could
take the axis-free staging cell where a map has no clean axis.

**Not every fight is into prepared positions** shapes axis-free flow: an area's
staged squads alternate **back**, waiting at the places for local assignment
to man them, and **forward**, an area defence a short standoff from the attack
toward the places, which meets the attack in the street and is never sent to
man. The first squad of an area goes back (its places need somebody), and a
squad already manning in the area counts as back, so the next one goes
forward. Where the places stand too near the attack for any street between,
everybody waits back. At most half the roving pool waits at once, so a lone
reserve squad stays where its commander keeps it; this, not the reserve floor,
bounds staging, since a waiting squad is the reserve standing nearer the
pressure rather than spent. A squad fighting (shot at in the last moments, or
firing) is never staged; a squad that merely knows of an enemy is, since on a
mission's small map that is every squad once a sentry has seen the attack.

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
`-Dbattle.conquest.attackerHoldsGround=false`, 2026-10-21) the fort was taken
in all nine and the hold fired in four, with 21 manning orders between them;
the defender won the fort back once, by its ordinary defence, in both arms.
Marines won eight and timed out once in both arms, losing 541 against 527:
inside seed noise, so the share does not cost the attack its momentum and does
not buy anything measurable on this ground either. At militia 90 it fired in
four of the seven battles in which the fort fell, marines winning all nine in
both arms (529 lost against 531). On Conquest it matters more: with the hold
off the marines lost 524 against 384 over the two fixtures, both timing out in
either arm. The hold is almost never pressed by a counter-attack, because the
defender has nothing left to push with when a place turns
(`conquest-command.md`, a place the defender loses, which counts how often a
launch meets a hold). The defender counter-attacks any lost place, a taken fort
or a taken compound, in proportion to what it has observed holds it or not at
all, and its reserve may man its own place's garrisonables only from what it
holds above the reserve law's share.

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
- **A post is defended from the open first (the duty bound).** A duty squad
  takes a place only when it is pressed **harder than a reserve squad needs**
  (5 against the ordinary 3: above the start, below the pressure that wants a
  second squad), and **at most a third of the squads on duty hold a place at
  once** (at least one from one duty squad up). A post is covered by the squad
  standing on it whether or not it stands behind a window, so taking the
  building beside it changes the texture of the fight at the post rather than
  whether the post is held; and a commander that puts most of its force on
  posts, as Sabotage does, otherwise turns every contact at a post into a
  siege (fight mix by mission, below). A post pressed hard still takes its
  building, which is the reactive defence the bound keeps. The roving reserve
  is not bound by it, since flow already sends half of it forward into the
  street, and the story commanders bound their own holders with a share of
  their own (story commanders, below). It is one rule for every mission
  defender rather than a Sabotage special case: on Raid, Assault and
  Extraction it trims a few manning orders and leaves the fight mix where it
  was. `-Dbattle.mission.reactiveDefence.dutyShare=1` with
  `-Dbattle.mission.reactiveDefence.dutyPressure=0` is the control; either
  alone switches off one half.
- **Never offered:** authored garrisons and declared holds (born manned, and a
  building whose born garrison stands in it counts as manned), external owners,
  Assault's contact response when it is not on a post, Extraction's
  interdiction, crews, demolition squads, machines (which guard instead).
- **A squad in a fight takes the cover beside it.** A squad in local contact
  may be sent only to a place within a few cells of it, and is not held to the
  reserve floor: taking the building is how it fights the fight it is in. A
  march across the map to a pressed building is never sent: the arrival bound
  keeps manning local, and flow is what brings a squad near enough.
- **Flow.** Only roving offers are staged (flow, then local assignment, the
  axis-free form): the mission plans its duties, then manning answers what is
  hot, then free roving squads are staged for the areas pressing a warm place,
  back or forward. A staged squad is still offered, carries an area defence
  rather than a place, and is drawn first when its area's places are manned; a
  forward one is not offered to manning at all. A squad the mission stops
  offering is forgotten at once, staged or not. `-Dbattle.mission.flow=false`
  is the control (reactive manning as before) and
  `-Dbattle.mission.flow.forward=false` stages everyone back.
- **Layers:** off an apron into the envelope of the building it fronts, out of
  an envelope into its rooms until the building has been quiet for a while,
  then back to the mission.
- **Machines.** A free lance the mission holds in reserve, or one fighting near
  the place, is attached to a manned, pressed place by the machine guard's
  director and bounds, and stands down with the garrison; while it guards it
  carries no mission order.
- **A place the defender loses is pushed for.** The counter-attack goes
  ahead of manning on these commanders (`conquest-command.md`, a place the
  defender loses): the mission plans its duties, the shared counter-attack takes
  what it may of the held reserve and the quiet authored garrisons for its
  objective places and key buildings, and only then is the rest offered here.
  A squad in its group is never offered; a squad it lets go is offered roving
  again. It is bounded — half the side, two pushes a battle, gone at half of
  parity or not at all — so a capture is contested on these maps without each
  one turning into a siege of the defender's own building.
  `-Dbattle.mission.counterattack=false` is its control.
- A mission's published picture keeps each squad's mission role and names the
  manning in its reason, so the mission's own memory of routine and response is
  read as before. `-Dbattle.mission.reactiveDefence=false` is the control and
  plays the battle as it was.

The Veteran's Job and Cold Account run these commanders and inherit it, and so
does Last Lift, whose defender is the Extraction commander. The bespoke story
commanders come in through their own seam (story commanders, below).

The scene `mission-defence` plays it on the Raid's and the Assault's production
maps with the mission's own commander: a building pressed by a party is manned
from the pool by the commander's envelope order, a holder stands to at its
windows, and the building is given back within the dwell once belief of the
party cools; in `raid-lances` a reserve lance guards the manned building from
outside it, within the bound, and stands down with the garrison.

Measured on six seeds per mission (the canonical fixture and five offsets)
against the same-build control, with `commanderEvidence` (2026-10-21, flow on):

| mission | manning orders | releases | fall backs (rooms) | marine losses on / off | defender losses on / off | outcomes on / off |
|---|---:|---:|---:|---:|---:|---|
| Raid | 12 | 2 | 3 (3) | 112 / 140 | 146 / 137 | defender 3, marines 3 / defender 5, marines 1 |
| Assault | 20 | 5 | 4 (4) | 366 / 377 | 267 / 226 | timeout 4, defender 2 / 5, 1 |
| Sabotage | 21 | 3 | 4 (4) | 84 / 75 | 64 / 88 | defender 6 / defender 4, marines 2 |
| Extraction (alarm fixture) | 26 | 0 | 7 (7) | 136 / 138 | 197 / 200 | marines 6 / 6 |

These figures predate the duty bound (above); with it Sabotage sends four
manning orders over the six battles and Raid eleven (fight mix by mission). One
to five squads held at once; no reserve lance existed on these fixtures, so
no guard was attached outside the scene. Outcomes moved by a seed or two
either way, inside the noise of six seeds, and the defence reads as buildings
held and given back: on Sabotage the defender kills more and loses fewer, on
Assault it loses more, and Raid's defender with manning gives up two of
its wins against the control's.

The scene `mission-flow` plays the flow on the Raid's production map with the
Raid's own commander: six mobile squads far from a held place's building and a
sentry by it; a party seen a long way short of the building draws a reserve
squad to wait at it and the next one forward, before anybody mans it; a waiting
squad carries an area defence, never a place; and every waiting squad is given
back within the flow's dwell once belief of the party has cooled.

Flow measured the same way (six seeds a mission, against the same-build control
`-Dbattle.mission.flow=false`, and `-Dbattle.mission.reactiveDefence=false` as
the baseline with no manning at all). *Arrived first* is the share of manning
orders whose squad stood on its place before a believed contact reached it
(rather than being reached first, at the place or in a fight on the way); the
fight mix is `PlaceFightProbe`'s: the share of all contacts that are brawls
(both ends in the open), and the share of the defender's shots fired from the
open.

| mission | staged (forward) | manning orders, from staged | arrived first, on / off | marine losses on / off | defender losses on / off | brawls on / off / none | defender shots from the open on / off / none | outcomes |
|---|---:|---:|---|---:|---:|---|---|---|
| Raid | 9 (2) | 12, 4 (19 off) | 2 of 12 / 4 of 18 | 112 / 110 | 146 / 155 | 75% / 79% / 75% | 69% / 80% / 68% | unchanged (defender 3, marines 3) |
| Extraction (alarm fixture) | 10 (5) | 26, 3 (26 off) | 10 of 24 / 8 of 25 | 136 / 154 | 197 / 201 | 84% / 84% / 86% | 73% / 68% / 70% | unchanged (marines 6) |
| Assault | 0 | 20 | 8 of 15, the same | 366, the same | 267, the same | 76% | 79% | unchanged |
| Sabotage | 0 | 21 | 9 of 20, the same | 84, the same | 64, the same | 74% | 78% | unchanged |

Assault and Sabotage put nearly every squad on a post or a response on these
fixtures, so nothing roving was ever free to stage and both played byte for
byte as their controls. On Raid flow staged nine squads, four of which were
then the ones sent to man, and the commander sent a third fewer manning orders
(the waiting squad was already local); on Extraction ten, and the share of
garrisons on their place before the attack rose from a third to more than
two in five while the marines lost eighteen fewer. Brawls stayed three in four
of every contact on every mission, at the level of the baseline with no
manning at all; the defender's open-ground share fell on Raid to the baseline's
own level and rose on Extraction, so the mix did not drift toward the siege the
law forbids. With `-Dbattle.mission.flow.forward=false` (everyone back) Raid's
defender fired 76% of its shots from the open and Extraction's 72%: inside the
noise of six seeds. Conquest's flow was not touched, so its trial ground is
unchanged by construction.

## Fight mix by mission

The law that not every fight is into prepared positions is read here
(`PlaceFightProbe`, `commander-evidence-missions.md`, places and fights): a
**brawl** is a contact with both ends in the open, and "from a post" is the
share of the defender's shots fired from one. Each garrison feature against its
own same-build control, every fixture a mission's harness plays (six seeds for
the procedural missions, eleven for the stories, nine for each trial-ground
set, Conquest's two). *None* is every manning feature off
(`-Dbattle.mission.reactiveDefence=false`, and for Conquest and the trial
ground with envelopes, stretches, aprons and the attacker's hold off too). The
procedural missions' rows were measured on 2026-10-21 with the duty bound
(mission defenders, above) and the mission defenders' counter-attacks; *duty
bound off* is its control. The envelopes, aprons, stretches and flow columns
were played an hour earlier, before the counter-attacks' latest revision. The other rows
are from earlier the same day and the duty bound does not reach them.

| mission | brawls, all on | none | duty bound off | envelopes off | aprons off | stretches off | flow off | defender from a post, on / duty bound off / none |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| Raid | 85% | 75% | 83% | 70% | 84% | 84% | 78% | 22% / 23% / 28% |
| Assault | 75% | 78% | 77% | 77% | 80% | 80% | 81% | 21% / 21% / 18% |
| Sabotage | 88% | 91% | 80% | 91% | 88% | 88% | 88% | 11% / 20% / 7% |
| Extraction | 85% | 84% | 84% | 84% | 84% | 84% | 80% | 23% / 22% / 24% |
| Conquest | 69% | 66% | — | 62% | 65% | — | — | 21% / — / 22% |
| Trial, default | 32% | 33% | — | — | — | 33% | — | 53% / — / 52% |
| Trial, militia 90 | 42% | 39% | — | — | — | 41% | — | 49% / — / 50% |
| Relay Nine | 25% | 22% | — | — | — | — | — | 72% / — / 73% |
| Thunder Run | 46% | 46% | — | — | — | — | — | 30% / — / 30% |

**The stack has not driven the open ground away.** On every procedural mission
at least seven contacts in ten are brawls, and on Conquest and the trial ground
the stacked features leave the share where the control has it, a few points
either way. On every procedural mission switching envelopes off is the same
battle as switching manning off; aprons and stretches change nothing there.

**Sabotage was the one real drop, and the duty bound is what closes it.**
Without the bound, its site posts took the buildings beside their sites: brawls
fell from 91% with no manning to 80%, and the defender's shots from a post
rose from 7% to 20%, all of it building envelopes. Sabotage puts nearly every
squad on a post or a response, so nothing roving is left to stage forward and
every manning order was a post going indoors. A share alone was not enough (the sweep
was played the same day, before the mission counter-attacks, whose arrival
moved none of Sabotage's figures): at most a third of the posts holding at once (`dutyPressure=0`) still left 78%
brawls and 19% from a post, because one site post behind windows makes the
whole fight at its site a siege. Raising the pressure a post needs is what moves
it: at 4, 82% brawls and 17% from a post (eight manning orders over six
battles); at 5, 88% and 11% (four orders); at 6, 90% and 8% (one order); at 8,
nothing is ever manned and the battle is byte for byte the one with no
manning. Five is the bound: within a few points of the no-manning mix while a
post pressed hard still takes its building. Outcomes did not move (defender 3,
marines 3 against 4, 2 for the control; marines lost 75 against 78, the
defender 109 against 89).

The same rule on the other missions trims manning orders and leaves the mix
within a few points of its control: Assault sends 24 orders against 33, 75%
brawls against 77% (21% of the defender's shots from a post either way; two of
its six battles end inside the clock where the control's all time out); Raid
11 against 19, 85% against 83% (22% from a post against 23%, the defender
winning four against three); Extraction 21 against 19, 85% against 84%. These
two missions' figures moved by a few points between builds an hour apart
while Sabotage's did not move at all, which is the size of the noise on six
seeds. On Raid, envelopes keep fights in the open rather than taking them away
(85% brawls against 75% with none, and 22% of the defender's shots from a post
against 28%). Flow pulls
Raid's and Extraction's mix toward posts when it is off (78% and 80%), because a staged forward squad meets the attack in the street. On
Conquest envelopes push the other way too: with them off the share falls to 62%
and the marines fire from a post twice as often (14% of their shots against
7%), so there they keep fights in the open. The trial ground is a fort and was
always a siege; the stack does not make it more of one.

## Story commanders

The bespoke story commanders give every squad they own an authored duty, and
their lesson beats read where those squads are and what they are doing:
Junction's reserve walking onto the first gain, the depot's reserve committing,
Relay Nine's pool holding home. A story commander knows its own squads by its
own issuer on their mission directive, so a planner claiming squads from outside
would take them out of its roster for good, and the duty and the beat that hangs
on it with them. A shared planner put in charge of Junction's pool did exactly
that (`conquest-command.md`, Defender command) and was reverted.

- **Hosted, not outside.** A story commander holds the side-neutral planner
  itself (`StoryGarrisons` over `MissionGarrisons`). Its orders are the
  commander's own proposals, under its issuer and authority, so the commander's
  own-squad check reads a squad manning a window as its own on the next pulse.
  No new claim or tag is needed: the seam is who holds the planner.
- **The story declares, every pulse.** After planning its authored duties the
  commander offers a squad only while that squad's duty is a **standing** one —
  a wait at a post, a guard on a stage, holding home or holding its ground —
  and always on duty, so it may man only a place within a dozen cells of that
  post. A squad whose duty moves (a reserve committed, a column marching, a
  push, a retake, a fall back) is not offered that pulse, the planner forgets
  it, and its authored order is back in the same plan. Only infantry is
  offered; authored garrisons and declared holds never are.
- **The story's picture is the story's.** Only the proposals are rewritten.
  The commander's published picture keeps each squad's authored duty and
  reason, so every observer that turns the picture into beats reads exactly
  what it read before.
- **A share, not the force.** At most a third of the commander's own infantry
  squads holds a place at once, at least one from two squads up and none for a
  lone squad, so the rest keep their posts in the street and the yard. The share
  bounds new sends; nobody already holding is turned out by it, and pickets
  before massing still picks which pressed place gets the squad.
- **A stage is the story's, not a place to hold.** A squad whose post is on a
  stage the company has to take, or near enough that a place it could man
  reaches the stage, is not offered. Holed up in the stage's rooms after the
  company has taken it, it re-contests the stage and walks the story back. A
  Small War measured it: offered every post, the customs watch fell back into
  the customs block's rooms and returned one battle from fuel control to
  customs, and Rusk's warship went down overhead in three battles of six, which
  it never did in the control. A Small War now offers only posts more than
  twice the duty radius from every stage.
- `-Dbattle.story.reactiveDefence=false` is the control (and
  `-Dbattle.story.reactiveDefence.share=` the share);
  `-Dbattle.mission.reactiveDefence=false` switches the planner off for story
  and procedural commanders alike.

Wired: Junction's raiders (the reserve and the road column while they wait),
Relay Nine's and Thunder Run's pools (squads holding home or their ground), the
Counterattack depot's guards (gate, yard and rear guard on their stages) and A
Small War's Greyhook groups on posts clear of the stages (the service-road
picket and the pier crew).

Not wired as hostile sides, because they have no standing duty to offer: Relief's
defender assaults the anchor; in The Yard's Owners, Water Under Seal, Limited
Warrant, District Escort, Salvage Rights, Cold Start and Working Passage every
hostile squad attack-moves onto the work, the hall, the lane crossing or the
ambush ground, or walks out; in Terms of Protection Greyhook's sections
attack-move and the one squad that holds is the mortar team, whose post is its
tube. Those commanders' standing duties are the company's and the employer's
parties' (constables at the apron, militia at a workshop or a pump court, the
estate guards at the Cinder Steps gate), and the company is the player's. The
employer's parties man as parties (below): The Yard's Owners' and Limited
Warrant's constables are wired, the other six are not yet. Each of the nine has a
zero-input harness all the same (`commander-evidence-missions.md`, story
missions without a planner), so a later duty change is measured, not assumed.

Measured with `commanderEvidence` against the same-build control (2026-10-21),
every fixture of each mission's matrix. The beat lists came out identical by
name and order on every fixture of every mission. The fight mix is
`PlaceFightProbe`'s (`commander-evidence-missions.md`, places and fights): a
brawl is a contact with both ends in the open, and "from a post" counts the
defender's shooters standing on one.

| mission | fixtures | manning orders | fall backs (rooms) | beats | brawls, on / off | defender shooting from a post, on / off | outcomes |
|---|---:|---:|---:|---|---|---|---|
| Counterattack | 12 | 12 | 8 (8) | identical | 64% / 64% | 24% / 23% | marines 9, unresolved 3, in both arms |
| Junction | 11 | 0 | 0 | identical | 39% / 39% | 53% / 53% | byte for byte as the control |
| Relay Nine | 11 | 0 | 0 | identical | 23% / 23% | 75% / 75% | byte for byte |
| Thunder Run | 11 | 0 | 0 | identical | 46% / 46% | 30% / 30% | byte for byte |
| Relief | 12 | 0 | 0 | identical | 92% / 92% | 4% / 4% | byte for byte |
| Last Lift | 13 attempts | 7 (Extraction's) | 4 (4) | identical | 96% / 96% | 0% / 0% | unaffected by the story switch |
| A Small War | 6 | 0 | 0 | identical | 39% / 39% | 51% / 51% | byte for byte: marines 5, Greyhook 1 |
| A Small War, every post offered (not kept) | 6 | 20 | 18 (18) | 5 of 6 differ | 35% / 39% | 54% / 51% | marines 5, Greyhook 1 in both; customs walked back once, Rusk lost three times |

- **Counterattack** is where it bites: the rear guard, and on two fixtures the
  yard guard, takes the warehouse beside its stage when the company presses it
  (one squad at a time), and is driven into its rooms when the company gets in.
  The mix hardly moves — two fights in three are still brawls, the gate and yard
  are still met in the open — because the share lets one guard of the depot's
  handful hold at a time. The company won every battle it won in the control,
  39 ticks later on the mean, losing 8 marines over the twelve against 14. On
  the depot-hold harness the marines won three of four in both arms, losing a
  different seed in each.
- **Junction** offered its waiting reserve and road column on 1,618
  squad-pulses and nothing near their posts was ever pressed while they
  waited: the lesson's reserve waits out of the fight by design. **Relay
  Nine**'s pool is one squad, which the share keeps on its post (225 offered
  pulses, all closed). **Thunder Run** offered nobody: no pool squad held home
  or ground on these fixtures.
- **A Small War** offers its service-road picket and pier crew (about 115
  offered squad-pulses a battle) and neither was pressed: the company's road
  runs through the three stages, and the Greyhook groups it meets there keep
  their authored posts. The nine story missions without a planner of their own
  were measured on the same day as baselines (`commander-evidence-missions.md`):
  brawls are 0-3% on The Yard's Owners and Limited Warrant, where Greyhook
  attacks into the hall or the galleries and fights under a roof, and 85-90% on
  Terms of Protection, Cold Start and Working Passage.

The scene `story-defence` plays the production Counterattack depot with its own
story commander: a guard on a standing duty holds a building by the story's own
envelope order, every holder is in the story's roster on its published pulse, a
holder whose duty moves takes its authored order back within a pulse, no squad
is sent past the share, and the reserve-committed beat fires when the picture
commits the reserve.

## Parties

Owner, 2026-10-21: "We should try to model neutral / allied parties as well.
Missions may have triggers which say when they should consider taking new parts
of the map or so on to make things interesting, but otherwise I want to have
generic systems we can re-use. There might also be locked positions that allies
are meant to take and hold à la Thunder Run."

A **party** is a force on the field under a commander of its own: one per
faction with strategic agency (`ai-nouns.md`, sides). The company is the
player's; the opposition is `DEFENDER`; an employer's or patron's force —
constables, militia, estate guards, a garrison's auxiliaries — is `ALLY`; the
residents are `CIVILIAN`. The garrison system is keyed by party, never by which
side of the fight it is on: the planner is handed one party's own picture, the
squads that party's commander offers and the map's places, and answers for that
party alone (`MissionGarrisons`; `PartyPlaces` hosts it for any party
commander).

- **A party's places are its own commander's.** Only a party's commander offers
  its squads, under its own issuer and authority, so the commander reads a
  holder as its own on the next pulse. No commander offers another party's
  squads, and the player's company is never manned by a commander: the occupy
  and line orders and the GARRISON stance remain its verbs. An ally is not the
  player's to command (`ai-nouns.md`).
- **Belief from its own picture.** A party's pressure is its own faction
  influence, built from its own squads' believed contacts, never the company's
  and never truth. `ALLY` has a picture of its own, published beside the
  company's and the defender's whenever the battle fields allied combatants, and
  an allied holder stands to at its windows by it. Constables at a dispatch house
  man its windows because they have seen the counter-attack coming, not because
  the company has.
- **A party guards the place it was put on.** The story commanders offer an
  employer's squad only while its duty is a standing one, and only for the place
  its post stands in or touches: it holds that building from its windows rather
  than standing in its yard, and is never sent to the next building on its own
  initiative, since an employer's post is often what a story counts (the Yard's
  Owners inspection needs a friendly body in every site). A place directive opens
  more (below).
- **A share, then the mission's allowance.** The story share (a third of the
  party's own infantry, none for a lone squad) bounds what a party mans on its own
  initiative; an active hold directive adds its own allowance for its own places,
  because a mission that says "hold this" says how many it means.
- **Civilians are a party with no fighters.** Nothing is offered for them.
  Whether residents should shelter in buildings is a different question (open).
- `-Dbattle.party.places=false` is the control: no allied picture, no party
  manning and no hold directives, the battle as it was. A locked position that
  re-expresses an authored duty (Thunder Run's militia) does not follow the
  switch, since it is the story's order and not a feature on top of it.

## Place directives

A **place directive** is a mission's word to one party's commander about one
place: what to do there (its kind), where (its target), when to begin (its
`when` trigger) and when to stop (its `until` trigger). It is declared once, at
mission setup, held by that party's commander and evaluated on every one of its
pulses over its own frame (`PlaceDirective`, `PlaceDirectives`). It is the one
seam by which a mission, story or procedural, makes its battle move — "consider
taking this region once the reserve commits", "hold this gain once the company
has it", "counter-attack this place when it is lost" — without a mission-specific
code path in any commander. Commanders may still plan their authored duties as
before; a directive adds to them.

- **Kinds.** `HOLD`: consider holding the target. Its places are open to the
  party's reactive manning within the directive's allowance, its free squads
  offered roving for them and its standing-duty squads beside their posts; belief
  still decides whether anybody goes, so a hold directive opens places, it never
  fills them, and its free squads are staged toward pressure, back at the places
  and forward to meet the attack, by the flow tier's laws. `LOCK`: a locked
  position (below). `COUNTER_ATTACK`: retake the target. A counter-attack
  directive names the place and when; how and with what is the commander's
  counter-attack's (`conquest-command.md`, Defender command), which reads the
  active ones each pulse. The Raid, Assault, Sabotage and Extraction defenders
  read them today (`MissionCounterattack`): an active one is offered to the
  shared planner as a lost place of its own, worth more than any objective the
  mission reads for itself, and the mission places inside its target are
  folded into it. The trigger says when and nothing else; the doctrine's
  bounds, its observation and its stand-down still decide whether a push goes,
  so a counter-attack authored against more than the side can raise is stood
  down like any other. The Raid discloses its alarm and a secured target as
  flags. `-Dbattle.mission.counterattack.triggered=false` is the control. A
  new kind joins this list rather than starting a parallel mechanism.
- **Target.** A region of public geometry: a centre cell and a radius (cells,
  square). Its places are the garrisonables whose centre lies in it. A lock may
  also name posts, its own cells to stand on.
- **Lifecycle.** Dormant, active, released. On every pulse each directive is
  evaluated once, in declaration order, on that pulse's frame: an active one
  whose `until` holds is released; a dormant one whose `when` holds becomes
  active unless its `until` already holds too; never both on one pulse, so a
  directive that comes on is held for at least one. A released directive stays released unless it is declared to
  **re-arm**, in which case it is dormant again from the next pulse (a
  counter-attack that may be tried again). The order directives became active in
  is remembered, ties in declaration order; it is what orders locked positions.
- **Triggers** are generic predicates, never a mission's own code: always and
  never; a tick reached; active for a number of ticks; a named mission **stage**
  at least a value (a phase reached); a named mission **flag** (a beat fired, a
  public fact such as a site the company holds); the target, or another region,
  **held** — own, hostile, contested or empty — by the party's own belief (its
  own squads standing in it, believed hostiles in it); **lost** (seen held by the
  party on an earlier pulse, and now hostile or contested); believed **pressure**
  on it at least a level; and any of, all of, not. Each prints as what it reads,
  so a trace says why a directive moved.
- **What a trigger may read.** Belief predicates read the party's own picture
  and nothing else. Stages and flags are the mission's disclosure
  (`mission-command-nouns.md`, perspective and disclosure): a mission says only
  what it may say to that party (a site the company holds is public; a hidden
  enemy's position is not), and the mission answers for it.
- **Fun is still the test.** A directive is judged by the fights it makes as
  well as by what it holds (not every fight is into prepared positions, above): a
  hold that turns every contact into a defence from windows is a siege the
  mission chose, and its evidence reports the fight mix against the directive
  switched off.

## Locked positions

A **locked position** is a `LOCK` directive: a place a mission declares a party
must take and hold. While it is active the party's commander:

- gives it squads before anything else, by **detachments** — the commander's own
  stable numbering of its squads, first come first numbered. Detachments fill the
  active locks in the order they became active, one each, oldest first, and
  detachments beyond them reinforce the newest: a party led forward holds every
  gain once and leans on the latest;
- orders each the lock's own hold: a defend area on the lock's post for its
  detachment (posts cycle by detachment number) or, with no posts and a
  garrisonable at the lock's centre, that place's manning order. A defend area
  fights whatever is believed inside it, which is how the party takes a lock held
  against it; there is no separate assault stage;
- never offers a squad on a lock to reactive manning, flow or release: the lock
  outranks the place's pressure, and nothing reactive gives it back. A squad on a
  lock is also exempt from the share;
- lets the lock go only when its `until` holds (the mission unlocks it); its
  squads go to the next lock, or back to the commander's own duties;
- **holds it, but does not die in it.** A detachment worn to half its strength
  or less is let go, back to the commander's own duties, unless the lock is
  declared to hold **to the last** (an authored hold to the end). An employer's
  section is often what a story still needs later — the Yard's Owners'
  inspection needs a friendly in the operator entrance — and a lock that spent
  it would turn a fight into a stalled mission. Strength, not pressure, is what
  lets go: nothing reactive does. The floor alone is not enough where the walk
  back is under fire, so a lock is put where its detachment can fight from a
  place, not stand in the open (parties wired, below).

Thunder Run's militia are the first: four locks, the convoy staging (active from
the start, unlocked by the company's first gain) and one per road site, each
locked once the company has held it and never unlocked, all held to the last.
That is exactly the old stable militia holds, re-expressed (`mission-command-nouns.md`, player
and faction extensions).

## Parties wired

Three missions' employer parties run on these: Thunder Run's militia on its
locks (above), and the constables of The Yard's Owners and Limited Warrant as
parties with places and one lock each.

- **The Yard's Owners.** Each constable section on a standing duty (its wait
  before its site is taken, then the site) is offered for the place its post
  stands in. The section still waiting for the operator entrance is locked into
  the tooling hall at its freight doors (the hall's own manning order, no posts)
  from the company's first hold of dispatch, when the prize crew commits, until
  the company holds the entrance and the section goes to keep it. A post in the
  yard outside the doors was tried first: the section stood in the open across
  the Cinder Steps group's line and was wiped out on two fixtures of twelve.
  Inside, it fights from the windows. The inspection needs a friendly at every
  site, so a site whose constables are all dead is the company's to cover through
  it (`YardOwnersCommand.orphanedSites`): one company squad goes there instead of
  waiting at the hall. That never happens in the control, where no constable
  dies.
- **Limited Warrant.** The constables are offered for their posts' places, and
  locked into the gatehouse by its manning order from calibration until egress,
  where the rearguard's line runs past it. A lock on the lane, and a hold on the
  gatehouse instead of a lock, were tried: the lane lock cost technicians and
  constables in the open, and the hold never fired, because the constables never
  see the rearguard before it is upon them.
- **Reactive manning by a party has not yet fired in production.** Neither
  mission's constables believe pressure on the place their own post stands in
  before the fight reaches them there; the `party-places` scene shows the seam
  works when they do.

Measured with `commanderEvidence` against the same-build control
(`-Dbattle.party.places=false`, 2026-10-21): the default matrix and eight extra
seeded fixtures each. The controls reproduce the previous build byte for byte.
"From a post" counts the side's shooters standing on one; ally losses include
the technicians.

| mission | fixtures | beats identical by name | marines win, on / off | mean ticks, on / off | brawls, on / off | defender from a post, on / off | ally from a post | ally lost, on / off |
|---|---:|---|---|---|---|---|---|---|
| The Yard's Owners | 4 | 4 | 4 / 4 | 5,153 / 5,801 | 0% / 0% | 87% / 93% | 96% | 2 / 0 |
| The Yard's Owners, extra | 8 | 8 | 8 / 8 | 5,494 / 5,736 | 1% / 0% | 82% / 92% | 99% | 15 / 0 |
| Limited Warrant | 4 | 4 | 4 / 4 | 5,407 / 5,459 | 5% / 3% | 99% / 96% | 62% | 7 / 1 |
| Limited Warrant, extra | 8 | 7 | 7 / 8 | 6,991 / 5,478 | 7% / 4% | 92% / 94% | 43% | 21 / 4 |
| Thunder Run | 11 | 11 | as the control | 16,456 / 16,456 | 46% / 46% | 30% / 30% | 50% / 50% | 59 / 59 |

- **The fights move off the defender's windows, not onto them.** With the
  constables in the hall and the gatehouse, the counter-attacks meet a place the
  employer holds: the defender shoots from a post less on The Yard's Owners
  (82-87% against 92-93%) and the brawl share rises a little on Limited
  Warrant. Nobody is besieged who was not before; the allies fight from their
  own posts, and the company's work finishes sooner on The Yard's Owners (650
  and 240 ticks on the mean).
- **The constables pay for it.** None died in either control; with their places
  they lose about a fifth (The Yard's Owners) to over half (Limited Warrant)
  of their number. That is the cost of a party that fights rather than watches,
  and the reason the strength floor exists.
- **The one lost battle is Limited Warrant's egress.** On one extra fixture a
  single relief-post squad outlived the battle with a line on the calibration
  load's route, and the carrier pauses under any hostile in sight, so it never
  reached the landing. The control's history happened to kill that squad. The
  mission has no order that clears a threat to the stalled load; that is the
  mission's flow, not the party's, and open (below).

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

**Shutting, in play**, against a same-build control with
`-Dbattle.sallyDoors.closable=false` (conquest-trial, fort on the lane, seeds
41 to 80 at the combined-arms force, and seeds 41 to 64 rifles-only; each
first time a body stood in an open door opened for a side hostile to its own
is counted, which includes marines in a port the defender opened before the
marines took the fort): with the combined-arms force the attack's use of doors
opened for the other side fell from 1,141 such crossings in 39 of 40 battles
to 75 in 3, the 75 all through doors whose holder lost its place with them
open. Doors opened 118 times against 49 and shut 117 times (108 behind a
sortie, 9 in the enemy's face): marines who took the fort opened its ports for
their own sorties 56 times against 7, because a shut port is theirs to open
where an open one was simply walked through. Marines won 39 of 40 either way,
losing 2,173 against 2,396, defenders 2,164 against 2,216. Rifles-only, the
crossings fell from 4 to none; marines won 4 of 24 against 3. Both readings are
findings, not balance acceptance.

The scenes are `sally-door` (a walled yard that is not a fort: a sortie by
infantry, the walk round with doors closed, the enemy at the door, an attack on
the door, a taken yard where the player sends a squad and a Warden out, a
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
  which is the march itself. Staging nearer the belt is the likely next step,
  and axis-free flow (flow, then local assignment) is its general form: a
  squad waits at the places an area presses before they are hot, so manning
  finds somebody already there. The missions use it; Conquest's belt still
  stages along its axis. A garrison stand-to at the work being lost, sent round
  the walls, was tried and cost more than it saved.
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
- **Story allies and the stages.** The employer parties man as parties
  (parties wired, above) on three missions; Water Under Seal, District Escort,
  Salvage Rights, Cold Start, Working Passage and Terms of Protection have
  employer posts not yet offered. Whether a stage guard of A Small War could be
  offered places on the stage's far side from the company is open; each has a
  harness to measure it.
- **A load stalled under one survivor.** Limited Warrant's carrier pauses under
  any hostile in its sight and no order clears one, so a single surviving relief
  post can hold the egress forever. Whether the company should be sent at a
  threat to a stalled load is open.
- **Story maps that offer nothing.** On Junction, Relay Nine and Thunder Run the
  seam is wired and nothing is manned. Whether a one-squad pool should man its
  home's windows, and whether a story ally holding a gain (Junction's and Relay
  Nine's militia) should, is open.
- **Missions without a reserve.** Assault and Sabotage spend nearly every squad
  on posts and responders on their fixtures, so flow has almost nothing roving
  to stage there; whether a quiet post's squad should ever be lent to an area's
  flow is open (the mission outranks the place today).

## Stories


- `fort-sandbag-damage-art.md` — real art for the placeholder fort sandbag damage
  states.
