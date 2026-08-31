# Ground variant prompts

Prompts for extending the outdoor ground **variant pools** with ImageGen, via
`codex exec`. Companion to `art-source/doodads/IMAGEGEN-PROMPTS.md`, which
covers doodads.

**These are not doodads and the doodad contract is wrong for them.** A doodad is
one isolated object on a chroma key with real alpha. A ground tile is the
opposite: an opaque square that covers its whole cell, tiles seamlessly against
itself and its siblings, and has no subject, no padding, and no transparency.
The doodad acceptance check "alpha min is 0 and max is 255" would fail every
correct tile here.

## Why these variants

A variant pool is hash-picked per cell, and the pool is supposed to be what
stops a large field repeating.

**`floors.stone` and `floors.sand` declare three cells and hold one picture.**
All three cells of each are byte-identical; whoever cut them filled the slots
with copies. `floors.grass` and `floors.dirt` do carry real variants. So the two
families a barren or arid world is made of have no variation at all, and these
are the first variants they will have rather than extra ones.

That also means the static in the rendered maps was never the stone texture
repeating -- a uniform tile cannot produce speckle. It was entirely the
cross-material mix below.

The fault was ours rather than the art's: the barren surface palette drew
STONE, DIRT and RUBBLE per cell, which is three *different materials* hash-mixed
at cell granularity and reads as static however it is placed. The fix is one
material per surface, with enough variants that a single material does not
repeat, which is also why
**no edge or transition frames are needed**. Transitions only
matter where two materials meet, and a single-material field never does.

## The contract

Every tile in a family must satisfy all of:

- **Exactly 56x56 pixels.** That is `Floors_Tiles.tileset.json`'s `cellPx`.
- **Fully opaque.** No alpha channel, or an alpha channel that is 255
  everywhere. No chroma key, no padding, no cast shadow.
- **Seamlessly tileable against itself on all four edges.** The right column
  must continue into the left column, and the bottom row into the top row, with
  no visible seam when the tile is laid in a grid.
- **A sibling, not a different material.** Any two tiles in one family, laid
  side by side, must read as the same ground with different debris on it. If a
  viewer can tell where one tile ends and the next begins, it is wrong.
- **No directional lighting and no large features.** A ground tile is seen a
  thousand times at once; anything that reads as an object, a highlight, or a
  gradient becomes a visible lattice. Keep contrast low and detail fine.

## Family: barren regolith — extends `floors.stone`

Match the three cells already at `(8,0)`, `(9,0)`, `(10,0)` of
`mod/graphics/tilesets/Floors_Tiles.png` (each 56x56, so pixels x=448..615,
y=0..55). Open that file and sample its palette before drawing.

Cool mid-grey crushed stone over compacted grit. Eight variants, each differing
only in the arrangement and density of fine debris:

| # | ask |
|---|---|
| 1 | even fine grit, no feature larger than three pixels |
| 2 | fine grit with a scatter of slightly paler flecks in the lower half |
| 3 | fine grit crossed by one faint hairline fracture, corner to corner |
| 4 | fine grit with a shallow darker patch off-centre, soft-edged |
| 5 | fine grit with a loose cluster of small angular chips |
| 6 | fine grit, very slightly coarser overall, no distinct features |
| 7 | fine grit with two or three faint pale streaks, as if wind-scoured |
| 8 | fine grit with a sparse dusting of darker specks |

## Family: arid dust — extends `floors.sand`

Match the three cells already at `(0,1)`, `(1,1)`, `(2,1)` of the same sheet
(pixels x=0..167, y=56..111).

Pale warm tan wind-blown dust over hardpan. Eight variants:

| # | ask |
|---|---|
| 1 | even fine dust, featureless |
| 2 | fine dust with faint parallel ripple lines, low contrast |
| 3 | fine dust with a scatter of tiny darker pebbles |
| 4 | fine dust with one soft paler drift across a corner |
| 5 | fine dust over a barely visible cracked hardpan pattern |
| 6 | fine dust, very slightly darker overall, featureless |
| 7 | fine dust with a sparse scatter of pale grit |
| 8 | fine dust with a faint shallow depression, soft-edged |

## Running it

```bash
codex exec -s workspace-write --skip-git-repo-check "<prompt>"
```

`--full-auto` is not a flag in codex-cli 0.149. Write outputs to
`art-source/tilesets/ground-variants-raw/`. **Forbid git in the prompt** —
`codex exec` is an agent with repository access and will commit unasked.

## Measured thresholds

Calibrated against the shipped cells rather than chosen in advance
(`verify_ground_variants.py --baseline`):

| | shipped range | threshold |
|---|---|---|
| seam ratio (wrap vs interior step) | 1.19 – 3.54 | <= 3.5 |
| sibling colour spread within a family | 1.4 (dirt), 4.3 (grass) | <= 6.0 |

A seam ratio near 1 means the wrap looks like any other step across the tile,
which is what seamless means. `floors.stone` scores 1.53 while being a single
picture, so passing the seam check is necessary and not sufficient — the colour
spread is what catches a family that came back as different materials.

## Verify before believing it

`verify_ground_variants.py` in this folder checks the contract mechanically:
exact size, full opacity, tile-against-self seam energy versus interior energy,
and per-family colour spread. ImageGen does not report when it has ignored a
constraint, and on past batches most of a first pass failed at least one.
