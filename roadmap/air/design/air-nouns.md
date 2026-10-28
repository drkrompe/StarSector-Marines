# Air — nouns and model

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-10-26 — a player's carrier bay is renewable: one airframe on station, a refit after each sortie it comes home from and a replacement after each loss, never running out. Earlier the same day, machines ride the ferry through one shared lift queue, one machine a sortie; a cradle keeps its seats; a standby craft (heavy lift included) takes squad sorties still owed before any machine. Earlier, 2026-10-25: a planted Bombard an operator sees is designated ahead of a lone nest.

Earlier 2026-09-01 — an aircraft on its stand is a unit and only a unit: the
3x3 of navigation grid a parked hull used to stamp around itself is gone, along
with the release every ending owed it. A wreck still writes, because a wreck is
nobody's body.

Earlier 2026-08-31 — an authored airfield is a base whose lot published it as
one, berths and node together, so a generated field loads its crew instead of
flying pre-loaded. An aircraft on its wheels is a real target rather than a
damageable one, and it reaches that on the shared terms every body is on: one
carrier surface, one admission, one damage route, its hull an ordinary
`HEALTH`/`ARMOR` pair. Whether a craft can be engaged is a relation between a
shooter and it rather than a property of it, with altitude a per-weapon
capability. How much of an aircraft there is to hit is one number asked of the
airframe by both representations; ground durability is a role ladder and no
longer pretends to follow it. A parked hull is something to walk round and see
round, its wreckage still is not, and a cook-off spares the next stand by rule
rather than by arithmetic.

## Purpose

Air is the battle tier's atmospheric craft: transports that deliver people and
materiel, fighters that make recurring combat passes, and overhead ships that
can become part of the ground battle. A craft is not a special effects layer or
an alternate battle: it is a capability-composed entity in the same simulation
world as ground actors, with continuous flight motion instead of grid movement.

The feature recaptures loaded vanilla and modded hulls for ground-scale play.
The game remains the authority for a hull's authored data; the mod chooses how
that data reads in atmosphere and what role the resulting craft has in a
mission.

## Core vocabulary

**Remote ship command** selects a player-owned overhead hull without taking its helm.
Move replaces its travel with a station on observed ground; Hold brakes onto its
current point. Weapons and survival remain the hull's, and a bounded support visit
continues spending time while following remote travel. Autonomous hands the original
station or route back. A piloted, grounded, destroyed or withdrawing hull cannot
accept remote movement. Direct control temporarily masks the accepted remote station
and returns to it on handback.

A remote tender build lays the ordinary construction option and lets the tender fly
to its normal standoff: costs, crew, threat stand-down, spacing and work time stay the
same. Explicit remote work suspends independent site choosing. Move or Hold withdraws
unfinished sites through the ordinary refund path; Autonomous resumes choosing.
Mission-owned grounded construction keeps its own authority and option gate.


**Air craft** is the broad category. It has a continuous **body**: position,
facing, velocity, and motion state. A body is a kinematic fact, not a behavior
or a visual approximation. Steering systems supply goals and modes; the body's
handling determines the resulting turn, acceleration, drift, and stopping
shape.

**Locomotion** is how a craft is being moved right now, and it is the thing
that changes across a sortie. One aircraft, one entity, three ways of moving
it: **grounded** on its wheels, **managed** along a solved trajectory, and
**free flight** under the steering. A machine parked in a shed, one rolling
down a taxiway, one being flown onto a threshold and one making a gun run are
not four kinds of thing — they are the same thing under four sets of physics,
and saying so is what lets the whole sortie be one entity.

**The phase is the only source of truth for the mode.** A stored mode is a
second thing to keep in step, and the phase already says everything it does: a
craft rolling out is on its wheels because it is rolling out. So the mode is
derived, the derivation is total, and a phase that would need two modes is a
phase that wants splitting rather than a mode that wants a flag. That is
exactly why the settle onto a landing pad is a phase of its own and not a timer
inside the run in. It also removes the hand-written lists that used to answer
cross-cutting questions: whether an anti-air post can reach a craft is whether
the craft is in the air, which is the mode, and a list is a thing the next
phase added gets left out of. See `AirLocomotion`.

**Ground position** and **air position** are different representations, not
different universes. Ground actors use grid membership where occupancy,
pathfinding, and tactical decisions need it. Air craft use continuous motion.
A craft obtains only the components its role needs, so a transport without grid,
combat, or AI components is naturally excluded from ground-only systems. A
hybrid such as a drone may carry both continuous flight motion and a derived
ground-facing presence when its gameplay requires it.

**Hull identity** names the loaded ship hull from which stable physical facts are
derived. It is intentionally separate from a craft's mission/loadout identity:
one says how the hull moves and is shaped; the other says why this instance is
present, who owns it, and what it can do in the battle.

**A gunship** is a craft whose sortie is to circle a ground point rather than land on it or run in at it. It works a broad, slowly turning oval over the point for a bounded visit while its turret covers the ground beneath. It is flown in its own phase, `ORBIT`, because nothing in a circling visit ends on a line: it ends on the clock the sortie was given. See **Gunships** under Roles and lifecycle.

**Air appearance** is authored render state such as flight phase and apparent
altitude. It is not a second physics body. Visual scale, offset, and engine
intensity are derived from that state and the body, keeping the simulation and
the rendered craft anchored to the same actor.

Replacement battle artwork is registered to the original hull frame. Texture
resolution does not change the aircraft's physical size, centre of gravity or
weapon locations. When reconstructed art places an engine nozzle differently,
its appearance may offset the visible plume in that same hull frame; engine
geometry for flight and thrust demand stays authoritative and unchanged.

An engine's visible burn follows its realized work: a compact idle, a restrained
sustaining flame at steady speed, and a longer, brighter plume while accelerating.
Individual nozzles also respond to turning demand, with smooth attack and decay
on the simulation clock. Night missions retain self-lit nozzle and exhaust masks
at the same hull hardpoints. A little of that light catches the adjoining hull
plating, retaining its painted detail and transparent gaps; the rest of the
airframe keeps its environmental shade. Reflected light follows each nozzle's
burn, including asymmetric turning demand. Engine light neither reveals fog nor
changes the craft's motion or detection.

A craft's drawn size is `AirAppearance.GROUND_SCALE` on the ground rising to
`GROUND_SCALE × ALTITUDE_SCALE_GAIN` at altitude — the gain is factored out as
a ratio precisely so climbing can be re-dialled without touching the ground
size the two representations agree on. It was brought down from 1.5 to 1.2 on
2026-08-31: the earlier value read as a near-50%-larger pop rather than the
subtle "a little bigger up high" the cue is meant to be.

## Hull-derived facts

The runtime hull specification is the authoritative, mod-aware input. Its
engine specification supplies the maneuver ratios; its ship description
supplies the visual/physical geometry. The offline kinematics table in
`vanilla-kinematics-reference.md` is only a calibration reference, never the
runtime source.

Three authored responsibilities remain deliberately distinct:

1. **Silhouette and hardpoints** preserve the hull's authored proportions.
2. **Footprint** uses one gameplay-tuned pixel density for every hull. It keeps
   the relative Starsector size ladder without per-hull visual sizing. The
   current scale is intentionally map-friendly rather than literal metres;
   increasing map scope is the future lever for a more realistic absolute scale.
3. **Kinematic feel** converts source maneuver ratios to ground-scale motion and
   adds atmospheric damping. The present linear conversion intentionally seeds
   its calibration from the shared pixel density, then applies an atmosphere
   multiplier; changing that common calibration therefore requires conscious
   visual-and-motion review. Per-hull footprint changes still must never be used
   to tune motion.

An air body's origin is the hull's authored centre of gravity. Hull rendering,
engine placement, weapon mounts, and future collision geometry all share that
origin. A mount's simulated location and its drawn location therefore describe
the same point; per-mount sight and fire are meaningful on a long craft rather
than collapsing to its centre.

Atmosphere is a deliberate adaptation, not an attempt to recreate vanilla space
flight. Hull maneuver data preserves role contrast, while damping and limited
presentation adjustments make that motion legible near the ground. A later
turn-ramp refinement is justified only if play establishes that rate-limited
turning fails to express a role.

**What the atmosphere calibration is actually setting is the circle.** Speed and
turn rate are two dials with one product — a craft's tightest circle is its
speed over its turn rate — and neither of them alone says how the aircraft
reads. A hull's authored turn rate is a *space* fighter's, and space fighters
pivot: passed through raw beside the old speed multiplier it put every fighter
in the game on a turn radius of about seven cells, which is a machine that can
turn round inside the beaten zone of its own gun run. That is the bee the model
is not supposed to be. Faster and slower to turn compound, so the pair together
puts the ladder at eighteen to twenty-three cells — a wide banking circuit a
player can watch develop and get out from under, and long strafes between the
turns. `AirHandling.minTurnRadiusCells` is that number, asked for directly
rather than re-derived at each of the places that need it: a look-ahead, a gate
wide enough not to be orbited, a review of how a re-dial reads.

## Roles and lifecycle

### Transports

Shuttles are the shipped proof of the air model. A transport owns a sortie:
it waits or re-arms off-map, enters toward a landing berth, delivers the
mission-authored passenger count, then follows an explicit post-delivery
disposition.

### Based aircraft

An aircraft is **two representations, one thing**. In the air it is an air
entity. On its hardstand it is an ordinary grid unit, and a launch or a landing
is a handoff between them.

**Both of them are bodies, and being a body is what makes an aircraft
shootable.** The split used to carry that weight as well: a parked aircraft was
a grid unit precisely so that being perceived, traced against line of sight,
hit, attributed, killed and wrecked came from the paths that already did those
things, and an air entity carried no grid or combat components so every grid
walk skipped it for free. Teaching the combat stack an air-aware branch in each
of those walks would have bought a handful of shootable aircraft at the cost of
that property forever.

The convoy work removed the branch from that trade, and **an aircraft is now a
body on the shared terms rather than an air-shaped copy of the convoy's**.
`ecs-nouns.md` owns what a body is and what a carrier owes it;
`AirTargetService` is Air's implementation of that surface, and the durability
law, the damage route and the spatial admission are all the shared ones. What
remains Air's own is the two things genuinely about aircraft: which craft ground
fire can reach — on its wheels, in the open — and what dying means, which
converges on `AirSystem`'s shoot-down so a kill lights the cook-off, leaves the
wreck and gives the runway back.

Structure therefore lives where every other body's does — an ordinary `HEALTH`
component beside an ordinary `ARMOR` one — rather than in the sortie's mission
bag. There is one hull number, written by the launch that took the aircraft off
its berth, drained by whatever shoots it, read by the bar over it and handed
back to the berth when it parks. A sortie carries what the aircraft is doing,
never how much of it is left.

The skin is one skin. A parked airframe and a rolling one are the same hull and
take the same armour off the same ladder; what separates them is how often a
round finds them, because a hull on chocks is a mark you can settle onto and one
going past at taxi speed is not. That is a single authored multiplier on
incoming accuracy and deliberately not a second durability profile — two ladders
for one aircraft would be a fact with two values, consistent exactly as long as
nobody re-dialled either.

**A craft in the air is out of a rifleman's reach, and that is a fact about the
rifleman.** It carries the same components as one on its wheels and is present
in the battle in exactly the same sense; what separates the two is that
engagement is a **relation** — can *this shooter* reach *that body* right now —
rather than a property the body carries around.

The relation has two halves and they are owned in different places. **Presence**
is the carrier's: on the map, alive, and not down with the ramp open, since a
loading craft's passengers have already left the roster and a landed one is the
same craft at the other end of the trip. **Reach** is the shooter's, and the
only question in it today is altitude. `EngagementService` is where the pair
meet; `EngagementService.reachesAltitude` is the capability, and it is the one
implementation of what used to be a hardcoded "only a defence post can reach up"
filter written inside the anti-air drain — the one place that had ever needed
it, and therefore the last place a second consumer would have looked.

Splitting them is what lets the parts of the battle that are entitled to the
absolute question keep asking it: the spatial index and the blast sweep serve a
fight at ground level, so they admit a body that is present and on the ground,
and a flying machine stays out of both. Nothing about that is a special case for
air — it is the same derivation for any body a carrier ever calls airborne.

When anti-air arrives it is the capability that grows, not a second candidate
set. A weapon gains an authored elevation, `reachesAltitude` reads it, and every
consumer of the relation inherits the change without being touched.

A warship's weapons are the first aimed shooter to reach altitude, and they do
it by the weapon rule under **Overhead ships**, not through that capability: the
rule is the ship's own, and the aimed hit enters through a second door beside the
ordinary damage route (`applyAimedDamage`), which admits a present airborne body
and applies the same armour and structure law and the same shoot-down. The
ordinary route is unchanged, so ground fire still cannot reach a machine in the
air.

The **berth** is the thing with identity, not the airframe. A hardstand is
authored into the map and stays put; the aircraft on it comes and goes and may
never come back, and the state an attacker is trying to create — this pad had an
aircraft and now does not — has nowhere to live if the aircraft is the record.
A berth is parked, away, refitting, or destroyed, and the unit standing on it
is a consequence of that state rather than a thing anybody places directly.

An **airframe** is what a berth holds: a kind of aircraft, narrowed to what
standing on the ground actually requires — a sprite, a hull that sizes it, and
structure to shoot at. Deliberately not everything an aircraft is. A
transport's capacity, a fighter's guns and the handling either flies with are
asked of the concrete type by whoever needs them, because nothing that puts a
hull on a hardstand cares.

That narrowness is what lets the two lists stay separate. A transport is a
`ShuttleType` and a fighter is a `FighterProfile`, and the game's fighters are
**factional** — six hulls, and `FighterProfile.poolForFaction` already knows
that a Hegemony or Luddic or pirate field flies Talons and Broadswords while a
Tri-Tachyon or Remnant one flies Wasps and Thunders. Copying those hulls into
`ShuttleType` so a berth could name one would have put the same five aircraft
in two enums; making the berth hold the smaller thing they have in common costs
three methods. Ground durability is authored per fighter rather than scraped,
the way a transport's is: a hull's campaign HP is balanced against ship weapons
and says nothing about what a rifle section does to one parked on concrete.

**And it is not a function of drawn size either.** It is a role-and-toughness
ladder: a heavy fighter is heavy because of what it is built to survive, not
because of how long its sprite is. The doc claimed the opposite for a while
— that on the ground the only thing that matters about an aircraft is how much
of it there is — and the roster appeared to bear it out, because a fighter
carried a second, authored drawn length that ranked identically to its
structure. Against the hulls the game actually ships it does not: order the six
by real length and it runs Wasp, Talon, Broadsword, Dagger, Longbow, Thunder,
while the structure on them runs 25, 30, **45**, 40, 38, **35**. The Broadsword
is mid-sized and the toughest thing on any apron; the Thunder is the longest
hull and nearly the softest. The numbers are right and the claim was wrong, so
the authored length is deleted and the claim with it.

Drawn size is not idle. It is what decides how easily a shot finds the aircraft
— the target radius above — and it comes off the hull's own spec, one answer in
one place. How much killing an aircraft takes is a separate fact, and keeping
the two separate is what stops a re-cut sprite quietly re-balancing every
apron on every field. The whole fighter ladder still sits below the lightest
transport, which is what makes burning a row of fighters a faster afternoon
than burning one transport.

Every berth is on the apron, in the open. The base's hangars are where aircraft
are worked on rather than where they wait, so an attacker who reaches the field
can burn what is standing on it without going indoors — the exposure is the
point, and it is what makes a raid on the field a real alternative to taking the
compound. `mapgen-nouns.md` owns the lot's geometry.

**An aircraft on its stand is a unit, and only a unit.** It holds the cell it
stands on the way every body does, and being seen, gated by fog, traced against
sight, hit, attributed, killed and wrecked all follow from that rather than from
a second copy of the aircraft written into the navigation grid.

It was both for a while. A parked hull stamped a 3x3 patch around itself — its
own cell opaque, the ring non-walkable and see-through, a step-clear for whoever
was under the wheels, and a packed mask remembering which cells it had taken and
what had been marked on them, so departure could put them back exactly. All of
that existed to keep one sentence true, that an aircraft is something you walk
round, and it bought that sentence at the price of a representation that had to
be given back correctly by every ending a berth has — launch, burning on the
concrete, loss over the objective, and whichever ending nobody has written yet.
Both ways of getting it wrong are silent. A stand that never closed is an apron
with pictures on it; a claim that outlives its hull is an invisible wall on the
concrete that nothing can walk through and nothing explains.

The one thing the stamp genuinely carried is kept, and is not the airfield's
rule: an arriving hull moves whoever is under its wheels, because two bodies in
one cell is worse and refusing to place is worse still — a pad anybody could
stand on to stop a field flying. That is the ordinary rule for an immobile
arrival, the same one that seats a turret on its mount and a machine off a
shed's stocks, and it moves the one body in the cell rather than shoving whoever
is merely near a pad on every turnaround.

The rest is simply gone, and with it a defect that was worth the trade on its
own: a sight line exempts its two endpoints and nothing else, so a hull opaque
across its own footprint is a hull no round can reach. That emptied the raid on
the field outright — three aircraft burned in five seconds became none in three
minutes — and needed a careful rule about which single cell was allowed to be
opaque. A hull that writes nothing has nothing to be opaque in.

**A wreck still writes, because a wreck is nobody's body.** The hull that died
is dead, released and never coming back; what is left on the concrete is drawn
off the berth, so terrain is the only place left to say it is lying there. That
makes it the one thing on a stand that marks the ground at all, and nothing has
to be given back before it does — the wreck-and-obstacle section below owns what
it marks and why.

**A field is not always a lot, and it belongs to whoever owns it.** A garrison's
apron on a battle map and a ship's boat bay are the same arrangement: berths with
machines standing in them, hands who service those machines where they stand, and
a turnaround paid for in hand-seconds. So a ship's bay installs this service
rather than a shipboard imitation of it, and the one fact that differs — whose
aircraft these are — is held by the field rather than by whatever ticks it. It
was a constructor argument to the tick consumer while every field was a
garrison's, which would have meant building that consumer one way for a lot and
another for a bay over something that has nothing to do with what it does.
`ship-interiors-nouns.md` owns the bay as a room.

**An air loss outlives the aircraft it names.** A shot-down aircraft is
released and its entity reaped, so anything a battle wants to say about it
afterwards has to have been written down while it was still there: what it was
flying as, whose it was, what killed it, and which marines were still aboard.
That ledger is the simulation's and is append-only for the length of a battle;
resolution reads it once. It is the only reason a loss can reach the campaign at
all — a count taken at the end would be a count of what is missing, which cannot
name a boat or a passenger.

**Only the company's own boats reach the campaign.** A loss carries the campaign
id of the boat its frame was frozen from, and a frame that was never frozen from
one — an employer's transport, a padding craft, a fixture's plain pattern —
carries none and is simply not the company's to lose. `company-view-nouns.md`
owns what striking a boat off the deck then means.

**A boat pattern is what the hull carries; a fitted boat is what flies.** The
pattern is the airframe — a `ShuttleType` — and it is a fitting of the hull
rather than anybody's property: landing craft for a hull whose reason for
existing is putting a ground force somewhere, a gig for every other. What the
*company* owns is the boat standing in the berth, and what a yard has bolted to
it. `company-view-nouns.md` owns that campaign side.

A **fitted boat** is the two put together: the pattern's numbers with the
fittings' factors applied, frozen at launch and read nowhere else. It is what a
`ShuttleAssignment` carries and what an air entity's identity, durability and
handling come from, so a refit in the middle of a mission is not a thing the sim
has to have an opinion about. Only the numbers a fitting is about are touched —
plating sets the armour over the hull (none on a bare hull; the hull itself is the
pattern's), the drive scales forward speed and both accelerations,
a lift cradle sets the lift rating, and the door guns are what an air-to-ground
sortie mounts on her hardpoints in place of the role's kit — and turn rate,
damping, hardpoint count, sprite and hull id are the pattern's and are delegated
untouched. A better boat is faster, tougher or better armed, not a different
aircraft. A gun is mounted on the hull's turret slots before its fixed rails, so
the one gun a Kite-hulled boat carries sits on her turret.

**Lift.** Every machine a drop carries has a **lift class** — Light, Medium or
Heavy, its weight class seen from the air (Hound and the heavy APC Light;
Warden, the escort APC and the militia technical Medium; Bulwark and the
breakthrough tank Heavy). It is not the machine's inertia, which manual driving
reads separately. Every airframe has a **lift rating**, the heaviest class it
takes: a Valkyrie lifts Heavy as built, the Hermes Light (a Hound or a light
APC under her belly), and every other pattern nothing. A
bus-class boat (Buffalo, Tarsus, Mule, Nebula) earns Light or Medium lift
through the third fitting slot, the hold: a **cradle** adds the rating and keeps
the seats, so a cradled boat flies its squad loads like any other boat and then
machines. Deboarding stays one kind per sortie: a squad load or exactly one
machine. Other small boats never lift a machine. Whether one carrier takes one machine
is a single pure check (`LiftAdmission`) that every consumer asks — the drop
powers, the briefing and a per-mission lift budget — rather than comparing
classes itself; a refusal is either *no lift* or *under-rated*.

**The lift queue.** Lift changes who flies, never who may call: the powers'
sources stay where `command-powers-nouns.md` puts them. The player's machines
ride the **ferry**, the marine transports that bring the company in, through one
first-in-first-out **lift queue**. The opening squad loads go first: each craft
flies its own sorties, then a lift-rated craft waits off-map on **standby**. A
standby craft first takes the last squad sortie another ferry craft still owes
(from the craft owing most, flown to that craft's landing ground with its marines,
a load larger than its seats split), so no machine comes down while a squad still
waits. Each player order is a **machine drop** job; a standby craft with no squad
sortie left to take takes the oldest job it can carry (`LiftAdmission`) and flies exactly that machine to its landing ground,
so a boat shot down with a machine aboard settles as that boat's loss like any
other. A budget carrier no ferry craft matches (the employer's or a ship's heavy
lift, or a committed cradle with no seat in the arrival) joins the ferry as an
ordinary standby craft of its pattern, so heavy lift is one more ferry craft,
lands squads with the rest of the ferry, and is not a separate delivery. An Aeroshuttle standing in for a cradled hull (Conquest
paired arrivals) inherits its lift rating; one standing in for a Valkyrie does
not, and that heavy lift joins as above. A job waits until a craft that can
carry it is free, and reads as having no craft when none is left alive. The
carrier crosses launch in the power's commitment like the rest of its payload.
The commander AI's mech drop and enemy lances keep their own deliveries.

**The briefing reads lift before launch.** Its lift row shows one slot per boat
berth on the company ship (an empty or grounded berth stays visible) and a slot
for the **mission lift** (heavy lift a ship, employer or contract supplies), each
with a weight symbol for its rating. Every bay machine sits in the field inset
beside the squads, as a small damage doll under its chassis or vehicle type,
carrying the wear its repair still leaves, and two stacked bricks: its weight,
lit red when lift falls short of its class, and a warning, lit and slowly
breathing whenever it will not drop. Its words are its hover, which gives
one verdict: drops, no lift of its class,
lift spent ahead of it, no drop call at all, or held back. It asks the
`LiftBudget` the drop power will build, machine by machine in bay order, and
decides nothing itself (`BriefingLift`). Beside Deploy, four **warning lamps**
sum the force as it would launch: strength under the recommendation, wounded or
empty billets in the selected squads (hours until the wounded are fit, and what
Replenish can draw for the empty ones), lance mechs under repair (hours left),
and machines that stay aboard. A lamp is dark while its check passes, lit amber
when not, and never blocks the launch; its words are its hover
(`DeployWarnings`). Beside the lift row, the briefing's **air cover**
shows what flies over the company as plans in the same hand: one tile per fighter
type with how many aircraft its carriers put up (one a bay, every carrier aboard,
no toggle), and the close-air-support gunship, one per fleet ship that brings it
(`BriefingAir`). The count is airframes, not sorties. Past nine squads, every full-strength
squad folds into one stacked tile with a multiplier, its hover counting the stack
per officer, so only the wounded and short-handed squads keep their own tiles.

**Campaign overhaul survives tactical servicing.** An owned fitted boat retains the
worst missing-hull fraction observed across its trips, including a final partial tick.
Its persistent identity joins those observations after tactical entities leave the
field or receive carrier patches. Settlement freezes that evidence once for the
battle; destroyed boats are losses, never recovery candidates. Campaign boats stay
grounded during their bounded recovery, including in the ambient ship scene.
`company-view-nouns.md` owns the repair clock, resource acceleration and lesson.

**Fitting a transport does not remove its hold.** Unloading, payload reloads,
and boat-deck dispatch resolve the transport pattern from either a bare pattern
or a fitted boat. They never replace the flying frame to make it qualify: that
would discard the campaign identity, plating and drive. The same delivery state
machine serves both representations, including subsequent sorties and partial
passenger loads.

**Missing company lift is a launch constraint, not a replacement order.** A
manifest with no player boat keeps only explicitly supplied employer craft.
Employer lift can operate alone only when it covers all required drops; ordinary
operations with selected company personnel still need a company boat. Local
stationing incidents retain their own local lift. Briefing and launch enforce
the same rule before map construction; neither fills missing player trips with
anonymous Aeroshuttles.

**A company boat flies with a shield and one flux pool, on a hardpoint's rules.**
`FittedBoat.shieldSpec` builds her shield from her emitter (arc, flux per blocked
point of damage) and her power fitting (pool and venting), and `AirSystem` fits it
into the battle's flux shields (`TurretShieldService`, the hardpoint shields'
service, `FluxShield`'s rules) when she spawns. It rises toward what her guns are
on or what last hit her and turns to it; a round credited to a shooter inside its
arc is stopped whole and becomes flux, and so is each anti-air post's share of the
area drain. **Her guns charge the same pool**: every round but a missile's, an
energy round most (`BoatGun.Family`). Past 85% of the pool her flux guns rest
until it vents to 55% while missile pods keep firing; a full pool overloads, the
shield drops and every gun falls silent until it has vented. She is drawn with
the dome shader cut to her arc (`TurretShieldField`), where her hull is drawn and
only while she is over the battle, in her emitter tier's tint. The shield goes
when she falls or leaves. Other aircraft carry none.

**The fit owns seats, ramp and loaded speed; the deck owns the turnaround.**
The frame's seats are the pattern's (the Hermes lands a whole squad), and its
deboard cadence is the pattern's quickened by the drive. A boat flies slower
with weight aboard: a machine drags her by its lift class and each marine a
little (`FittedBoat.carrying`), so she runs back faster than she ran in, and a
better drive wins the loaded speed back. The deck turnaround stays off the
frame: it is paid for in hand-seconds and a fitting does not buy more hands.

**A fixture carries the fitting ids, not the frame.** A replay that rebuilt a
plain pattern would fly different aircraft than the run it is replaying, so the
codec writes the ids beside the type and rebuilds the frame on read; a fixture
without them reads as standard fit, which is what every boat that has never been
refitted actually is.

**A ship's boat flies rather than counting down.** A bay could have been given
a timer that emptied a berth for a while and put a boat back looking serviced,
and from a deck screen the two are nearly indistinguishable. What a countdown is
not is the same machinery: the berth would empty with no aircraft existing, the
hull that came home would not be the hull that left, a boat could not be lost,
and every consequence a garrison field already has would be owed a second
implementation for ships. So a boat is an air entity on a mission through the
ordinary state machine, and everything downstream of that is free — including
the hand-back, which is the same one every based sortie runs.

Where it goes is **deliberately nowhere**: out through its bay's door to a point
off the hull, and back. On a deck view there is no elsewhere to fly to, and
inventing one would be a second world nobody is looking at. What the flight is
for is the time the berth stands empty, the hull it comes home with, and the
crew having something to turn round; a mission that means something replaces the
destination and keeps all of that.

**An indoor departure follows the room's clear corridor.** The boat lifts into
a low hover, clears its own berth into the central aisle, stops to turn, and
crosses the actual opening on its centreline. It climbs beyond the opening and
returns along the same corridor to the same berth, stopping before each turn
and settling before the field takes it back. A single rank clears its noses
into the apron before aligning with the opening. These are managed flight
phases over the same air body, with the hull's acceleration and turning limits;
they are neither wheeled taxi nor a free-flight shortcut across occupied banks.
The pressure screen at the opening passes aircraft while keeping infantry aboard.

`BoatSortieSystem` is the sibling of {@code AirStrikeSystem} and is gated on the
one thing that separates the two places: a berth with somewhere off this map to
be. A garrison's hardstand has none — an aircraft on a lot flies to somewhere on
the same battle — so the ship-side dispatcher finds nothing on a ground map
without being told which kind of place it is on.

A boat berth is a **hardstand**, because that is what the kind means: a berth
something lifts off rather than rolls out of. A ship's boat leaves through a door
and needs no run, so it is a hardstand that happens to be indoors — a shelter
berth would send it looking for a strip.

**A field is worked by people, and they are on the roster.** An apron publishes
servicing at both flanks of every stand and a board ahead of each nose, and the
side's own technicians stand a watch on it — the same trade that works a motor
pool, because it is the same work: a machine standing in a berth with somebody
servicing it. They can be shot on the apron like anybody else, which is the
point of their being units rather than scenery. `ai-nouns.md` owns the shift and
`mechs-nouns.md` owns the trade.

That work is **published by the host rather than by generation**, which is the
opposite of how a fitted room does it and is the one place the airfield departs
from the pattern. Everywhere else a room's jobs are cut with the room and the
berth a job names is one that fitting laid. An airfield is a paved lot with
hardstands marked on it: which of them are berths, in what order, and what
stands on each is settled at setup. A servicing job names its berth by index,
and an index means nothing except against the list it indexes — published from
generation those indices would have to agree with a berth list registered later
in another file, in an order neither of them states. The symptom of getting it
wrong is technicians servicing the wrong aircraft, which nothing downstream can
see. Authored beside the berths, the job and the berth it names are made in the
same pass and there is no order to disagree about.

**Whether a stand is worked is read off the berth, not off the unit on it.** The
board is published during setup and the field puts its aircraft out on its first
tick, so asking for the unit finds every stand empty — which offers no servicing
anywhere and leaves the field unmanned for the whole battle, looking exactly
like an airfield nobody had got round to staffing.

Four rules give the field its stakes:

- **The hull is continuous.** An aircraft that comes home shot up parks shot up,
  and is written off on the ground by that much less fire. A sortie flies the
  hull that was standing there, not a fresh one conjured at those coordinates.
- **Loss is permanent.** An airframe burned on its pad or lost over the
  objective is not replaced, and its berth is written off for the battle. A
  field is a finite thing to lose.
- **A turnaround is a window, and it is a window because it is work.** Servicing
  used to be free and instant because it happened off-map at a carrier nobody
  could reach. On a field it happens on ground the attacker can walk onto — on
  the aircraft's own stand, with the aircraft standing on it — and it is paid for
  in hand-seconds from the technicians actually at that stand. A countdown made
  the field answer again on schedule whether or not anybody was working, so
  killing the ground crew denied it nothing and reaching the apron mid-servicing
  found the aircraft was not even there. Every turnaround owes a base cost before
  any damage is counted — magazines, tanks, a walk round — because a field whose
  sorties are never intercepted would otherwise turn them round instantly, and
  then owes the patching on top, so damage costs a field its next sortie as well
  as its hull. A properly manned field turns one round in about the time the
  countdown used to take; a field whose crew is dead never turns one round
  again.
- **An airframe destroyed on the ground goes up.** It is a full tank under a
  thin skin, and that is the whole reason burning one is worth a fire team's
  time; a hull that simply stopped existing was a target with a lot of hit
  points and nothing else. It leaves a fireball, a burning wreck, and a blast
  that catches whoever is beside it — the raiders who walked onto the apron
  included, since fire does not check anybody's colours, and the ground crew
  that came out to fly it. Recorded: burning three aircraft from four cells
  away cost about half a six-man fire team. Riflemen out-range that
  comfortably, so the price is for standing on the apron rather than for the
  raid. **Parked or rolling makes no difference.** A fighter killed taxiing,
  holding short, or partway down a takeoff roll or a landing rollout is the
  same tank under the same skin as one killed on its stand, and it goes up the
  same way. Taxiing aircraft became genuinely shootable once a strip made the
  ground procedure exposed rather than a formality, and a kill on it that
  produced neither fire nor wreck would have been a cheaper kill than the
  identical one a few seconds later on the stand — the crossing has to cost
  what the apron costs, or the whole reason a runway is dangerous is a lie for
  half its own length.

The fire **does not chain**, and it is a rule rather than a clearance. An
aircraft standing on a berth is left out of the blast outright, so one satchel
is worth one aircraft whatever is parked either side of it. A blast that took
its neighbours with it would make one satchel worth an entire airfield and
delete the only decision a raid contains, which is how much of the field to
spend the visit on, and that decision is too important to rest on two hulls
being small enough. The fire's *reach* is still sized to the stand and the
apron around it and still stops short of the next hardstand, which an authored
field puts eight cells away — that is what keeps it off the ground crew and the
riflemen over there, and it is the shortfall a wider blast or a tighter apron
would spend. What no longer depends on it is the aircraft.

Only the berth is spared, because only the berth is a decision. A craft caught
taxiing, holding short or partway down a roll is on open ground beside a fire
and burns like anything else out there, which is the same reason the crossing
is worth attacking at all.

**The wreck stays on the ground.** What the fire leaves is the aircraft's own
hull, charred and in three pieces, remaining where its sections settle
for the rest of the battle. The smoke that marks a fresh kill burns
out in half a minute, and with nothing permanent behind it a burned field looks
exactly like a field whose aircraft happen to be away, which is precisely the
question a raider walked over there to settle.

Where that place is depends on how the aircraft died. On a hardstand the wreck
is drawn off the berth rather than off the airframe, because the airframe is
dead, released and gone by the time anybody looks at the pad again, and the
berth is the thing that outlives what stands on it — a berth's own wreck never
moves, so its position is a fact the berth already carries. A craft killed
taxiing, holding short, or partway down a roll has no berth under it: it
stopped wherever the fire caught it, off the stand it flew from and often well
short of the one it was headed to. Its wreck is the same hull torn the same
way, but it has to carry its own position and bearing instead of borrowing a
berth's, since nothing else on the field remembers where a taxiway kill
happened. The berth that sortie flew from is still written off — a field
does not get an airframe back because the wreck is somewhere else — it simply
has no hulk sitting on its own pad to show for it.

An aircraft lost at altitude carries its horizontal momentum while falling,
trailing fire and smoke. A slow or hovering craft loses stability, tips toward
one side and builds lateral speed through the fall instead of receiving an
instant kick. A fast craft keeps its existing travel. The hull stays whole
and retains its original colors during the fall, then chars and separates at
ground contact. Fighters and utility craft such as the Mudskipper share this
same crash appearance. The sections inherit the hull's travel, spin about
their own centres and skid apart with diminishing speed
before settling. Its passengers and sortie are
lost immediately; its guns stop firing.
Ground impact is a separate physical event: a hull-sized explosion damages
nearby units of either side and structures, then leaves the same charred,
broken hull where its skid ends for the rest of the battle. The impact fire
burns down and the smoke eventually clears; the wreck never fades with them.
Rolling fuel explosions follow the sliding hull and spread across the ground
in a filled fan, including a hot centre. Position, size and outward motion vary
between bursts; the fan is an irregular expanding mass, not two straight arms.
Each released blast keeps moving independently, slowing and billowing outward.
Short sparks and small tumbling metal chips scatter with those bursts and
dissipate while the three main hull sections remain.
This is the visual aftermath of the impact; it does not repeatedly apply the
impact blast's damage.
The physical impact breaches walls, roofs and destructible natural props before
resolving occupants. The grounded hull continues crushing a swept corridor during
its skid. Overlapping contact samples strike each actor once per crash; damage
is tied to distance travelled, not frame count or visual fireballs. Permanent
mountain masses remain impassable. Settled airborne fragments are persistent
rough ground that halves infantry, mech and vehicle movement speed without
closing a route or opening any pre-existing obstacle. Hardstand and taxiing
wrecks retain their solid footprint.
A hull falling beyond the battlefield does not leave an invented wreck at its
edge. Battle resolution waits for an ongoing crash to settle.

A crashing hull can damage other parked aircraft. This differs from the
hardstand cook-off's deliberate exclusion of neighboring berthed hulls: falling
wreckage is an external impact, not a shortcut for raiders clearing a flight line.

**A hull comes apart along a V.** The nose section separates as a wedge and
what is left splits down the spine, both tears walked so the edges are ragged
and ragged differently for every hull on the field. Grounded kills leave a
slightly separated, turned arrangement. Airborne kills open those same tears
through motion, with bounded spread that preserves the aircraft's recognizable
shape. First ground contact fixes the pose and tear pattern for the rest of
the crash. The moving sections and permanent wreck share that same history:
removing the destroyed aircraft never repositions, reseeds or replaces its
pieces. Their motion follows battle time, so pausing or looking away cannot
change where they settle. This is drawn from the current aircraft sprite,
including upgraded art, without requiring separate wreck textures. The tear
follows a lattice addressable as ordinary source rectangles. See `HullBreakup`.

A hardstand or taxiing wreck is an obstacle, and **only** an obstacle. Nobody walks through it;
everybody sees and shoots straight across it. A non-walkable cell is opaque
here unless it says otherwise, so the wreck says otherwise — a burnt-out
airframe is a frame with holes in it, and an apron strewn with them is still an
apron you can cover by fire. What it gives the people beside it is **cover**,
which comes from being unwalkable rather than from being opaque.

Never opaque, and not out of fastidiousness. A sight line exempts its two
endpoints and nothing else, so anything opaque across a footprint wider than one
cell is a thing nothing can shoot: it hides behind its own overhang, and its own
explosion cannot reach the people who set it off. The intact hull learned that
the expensive way while it still stamped ground of its own — a field of three
aircraft went from burned in five seconds by six riflemen to untouched after
three minutes — and answered it with a careful rule about which single cell was
allowed to be opaque. It writes nothing now, so the rule survives only here,
where a hulk covering nine cells would otherwise reinstate the same defect on a
target that is already dead.

**A wreck never settles on top of somebody.** Whoever is standing where the
hull comes down — the ground crew who walked out to fly it, the raider who
walked out to burn it — steps clear to the nearest cell that will take them,
and a cell nobody could be stepped out of is left open instead. A unit sealed
into a cell it can never leave stops answering its orders for the rest of the
battle, which is a far worse outcome than a hull with a gap in it. A hull being
*placed* on a berth moves the one body in the cell it needs and nobody else, by
the ordinary rule for an immobile arrival — see the berth section above. A death
happens once and a placement recurs, so what a placement may move is bounded
much more tightly than what a wreck may.

A sortie's passengers are never at risk from this. An aircraft is taken off its
berth at the moment the request is dispatched, before the crew starts walking,
so the airframe standing on a pad and the crew walking toward it are never on
the field at the same time; the craft they board is loading, which is the one
grounded phase ground fire is refused. Should a loading craft ever be made
shootable, it owes its passengers a disposition, because they have already been
taken off the roster.

Every way a sortie can end draws one distinction: a craft that reached its own
pad is an aircraft home from a job, and one that ended any other way is an
aircraft that did not come back.

This is a second and independent way to end an enemy's air. Holding the
`AIRBASE` compound is the other, and the two ask genuinely different questions —
a field held with every aircraft burning supplies nothing, and a field lost with
the aircraft intact takes them with it.

**A pad is not an aircraft, and a sortie never conjures one.** The berth is
what an aircraft is taken from and given back to; a `LandingPad` on its own is
a painted square. A dispatch that could not find an airworthy stand used to
fall back to *the nearest pad by distance* — spawning a hull on ground it did
not own, flying the mission, returning to that same pad, and being destroyed on
arrival because there was no berth to recover into. Several concurrent sorties
picked the same nearest pad, so aircraft stacked on one hardstand, materialised
on it, and vanished into it. A field with nothing airworthy declines the
request and lets the trucks take it.

**Whoever asks whether the field can supply must ask about the kind of place
they can actually use.** The unqualified question — is *anything* airworthy —
answers yes on the strength of a fighter in a shed, which a vertical-lift
transport can neither reach nor lift out of. That disagreement between the
supply question and the supply answer is what sent every transport sortie down
the conjuring path above.

A **runway** is the second kind of place an aircraft can leave from, and the
first that is not a square of ground. It is the centreline a craft rolls along
and its two thresholds, published by the lot that laid it rather than recovered
by scanning for runway-coloured ground: the lot knows exactly where it put the
strip, and which ground kind stands in for runway is an art decision that has
already changed once without touching a line of generation. Geometry a system
depends on cannot live in the art. Neither threshold is privileged — which end
a roll starts from is a decision for the sortie, and it is decided on the
turning the whole procedure costs rather than on the destination alone. Picking
the end farthest from the target leaves the aircraft pointing the right way
after takeoff and is blind to everything before it: a craft parked beside one
threshold was sent the length of its own field to the other one and then had to
turn most of the way round on arrival, which buys a departure heading at the
price of a half-circle of taxiing and a half-circle of turning. Both ends are
scored on the turn at the threshold — from the direction the aircraft arrives on
to the direction it will roll — plus the turn onto course after it is airborne,
with a small charge per cell of taxi so a near end is not passed over for a few
degrees. `mapgen-nouns.md`
owns where a strip is laid; Air owns what happens along it.

**A base with a strip does not stop being an airfield.** Vertical-lift
transports keep using the apron hardstands on the same base, so a field with a
runway has both kinds of aircraft on it. That is what makes the strip a
distinct thing rather than a replacement for the pads beside it.

**The aircraft is the weapon.** A strike carries no turret. A turret traverses
and picks its own target, which makes where the aircraft points irrelevant to
where it shoots — and an aircraft whose heading does not matter is a
helicopter. A fighter's guns are bolted to its nose, so aiming them is flying
it, and that is what makes a pass worth watching.

So a strike flies **runs** rather than holding station. The line is laid out
when the run begins and is not re-aimed while it is flown: the machine commits,
pointed at where the enemy was when it rolled in, and whether they are still
there is their business. Between passes it goes out wide and comes back on a
different bearing, carrying its speed through the turn, because a fighter
cannot pivot on the spot at the end of a run — the turn is most of the time an
attack takes and none of its damage, which is the window a target has to get
out of the open.

**A run starts when the aircraft is pointed at the position from outside its own
reach — not when it has arrived somewhere.** Reaching a point and reaching it
pointed the right way are different things, the same distinction the takeoff
roll draws, and out here the difference is the whole pass. The start of a line
laid out one leg early sits on the far side of the objective, so a craft steered
onto it arrives *pointing away*: at a wide turn radius it then wheels through
most of a half-circle to get its nose round, wanders eight cells off its own
line doing it, and takes the pass past the position rather than over it —
measured at five and a half cells, which for a weapon that lands its rounds
within two is a sortie flown at an empty field. Both halves of the condition are
load-bearing. Without the standoff the craft rolls in from inside its own firing
range and the burst is over before it is aimed; without the alignment it rolls
in sideways and flies the pass as one long turn. A craft satisfying neither
carries on round its circuit, which is what a repositioning aircraft is doing
anyway — its heading sweeps the whole compass every circuit, so the condition is
reached rather than waited for.

**The line is then laid from where the craft actually is.** That is the same
moment of commitment, not a re-aim during the pass; what it removes is a lateral
error the aircraft has no way to correct, because a run flown by steering at the
far end of a line is a chord when it starts off the line, and the pass misses by
about a third of however far off the start was.

Rounds are **rolled onto the ground** ahead of the nose rather than resolved
against a victim. Each one lands where it lands and detonates there, so a burst
walks a scattered line across a piece of ground and being caught is a question
of how much of you is standing in it. Every round is an ordinary detonation, so
splash, wall damage, line of sight and roof interception all come from the
pipeline that already owns them, and a squad under an intact roof is not
strafed. Measured on a platoon of twelve in the open: **massed shoulder to
shoulder, one or two survive one strike; dispersed five cells apart, ten do.**
Both halves of that are the design — a run that killed everyone regardless would
make dispersal pointless, and one that killed nobody would make the airfield
pointless.

**A gun's cadence is set against the speed the aircraft flies at.** What a pass
puts on the ground is rounds per *cell* and not rounds per second: the beaten
zone is the same stretch of ground whatever the airspeed, so a machine crossing
it half again as fast at the same rate of fire simply works it half as hard.
Measured across the turn re-dial, the same strike over the same bunched platoon
went from killing ten of twelve to killing four, on an identical footprint.
Raising the rate rather than the damage is what keeps the picture the same —
bigger craters would be a different weapon. A rate of fire is not a volume,
because fire cues are thinned to their own minimum gap, so the two can be set
apart.

The **ordnance** is about delivery rather than about guns, which is what lets a
second kind of aircraft exist without a second kind of code. A rotary cannon, a
beam, a missile pod and a stick of bombs differ in how fast rounds leave, how
many there are, how tightly they land, how big a hole each makes — and, above
all, in how a round physically gets from the aircraft to the ground.

**Where a round lands is a consequence, never a dial.** An aircraft is at a
height, and every round leaves it with some velocity of its own plus whatever it
keeps of the machine's. The ground is where that sum arrives. There is no lead
offset to tune, because a lead offset is an answer with its reasoning thrown
away — and one set by hand goes stale the moment anything else about the weapon
moves. Three delivery classes fall out of the same arithmetic:

- A **gun** — a cannon or a beam — is a **laser pointer**. Its round is on the
  ground inside a few hundredths of a second, so the aircraft does not move
  while it is out there and the impact is simply where the nose was pointed at
  the ground. The reach is sight geometry: a height and a depression, nothing
  else. That is why it is more than a dozen cells and not four. Fire that lands
  under the aircraft is fire from something hovering.
- A **missile** has **its own motor**, so it neither depends on the aircraft's
  momentum nor is limited to a gun's sight line. It is out there for a real
  third of a second and it reaches half again as far, which is what lets a
  missile boat work a position from standoff and turn away without ever coming
  over it.
- A **bomb** is only **let go**. It keeps most of the aircraft's speed and none
  of its thrust, and spends about a second falling — and because it keeps
  *most* rather than all of that speed, the aircraft is past the impact by the
  time it happens. Bombs land behind the machine that dropped them, and nothing
  told them to. It is also the one delivery whose reach moves when the airframe
  is re-dialled, since all of its forward throw is borrowed: **a faster bomber
  has to come in lower.** Flown at the widened calibration from the old release
  height the stick landed further ahead of the aircraft than a missile pod
  reaches, which would have made the close-in weapon the standoff one. Height is
  what answers that, because it is the only dial that shortens the fall; a
  longer release range would have turned a bomber into something else.

A round with a real flight time is genuinely **in the air**: it goes on the same
in-flight queue every other slow-flight munition uses, so the ground under it
can change while it is there. Only a shell is fast enough to be resolved where
it left.

**Reach and firing range are one decision.** Rounds land from *range minus
reach* short of the target to about *reach* past it as the craft closes, so a
weapon that opens fire at twenty-six cells and puts its rounds four cells in
front of the nose drops every single one of them short — which is what a strafe
looked like from the ground, and the fault this model exists to remove. Every
class leads about half its own firing range.

A **finite load** makes the cadence part of the aim as well. A gun stops when
the target passes off the nose, so its reach alone decides where the burst ends;
a pod or a bomb bay stops when it is empty, and a load that empties early puts
every round short however good the reach is. The release has to last long enough
to carry the aircraft across the gap between its standoff and its reach.

**Range is not aim.** A gun bolted to the nose can only put fire where the
aircraft is pointed, so release is gated on the target lying within a cone of
the nose and not merely on being near. Without that the run keeps firing on
range alone and sprays the ground behind itself for the whole second half of the
pass — invisible while the reach was four cells and glaring once it was not.
The cone is also what ends a run: as the craft arrives over the position the
bearing to it swings out through a right angle in a fraction of a second, and
fire stops there.

That gate exposed a second thing. A strike arrives **on station**, which is over
the objective, so on its first pass the craft was already well inside its own
run-in with nowhere left to attack from — the whole first pass consisted of
flying away from the target while shooting. A craft that is not upstream of the
line it is about to fly goes out to the start of it first, like any later pass.

The one structural difference between kinds is whether the load is finite: a gun
fires for as long as it holds its target under the nose, a bomber or a missile
boat releases what it loaded and is done — which is why one can make three
passes and the other cannot.

Measured on a lattice of markers under one pass, the kinds come out visibly
apart: a beam lands **noticeably narrower across the run** than a cannon does
(1.5 cells against 1.8), which is the difference between painting a line and
throwing craters. Along the run the classes separate further — a cannon pass
works the ground from **eleven cells short of the target to twelve past**, and a
missile pass from **fourteen short to twelve past**, opening from further out
and finishing in the same place. **The nearest impact is on the target**, which
is the measure the other two cannot make: a pass that misses by five cells still
touches plenty of markers and still sweeps from short of the position to past
it.

**And they are told apart at a glance and with your eyes shut.** A delivery is
a thing to watch: the round leaves the nose, crosses open ground, and arrives.
The three kinds get three pictures and three sounds rather than one effect
scaled by calibre, because that difference is the only way a player reads which
aircraft is over them. A cannon throws a fast stream of hot streaks that arrive
short of where they were aimed and kick up dirt, under a thinned burst of gun
fire. A beam is a narrow line that is simply *there* the instant it is
released, dragged along under one held tone. A stick of bombs falls visibly —
long enough to watch it come down — and lands as the same blast an airframe
cooking off on its stand makes.

The presentation keys on the **delivery**, not on the carrier. A shell, a beam
and a bomb are what the effects are chosen by; nothing in the treatment knows
an aircraft exists, so a later carrier that puts rounds on the ground the same
way inherits all of it. The simulation resolves the delivery and publishes what
happened; what it looks and sounds like is the presentation tier's business
alone, and no rendering decision is readable from the simulation.

**A round arrives when the picture says it does.** The delivery resolves the
instant it is released, but a bomb is drawn falling for a third of a second, so
the blast waits for the bomb rather than preceding it.

**A cadence is not a volume.** A rotary cannon releases two dozen rounds a
second and a beam nearly twice that; a clip per round is not a louder gun, it is a
wall of overlapping voices in which nothing else in the battle can be heard.
Fire cues are thinned to a cadence that reads as a burst, and the beam — which
is one continuous sound rather than a series of events — is a held loop left to
lapse when the firing stops. A warship's lance follows the same rule: its
`fireSoundOne` sounds once as the sweep lights, and its `fireSoundTwo` is
re-requested at the beam's tip on every tick the sweep burns (a weapon naming
only the second has no separate loop and keeps its single report).

Every clip is one of the base game's own mono weapon files, registered under our
own id against the read-only install rather than copied, the same way the
vehicle engine loops resolve. Nothing is redistributed.

A **strike sortie** is the reason a station has sheds. An armed aircraft
leaves on the field's own decision rather than on a request for passengers,
works its target, and comes home to the shed it came out of. Its business at
the objective is its guns rather than its ramp, so unlike a transport it never
touches down on what it was sent to attack — it arrives on station, which is a
wider thing than arriving on a cell.

The field **commits half its sheds and keeps half back**, rounded up so a
one-shed strip still flies. A garrison that scrambled its whole air arm at first
contact would spend itself in the opening minute and have nothing left for the
assault the field exists to answer; a garrison that flew one aircraft while four
sat in their hangars is not exercising restraint, it is a station whose air arm
does not exist. The limit is a share of the field's own surviving
establishment, so a strip with one shed and a station with six are different
propositions, and an attacker who burns hangars narrows what the field can put
up *now* as well as what it can put up ever. It is asked of the **berths** —
how many sheds are committed — rather than of the aircraft, because a berth is
what a sortie actually spends.

**The stagger between launches is not the gap between sorties.** It used to be
the latter, and that alone made the limit meaningless: at the current
atmosphere calibration a sortie is about forty seconds from shed to shed, so a
forty-five second wait after each one guaranteed the field was empty before the
next aircraft moved, whatever any cap said — and what a playtester saw was a
station with five airframes putting exactly one over the battle. What paces a
launch is how fast the base can push one departure through its own taxiway and
strip, which is a handful of seconds; what limits the field is the share above.
The pacing is physical and the limit is doctrine, and confusing the two is what
produced a doctrine nobody had chosen.

The target is the **densest** enemy concentration rather than the nearest or
the largest: nearest sends aircraft after whichever scout wandered closest to
the fence, largest picks the same push every time, and density is both what an
aircraft is good against and what a player can see the reason for afterwards.
With more than one aircraft up the field asks for the densest concentration
**nobody is already working** — two cluster radii clear of it, so the second
sortie is attacking somebody the first is not. Two aircraft on one platoon
stays available and is the answer when the map holds only one concentration
worth attacking; what it must not be is the only sentence the dispatcher can
say.

**A strike cannot be shown to shoot headlessly.** Its guns are placed from the
hull's real weapon slots, which need the game loaded to read, so a headless
sortie flies unarmed and its time on station collapses to a few ticks. Every
other part of the cycle is observable and observed; that the arming works is an
inference from transports using the same resolver on the same path. A sortie
that comes up unarmed therefore says so once in the log, rather than flying
silently harmless forever.

**On its wheels it is on the ground, and the ground is in the way.** A taxiing
aircraft follows walkable ground round the buildings rather than steering
straight at the threshold through whatever stands between — a body built for
flight has nothing in it that stops, so the straight line went through the shed
it had just come out of. The route is geometry only: an aircraft is not
queueing behind the infantry crossing the apron, it is going round the hangars.
A move that would end inside something is refused per axis, so a craft that
cuts a corner slides along the wall; a craft that is *already* inside
something — which every aircraft is, standing in its own shed — is let out,
because refusing on the destination alone pinned it in the hangar for the rest
of the battle.

**A taxiing aircraft is still the air entity.** It is at zero altitude with its
engines running, which is the shape the loading phase already established, and
it is deliberately not turned into a grid unit for the taxi. The only handoff
between the two representations is at a standstill, on a berth; making the taxi
a grid walk and the roll a flight would put a second handoff in the middle of
one continuous movement, which is exactly the seam this model keeps still.

**The two representations draw the same size at that seam, by law.** The berth
hull (`UnitRenderService`'s `emitHull`, and the wreck it leaves behind) and the
air entity at `altitudeT == 0` (`ShuttleRenderSystem`) both draw at
`AirAppearance.GROUND_SCALE`; a parked scenery hull on a civilian berth
(`ParkedAircraftRenderSystem`) agrees for the same reason — it is the same kind
of object at rest. A hull that changed size crossing the one handoff this model
keeps still would read as a launch or a recovery popping, which is exactly the
seam the taxi/roll design above exists to keep invisible.
`AircraftGroundAirHandoffScaleTest` pins the berth and the air-entity collector
landing on the same drawn number so this cannot drift back apart silently.

**And they are the same size to shoot at, for the same reason.** How much of an
aircraft there is to hit is one number, and while each representation derived
its own the same Valkyrie was four and a half cells of aircraft taxiing and half
a cell on its hardstand — the archetype's authored figure, which is the answer
for a type whose whole family is one size and is nothing to do with an aircraft.
So the number is the **airframe's**, `Airframe.targetRadiusCells`, asked by the
air carrier and by the shared roster accessor alike; a berthed hull reaches it
through the airframe it carries, in a lookup rather than by asking the field
which hardstand it is standing on, because that read happens per candidate per
shot. That is the same convention a turret and a convoy chassis already follow:
a type whose geometry is per-instance answers from the per-instance thing, and
only a type that is genuinely one size falls through to `UnitType.radius`.

**Being a large thing to hit is deliberately not being a large thing to walk
round.** A parked aircraft denies people no ground at all now, and the ground a
wreck denies them is a fixed square rather than one derived from the hull: how
much deck an aircraft takes up is a gameplay decision, and coupling it to the
art would re-tune every apron on every field whenever a sprite changed. The same
separation the drawn-size fix drew, one seam over.

Sizing the parked hull honestly is what turned the no-chain rule above from a
sum into a statement. A cook-off catches a body at *blast radius plus that
body's own radius*, so for as long as every aircraft reported the archetype's
flat half cell the rule held by four and a half cells against a pitch of eight,
and nobody had to mean it. Reporting real drawn size put the largest transport
at four and a half cells of aircraft on its own, which reaches across the gap;
that it had never been seen was down to which hulls the fields happen to base,
and a rule that survives on which hulls somebody picked is not a rule. So the
berthed aircraft is excluded from the fire by name and the sum is no longer
load-bearing for it. The bound the sum would have had to satisfy is not
knowable anyway: it is the size of the largest hull anybody ever bases, which
is not a number the build can be shown.

**Rolling is not flying slowly.** Ground movement is its own locomotion model
rather than the flight steering held down to walking pace. What flight does to
change direction is point the nose and wait for the sideways component of its
momentum to bleed off; a wheeled aircraft has no sideways component to bleed,
goes where it is pointed and nowhere else, steers through an arc its
undercarriage sets rather than at a heading rate, and can stop. Driven the other
way it crabbed across the apron and settled onto its waypoints like something
hovering, which is what a decorated flight profile buys.

Three consequences carry the feel. Velocity is composed from the heading and one
speed, so lateral drift never exists rather than being damped away. The arc is
bounded, and speed through it is bounded by the sideways load the wheels will
take — so **a turn tightens as the craft slows**: it gives up speed for a sharp
corner instead of widening it, and at rolling speed it can only make the
gentlest correction. And an aircraft too far off where it is going stops and
**swings its nose round standing still**, on the brakes, at a rate that is a
ground manoeuvre rather than a hull turn rate — the one thing a rolling model
cannot do on its own and the one thing every aircraft plainly does.

The route is followed by a carrot sliding along it rather than by aiming at the
next cell, which is what stops a body with a turn radius orbiting a waypoint it
cannot reach; it is the same pursuit law the ground vehicles and the infantry
mover already follow, with a look-ahead derived from the turn radius, because
the two are the same quantity.

**Lining up finishes before the roll starts.** Reaching a threshold and reaching
it pointed down the strip are different things. A craft that opened the throttle
and steered onto the centreline at the same time arrived at rotation speed
somewhere off the side of it — the turn threw it off the line it was supposed to
be building speed along. So the roll will not accelerate at all until the
aircraft is straight: it squares up where it is standing, which is a stretch of
time in the open like every other part of the procedure, and only then rolls.
Both rolls are driven on the wheels, which is also why the landing rollout no
longer needs its heading pinned to the strip every tick to stop it
weathercocking across the runway — it cannot leave the centreline sideways.

**A plume is not a hum.** The engine note keeps an idle floor, because a
machine on a hardstand hums; the visible thrusters do not, because an aircraft
rolling at walking pace on its wheels drawing full afterburner reads as one
hovering an inch off the ground. The takeoff roll is the exception, and the
reason the phase is asked for rather than the altitude: it is the one ground
phase where the engines are doing everything they can, and at the start of it
the aircraft is still at zero altitude.

**And it really is shot at.** Air used to be reachable only by defence posts
and only while airborne, which meant the minute of open ground a strip buys was
a minute of complete safety — a fighter taxiing past a fire team was in no
danger whatsoever, and the trade the runway exists to make was a fiction. It
was then made damageable without being made a target: an attrition field
counted enemies within ten cells and subtracted hull, so the aircraft lost
structure while nothing in the battle had aimed at it. Nobody fired a round,
nothing was drawn or heard, cover and walls and roofs counted for nothing, an
enemy who could not see it drained it anyway, and no shooter was credited with
the kill. Damageable and targetable are different properties and the difference
is the whole feature.

An aircraft on its wheels is a large slow object in the open, so it is acquired
and engaged like anything else: within rifle reach rather than through an
anti-air bubble, by whoever has a clear line to it, through the cover and the
walls that stand between, with tracers and impacts and somebody credited
afterwards. A loading craft stays exempt: its passengers have already left the
roster and making it shootable would owe them a disposition nothing gives them.

**Being shot at is a matter of line of sight, and it is not gated on fog.**
That is the same rule every other body follows and the same rule a convoy
chassis follows: the crew of an aircraft see nothing — it carries no vision at
all — and a shooter needs a clear line rather than a revealed cell. The
player's own picture is a separate question, and `ShuttleRenderSystem` answers
it differently for air on purpose: **every craft is drawn whether or not the
player's side can see it, and that is a kept debugging affordance rather than a
missing fog gate.** A sortie is a minute-long procedure across the whole map and
watching all of it is how the thing gets developed at all. Do not "fix" it into
a fog check; if the player's picture ever needs tightening, that is a decision
about what a player should see, taken deliberately, and not a bug report about
this line.

Both halves are derived from the locomotion, never listed: a phase left off a
hand-written list is a phase nothing can touch, and replacing the armed loiter
with attack runs did exactly that and made every strike invulnerable while it
attacked. `ShuttleMission.isOnItsWheelsAndExposed` survives that change with one
job left — deciding whether a kill leaves a hull on the apron or a machine
falling out of the sky — and is no longer a targeting gate.

A craft that has to roll has a **ground procedure** either side of its flight,
and it is on its wheels and shootable for all of it: out of the shed, down to
the threshold, a wait if the strip is busy, and then the roll itself, which is
the only part where the aircraft is accelerating and not yet flying. Coming
home it is the same in reverse — touch down at the end it reaches first, roll
out to the far one, turn off, taxi back to its own shed.

**A landing is flown, and nothing puts the aircraft down.** The approach used
to be a railroad with a takeover at the end of it: out to a point a stated
fourteen cells along the extended centreline, then down that centreline, and at
the threshold the simulation placed the craft on the strip pointing along it and
drove the rollout from there. It worked while every aircraft could turn inside
seven cells. The atmosphere calibration that widened the circle to twenty-odd
made it geometrically impossible — a machine cannot turn onto a centreline it
joins fourteen cells short of the threshold — and what the takeover had been
hiding all along was a craft arriving crabbed and being snapped straight. At the
widened calibration it stopped hiding it: measured, a fighter reached the
threshold a hundred and seven degrees off the runway on average, and from four
dozen arrival poses only five ever reached it at all. The rest orbited.

So the arrival is **solved and then flown**. Where the craft is and where it has
to be are both poses, and the shortest path between two poses under a bound on
how tightly a vehicle can turn is a closed-form question the project already
answers for its ground vehicles; the forward-only subset of that answer is a
Dubins path, and one exists for any two poses. The circuit falls out of the
geometry — the leg out, the turn onto final, the straight run in — rather than
being authored, and **the touchdown is a real arrival**: the aircraft keeps its
position, keeps the heading the path ended on, and keeps the speed it came in
with, so the rollout starts from a landing rather than from a standstill on the
numbers. Measured on the shipped airfield, a fighter now crosses the threshold
two degrees off the centreline at approach speed, with no tick in the whole
approach moving it further than its own airspeed allows.

Three things about that path are load-bearing.

**It is solved to a final approach fix, not to the threshold.** The last stretch
is a straight line down the runway axis, which absorbs whatever the turn left
and — the structural reason — makes a go-around a re-plan rather than a
degenerate one. A craft denied the strip a cell short of the threshold, asked
for a path to the threshold, is told to fly straight ahead; asked for a path to
the fix behind it, it is told to fly a circuit, which is what a go-around is.
How long that final is derived from the turn radius rather than stated in cells,
because the number it replaces was right for a shuttle and impossible for a
fighter.

**It is planned at a wider circle than the craft can fly**, because a path laid
out at the tightest arc a hull manages is one the follower has no margin to
correct on: it falls progressively outside the curve and never catches up.

**It runs on past the threshold, down the strip.** A follower whose carrot has
nowhere left to slide pins it to the last point and starts chasing it, and a
body with a turn radius chasing a point it is offset from swings its nose
further off the closer it gets. The takeoff roll already aims its carrot beyond
the far threshold for exactly this reason; a landing has the same shape arriving
from the other end. **The look-ahead is the crab at touchdown** — a craft
following a carrot on the centreline from half a cell off it arrives
`atan(offset / lookAhead)` crooked — and that lands on a specific number,
because a wheeled aircraft more than five degrees off where it is going stops
and swings its nose round standing still, which on a runway is the pirouette
this whole model exists to remove.

The approach is obstacle-free and constant-radius, which is honest at altitude
and would not be on the ground, and it is a path rather than a trajectory: how
fast the craft flies it stays the caller's business. A circuit at a fast hull's
radius is large, and on a small map it takes the aircraft off the edge for
several seconds — thirty-odd cells outside a seventy-six cell field, measured.
That is reported and not corrected. The map edge is not a wall to something at
altitude, every off-map sortie crosses it by construction, and the only way to
buy the legibility back would be to arrive crabbed. If a hull spends too long
out of sight coming home, the lever is the atmosphere calibration that set its
circle. A landing that will not solve at all still lands — it flies straight in
and arrives on whatever heading it managed, which is what every landing did
before — but Dubins has an answer for any two poses, so that is a guard rather
than a case: across four dozen arrival poses for two hulls it never once fired.

**Coming home is not leaving.** A craft that rolled off a strip owes itself back
to it, and the leg that takes it there is an approach: it steers to a runway
threshold rather than an off-map exit, it descends rather than climbing away,
and it ends by taking the strip rather than by ceasing to exist. Everything a
phase decides is the other way round, which is why it is a phase of its own
rather than a departure with a different destination — and why the landing
procedure sat unreachable for a while behind an egress that always flew off the
map. Which end it lands on is decided when the leg starts rather than at
dispatch, because it depends on where the sortie actually finished up. That is what a runway
buys over a vertical lift: a minute of ground movement in the open, every
second of which somebody can be standing on.

An aircraft is **on the map from the moment it leaves a berth until it is
finished with one**, and that is what decides whether it is drawn. Being over
the battle is a narrower thing, and it is what decides whether the craft's guns
and sensors are working: a machine taxiing to the strip is nose to tail with
its own ground crew inside its own perimeter, which is neither somewhere to
hunt for targets nor somewhere to sweep fifty cells from. The two questions
were one predicate for a while, written as a list of the phases that qualified,
and every phase added afterwards was left out of it — so a craft loading on its
pad, taxiing, holding short, rolling, or taxiing back in was not drawn at all.
A minute of exposed ground movement nobody can see is a vertical lift with
extra steps.

Splitting the predicate did not close the hole; it moved it. The "over the
battle" half was still a hand list — INCOMING, PAD_DESCENT, LANDED, DEPARTING,
RETURNING — and `ATTACK_RUN` and `REPOSITION` were added to the phase enum
afterwards without a mention in it, which is the whole of a strike aircraft's
time actually attacking. A fighter mid gun-run swept no fog and fired no
mounted turret, invisible in exactly the way the runway-exposure list already
warned about once. `ShuttleMission.isOverTheBattle` is now an exhaustive
switch with no default case: every phase this enum ever grows must be placed
on one side or the other before the project compiles, so the next phase added
cannot repeat this by omission the way the last two did.

The strip itself is a **resource with one occupant**. Two aircraft rolling down
one runway is not a race the simulation is entitled to lose, and the queue that
falls out of it is the point — a field with three aircraft and one strip
launches them in sequence, so anything sitting between a shed and the threshold
delays every one of them. That queue was theory while a field flew one sortie
at a time; with several up it is load-bearing, and holding short is an ordinary
part of a departure rather than an edge case. A craft holds the strip from the moment it starts its
roll until it is airborne, through the final approach it is committed to, and
through a landing rollout, because in each of those it is either standing on the
strip or about to be.

**A claim outlives its aircraft, and that is a field closed for the battle.**
An aircraft on its wheels is shootable — that is what the exposed ground
procedure is for — so a fighter killed during its takeoff roll is an ordinary
thing to happen, and nothing on that path gave the runway back. Every sortie
that came home afterwards was refused the strip and flew circuits until the
battle ended: measured at seventy-five go-arounds and still climbing, which from
outside is exactly one aircraft looping forever on approach. The release
therefore belongs to the **teardown** — the one place every ending goes through,
whether the craft was shot down, scrubbed on its pad, or shut down in its own
shed — rather than to any of the endings, so an ending nobody has written yet
inherits it. Releasing stays tolerant of a craft that never held the strip,
which is what lets that one call be unconditional.

**Both ends of the queue ask at the same rate.** The strip goes to whoever asks
on the tick it is free, which is deliberate — a queue with entries in it would
have to survive one of them being destroyed on the taxiway — but that rule is
only fair while everybody asks equally often. A craft holding short asks every
tick; a homebound craft that asked once, as it crossed the numbers, asked once
per circuit, and lost that race about as often as the strip was busy. On a field
flying several sorties off one runway that is most of the time, and the result
is a landing that never happens for a reason that has nothing to do with
geometry. So a craft **on final asks for every tick of it**, from the fix to the
threshold.

**And a go-around is bounded, because an aircraft that cannot land is a bug
with no acceptable duration.** After a couple of circuits the craft stops asking
and puts down on the strip whatever is recorded against it. The fallback has to
be a landing: a craft that gave up by leaving, or by ceasing to exist, would
have turned a queueing problem into a lost airframe, while two aircraft on one
runway for a few seconds is the lesser fault by a wide margin.

Handling on the ground is the **same hull with a ceiling on it** — a bus taxis
like a bus — rather than a second authored profile per aircraft, which would be
a second place for one fact to live. What is borrowed is power and braking; the
rest is being on wheels. Turn radius in particular is gear geometry and is not
carried across from the hull's flight agility: that number is an angular rate
rather than a radius, and converted at taxi speed it makes every aircraft pivot
on the spot, which is the reading the ground model exists to replace.

A strip is not a requirement for an air arm. Most airbases have none — an
aircraft that lands vertically needs somewhere to stand and somewhere to be
worked on, and a strip is what a base adds when something has to roll.

Distinct from the **scenery hulls** that dress surplus civilian port berths.
Those are props: no unit, no HP, and nothing flies them. They look identical on
the map and are not the same kind of thing at all.

An **authored airfield** is a base whose lot published it as one. That is a
single fact carrying both halves — berths the air arm draws sorties from, and
the `AIRBASE` node a commander reads as a place to take — and the two are
inseparable on purpose, because a field with only the first half is a spawner
wearing an airfield's paint. Whoever asks whether the garrison can fly is asking
about a base with a node on it; a civil landing pad has neither half and is
somewhere to put an aircraft down. `mapgen-nouns.md` owns which lots are which.

A sortie flown from an authored airfield has one more phase in front of
that. The craft starts down on its own hardstand and **loads on the ground**:
its passengers are not aboard when it spawns, but walk out to the pad and
embark, and it lifts when it is full or when nobody else is coming. That is the
difference between an air arm and a spawner. A sortie that arrives already
loaded has no cost and no story — the aircraft is a delivery mechanism that
happens to be drawn. One that has to be loaded has both: the garrison commits
people who can be seen and shot, they cross open ground to reach the field, and
an attacker standing on the airfield — or merely shooting across it — has
stopped the lift without touching the aircraft.

**Launching off a hardstand climbs before it flies away.** Loading pins the
craft to the ground, and the leg that follows judges its own altitude by how
much of the flight to the LZ is left — which is already the whole flight on
the very first sample of a fresh one. Without something between them a sortie
that just finished boarding popped from the ground to cruising height in the
single tick the ramp closed: altitude and drawn scale both jumped their full
range in one frame, the same discontinuity the settle below exists to remove,
run the other way. So a launch climbs on the spot, over the pad it just left,
before it turns for the LZ — the mirror of the settle, and a phase of its own
for the same reason: a craft climbing straight up and a craft flying a leg are
not moved by the same model. Only a sortie that starts down on its own
hardstand needs this; one entering from off-map is already at cruise, and one
rolling off a strip reaches cruise over the length of its takeoff roll.

**A vertical lift settles onto its pad; it does not arrive on it.** The run in
brakes down to a hover over the spot, and the last of the descent is its own
phase: the craft holds, kills the drift it came in with, and sinks. Its heading
is whatever the approach left it on and is never touched. What that replaces was
a single tick in which the shuttle stopped being where it was and stopped
moving — which is a helicopter ceasing to exist mid-air and reappearing landed,
and reads exactly as badly as that sounds. A pad does not need a runway's
circuit, because a machine that lands vertically can arrive from any bearing;
what it needs is the deceleration and the descent to be things that take time.

**The settle ends on a condition, not a duration.** It used to end after a
stated number of seconds regardless of where that left the craft, which is the
same placement the settle itself exists to remove, just deferred rather than
undone: a stated duration is somebody's guess at how long braking takes, and a
bus-tier hull's gentler brakes make that guess wrong by exactly the margin its
brakes are gentler. Measured on the shipped hull ladder, every bus-tier
transport — Buffalo, Tarsus, Mule, Nebula, Valkyrie — was still two and a half
to nearly four cells short of the pad, doing several cells a second, when the
clock ran out, and was snapped to a dead stop there anyway: over a hundred
cells/sec² against a brake rated for four. The settle now ends when the
craft is genuinely down — over the pad, and its speed killed — both read off
the body's own motion rather than off a clock, and both against numbers the
hull owns (its braking accel) rather than one authored duration asked to fit
every hull. A settle that could in principle never converge would be worse
than the snap it replaces, so it still carries a bound; landing on that bound
still respects the brake; nothing is moved, the settle is simply accepted as
finished where the craft actually is.

**Unloading is bounded at both ends of the trip.** A passenger needs somewhere
to stand, and a landing zone can have nowhere: a squad that lands and holds
around its own drop point fills the search on its own. The craft reaches further
before it gives up — the search is nearest-first, so a wider bound costs nothing
when the ramp is clear and only spreads the spill when it is not — and if there
is genuinely nothing, it leaves with whoever is still aboard. Waiting is not an
option a delivery has: an undelivered passenger is a failed delivery, while a
craft that retries forever holds its landing zone for the rest of the battle,
never departs, and reports nothing. The same rule and the same reason apply to a
convoy stopped on its drop point; `convoy-nouns.md` owns that vehicle.

A loading craft is on the ground and is not shootable-down as an aircraft, the
same as one that has landed. Boarding has a deadline, because the squad walking
out to it can be killed on the way: without one, a sortie whose squad died in
the yard would hold its hardstand for the rest of the battle and the air arm
would quietly stop existing. Whoever reached the ramp goes; the seats their
friends would have filled stay empty. The deadline **scrubs a sortie only when
nobody boarded** — boarding takes a passenger off the roster, so cancelling on
top of people who made it aboard does not call off a delivery, it deletes them.

A sortie borrows its crew and gives back whoever it did not take. While it is
loading, the crew is held at an authority nothing outranks, so no other order
can pull the boarding party apart mid-lift; when the sortie closes — lifted or
scrubbed — the survivors pass to whoever the delivery policy names, normally
the mission commander. Handing them over rather than merely releasing them is
the load-bearing part: a commander's pool is what it owns, so an unclaimed
squad is every bit as stranded as an over-claimed one. Without the handoff the
ground crew that did not fly stands on the pad for the rest of the battle while
each new sortie marches another four out to join them. It is an air entity
throughout that lifecycle, not a temporary handle or a parallel id space.

**At a ramp the squad's leader boards last**, so a lift that cannot take
everybody leaves the NCO on the field with whoever it could not carry rather
than flying the leader out from under them. Decided among the marines actually
standing at the ramp on that tick, which is what last can mean here; a leader
standing there alone still flies. The manifest end of the same law — the NCO is
seated first in the squad's run, and so rides its first lift — is
`company-view-nouns.md`.

Transport survival, payload delivery, and mounted guns are role capabilities;
they do not make the craft a normal grid combat unit.

**A transport that has unloaded leaves.** There is no phase between setting the
payload down and turning for the exit, whatever the hull is carrying on its
hardpoints. An armed craft that stayed to work the drop zone was a free
gunship: it hung over the objective on the squad's centroid with nothing but a
fuel timer to make it go, so the delivery quietly bought fire support the
mission never paid for and the marines it dropped were not the ones deciding
the fight. Guns on a transport are what it defends *itself* with on the way in
and the way out — a run through an anti-air bubble is the risk the sortie takes
— not a reason to hold station. A craft that wants to work a target is flying a
strike, which is its own sortie with its own attack runs and its own cost.
Conquest already delivered this way; every other mission now does too.

**Gunships.** The sortie that is *for* working a target by staying over it. A
called Mudskipper (Close Air Support, `command-powers-nouns.md`) enters off the
map from the nearest edge, joins a broad ring about the targeted
point tangent to its approach rather than flying to the point and out again, and
circles overhead of it for a bounded visit, then leaves the way it came. The
visit's clock runs only while it is on the ring, so what the player buys is the
time the turret had the ground. It holds one direction round for the whole visit
and a slower pace than its own cruise, and the ring is sized so the airframe's
turning circle fits inside it. The station radius is sixteen cells at four cells
per second, about twenty-five seconds per circuit. The ring is *worked*, not
drawn: an oval that slowly turns and wobbles, swinging up to a quarter of the
radius in and out, so no two laps trace the same line. The craft slows and dips
as it tightens in to work the ground, then opens out, climbs and picks up pace
on the wide leg, and its hover bob quickens as it hangs and settles as it runs.
A fixed radius at one pace and height turned it at one rate for the whole visit,
which read as a model on a turntable and flattened the visit the player paid
for. The swing keeps the ground inside the turret's reach from every point.
Cover fighters keep the plain ring, since they hold a station between jobs. Mudskipper transit is seven
cells per second, with gradual acceleration and a restrained heading slew so
entry and departure share its deliberate overwatch feel.

It is an overwatch role, not a strafing one: the craft is not aimed at anything.
It carries the hull's own turret, at the hull's own weapon slot, like any other
armed craft, and that turret traverses freely, so circling is what lets one gun
cover the ground from every side of the ring and keep a target in reach as the
craft goes round. There is no special mount, arc or weapon for a gunship; a
Mudskipper's one hardpoint is simply the thing it circles with.

A gunship carries nobody and sets nothing down, borrows no berth, and is as
exposed as any aircraft in the air: it is a sortie flown for its guns, paid for
at the card when player-called, and it adds nothing to the transport law above. It is not a free
gunship in the sense that law forbids, because nothing was delivered to get it.

An employer may instead supply and direct a mission gunship. It remains an
ordinary vulnerable aircraft with the same weapons, damage and crash rules;
its ownership grants no player card, cost, crew seats or campaign airframe.
A mission may give its incoming gunship a committed waypoint pass before it
joins the station. Ordinary steering flies each leg; it grants no immunity,
teleportation or predetermined damage. Actual guided-AA launches against the
craft publish its missile-warning fact for observed story responses. An employer
controller may interrupt the pass with evasive waypoints and a withdrawal;
the shared flight system hands the completed break to ordinary egress instead
of joining the orbit. Changing the route never resets position or velocity,
and a departing gunship retains cruise altitude rather than replaying a takeoff.
Its controller may move the orbit center from friendly progress and public
mission state. Retasking preserves the remaining station time and the chosen
orbit direction. See Talon Two in `early-operation-nouns.md`.

Prepared ground AA emplacements acquire hostile airborne craft, traverse,
and fire visible aimed rounds or guided missiles. Weapon range, structural
line of fire, cadence and ordinary aircraft armor determine the result. Aimed
rounds use weapon accuracy; a guided missile accelerates and turns toward a
live airborne target within its authored turn rate and fuel lifetime. Damage
requires physical contact along its traveled segment. Losing the target leaves
it coasting until expiry; destroying the launcher does not erase rounds already
in flight. Its sprite and smoke trail follow that same simulated position.
Guidance belongs to dedicated AA mounts, separately from ground direct fire
and point-defense targets. They do not attack
infantry and do not also apply the older abstract turret threat drain. Destroying
the emplacement removes its future fire; a mission must not script a shoot-down
regardless of whether the guns survived.

**Missile anti-air families.** Dedicated Locust, Swarmer and Proximity Charge emplacements are aircraft-only
weapons. They acquire hostile airborne craft through the ordinary anti-air
mount authority, and every released round has one physical flight shared by
collision, effects and sound. They neither engage overhead ships nor add missile
hit points, interception targets or a new point-defence model. Existing mission
batteries, including Ridgewatch, and the ground-artillery Locust retain their
own established behavior; these are additional catalog choices.

Ordinary heavy defense posts may select any of the three missile towers alongside
their gun layouts. Each tower uses one existing post allocation and reserves its
full foundation; selection does not add a second defense budget. Dedicated fort
aspects and authored mission batteries keep their explicitly chosen composition.

The Swarmer releases four missiles at 0.2-second intervals; the Locust releases
thirty at 0.1-second intervals. Both wait five seconds after the last round
before starting another salvo. A committed salvo completes even if its original
target is lost. Each missile accelerates, leads moving craft and turns within
its finite powered flight. It may switch to a closer eligible hostile aircraft
while its previous target still lives. Contact with a hostile aircraft applies
one direct payload, without invented blast damage or friendly collision. Their
orange motor flame and smoke follow the physical body and stop when its flight
ends; losing the launcher does not erase a released missile.

A fighter can react to a real, closing guided missile that is pursuing it. Its
sortie owns a short, bounded warning delay and a brief forward-moving lateral
jink with an opposite correction, flown through the same hull-limited steering as its
ordinary task. The forward direction dominates the lateral and away components,
so a head-on warning does not ask the fighter to reverse and brake away its
speed. The maneuver temporarily replaces ordinary strike advancement, then returns
the unchanged sortie to normal flight. Cover duty's station, rest and chase
clocks keep running, and spent or aborted work still ends. Nose guns may take
an ordinary opportunity if their real firing cone still holds; the break adds
no special firing prohibition. It never moves
the craft twice, changes its durability or supplies a missed-hit roll. A spent,
receding or distant round is no warning. Transports, gunships and ground or
landing procedures keep their existing flight owners. An evidence fixture may
disable the reaction on its own fighters to compare the same attack with and
without evasion. Whether the break buys an escape remains a consequence of
speed, geometry, missile fuel and contact: both bodies' movement is swept over
the same tick so fast crossings cannot masquerade as dodges between samples.
A fighter crossing the map boundary retains its final physical segment until
contacts resolve; only surviving exits complete their departure afterward.

A Proximity Charge is an unguided drifting charge, launched once every two
seconds within a twenty-degree full cone (the atmospheric interpretation is
plus or minus ten degrees). Automatic fire keeps a ten-cell minimum stand-off,
outside its 7.5-cell blast radius, so the mount does not routinely launch into
its own blast envelope; this is a ground-emplacement adaptation rather than a
vanilla minimum range. It leaves quickly and slows during its four-second
flight. The emplacement leads its observed target at launch, accounting for
that slowdown and the target's current horizontal velocity. Traverse and launch
elevation use the same predicted point; once released, the charge never steers.
A turn or speed change can defeat that prediction, and a target beyond the
charge's finite reach supplies no promised interception. Its purple glow and
launch smoke are unpowered presentation: it has no
continuous rocket exhaust. Missile distances use twenty native game units per
cell; guided linear motion is additionally calibrated for the faster atmospheric
fighter handling, with Locust and Swarmer cruise speeds of forty and thirty
cells per second. Guided angular speed is separately calibrated against fighter
handling so a fast missile still has a turn it must physically fly, leaving a
lateral break a physical escape window rather than a missed-hit roll.
The charge retains the unpowered distance/speed conversion,
so its forty-unit hostile proximity fuse is two cells,
its 150-unit blast radius is 7.5 cells and its seventy-five-unit full-damage core
is 3.75 cells. Physical contact with an airborne craft or blocking terrain also detonates the charge. Outside the core,
damage falls to zero at the outer radius. The blast can catch friendly aircraft
and exposed ground bodies, including the launcher: altitude and body extent
must actually bring them inside its volume, while intact roofs and intervening
structure protect ground occupants. Neither fuel expiry nor leaving the map
creates a detonation at an invented endpoint.

These atmospheric mounts, tower proportions, speeds and damage remain an
initial calibration. The damage mapping preserves a qualitative distinction:
Locust fragmentation uses zero penetration and struggles against armor;
Swarmer and Proximity Charge use penetration sixteen and twenty-four as an
explicit initial mapping of their high-explosive identity. Those figures are
mod choices, not a claim that the ground durability model reproduces vanilla
damage-type arithmetic.

An authored fixed AA mount may stand entirely on a terrain summit. Its air
firing line clears the continuous supporting hill from that origin, then
resumes ordinary structural obstruction: a separate ridge or intervening
building still blocks. A missile may likewise clear that launch hill before
entering ordinary flight; leaving it ends that exemption. This narrow mount
contract grants no ground sight, walkability, height bonus or generic hill
traversal. The summit stays blocked after the emplacement is destroyed.

An airframe's `seats()` is its maximum: the pattern's `capacity`, or a fitted
boat's cabin. `ShuttleAssignment.seatsPerSortie`
is the actual manifest and is restored on every cycle. Arrangement, shared
arrival area, squad grouping, and departure behavior belong to the mission's
arrival policy rather than to the hull type.

A **landing berth** is a physical touchdown point; a **landing area** is an
explicit tactical destination. All transports share one logical area by default.
Tagged campaign personnel therefore reunite by squad identity across boats,
berths and later trips without mission-specific setup. Separate squad fragments
require explicitly different landing areas, as on Conquest's independent fronts.
An omitted area must never become a berth-coordinate grouping rule. Generated
militia retain their separate sortie or authored arrival-group identities.


The Aeroshuttle is a purpose-built six-seat half-squad craft. Conquest keeps
the committed campaign hull as its lift source but resolves the visible final
descent to ownership-separated paired Aeroshuttles. Its mission configuration
owns the active zone count, reusable player pairs per zone, and timing jitter.
Those final-descent craft are reusable: committed lift and selected personnel
become additional cycles, not additional permanent landing berths. Seeded
per-craft launch and re-arm offsets prevent lockstep flight without weakening
deterministic fixture replay.

Everywhere else a company boat seats her whole cabin each trip, so a
twelve-seat Hermes lands a squad whole (owner, 2026-10-27): the half-squad
cap belongs to Conquest's paired descent, never to a story mission that
authors the paired policy (`DetachmentResolver.companySeatsPerTrip`). A
mission that arranges heavy lift still flies its machines on the company's own
boat when she can lift them, with the arranged lift behind her for the rest.

### Fighters and drones

Fighters fly the same sortie as anything else the air model puts up. A
`FighterProfile` is an airframe: it says what the aircraft looks like, which
hull sizes and flies it, what it drops, and how much killing it takes on the
ground. How much of it there is comes off the hull and is not authored there.
Everything else about a fighter is its sortie.

A fighter used to be none of that. It lived in a cosmetic overlay with its own
heading integration, its own map-edge entry and cycling re-entry, its own
cluster scan and bank-back state machine, its own tracer and missile fire
resolving straight into the damage service, and its own particle system for all
of it — every one a second, worse implementation of something the air model
owns. Worse in a specific and consistent way: those rounds could not be stopped
by a roof, that aircraft could not be shot down, that damage bypassed cover and
armour, and nothing in the simulation that looks at air could see it. The
overlay is gone; what remains under `battle.flyby` is the roster.

Wing composition, air-to-air, and formation are still future capabilities. What
a wing is today is a commitment: a profile, a side, and a cadence. A
**scheduled** wing is a stock of sorties arriving at set times, and is spent
when they are flown; a mission-generated employer or enemy wing is one.

A **bay** is the other cadence, and the one a player's carrier gives. It is
the carrier-side counterpart of a berth: it holds one airframe on station for
the whole battle, and the state is the bay's, not the aircraft's. The airframe
is on deck, airborne, **refitting** or being **replaced**. One that comes home
from a sortie (its passes, and so its ammunition, spent) rearms for a short
refit and flies again; one that is shot down is replaced after a long respawn.
Nothing ends the cycle: the bay's cover is a renewable strength, as a carrier's
fighters are in space combat, and a loss costs time rather than the bay. How a
sortie ended is read off its mission at the end, because the entity of a craft
that left the map or finished its crash is reaped before anyone can ask it
(`FighterBay`, `AirCoverSystem`). The draft clocks are twenty seconds to refit
and ninety to replace, one pair for every wing (`PlayerFleetWings`); the
force score counts a bay as the two sorties it used to fly.

The player's fitted Talon, Wasp, Broadsword, Thunder, Longbow and Dagger wings
all contribute through the same carrier commitment, one bay each. Each supported
bay keeps its own battle airframe and ordnance, and first launches are staggered
across the bays. Unsupported wings contribute nothing; bomber bays do not grant
an extra air-support pool or bypass commitment.

### Air cover, and where a sortie is from

**Origin is a property of a sortie, not a kind of aircraft.** The same airframe
flies the same runs whether it rolled out of a shed on the map or crossed the
boundary from a carrier overhead. What differs is where it came from, what it
owes itself back to, and therefore how it ends — and nothing between arriving on
station and turning for home differs at all.

There are two origins.

A **berth** is an origin the map owns. The aircraft is borrowed from a
hardstand, and the sortie carries that berth so a completed one parks and a lost
one writes the stand off. `AirStrikeSystem` is the dispatcher.

A **corridor** is an origin off the map: a named source, a point outside the map
the craft enters by, and a point outside the map it leaves by. This is how a
player's committed carrier bays reach the ground battle — friendly air cover,
which flies in, works, and goes home without ever touching the map's own
aviation. `AirCoverSystem` is the dispatcher and the wing's own cadence (a
schedule, or a bay's cycle) decides when.

**A corridor is stated, never discovered.** Both its ends are off the map by
construction, so there is nowhere on the map for an off-map sortie to appear or
vanish. That rule is the whole reason the corridor is a thing rather than a pair
of numbers picked where a craft is spawned: the transport dispatch that fell
back to *the nearest suitable place on the map* put most of its sorties on
hardstands nobody had assigned them, several of them on the same one, and
deleted each craft on arrival. A dispatcher that cannot state a corridor
declines the sortie, exactly as a field with nothing airworthy declines.

The edge is the one nearest the sortie's own side, because that is the direction
its carrier is overhead in and the direction a player reads as *ours*. Along it
the sortie enters abeam what it was sent for, so the run-in is roughly straight
at the objective, and leaves abeam its own force, so it goes home over its own
people rather than across the enemy's.

**Air cover makes passes and leaves; it does not hold station.** It is a strike
sortie, which arrives on station rather than on a cell, flies its runs, and
turns for home when the passes are spent (a bay then refits it and sends it
back) — the same rule and the same code as a
based strike, for the same reason the noun *attack run* exists at all. What ends
it is the passes, or being shot down; there is no recall, because the player's
control over air cover is at commitment time. That is also what makes the origin
legible: the aircraft over the battle came from a named ship in the player's own
fleet, which the player chose to commit before the drop.

A corridor sortie interacts with the runway model not at all. It claims no
strip, takes no berth, and never lands — it has no hardstand on this map to come
home to. A field's strip is one origin's ground procedure, not a requirement of
flying.

### Overhead ships

In play a ship is called, not placed: the Ship on Station command power (see `ship-support.md`) fits a frozen source (a committed member, a stock hull or an employer's offer) and sends it in from the company's edge. Ordinary missions and campaign missions differ only in the source, loadout, cost and writeback they hand that one mechanism. The defending world uses the same call: a defended world (target-profile defence level) hands its side one frozen stock hull, its commander calls it once when the marines threaten ground it must hold, with a warning beat and ground marker ahead, and it holds ground under `DEFEND` (see `ship-support.md`, slice 4).

A ship's one real ground-side opponent is the **fortress battery** (`ground-defences.md`): a multi-cell
structure with ship-grade guns taken from the game's own station modules, held by whichever side holds its
fire-control point. It is the exception to the weak ground-to-ship rule on purpose. Its rounds are ordinary
ship shells that reach a hull through the shield and armour rule and land on hulls only; its reach is a live
**envelope** that a doctrine ship treats as a capital hazard to keep its station out of, and that a ship
gambling inside pays for. Taking the point silences the battery and hands it to the taker; it is written for
sides, never for marines or defenders.

Ships are larger air craft whose decisive ground interaction must be their
actual hull silhouette. An on-map implementation first rejects ground fire
against the hull's broad radius and then resolves it against the rotated
concave authored polygon; a hit belongs to the true contact point, not an
enclosing circle or box. That fidelity is the standing contract, not a claim
that overhead ships are implemented today.

A friendly capital can also be flown by the player through the one direct-control
session (`direct-control-nouns.md`, Warship carrier). The pilot supplies the hull's
travel and lays areas per battery; the ship's flux, ordnance, shield, armour, point
defence and friendly-fire rules stay its own, and on handback the ship returns to its
director, doctrine and station with no pilot order standing.

**A warship on station is not an aircraft that happens to be large.** The shared
density ladder (`AirScale`) puts even a capital at a few tens of cells, small
beside a 560x336 map, so size does not keep a capital off the map. What keeps it
from flying the atmospheric fighter model is that the model is about circles,
sorties and ground procedures, and a capital has none of them. It hovers: it
arrives, holds a station, drifts between stations on a mass-limited translation
and yaw, and leaves. It never lands, never orbits and never flies a run.

**In atmosphere a warship is a lumbering giant, and a fight between ships is a fleet
action (standing, owner, 2026-10-16).** This holds for every warship over a battle, not
for one mission:

- **Its body is large.** A hull is drawn, hit, shielded and fitted at six times the
  shared density (`WarshipScale.HULL_PRESENCE`), within a few percent of its true size
  against the ground, so a frigate is about twenty-five cells long, a destroyer forty to
  fifty, a cruiser about sixty and a capital about a hundred, longer than a compound. Aircraft stay on the shared density: a fighter is a fighter beside
  marines, and it is the ship that is out of the ordinary. Only the body grows against
  the ground: a gun's reach at the ground or the air, a round's speed and a shell's blast
  stay on the shared density, so no weapon hits the ground harder or further.
- **Between hulls, reach is a hull's.** A gun fired at another hull reaches by the
  atmospheric hull reach (`WarshipScale.hullReach`): half the body's scale, so a pulse
  laser reaches about three Wolf lengths and the fight keeps its shape as the body grows. Ships are big and their guns reach a few of
  their own lengths, as their base game's do; a gun's reach at the ground and the air is
  unchanged, because those fights are measured against marines and aircraft, which did
  not grow. A missile at a hull reaches by the same hull reach, from its own range.
- **It handles like a ship of the line.** Its speed and turn are its table row's pulled
  toward a battleship's and then halved (speed) or cut to three fifths (turn)
  (`AtmosphericHandling`): the ladder between hulls survives, so a frigate still outruns
  and outturns a capital, but a frigate crosses a hundred cells in over a minute; and no
  hull gathers or sheds its way in under six seconds, or settles into or out of a turn in
  under four. Every hull carries momentum.
- **How a ship fights falls out of where its guns bear.** A **broadside ship** keeps
  most of its anti-ship firepower on a target laid abeam (a Venture, a Conquest); a **bow
  ship** does not, because its battery bears only ahead (a Wolf (H), a Hound (P), an
  Onslaught, whose heavy guns are forward). Nothing names a hull either way: it is read
  off the mounts every time it is asked, so a refit or a lost gun can change it.
  A broadside ship takes its station on the target's beam, a little ahead along the
  target's course, and lays the target abeam, so it fights on parallel and converging
  lines; between two broadsides it prefers the side with more plate left, so a ship
  whose engaged side has been stripped wears round to fight on its fresh one. A bow ship
  hunts: it takes its station on the bearing round the target where the target's own
  guns bear least, weighed against the flying to get there, and holds its nose on the
  target, so it makes passes and works round to a quiet quarter rather than trading
  broadsides.
- **Hulls fight at a distance.** A gun firing at a hull reaches its base-game range
  times the hull presence, times the reach share, times the **hull range** (two and a
  half): hulls trade fire across several of their own lengths, never nose to nose. A
  round or missile at a hull flies the hull range times as fast, so its flight takes the
  base game's seconds at that distance and point defence has the base game's time to
  answer a missile; a carrier splits at its split range times the hull range. Fighters
  and point defence against craft keep their own close-in reach and clocks.
- **The standoff law.** A duelling ship's range comes from its guns and both hulls'
  size, by one rule for every hull: a ship that outranges its target holds off between
  the two reaches, one that does not closes well inside its own, and neither stands off
  farther than the hull range times three of the two hulls' mean length, nor nearer
  than the two hulls and a clearance. The reach the law reads is the ship's **battery
  reach** — the range at which three fifths of its anti-ship firepower bears — not its
  longest gun, so a ship with one long gun does not hold its main battery out of reach.
  Range advantage matters (the outranged ship has to close under fire) but nobody holds
  off untouchable across the map.
- **Escorts screen by fighting, on a leash.** With no hull to fight, an escort holds its
  place in the screen line abreast of its charge. With one, it takes its own duel
  station by the same laws, pulled in to within its charge's half-length and its own
  reach of the charge, so it brawls with what threatens the charge without leaving it.
- **A ship free to manoeuvre fights with its guns.** Its missiles wait until its guns
  are in reach; a ship held on station keeps launching at the missile's own reach. A
  **finisher** (the base game's role for a Harpoon) is held for a hull already worn to
  three fifths or overloaded, as the base game's own fleets hold it.
- **A hull fight runs on its own clock** (`warship-tactics.md`). A round fired at a hull
  is followed by the next after the weapon's charge-up and charge-down, not at the floor
  of the tick, and that whole cycle, the missile ripple, and on the duel footing flux
  dissipation, shield upkeep and overload run at one over the **hull-fight tempo**. The
  tempo is derived, not tuned: it is the hull presence times the reach share (three),
  and the hull range does not enter it, because a round's speed carries that factor, so
  the rounds in the air and the share of a fight spent crossing are the base game's at
  any hull range. While it fights a hull a ship's flux runs on the duel footing's
  thresholds whatever its posture, so air superiority's sparing flux pair never holds a
  hull fight's guns silent. Damage, armour, shields and hit points are the base game's,
  so every matchup keeps its balance and only takes longer. Ground fire, air fire and point
  defence keep their own clocks, except point defence against a missile aimed at a hull, which
  answers it on the hull fight's clock and at its hull reach (below).
- **A hull lasts three times as long against another hull's guns** (owner, 2026-10-18:
  "things are going down seemingly very fast"; `warship-behaviours.md`, Decided: hull-fight
  durability). A round one warship lands on another meets the shield and the armour exactly
  as the base game's does (damage type, armour reduction off the plate left where it entered,
  glance), and then what the armour lets through is divided by the **hull-fight durability**
  (`WarshipScale.HULL_DURABILITY`, three) on its way to the plate and to the hull alike. One
  factor after the armour rule, so every ratio between hulls and weapons is the base game's;
  only the wear is slower, so a hull is visibly chewed up (plate stripped, sections giving
  way, breaches burning, mounts silenced) before it goes down, and no frigate dies to a
  near-peer's first alpha. Shields and flux are untouched, so the flux war is the base
  game's. Nothing else that hurts a hull is divided: a bomb, a battery, a ground round, an
  orbital strike, a falling wreck. `-Dbattle.warship.hullDurability=1` is the control.
- **A fight between ships goes where the fighting takes it, and is kept off the map's
  edges by a soft pressure** (owner, 2026-10-18). No mission declares an area for it: the
  fight may drift over the ground battle as readily as over open ground. Every hull frigate-
  sized and up that its own helm is flying against hulls (on its duel helm or a manoeuvre),
  in every mission, wants a band of clear air between its hull and each edge, its **edge
  margin**: the larger of its tightest turning circle and one and a half of its own lengths,
  never more than a fifth of the map's shorter side, so a small map keeps a middle. The
  duel, bow, manoeuvre and interposing-escort stations it chooses are kept out of the band, and its
  course is pressed away from an edge as it nears one (`WarshipEdgeHelm`): the pressure
  starts at the margin and grows to the edge, reads where the hull will be once it has
  stopped closing the edge at its own acceleration (so a heavy hull coming in fast turns
  away early), sheds the way toward the edge before pushing back in (so a ship running
  along the band keeps its way along it), and comes after keeping clear of other hulls,
  since an edge cannot be shouldered aside as a hull can; a manoeuvre's bow comes round
  onto the course it leaves. A centre that crosses an edge anyway is held on it, for every
  flown hull whether or not the pressure applies to it, and a hull leaving across its edge
  is held off the other three: only a side's own edge is a way on or off. A ship
  holding a station someone chose (a mission's, a called ship's, a remote order's, an
  escort's place in the screen line) keeps it: those are places, not a fight. Exempt too:
  a hull deliberately crossing its side's edge (flying in until it has cleared its margin,
  or leaving), and everything placed rather than flown (a route or lift run, a low pass or
  low hover, a berthed hull, a pilot's helm, every craft of fighter size).
  `-Dbattle.warship.edgePressure=false` is the control.
- **An escort screens beside its charge.** Fighting a hull, an escort interposes: it
  stands in line between its charge and the threat, clear of the charge however either
  turns (both hulls' half-diagonals and the screen's gap), its fellow escorts spaced square
  to the threat, so a screen and the ship it screens meet the enemy together, the screen
  first. The screen is physical: a round meets the first hull on its way, so fire laid on
  the charge through its escort is taken on the escort's shield or plate. A screen changing sides comes round its charge on that radius rather than
  across its bow or along its side.
- **Warships are bodies and do not pass through one another** (`warship-tactics.md`).
  Every pair of hulls frigate-sized and up, friend or foe, is in contact on its real
  footprint; fighters, bombers, drones and boats fly over a capital freely, and falling
  wrecks, hulls in the cloud deck, berthed or in a low hover are out of contact. Contact
  is soft: overlapping hulls are eased apart over seconds along the line between them,
  the smaller giving more way, momentum kept, with a positional correction only for a
  deep overlap. A hull at rest on a mission station gives no way, so two a mission lays
  over each other stay where it put them. Nothing is damaged: ramming is not modelled.
  Contact is the backstop; the behaviour is **avoidance**: a ship fighting hulls,
  escorting or leaving bends its course (and, on a manoeuvre, its bow) away from any
  hull it would close inside the screen line's gap, looking as far ahead as its handling
  needs, and keeps its station clear of friends. An escort's screen keeps a narrower gap
  within itself and carries its charge's way, but an escort gives its own charge the whole
  gap: the charge flies its fight and the screen makes way.
  `-Dbattle.warship.escortClearance=false` is the control (the narrower gap toward the
  charge, a station just off its side, and a straight course across it).
- **A hull fight is flown in manoeuvres, decided for the group** (`warship-tactics.md`).
  Once a second each side's hunters (ships fighting hulls with their duel helm, not
  escorts) are given a **role** and a target: against a charge with escorts one hunter
  **flanks** round to the charge while the others **pin** the escorts; otherwise each
  takes the hull cheapest for it, with a penalty for a hull a friend already holds, so
  two ships share a hull only by decision (the last hull, or one worn under a third), and
  then from opposite sides; broadside ships on one target form a **line** (a lead and
  seconds in its wake). Each ship then flies a **manoeuvre**, an intent held for tens of
  seconds with its own end conditions: approach on an oblique course, an attack run
  (bow ships) or a broadside run (broadside ships) at the standoff, extend away, come
  about through a fixed side to unmask fresh guns, flank, line ahead. **A front-shielded
  ship never turns its back in reach while its shield holds**: in a pack or a line its
  run gives way to a **standoff**, a drift astern and to one side round its target with
  nose, guns and shield still on it, out past the target's reach only while it vents; an
  omni-shielded or shieldless ship comes about instead. An attacker gains by getting
  outside a front shield's arc (a hunter's station prefers it, and a flanker in a pack
  finds it); the defender's nose follows its attacker within its slow turn. A run is held
  on while its target is near its flux limit. On a manoeuvre a ship travels along its
  nose, so range opens and closes as a ship's course does rather than a hover holding a
  ring.
- **A ship set upon by several hulls answers the one that threatens it** (`warship-tactics.md`,
  slice 7). A ship chooses its hull by range over its reach, keeping the one it has. When two
  or more hostile hulls are fighting it and the one it has stops being a threat (less than half
  of its battery laid on the chooser, or out of its own reach), it chooses again with threat
  weighed: a hull is the cheaper the more of its battery it has laid on the chooser from inside
  its reach (a hull closing from just outside it counts for up to half), and the less the
  chooser must turn to answer it, and the one it had keeps only half its keeping bonus. A hull
  that turns its guns away, or opens the range, lets the chooser's attention go to the one in
  front of it. A ship in an even fight never drops the hull it is wearing down for another.
  `-Dbattle.warship.threatTargeting=false` is the control.
- **A ship choosing between hulls goes for the one its guns can hurt.** With two or more hulls
  to choose from, a hull costs the chooser more the more of its anti-ship gun battery that
  hull's armour turns away by the armour rule (up to two reaches of range for a hull its whole
  battery glances off). A cruiser whose autocannons bounce off a destroyer's plate turns on the
  frigate beside it, and keeps its pulse laser on whatever it can. One hull alone is fought
  whatever it is. `-Dbattle.warship.biteTargeting=false` is the control.
- **Two or more bow ships on one hull fight it as a relay** (`warship-tactics.md`, slice 7).
  One is the **tank**: it holds the target's attention from nearest and nose-on, on the
  target's battery side, its shield on the target, and fires only while the hard flux its
  shield holds stays under half its pool (its own guns' flux is soft and vents behind the
  shield, so it does not count against the tank). The others are **strikers**: they work the target's quarter, round
  from the tank and off its shield arc where they can, at full fire, since nothing is
  shooting at them. The ship with the most flux to spare holds the target, unless the pack
  has a **decidedly sturdier** hull (half again what each partner can stand, read off its
  remaining hull, its armour and the damage its shield's pool can absorb): that hull is the
  tank, a lighter partner relieves it only when its shield is going, and it takes the target
  back from a lighter tank as soon as it is fresh again. A destroyer and a frigate on one
  target fight as anvil and hammer; a worn destroyer can fall below a fresh frigate, and two
  of a kind relay on flux alone. When the tank's
  pool runs high and a partner inside its reach is fresh, they **swap**: the partner closes to
  catch the target's eye while the old tank backs off nose-on, holding its fire; once the
  partner is nearer and has its battery on, the old tank turns away (this is the one place a
  front-shielded ship turns its stern in reach, under its partner's cover), opens past the
  target's reach, lets its shield drop and **vents**, and comes back as a striker. It vents
  inside its own edge margin: it runs out on the course with the most room toward its hold
  (up to a right angle off straight away, the nearer course on a tie), and where the map has
  no room for the full distance it holds short of it, nose back toward the fight, rather
  than pinning itself against the edge. A tank
  whose shield is going with nobody fresh breaks off all the same; a striker running high
  vents while the tank holds. Catching and shedding the target's attention is nothing but
  the target's own choice of whom to fight, above. A line of broadside ships keeps its line,
  an escorted charge keeps its flank and pin, and a one-on-one is a duel.
  `-Dbattle.warship.packRelay=false` is the control.
- **One ship against one is a duel, flown for position** (`warship-tactics.md`, slice 6).
  At hull range a defender can always pivot faster than an equal can circle it, so
  nobody is got round by speed alone: position is won by choices, read off the two
  hulls. A bow ship comes in for its target's quiet quarter on its own course rather
  than at its nose. Between runs a front-shielded ship **orbits**: it circles under way,
  its target held off the bow at the widest angle its shield still covers and its side
  guns still bear on, so two ships jockey round each other with shields on; a hull whose
  battery is nearly all in its bow (an Eagle) keeps its nose on in a standoff instead.
  A ship **crosses the T** when its main battery bears and its target's does not: it
  turns in first from an orbit when it is ahead on flux and hull, and on any target that
  has turned its cover or its battery away it holds its run (a **cross**). The **counter**
  is the defender's: turned in on, it turns in to answer, within its own turn rate. Two
  ships equally full hold their shields on each other and the flux race decides; the one
  whose shield is going (overloaded, or nearly full and losing the race) **breaks**:
  it turns well off its target and opens at full way, shield or no, comes about the short
  way once vented or run down, and its quarter is what the other crosses. A shieldless
  bow ship makes passes (run, extend, come about) and comes back early, the short way,
  to answer or to cross. An overloaded ship is still in the fight it is flying. Which ship
  wins the position is the fight's own doing; faster and handier hulls turn in and come
  about sooner. `-Dbattle.warship.duelManoeuvres=false` is the control. An escort fighting hulls **interposes** between its charge
  and the threat. A circuit a mission flies for one of its hulls is a manoeuvre of the
  same kind, owned by the mission.

What the ground-support manoeuvres (a gun run, close air support, a low pass, a shelling)
take from this is described with each: they inherit the slower handling through the
hull's own figures, and none of them is turned into a broadside.

**It is a superweapon, used rarely, and it is the map's dominant fact while it
is there.** Its weapons are authored *ground effects* resolved by the simulation,
not vanilla projectiles bridged in, because the vanilla combat model cannot
express area effect against infantry (`vanilla-combat-bridge-nouns.md`, law 4).
Two classes carry the design:

- A **sweep** is a beam whose ground end is walked across an arc over seconds.
  Each tick the swept segment damages everything in its band — units, cover,
  walls, roofs, whole buildings — through the ordinary detonation and map-edit
  paths at far larger magnitude. A sweep is slow enough to read and to run from;
  that is its counterplay, and dispersal is the answer to it.
- A **barrage** is a telegraphed footprint with a real fall time, the bomb
  class of `AirOrdnance` scaled up.

Friendly fire is unconditional, as it is for every detonation: a ship's weapons
do not check colours. The cost of calling one in over your own marines is the
point.

**Its guns are the game's, placed by the hull's own data.** A warship is fitted
from a vanilla variant, or from a campaign fleet member's own fitted variant
(both reach the same fitting through one loadout seam: a hull and a weapon per
slot): the hull spec says where each slot is, which way it
faces and how far it traverses; the variant says what sits in it; the weapon,
projectile and weapon-table data say what a round does, how far and fast it
goes and what it looks like. The mod authors none of those numbers or sprites.
One conversion (`WarshipScale`, the shared density) turns the
game's units into cells, so a gun's reach and a shell's blast keep the
proportion the player knows from the ship; the hull's body is that density times
its atmospheric presence (above), so where a slot sits is the hull's own data at the
hull's drawn size. A round is an ordinary detonation
that the ship carries from the muzzle to the ground over the flight time its own
speed implies, so one clock serves what is drawn crossing the distance and what
the ground suffers on arrival, and a heavy round lands as the existing heavy
blast; point-defence-only
weapons never fire at ground (see below). A weapon fires at its tightest cone, because
the wider figure is only what sustained fire builds up.

**A warship brought into a battle already under way arrives like a ship.** It comes
down through the cloud deck at a distance, never on top of what it came for: a called
ship breaks out on its own side of its station, a glide and a whole hull length short
of it, or over its own map edge when the station is nearer the edge than that, and a
mission's ship comes down where the mission puts it. It comes down already on its
**entry heading**, which the mission names or which defaults to facing its station or
first objective, so it never has to come about after appearing; the descent's
presentation follows that heading. While it is still coming down it fires nothing and
takes no duel station. Then it closes under its own handling, so the approach is part
of the fight.

**A mission may give each side an approved map edge, and then nothing of that side warps in
or out** (owner, 2026-10-18). Its ships come on by flying in across the edge: the hull is
placed just off the map, already on its heading and under way at its top speed, with a
station ahead well inside its margin, and is seen crossing the edge; it can fight as it
comes, and the edge pressure leaves it alone until it has cleared its margin. Every way one
of its ships leaves (a mission's withdrawal, a visit's end, spent station time, a doctrine's
withdrawal, an escort following its lead out) flies straight out across the same edge,
nose first and in level flight, and the hull is gone only once its own propulsion has
carried it off the map; until then it can be hit, answers a hull in reach, and its escorts
keep it as their lead. A hull already berthed on the map still lifts from its pad. The seam
is general (`WarshipSystem.assignEdge`, `enterAcrossEdge`, `leaveAcrossEdge`); a side with
no edge arrives and leaves as below.

**A ship enters and leaves through the cloud deck, and that is presentation only.**
A ship brought in from the map edge starts at the top of the deck and descends to its
hover, and one that departs climbs back; everything seen of that (the shadow lengthening
and softening with height, the haze and cloud on the hull, the re-entry glow, the dust
and shock ring on the ground, the exhaust wake of a climb) is read from one lift value
that is a function of the ship's own entry and departure clocks, so a frame is
deterministic from ship state and tick and the simulation never reads it. A ship that did
not enter this way hovers as it always did. The ground feels it only as light and dust;
nothing about the hull's hit shape, guns or collision changes with the descent. The
planet's surface palette may tint the glow, cloud and dust.

A ship is heard in the game's own sounds, never a mod clip: the weapon's fire sound and its blast's sound come from the same specs, the simulation only records the requests, and the presentation plays them positionally. A burst is thinned to a report every fraction of a second rather than a cue per round, and a lance sounds once when it lights, not while it telegraphs.

**A hull must bring its guns to bear, and a broadside cannot shoot dead ahead.**
A turret traverses within its slot's arc and a hardpoint fires where the nose
points, so which guns reach a target is a fact about the ship's heading. The
Onslaught's heavy turrets are port and starboard; ahead of it only its
hardpoints and bow guns answer. A barrel never leaves its slot's arc: a bearing
beyond it is met at the nearer stop, and a swing between stops goes round
inside the arc, never through the hull. **A turret stays laid on the hull it
fights.** Between rounds (cooling down, venting, waiting on the volley or a
missile's window, holding for a friend in its line) it keeps tracking where it
would aim at the hull it reaches and bears on, and rests along its slot only
when no hull is in its reach and arc. A barrel that swung home for every
cooldown spent most of a hull fight re-laying: on the hull fight's long clock a
slow heavy gun drifted twenty degrees off and took seconds to come back, and was
on its target a fifth of the time. Ground orders keep their own aim; the hull's
lay only replaces rest. `-Dbattle.warship.layBetweenRounds=false` is the control. Choosing a heading that brings the battery to
bear is the director's job and part of what makes a capital worth positioning.

**A warship takes orders, and an area order leaves the choosing to the ship.**
The mission's voice is a value: hold fire, or fire on an area. An area order
names ground and a radius, and every mount picks, at random among the hostile
units inside it that it can reach, what to fire on, so a battery spreads over
the ground it was given. Nothing outside the circle is fired on, though a round
that lands inside it hurts whoever stands there. The order may also ask the ship
to bring its guns to bear: it then turns, once, to the heading that puts the most
firepower on the area, preferring the smaller turn when two are nearly as good.
The hull turns at its atmospheric turn rate and acceleration and brakes onto the
heading, leading a heading that is still moving so a turning ship settles on it; a turret swings at its weapon's own traverse rate and fires only when
it has come onto its bearing, and a fixed mount points where the hull does. A
choice is kept until it has been fired on. Eventually these orders belong to
every such unit, not just to cinematics.

**A warship hovers to a station at its hull's own speed, and a director can choose
its ground.** The ship translates in any direction regardless of heading,
accelerating, cruising and braking onto its station at its atmospheric speed and
acceleration; a ship that has moved far re-chooses the
heading that brings its guns to bear. A director, given only an area size,
issues ordinary area orders itself: the densest hostile concentration within
the ship's longest reach that no friendly ship's area already covers, chosen
again once that ground holds nobody. Ground beyond every gun is never chosen.

**A directed ship holds several areas at once, so the fire is a stream.** A
director keeps up to a limit of areas (three unless told otherwise): the densest
free concentrations in reach, none overlapping another (centres at least a
radius apart) and none already covered by a friendly ship. An area is dropped
once it has held no hostile for a couple of seconds and replaced at once from
the remaining ground. The area-fire mounts are dealt the areas in rotation, the
densest twice per round, skipping any a mount cannot bear on, and a mount whose
area is dropped chooses again; a beam mount sweeps the area it is dealt. When
bringing guns to bear, the heading is the one that maximises firepower summed
over all areas, each weighted by the hostiles it held. A manual order replaces
every area the director held and stands alone until its ground is empty, after
which the director resumes; the ship's single standing order reads as its first
(densest) area, and spotting lights every area it holds.

**A shell's blast wears the weapon's own colour, and hurts as far as its own footprint.**
A heavy landing carries the round's explosion colour to the presentation, which
tints the fire, the flash and the ground light with it. Two radii are kept apart
because the game's data keeps them apart: a projectile's `explosionRadius` is the
*visual* fireball ("purely visual", in the spec's own words) and sizes the drawn
burst, while `explosionSpec.radius` (with `coreRadius`, the full-damage centre) is
the damage footprint. Damage, wall breach and roof loss use the footprint; a round
that declares none falls back to its visual size, since the ground battle needs
some area and that is the only one the spec gives. The footprint is one flat
radius today: the ground detonation has no falloff, so the spec's outer radius
applies at full damage and the core radius is carried but not yet used. A Reaper's
fireball is about sixteen cells across and its blast about eight; the first
version of the model blasted the fireball, which was a defect and not a decision.

**A scripted flyby is a route the ship passes through without stopping.** Given
waypoints (and optionally told to loop), the hull flies each leg at speed,
counting a waypoint as passed within a few cells and braking only onto a last
one that does not loop. With no instruction to bring guns to bear it points
along its travel; whatever ground it is ordered or directed to fire on, it keeps
firing while it moves, so a cinematic can be authored as a path.

**A friendly warship is a spotter.** Its hull, the ground it is ordered onto and
the places its rounds and lance tips land are projected into fog as temporary
observation sources, so the player sees what it destroys; a hostile ship reveals
nothing to the player. This is presentation only and changes nothing it can fire at.

**A warship burns the engines its hull lists and casts a shadow from high up.**
Each nozzle the hull spec places (position, flame direction, length, width and
engine style, in the same hull frame as its gun slots) burns in a muted,
heat-whitened take on the game's engine colour for that style, and how hard it
burns is read back off the motion the simulation gave the ship: a low glow
holding the hull up, barely more at cruise, a modest flare while it accelerates
or comes about (aft nozzles hardest when pushed ahead, side nozzles when pushed
across, the nozzles on the outside of a turn harder than those inside it),
dimmed while braking onto a station. **In atmosphere the exhaust is hot air, not
a torch (standing, owner, 2026-10-16):** a flame is the nozzle's authored length
at the shared density, never grown by the hull's presence, capped at about a
third of the hull's own length, with a faint haze of worked air astern of it, so
a cruising ship of the line trails a few cells of glow rather than a burn longer
than itself. Only the climb out through the cloud deck and a low pass's wake are
louder, and even those are a hull or two of hot air astern; the plasma streak of
a re-entry is the air burning, not exhaust. Nothing about this feeds the
simulation. The ship's shadow is its own hull sprite laid on the
ground down-sun by the sun's reach at a presentational hover height, higher than
an aircraft's, through the same shadow layer, sun and tint every craft uses. Like
an aircraft's, that height is a drawing choice and not a physical altitude.

**A round between hulls is drawn at the hulls' scale, as its spec says the game draws it**
(`VanillaRoundLook`). The projectile spec decides the kind: a `BALLISTIC` shell is its bullet
sprite stretched to the spec's length and width along its flight; a `BALLISTIC_AS_BEAM` bolt is
a tapered streak of light in its fringe and core colours, even when the spec also names a bullet
sprite (the Antimatter Blaster does, and the game never draws it); a missile is its sprite at its
size, its engine flame at its nozzle while the motor burns, and its contrail one continuous
ribbon of the spec's width, spread and colour, laid only while the motor burns and drifting on
at the spec's share of the missile's speed. A round fired at a hull, or carrying on past one, is
drawn at the hull presence, so it stands to the ship it hits as in the base game (a Heavy
Autocannon shell about a quarter of an Enforcer, not a speck under a glow), and its contrail
hangs on the hull fight's clock. The glow under a round is the spec's own (`glowColor` at
`glowRadius`); a round whose spec names none gets a soft halo no longer than itself. A round at
the ground or the air keeps the shared density, slimmed, because there it is measured against
marines. This is drawing only: a round's reach, speed and blast are unchanged.

**A lance is a beam weapon strong enough to cut ground, and under an area order
it sweeps.** A beam mount of at least 500 damage per second, such as a Paragon's
Tachyon Lance, lays one committed sweep across the ordered area as that mount
sees it, walked at the weapon's own traverse rate, kept inside its slot's arc and
drawn in the weapon's own colours. Weaker beams (the Graviton Beam, the Tactical
Laser) are anti-ship weapons and stay silent against ground for now.

**A warship's point defence is its own and ignores the order.** The flak,
vulcans and PD lasers a variant mounts are independent of hold fire and area
fire. Each mount takes the nearest hostile round in flight inside its reach and
arc, whichever way the round is headed: the rounds the ground battle already
tracks that opt in to interception (the same opt-in the carried interceptor pods
read) and the shells of hostile warships. The umbrella is the mount's reach, so
a ship covers itself and the ground beneath its guns without being told what to
protect; it never engages friendly rounds. The barrel swings at the weapon's own
traverse rate and fires once on its bearing. An engagement is the weapon's burst,
no faster than about three a second, and the round dies if any shot hits. A shot's
chance is derived from the weapon, not authored per gun: a beam is hitscan, a
projectile has a lower base; both fall to half at full reach; a projectile loses
accuracy against ordnance that crosses fast relative to its own muzzle speed, and
to its own cone. A miss still spends the engagement, so a heavy salvo saturates
the mounts and the surplus lands. Interception is not damage: a destroyed round
never lands. Tracers and kill flashes use the weapon's own colours.

**Ownership is the mission's, not the ship's.** The same entity serves the
company's own flagship, an allied or neutral hull, and an enemy's. Who may call
it, what it is aimed at and why it is present are decided by the mission or the
command power that committed it (`command-powers-nouns.md`); the entity carries
a faction and no allegiance logic of its own. What it fires at is chosen by a
director from the densest concentration of opposing force not already being
worked, the same rule a fighter strike uses.

**A smaller combat ship may escort a larger one.** An escort is a ship with a lead-ship relation (named by a mission or script, or picked up automatically as its side's flagship when that option is on): it keeps station in a screen line abreast the lead, parallel to the lead's course on the beam its threats come from, and holds the lead's heading, its threat picture counts a strike sortie aimed at the lead at twice an ordinary armed craft, and its anti-air fire takes those craft first. It is a posture-layer role, so it changes where the ship sits and what it favours and never how hard anything hits; when the lead is lost or leaves, the relation ends and the escort returns to its own station and posture. See `warship-behaviours.md`.

**A ship may make a low pass: a fast, low run along a line, guns on the corridor, at the cost of station time.** The order (the pilot's, a script's or the debug panel's; see `ship-support.md`, slice 5) flies the hull at a burn of its own table speed with no station kept, fires on hostiles in a corridor sliding down the line through the ordinary hand-order fire machinery (so friendly fire stays on and nothing is refused or multiplied), charges twice its flying time against the stay, and gives the ship back to its station, route or pilot; its shadow lies closer and sharper, its wake is louder and it leaves dust along the line, all presentation read from the pass's own clocks.

**Balance law (standing).** Ship against ship in atmosphere keeps the base game's balance, with numbers and hints taken from vanilla data (shield arc and efficiency, armour, flux) rather than authored tables. Ground against ship is weaker, at least until planetary defences are built. Ship against ground is stronger: in-universe these are essentially superweapons, and ships are overpowered against the ground by design. Class and posture weights are perception inputs and never alter damage either way.

**A planetary shield dome takes hostile ship-grade ordnance as flux, and nothing else.** A shield generator (a multi-cell structure, `ground-defences.md`) projects a dome of fixed radius with its own flux pool and dissipation. A warship round, warhead or lance beam, or an orbital barrage, from a side hostile to the generator's holder and landing inside the dome, is absorbed instead of detonating (`PendingDetonation.isShipGrade`; the detonation pipeline asks the dome first, so splash, wall damage and the ship's own landing FX never happen). The round that fills the pool is absorbed too; the dome then drops for its overload time, during which ordnance lands as if it were not there, and recovers empty. It stops incoming fire only: the holder's own fire, people, small arms, mech weapons, rockets and aircraft (and aircraft ordnance) pass. Either side may hold a generator; whoever holds it owns the dome, a side with combatants standing by it and none of the holder's takes it (the dome reboots), a taken generator changes sides with its holder (`ground-defences.md`, *Captured structures*), and destroying it drops the dome for good. Concentrated fire opens the sky and spaced fire never does, which is how a defended world resists ship-vs-ground superweapons without a damage nerf.

**A warship is hittable, but only by ordnance.** It carries its hull table's own
hit points and armour (the Onslaught's 20000 and 1750). Any hostile detonation
whose blast reaches the hull's footprint is a hit, through one armour rule: a
hit's strength is its damage times its penetration, the share that gets through
is strength over armour plus strength, and a hit weaker than a tenth of the armour
glances off and does nothing. A rifle round is nothing to it however many fire; a
mortar or orbital strike chips it; only anti-ship-grade ordnance in numbers brings
it down. Its own side never hurts it. A hull taken to nothing bursts in a heavy
blast and every gun, shield and point-defence mount dies with it, but it does not
leave the battle: it **falls**. It keeps the momentum it had (a hovering hull slides
off as its lift fails), turns as it goes, trails fire, and strikes the ground a few
seconds later as the crash a downed aircraft makes, hurting whoever is under it on
either side, then skids to rest and lies there as wreckage for the rest of the battle.
A falling hull is coming down to the ground, so it eases back to its true landed
size as it falls (the lift run read backwards, `LandedScale.fallFactor`) and strikes,
skids and lies at that size: its crush is half its landed length, its skid half its
beam, with no aircraft cap, because a hull is drawn at its true size on the ground
and an airframe is not. The fall is a pure function of the hull's own state, so it
lands the same way every play (`WarshipWreck`). The hit comes from the detonation pipeline itself, so any source
that already lands a detonation is a counter without a ship-specific shortcut.

**A hull's armour is a grid, and a hit has a place on it** (`warship-hull-damage.md`). The
**plating** is the base game's armour grid read off the hull's own sprite box and collision
polygon: cells of about fifteen hull pixels, the outer two carrying **plate** (a fifteenth of
the rating each), the deeper ones the **core**, every cell an equal share of the hull's hit
points as **structure**. A round enters from the side it came from (a blast with no source,
from the side nearest where it landed), and the shield is asked of that side. The armour rule
reads the plate left in the base game's block about the entry cell, never under 5% of the
rating; the plate soaks what gets through and the hull takes only what it cannot. A cell spent
of plate and structure is **breached** and a round's line passes through it, but an outer
**section** (bow, port, starboard, stern) gives way only once the hull has lost a quarter of
its hit points through it, and the core is holed only from an open neighbour: a side is
stripped, then opens, and then the core takes rounds nearly whole. Hull hit points stay the
one authority on when the ship dies. The hull is drawn from the plating, the game's own sprite
cut on the grid as a broken aircraft is: stripped plate darkens and takes the base game's burn,
crack and hole decals, a breached cell shows the ship's insides (torn frames under the plating,
gutted compartments in the core, tinted to the builder's metal) rimmed with torn plate, never the
ground, which only a hull breaking apart shows, a fresh breach glows and an open one burns, and a round's blast is
drawn where it met the hull or its shield. A wreck keeps its cut. A mount stands on the cell
under its slot: when that cell is first breached the mount is silenced for the base game's repair
time (scaled by the hull-fight clock) and comes back; once repaired, a second hit on the holed plate
under it loses it for the rest of the battle. A hull's heading choice counts only the guns it has in
action. A **hardpoint** sights, and measures its reach, from its mount pulled in toward the hull's
origin by the hull reach share: hulls are drawn larger against their guns' reach than in the base
game, and from the mount itself a fixed gun out toward a beam would never bear on the target the
nose is laid on. `-Dbattle.warship.platingGrid=false`
restores the flat rule and the old whole-hull darkening.

**A hit reads as what it met** (`WarshipHitFx`). Every round a hull fight lands leaves a
**hit mark** beside the ships: presentation state, made after damage and flux are decided,
read by nothing in the simulation and drawing nothing from its random stream. A mark is held
in the struck hull's own frame, so it rides with a ship coming about; it carries the round's
base-game damage type, its weapon's colours and a size that is the weapon's own hit glow at the
hull's drawn scale, so a hit is as large against the ship as in the base game and reads from the
duel camera. A round a shield absorbs marks the point on the bubble toward its source: light on
the shield (a cold flare, a ripple running along the rim, sparks skipping off at the angle of
reflection), never fire, and never the ground blast a heavy round brings to metal. A round that
reaches the plate is fire and matter: a flash, sparks thrown back, chips of the hull's own metal,
smoke and a cooling scorch. Kinetic flares hardest on a shield and throws the longest sparks;
high explosive is a fireball on metal and a dull splash on a shield; energy sears and leaves the
plate molten; fragmentation sprays. A round that holes a cell marks a **breach** (a second blast
and a shockwave), a mount lost with its plate goes up, and the shield's own moments are marks
too: it **shatters** when an overload takes it and **gutters out** when an automatic shield locks
itself down at its drop level. Between hits a shield shows its **strain**, warming from blue to
violet and shivering and crackling as the pool fills past half, an overloaded ship crawls with
arcs, an open breach vents vapour and fire from the rim of each hole, and a holed ship under way
trails smoke left in the world behind it. An omni shield narrower than all round is drawn turned on
its hull target, as the base game's tracks the threat; what it absorbs is still the model's every
bearing.

**A hostile hull is a target for a ship's guns, in any mission.** Warships of
opposing sides overhead fight each other by default, in an ordinary or generated
mission as much as a story chapter. A ship's **ship-fire order** (`ShipFireOrder`)
is `AUTO` unless something says otherwise: it fights any hostile hull once no
**hull-fire hold** is pending, provided it carries a weapon that may fire at a
ship (a gunless tender or rig is a target, never a duellist). `ALL` is an
explicit order from a mission or script that no hold delays; `OFF` keeps a ship
off hulls. A ship fights hostile hulls with the same
mounts, cooldowns, magazines and flux as any other fire, as a third customer beside
ground and air fire. Which guns may fire at a ship follows the base game: point
defence never does, a lance keeps its ground sweep, a standoff missile keeps its
ground payload; everything else may. A round is aimed where the hull will be,
thrown off by the weapon's cone as a hash of who fired what, and flies as an ordinary
shell. **It meets the first hull on its way, whoever's it is** (`WarshipRoundSweep`): each
tick its travel is a segment swept against every hull's footprint, the true-scale
rectangle soft contact keeps hulls apart by, and it stops at the first it crosses. A
raised shield is met first, on its bubble, where the round crosses it from outside inside
the arc; a round entering the bubble through the open part of the arc goes on to the plate,
and the plate cell it meets is the one its line enters. A round that reaches its aim point
having met nothing lands on any hull its blast reaches there. Either way it meets the shield
and the armour by the base game's damage types (kinetic doubled on shields and halved on
armour, high explosive the reverse, fragmentation quartered on both, energy as it is) at the
base game's own armour reduction rather than the ground's penetration scale. The hulls a
round can meet are the hulls in contact: fighters, drones and boats, falling wrecks, hulls in
the cloud deck, berthed, lifting off or in a low hover are flown over, as other hulls fly over
them, and the ship that fired is never met by its own rounds. A guided or unguided missile
meets hulls the same way; a multi-warhead carrier flies to its split unmet and its warheads,
which strike with their own type, meet whatever they cross from there; a beam meets the first
hull along its line at once. So an escort standing in the line of fire takes what was meant
for its charge, a hostile hull between a ship and its target shields the target, and a ship's
rounds can hit a friend. A round that meets nothing carries on along its line, still able to
meet a hull it crosses, and comes down on the ground as an ordinary detonation, so stray fire
between ships is real below them and among them. Rounds between hulls are direct fire and no
point defence engages them. Fire at the ground and at aircraft, and a fortress battery's fire
up at a hull, falls past or rises past other hulls without meeting them: those rounds are
travelling between heights, not across the fight. `-Dbattle.warship.inFlightCollision=false`
is the control (rounds resolve only where they land, on hostile hulls).

**A mount does not fire through a friend.** Before it starts on a hull and before each round,
a mount reads its line from the muzzle to its aim point; a friendly hull it can meet lying
across that line short of the target (its footprint grown by a cell, for the cone) fouls it.
A fouled mount takes another hull in its reach with a clear line, or, engaged, holds its round
until the friend has passed and gives the engagement up after an unaligned barrel's patience.
A hostile hull in the line never fouls it. The check reads only the line to the aim point: a
round that misses and carries on can still find a friend beyond its target. Nothing about the
helm changes for it, so what it does to the formations is a matter of where they already put
their hulls. A line of broadside ships lays its guns abeam, clear of its own lead and seconds,
and the check touches only a chase gun laid along the line. In a pack relay the striker works
the target's quarter round from the tank, so its line seldom crosses the tank, and when it
does those mounts wait for the striker's own movement to open it. A charge's guns that bear
through its own interposed screen are the ones that fall quiet: the screen is across the very
line it guards, which is the price of being screened. `-Dbattle.warship.clearLineFire=false`
is the control. A ship with a hull to fight and no ground to bring its guns to bear on turns to
the heading the broadside or bow rule gives (below), re-chosen as it moves. A
vanilla skin (a Wolf (H), a Hound (P)) is fitted as its base hull under its own
sprite. Why it is shaped this way: `warship-behaviours.md`, Decided: ship against ship.

**A ship fighting a hull manoeuvres by the standoff law.** Its **duel helm** moves the
station it flies to, never its speed or turn. The range is the standoff law's (above).
A broadside ship's station is on the target's beam at that range, on the side it is
already on, and a little ahead along the target's course; a bow ship's is the target's
quietest quarter at that range. The heading follows the same split. A broadside ship
takes, of the headings that keep most of the anti-ship firepower it can bring on the
target, the one that keeps the target most nearly abeam with the least turn, preferring
the broadside with more plate left; a bow ship takes, of the headings that keep nearly
all of it, the one with the most and the least turn. A gun counts only if it still bears
a couple of degrees either side, laid from where its mount sits rather than the hull's
centre, and never if it was lost with its plating. A ship being piloted,
departing, passing low or under a remote order is not steered by it, and the
mission station returns when no hull is left to fight. While a ship's fire order
allows hull fire, its variant's flux capacitors and vents count as the base game
counts them; otherwise the hull table's flux stands, as ground bombardment was tuned
against it.

**A missile at a hull is ordnance, not a gun.** It flies its own path to where the hull
will be after the missile's own flight, and the target's point defence may shoot it, as
it may any missile; a guided one launches inside its slot's arc widened by how far it
turns after launch. A multi-warhead carrier splits at its spec's range short of the hull,
and its warheads are aimed at the split, at the hull as it is then, spread across the
spec's arc, so the hull's size decides how many strike; each warhead that reaches no hull
carries on to the ground. Missiles never set a ship's heading or its duel reach: those
are its guns'. A ship steered by its duel helm holds its missiles until the target is
within its guns' reach, so it does not spend its racks closing; a ship held on its
station launches at the missile's own reach. A hull's tubes **ripple**: no two tubes on
one hull start a volley within three quarters of a second of each other, and no tube
launches the next round of its volley sooner than a quarter second, so a pair of
launchers fires left, right, left. A missile's own damage, flight and warheads are its
weapon's.

**A missile at a hull is a resource, launched into a window** (`warship-tactics.md`, missile
behaviour). Each launcher has a **role**, read off the weapon table's own role column and the
spec, never named per weapon: a **torpedo** (Reaper, Hammer, Atropos), a **finisher** (Harpoon),
an **anti-shield** missile (Sabot), a **saturation** launcher (a multi-warhead carrier, or a
torpedo launcher with a deep magazine such as the Typhoon's), or **general** (everything else,
which fires as it reaches). A **window** is a fact about the target read from where the
launcher stands at the moment of launch: it is overloaded, its shield is down or locked out,
the launcher is outside its shield's arc (the **flank**), its pool is running high, its hull is
worn, the pack's tank holds its attention (a striker's **strike**), or a packmate's **salvo** is
already on its way. A torpedo goes only into an overload, a lowered shield, the flank or a hull
with no shield; a finisher adds a worn hull and waits for one even against a shieldless hull; an
anti-shield missile goes into a raised shield, best a full one, and is held while the shield is
down; a saturation launcher goes with a salvo or into any opening. A launch into an opening
holds a **salvo** open on that target for the side's other launchers for a few seconds of the
hull fight's clock, so packmates' racks join it and the missiles arrive together, which is what
beats point defence by numbers. Nothing about the missile changes: when it goes is the only
choice. `-Dbattle.warship.missileWindows=false` is the control (launch at reach, a finisher held
for a worn or overloaded hull). A shield **lowered at rest** is no opening: an automatic shield
rises for the first hostile round on its way, long before a missile arrives, so only a shield that
cannot or will not rise (locked out by a full pool, or held down by hand) reads as down; a Sabot
may go into a resting shield, since its flight raises it.

**A ship spends a few missiles into each opening, not its racks** (`MissileOpenings`). An
**opening** is one stretch of a target's vulnerability that a side shares: the first launch into
an opening window opens it, and every launch of that side at that target while it lasts, a
packmate's salvo included, spends into it. A **forced** opening (an overload, a shield that
cannot rise) lasts as long as the target cannot defend itself; a **standing** one (the flank, a
pool running high, a worn hull, a striker's moment) lasts a few seconds of the hull fight's clock
and then the next launch opens another. One that is overloaded or worn is **decisive**, and a
forced or decisive window arriving in a lesser opening opens a new one. Into one opening a ship
spends at most its **budget**: a pair of volleys, or a quarter of what is left in its gated racks
if that is more; a torpedo or a finisher goes into a lesser opening only while the ship still holds
a pair of them back for a decisive one. What is not spent keeps for the next opening, so a rack
lasts over several shield lockouts and packmates spend theirs into the same one together.
General-purpose racks, and launches into a standing condition that is no opening (a Sabot into a
raised shield, a torpedo at a hull with no shield), are paced only by the tube ripple.
`-Dbattle.warship.missileRestraint=false` is the control: no budget, and a resting shield reads as
down.

**A missile at a hull flies a readable run.** It leaves the tube along the barrel's bearing and
swings onto its line in a **launch arc**, weaves through the middle of its flight, and flies the
last quarter straight in (its **terminal run**). A heavy body (a torpedo, a Harpoon) is pointed
rather than swung: a short arc, barely a weave and a long straight run-in over nearly half its
flight, a slow blow a defender sees coming. The motor's boost, cruise and coast along the line,
and so its timing and landing, are its spec's as before; only the shape across the line is the
hull run's. `-Dbattle.warship.missileFlight=false` is the control.

**A magazine at a hull is the base game's, refills included.** On the duel footing each missile
mount carries the weapon table's `ammo`, and a magazine the base game regains (`ammo/sec`, in its
`reload size`) refills at that rate on the hull fight's clock, never past its size; a full
magazine earns nothing toward the next reload. Nothing else refills a rack over a battle.

**Point defence watches what is coming, and answers what is coming at its own hull first.** A
point-defence mount with nothing in its reach lays its barrel on the nearest hostile round it
bears on within four times its reach, so a fast missile that is inside the reach for a few ticks
meets a barrel already on it. A missile or warhead aimed at a hull flies the hull fight's
geometry (the hull reach's distance at the hull range's speed), and point defence answers it in
the same geometry: it reaches it at the mount's hull reach, judges the shot as the base game
would at the base game's distance and speed, and cycles on the hull fight's clock, so a mount
has the base game's shots at a missile crossing its umbrella and a salvo is thinned as it is in
the base game (`-Dbattle.warship.pdHullReach=false` is the control; without it a mount's
umbrella is a sliver of a hull's length that a hull missile crosses in a tick or two, and nearly
every salvo landed whole). Against ground ordnance and aircraft a mount keeps its own reach and
clock. Choosing among rounds it can shoot, it weighs one aimed at its own hull as half as far
off, so a salvo coming at the ship is answered before rounds merely passing
(`-Dbattle.warship.pdThreatFirst=false` is the control). There are no flares or evasive turns:
no fit flown over a battle carries a flare system, and a heavy hull cannot move its own length
in a missile's flight, so point defence and the shield are a ship's answer to a salvo.

**A pack divides a hunt; an escort interposes.** When two or more ships of a side hunt
hulls with their duel helm and a hostile hull is being escorted, the hunter nearest that
**charge** runs it down and the others pin its escorts, re-decided as the fight changes
with enough hysteresis that near equals do not trade roles. An escort with a live lead
holds its place in the screen line until it has a hull to fight, then brawls on its
leash (above), and it saves itself by the ordinary withdraw rule. Ground fire
`HOLD` keeps a duelling hull off the ground entirely. The first time a ship turns on a
hull it takes the base game's **duel footing** for the rest of the battle: its variant's
capacitors and vents, and its missiles' magazines from the weapon table (an `ALL` order
takes it at once). A ship that never meets a hostile hull keeps the hull table's flux and
bottomless racks its bombardment was tuned to.

**A mission may hold hull fire until its moment.** A **hull-fire hold** is named and
battle-wide: while any is pending no `AUTO` ship fires at a hull. It is released by a
story beat, by a condition on the battle, or by the mission by hand, and stays
released. It times the start of a fight overhead and nothing else: ground fire, air fire
and an explicit `ALL` are not held, and it decides no outcome.

**A hull leaving by a climb-out is still a hull until it is gone.** It flies an
authored route over the map edge at its own speed, climbing by how much of the way it
has flown, and it has escaped only when propulsion has carried the whole hull off the
map. Until then it can be hit and it answers: a departing hull fires at hostile hulls in
its guns' reach as it goes, though it takes on no new ground or air work, and its escorts keep it as their lead and follow it out
once it has gone. A hull escaping across its side's edge is escorted out the same way. Any other departure of a lead releases its escorts.

**A hull that has made its height may leave straight up through the deck.** That is
the reverse of an arrival: the hull keeps way on along its heading, climbs, and is gone
from the battle once it is into the deck, without crossing an edge, so its escape does
not drag the fight to the map's edge. It counts as a climb-out (gone this way it has
escaped), it can be hit until it is into the deck, and its escorts follow it up.

**A mission's hull visits once and is nobody's to command.** A hull on a **visit**
belongs to its mission: no remote ship command, piloting or power reaches it, whatever
side it is on. It leaves through the ordinary departure once no hostile hull has been
over the battle for the visit's dwell. A hull on a visit usually arrives through the
cloud deck. A ground event (a wreck striking and skidding, an airframe going up) hurts
the ground and never a hull hovering over it.

**A mission may keep a hull over the battle until it is destroyed: it holds the field**
(`Warship.holdTheField`). Its doctrine never withdraws it, however worn and whether or not
a hostile hull is left to fight, and a visit it is on never ends, so it stays the
mission's without leaving. It fights exactly as it otherwise would; only the mission
itself can still send it away. A duel whose every hull holds the field ends only in
destruction or in a hull the mission sends out across its edge, and its winners stay overhead.

**A mission may hold the battle open until a hull's part in it has concluded**
(`WarshipSystem.concludeBeforeBattleEnds`). The battle then does not end, whatever its
objectives say, while that hull is still over the map: it concludes once it has flown off
the map, or once it has been destroyed and its wreck has fallen, struck and come to rest. It
is the warship form of the hold a crashing aircraft already puts on the battle's end, and it
is for a story overhead that has an ending: a ground fight that finishes first waits for it.
Nothing about how the hull flies or fights changes, and nothing in the ground referee reads it.

**The duel ledger** records, for a debrief, what became of every hull over the
battle: escaped (climbed out, or flew out across its side's edge as an escape, and gone), broke off (withdrawn by its doctrine), departed
(any other leaving) or destroyed (when, where it burst and where its wreck struck). It
also counts ground casualties by side, from stray rounds and from wreckage. Nothing in
the battle reads it, and a ground objective never does.

**A duel overhead belongs to its mission, never to the ground.** A ground chapter may
stage a duel through an optional seam on its referee: hulls stand quiet under a
hull-fire hold until the chapter's moment comes, the duel then runs on its own, and the debrief reads its
ledger. The referee settles on nothing the duel does; stray rounds and wreckage are the
only way it reaches the ground. Black Sluice is the first: the *Greyhook*, Voss's Venture,
landed alone in her cradle south of the ridge (the frigate pads beside her empty), and a
Hegemony Enforcer and a Wolf lent by Dormer's family arriving when Voss orders the withdrawal
(owner, 2026-10-19: "We don't need this to be an even fight"). Their
edge is the west, the side the company landed on and
the far side of the basin, so they fly in across it heading east and their whole approach
is in sight before a gun bears; Greyhook's edge is the south, over her own pads and the flats
beyond, the only way she leaves. No area is declared; the fight goes where it goes, held off the edges
only by the standing pressure. The map is sized for the fight, not only for the ground: open
approach ground west of the station puts Greyhook's pads outside an Enforcer's reach as it
crosses the edge, so Dormer's ships close for most of a minute in sight before a gun bears, and
the standoff fits along the map with the edge margins clear. The *Greyhook* fights first and
runs second (owner, 2026-10-19: "We can add back the escape for the Venture as a story element
to give it a conclusion"). She lifts and fights alone under the ordinary doctrine and tactics,
and every hull of the duel holds the field, so no doctrine withdraws one. Voss's order to run
is the one scripted thing (`BlackSluiceDuel.BreakOff`): a set time after the first hull fire,
somewhere in a window that the moment of Voss's order fixes, or sooner if her hull falls under
half or nothing of Dormer's is left to stop her, she flies out across her edge, answering what is in reach as she
goes, and Dormer's ships, faster than she is, keep shooting. She gets off the map or is shot
down onto the basin; either way the battle waits until she has (above, holding the battle
open), and Dormer's ships stay overhead once she is gone. The pair fight her as a pack relay,
the Enforcer her anvil and the Wolf darting at her quarter, and the mission refits the
Enforcer.

**A mission may refit a hull for one fight.** A **refit** (`HullRefit`) is named, given to
one hull by the mission that fits it, and touches only what a shielded hull fights on: its
flux pool's capacity and dissipation (the variant's capacitors and vents included), its
shield's arc, upkeep, and the flux each absorbed point costs. It never touches a gun, the
plate or the hull, never the hull table or a variant another fight reads, and never the
rules every hull fight shares, so the balance law still holds everywhere a mission has not
named a refit. Black Sluice is the first: Dormer's Enforcer flies in his refit
(`BlackSluiceDuel.RUSK_REFIT`), while the Wolf and the Greyhook fly as fitted (her refit seam,
`BlackSluiceDuel.GREYHOOK_REFIT`, is at identity). The fight reaches the radio as story beats
(Dormer's ships sighted as they cross the edge, closing, the first hull fire, the first of them
down while the Greyhook still flies, Voss ordering the run, the Greyhook down or off the map,
the last of Dormer's down). The debrief says whether she got away or was shot down short of her
edge; only the company pulling out leaves her still up there. A
warship over a ground battle never launches a wing from its bays (bays only classify the
hull), and the Greyhook's fitting carries none, so it stays hull against hull. She carries no point
defence: her two small hardpoints, fixed forward, hold a Harpoon and a Sabot rack (owner, 2026-10-19).

**A warship's stay and its magazine can both be bounded.** Station time is counted
down only while the ship is on its station, not while it travels to it; when it
is spent the ship stops firing and hovers away along the line from the map's
centre through where it stands, at its own speed, and is removed once clear of the
map (or after a fixed outer limit). Its point defence keeps working until it is
gone, since it shoots ordnance and not ground. The ordnance budget is counted in
rounds: every area-fire round landed and every lance sweep laid spends one, from a
single pool shared by the ship's mounts, and a ship can instead (or as well) give
each mount the magazine its own weapon carries, from the weapon table's `ammo`
column, where a blank column is a gun that never runs out. Rounds already in
flight when the pool empties still land; nothing new is fired, even mid-burst.
A mission can also release a ship into forward departure on its current nose
bearing. This abandons pending bursts and directing, and uses normal propulsion
and off-map removal. A patrol flyover therefore continues from the firing station
without a new broadside turn, a teleport, or further bombardment.
Both budgets are unlimited unless a mission sets them, and both are readable
(remaining seconds, remaining rounds, per-mount magazine) for a HUD.

**A warship has a posture: its current job, chosen from the battle and never
from a damage dial** (`warship-behaviours.md` owns the model). A doctrine reads
a snapshot once a second and picks one of five postures: bombard (the shipped
density director, and the default for a ship with no doctrine), support
(attacking), intercept (defending), air superiority, and withdraw. A posture
sets the parameters of machinery that already exists (how many areas the
director holds and how wide, how near a target may be, which mounts may start a
burst, where the flux stops-and-resumes thresholds sit, whether the station
keeps clear of a declared hazard); it fires nothing itself and no posture
scales a gun's damage. Air superiority is entered when the weighted threat to
the ship (a mission-declared threat, hostile warships, hostile strike craft)
reaches an entry level and left only after it has stayed under half of it for
a dwell, so a pause between sorties is not the end of the fight; while it lasts
the ship spends flux on shots of opportunity from its secondary battery only,
and keeps clear of any declared capital hazard. Withdraw is for the rest of the
battle and leaves through the ordinary departure, for a hull worn low or a ship
threatened past what it can answer. **A ship fighting a hull fights it to the end**:
while it has a live hostile hull to fight, the doctrine never withdraws it, however
worn, so a duel between warships is decided by a kill and never by a voluntary
break-off. A ship stays in that fight in the moment between its foe going down and its
choosing the next, and while its anti-ship guns are only silenced under repair: either
way the nearest live hostile hull is its foe. A withdrawal a mission orders (an edge exit, spent station time)
is the mission's, not the doctrine's, and stands. A hull that holds the field (above) is never withdrawn by its doctrine at all, foe or none. A hull still coming down through
the cloud deck, or flying in with its centre still past the map's edge, is not yet a
target, and one still past the edge does not fire at hulls. What the ship is trying to do
about the objective is a **mission stance** (attack, defend, hold), a value a
mission sets and changes at any time, never derived from the ship's side; a
hostile loaded shuttle is never a threat to a ship but an inbound drop. A
mission may pin a posture, which stops the doctrine, and the debug readout
states the posture and why. **A warship has a class**, read once at fit time
from the hull's size, its hints, fighter bays, designation and shield type
(combat, carrier or support, at frigate to capital size); the class is not a
posture and only scales how much each air threat counts toward the entry level,
so a frigate fears a fighter and a carrier fears them more, while a combat
capital is the unscaled row. A carrier additionally conserves flux in a fight and keeps a wider standoff (`PostureProfile`; `warship-behaviours.md`).

**Support fire weighs the objective and its own side, and never refuses a
manual order.** Under the support and intercept postures the director scores
each candidate area as its hostiles times one plus twice its closeness to the
mission's objective ground, less a heavy weight per friendly body in its blast.
Objective ground is read from what the battle already says: the assignments its
own side's squads hold (an attacking ship looks where the assault was sent, a
defending one where its squads hold), the tactical map's objective nodes, and
ground a mission declares on the ship; a hostile commander's orders are never
read. A friendly counts when a round aimed at any hostile in the area would land
within the weapon's blast and a safety margin of it, and declared protected
ground counts heavily with its own radius. An area with any friendly exposure
is refused, an area already held is dropped when friendlies walk into it, and
when every candidate is refused the ship holds fire and says so (the readout
counts the refusals, so a starved ship is visible and not mistaken for broken).
A mission may set **danger-close**: the ship then fires, still preferring fewer
friendlies. The weapons are not weakened by any of this.

**A defending ship denies a landing before anybody is standing in it.** Under
the intercept posture an inbound hostile loaded boat is ranked by the people
and machines it carries per second to touchdown, and its landing zone becomes
a held area aimed at open ground, not at a unit. Each barrel waits on the zone
until a round fired now would land as the boat touches down, so the first rounds
fall with the boat and keep falling until everyone is out of it; a zone with a
friendly in the blast and margin is skipped unless danger-close. Denial is the
fire for what cannot shoot the boat: whether a detonation on a boat's pad hurts
it is not modelled (the boat is untouched in the scene). The ship also shoots a
loaded boat in flight with the weapons the next paragraph allows, and a boat it
brings down loses everyone aboard.

**A warship's weapons may fire at aircraft, and the weapon decides which.** As
in the base game, the gun and not the hull is what cannot track a fighter. A
weapon's class is read from its table hints and the size of its slot: a mount is
*flak-capable* when its weapon carries `ANTI_FTR` or `PD_ALSO`, or is a
medium-or-smaller weapon that is not `STRIKE`; *point defence* when it carries
`PD` alone; and *assault-only* when it carries `STRIKE`, or is large with
neither an anti-fighter nor a point-defence hint. The air layer has two target
classes: a fighter (any fighter hull, bombers included) and a transport (every
other craft, a shuttle or a fitted boat, which is slow and large). Flak-capable
mounts may engage either; point-defence mounts only fighters, because a
transport is a ship and the game's point defence never fires at ships;
assault-only mounts only a transport. A mount that may not engage a craft is
silent against it however near it flies. The Onslaught's Mark IX, Reaper and
built-in cannons are assault-only; its Devastators, Hellbore-class mediums,
Annihilator pods and light guns are flak-capable; its flak and vulcans are point
defence. The ship does not look up unless told: an explicit order (fire at any
hostile aircraft), or a posture. Air superiority engages any hostile aircraft in
reach, armed craft first; intercept engages loaded boats; every other posture,
and a ship with no doctrine, fires at ground only as it always did.

An engagement is the weapon's burst on the weapon's own cooldown, paid for in
flux and ammunition like any other fire (a posture's flux limit governs it, so
a ship conserving flux for the air fight cannot spend it on a heavy burst), and
a mount with ordnance to shoot always shoots the ordnance first. A projectile
flies as an ordinary shell to the point the craft is led to (its velocity times
the flight time) and a beam pulses at once. Whether a round hits is a pure hash
of who fired what at which craft, never the shared random stream, against the
point-defence chance (an aircraft is fast ordnance crossing at its cross-range
speed, worse at long reach and with a wide cone), times 0.6 for a fighter,
which jinks; a miss passes wide. A hit lands its weapon's damage on the craft
through the same armour and structure law every body takes, and a craft taken to
nothing dies the one way an aircraft dies, with the passengers still aboard.
That route is the only way a shot reaches an airborne body: the ordinary damage
route still refuses one, so a blast or a bullet fired at the ground never
touches a machine overhead.

**A warship is overpowered on purpose, and flux governs its rhythm, not its
balance.** Owner direction: these ships are not balanced inside the battle. In the
world they are gated by role-play context (diplomacy with the local government, a
fleet already busy with hostile fleets) and later by the player choosing
compatible fleet ships for compatible missions. Readiness (combat readiness) and
a command-power commitment are deliberately **not** modelled until general ship
support exists. So the in-battle limits are about rhythm and legibility, and no
weapon does less damage for them. Flux is the fire-rate governor, read from the
game's own data: the hull's `max flux` and `flux dissipation` and each weapon's
`energy/shot` and `energy/second`. All mounts, point defence included, add to one
pool that drains at the hull's dissipation rate. A burst or a lance is paid for
when it is started. Below 85% of capacity the ship fires freely; at 85% it stops
starting new fire (what is under way finishes, a laid lance burns out) until the
pool has drained to 55%, so a battery surges, rests and surges again, and a salvo
that would not fit in what is left is not started, which keeps a governed ship off
real overload; nor, on a ship with an automatic shield, one that would carry the pool past
that shield's 90% drop level, so a ship's own guns never lock its shield out (a Wolf's
antimatter blaster is over a quarter of its pool a shot).
`-Dbattle.warship.shieldReserve=false` is the control. A ship **presses** past that order, up to 80% of its pool (short of its own
shield's drop), at a target that is overloaded, or shield-down and deeper in flux than
the shooter, or at least 60% full and ten points deeper than it: the leader in a flux war
keeps the pressure on until the other shield gives, and of two ships with their shields
locked out the one further gone breaks off to vent. If the pool does reach capacity (point defence, or the soft limit
switched off) the ship overloads: every mount, point defence too, falls silent and
bursts in progress are abandoned for four to ten seconds (longer the further past
capacity), a laid lance still burns out, dissipation continues, and the pool is
empty when it resumes. Capacity and dissipation scale together
(`Warship.fluxScale`). A directed ship is on flux by default, at half the hull
table's figures (`Warship.BRAWLER_FLUX_SCALE`); a ship given only explicit
orders, as a cinematic script does, is not, and `limitFlux`/`unlimitedFlux`/
`fluxScale` override either way. The half used to be a stand-in for the
shield's share of the pool; shields are modelled now (next paragraph) and take
their own share when they are raised, but the half stays, deliberately: a
shield is up only when something threatens the hull, so in a ground fight
nothing is drawing on it, and at the hull table's own figures a ground
bombardment never nears an Onslaught's capacity (measured in the
`warship-shields` scene: the guns alone peak at 28% of the full pool and never
rest, against 82% of the half pool and a visible surge-and-rest). It is a
brawler's fire-rate setting, not a placeholder. It is readable for a HUD (`fluxFraction`,
`overloaded`, `overloadSecondsRemaining`), and the debug warship panel can toggle it.

**A shield is the major draw on that flux, and the trade is one pool.** Owner
direction is half of the game's functionality for a brawler, so the model is the
part that moves the budget and nothing finer. The figures are the hull's own: the
table's `shield type` (FRONT, OMNI or none; a phase cloak is none), `shield arc`,
`shield upkeep` and `shield efficiency`, and the spec's `shieldRadius` and
`shieldCenter`. While a shield is raised it draws its upkeep (a share of the
ship's dissipation, per second) from the pool, and **the pool stops venting its
hard flux**: what absorbed hits put there. Everything else in the pool is soft (the
guns, point defence, the upkeep itself) and keeps venting at the hull's rate behind the
shield, as the base game's does; with the shield down the soft share vents first and
then the hard. A ship that holds its shield under fire therefore climbs by what it is
hit with rather than by its own guns, and vents that only once the shield is down. A
hostile detonation that reaches the hull from inside the arc (asked of the side the round was
fired from, for a round with a source) is absorbed: its
damage times the shield's efficiency becomes hard flux and the hull takes nothing.
`-Dbattle.warship.hardFlux=false` is the control: no flux is hard, and a raised shield
holds the whole pool from venting. A
FRONT shield faces the hull's heading; an OMNI shield covers every bearing. A hit
from outside the arc, or while the shield is down or still rising (0.4 s), goes to
the armour exactly as before. If the pool reaches capacity the shield drops and
the ship overloads, the ordinary overload. A raised shield therefore costs the
guns its upkeep and the pool's room, and under sustained fire the whole ship its guns:
the hits it holds fill the pool until the shield must drop to vent them.
By default the shield is automatic: it rises when a hostile round that would hurt
the hull is about to land within a few cells of it from inside the arc (for a hull with an
armour grid and a round with a firer, judged by the side it was fired from, as absorption is) (a queued
detonation, a projectile in flight, a hostile warship's shell, or a landing that
close), drops three seconds after the last such round, and drops at once when the
pool passes 90% and stays down until it has vented to 55%, or to the level at which its
posture lets the guns fire again if that is lower (the ship takes hull damage rather
than overload, and a shield cannot hold the vent shut while the guns are still silent). `raiseShield` holds it as commanded and `shieldAuto` gives it back.
A shield works only on a ship that is on flux, so a scripted hull's armour answers
as it always has. It is readable for a HUD (`shieldUp`, `shieldLevel`, `shieldState`,
absorbed hits and flux) and drawn as the arc of the hull's own bubble. A round too
weak to hurt the hull never raises an automatic shield, but a raised one absorbs it like any other.
Not modelled: damage-type multipliers on efficiency, rounds stopped at
the bubble's radius rather than at the hull, and per-hull shield colours (the
stock hulls name none; every shield is the default pale cyan).

**A missile flies the flight its own spec gives it.** A missile mount's round
follows a closed-form path, evaluated rather than integrated, so what is drawn,
what point defence can shoot and what the ground suffers all read one clock.
The path is read from the projectile and the weapon table, with nothing authored
per weapon: it launches at the table's launch speed, boosts at the motor's
acceleration to cruising speed, cruises while the motor burns for the weapon's
flight time, and, if the aimed ground is beyond what the motor can carry it,
coasts with the speed decaying exponentially and lands short by what the coast
loses. Gravity moves nothing a warship fires. A round is held to its arming time.
Across the line it bulges to one side, a launch fan that makes a volley a
trumpet, and weaves. What kind of missile it is is derived from the spec, not
declared: an unguided rocket (`ROCKET`, or no turn rate) only wobbles lazily on
its bulge, while a guided missile (homing, poorly guided, a carrier) jinks across
the line with an amplitude bounded by what its own turn rate can do at its own
speed, so a swarm missile jinks hard and a stiff one barely. Both ends of the
path are exactly the committed ones. Every random-looking quantity is a pure
hash of one seed taken from who fired what, where and when, never from the shared
random stream, so a missile cannot shift another gun's draws. The nose points
along the path's tangent; the drawn flame, glow and smoke are the projectile's
own engine slot and contrail spec, the smoke being the path sampled at earlier
times so it hangs along the weave.

**A multi-warhead missile carries its warheads' payload, not its own table row,
and releases it as a swarm.** The Hurricane's row says 500 but its spec releases
seven warheads of 500; the Sabot five of 200 kinetic; the Hydra six of 500 energy.
A multi-warhead missile flies as a *carrier* to its split point (the spec's split
range short of the ground, no nearer than a third of the way, and no sooner than its
minimum time unless the spec says it may split early), and there it *splits*: one flash, one split sound (the spec's own),
a puff of the spec's smoke colour, and its warheads leave it as bodies of their own.
Each warhead carries the spec's damage, damage type and blast footprint and a
detonation committed when the missile was fired. They leave the split in the spec's
fan (the Hurricane's is wide, the Sabot's twenty degrees is a shotgun), curve and
weave, and converge on aim points scattered **at random** over a disc about the ground
the carrier was aimed at, arriving staggered across a window after each one's own
travel: a drumroll, not a tulip and not a single thump. The warheads are dealt out
by a stratified random scatter, so cover is even without being patterned, and the
arrival order blends distance from the shooter with chance, so the burst can walk across
the ground without being a metronome. The disc's radius grows with the number and the
size of the warheads. A carrier shot down before it splits loses the whole payload.

**The weapon's damage and area are a budget, however many bodies it becomes.** Damage
adds, so `n` bodies carry `D / n` each; area adds too and goes with the square of
the radius, so each carries a radius of `R / sqrt(n)`. Where the spec already gives a
count (a burst, a warhead count) the shares are the spec's and nothing is re-derived;
the budget binds where the count is synthetic and wherever bodies are fused. Bodies
are capped (32 from one pull of a trigger, 160 in the air from one hull, 480 in the
battle) and **over a cap they are fused, never dropped**: a round beyond the cap folds
into a body of the same weapon already in the air (damage added, radii added as areas),
and a carrier's warheads fuse into fewer, heavier ones, so the picture is thinned and
the damage is not. A carrier always keeps at least one warhead, so the ship cap can be
exceeded by a carrier but never by a rack round.

**A rack volley is a rolling barrage over a footprint.** A rack, pod or swarm launcher
(Annihilator, Hammer rack, Squall, Swarmer, Locust) fires one body per round already,
and a burst of three or more is one *volley*: its rounds are scattered over a
footprint about the ground the mount chose (the same stratified random scatter, nearer
rounds first for a rocket rack, in no order for a swarm), and a burst the spec spaces out
over many seconds is held to a few so the barrage rolls rather than trickles (a Squall's
twenty rounds leave in about three seconds, not ten). The feel is authored in a small
table by archetype (rocket rack, light swarm, MIRV bloom, heavy) and overridden by weapon
id; every weapon the table does not name derives its archetype from the spec, so a modded
launcher arrives with a sensible shape. A heavy single torpedo (Reaper, Atropos, Harpoon,
Hammer) stays one deliberate body.

**How literal the missiles are is a per-ship preset.** FAITHFUL is the spec's numbers
only (a straight sheaf in a short stagger, rack rounds in the weapon's own cone, no
telegraph). FLAVOURED, the default, is the bloom, the scatter, the compressed barrage and
a faint telegraph. CINEMATIC is the same over a longer, wider drumroll and also splits the
Hammer family into a cluster inside the budget rule; the torpedoes keep their single warhead.
The presets change flair and never ordnance: damage and area are the budget in all three.
The ground a volley or sheaf will fall on is marked while it is still in the air by a faint,
dashed, slowly turning ring that pulses faster as it nears, in the style of a lance's
telegraph and much quieter.

**Every body is something point defence can see and kill.** A round, a carrier and a
released warhead are each one *interceptable body*: where it is drawn, how fast it is
crossing, whose it is, how tough it is, and a way to mark it dead. A warhead's
toughness is the carrier's own hit points shared among them (the spec names none for
the warhead), a rack round's is its projectile's. Toughness blunts a point-defence
shot: none up to a flak-soft sixty hit points, then falling so that a swarm of weak
missiles is the case flak was designed for and a torpedo is not. One engagement still
kills at most one body, so a swarm saturates a mount by volume; and a carrier is the
weak point of a cluster, because shooting it down before the split takes seven
warheads with one shot. The same bodies are visible to the carried interceptor pods
and the ground defences that read a battle's ordnance through the one seam, so they
stop warship missiles without knowing what a warship is.

**A standoff missile's beam is the one payload point defence cannot stop.** A
Dragonfire flies to the spec's standoff short of the ground, paints it for the
spec's time, then fires a beam payload. It is a hard body to point defence on the way
out and while it paints, and a missile shot down in either fires nothing; once the beam is
loose it is no longer ordnance in the air, and every defender (a warship's mounts, a
tender's lasers, a deployable pod) leaves it alone. A Hydra's platforms likewise swing
out abeam of the ground before they come in, to both sides.


**Point defence reads the same path.** A mount engages a missile where it is
drawn, at its speed at that instant; one engagement still kills at most one round.

The wider design, which carries the lore of each missile line, the swarm-burst
volley and body model, and the open questions, is `warship-missiles.md`; the
owner's direction there is spectacle first, point defence as the counter on the
ground and in the air, and balance after.

**What a ship is trying to do in an ordinary fight or mission is designed in
`warship-behaviours.md`.** A small doctrine above the orders would pick a
sticky posture (air superiority, support, intercept, bombard, withdraw) from a
threat picture and the ship's stance on the objective, and set only the
parameters of the machinery described here: the director's areas, an
opportunity range, a flux ceiling, a station. It issues ordinary orders and
changes no weapon's damage. A warship's weapons may fire at
aircraft by the rule above, and hostile strike aircraft can now hurt a warship:
the dispatchers (field sorties and committed wings) treat a hostile hull as a
candidate target ahead of the ground, provided the craft's ordnance gets
through that hull's armour at all and fewer than two of the side's craft are
already working it. The craft flies the ordinary attack run at the hull's
centre and its rounds land as ordinary detonations, so the hull's own shield
and armour rule answer them; there is no second damage route and the ship is
still not an entity. The threat picture counts an armed craft only where it can
hurt, and shield type scales its weight (none, then front, then omni, fear it
less in that order). Ground defences still cannot hurt a warship at all.

It must be answerable. The hull and armour pool above, a bounded time on station
or ordnance budget, and telegraphed sweeps and falls are the counterplay; none of
them is optional. Modules, cumulative ship damage beyond the hull pool and ground-weapon ceiling
rules remain future decisions. Altitude is ultimately a shared camera-space axis, never a fake
shrink that changes a hull's physical scale.

### Tenders

A **tender** is an overhead hull whose job is its drones: the vanilla Shepherd
fitted as a mobile construction ship. It is not the Shepherd boat. The boat is a
shuttle-layer craft that lifts a squad for one sortie; the tender is a warship
body that hovers and stays, composed with a complement and a line of build
orders. Nothing in it is a second flight, drone or turret model.

**The body is a warship; the drones are drones; the product is a turret.** The
hull is fitted from the same `.ship`, hull table and sprite as any capital, with
the vanilla variant's guns left off, and is flown by the warship helm to a station.
It can be fitted from a campaign Shepherd as a warship is from its fleet member.
An explicit mission or debug fixture may supply a stock hull; ordinary campaign
support instead requires a currently owned eligible committed member. Losing or
selling a rewarded ship neither reopens its story award nor summons another tender.
Its complement is the hull's built-in wing, half
defence drones and half builders, drawn as the Shepherd's own wing is in fleet
combat (the Borer sprite, at hull scale), which is a look and nothing else.
A defence drone is the shipped armed drone whose squad is anchored to the tender
instead of a hub; a builder is the same unit unarmed. A finished site is an
ordinary map-turret entity from the turret catalog, so what it can do and how it
dies are the turret's, not the tender's.

**A build is a visible job with a clock, and it is free.** A laid site joins a line;
nothing is spent. The tender keeps an optional cap on build charges that defaults to
unlimited, so a cost is a number to set rather than a rule to build. The tender holds station a few cells short of it, the builders fly out and
work a ring around it, and progress is linear in the builders present against a
full crew, so one builder takes three times as long and none take forever. A
site is stood down, with the builders sent home into the bay, while a hostile its
side knows of is inside a threat radius wider than any rifle's reach, or for a few
seconds after a builder is hit, and resumes when the ground is clear. **A tender knows
what its side knows, not where every enemy is:** the contacts its squads remember and
what the hull and its drones see from the air. An enemy nobody has seen is found by
being shot at. A lost drone is gone for the battle. Builders are heard and seen at work in the game's own sounds and presentation-only effects, from events the tender tick records (weld crackle, stun on a hit, a ping when a site stands down, a chime on completion); `tender-ship.md` has the cues.

**The wing flies; it does not teleport.** Drones leave through the bay's mouth in a
staggered line, and recall, a stood-down build and a retreat are flights home to a
short approach and then the bay, docking on arrival, one a tick, and leading a moving hull. Defence
drones loiter on a loose ring round the hull, clear of a longer hull by its extra length
(tighter when it is threatened), engage
the hostile nearest the hull inside its reach, are let off their target past a leash,
and a badly hurt one goes home to be repaired and sent out whole. Builders keep one
station each on the ring round the site and weld there. This is the ordinary drone
swarm action under an anchor that declares itself an escort; hub squads are
unchanged. Variation comes from a hash of the drone's id, never the shared random
stream, and neighbours' positions are a serial-phase snapshot, so the wing replays
identically. `tender-ship.md` has the numbers and the reasoning.

**A tender is a weak ship, hittable like any ship, and only by ordnance.** It is
not a combatant; it is pressed into the role. It carries the vanilla Shepherd's own
hit points, armour and omni shield on the hull's own flux, the shield raising itself
by the ordinary automatic policy, and no defensive buff beyond that. Its drones are
what a rifle can reach. The shipped drone's strength is left alone, so a screen of
three stops a lone raider and loses to a fire team; the lever for more is more
drones, never a tougher drone. `tender-ship.md` carries the rejected alternatives,
the build candidates and the open questions.

**A tender's point defence is its defence drones, and it is weak.** As in the game, each
defence drone carries the Borer's mining laser and burns hostile ordnance with it: the
same `Interceptable` bodies and opted-in ground rounds that warship flak and carried
pods engage, destroyed rather than damaged, so a stopped body never lands. Reach, damage
per second and the mount's turn rate are the laser's own table figures (reach at ground
scale), and a beam is a *dwell*: the body falls when the damage it has taken reaches its
hit points, so a missile costs one drone seconds, a torpedo is beyond the wing, and a
salvo bigger than the wing can burn through in the time it crosses saturates it and lands.
The wing puts its beams on the most urgent body, ordnance bound for the hull before
anything else, then the ground a drone overflies within its own reach. Only a defence
drone that is out of the bay, alive and not away chasing a raider fires; a builder
never does, a docked or recalled drone cannot, and the tender stays a ship with no guns.
The tracer is drawn through the warship point-defence marks.

A tender may be flown by the player through the direct-control carrier seam (`direct-control-nouns.md`, Tender): the same build orders and drone recall, laid by the pilot's buttons instead of an AI or the debug panel.

A mission may instead supply a **grounded construction host**: an existing static
hull on the map owns the wing and the construction line, without creating an
overhead ship or granting the company a fleet member. Its exterior bay is fixed;
builders work only within its finite reach, along clear exterior sortie corridors
with clear work stations. It has no flight, extraction, helm or autonomous build
director. The mission enables its construction command card when power is
available and reports loss of the static host. Drone damage, finite losses,
known-hostile stand-down, build progress and completed structures follow the
ordinary tender rules. The mission's static hull retains its own appearance and
collision; its construction metadata is never an air-layer body.

**Unpiloted, a tender builds from its hull's mission stance.** The stance is the same
`MissionStance` a warship's doctrine reads, and the ground it cares about is the same
objective ground. Under ATTACK it raises the quickest position on the most forward
cleared ground behind the advance and keeps close to it; under DEFEND it raises the
heaviest it can pay for on the known approach to held ground; under HOLD, the
default, it waits to be told. The AI lays the same site a pilot or the debug panel
would, so building, stand-down and failure are one model whoever chose the site. What
bounds a free tender is ground already covered by its side's turrets, not a count.
`tender-ship.md` has the rules.

A recovering mission hull may instead use a **low hover** with the same full-size
body as its grounded appearance. It remains a live warship and tender, with a
fixed attitude and swept terrain clearance rather than a second movement model.
Its builders work within reach of the moving hull and outside its footprint;
completed or cancelled remote construction returns mission travel, while explicit
Move or Hold retains helm authority. The whole body keeps its scale until ascent.
Physical departure means propulsion carried that whole hull beyond the map;
timeout removal is a separate ending and cannot satisfy a mission's departure
award. `early-operation-nouns.md` owns Working Passage's company gates, militia
holds, critical abort, recovery and once-only fleet transfer.

A mission may instead **land** a warship in a berth (`landed-ships-true-scale.md`).
A landed ship is drawn at **true scale** beside infantry, on the grounded Shepherd's
convention of 0.26 cells per vanilla hull pixel (about 5.8 times the shared air
density): a Hound is about nineteen cells long, a Venture about fifty. It is a
cold ground object: no plume, guns stowed, drawn among the vehicles, its shadow
tight under it, holding its berth. It is still a live warship with its flight
fitting: hit shape, slots and shield stay at flight scale, which is sound only
because nothing engages a berthed hull (infantry fire never reaches a hull, and a
mission that lands ships holds hull fire until it lifts them). **Lifting is a
run, not a resize**: a lifted hull flies its **lift run**, a leg ahead off the
berth, a climbing turn toward where it is going (a point the mission names, the
first waypoint of a climb-out, or else the middle of the battle) and a leg out
along that bearing, all scaled to its landed length. Its drawn body eases by
smoothstep to flight presence with the share of that run flown, while the shadow
draws out from under it, the berth's grit blows out and the guns run out, so the
size change reads as altitude and distance carried by travel and never as a hull
changing size where it sits. The run is flown before any route or station the
mission has given; a hull given nothing newer holds where its run comes out, not
over its empty berth, and a hull held or taken over mid-run is through its lift
by time. The ease is a ratio to whatever `HULL_PRESENCE` is in force. A hull with
a remaster (`art-source/ships/`) draws it in both regimes, so detail does not
change at liftoff either; the berth itself (a concrete pad, see-through and
unwalkable) is the mission map's.

### Authored structural bombardment

A mission may explicitly order a capital to demolish standing structures in a
bounded area, even when there are no living enemies inside. The ordinary area
engagement order still targets hostile units. Structural bombardment chooses
actual standing wall cells within the stated circle and uses the same mounting,
traversal, ammunition, projectile flight, blast and terrain damage rules.
Its explicit stationary-structure envelope is three times the ordinary
weapon range in ground cells, allowing a capital to stand off outside a base.
This is a gameplay range policy, not a damage multiplier or a claim about
literal Starsector metres. Acquisition, retained aim and hull-facing selection
all read that envelope; normal unit engagement keeps its ordinary range.
Clearing occupants does not erase a structure, and ordering bombardment does not
turn unspent ammunition into damage. Hold fire cancels unlaunched burst rounds;
rounds already in flight retain their ordinary impact and friendly-fire behavior.
The mission owns evidence, withdrawal, warning and completion; the ship owns its
weapons and the damage they actually cause.

Ground trigger permission is separate from the area order: batteries can acquire
and traverse onto ordered ground while launches are withheld. Withholding cancels
unlaunched burst rounds and leaves point defence independent. A heavy opening
permission admits weapons whose individual rounds carry at least 100 damage;
ordinary permission restores the whole battery. Neither changes weapon damage,
cooldown, ammunition or travel. A settled bearing can be retained by subsequent
area orders that do not request another hull turn. Structural orders may clip
their circular acquisition envelope to a rectangular sector; ordinary splash
and already committed rounds can still cross its edges.


## Air support pool

A side may hold a **pool** of bombers and fighters that ground forces direct. The mission declares each kind's stock, cap and regeneration time as it declares a mech-drop allowance; a legacy mission declares none and the machinery is inert.

- **Who calls.** Only a living comms operator (a marine carrying `special.comms-radio`, the forward-observer kit, which the player may also acquire through the armory; it is worn as a back-mounted pack in every pose and raises a corded handset to the helmet only while the call is on the net) that is not suppressed and has a clear line to the point, or to a hostile the side has sighted, may task an airframe. So may a **fort's radar** (`FortSensors`, `forts.md`): the radar on a fire-control bunker is an operator for whichever side holds the bunker, designates only ground it has in sight over real ground (its own works looked over), vouches for no belief, is silent while the bunker is contested or wrecked and sees only by eye without the fort's power. The director picks its target from the concentrations an operator can see, never the densest group anywhere. A planted Bombard (`bombard.md`) an operator sees is a designation of its own, scored above a lone nest, and a fighter's cannon is enough for it. The AI's director and the player's Air Strike power both go through one request, so they cannot disagree.
- **What flies.** There are three errands, each a real sortie through the corridor and the ordinary gun-run flight. A bomber **bombs**: a stick on a seen point. A fighter **strafes**: the same approach, run, pull-out and return with the autocannon, light on armour and cheap. A fighter on **cover** takes a station on a ring over friendly ground and fights from it (below). An airframe is busy for its sortie. One shot down is lost until it regenerates; one driven off (hull below a share) or aborted flies home and returns to the pool. The ledger keeps called, launched, returned, aborted and lost per side and errand (`AirPoolService.tally`).
- **Which airframe a target calls for.** `AirTasking` classifies the ground within five cells of the aim point: works (a gun or drone hub), armour (a mech, or a vehicle the cannon cannot penetrate) or open. Works and armour call for a bomber; open ground calls for a fighter unless a dozen or more combatants stand massed, which calls for a bomber. The other kind is sent when the preferred one is out of stock, except that the AI director will not send a fighter at a nest: it waits for a bomber, because a cannon pass at a hardened gun costs a cheap airframe for nothing. The player's Air Strike power asks by position and the pool picks the kind. A fighter is exposed to anti-air by 1.5 times a bomber's drain on a low run, and replaces faster (25 s against 40 s in the Conquest declaration).
- **Cover (CAP).** The commander (`ConquestCommand`, from a company's muster and assault stages) or the director (when a hostile armed aircraft is seen near its units) asks for cover over a point through the comms operator, as any call; the director keeps one fighter back for strafes and refuses a second patrol within forty cells of a standing one. The fighter flies the ring a gunship does (`GunshipOrbit`) for a bounded station time. From the ring it chases a hostile armed aircraft inside a hundred and ten cells of its cover (attackers of the covered ground first; one over anti-air the side has sighted is left alone, and a chase that carries the prey over such guns is dropped), in the `INTERCEPT` phase, with `AirToAirFire`: nose guns with a firing window of sixteen cells and a narrow cone, a rolled hit chance falling with range and angle, damage through the one aimed-damage path every body takes, losses recorded as "air-to-air". **The chase is a tail chase** (`AirIntercept`): met head-on the two craft close at the sum of their speeds and the guns hold the prey for about half a second, so a fighter that is clearly the faster flies lead pursuit from ahead or beside (which also turns it in behind once it has gone by) and, from the prey's rear quarter, steers for a slot eight cells behind the prey, braking onto it, where the cone holds for as long as the station does. Against a prey it cannot outrun it flies plain lead pursuit and takes what window the geometry gives. A cover patrol therefore flies the Talon airframe (`AirPoolKind.coverProfile`), faster than the Dagger it hunts; a strafe or bomb sortie keeps the Broadsword or Dagger. A cover gun pass holds its rounds while a friendly stands within the blast and a margin of the aim. On a ring centred on the covered ground a bomber is, in the sweeps, downed during or after its stick rather than before it: the fighter on the far side of its ring cannot turn and catch an inbound bomber inside the second and a half before release. Placing the patrol forward of the ground is the open lever. Secondarily it takes a gun pass at the densest group of hostile combatants the side has sighted near its cover, only with no friendly within eight cells and the AA risk under the ceiling. Symmetric: a defender with a fighter stock does the same over its own ground. The cover fighter's rounds and time are finite; it goes home when they run out.
- **Where it comes from.** A sortie crosses the map edge nearest its side's home ground: the landing zone for the side that landed, the keep for the side that holds one. It comes in over its own side and its stick runs on toward the enemy. Home was once the centre of the side's live force, which moves with the advance: with the marines gathered short of the keep near the far edge, a marine bomber came in from the enemy's rear and laid its stick back through them, killing seventeen in one pass.
- **Danger close.** The director does not place a strike with a combatant of its own side, or of any side it fights beside, within ten cells of the aim point: an allied officer never puts a stick on the company.
- **Ground, not fliers.** A stick or a gun run is a ground blast, and no ground blast reaches a flier (`colony-breach-nouns.md`, Harrier), so the director counts a concentration by its bodies on the ground (`EnemyConcentration.densestOnGround`): a Harrier pack loitering overhead is no target, and a pack over a few bodies is those few bodies.
- **Forward observer.** A soldier carrying the radio, as the military issues them: one to each **post**, a formation the mission names (a redoubt's garrison, a lane's column), carried by a soldier other than the leader of the first of its squads on the map with a hand free (`ForwardObservers`). A post is issued once; an observer killed leaves that formation with no eyes for the air for the rest of the battle. The Rav Bloom battles give the world's military an **allied air wing** (`AlliedAirWing`): an `ALLY` pool of two bombers and two fighters, replaced every minute and every forty-five seconds (a shot-down airframe is missing like any other, so the wing refills through a long battle but never flies more than its establishment at once), that its own director tasks through those observers alone, flying in over the ground behind the allied line the mission names as its home (a side with no landing zone or keep would otherwise come in over its force's centre). The officer judges anti-air only from what the side has sighted: a mass among spore colonies it has seen is left to the ground, one whose colonies nobody has seen costs aircraft. Which formations carry observers is each mission's: `cordon-nouns.md` and `heart-nouns.md`. The militia, townspeople and the company bring no radio onto that net.
- **Roving observer.** A two-man team (an observer with the radio and his escort) the military sends up from its rear now and then (`RovingObservers`), walking to the best **vantage** the mission names from what the side knows (`BloomVantages`: a redoubt's call, a lane's squads in contact), never from where the swarm is, and not within twenty-five cells of another observer. It watches there and moves on: after seventy-five seconds, or once it stands at its vantage with the swarm within seven cells or fire on it heavy enough to stop the radio, but never sooner than twenty-five seconds after it set out, so a team in a running fight is not forever on the move. Rare by rule (owner: five to ten observers at most): a team comes up only while the side has fewer than eight observers on the map, **fixed posts and roving teams counted together**, and fewer than three teams rove; the first ninety seconds in, the next no sooner than two and a half minutes after. A team whose observer dies is gone. The Cordon and Broad Front send them; the Burn does not.
- **Anti-air.** The area drain over a turret cluster and real AA positions both hurt a pass, and a believed-defended point is judged by `AaRisk` from the guns the side has actually sighted: a strike is declined when the expected loss is more than an airframe. A fort's massed guns are left to the ground force; a lone nest is bombed out, but never an anti-air mount (a flak or missile emplacement, a spore colony): what shoots aircraft is the ground force's, and a bomber sent straight over one is the trade it exists to win. A dedicated anti-air mount counts wherever its own weapon reaches, not only inside the bubble, so a maw-grade spore colony twenty cells off is part of the estimate once seen. An unsighted gun is a surprise, but the side sees its own aircraft fall: for three minutes the ground within twenty cells of where it lost one is believed worth a whole airframe more, so a second sortie is not sent where the first just fell unless nothing else is believed there. A fort may also carry an optional **anti-air yard** (`forts.md`): Locust batteries and Swarmer towers on the bastions whose real reach, not the fourteen-cell bubble, is what the estimate counts once the fort's works are sighted. It shoots only aircraft, never hulls; its console wrecked, its fort's power lost or the yard taken changes what it does and what the officer believes. The command layer treats a yard that shuts the sky as the first work of a plan that wants an air strike (`AirSupportFacts.blocked`). Sorties are declined over a known yard, not routed around it.
- **On screen.** The player's battle HUD carries an air plate under the top-right command rail whenever the player's side holds a pool, and nothing when it does not: airframes available against the establishment for each kind, how many are in the air and on what errand (strike, strafe or cover), the clock to the next replacement, and what has been lost, with the last minute's losses called out. Fair knowledge only: it shows the player side's own pool, never the enemy's. It reads an immutable `AirPoolView` taken from the pool and changes nothing; a sortie's errand comes from the one place the pool names it, so a new mission type shows on the plate by being recorded there.
- **Reading.** The capability profile reads availability and risk from `AirStrikeCapability`, so a plan may name an air strike as an asset; roles are matched in `phased-assault.md`.

UNTUNED: cadence, regeneration, ordnance and the AA mix are first-pass figures.

## Standing laws

- There is one entity world and one id authority. Air versus ground is expressed
  by component membership, never by a private air registry or duplicate storage.
- A craft's body is the authority for motion. Rendering, weapons, effects, and
  any grid-derived representation must read or synchronize from it rather than
  keep competing positions.
- **A flying craft's arrival tolerance is derived from what the craft is doing,
  never authored as a distance.** An arrival gate is a distance a craft has to
  be sampled inside on some tick, and there are two ways a craft never is. It
  steps clean over a gate narrower than one tick's travel. And it settles into
  an *orbit* around a point inside its own turning circle — about a radius out,
  about ninety degrees off the bearing to it, indefinitely; a Broadsword sent to
  a landing zone at the widened turn radius circled it three and a half cells
  out at four cells a second and never got closer. Neither is a craft that is
  nearly there. Authored numbers are floors; the gate admits the widest of the
  floor, the step, and the circle. Both derived terms read the body's *current*
  speed rather than the hull's maximum, because half these arrivals are flown
  braked and a max-speed bound would land a carefully braked transport most of a
  cell short of its pad — so both close as the craft slows and neither moves a
  braked arrival. This is what makes the atmosphere calibration re-dialable at
  all: the floors are a transport's, and a fighter several times faster steps
  over every one of them. Ground tolerances stay separate and stay authored — a
  wheeled aircraft can be asked to hold short of a point and does.
- **The circle is admitted for a craft steered at a point and never for one
  flown along a path.** An orbit is what a body does when it is aimed at
  something inside its own turning circle. A craft following a carrot that
  slides along a solved path and runs on past its destination cannot do it, so
  the term buys nothing there and costs exactly its own width — and its width is
  a runway. Gated on the steered rule, a fighter's landing fired seventeen cells
  short of the numbers: the wheels went down at x=79 on a strip that ends at
  62.5, and an arrival from the other end put them down at x=-9.6, off the map.
  Both were correct to within two degrees of heading, which is why it went
  unnoticed — a landing is a pose, and half of one being right proves nothing
  about the other. Where an arrival is flown rather than steered the gate is the
  step alone, and the real capture is the geometry the path was solved to:
  crossing the threshold.
- **An aircraft is one entity for its whole life, and what changes is how it is
  moved.** Grounded, managed and free flight are three sets of physics over one
  body, swapped at phase boundaries; the phase is the single source of truth for
  which one has it. Nothing is placed at a boundary between them — a landing
  that ends in a teleport is a handoff pretending to be a manoeuvre, and every
  arrival gate, descent and rollout downstream of it was tuned against a
  discontinuity rather than against the aircraft.
- Runtime hull specifications are the shared, mod-aware source for hull facts.
  Hand-authored exceptions require a concrete non-standard craft, not routine
  per-hull tuning.
- Visual and simulated attachment points share the centre-of-gravity frame.
- Footprint and movement have separate responsibilities. The current linear
  calibration is seeded from the shared density, so re-dial it with deliberate
  visual-and-motion review; never use a per-hull footprint adjustment to change
  a craft's movement.
- Dense storage or performance work follows measured fighter-swarm pressure;
  it is not a prerequisite for a small air population.
- Air capabilities are opt-in. A transport does not accidentally participate in
  occupancy, infantry targeting, victory counts, or ground AI merely because it
  exists in the common world.

## Boundaries and extension paths

`command-powers-nouns.md` owns the player's commitment of fighter cover and
other support; Air owns how committed craft exist and behave in the simulation.
`convoy-nouns.md` is the ground-vehicle sibling: both use data-driven bodies and
steering, but neither is an implementation template for the other.

`vanilla-combat-bridge` owns the seam where a real vanilla combat host and the
ground simulation interact. It may host external air, but does not create a
second internal air model. `battle-render` owns the eventual camera-Z and
view-projection work that gives altitude its shared presentation space.

Ship collision, wing composition, air-to-air, modules, and air persistence stay
as extension paths until each has a bounded story and an identified gameplay
need. `fighter-air-entities.md` carries what is left of the fold.

### Survivor pickup

`SurvivorPickupSystem` can be configured with a faction and pickup cell before
deployment, then requested independently of any mission objective. It retains
the actual deployment craft's fitted frame and lift role, waits for delivery to
finish, and returns as many surviving boats concurrently as the remaining
passengers need. Each aircraft has at most one trip active, and destruction
removes that aircraft alone even when another boat flies the same hull pattern.
Infantry lifts honor physical seat capacity; mech lifts carry one chassis. No
extra company boats are minted. Separate reachable outdoor ramps stay inside
the safe pickup area; when no free ramp fits, that boat waits for space.
Units with withdrawal orders reserve seats on individual landed craft and walk
to their assigned ramps. Each boat boards independently at 0.8-second intervals,
with non-leaders at its ramp boarding first. A lift departs
when full, when nobody eligible remains, or after 90 seconds on the pad; later
trips collect those left behind.

A `PickupManifest` distinguishes aboard, recovered and lost passengers. Boarding
removes the ground entity without producing a corpse, preserving campaign ID
and combat telemetry. Ordinary aircraft return and destruction resolve the
manifest; destruction records passenger losses in the boat ledger where owned,
without counting those losses twice. Mission settlement includes off-field
survivors and their career evidence. Extracted survivors prevent a false wipe,
but only physical return resolves a mission's extraction requirement. The
mission, not this transport service, decides when bombardment is authorized.
