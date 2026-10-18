# Ship scale: one sizing basis for every cinematic hull

Status: DRAFT. Constants are proposed; no tracked `metres_per_unit` has moved to them yet.
Written: 2026-10-18

A hull's size comes from what it holds, not from its sprite. `ship_scale.py`
sizes every hull the same way:

    contents  = max crew x habitable m3 per person (by class)
              + cargo x the cargo-unit volume + fuel x the fuel-unit volume
              + hangars (from the fighters' own sizes) + boat bays
    enclosed  = contents / (1 - machinery share of the hull's class)
    metres per unit = cube root(enclosed m3 / the model's measured enclosed units3)

Before this, each hull was sized from a share of its pressure hull taken as
usable: 55% for freighters and 33% for the Legion. Those shares cannot be
compared between hulls, and they gave a carrier less hull per person than a
freighter.

## Measured volume, one way for every hull

Enclosed volume is the union of the pressure hull with its holds, hangars and
boat bays. It never includes drive pods, engine cans, armour laid over the
hull, or open structure. `SOLIDS` in the module names each model's solids, and
`ship_scale_measure.py` measures their union inside the hull's .blend. Before a
model exists, `estimate_units3` takes k x (sprite alpha area)^1.5. k is
measured on the built models: Hound 0.090, Second Wind 0.172, Legion 0.117.
Treat an estimate as about +-8% in metres per unit.

## The calibrated constant: one cargo unit

**The Hound (A) is the anchor.** Its 0.88 m/unit is fixed by modelled
human-scale fittings: a 1.4 x 2.0 m aft door, a 1.7 x 2.1 m airlock,
0.9 x 1.0 m viewports, walkway rails and a 1.8 m mannequin. 0.83 is the
cramped floor. For a freighter, cargo is the dominant term, so the cargo unit's
volume is the one constant solved, not chosen. It is whatever fills the
Hound's measured hull once crew, fuel and machinery have their real-world
shares: **50.6 m3 per unit** (37 m3 if the Hound sat at 0.83).

Physical check: a twenty-foot container is 38.5 m3 outside. 50.6 m3 is one
such container with about 30% for hold access, cell guides and handling, a
reasonable meaning for one Starsector cargo unit. The old 2.0 m3 per unit made
the Hound's 75-unit hold about four delivery vans, which is why it solved to
half its fitted size.

## Real-world constants

**Habitable volume per person**, compared against real habitable figures:
naval warships give their crews 15-30 m3 each for voyages of weeks, and the
ISS gives its crew of 7 about 55 m3 each. The range is 40 (a cramped warship)
to 70 (a roomy long-haul ship).

**Fuel: 32 m3 per unit.** One unit is one tank-container load: an ISO tank
holds about 26 m3, plus 25% for tankage, insulation and plumbing.

**Hangars:** each craft's bare length x span x height box, at the fighter's own
size, times the class's hangar factor, with one spare per wing. A Nimitz's
210 x 33 x 8 m hangar (about 55,000 m3) holds 25-30 aircraft, about 1.8x each
F/A-18's box. Servicing in space and ordnance handling add to it.

**Boat bays: 960 m3 each.** That is the mod's deck program's 16 x 10 m boat bay
at 6 m clear height. The Second Wind has two.

**Capitals feel like capital ships** (owner). Where a range is left, a capital
takes the generous end and a small hull the lean end:

| class | habitable m3 per person | armour | drive, reactor, flux | weapons, magazines | structure, tanks, stores, life support | machinery total | hangar factor |
|---|---|---|---|---|---|---|---|
| frigate | 40 | .06 | .22 | .06 | .12 | .46 | 2.5 |
| destroyer | 50 | .08 | .20 | .07 | .12 | .47 | 2.6 |
| cruiser | 60 | .11 | .18 | .09 | .12 | .50 | 2.8 |
| capital | 70 | .18 | .16 | .12 | .12 | .58 | 3.0 |
| freighter | 40 | .03 | .15 | .02 | .12 | .32 | 2.5 |

Real warships give machinery spaces about 20-25% of their internal volume. The
drive plant's share shrinks with size, while armour and weapons grow with
class. A freighter carries little of either. The class comes from `hullSize`,
except that a designation naming a freighter, transport, tanker or liner makes
the hull a freighter.

## Frigate-to-capital length

The game's sprite lengths are Hound 72, Lasher 96, Dominator 180, Legion 308
and Onslaught 384 px. They are a floor on the ratio, not the method. The module
reports each capital's length against both the Hound and the Lasher, and flags
any capital under about 4x a frigate, or under its own sprite ratio. A flagged
capital is never published silently.

## Whole hull against whole hull

These are approximate public figures for enclosed volume per person: Visby
corvette ~60 m3 (73 m, 43 crew), Perry frigate ~65 (136 m, ~200), Arleigh
Burke ~100 (155 m, ~330), Iowa ~55 (270 m, ~2,700), Nimitz ~60 (333 m,
~5,500). The ISS has 130 m3 pressurised per person. A naval hull is long and
slender (length 7-10x beam) for seakeeping. A Starsector hull is nearly square
in plan, so compare volume per person, not length per crew.
