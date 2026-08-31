# Art source

Pre-pack art inputs and the scripts that turn them into shipped assets.

`mod/` is what ships: `deployMod` is a `Sync` of that whole folder into the
Starsector mods directory, so anything left inside it is copied to every install.

**The rule: `mod/` holds the built asset and the data that describes it, never
the thing it was built from.** Raw generated sheets, ImageGen masters, retained
`sources/` originals, annotation documents, and the scripts that consume them all
live here instead — roughly 135 MB of them, which is more than the art they
produce.

They stay version-controlled rather than local-only, because a sheet has to be
re-derivable and re-annotatable later, and because the annotation of a sheet is
work that cannot be redone mechanically.

| Directory | Holds |
|-----------|-------|
| `tilesets/` | Raw tileset sheets, their authoring documents, and the normalization and atlas-packing scripts. |
| `armory/` | ImageGen masters for Armory item icons. The shipped icon is the master downscaled to a 512px long edge, which `RawArtStaysOutOfModTest` enforces. |
| `fx/` | Scripts that derive shipped battle FX textures. `build_smoke_field.py` lifts the shared particle sheet's wreck-plume frames to a cool white so a deployed screen reads as equipment rather than damage; its source is a shipped asset rather than a master here, because matching the plume exactly is the point. |
| `doodads/` | The whole prop chain: ImageGen masters, raw renders, the scripts that derive frames from them, the derived `sources/`, and the atlas builder. |
| `alien-modular-topdown/` | Retained alien layer originals and the script that normalizes them. |
| `mech-modular-topdown/` | Retained mech layer originals and faction chassis masters, the alpha cleaner and layer builder, and the base/faction contact-sheet renderers. |
| `marine-modular-topdown/` | Retained marine body/head/weapon originals, their prompt recipes, and the variant builder. |
| `colonist-modular-topdown/` | Retained colonist layer originals. The layers they produced are checked in; no builder is currently kept. |

Tileset authoring documents — one per annotated sheet, written by the Tilesets
page of `gradlew.bat layerAuthoring` — live in `tilesets/` beside the sheet they
annotate, as `<name>.tileset-authoring.json`.

## Seeding a tileset for annotation

The Tilesets page lists every sheet it finds here, so dropping a raw sheet into
`tilesets/` is enough to make it appear as work to do.

Seeding it is the next step, and it is a procedure rather than a preference —
the `ingest-tileset` skill in `.claude/skills/` walks through it, and
`ProjectTilesetSeedsTest` fails the build for a raw sheet that arrives without a
document. Measure the sheet first:

```powershell
tools/authoring.sh tileset_measure '{"sheet":"<name>","gridCols":10,"gridRows":10}'
```

That reports what can be read off the pixels — size, whether there is a usable
alpha channel, how many pieces each alpha threshold finds — and drafts the seed.
It deliberately **does not detect the cell grid**: seam-energy and
autocorrelation were both measured against sheets whose grids were known and both
read noise, so it verifies a stated `gridCols`/`gridRows` instead of inventing
one.

Measuring is a Java tool (`SheetMeasurement`, reached through `tools/authoring.sh`
or the MCP server) rather than a script here, because it needs no Python
install and shares one implementation with the slicer whose thresholds it
reports. A `measure_sheet.py` once did the same job and was deleted rather than
maintained as a second answer to the same question.

The draft's `note` is a placeholder, and replacing it is the ingest step no
measurement can do. A seed:

```json
{
  "sheet": "art-source/tilesets/reactor-hall.raw.png",
  "sheetName": "reactor-hall",
  "idPrefix": "doodad.reactor-hall",
  "cellPx": 64,
  "alphaMin": 40,
  "gridCols": 4,
  "gridRows": 4
}
```

- `sheet` — the raw art, project-relative.
- `sheetName` — base name for the exported atlas, tileset and catalog card.
- `idPrefix` — prefix for generated piece ids before they are renamed.
- `cellPx` — cell size of the exported atlas; keep it at or above the game grid
  so a finely drawn sheet keeps its detail.
- `alphaMin` — alpha at or above which a pixel counts as art. Raise it when a
  soft key fuses the whole sheet into one piece.
- `gridCols` / `gridRows` — the plate layout the sheet was drawn to. Stated, not
  measured: it is what you asked the generator for, and it cannot be read off the
  pixels. **Cells need not be square** — a 20-frame strip is `20 x 1` — and every
  cell size is derived from this and the sheet's own size. `1 x 1` means the
  sheet is not a plate.
- `outputSheet` — optional. Where the packed atlas is written under `mod/`. When
  absent it follows the content: a sheet declaring blocks is terrain and goes to
  `graphics/tilesets/`, one that is only props goes to `graphics/doodads/`.
- `note` — what is true of this sheet, shown when it is opened. Slice settings
  say how to cut a sheet up but not what it *is*: that it has no alpha channel
  and arrives as one fused plate, that its frames are not square, that it is a
  patch composited into another sheet rather than a tileset of its own. A sheet
  listed as `seeded — see note` is telling you to read this before slicing.

Every raw sheet in `tilesets/` is seeded. Three are ready to cut — select the
single fused piece and split it on the grid the note gives. The other three are
seeded to record why they are *not* a straight ingest, which is worth more than
leaving them in the queue as untouched art.

`blocks` may also be pre-declared, so the walls a sheet is known to contain are
named before anyone opens it:

```json
"blocks": [{ "id": "reactor-hall.wall", "layout": "wall-3x3", "fillRgb": "0x060A10" }]
```

What a seed cannot supply is which piece is which: that needs the slice, which
needs the image. Seeding sets a sheet up; annotating it is still the work the
page exists for.

## Writing a script here

Every script computes the repository root and addresses `mod/` from there:

```python
HERE = Path(__file__).resolve().parent
REPOSITORY = HERE.parent.parent
ROOT = REPOSITORY / "mod" / "graphics" / ...
```

Each domain sits exactly one level under `art-source/`, so `HERE.parent.parent`
is the repository root everywhere and the idiom does not have to be re-derived
per script. Never reach a shipped path by counting `.parent` hops up out of
`mod/`: that is what made the previous layout fragile, and it silently retargets
a write the moment anything moves.

Run scripts from the repository root, for example:

```powershell
python art-source/doodads/stitch_atlas.py
```

## What stays in `mod/`

Documentation of the **shipped art** stays beside it — the `README.md` in each
`*-modular-topdown/` directory describes the runtime layer set, which is what a
reader standing in that directory wants. Documentation of **how the art is
produced** moves here with the pipeline: `doodads/README.md` is the atlas
builder's manual, and `marine-modular-topdown/PROMPTS.md` is a generation recipe.

No script stays behind. A script that derives shipped art from shipped art —
`mech-modular-topdown/render_variants.py`, which reads the checked-in mech layers
and writes a contact sheet into `roadmap/` — is still a build step rather than an
asset, and nothing about it needs to sit next to the art it reads.

`RawArtStaysOutOfModTest` enforces the boundary from the other side: no
`.raw.png`, no `*.tileset-authoring.json`, no `imagegen*` directory, no
`sources/` directory, and no `.py` file survives under `mod/`.
