# Air — nouns and model

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-08-30 — a strip is published geometry rather than paint, and a base can have one.

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

A **runway** is the second kind of place an aircraft can leave from, and the
first that is not a square of ground. It is the centreline a craft rolls along
and its two thresholds, published by the lot that laid it rather than recovered
by scanning for runway-coloured ground: the lot knows exactly where it put the
strip, and which ground kind stands in for runway is an art decision that has
already changed once without touching a line of generation. Geometry a system
depends on cannot live in the art. Neither threshold is privileged — which end
a roll starts from is a decision for the sortie, and the far one is normally
right because it leaves the aircraft pointing where it is going. `mapgen-nouns.md`
owns where a strip is laid; Air owns what happens along it. See
`runway-airbase.md` for the ground procedure being built on it.

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

Fighters are recurrent atmospheric passes. Their hull-derived handling already
drives real bodies, preserving interceptor-versus-bomber contrast instead of a
scripted constant-speed glide. They are not yet fully composed air entities:
the legacy flyby presentation still owns their roster, lifecycle, firing, and
rendering. The active fighter story moves those responsibilities into the air
model while preserving the established loadout, faction-pool, and strafing
semantics.

A wing is a gameplay commitment, not merely a visual density setting. Formation,
count, role, and carrier relationship are future wing-level semantics to add
when the entity transition gives them a durable home. Fighter survival and
air-to-air/anti-air interactions are separate future capabilities, not assumed
by the current cycling-pass behavior.

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

The next concrete work is `fighter-air-entities.md`. Ship collision, wing
composition, anti-air, modeled fighter fire, modules, and air persistence stay
as extension paths until each has a bounded story and an identified gameplay
need.
