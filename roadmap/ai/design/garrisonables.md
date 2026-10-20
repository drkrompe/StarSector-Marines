# Garrisonables

Status: ACTIVE — reactive manning is shipped for fighting-position runs, a stretch at a time, on both Conquest sides (the defender's own belts and compound aprons, the attacker's taken ones) and for a player's squads that opt into it, the player's machine assault on a believed-held garrisonable is built, and machines guard the places their side mans (player and both Conquest commanders); building envelopes and other kinds remain direction.

Written: 2026-10-13

Updated: 2026-10-20 — runs are garrisoned a stretch at a time (stretches, shifting, the alarm floor over stretches); the defender counter-attacks a lost fort in proportion and its reserve lends to its own belt (`conquest-command.md`); compounds offer prepared ground (sandbagged doors) manned like a belt; machines screen the places their side mans.

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
- a **building envelope** — the apertures and stance cells the defense frontage
  already derives (`ai-nouns.md`, frontage and standing to);
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
layer is what a squad falls back to when the one in front of it is lost.

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
  everything else.
- **Pickets before massing.** Across garrisonables as across apertures: every
  threatened place gets a squad before any gets a second, and the reserve
  yields to that rule rather than outranking it.
- **Fall back through layers.** A squad whose place is breached (enemy inside
  it), destroyed below its useful size, or untenable falls back to the next
  layer in if one is linked; with none, it releases to the commander. A fall
  back is squad-tier movement under an existing assignment, not a new order.
  A breach is read against the whole run, not the stretch: an enemy a few cells
  along the same trench, past the stretch's end, is inside it.
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
counter-attacks a lost fort in proportion or not at all (`conquest-command.md`,
Defender command); on the trial ground it launched against the hold in six of
nine at the default garrison and retook the bunker once, before the hold was
whole, and the defender's reserve may man its own fort's belt only from what it
holds above the reserve law's share.

## How it fits what exists

The defender commander already raises a threatened track and forward band from
contact and rallies a bounded number of patrols into it (`conquest-nouns.md`).
A garrisonable inside that band is a better destination for a rally than a
point on the track: the rallied squad mans it and digs in instead of meeting the
attack in the open. That is the natural first consumer.

Standing to is already reactive manning of a building envelope by its own
garrison. Unifying it under garrisonable posts is a later step, taken only once
fighting-position runs prove the commander-tier half, because frontage carries
its own laws — what an aperture may open onto, held layers, the breach that
dissolves an envelope — that must survive the move intact.

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
  none (`compound-prepared-ground.md`).

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
at its windows (`ai-nouns.md`, occupy). Reactive manning by the AI is the next
consumer.

## Decided direction

Owner, 2026-10-20: where a choice is open, prefer the one that adds fun.

- **Attackers hold what they take.** Captured ground is a garrisonable like any
  other: the attacking commander uses the same reactive assignment to man a
  taken belt, trench or building against the counter-attack, not only manual
  orders. Built for taken fort belts (see Holding taken ground) and for the
  aprons of taken compounds; building envelopes wait for that kind to reach the
  tier.
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
  is the same finer place the open question below asks for.

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

## Open questions

- **Marching late.** A stretch is pressed only when an attack is near it, and
  the pool waits at the works, so a garrison often walks forty to sixty cells
  round the works and arrives as the attack does: half of the garrison samples
  near a fight are squads still on the way. Staging nearer the belt, or a way
  through the works rather than round them, is the likely next step; a garrison
  stand-to at the work being lost, sent round the walls, was tried and cost more
  than it saved.
- **Alarm-held stretches.** What little stranding is left is mostly a fort's
  alarm holding stretches while the defender believes nothing anywhere near: an
  attack inside the works, unseen. Whether the floor should fall to the
  stretches nearest the works being lost is open.
- How a garrisonable is valued against an objective: a trench in front of the
  keep is worth more than one at the map edge under the same pressure.

## Stories


- `compound-prepared-ground.md` — compounds and facilities offer prepared ground
  and building envelopes as garrisonables.
- `fort-sandbag-damage-art.md` — real art for the placeholder fort sandbag damage
  states.
