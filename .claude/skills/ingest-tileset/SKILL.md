---
name: ingest-tileset
description: Ingest a new raw tileset sheet into the project - measure it, write its authoring seed, and hand it to the Tilesets page. Use whenever a raw art sheet is added to art-source/tilesets/, or when asked to ingest, seed, or set up a new tileset, tile sheet, or prop sheet.
---

# Ingesting a tileset sheet

A generated sheet arrives as one PNG with no structure. Turning it into loadable
content splits cleanly in two, and the whole point of this procedure is to keep
the halves apart:

- **Measured** — size, alpha, piece counts, the grid arithmetic. Never guess it.
- **Judged** — what the sheet is for, what each piece is, which blocks it
  contains, what the next reader needs warning about. Never let a tool invent it.

Your job is the judged half. Run the tool for the rest.

## 1. Put the sheet where the project looks

```
art-source/tilesets/<name>.raw.png
```

Never under `mod/` — that folder is synced into every Starsector install, and
`RawArtStaysOutOfModTest` fails the build if pre-pack art lands there. The `.raw`
marker is stripped when pairing the sheet with its tileset, so
`urban-tileset.raw.png` pairs with `urban-tileset.tileset.json`.

## 2. Measure it

```bash
tools/authoring.sh tileset_measure '{"sheet":"<name>","gridCols":10,"gridRows":10}'
```

This needs no Python and no server — see the `authoring-tools` skill for the
rest of the tool surface. `art-source/tilesets/measure_sheet.py` measures the
same things for a sheet that is not yet in the project.

Pass `gridCols`/`gridRows` whenever the sheet was generated to a layout you asked
for — which is most of the time, since you chose the layout in the prompt. Cells
need not be square: a 20-frame strip is `20x1`, and the split cuts exactly that
grid. **Nothing detects the grid for you** — seam-energy and autocorrelation
were both tried against sheets whose grids were known, and both read noise, so
it verifies a stated grid rather than inventing one. If you do not know the
layout, ask; do not guess a number into the seed.

Read the report before writing anything. It tells you which kind of sheet this
is, and the two kinds are annotated completely differently:

| Report says | What it is | How it is annotated |
|---|---|---|
| `alpha channel: yes`, several pieces | A cut-out prop sheet | Slicing finds the props directly. Set `alphaMin` where the piece count stops changing. |
| `alpha channel: NO` | A fused plate | Slicing finds one piece covering everything. That is correct: it is split on the grid. |
| Cells are not square | A strip, or any sheet not drawn on a square grid | Ordinary for generated art. The split cuts the stated grid exactly, so it is handled like any other plate. |

## 3. Write the seed

The measurement comes back with a drafted seed. Its `note` is a placeholder;
replacing it is the work. Write the finished seed with:

```bash
tools/authoring.sh tileset_write_document @seed-call.json
```

where the file holds `{"name": "<name>", "document": { ... }}`.

```json
{
  "sheet": "art-source/tilesets/reactor-hall.raw.png",
  "sheetName": "reactor-hall",
  "idPrefix": "doodad.reactor-hall",
  "cellPx": 64,
  "alphaMin": 40,
  "gridCols": 4,
  "gridRows": 4,
  "note": "...",
  "blocks": [{ "id": "reactor-hall.wall", "layout": "wall-3x3", "fillRgb": "0x060A10" }]
}
```

- `cellPx` — cell size of the **exported** atlas. Keep it at or above the game
  grid so a finely drawn sheet keeps its detail.
- `gridCols` / `gridRows` — the plate layout on the **raw sheet**. Stated, never
  rounded from a pixel size, and free to be non-square.
- `blocks` — pre-declare the walls and corners the sheet is known to contain.
  A block's cells are chosen by `GridLayout` from a four-neighbour mask, so
  facing is never a field on a piece. Layouts: `single`, `floor-3x3`,
  `wall-3x3`, `perimeter-3x3`, `striped-3x3`.
- `note` — see below. This is the field that justifies the whole procedure.

### The note is the deliverable

Slice settings say how to cut a sheet up but never what it *is*. Write what the
next reader would otherwise learn by failing:

- the measured facts that change how it is handled — no alpha channel, non-square
  frames, an unusually soft key;
- what the sheet is for, in a sentence;
- anything that would make exporting it a mistake. **If the sheet's name matches
  an existing `mod/data/tilesets/*.tileset.json`, say so**: exporting over it
  rewrites ids and coordinates that `GenMappingRegistry` references.

A seed carrying a note lists as `seeded — see note` and shows the note when
opened. A seed with nothing to say is a test failure, not a style problem.

## 4. Verify

```bash
gradlew.bat :test --tests '*ProjectTilesetSeedsTest*'
```

This asserts every raw sheet has a document, every seed resolves to a real sheet
with usable settings, and every un-annotated seed explains itself. A new sheet
with no seed fails the build — that is the mechanism that makes this procedure
part of ingestion rather than a thing to remember.

## 5. Hand it over

```bash
gradlew.bat layerAuthoring
```

Tilesets page → pick the sheet from the project list → **Open**. A seeded
document slices on open. From there it is annotation, which is a person's job:

- For a fused plate: select the single piece, **Split selected on grid**, then
  **Group selected as block** for any wall or corner set. Slot names read as
  *"the exterior is on this side"*, not "the neighbour is a wall" — a mirrored
  assignment still loads and still resolves, so the preview drawing each block as
  a room is the only thing that catches it.
- For a prop sheet: set each piece's footprint, cover, id, note and tags.

Export writes the packed atlas, its tileset, and a `*.tileset.md` catalog card.

## Do not

- Guess a layout, or accept the `1 x 1` default when the grid is unknown.
- Write `TODO` into a committed note.
- Export over a shipped tileset without saying why in the note first.
- Add a per-piece facing field. Facing is a block layout; see
  `moddable-tilesets-nouns.md`.
