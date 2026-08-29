# Story — Aircraft based on the airfield

Status: PLANNED

Written: 2026-08-29

Read `air-nouns.md` and `reinforcement-nouns.md` before changing this story. An
air craft is an air entity: air identity, kinematics, a mission, and an
appearance, and deliberately no grid or combat components. That exclusion is
what this story has to work around, and it is not up for negotiation — it is
why every grid walk in the battle can skip air for free.

## Current substrate

The garrison airfield is authored geometry and nothing else. `FortressAirfield`
paves an apron, marks hardstands, and publishes one `AIRBASE` tactical node;
`LandingPad.Purpose.GARRISON_AIRFIELD` is read in exactly one place in the whole
codebase, `ShuttleMeans`, which keeps the pad list to pick a sortie's entry and
exit.

Nothing is ever parked on those hardstands. A sortie's aircraft is conjured on
the pad at dispatch, loads, flies the delivery, returns to the pad coordinates,
and — because `ShuttleMeans` sets `totalCycles = 1` — goes `GONE`, and
`reapGoneCraft` destroys the entity at end of tick. Between sorties the field is
bare. What reads as a based aircraft today is a craft mid-`LOADING`; it looks
persistent only because it spawns *on* the pad instead of flying in.

A parked craft also cannot be hurt by anything. Air damage is a single path:
`tickAirThreat` drains HP from enemy *defense posts* in an area bubble, with
infantry and mechs explicitly excluded, and only in the `INCOMING`,
`HOVER_STATION`, and `DEPARTING` states. A craft on the ground is exempt by
design, and no marine has ever been able to shoot one.

## Goal

The airfield holds real aircraft. They stand on their hardstands where they can
be seen and shot, they are what a sortie flies out and brings home, and killing
them ends defender air for the battle without the attacker having to take and
hold the ground.

This is a second, independent way to end air delivery. Taking the `AIRBASE`
compound already does it through the supply gate. Destroying the airframes does
it without holding anything — and holding the field with every aircraft burning
should be exactly as useless as it sounds.

## The load-bearing decision: two representations, not one

A parked aircraft must be perceived, gated by fog of war, traced against line of
sight, hit, attributed, killed, and wrecked. Every one of those is a grid/combat
concern, and the air entity has none of those components on purpose.

**Reject:** teach the combat stack to see air. Perception, LoS, targeting, and
damage resolution would each grow an air-aware branch to make three aircraft
shootable, and the "grid walks skip air for free" property is lost for good.

**Adopt:** a parked airframe is an ordinary grid unit; a flying one is an air
entity; a launch and a landing are handoffs between them. The parked side then
inherits perception, fog gating, cover, LoS, damage attribution, death, and
wreck FX without a line of new combat code.

The shape already exists. `DRONE_HUB_STRUCTURE` is a static structure that is
"combatant so marines target and damage it" while `UnitRole.STRUCTURE` keeps it
out of every aim loop, with its sprite and HP set per instance and the parent
`UnitType` carrying an empty sprite path — the `MapTurret` convention. A parked
aircraft is that, with a hull instead of a launcher.

**The handoff itself is the novel work and the main risk.** There is no
precedent for it: `DroneHub` spawns grid units from a grid unit, so nothing in
the codebase currently converts between a grid unit and an air entity in either
direction. Everything else here is assembly.

## Decisions

- A new `UnitType` for a parked airframe, following the empty-sprite-path
  per-instance convention; the instance takes its sprite and hull size from the
  `ShuttleType` it represents. `UnitRole.STRUCTURE`: it never aims and never
  fires. A based aircraft is a target, not a weapon.
- It occupies its hardstand's centre cell only. The berth law from the airfield
  work stands — a hardstand is a clear footprint — and the crew has to be able
  to walk to the ramp, so the aircraft must not wall its own pad off.
- An **airfield service** owns the berths: which pad, which airframe, airworthy
  or destroyed, present or away. `ShuttleMeans` stops holding a pad list and
  asks the service instead.
- `canFulfill` gains an airframe gate beside the existing supply gates. The two
  airfield conditions are genuinely different questions and both are asked: the
  compound must still be defender-held, *and* there must be an airworthy
  airframe on it.
- Hull damage survives the handoff in both directions. A craft that comes home
  shot up parks shot up, and is destroyed on the ground by that much less fire.
- A destroyed airframe is gone for the battle. No respawn, whether it burned on
  its pad or was shot down over the objective. That is the stake.
- Internal air only, mirroring `requireInternalAir`. On a hosted battle where
  the game owns the air there is nothing for a berth to hold.

## Slices

Each ships on its own and is worth having alone.

1. **Berths and airframes.** The airfield service, plus a parked airframe
   spawned on each garrison hardstand at setup: visible, fog-gated, targetable,
   destroyable, and leaving a wreck. Sorties still conjure their craft as they
   do now. Complete on its own — a fire team can shoot up a field and see it
   burn.
2. **Launch handoff.** A dispatch consumes a berthed airframe: the grid unit is
   removed and the air entity takes its place at the same pose and hull state,
   and the berth is marked away. `canFulfill` gates on an available airframe, so
   a burned-out field stops answering requests.
3. **Return handoff.** The exit leg lands on its own berth and becomes a parked
   airframe again, carrying its damage. A shoot-down or a scrub leaves the berth
   empty for good.
4. **Turnaround.** A refit timer on the berth before an airframe is available
   again, so a field under pressure cannot cycle a shuttle instantly. The
   existing `DEPARTING` recycle already models a full refit as free and
   instantaneous; this is where that becomes a cost paid somewhere the attacker
   can reach.

## Acceptance

- A unit test per handoff direction: an entity count that does not leak or
  double, hull damage that survives the crossing, and a berth whose state
  matches what is on the map.
- A unit test that a destroyed airframe is not replaced, and that a field with
  none refuses a sortie while its compound is still defender-held — the two
  gates proven independent.
- A scene, extending `AirfieldSortieScene` rather than replacing it: a marine
  team walks onto an undefended field and burns the aircraft on their pads, and
  the next reinforcement request goes unanswered. The existing pair already
  records the crew's walk under fire; this is the same field from the other
  side.

## Out of scope

- Anti-air against a parked craft. Ground fire kills it; the AA bubble stays a
  thing that happens to aircraft in the air.
- Marine-side based aircraft. The airfield is a defender installation, and the
  marine side has no field to base from.
- Repair or replacement of a destroyed airframe from campaign stock. The battle
  is transient and the loss is meant to be permanent within it.
- Aircraft on hardstands other than a garrison airfield's.
