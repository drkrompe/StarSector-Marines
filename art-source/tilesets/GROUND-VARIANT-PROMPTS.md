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

(Since then `floors.dirt` has been widened to eight materials as well, and
`floors.snow` created with eight. `floors.grass` is the last three-cell plate
pool. Note that "carries real variants" was always a claim about the plate, not
about what reaches the screen — see the closing section.)

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

## Family: arid dust — asked for `floors.sand`, shipped as `floors.dirt`

**Read the closing section before reusing this prompt.** It was written to
extend `floors.sand` and matched dirt instead, because the atlas moved under it
while it ran; it now fills `floors.dirt`, whose palette it happens to hit
exactly. `floors.sand` was regenerated separately as `sandvar-*`.

It was matched against the three cells then at `(0,1)`, `(1,1)`, `(2,1)` of the
same sheet (pixels x=0..167, y=56..111).

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

## Family: frozen snow -- fills `floors.snow`

Unlike the two families above, this one has nothing to match: `GroundKind.SNOW`
was declared years ago and never emitted by anything, so there is no shipped
cell to sample. `regolith-1.png` is passed in as a reference for **format only**
-- size, opacity, and how fine the detail has to be -- with the palette stated
in words instead.

Wind-packed snow over ice on an airless world. Cold pale blue-white and
desaturated, around a mean of (206, 214, 224), never pure white and never fresh
powder. Eight variants:

| # | ask |
|---|---|
| 1 | even fine granular snow, featureless |
| 2 | faint scatter of slightly paler wind-drifted flecks |
| 3 | one very faint hairline crack in the ice beneath |
| 4 | shallow soft-edged patch where the blue ice shows through |
| 5 | loose cluster of small wind-carved ripples |
| 6 | very slightly coarser overall, no distinct features |
| 7 | two or three faint pale wind-scour streaks |
| 8 | sparse dusting of darker grit frozen into the surface |

## Running it

```bash
codex exec -s workspace-write --skip-git-repo-check "<prompt>"
```

`--full-auto` is not a flag in codex-cli 0.149. Write outputs to
`art-source/tilesets/ground-variants-raw/`. **Forbid git in the prompt** —
`codex exec` is an agent with repository access and will commit unasked.

## The wrap is made here, not asked for

`codex exec` returns a large square picture of a material. It cannot return a
tile: the model has no way to see its own edges, so no amount of prompting makes
opposite ones meet. `normalize_ground_variants.py` takes the masters and
produces the tile -- a double-sized centre crop, downscaled, then wrapped by a
four-corner blend that is periodic by construction rather than by inspection.
It also shifts each tile onto the family's mean colour, which is a constant per
channel and leaves the texture alone; the snow batch landed at a sibling spread
of 8.2 and came out at 0.9.

**Do not ask the agent to do this step.** `codex` runs under a pwsh that has
neither `magick` nor any Python its `py` launcher can find, so it will generate
the masters, fail every post-processing attempt, and sit there. Its masters are
written to `$CODEX_HOME/generated_images/<session>/` regardless, which is where
to pick them up from:

```bash
python art-source/tilesets/normalize_ground_variants.py     ~/.codex/generated_images/<session> art-source/tilesets/atlas-material-source/<family> <prefix>
```

## Measured thresholds

Calibrated against the shipped cells rather than chosen in advance. The sheet is
passed in rather than named inside the script, because `OneProducerPerSheetTest`
reads a script that mentions an exported atlas as a second producer of it — the
right law, even though this one only reads:

```bash
python art-source/tilesets/verify_ground_variants.py --baseline     mod/graphics/tilesets/Floors_Tiles.png 8,0 9,0 10,0
```


| | shipped range | threshold |
|---|---|---|
| seam ratio (wrap vs interior step) | 1.19 – 3.54 | <= 3.5 |
| sibling colour spread within a family | 1.4 (dirt), 4.3 (grass) | <= 6.0 |

A seam ratio near 1 means the wrap looks like any other step across the tile,
which is what seamless means. `floors.stone` scores 1.53 while being a single
picture, so passing the seam check is necessary and not sufficient — the colour
spread is what catches a family that came back as different materials.

## Verify before believing it

```bash
python art-source/tilesets/verify_ground_variants.py     art-source/tilesets/ground-variants-raw --family regolith
```

`verify_ground_variants.py` in this folder checks the contract mechanically:
exact size, full opacity, tile-against-self seam energy versus interior energy,
and per-family colour spread. ImageGen does not report when it has ignored a
constraint, and on past batches most of a first pass failed at least one.

**`--family` is required whenever the directory holds more than one.** Colour
spread is a within-family measure, so run over a mixed folder it reports the
distance *between* families — `ground-variants-raw` now holds regolith, dust and
sand, and measuring the lot scores 75 against a threshold of 6 with every tile in
it fine. The flag used to be only the report's label and this command was
therefore already lying by the time a third family arrived; it now selects the
tiles too, and a name matching nothing is an error rather than a silent pass.

## Reference the material file, not an atlas cell

The first arid batch was told to match "the cell at x=0, y=56" of the exported
atlas. Between launching it and its finishing, that atlas was re-exported with
eight new stone cells, which moved every block: `floors.dirt` landed on the
coordinates `floors.sand` had occupied. The batch matched what was there and
came back a faithful mid-brown, mean (131.6, 89.4, 52.6), which is dirt's colour
rather than sand's (162.5, 134.9, 81.7).

Nothing misbehaved. A packed atlas is a build output whose layout moves whenever
anything is added, so it is the wrong thing to point a long-running job at.
Point at the tileable material under `atlas-material-source/` instead — it is
the actual source, it has a stable path, and it is what the pool draws from
anyway.

Those eight tiles were kept as `dust-*.png` and parked. They are now ingested,
as `floors.dirt`'s eight materials — but the paragraph that used to sit here was
right about what that does and does not achieve, and the reason is worth keeping.

`GroundRenderSystem` draws GRASS and DIRT from the sliced nature strip and falls
back to the `floors.*` block only when that sheet fails to slice, which in a
loaded game it does not. So `floors.dirt` is the **degraded** path, and
widening it makes that path consistent with `floors.stone`, `floors.sand` and
`floors.snow` without changing a shipped map. That asymmetry is also why the
regolith batch worked on sight: STONE and SAND have no nature-strip entry and go
through the `floors.*` block directly.

**What DIRT actually draws is worse than any of the pools this document was
written about.** `TileManifest.pickNatureDirtTileId` hashes between
`nature.dirt-1` and `nature.dirt-2`, and those two frames of `nature-tiles.png`
are **byte-identical** — so every dirt cell in the game is one picture, and the
pair is a no-op exactly the way `floors.stone`'s three cells were. `nature.grass-1`
and `nature.grass-2` are byte-identical too, which makes GRASS the same defect
on far more of the map.

Fixing that is a different shape of job: the strip is an auto-strip addressed by
frame index rather than a variant pool cut on a grid, and `pickNatureDirtTileId`
hashes over a hard-coded pair rather than over a pool. It also has a palette
decision in it that this batch does not settle. The nature strip's dirt is a dull
olive-brown, mean (91.9, 79.3, 46.0); the dust family is a warm orange-brown at
(131.6, 89.4, 52.6), because it was matched to the Floors plate. Dropping these
eight onto the strip unchanged would recolour every dirt cell on every temperate
map. Re-normalize them onto the strip's own mean first —
`normalize_ground_variants.py` shifts a family's colour and leaves its texture
alone, which is the step that keeps a variety fix from becoming an art change.
