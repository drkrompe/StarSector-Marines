# Story — An airbase that flies fighters off a runway

Status: ACTIVE

Written: 2026-08-30

Read `air-nouns.md` before changing this story. A based aircraft is two
representations of one thing — an air entity in flight, an ordinary grid unit on
the ground — and everything here happens at the handoff between them.

## Why

`AirbaseLot` has laid a runway since it was written. `Size.FIELD` puts four rows
of strip along its approach edge, an apron with hardstands behind it, hangars
with aircraft-sized openings at the back, and a taxiway between them, and
`FortressWardStage` prefers that size — so fortress wards have had runways on
them all along. Nothing has ever used one. A based aircraft lifts vertically off
its hardstand, and the strip is paint.

That is the right model for a shuttle and the wrong one for a fighter. A field
that answers a request by conjuring a vertical lift off a marked square has no
procedure an attacker can interrupt: the aircraft is airborne the moment it is
asked for. A field that has to move an aircraft out of a shed, down a taxiway,
onto a strip and along it before anything is flying has a minute of ground
movement in the open, and every part of that minute is somewhere a raider can be
standing.

## Scope

A new airbase variant that bases armed aircraft, and the ground procedure that
gets them into the air and back.

1. **The runway becomes a noun.** `AirbaseLot` publishes the strip it already
   paints — centreline, thresholds, width — the way it already publishes berths.
   Derived from the lot's own geometry rather than recovered by scanning ground
   kinds, because the lot knows exactly where it put it.
2. **A larger lot variant** beside `FIELD`/`PAD`/`STRIP`: a longer strip, more
   hangar, and berths **inside the sheds** rather than out on the apron. A
   fighter lives in a shelter and is worked on there; the apron hardstand is a
   transport's parking space. Reachable through its own `MapFeature` so a
   mission can ask for one.
3. **Taxi, roll, rotate — and the same in reverse.** New sortie phases between
   a berth and flight: out of the bay, along the taxiway, hold at the threshold,
   accelerate down the centreline, rotate. Inbound: align to the strip, touch
   down, roll out, taxi in, shut down in the bay it came from.
4. **The strike sortie.** An armed based aircraft that launches on a trigger,
   makes its passes, comes home and lands, taxis in, and refits.

## Decisions

- **Based strike aircraft first, flyby fighters folded onto the seam after.**
  Fighters today are the `battle.flyby` shell's private roster, and
  `fighter-air-entities.md` is the story that moves them into the air model.
  Building this on the existing based-aircraft substrate — air entities,
  `AirTurrets`, `LOITER_IF_ARMED`, the fire-support window — ships the runway
  without waiting on that migration, and gives the migration a landing point
  that already works. Until it lands, these are **based strike aircraft** and
  the flyby's are **fighters**; the names stay distinct on purpose so the
  duplicate is visible rather than quietly permanent.
- **The runway is a shared resource with one occupant.** Two aircraft rolling
  down one strip is not a race the simulation should be allowed to lose. A
  sortie holds short until the strip is free.
- **Ground movement is the air entity at zero altitude**, not a grid unit. A
  taxiing aircraft is already the shape the `LOADING` phase established: down,
  engines running, steering. Making it a grid unit for the taxi and an air
  entity for the roll would need a handoff in the middle of a continuous
  movement, which is exactly the seam `air-nouns.md` keeps to a standstill.
- **A runway lot does not stop being an airfield.** Vertical-lift transports
  keep using the apron hardstands on the same base. A field with a strip has
  both kinds of aircraft on it, which is what makes the strip a distinct thing
  rather than a replacement.

## Acceptance

- A lot with a strip publishes exactly one runway with its true centreline,
  thresholds and width; a lot without one publishes none.
- The new variant places berths inside hangar bays, keeps the apron hardstands,
  and satisfies every standing `AirbaseLot` law — fence gated on each side,
  clearance reserved, nothing standing on a berth.
- A based strike aircraft leaves its bay, taxis, rolls, and is airborne only
  after it has run out the strip; it is shootable on the ground for the whole
  of that, and killing it there kills it.
- An aircraft coming home lands along the strip and taxis back to a bay; a
  sortie that cannot reach one does not strand the aircraft on the runway.
- Two aircraft launching together do not share the strip.
- Animated evidence of a full cycle, and of the same cycle interrupted by a
  fire team on the taxiway.

## Out of scope

- Air-to-air, anti-air against the strike aircraft in flight, and modeled
  fighter fire — `air-nouns.md` keeps those as separate Air-owned follow-ups.
- Wing composition from `wing_data.csv`.
- Retiring `battle.flyby`; that is `fighter-air-entities.md`, and this story only
  has to leave it a seam worth landing on.

## Plan

| Slice | Work |
| --- | --- |
| 1 | `Runway` published from `AirbaseLot` through `GenContext`/`MapResult`. No behaviour. |
| 2 | The new lot variant: hangar berths, longer strip, `MapFeature`. |
| 3 | Taxi and roll: the sortie phases, the runway as a held resource. |
| 4 | The strike sortie: armed based aircraft, trigger, passes, recovery. |
| 5 | Evidence: a runway loop in the airfield scene, and an interrupted one. |
