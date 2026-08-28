# Ship deck family

Status: IN PROGRESS — the deck, its rooms and its circulation are built and
integrated. Transverse bulkheads and the breach point remain.

Written: 2026-08-26

Updated: 2026-08-28 — circulation is no longer left a tree: a link pass runs
after placement and joins dead ends that are near in the hull and far apart
along the halls. What is left is the bulkhead chokepoint sequence and the breach
point, which the end spawns still stand in for, and the packing question the
link measurement below turned up.

Read `ship-interiors-nouns.md` before implementing this story. Read
`mapgen-nouns.md` for the recipe, context, stage, and validation obligations this
family inherits.

Add the longitudinal deck family alongside the existing station recipes. The
station layouts organize space around a core; this one organizes it around an
axis. The station recipes' layout-neutral topology tier is reused as-is — this
story adds a spatial premise, not a second topology model.

## Scope

- A **hull profile** stage that establishes the deck silhouette as beam per
  frame: narrow forward, broadest amidships, tapering aft, and free to differ
  port from starboard. Everything outside the profile is hull, not playable
  space.
- A **spine** stage that runs the primary fore-aft corridor and assigns each
  frame a longitudinal zone.
- ~~A **compartment carve** stage that hangs purposed compartments off the
  spine~~ — superseded. Rooms are packed at authored footprints and circulation
  is cut from the space that packing leaves; hanging every compartment off the
  spine turned out to be the defect, not the design.
- A **link** pass that runs after placement and joins circulation to itself
  where a short cut removes a long walk. Shipped; see the measurement below and
  law 23 in `ship-interiors-nouns.md`.
- A **transverse bulkhead** stage that divides the deck at chosen frames with
  authored hatches, producing an ordered chokepoint sequence.
- A **breach point** stage that places the boarding entry on a flank at a chosen
  frame, and sets the longitudinal assault gradient from it.
- A deck graph that carries the shared topology tier plus this family's
  annotation: each compartment's frame span, zone, and side of the spine.

## Constraints

- Determinism is inherited: identical request inputs and recipe composition
  produce the same deck. No stage draws for an absent feature, and no unordered
  iteration decides an outcome.
- Decoration cannot create movement, cover, or wall rules; bulkheads and hatches
  are structural and declare their effect.
- The layout-specific ring, core, and port annotations on the existing station
  graph are not a ship's. Decide in this story whether the shared tier is
  extracted, renamed, or annotated in place, and state the reason in the commit.

## Acceptance

- Generated decks are elongated and not mirrored fore to aft. A determinism sweep
  over representative seeds shows beam genuinely varying with frame; a symmetric
  or rectangular result fails the family.
- Depth-from-entry correlates with longitudinal distance from the breach point,
  so the assault gradient runs along the axis rather than radially.
- Transverse bulkheads appear in the graph as an ordered chokepoint sequence,
  and every hatch is passable.
- Every compartment is reachable from the spine, and the deck remains connected
  and deployable across the seed sweep.
- Consumers read zone, frame, and spine membership from the published graph. No
  test or stage re-derives them from cell coordinates.

## Measured: circulation is about a fifth of a walkable deck

Counted 2026-08-28 over three hulls, generating each from its own outline:

| Hull | Deck box | Walkable | Corridor cells | Room cells | Compartment area |
|---|---|---|---|---|---|
| Starliner | 428x185 | 33212 | 6435 (19.4%) | 26777 | 26777 |
| Legion | 273x274 | 29953 | 6043 (20.2%) | 23910 | 23986 |
| Valkyrie | 257x79 | 9045 | 1951 (21.6%) | 7094 | 7246 |

Two things follow. The pack is real rather than an artifact of what the plan
draws: compartment area and actual walkable room cells agree to within a
percent, so the rooms genuinely occupy the deck they appear to. And the spine
and its branches are a fifth of the walkable ship, which a plan drawing only
compartments leaves invisible - the deck plan now draws them.

## Known: a large program occasionally fails to pack

Measured 2026-08-28 over eight seeds per hull, generating each from its own
collision outline:

| Hull | Lift | Berths placed across eight seeds |
|---|---|---|
| Starliner | 1450 | 1458 x7, then 423 |
| Legion | 800 | 801 x7, then 135 |
| Valkyrie | 240 | 243 on every seed |
| Conquest | 100 | 108 on every seed |
| Atlas | 50 | 54 on every seed |

So this is not a ceiling on program size — the two largest programs pack
completely on most seeds. It is an occasional collapse, roughly one seed in
eight for the big hulls and never seen on the small ones, that drops the deck to
a fifth or a third of what the ship owes. A player would experience it as one
particular ship being inexplicably cramped, permanently, since the seed is
stable per hull.

An earlier reading of this blamed program size. That was an artifact of
generating from a synthetic taper; hulls laid out inside their real outlines
place far more, and the failure that remains is intermittent rather than
size-related.

## Measured: the detours are real, and nine in ten of them are unfixable

Counted 2026-08-28 over five vanilla hulls at two seeds each, looking only at
pairs of compartments whose doors lie within twenty-five cells — the pairs a
person would expect to step between. Ratio is walked distance over
straight-line.

| Hull | Near pairs | p90 before | over 3x before | of those, joinable at all | over 3x after |
|---|---|---|---|---|---|
| Wolf 42 | 82 | 1.29 | 0 | 0 | 0 |
| Valkyrie 42 | 251 | 5.50 | 45 | 7 | 43 |
| Valkyrie 7 | 225 | 3.87 | 36 | 4 | 35 |
| Eagle 42 | 168 | 3.25 | 24 | 2 | 19 |
| Eagle 7 | 185 | 3.43 | 29 | 6 | 18 |
| Conquest 42 | 390 | 4.88 | 82 | 9 | 72 |
| Conquest 7 | 572 | 4.40 | 101 | 4 | 95 |
| Atlas 42 | 142 | 3.67 | 23 | 2 | 23 |

Three readings, and the third is the one that matters.

The **detour is real**: a tenth of near pairs on every hull above frigate size
are walked at three to five times their straight-line distance, and the worst
single pair on the Valkyrie is eighteen cells apart and two hundred and fourteen
cells of walking.

The **pass takes what is there**: nothing on the Wolf, which has no detour worth
fixing, and three to six links on the larger hulls. It beats the per-pair
"joinable" count because one link fixes several pairs at once.

The **ceiling is the packing, not the search.** Only about one badly-detoured
pair in ten can be joined by any legal cut, because a passage may never take a
cell a compartment stands behind and in a packed warren every scrap of leftover
deck is within one cell of a room. Meanwhile a third to two fifths of the hull
sits empty aft, where no corridor needs it. That is the same defect
`facility-room-themes.md` is tracking from the other end: the deck's empty space
is in the wrong place. Raising the detour bar higher, cutting longer links, or
searching harder were all measured and none of them moved it — the answer is to
pack so that circulation survives, not to let a link open a compartment.

## Out of scope

Compartment interiors, fixtures, ambient life, upgrades, and any adoption by an
operations screen. This story ends at a validated empty deck with correct
topology.
