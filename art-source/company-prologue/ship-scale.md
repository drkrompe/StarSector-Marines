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
hulls. One set of contents and one set of class factors can.

## The constants and where they come from

**Habitable volume per person at max crew: 300 m3** (range 273-328). The mod's
own deck program, which the game's deck maps are drawn from, gives a 40-crew
frigate 2,427 m2 of rooms, about 61 m2 a person. At x1.40 circulation and 3.2 m
decks that is 273 m3. The Hound (A) is the calibration anchor: its doors,
airlock, viewports, rails and mannequin were built to a person at 0.88 m/unit,
which needs 328. The Second Wind, built to its own fittings at 0.65, solves to
0.663 on the same constant, which is a second, independent check. The figure
holds for every crew size, with no economy of scale. The deck program gives
the Legion only 21 m2 a person, and that is how a carrier came to read smaller
than a freighter beside it. 300 m3 is far above naval norms (15-30 m3) and the
ISS (55 m3). A Starsector crew lives aboard for months, and the models'
fittings were built to this.

**Capitals feel like capital ships** (owner). Where a real-world input leaves
a range, a capital takes the generous end and small hulls take the lean end.
That means crew room x1.2, hangars at 3.0x each craft's box, deep armour and
big magazines.

**Cargo: 2.0 m3 per unit.** One unit is taken as one tonne of goods. Mixed
palletised cargo stows at about 1.5 m3 per tonne, plus 30% for hold access and
handling.

**Fuel: 1.5 m3 per unit.** One unit is taken as one tonne at about 0.8 t/m3,
plus 20% for tankage.

**Hangars:** each craft's bare length x span x height box (the fighter's own
size), times the class's hangar factor, with one spare per wing. A Nimitz's
hangar is 210 x 33 x 8 m (about 55,000 m3) and holds 25-30 aircraft, roughly
2,000 m3 each against an F/A-18's 1,090 m3 box, about 1.8x. Servicing in space
and ordnance handling add to it.

**Per class** (shares are of the enclosed volume no crew, cargo, fuel or
fighter can use):

| class | armour | drive, reactor, flux | weapons, magazines | structure, tanks, stores, life support | total | habitable factor | hangar factor |
|---|---|---|---|---|---|---|---|
| frigate | .06 | .22 | .06 | .12 | .46 | 1.0 | 2.5 |
| destroyer | .08 | .20 | .07 | .12 | .47 | 1.05 | 2.6 |
| cruiser | .11 | .18 | .09 | .12 | .50 | 1.1 | 2.8 |
| capital | .18 | .16 | .12 | .12 | .58 | 1.2 | 3.0 |
| freighter | .03 | .15 | .02 | .12 | .32 | 1.0 | 2.5 |

Real warships give machinery spaces about 20-25% of their internal volume. The
drive plant's share shrinks with size, while armour and weapons grow with
class. A freighter carries little of either. The class comes from `hullSize`,
except that a designation naming a freighter, transport, tanker or liner makes
the hull a freighter.

**The game's sprite lengths are a floor on the frigate-to-capital ratio, not
the method.** The sprites are Hound 72, Lasher 96, Dominator 180, Legion 308
and Onslaught 384 px. The module reports each length against the Lasher's and
flags a capital that comes out under its sprite ratio or well under about 4x.

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
hull is nearly square in plan, so the same volume gives a far shorter ship.
This basis gives about 450-900 m3 of enclosed hull per person: several times
any warship's and the ISS's 130 m3 pressurised. That is the cost of honouring
the deck program and the fittings the models were built to, and it is stated
here rather than hidden.

## Measured and estimated volumes

A model's volume is the union of the solids a crew could be inside, measured by
its own driver on every build. Before a model exists, `estimate_units3` takes
k x (sprite alpha area)^1.5, with k measured on the built models: Hound 0.188,
Cerberus 0.169, Mudskipper 0.178 and Legion 0.117. Frigates use 0.178 and
capitals 0.117. Treat an estimate as about +-20% in volume, or +-7% in metres
per unit.
