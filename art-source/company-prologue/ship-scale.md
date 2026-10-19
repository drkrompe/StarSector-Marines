# Ship scale: one sizing basis for every cinematic hull

Status: ACTIVE. Owner's choice 2026-10-18: option C (300 m3 a person, capital factor 1.45).
Written: 2026-10-18

A hull's size comes from what it holds, not from its sprite. `ship_scale.py`
sizes every hull the same way:

    contents  = max crew x 300 m3 x the class's habitable factor
              + cargo x 2.0 m3 + fuel x 1.5 m3
              + hangars (from the fighters' own sizes) + boat bays x 960 m3
    enclosed  = contents / (1 - machinery share of the hull's class)
    metres per unit = cube root(enclosed m3 / the model's measured enclosed units3)

Each model's pass 4 calls `ship_scale.record(<hull>)` and stores the block in
its geometry.json. The published `metres_per_unit` is the solved value,
rounded. A hull is not given its own constants; change a constant here and in
the module together, and every hull moves with it.

Before this, each hull was sized from a share of its pressure hull taken as
usable: 55% for freighters and 33% for the Legion. Those shares could not be
compared between hulls, and they gave a carrier less hull per person than a
freighter.

## Measured volume, one way for every hull

Enclosed volume is the union of the pressure hull with its holds, hangars and
boat bays. It never includes drive pods, engine cans, armour laid over the
hull, or open structure. `SOLIDS` in the module names each model's solids, and
`ship_scale_measure.py` measures their union inside the hull's .blend. Before a
model exists, `estimate_units3` takes k x (sprite alpha area)^1.5, with k by
class (frigate 0.131, capital 0.117). Treat an estimate as about +-8% in metres
per unit, and replace it with a measurement as soon as the model exists.

## Crew room: the dominant term

**300 m3 a person at max crew, for every crew size.** The mod's deck program
(`DeckSizing.programFor`, the program the game's deck maps are drawn from)
gives a 40-crew frigate 2,427 m2 of rooms: about 61 m2 a person, x1.40 for
circulation, x3.2 m decks = 273 m3. The Hound (A)'s modelled fittings (doors,
airlock, viewports, rails, a 1.8 m mannequin) were built at 0.88 m per unit,
which needed 328. 300 lies between them; the range 273-328 is reported for
every hull. There is no economy of scale.

That is far above naval norms (15-30 m3) and the ISS (about 55). A Starsector
crew lives aboard for months, and the deck program is what the game draws.

**Capitals feel like capital ships** (owner). A capital's crew room takes a
factor of 1.45, which brings the capitals to about 3.3-4.3x a frigate's length.
The owner chose this from lineup renders of four options.

| class | habitable factor | armour | drive, reactor, flux | weapons, magazines | structure, tanks, stores, life support | machinery total | hangar factor |
|---|---|---|---|---|---|---|---|
| frigate | 1.0 | .06 | .22 | .06 | .12 | .46 | 2.5 |
| destroyer | 1.05 | .08 | .20 | .07 | .12 | .47 | 2.6 |
| cruiser | 1.1 | .11 | .18 | .09 | .12 | .50 | 2.8 |
| capital | 1.45 | .18 | .16 | .12 | .12 | .58 | 3.0 |
| freighter | 1.0 | .03 | .15 | .02 | .12 | .32 | 2.5 |

Real warships give machinery spaces about 20-25% of their internal volume. The
drive plant's share shrinks with size, while armour and weapons grow with
class. A freighter carries little of either. The class comes from `hullSize`,
except that a designation naming a freighter, transport, tanker or liner makes
the hull a freighter.

## The other real-world constants

**Cargo: 2.0 m3 a unit.** One unit is taken as one tonne of goods: about
1.5 m3 a tonne for mixed palletised cargo, plus 30% for hold access.

**Fuel: 1.5 m3 a unit.** One tonne at about 0.8 t/m3, plus 20% tankage.

**Hangars:** each craft's bare length x span x height box, at the fighter's own
size, times the class's hangar factor, with one spare per wing. A Nimitz's
210 x 33 x 8 m hangar (about 55,000 m3) holds 25-30 aircraft, about 1.8x each
F/A-18's box. Servicing in space and ordnance handling add to it.

**Boat bays: 960 m3 each.** That is the deck program's 16 x 10 m boat bay at
6 m clear height. The Second Wind has two.

## The hulls today

| hull | metres per unit | length | volume | state |
|---|---|---|---|---|
| Hound (A) | 0.88 (solves 1.09) | 61 m | measured | published, kept: the anchor for person-scale fittings |
| Second Wind | 0.65 (solves 0.69) | 66 m | measured | published, kept |
| Lasher | 0.65 published (solves 0.95) | 62 m (90 m solved) | measured | published before option C; re-publish at 0.95 owed by the Lasher session |
| Legion (XIV) | 0.99 (solves 0.995) | 292 m | measured | published |
| Onslaught (XIV) | 0.86 (solves 0.856) | 301 m | measured (2.48 M units3) | published, final: a capital like the Legion, no override (owner) |
| Shepherd (Morrow) | 0.78 (solves 0.74) | 67 m | measured | published, kept (owner, 2026-10-18); the drone bay is open, so no hangar term |
| Paragon | 0.87 (solves 0.751) | 312 m hull (269 m solved; 346 m over the bow autopulse barrels) | measured (2.46 M units3; open bore, shield emitter lining and deck gantry excluded; the bow hardpoint bays cut through the belt) | published by owner override: larger than the Onslaught (owner, 2026-10-18); 315 m accepted by eye beside the Onslaught and Legion in the fleet lineup (that figure counted the old bow emitter placeholders, 3 units past the prow). The rule gives 271 m: 1,000 crew against the Onslaught's 1,500. The blockout was first slimmed to the Legion's and Onslaught's fullness (mean depth over plan 33.9 units against 28.4 and 37.4) |
| Colossus | 0.50 (solves 0.431) | 108 m (94 m solved) | measured (1.13 M units3; longerons, open trusses, drive pods and the open container bays excluded) | published by owner override (2026-10-18): kept at 108 m after the owner's thick cargo front (pass 3 round 1) deepened the block from 0.72 M to 1.18 M units3 and lowered the solve from 0.502; the open container bays (round 2, bow and aft faces) trim it to 1.13 M (1.129 M after round 4's keel hatches). Crew room sets the solve: 200 crew are 60,000 of the 61,980 m3 of contents, the 900 cargo only 1,800 m3 |

The Hound, the Second Wind and the Shepherd keep their published values by
the owner's decision, and the Paragon and the Colossus are published above their solves by owner override. Their fittings were modelled at those scales.

## Frigate-to-capital length

The game's sprite lengths are Hound 72, Lasher 96, Dominator 180, Legion 308
and Onslaught 384 px. They are a floor on the ratio, not the method. The module
reports each capital's length against both the Hound and the Lasher, and flags
any capital under about 4x a frigate, or under its own sprite ratio. A flagged
capital is never published silently.

Under option C both capitals are flagged against the Lasher's solved length
(3.3x and 3.6x; measured volume, 90 m). The owner chose C with that shown.
Against the Lasher's pre-C published 62 m the Legion would be 4.7x; that
number is stale until the Lasher is re-published on this basis.

## Person-scale fittings

Doors, hatches, rails, ladders and catwalks are true metres at the published
scale: a 2.1 m door is 2.1 m whatever `metres_per_unit` is. A model that drew
them at an earlier scale scales those authored sizes by old/new, as the Legion
does with `lg_kit.PERSON`. Hangar mouths, bays and fighters are sized from
contents and do not rescale.

## Whole hull against whole hull

These are approximate public figures for enclosed volume per person: Visby
corvette ~60 m3 (73 m, 43 crew), Perry frigate ~65 (136 m, ~200), Arleigh
Burke ~100 (155 m, ~330), Iowa ~55 (270 m, ~2,700), Nimitz ~60 (333 m,
~5,500). The ISS has 130 m3 pressurised per person. A naval hull is long and
slender (length 7-10x beam) for seakeeping. A Starsector hull is nearly square
in plan, so compare volume per person, not length per crew.
