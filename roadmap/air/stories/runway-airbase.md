# Story — An airbase that flies fighters off a runway

Status: ACTIVE

Written: 2026-08-30

Updated: 2026-08-30 — a berth holds an airframe; a station's sheds are factional.

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
2. **A larger lot variant** beside `FIELD`/`PAD`/`STRIP`: a longer strip, a
   third shed, and a wider taxiway. Its aircraft live **in the sheds**, which is
   what separates it from a field — a fighter is kept in a shelter and worked on
   there, and the apron hardstands beside it stay what they always were, a
   visitor's parking. Every shed on every size publishes the bay its aircraft
   stands in, because the bay is on the map either way.
3. **Taxi, roll, rotate — and the same in reverse.** New sortie phases between
   a berth and flight: out of the bay, along the taxiway, hold at the threshold,
   accelerate down the centreline, rotate. Inbound: align to the strip, touch
   down, roll out, taxi in, shut down in the bay it came from.
4. **The strike sortie.** An armed based aircraft that launches on a trigger,
   makes its passes, comes home and lands, taxis in, and refits.

## Decisions

- **A fighter is a `FighterProfile`, and there will not be a second list of
  them.** Vanilla fighters are factional, and the project already has that:
  `FighterProfile` carries the five hulls with their sprites, hull ids and
  weapon classes, and `FighterProfile.poolForFaction` already sorts them — a
  Hegemony or Luddic or pirate field flies Talons and Broadswords, a
  Tri-Tachyon or Remnant one flies Wasps and Thunders. Adding fighter hulls to
  `ShuttleType` so a station could base one *today* was considered and
  rejected: it would put the same five aircraft in two enums, which is exactly
  the duplicate this story promised not to build. A station therefore bases the
  transport substrate as a stand-in until the fold lands, and the fold is what
  makes its aircraft factional.
- **The berth holds the smaller thing, not a copy of the bigger one.** Making
  a berth able to keep a fighter did not need `battle.flyby` folded into the
  air world first. What a berth actually asks of an aircraft is a sprite, a
  hull that sizes it, and structure to shoot at — three methods, which both
  `ShuttleType` and `FighterProfile` can already answer. `Airframe` is that,
  and it is deliberately narrow: capacity, guns and flight handling are asked
  of the concrete type by whoever needs them. The `fighter-air-entities.md`
  fold is still worth doing and is still about moving the overlay's roster,
  lifecycle, firing and rendering into the air world — it simply is not a
  prerequisite for a field that bases fighters.
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

### The runway path is reached on every conquest map

Worth writing down because it was nearly measured wrong. A sweep of 24 seeds at
240x168 found **no runway at all** and one or two sheds per map, which reads as
the whole feature being unreachable. The maps were the wrong size: conquest
generates at `MapScale.LARGE`, 280x168, and the fortress ward is cut from the
band's width. At the real size every one of those 24 seeds lays exactly one
strip and three or four shelters — the station's three plus whatever a
city-landmark pad contributes. Measure a generation question at the size the
game generates at, or the answer is about a different map.

### Where a station actually fits

Two things had to change, and the second was found by measurement rather than
reasoning. `FortressProgram` sized the ward from `AirbaseLot.area(FIELD)`, so
the reservation was cut to the base it expected and a larger one could never be
offered ground it would take; it asks for a station's area now. That alone
changed nothing, because **the ward comes out about 250 cells wide and 29
deep** while the band it is cut from is 45 deep. A station three rows deeper
than a field was refused on depth on every map, with 190 cells of unused width
beside it.

So a station grows **along the frontage and not backwards**. Depth is the scarce
axis and the only one a bigger base can realistically be refused for; length is
what a strip wanted in the first place. The cost to everything else on the map
is nil within noise — measured over six seeds, walkable ground moved from 41104
to 41095 cells and the landing-pad count did not move at all, while the ward's
strip went from 41x4 to 55x4 and its sheds from two to three.

| Slice | Work |
| --- | --- |
| 1 | `Runway` published from `AirbaseLot` through `GenContext`/`MapResult`. No behaviour. **Shipped.** |
| 2 | `Size.STATION`, and every shed publishing the shelter its aircraft lives in. **Shipped.** |
| 3 | Taxi and roll: the sortie phases, the runway as a held resource. **Shipped.** |
| 3b | A host that reserves ground for a station, so the variant is reachable. **Shipped.** |
| 4 | Shelter berths registered at setup, on a field with a strip. **Shipped.** |
| 4b | A berth holds an `Airframe`, and a station's sheds hold the defender's own fighters. **Shipped.** |
| 4c | The strike sortie: `AirStrikeSystem` decides, an armed fighter rolls, works its target without landing on it, and comes home. **Shipped.** |
| 5 | Evidence: a runway loop in the airfield scene, and an interrupted one. |

### What a strike cannot be shown to do headlessly

A craft's guns are placed from its hull's real `weaponSlots`, which need the
game loaded to read, so a headless strike flies unarmed and its station time
collapses to a few ticks. Measured over two conquest battles a station launches
four or five strikes and every one of them completes the full cycle — taxi,
hold short, roll, transit, station, return, rollout, taxi in — with `armed=0`
throughout. That the arming works is an inference from transports using the
same resolver on the same path, not something a test here has shown. A sortie
that comes up unarmed says so once in the log rather than flying silently
harmless forever.
