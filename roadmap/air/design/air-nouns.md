# Air — nouns and model

Status: ACTIVE

Written: 2026-08-23

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
disposition. `LOITER_IF_ARMED` preserves bounded fire support;
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
descent to paired Aeroshuttles.

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
