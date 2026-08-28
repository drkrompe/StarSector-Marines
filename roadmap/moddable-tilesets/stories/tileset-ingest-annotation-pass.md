# Tileset Ingest and Annotation Pass

Status: Proposed

Written: 2026-08-28

Read `moddable-tilesets-nouns.md` before changing this story.

## Problem

A generated art sheet arrives as one PNG with no structure. Turning it into
loadable content means deciding, piece by piece, what each thing is and how much
deck it covers — and those decisions have nowhere to live.

The Tilesets page already slices a sheet reliably and packs only the pieces
marked included, so "find the pieces" and "ship only what we use" are solved.
Four things are not:

1. **Only doodads can be authored.** A sheet's walls and corners cannot be
   expressed at all. Every wall in the game is still a hand-written `blocks`
   entry pointing at a hand-counted origin on an unpacked sheet.
2. **An annotation pass cannot be resumed.** Reopening a sheet re-slices from
   scratch and regenerates ordinal ids, discarding every footprint, cover and id
   decision from the previous sitting. Annotation is therefore a single
   uninterruptible sitting per sheet, which is why it does not happen.
3. **Raw inputs ship.** `mod/graphics/tilesets/imagegen-source/` holds the raw
   sheets and their normalization scripts, and `deployMod` is a `Sync` of `mod/`,
   so several megabytes of pre-pack input are copied into the game folder.
4. **Nothing records what a piece is for.** An id and a cover level do not say
   that a piece is an exterior industrial wall rather than an interior office
   one. The next person to choose between them — frequently an LLM asked to
   assemble a map — has only the id to go on.

## Scope

Make the raw sheet the input and the packed atlas the output, with a resumable
annotation pass between them that can express walls and corners as well as
doodads, and that records what each piece is for.

Out of scope: changing how the game renders any existing tileset; a new
autotile geometry; selecting tilesets per map; migrating the existing
hand-written `blocks` entries to authored output. This story makes authoring
possible, not retroactive.

## Model decisions

### Facing is a layout, not a field

A wall with a facing is already modeled: a `blocks` entry with an `origin` and a
named `GridLayout` such as `wall-3x3`, whose facing is resolved per cell from
the four-neighbour mask by `GridLayout.resolve`. Corners are the same layout's
diagonal cases.

This story therefore does **not** add a per-piece `facing` field. Such a field
would be a second, parallel answer to a question the model already answers, and
would move geometry authority out of code and into art data — the exact
inversion `moddable-tilesets-nouns.md` prohibits. Instead each sliced piece gains
a **role**: an excluded piece, a doodad, or a named slot in a named block.

The mask each layout consumes means **"the exterior is on this side"**, not
"the neighbour is a wall" (`WallMasks.pickTileFromMask`). Slot labels in the
authoring UI must be worded that way. Inverting it silently produces a sheet
whose walls are inside out, which no validation can catch.

### Blocks pack as a patch, not as pieces

`GridBlockDef` addresses its members by an origin plus a layout-relative offset,
so a block's nine cells must be contiguous and correctly arranged in the packed
atlas. The current shelf packer places each included entry independently and
would scatter them. Packing gains the notion of a multi-cell group placed as a
unit; the resulting `origin` is then a packer output, never a hand-counted
number.

### The hint is a cell label

`cells` entries already carry `name` and `description` keyed by
`(sheet, col, row)`, and the noun doc already establishes them as descriptive
only, never participating in generation or combat. That is exactly the authority
an LLM usage hint should have, so the hint is written there rather than as a new
field on `DoodadDef` or `GridBlockDef`, neither of which grows a field in this
story.

`CellLabel` gains a `tags` list alongside its existing text, for the greppable
half of the annotation. Tags stay descriptive: a tag may never become a
generation or tactical selector without going through the code path that owns
that law (validation law 5).

The primary artifact for an LLM is not the JSON but a generated catalog card
beside it — one row per authored id with its role, footprint, cover, tags and
note, so choosing content does not require reading atlas coordinates.

### Raw art is not mod content

Raw sheets and the authoring documents that annotate them move to `art-source/`
at the repository root. They remain version-controlled and reviewable; they
simply stop being inside the folder that `deployMod` synchronizes. `mod/` keeps
only packed atlases and the tilesets that describe them.

## Chunks

### 1. Authoring document

A tileset authoring document persists one annotation pass: the raw sheet it
annotates, the slice parameters that produced its pieces, and per-piece id,
role, footprint, cover, inclusion, tags and note. Written to
`art-source/tilesets/<name>.tileset-authoring.json`, opened and saved from the
Tilesets page, and validated before atomic replacement like every other
authoring write.

Re-slicing an already-annotated sheet reconciles rather than replaces:
annotations are carried across by piece bounding box, a piece that no longer
matches is reported rather than silently dropped, and a newly-appeared piece
arrives unannotated. The page's dirty state covers the document.

Acceptance: annotate, save, close, reopen, and every decision is intact.
Re-slice at a different alpha threshold and surviving pieces keep their
annotations while the report names what changed.

### 2. Roles and block packing

Each entry gains a role. A doodad exports as today. A block member names its
block id, its layout, and its slot within that layout. An excluded piece is
neither packed nor exported, which is already true and stays true.

The page gains a "group as block" action over a multi-selection: the selected
pieces, in slice reading order, populate the chosen layout's slots, with per-row
slot override for the sheets whose reading order does not match. Because tileable
plates arrive fused and are separated by `SheetSlicer.splitOnGrid`, the common
case is splitting one 3×3 plate and grouping the nine results in one action.

`TilesetExport.pack` places each block's members as a contiguous patch and
reports the resulting origin; `TilesetExport.tileset` emits `blocks` entries
whose origin comes from the packer. `TilesetPreview` renders block members
through the same `GridLayout` the game uses, over a neighbour mask that exercises
each facing and both null cases, so an inside-out wall is visible in the page
rather than in the game.

Acceptance: a raw sheet containing one 3×3 wall plate and several props exports
an atlas and tileset that ingest into a real `TileRegistry`, whose `blocks` entry
resolves every facing to the intended art, and whose enclosed case falls to
`fillRgb`.

### 3. Raw and packed separation

`mod/graphics/tilesets/imagegen-source/` moves to `art-source/tilesets/`, with
its normalization and packing scripts. Path references are updated. A test
asserts that no raw input remains under `mod/`, so the boundary is enforced
rather than remembered.

Acceptance: `deployMod` copies no `.raw.png` and no authoring document into the
Starsector mods directory, and the existing tilesets still load unchanged.

### 4. Hints and the catalog card

Entries carry a free-text note and a tag list. Export writes them as `cells`
entries against the packed atlas and generates
`mod/data/tilesets/<name>.tileset.md`: one row per authored id with role,
footprint, cover, tags and note, plus the sheet's own summary.

`CellLabel` gains `tags`; the tileset viewer shows them. No generation or
combat path reads either the tags or the note.

Acceptance: an exported sheet's catalog card names every included id and no
excluded one, and an unfamiliar reader can pick an id for a stated purpose from
the card alone.

## Constraints

- Authoring code is tool infrastructure and must not enter the shipped mod jar:
  the page stays in root test sources, the generic host stays in
  `:layer-authoring` (see `CLAUDE.md`).
- Every write is validated before atomic replacement, and an atlas and its
  tileset are replaced together or not at all.
- No existing tileset's rendered output changes. This story adds an authoring
  path; it does not re-author shipped content.
