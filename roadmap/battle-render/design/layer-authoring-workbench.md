# Layer authoring workbench (`layerAuthoring`)

Status: ACTIVE

Written: 2026-10-20

Page-by-page detail of the `gradlew.bat layerAuthoring` workbench (Layers, Turrets, Rooms, Map damage, Tilesets), moved verbatim from `CLAUDE.md` (Build & deploy). Read it when working on or with a workbench page.

- `gradlew.bat layerAuthoring` → extensible standalone authoring workbench. The
Layers page provides drag, scale, rotation, variant-scoped phase-driven
animation playback, combined-sheet export, the shared snapshot
catalog, and validated atomic writes to
`mod/data/appearance/unit-layer-layouts.appearance.json`.
The Turrets page edits linked weapon, mount, structure, FX, and bounded
multi-turret defense-post layout data with a live deterministic preview.
It exposes specialized projectile/artillery behavior and audio; preview
flight reads the authored burst, boost, arc, contrail, and directional
launch-FX data instead of substituting a generic projectile treatment.
New sheets are ingested through the `ingest-tileset` skill: the
`tileset_measure` tool measures a sheet and drafts its authoring seed, and
`ProjectTilesetSeedsTest` fails the build for raw art that arrives without
one.
The Rooms page edits one shipboard room: its footprint, its deck, and the
fixtures standing on it. It opens on **which room?** — a grid of every room
the ship has, each one drawn as it generates today through the battle
renderer, so choosing is looking rather than reading twenty enum names. The
whole set costs one deck generation and the tiles fill in as they are drawn.
From there it walks four screens: the
footprint, the deck, the fixtures, and a comparison. **No room starts from an
empty grid.** Opening one runs the procedural fitting that owns it and records
what it did, so the first thing on screen is the room that already ships and
the first edit is a change to it.
The room is edited **on its own picture**: the battle renderer draws it as a
small map of its own and the grid marks that up, rather than replacing it with
coloured rectangles. Choosing between a vent plate and hazard striping is a
decision that can only be made by looking, and the flat-colour version was
perfectly clear about reservations while being useless for the one question
the deck screen exists to answer. Rendered at one cell per cell with no
surround, so the marks line up without arithmetic, and redrawn off the event
thread after every edit — a render that finishes after a newer edit is dropped,
so the grid keeps the last good picture instead of blanking. The marks are what
a render cannot say: which cells are deck, which are reserved circulation, and
which step is anchored where. The comparison
screen generates **the same hull at the same seed twice**, with the layout
suppressed and applied, and renders both — so a room drawn too large to fit
shows up as a missing room rather than as a surprise later.
**Both ways an authored room fails are silent**, so the page replays every
draft before trusting it. A fixture whose cell is taken is refused and the
room merely comes out sparser; an arrangement that severs its own circulation
has its whole fill thrown away and the room generates as bare deck. A
checkerboard of crates across an armoury — fifteen fixtures, each legal alone
— produced a compartment with nothing in it, while the count still said
fifteen. `RoomLayoutCheck` is what turns both into sentences, the fixture
count shown is what actually stands up, and a layout that would seal its room
is refused at save.
A room draws its **deck** from blocks too: paint a run with `road.vent`,
`apron.keep-clear`, a `floors.*` variant pool, anything that is not a wall. That
is flavour and nothing else — a vent run and a striped run are the same floor
to pathing, cover and sight — which is why it is kept separate from setting a
cell's `GroundKind`, the thing consumers actually read. The picker offers
every non-wall block rather than a list of floor layouts, because the ways of
being a floor keep growing and an enumerated picker would omit the next one.
A room may also name its own **bulkhead**, offered from the blocks that can
actually be a wall — shape rather than spelling, since `road.embankment` is
one. It changes the picture only; topology, cover and sight are untouched, and
where two rooms back onto each other the shared ring is one wall that the
later room wins. This uncovered a defect worth knowing about: a wall with no
exterior face draws its block's transparent centre, and no ship stage ever
stamped a face, so **every bulkhead on every generated deck was rendering as
nothing**. A wall nobody stamped now takes a face on each open side; a mask
somebody already set is never re-derived, so cities are untouched.
A kept room is **written and loaded**: `mod/data/world/rooms/` holds one
document per purpose and refit level, and `rooms.json` indexes them because
mod code reads through `SettingsAPI` and cannot list a folder. The tool
rebuilds that index from what is on disk rather than appending to it, since an
index that drifted from the folder is a room that silently stopped loading.
`RoomLayoutCatalog.loadBuiltins` installs them at application load, defensively
— an unreadable room generates the way it did before anybody authored it,
which is a worse room rather than a broken ship.
Reserved circulation is called a **walkway** on the page, drawn as hatching
rather than a tint — a translucent wash over a busy deck is invisible, and the
one thing an author needs to see here is which cells refuse furniture. A
seeded armoury comes back with six of its eight rows reserved, so walkways are
drawn and rubbed out cell by cell with a tool, plus a clear-everything command
for the common case.
**The deck screen shows its tool rather than hiding a mode.** An earlier
version had a "set the kind" button that silently redirected every later
click, so an author who pressed it once found that painting a floor did
nothing for the rest of the session — which is exactly what it looked like
from outside, and was reported as a bug in the painting. Click and drag paint
with the chosen tool; the room is redrawn once per stroke rather than per
cell. Zoom is explicit with a Fit default, since a vehicle bay is forty cells
across and a server room is five.
The Map damage page opens Relief's actual production map with named removable
damage layers. Paint scars, battered equipment or ruins; right-drag erases
from the selected layer. Layer visibility, removal, undo/redo and intact
comparison rebuild the same base recipe, keeping pristine geometry available.
Save writes `mod/data/world/damage/relief.damage.json` atomically. The shared
document and brush are recipe-independent; Relief is the first linked host.
`createSnapshots -Psnapshot=relief-damage` checks exact intact restoration,
production destruction and editor gestures, and renders at 1920x1080 / 100%.
The Tilesets page opens on a question rather than on a workspace: **what are
you doing?** Three ways in, each a walkthrough of numbered screens with Back
and Next, and each screen holding only the controls its own step needs. The
page used to present every command at once on one toolbar — seventeen controls
in the order they were written, with the cut, the annotation, the grouping and
the export interleaved — which is a palette for somebody who already knows the
procedure and nothing at all for somebody who does not.
**What can be a wall?** is about the surface, not about sheets. Screen one is
a grid of every `GroundKind` and `SurfaceRole` the generator can ask for, each
showing a 100px picture of whatever is drawn for it today, grouped
**Structure** then **Outdoors** and within each **Walls / Floors / Other**.
That grouping is a browsing aid owned by the tool, not vocabulary the game
consults — nothing resolves differently because a surface is filed under
Outdoors — and it is kept off `GroundKind` and `SurfaceRole` for exactly that
reason. Every surface must be filed explicitly; `SurfaceCategoryTest` fails
the build for one that is not, because where a surface belongs is a judgement
and there is nothing to derive it from. A preview paints the block's
`fillRgb` behind its cells, since that fill is what the renderer draws for the
case a hollow layout leaves empty — most of a courtyard is the fill, and shown
on a transparency checker it reads as an earthwork rather than paving. Screen two is **the set**: every block in the project that could fill
that surface, whichever sheet it is on, as a grid of pictures — because
`urban.wall` and `road.embankment` are both walls and are nothing alike to
look at. The one in use comes up selected and is drawn large beside the grid
**as a room**, three cells on a side, which is the only view that shows a
mirrored wall.
Four things can be done to the set. **Draw this one** points the surface at
the chosen block by editing the mapping in place — a surgical replacement of
one value, validated against the real catalog before it lands, because
re-serialising that file would reorder every key in it. **Add one from a
sheet…** hands off to the ingest walkthrough, which is the only place sheets
appear in this flow. **Remove from the set** dissolves the block, giving
its pieces back as doodads; it is refused while the mapping still points
there, since a surface with no block is a startup crash. Next opens the
block's sheet on a third screen, **Adjust the cut**.
That screen moves the selection's rectangle and nothing else. Slicing keys on
alpha and splitting divides by a stated pitch, and both are right most of the
time and wrong for a particular piece — a prop whose contact shadow was keyed
away with it, a cell whose seam sits a pixel off the line through its
neighbours — so re-slicing to fix one of them moves every other piece too.
**The selection moves as one grid**, because that is the shape the fault
usually has: a wall block's nine cells are one plate cut on one grid, and
correcting them a cell at a time is nine edits that have to agree and will
not. So the controls are a `GridCut`'s — where the first line falls, and how
big one cell is — and one piece is simply the 1x1 case. `GridPatch` derives
that grid from where the pieces actually sit rather than from their slot
names, since a selection may be a whole plate or a row of four; edges within
a quarter of a cell of each other are one grid line, so a cell already nudged
by a pixel stays in its column, and which lattice index each line holds is
recovered from the spacing, so a selection that skips a column still lands on
the right addresses. **A patch is a lattice, not a rectangle**: the selection
need not fill the grid it lies on. `floors.brick` is five cells in a plus —
one, then three, then one, on the sheet's own 47.55px pitch — which is an
ordinary variant pool cut on one grid, and requiring a filled rectangle
refused it for a reason that had nothing to do with the art. What is refused
is pieces no single origin and pitch describes, and the reason is written
where the picture would have been, because it is a paragraph and the caption
is a label in a split pane. The cut is drawn over the plate magnified, with
the seams between cells drawn and the excluded pixels dimmed, because a
boundary one pixel out is invisible at 1:1 and a pitch a fraction out shows
up against the interior seams rather than the boundary. Adopting a grid says
how far it would move the cells that are already there, and applies to all of
them or none. Saving applies the move, writes the document and re-exports,
since a cut is only fixed once the atlas is packed from it — and a re-export
drops the held atlas and thumbnails, because the picture a preview is holding
came from the sheet that was just replaced. `tileset_set_cut` is the
single-piece operation headless, previewing by default and refusing a
rectangle that leaves the sheet. The screen is also the fourth of the ingest
walkthrough.
**New art arrived** is the ingest sequence: pick a sheet, find the pieces,
say what each piece is, group blocks, name and size the output, save and
export. A screen will not advance until it has been answered, and says what it
wants rather than grepping out a disabled button — "Slice or split the sheet
so it has pieces to annotate".
**Just look at it** opens a sheet and shows the tileset as the game loads it
and a generated map drawn with it. Nothing on those screens writes.
The sheet list itself is found rather than browsed for: every sheet under
`art-source/tilesets/` with its state — raw, seeded, annotated, exported.
Dropping a raw sheet there is enough to make it appear; a hand-written document
carrying settings but no pieces is a valid seed and is sliced on open.
The page finds pieces by keying on alpha and proposes a footprint for each from
the sheet's stated `gridCols` x `gridRows` layout — cells need not be square,
and a fused plate is cut into exactly that grid — but footprints are edited
there rather than inferred, because
how much deck a piece covers is a judgement about the object, not a measurement
of the art.
**Fit grid to art** measures where that stated grid actually sits — generated
art sits inside a margin and is rarely drawn to a pitch that divides its own
pixel size evenly — and reports how many boundaries landed on a real seam and
how far they lie from the line through them. It applies only the axes that
measured well, and its dialog's fields are editable so the measurement can be
overridden. The cell *count* is never measured; only the placement is.
A fitted cut moves the plate's existing cells onto new rectangles, keeping
every id, block slot and annotation.
Pieces are picked on the sheet itself — click, ctrl-click to add,
shift-click to run, drag a box — and the table follows, because a cut cell's id
cannot be recognised in a list of a hundred. Each cell carries its `col,row` on
the picture, which is what lets a person and a model name the same cell.
**Copy selection for LLM** writes a labelled contact sheet of the picked cells
under `build/tileset-authoring/` and puts a table of their current annotation,
keyed by the same coordinates, on the clipboard with that image's path.
A piece becomes a doodad or a cell of a named block; walls
and corners are authored by grouping pieces into an autotile block's slots,
which the packer places as one contiguous patch. A block declared with layout
`variants` is instead a pool of interchangeable ground tiles picked by hashing
the cell — slots `v1`, `v2`, ... — written as an explicit cell list and packed
as a run, which is the shape `water.water` and the `floors.*` families load in.
A sheet whose document carries a
`strip` block exports as a sliced auto-strip instead: frames in a row at an
authored scale, addressed by frame index rather than `(col, row)`, which is
the shape `urban-tileset-3` and `nature-tiles` load in. A frame may declare a
`material` — its picture comes from a tileable file rather than from the plate
— or a `spriteBorderPx`, which mirrors away the drawn rim that would otherwise
tile as a lattice; both work on either shape, and a piece claiming neither is
packed as it was cut. Export writes a packed
atlas holding only
the included pieces, its `*.tileset.json`, and a generated `*.tileset.md`
catalog card; the atlas goes to `graphics/tilesets/` when the sheet declares
blocks and `graphics/doodads/` when it is only props, unless the document names
an `outputSheet`. Annotations are saved to
`art-source/tilesets/<name>.tileset-authoring.json`, so a sheet can be annotated
across several sittings; re-slicing carries existing annotations onto the newly
found pieces and names any that no longer match.
Its Map preview generates a city and draws it twice at one seed: as it ships,
and with pieces bound through the "stands in for" column painted over the
cells of the shipped id they are candidates for. The substitution is made in
the pixels, so the two maps are the same map and only the art differs. The
binding is preview-only and is never exported.
All three pages validate before replacement; the Turrets page prepares every
linked target before replacing files atomically and rolls back earlier files
if a later replacement fails.

## Headless tools and launchers

`tools/authoring.sh <tool> [json]` (or `tools/authoring.cmd`) calls one authoring tool and exits, with no workbench
window and nothing to start first: list what can be a wall, list, measure, read, write, slice, fit, split or export
a tileset, move one piece's cut, declare or dissolve an autotile block, render a map-preview comparison, run the
snapshot catalog. `--list` names the tools, `--describe <tool>` prints a schema, `--json` returns the structured
result. Arguments are one JSON object, inline, as `@file`, or `-` for stdin; from PowerShell quote the `@file` or use
`-`, because a bare `@token` is its splatting operator. Exit status is 1 when the tool reports a failure, 2 on a usage
mistake, and 3 when the freshness build failed, in which case nothing was called. See the `authoring-tools` skill.

**Always go through `tools/`, never through `build/authoring/`.** The generated launchers run whatever was compiled
last. Each wrapper runs `installAuthoringTools` first (about 0.7 s against a warm daemon), so a call cannot use
classes older than the working tree, and refuses to call anything if that build fails: a stale call once destroyed
a hand-cut tileset by running a safety guard's pre-guard classes. Build chatter goes to stderr; stdout stays the
tool result.

`gradlew.bat installAuthoringTools` writes the launchers under `build/authoring/` (they embed an absolute classpath,
so they are generated, not checked in) and prints the `.mcp.json` snippet that registers the same tools as an MCP
stdio server. Register `tools/authoring-mcp.cmd`, not the launcher it execs. An MCP server holds its classes for a
whole session, so after changing tool code restart it or use `tools/authoring.sh`, which rebuilds per call; prefer
the shell wrapper unless a session already has the server. Both are separate front doors onto the same domain code,
never an embedded server: an editor holding unsaved changes and a tool writing the same document would be two
writers (`authoring-entry-points.md`).

The workbench discovers top-level pages through `AuthoringPageProvider` services on the tool runtime classpath. Keep
the generic host in `:layer-authoring` and mod-domain pages (Turrets) in root tool/test sources, so the shipped jar
and the generic module do not acquire each other's dependencies. Its Snapshots tab runs the shared catalog in
process, off the Swing thread, confirms before replacing PNGs, and refuses `layers` evidence while the authoring
document has unsaved changes.
