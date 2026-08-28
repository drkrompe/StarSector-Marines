# Compound programs

Status: ACTIVE — the fortress is programmed and shipped; the ordinary compound
is not.

Written: 2026-08-28

Updated: 2026-08-28 — the Conquest fortress ward now packs, furnishes and berths
from its own program, so steps 2 to 4 are shipped for that family; its buildings
now author their walls and keep them out of the street, the wall stamper no
longer demolishes the ward it encloses, and the ward bounds how far its
buildings may be glued together. Step 1 — a compound sized to hold what it
owes — remains the open piece, and is still what blocks the hangar in an ordinary
military base.

Read `mapgen-nouns.md` for the recipe, context, stage, and validation
obligations any of this inherits. `ship-interiors-nouns.md` owns the deck
family this borrows from; nothing here changes it.

A compound should be built from what it is for. Today it is built from what
the partition left over, and the difference is measurable.

## The inversion

The deck family states the law plainly: **the program sizes the deck; the deck
does not size the program.** A hull owes rooms because of its role, complement
and class; each room has an authored footprint sized to its function; the
packer lays those footprints into the hull, and a room the hull could not hold
is reported as unplaced rather than quietly dropped.

The city family runs the other way round. A BSP partition cuts the district
into leaves, the claim pass grows a compound over adjacent leaves, and roles
are then handed to those leaves **by descending area** — the biggest becomes
the barracks, the next the armory, the smallest the motor pool. No wing is the
size its function calls for, because no function ever stated a size. The parcel
came first and the purpose was fitted to it afterwards.

The fortress is the same shape of mistake at a larger scale. `FortressWallStamper`
draws an authored perimeter — towers, gates, kill-zone bunkers, an outer ward —
around whatever the ordinary city fill happened to put inside it. The enclosure
is designed and its interior is leftovers, so the climax of a Conquest mission is
a very good wall with a district behind it rather than a fortress.
`FortressPreviewTest` renders the band: freestanding rectangles on a road grid,
one walled compound, and nothing that would read as a fortress if the wall were
taken away.

## Measured: the parcels do not fit the function

Counted 2026-08-28 over generated Conquest maps at 240x160, three military
compounds per seed.

`Compound.Role.VEHICLE_BAY` is unreachable in production. A `MILITARY_BASE`
grows to three members and the role order is COMMAND, BARRACKS, ARMORY,
VEHICLE_BAY — so the fourth is never assigned. Across 36 compounds, none had
one. Raising the growth target to four reaches the role on 24 of 36, and hands
it the *smallest* wing every time: interiors of 3x3, 4x3, 5x3.

Sizing a motor pool for the shared five-by-seven bay module, over 180
compounds:

| Wing | Fits a bare 5x7 bay | Fits bay plus a two-cell apron |
|---|---|---|
| Largest non-seed wing | 71 (39%) | 52 (29%) |
| Seed (command) wing | 168 (93%) | — |

Only the command wing is reliably large enough to hold a machine, and it is the
keep. Two out of three bases could not garage anything whatever role assignment
did, because nothing in the pipeline ever asked for a parcel a vehicle fits in.

## The wall is a consequence, not a frame

The order is the fix. A fortress interior is packed first — its program laid
into the district envelope, its roadways cut through what packing leaves — and
the wall is then drawn around the result. Today the wall is stamped last and
overwrites whatever it crosses, which is what makes its interior leftovers no
matter how good the wall itself is.

Inverting it changes what the wall *is*. Drawn around a packed interior, its
envelope follows the thing it defends, its gates land where the interior's own
roadways already run out to meet them, and the kill-zone buffer is measured from
real structure rather than from a biome inset. There is precedent in the current
stamper: it already expands its envelope to wrap the keep compound rather than
stranding it outside, because the keep's claimed footprint is authoritative. That
exception becomes the rule, and the special case for the keep disappears into it.

None of this makes the wall less authored. Towers, gates, MG nests, and forward
bunkers stay exactly what they are; they stop being drawn across a district and
start being drawn around a fortress.

## Measured: the program has to size the ground

Counted 2026-08-28 over eight seeds, packing the garrison program (1256 cells of
building floor) into envelopes scaled from it.

| Ground per cell of floor | Envelope | Unplaced over the sweep | Largest untouched square |
|---|---|---|---|
| 2.0 | 68x51 | 8 | 16 |
| 2.2 | 72x54 | 7 | 16 |
| **2.4** | **75x56** | **0** | **17** |
| 2.6 | 77x58 | 0 | 17 |
| 2.8 | 80x60 | 0 | 23 |
| 3.0 | 83x62 | 0 | 25 |

Two things follow, and the second was a surprise. There is a real floor — below
it the program genuinely does not fit, because each building carries a wall ring,
the roadways between them are two cells wide, and the ward keeps yard between
neighbours. And **too much ground is a defect in its own right**: the packer scores a position by how
tightly it wedges against something solid, and at the start the only solid thing
is the envelope boundary, so an oversized envelope pins every building to the rim
and leaves a hole in the middle that no tuning fills. The first fortress packed
this way had a forty-cell void at its centre. Sizing the envelope from the
program fixed it, which is the law arriving from the other direction: a place
built from what it is for cannot be given arbitrary ground either.

The table was re-measured when the ward stopped letting its buildings chain
together, and the floor moved from 2.2 to 2.4. **A sizing ratio measured under
one packing policy does not survive a change to it**: buildings held a cell
apart need more ground than buildings glued into a slab, and the ratio that had
been the tightest workable one left a building homeless the moment they were
spaced.

## Leftover ground is the yard

A hull's leftovers stay solid, because a void inside a ship is structure. A
fortress's leftovers are its parade ground, and closing them would produce a
fortress that is mostly corridor and cannot be fought through.

This is also what makes packed roadways read as roadways. Cut against solid
ground they are the only way through, which is a warren; cut across open yard
they are the made-up routes over it, and a squad can leave one and cross open
ground under fire. That choice is what a fortress assault should be about, and
it is a property of what the leftovers become rather than of how the roads were
routed.

## Organic comes from packing, not from subdivision

"More organic" is a property of the mechanism, not of the parameters. A binary
partition of a rectangle yields rectangles, always; tuning its split ratios
varies their proportions and never their kind. The deck family does not
subdivide. It packs authored shapes into an outline and cuts circulation
through the space packing leaves over, which is why a deck has passages of
differing length and route instead of a grid, and why an L-shaped range or a
compartment wrapping a hull flare is a data change rather than a rewrite of the
placer.

That mechanism is what a larger, more organic compound needs. The compound
perimeter becomes the outline, the program becomes what must go inside it, and
the yard is what the packing leaves — a courtyard with a shape, rather than the
road frame that happened to run between two leaves.

## What transfers

| Deck noun | Compound reading |
|---|---|
| Room recipe | A wing is a purpose with an authored footprint, a ward affinity, and the capacity one of it supplies. |
| Room program | What a base owes: a command post, berthing for its garrison, an armory, a motor pool if it fields vehicles. Derived from the installation's role and garrison strength. |
| Room shape | A footprint as a mask. A hangar is a shed; a bunker line is a run; a fuel bund is a ring. None of them are the leaf they landed on. |
| Longitudinal zone | A **ward**: frontage, yard, and rear. Functional prior, not decoration — the gatehouse and motor pool belong at the frontage, the keep and magazine at the rear, berthing off the yard. |
| Hull contact | **Perimeter contact.** Some wings must reach the compound wall or they do not work: a hangar whose vehicle door opens onto the yard with no route to a gate is a shed. This is the same constraint a boat bay has, and it is the one the measurement above says is missing. |
| Hookup | A wing declares where its opening may be before placement. A vehicle door is bay-width and cannot be a one-cell doorway punched afterwards. |
| Pose | One authored arrangement yields eight, and the wing records which it got rather than recovering it from the mask. |
| Unplaced program | A base that could not fit its motor pool says so. Today an under-provisioned compound is indistinguishable from a small one. |

The fitting tier already transferred: room fittings live in a family-neutral
package behind a furnishable room, keyed on the ordinary room purpose both
families already label. A wing that is packed and purposed can be furnished by
the same fittings that furnish a compartment, with the same
lanes-before-fixtures and roll-back-rather-than-seal contracts.

## The ward, as it shipped

The Conquest fortress band now holds a **ward**: a program-sized rectangle laid
out before the wall, packed from authored building footprints into three depth
bands measured from the attacker's approach, furnished by the shared fittings,
and opened as parade ground wherever packing left ground over. Four of its
rules were learned by measurement rather than chosen, and each is a standing
constraint rather than a tuning value.

**The ward is sized from ground it can actually build on.** Roads crossing the
band and the citadel standing in it take cells out of the middle; an envelope
sized as though they were not there comes up short by exactly what they occupy.

**A fortress does not inherit a city's street grid.** Keeping every road that
crossed the band subdivided it into blocks smaller than the buildings meant to
stand in them — 2647 buildable cells and not one clear pocket for a
fifteen-by-nine shed. The ward keeps one through route and the full width of the
street carrying it: enough to keep the published road graph honest, since a
defender convoy still commits along it, without cutting the ward into
courtyards.

**A room that means to hold bays is sized from the bay module.** A shed picked
to look about right came out at fifteen by nine, which the fitting could only
answer with a single shallow bay before the fill was rolled back for sealing
itself. Shed dimensions are arithmetic over the module, so they follow it.

**A building's wall stands on ground, and the ward has to reserve it.** The
envelope says where a floor may go, and the wall is the ring around that floor —
ground the packing is told to leave alone rather than ground it asks for. A shed
laid flush to the envelope's edge had its ring fall on the city street outside
the ward, which the ward does not own and must not close, because the road graph
published that street and a convoy is entitled to drive it. Nothing made those
cells solid and the shed came out open along its whole sixteen-cell flank,
walled by a pavement, with its bays and berths standing in the road. A floor may
therefore only stand where its ring still lands on ward ground. The ring
carries the room's own ground beneath it, and its facades carry windows: a wall
is not only a mask, and a garrison building with no apertures is defensible only
from its doorway.

**A garrison stands on ground, so its buildings may not be glued into a wall.**
Packing scores a position by how tightly it wedges against something solid,
which inside a hull is right — a void between two compartments is wasted
displacement. On open ground the same reward chains every building into one
slab, and nothing the packer checks notices: each room still has its door and
its route, so the packing is correct by every test it applies. What it cannot
see is the cost of crossing the place. Measured at production proportions, the
chaining ran sixty-six cells of unbroken wall across a hundred-and-twenty-six
cell ward and blocked the whole of its twenty-eight-cell depth; the ward now
charges a building for wall it lays against a neighbour's, and the worst run
falls to forty-five across and eighteen through — eighteen being one vehicle
shed, which is the shortest wall a ward with a vehicle shed can have. Buildings
left over across the sweep fall from seven to one at the same time, because a
slab wastes the ground it encloses.

The charge is a price rather than a ban, and it counts abutment as well as
sharing. A ban costs placements, and a room that cannot be placed at all is
worse than a room placed against its neighbour. Counting only shared cells
measures the wrong thing: two rooms standing back to back make a wall two cells
thick and share nothing, and it is exactly as impassable as one they share.

Demolition carries obligations the ward discovered the hard way, in both
directions. A stage that replaces what an earlier one built must take out what
it recorded as well as what it drew — doodads, points of interest, tactical
nodes, and the authored identities on shared edges. A window that outlives its
building is scenery nobody can explain, and, because an edge carries exactly one
authored identity, it is also an edge the next stage cannot build on. And a
stage that clears ground has to be told what it may not clear: keeping the wall's
route out of the ward is only half of not destroying it, because the same stage
demolishes any building straying into its sweep and takes the whole of it,
however far it reaches. The clearance the ward reserves is measured from its
band, and the wall lands where its route allows, so the two came out two cells
apart and the flood ate a shed to its far corner — leaving parade ground with
the fill still standing on it.

## What does not transfer

A deck is a closed volume with one authority. A compound is not, and pretending
otherwise would break things the city family is right about.

- **The city is prior.** A hull profile answers only to the hull. A compound
  perimeter has to live with a road network, parcel ownership, and neighbors
  that were placed before it. The program proposes a footprint; it does not get
  to overwrite roads or absorb a neighbor's fill, which is the boundary
  `cross-leaf-footprint-planning.md` exists to draw.
- **A frame is a shipboard coordinate.** It works because a hull is long and
  one-dimensional in the way that matters. A compound has no equivalent axis and
  should not invent one; a ward is an affinity, not an ordinate.
- **A transverse bulkhead is not a compound wall.** The deck's chokepoints come
  in a defensible order because there is only one way through a ship. A compound
  is approachable from several sides by design, and imposing an ordered
  chokepoint sequence on it would turn an open assault problem into a corridor.
- **Berthing arithmetic is the ship's.** Lift, minimum crew, and crew spaces
  size a hull's program from its complement. A base's program comes from the
  installation's role and its garrison, which is a campaign fact, not a hull
  stat.

## Sequencing

The program is the load-bearing piece; everything else is downstream of it.

1. **Open.** A compound states what it owes, as recipes with footprints and ward
   affinities, and the claim pass sizes the compound to hold them rather than
   labelling whatever it grew over. `cross-leaf-footprint-planning.md` owns the
   seam where that claim has to coexist with roads and neighbors. This is the
   step the ordinary military base still lacks, and therefore the one that keeps
   `compound-vehicle-hangar.md` blocked.
2. **Shipped for the fortress.** Wings are packed and posed into the claimed
   footprint, with perimeter contact honored for the wings that need it, and the
   yard is what is left. An unplaced wing is reported.
3. **Shipped for the fortress.** Purposed wings are furnished by the shared
   fittings, and a wing that berths machines publishes them.
4. **Shipped.** The fortress is programmed rather than stamped. Its interior is
   packed from its own program — magazines, vehicle sheds, barrack blocks — with
   roadways cut to reach them, and its wall is drawn around that result instead
   of across a district. The wall's own authored features are unchanged.

The fortress reached steps 2 to 4 without step 1 because it did not need a claim
pass: its band is given by the biome, so the ward sizes itself inside ground it
already has. An ordinary compound has no such given, which is exactly why step 1
is what remains.

Each step is a separate story. None of them is a reason to widen `MapResult`
with analysis nothing consumes, and all of them keep the standing obligations:
determinism from the request, no RNG draw for an absent feature, decoration
that cannot create movement or cover rules, and final connectivity and
deployment validation.
