# Ruins vault-theme props

Props and floor patches that tell a ruins vault's prize where the game's own
furniture falls short (`RuinsThemeDressing`, `ruins-expedition.md`, Inside the
ruin). Geometry-first (`geometry-first-imagegen.md`):

1. `python art-source/ruins-themes/make_guide.py` draws the block-out guides
   into `art-source/templates/ruins-<family>-guide.png`.
2. Each family is one Codex ImageGen call (`tools/codex_imagegen.py`) with its
   guide as layout authority, the brief in `prompts/<family>.prompt.txt`, and
   the shipped `civic-server-rack`, `galley-cold-store` and
   `industrial-reactor-housing` sources as material references (the patches
   use the frost and vats raws instead). The untouched result and its
   provenance live in `raw/`.
3. `python art-source/ruins-themes/export.py` cuts the shipped pieces and packs
   `mod/graphics/doodads/ruins-themes.png` with
   `mod/data/tilesets/ruins-themes.tileset.json` at 128 px a cell, 24 cells wide; metadata
   (ids, cover, footprints) is `_atlas.json`.

Shipped: `frost` (cryo rack, frost heatsink), `vats` (culture vat, grow bed),
`patches` (two frost, two growth), and the plant kit every theme's halls share:
`plant-line` (line module 6x3, line end 3x3, empty cradle 3x3) and
`plant-store` (rack rows 6x2 stripped and with crates, dock door 4x2). The
plant sheets used the shipped `industrial-machine-tool` source in place of the
cold store as a material reference.

The line module joins end to end along its long axis (east-west as shipped)
and the line end's bed leaves by its east edge: `export.py` cuts both at
roller-gap centres from one shared vertical window, so every seam meets gap to
gap (its comment records the measured boxes). The empty cradle's middle is
open: a 2x2 to 3x3 product stands in it.

Per-theme products, each whole, half-built and wrecked (`ruins-vault-facility`): `products-a` (cryo rack unit,
culture vat module), `products-b` (drone frame, forge cradle core), `products-c` (AI core cabinet, fusion lamp
field housing, synchrotron magnet segment), `products-d` (catalyst bed, fullerene spool drum) and `products-e`
(mantle bore head, holosuite projector stack), ids `doodad.ruins-<product>-<state>` with
states `whole`, `half`, `wrecked`. Each is cut at its guide frame and keeps the whole frame as its canvas on a 3x3
footprint, so the three states share one scale and stand in a cradle or across a line module. Armory and depot keep their existing props.
The states' block-outs are seeded per product (`make_guide.product`): half-built plating comes and goes panel by panel
on a 4x4 grid, never along one straight cut; a wreck keeps nearly its whole size, tipped a little, torn on a seeded
side, with its torn piece and smaller debris lying against the tear over a low patch of soot. Every brief carries a
strict top-down clause (no front faces, no oblique look into an opening): the first toppled crucible and pour pulpit
were drawn from the side because their guides invited it.

The foundry hall (`ruins-forge-area`): `foundry-a` (crucible, toppled crucible, fallen ladle, slag heap, pour
pulpit) and `foundry-b` (mould grid 8x5, grate catwalk 2x2). The toppled crucible has its own sheet (`foundry-tip`, its vessel identity taken from the
`foundry-a` raw): lying on its side seen straight down, its spill a thin flat puddle run out of the mouth, still molten
only in its core. Each crucible ships as a body and an overlay on the same
footprint, `crucible-melt` and `crucible-tipped-spill`: `export.py` splits the raw by the guide's melt mask
(`make_guide.MELT`), so the overlay registers exactly and the engine can pulse it. The mould grid's cells are
transparent, so the drop's melt shows through. The catwalk deck's span runs east-west with both ends flush, and it is
drawn as the deck of the drop's own crossing cells. The drop's firebrick wall and cast-steel lip strip are textures,
not props: `art-source/voids/foundry/` (`make_guide.py`, `derive.py`).

The ruins' leaf doors (`ruins-doors`, `LeafDoorArt`): the `doors` family ships as single images in
`mod/graphics/doors/` beside the ship's, at the leaves' 160 px a cell (`export.py`, `export_doors`): `ruins-leaf`,
`ruins-leaf-sealed`, `ruins-cargo-leaf`, `ruins-blast-leaf` (each a single leaf, its jamb end left and its meeting
end right, cut a little inside its frame and made opaque so it is solid to its ends), `ruins-leaf-torn`,
`ruins-blast-clamp`, `ruins-door-jamb` (its lamp lens plain, for the renderer to tint) and `ruins-door-rubble`.
The ship's own leaves, cargo pair, hardened leaves and jamb were the style references.

The mega forge hall's head (`ruins-themed-environments`, pick b1) ships on its own sheet, `ruins-forge`
(`_atlas-forge.json`): the 12x16 great crucible with its melt split off as `great-crucible-melt` (graded as the
upright crucible's), its control core housed on its west flank and its spout flush with its east edge; and that core
cut free on its skid, `furnace-core`, 3x3. Its guide is drawn at 112 px a cell (`make_guide.FAMILY_CELL`) so the
whole crucible is one block-out ImageGen takes at once.

The pour line (`forge-line`, 160 px a cell) shares that sheet: the 6x7 tapping furnace, its taphole and spout trough
running to its south edge (`tapping-furnace-melt`); the 3x3 ladle on its crane hook, upright (`ladle-hook-melt`)
and tipped mid-pour (`ladle-hook-tipped-pour`), with no cover since it hangs overhead; the 2x3 ore car and its
derailed state; the 2x4 cooling rack, empty and with castings; the 2x2 fettling bench, inspection table, pour head
and mould master (the b3 hauls); and the 7x1 fallen crane girder. Their hazard trim is muted as the spool drum's is,
and their melt graded as the crucible's.

The forge floor (`forge-floor`, 160 px a cell) shares it too: the 2x2 rail bed, its 4x4 quarter curve (meeting a
straight bed at its south and east edges) and the 2x2 buffer stop; the 2x3 slag car (`slag-car-melt`) and the 3x3 slag
car tipped at the pit lip (`slag-car-tipped-spill`, running out of its east edge); the 3x3 pallet bay markings; the
2x2 trench cover, the 1x2 drain gutter and the 1x1 floor drain; the overhead 1x4 crane runway and 1x2 cable tray;
the 1x4 fallen tray. The slag pit's edge is the `FOUNDRY` drop's own `foundry-lip`. The runs (rail bed, trench cover,
drain gutter, crane runway, cable tray) run north-south and are healed in `export.py` (`heal_run`) into a seamless
repeat along their length.

The frost and vats sheets' own patches are concept-only and not exported.
`raw/ruins-forge-concept.png` is the forge floor and shaft head concept
(assembly cradle, shaft head, hoist winch), not yet shipped.

Review: `gradlew.bat createSnapshots -Psnapshot=ruins-themed-site-look`, the
`inside/` cryoarithmetic-engine and soil-nanites shots.
