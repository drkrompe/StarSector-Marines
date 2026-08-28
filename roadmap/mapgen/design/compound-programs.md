# Compound programs

Status: ACTIVE — direction agreed, nothing implemented.

Written: 2026-08-28

Updated: 2026-08-28 — the fortress inverts: its interior is packed first and its
wall is derived from the result. Baseline evidence rendered.

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

1. A compound states what it owes, as recipes with footprints and ward
   affinities, and the claim pass sizes the compound to hold them rather than
   labelling whatever it grew over. `cross-leaf-footprint-planning.md` owns the
   seam where that claim has to coexist with roads and neighbors.
2. Wings are packed and posed into the claimed footprint, with perimeter
   contact honored for the wings that need it, and the yard is what is left.
   An unplaced wing is reported.
3. Purposed wings are furnished by the shared fittings.
   `compound-vehicle-hangar.md` is the first consumer, and is blocked on step 1
   for the reason the measurement gives.
4. The fortress is programmed rather than stamped. Its interior is packed from
   its own program — magazines, vehicle sheds, barrack blocks, the keep — with
   roadways cut to reach them, and `FortressWallStamper` then draws its wall
   around that result instead of across a district. The wall's own authored
   features are unchanged.

Each step is a separate story. None of them is a reason to widen `MapResult`
with analysis nothing consumes, and all of them keep the standing obligations:
determinism from the request, no RNG draw for an absent feature, decoration
that cannot create movement or cover rules, and final connectivity and
deployment validation.
