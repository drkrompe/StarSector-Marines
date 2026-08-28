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

## 5. Annotate it

Slicing and cutting are mechanical; deciding what each piece *is* is not. Both
halves are reachable from a shell, and neither needs the window:

```bash
tools/authoring.sh tileset_slice '{"name":"<name>","apply":true}'
tools/authoring.sh tileset_split_on_grid '{"name":"<name>","apply":true}'
tools/authoring.sh tileset_set_block @block.json
tools/authoring.sh tileset_export '{"name":"<name>"}'
```

See the `authoring-tools` skill for the whole surface and for the argument
forms; the same tools are served over MCP to a session that has the server
registered. A fused plate slices to one piece, which is the piece
`tileset_split_on_grid` cuts into the document's stated `gridCols` x `gridRows`.

**Slice once, then cut.** Running `tileset_slice apply=true` a second time on a
sheet that has been cut or annotated is refused rather than applied: a fused
plate has no gutters, so it is found whole again and every cut cell reconciles
to nothing. The refusal names what would go and how to override it. Do not
override it to get past this step — the override discards the annotation, which
is the part nothing can re-derive. Tuning `alphaMin` on an alpha-keyed sheet is
untouched by this: those pieces are mechanically derived and may be dropped and
re-found freely.
Each cut cell is named for where it sits — `<idPrefix>.c<col>r<row>`, zero-based
and column first, so `doodad.urban.c6r1` is the seventh cell of the second row —
which is how a row in the document is found in the picture. Pieces found by
alpha have no grid position and are named serially instead.
Everything that writes previews by default — pass `apply=true` to keep it.

`tileset_set_block` answers with what every slot it filled *means*, and reading
that back is the point of calling it: slot names read as **"the exterior is on
this side"**, not "the neighbour is a wall". A mirrored assignment still loads,
still resolves and is still opaque, so nothing downstream can detect it. Assign
a few slots, read the meanings against the art, then `apply`.

Per-piece footprint, cover, id, note and tags go in through
`tileset_write_document`: read the document, edit that object, write it back.

In the window, for a sheet in front of a person:

```bash
gradlew.bat layerAuthoring
```

Tilesets page → pick the sheet from the project list → **Open**. A seeded
document slices on open. **Split selected on grid** and **Group selected as
block** are those same two acts, filling slots in the selection's reading order,
and the preview draws each block as a room — the visual counterpart of the slot
descriptions.

Export writes the packed atlas, its tileset, and a `*.tileset.md` catalog card.

## Do not

- Guess a layout, or accept the `1 x 1` default when the grid is unknown.
- Write `TODO` into a committed note.
- Export over a shipped tileset without saying why in the note first.
- Add a per-piece facing field. Facing is a block layout; see
  `moddable-tilesets-nouns.md`.
