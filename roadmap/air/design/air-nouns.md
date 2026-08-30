# Air — nouns and model

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-08-30 — where a round lands is delivery physics, not a lead dial; a gun run can be seen and heard; a sortie has an origin, and air cover flies in off the map; an aircraft on its wheels is driven rather than flown; a fighter flies a wide banking circuit and a flying arrival tolerance is derived from what the craft is doing.

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

**Air craft** is the broad category. It has a continuous **body**: position,
facing, velocity, and motion state. A body is a kinematic fact, not a behavior
or a visual approximation. Steering systems supply goals and modes; the body's
handling determines the resulting turn, acceleration, drift, and stopping
shape.

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

**Air appearance** is authored render state such as flight phase and apparent
altitude. It is not a second physics body. Visual scale, offset, and engine
intensity are derived from that state and the body, keeping the simulation and
the rendered craft anchored to the same actor.

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

The split is deliberate and load-bearing. An air entity carries no grid or
combat components, which is what lets every grid walk in the battle skip air
for free; a parked aircraft, meanwhile, has to be perceived, gated by fog,
traced against line of sight, hit, attributed, killed and wrecked — all
grid/combat concerns. Teaching the combat stack an air-aware branch in each of
them would buy a handful of shootable aircraft at the cost of that property
forever. Being a unit on the ground buys the same behaviour for nothing. The
unit is a target and never a weapon: it is a structure, so it neither aims nor
fires, and what it does is stand there and be worth shooting.

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
and says nothing about what a rifle section does to one parked on concrete. It
follows drawn size, because on the ground the only thing that matters about an
aircraft is how much of it there is, and the whole fighter ladder sits below
the lightest transport.

Every berth is on the apron, in the open. The base's hangars are where aircraft
are worked on rather than where they wait, so an attacker who reaches the field
can burn what is standing on it without going indoors — the exposure is the
point, and it is what makes a raid on the field a real alternative to taking the
compound. `mapgen-nouns.md` owns the lot's geometry.

Four rules give the field its stakes:

- **The hull is continuous.** An aircraft that comes home shot up parks shot up,
  and is written off on the ground by that much less fire. A sortie flies the
  hull that was standing there, not a fresh one conjured at those coordinates.
- **Loss is permanent.** An airframe burned on its pad or lost over the
  objective is not replaced, and its berth is written off for the battle. A
  field is a finite thing to lose.
- **A turnaround is a window.** Servicing used to be free and instant because it
  happened off-map at a carrier nobody could reach. On a field it happens on
  ground the attacker can walk onto, so it takes long enough that a field cannot
  answer two requests back to back.
- **An airframe destroyed on its stand goes up.** It is a full tank under a thin
  skin, and that is the whole reason burning one is worth a fire team's time; a
  hull that simply stopped existing was a target with a lot of hit points and
  nothing else. It leaves a fireball, a burning wreck, and a blast that catches
  whoever is beside it — the raiders who walked onto the apron included, since
  fire does not check anybody's colours, and the ground crew that came out to
  fly it. Recorded: burning three aircraft from four cells away cost about half
  a six-man fire team. Riflemen out-range that comfortably, so the price is for
  standing on the apron rather than for the raid.

The fire **does not chain**. Its reach is sized to the stand and the apron
around it and stops short of the next hardstand, which an authored field puts
eight cells away. A blast that took its neighbours with it would make one
satchel worth an entire airfield and delete the only decision a raid contains,
which is how much of the field to spend the visit on.

**The wreck stays on the concrete.** What the fire leaves is the aircraft's own
hull, charred and in three pieces, lying at the place and bearing it was
standing, for the rest of the battle. The smoke that marks a fresh kill burns
out in half a minute, and with nothing permanent behind it a burned field looks
exactly like a field whose aircraft happen to be away, which is precisely the
question a raider walked over there to settle. It is drawn off the berth rather
than off the airframe, because the airframe is dead, released and gone by the
time anybody looks at the pad again, and the berth is the thing that outlives
what stands on it. An aircraft lost over the objective leaves an empty stand:
the same terminal state, and deliberately not the same picture.

**A hull comes apart along a V.** The nose section separates as a wedge and
what is left splits down the spine, both tears walked so the edges are ragged
and ragged differently for every hull on the field. The pieces shift and turn a
little where they lie — far enough that the tears open and the wreck reads as
three things, near enough that it still reads as one aircraft, and never far
enough to be debris thrown across the apron. This is drawn out of the
aircraft's own sprite rather than from wreck art, because these are the game's
hulls and nothing may edit them; the tear follows a lattice for the same
reason, since a lattice boundary is addressable as ordinary source rectangles
and a curve would need per-pixel masking that is not available. See
`HullBreakup`.

The wreck is an obstacle, and **only** an obstacle. Nobody walks through it;
everybody sees and shoots straight across it. A non-walkable cell is opaque
here unless it says otherwise, so the wreck says otherwise — a burnt-out
airframe is a frame with holes in it, and an apron strewn with them is still an
apron you can cover by fire. That is deliberately not how the intact scenery
hulls dressing civilian berths behave: a whole aircraft is a solid object.

**A wreck never settles on top of somebody.** Whoever is standing where the
hull comes down — the ground crew who walked out to fly it, the raider who
walked out to burn it — steps clear to the nearest cell that will take them,
and a cell nobody could be stepped out of is left open instead. A unit sealed
into a cell it can never leave stops answering its orders for the rest of the
battle, which is a far worse outcome than a hull with a gap in it.

A sortie's passengers are never at risk from this. An aircraft is taken off its
berth at the moment the request is dispatched, before the crew starts walking,
so the airframe standing on a pad and the crew walking toward it are never on
the field at the same time; the craft they board is an air entity that ground
fire cannot reach. Should a loading craft ever be made shootable, it owes its
passengers a disposition, because they have already been taken off the roster.

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
owns where a strip is laid; Air owns what happens along it. See
`runway-airbase.md` for the ground procedure being built on it.

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
shoulder, two survive one strike; dispersed five cells apart, ten do.** Both
halves of that are the design — a run that killed everyone regardless would
make dispersal pointless, and one that killed nobody would make the airfield
pointless.

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
  of its thrust, and spends a second and a half falling — and because it keeps
  *most* rather than all of that speed, the aircraft is past the impact by the
  time it happens. Bombs land behind the machine that dropped them, and nothing
  told them to.

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
apart: a beam lands **about three quarters as wide across the run** as a cannon
does (1.8 cells against 2.4), which is the difference between painting a line
and throwing craters. Along the run the classes separate further — a cannon
pass works the ground from **eleven cells short of the target to nine past**,
and a missile pass from **sixteen short to ten past**, opening from further out
and finishing in the same place.

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

**A cadence is not a volume.** A rotary cannon releases fourteen rounds a
second and a beam twenty-four; a clip per round is not a louder gun, it is a
wall of overlapping voices in which nothing else in the battle can be heard.
Fire cues are thinned to a cadence that reads as a burst, and the beam — which
is one continuous sound rather than a series of events — is a held loop left to
lapse when the firing stops.

Every clip is one of the base game's own mono weapon files, registered under our
own id against the read-only install rather than copied, the same way the
vehicle engine loops resolve. Nothing is redistributed.

A **strike sortie** is the reason a station has sheds. An armed aircraft
leaves on the field's own decision rather than on a request for passengers,
works its target, and comes home to the shed it came out of. Its business at
the objective is its guns rather than its ramp, so unlike a transport it never
touches down on what it was sent to attack — it arrives on station, which is a
wider thing than arriving on a cell.

The field flies **one at a time**, on an interval. A garrison that scrambled
its whole air arm at first contact would spend itself in the opening minute and
have nothing left for the assault the field exists to answer, and the single
sortie is what makes air a recurring threat instead of one event. The target is
the **densest** enemy concentration rather than the nearest or the largest:
nearest sends aircraft after whichever scout wandered closest to the fence,
largest picks the same push every time, and density is both what an aircraft is
good against and what a player can see the reason for afterwards.

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

**And it really is shootable.** Air used to be reachable only by defence posts
and only while airborne, which meant the minute of open ground a strip buys was
a minute of complete safety — a fighter taxiing past a fire team was in no
danger whatsoever, and the trade the runway exists to make was a fiction. An
aircraft on its wheels is a large slow object in the open and anything with a
weapon can engage it, at rifle reach rather than through an anti-air bubble,
and harder per shooter than a post manages against something flying. A loading
craft stays exempt: its passengers have already left the roster and making it
shootable would owe them a disposition nothing gives them.

The same list is what an anti-air post reads, so a phase left off it is a phase
nothing can touch. Replacing the armed loiter with attack runs did exactly that
and made every strike invulnerable while it attacked.

A craft that has to roll has a **ground procedure** either side of its flight,
and it is on its wheels and shootable for all of it: out of the shed, down to
the threshold, a wait if the strip is busy, and then the roll itself, which is
the only part where the aircraft is accelerating and not yet flying. Coming
home it is the same in reverse — touch down at the end it reaches first, roll
out to the far one, turn off, taxi back to its own shed.

**A landing is captured, not flown.** The approach is two legs: out to a point
on the extended centreline, which may be reached from any direction, and then
down it to the threshold — and because that second leg *is* the runway axis,
the aircraft is lined up on arrival without anybody testing its heading. At the
threshold the simulation takes the aircraft over: it is put on the centreline
pointing along it and the rollout is driven from there, with the nose held on
the strip for the whole of it. Asking the steering to brake a flying body onto
a point left craft arriving crabbed and pirouetting on the runway to sort
themselves out.

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

The strip itself is a **resource with one occupant**. Two aircraft rolling down
one runway is not a race the simulation is entitled to lose, and the queue that
falls out of it is the point — a field with three aircraft and one strip
launches them in sequence, so anything sitting between a shed and the threshold
delays every one of them. A craft holds the strip from the moment it starts its
roll until it is airborne, and through a landing rollout, because it is standing
on it; releasing is tolerant of a craft that never held it, since a strip left
claimed by an aircraft that no longer exists closes the field for the rest of
the battle.

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

A sortie flown from an **authored airfield** has one more phase in front of
that. The craft starts down on its own hardstand and **loads on the ground**:
its passengers are not aboard when it spawns, but walk out to the pad and
embark, and it lifts when it is full or when nobody else is coming. That is the
difference between an air arm and a spawner. A sortie that arrives already
loaded has no cost and no story — the aircraft is a delivery mechanism that
happens to be drawn. One that has to be loaded has both: the garrison commits
people who can be seen and shot, they cross open ground to reach the field, and
an attacker standing on the airfield — or merely shooting across it — has
stopped the lift without touching the aircraft.

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
each new sortie marches another four out to join them. `LOITER_IF_ARMED` preserves bounded fire support;
`DEPART` takes off immediately even when the hull has weapons. It is an air
entity throughout that lifecycle, not a temporary handle or a parallel id
space. Transport survival, payload delivery, and optional mounted fire support
are role capabilities; they do not make the craft a normal grid combat unit.

`ShuttleType.capacity` is the hull maximum. `ShuttleAssignment.seatsPerSortie`
is the actual manifest and is restored on every cycle. Arrangement, shared
arrival area, squad grouping, and departure behavior belong to the mission's
arrival policy rather than to the hull type.

The Aeroshuttle is a purpose-built six-seat half-squad craft. Conquest keeps
the committed campaign hull as its lift source but resolves the visible final
descent to ownership-separated paired Aeroshuttles. Its mission configuration
owns the active zone count, reusable player pairs per zone, and timing jitter.
Those final-descent craft are reusable: committed lift and selected personnel
become additional cycles, not additional permanent landing berths. Seeded
per-craft launch and re-arm offsets prevent lockstep flight without weakening
deterministic fixture replay.

### Fighters and drones

Fighters fly the same sortie as anything else the air model puts up. A
`FighterProfile` is an airframe: it says what the aircraft looks like, which
hull sizes and flies it, what it drops, and how much of it there is to shoot.
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
a wing is today is a commitment: a profile, a side, and how many sorties arrive
when.

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
aviation. `AirCoverSystem` is the dispatcher and the wing's own schedule is the
cadence.

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
turns for home when the passes are spent — the same rule and the same code as a
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

Ships are larger air craft whose decisive ground interaction must be their
actual hull silhouette. An on-map implementation first rejects ground fire
against the hull's broad radius and then resolves it against the rotated
concave authored polygon; a hit belongs to the true contact point, not an
enclosing circle or box. That fidelity is the standing contract, not a claim
that overhead ships are implemented today.

Size determines which ships may be on-map. The shared density ladder makes
fighters and smaller ships plausible at ground scope; very large capitals are
off-map/orbital support rather than giant polygons forced into a small battle.
Modules, cumulative ship damage, and ground-weapon ceiling rules remain future
decisions. Altitude is ultimately a shared camera-space axis, never a fake
shrink that changes a hull's physical scale.

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
