# Ship scale: one sizing basis for every cinematic hull

Status: DRAFT — constants proposed; no tracked `metres_per_unit` has moved to them yet.
Written: 2026-10-18

A hull's size comes from what it holds, not from its sprite. `ship_scale.py`
sizes every hull the same way:

    contents  = max crew x habitable m3 per person + cargo + fuel + hangars
    enclosed  = contents / (1 - machinery share of the hull's class)
    metres per unit = cube root(enclosed m3 / the model's measured enclosed units3)

Before this, each hull was sized from a share of its pressure hull taken as
usable: 55% for freighters (the Hound, the Second Wind, the Mudskipper) and
33% for the Legion as a warship. On top of the mod's deck program, those
shares gave the Hound about 490 m3 of hull per person and the Legion about 260.
That is backwards for a carrier, and those shares cannot be compared between
hulls. One set of contents and one set of class shares can.

## The constants and where they come from

**Habitable volume per person at max crew: 55 m3** (range 40-70). Habitable
means berths, messes, work stations, medical and recreation, with the passages
between them. The ISS gives its crew of 7 about 388 m3 of habitable volume, 55
each. Naval warships give their crews about 15-30 m3 each, but sail for weeks
between port calls; a Starsector crew lives aboard for months. 40 is a cramped
warship standard and 70 a roomy long-haul one. This constant is the biggest
lever: every hull's metres per unit moves with its cube root.

**Cargo: 2.0 m3 per unit.** One unit is taken as one tonne of goods. Mixed
palletised cargo stows at about 1.5 m3 per tonne, plus 30% for hold access and
handling.

**Fuel: 1.5 m3 per unit.** One unit is taken as one tonne, at about 0.8 t/m3,
plus 20% for tankage.

**Hangars: 2.5 times each craft's bare length x span x height box, with one
spare per wing.** Craft sizes are the fighters' own, at their own scales. A
Nimitz's hangar is 210 x 33 x 8 m (about 55,000 m3) and holds 25-30 aircraft,
roughly 2,000 m3 each against an F/A-18's 1,090 m3 box. That is about 1.8x.
Servicing in space and fighter ordnance handling add to it.

**Machinery shares by class.** Each share is part of the enclosed volume that
no crew, cargo, fuel or fighter can use:

| class | armour | drive, reactor, flux | weapons, magazines | structure, tanks, stores, life support | total |
|---|---|---|---|---|---|
| frigate | .06 | .22 | .06 | .12 | .46 |
| destroyer | .08 | .20 | .07 | .12 | .47 |
| cruiser | .10 | .18 | .08 | .12 | .48 |
| capital | .14 | .16 | .09 | .12 | .51 |
| freighter | .03 | .15 | .02 | .12 | .32 |

Real warships give machinery spaces about 20-25% of their internal volume. The
drive plant's share shrinks with size, while armour and weapons grow with
class. A freighter carries little of either. The class comes from `hullSize`,
except that a designation naming a freighter, transport, tanker or liner makes
the hull a freighter.

## Checks against real ships

These are approximate public figures.

| ship | length | crew | displacement | enclosed volume per person (approx.) |
|---|---|---|---|---|
| Visby corvette | 73 m | 43 | 600 t | ~60 m3 |
| K130 corvette | 89 m | 65 | 1,840 t | ~75 m3 |
| Perry frigate | 136 m | ~200 | 4,100 t | ~65 m3 |
| Arleigh Burke destroyer | 155 m | ~330 | 9,200 t | ~100 m3 |
| Iowa battleship | 270 m | ~2,700 | 58,000 t | ~55 m3 |
| Nimitz carrier | 333 m | ~5,500 incl. air wing | 100,000 t | ~60 m3 |
| ISS | 109 m truss | 7 | — | 130 m3 pressurised |

Compare volume per person, not length per crew. A naval hull is long and
slender (length about 7-10 times beam) for seakeeping and speed. A Starsector
hull is nearly square in plan (the Legion is as wide as it is long), so the
same volume gives a far shorter ship: a 50-crew frigate that would be 70-90 m
at sea comes out under 50 m here. This basis gives about 90-160 m3 of enclosed
hull per person, more than any warship, because crews live aboard for months.
The ISS's 130 is the closest real figure.

## Measured and estimated volumes

A model's volume is the union of the solids a crew could be inside, measured by
its own driver on every build. Before a model exists, `estimate_units3` takes
k x (sprite alpha area)^1.5, with k measured on the built models: Hound 0.188,
Cerberus 0.169, Mudskipper 0.178 and Legion 0.117. Frigates use 0.178 and
capitals 0.117. Treat an estimate as about +-20% in volume, or +-7% in metres
per unit.
