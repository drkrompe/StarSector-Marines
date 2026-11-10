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

The forge's machinery is the spacefaring era's, as the raid mine's mass driver and hover haulers are (owner: an ore
car "does not make sense for a space faring civilization"): automated, field- or robot-handled plant. Every piece
below is 160 px a cell in its guide and shares the `ruins-forge` sheet.

- `forge-line`: the 6x7 tapping furnace, its taphole and spout trough running to its south edge
  (`tapping-furnace-melt`); the 2x2 pour head and mould master (the b3 hauls). Its ore cars, ladle on a hook,
  fettling bench, inspection table, racks and girder are drawn on this raw but no longer cut.
- `forge-era-a`: the feed mag-rail (2x2 run), its 4x4 quarter curve (meeting a straight rail at its south and east
  edges) and 2x2 decelerator end; the 2x3 feed capsule pod and its derailed state; the 3x3 hover slag skid
  (`slag-skid-melt`) and the skid crashed at the pit lip (`slag-skid-crashed-spill`, running out of its east edge);
  the 2x3 thrust wash its lift fans leave.
- `forge-era-b`: the 3x3 ladle in an overhead manipulator's yoke, upright (`ladle-lift-melt`) and tipped mid-pour
  (`ladle-lift-tipped-pour`), with no cover since it hangs overhead; the 2x2 robot finishing cell and metrology bed;
  the 2x4 cooling stack, empty and with castings; the overhead 1x4 gantry runway; the 7x1 fallen runway beam.
- `forge-floor`: the 3x3 pallet bay markings, painted flat from the guide's marks in `export.py` (`flat_paint`), since
  ImageGen drew them as raised angle; the 2x2 trench cover, 1x2 drain gutter and 1x1 floor drain; the
  overhead 1x2 cable tray and the 1x4 fallen tray. Its rail bed and slag cars are drawn but no longer cut.
- `forge-wear`: oil stain, oil trail, coolant stain, scorch mark and arc burn, faded as the patches are but with a
  lower halo cut (`STAIN_FEATHER`), since they were drawn translucent to their edges; the 3x3 line pulpit's roof and
  the 1x2 recorder console.

The slag pit's edge is the `FOUNDRY` drop's own `foundry-lip`. The runs (mag-rail, trench cover, drain gutter, gantry
runway, cable tray) run north-south and are healed in `export.py` (`heal_run`) into a seamless repeat along their
length. Hazard trim is muted as the spool drum's is, and melt graded as the crucible's.

The theme works halls (`ruins-theme-halls`: one works hall per theme but the forge) ship on four sheets
sharing `_atlas-works.json`, each 32 cells wide and padding to 4096x2048 (`texture-fit.md`), so a hall loads only the
sheets its pieces are on: `ruins-works-kit` (kit, runs, blocks, stations), `ruins-works-plant` (vessels, plant),
`ruins-works-bays` (cells, rows) and `ruins-works-giants`. Their block-outs are drawn by `hall_kinds.py` (a kind `run/fallen` is that run
fallen from overhead) at 160 px a cell, the giants at 112. The sheets are grouped by shape across the halls, so each
generation serves several of them:

- `works-kit`: the 6x6 cold head, 3x3 heat exchanger and coil winder (shared by the cold, ring, field halls and the
  reactor train), the one 2x2 `works-conveyor` bed every hall's line lays, a 1x2 wall service drop per service
  (coolant, nutrient, powder, power, steam) and the 3x1 `line-console` run.
- `works-runs`: the 2x2 floor runs (coolant return, coolant loop, transfer pipe, tensile bed, spoil conveyor) and the
  1x2 overhead runs (coolant header, feed manifold, pipe run, powder line), each with its 1x4 fallen state.
- `works-blocks`: the hover skids (waste, reject, crashed bed changer) and mag-rail unit carrier; walls laid a cell at
  a time (shield block, blast wall, blast baffle, cage fence, the fence turned north-south as `cage-fence-v`) and their
  rubble; the 3x2 dispatch crates.
- `works-vessels-a`/`-b`: silos (whole and split), the reactor column, the product, nutrient, feed and precursor
  tanks, the lattice tank, coating chamber and draw tower, all seen as roofs.
- `works-stations`: the small benches and stations and the 3x3 presses, sealers and tether anchor.
- `works-plant-a`/`-b`: chillers (whole and burst), the preheat furnace, cold manifold, cryostat, feed pumps, ice
  ridge, hoist and spool winders, beam dump, ring segment (turned east-west as `ring-segment-ew`), RF cavity, linac
  section and the cutter and drum racks.
- `works-cells-a`/`-b`: the charge cell, drawn open to the east for a west bay and mirrored as `-west` for an east bay
  (whole and torn open), the bore stand, the great vat (whole and burst) and the print bed.
- `works-rows-a`/`-b`: rack rows, the sorter, stacker rail and what fell across a bay (toppled racks and stacker,
  the fallen reactor column and print gantry), the pipe rack, director's booth and spoil heaps.
- `works-mega-a`/`-b`: the 12x12 headframe and 6x6 print gantry, overhead and open in the middle; the 10x8 proving
  torus, the 6x5 precursor furnace and the 12x4 proof butt.

Every works piece is cut at its exact frame; ImageGen's faint gutter haze (alpha under 8) is cleared.

The frost and vats sheets' own patches are concept-only and not exported.
`raw/ruins-forge-concept.png` is the forge floor and shaft head concept
(assembly cradle, shaft head, hoist winch), not yet shipped.

Review: `gradlew.bat createSnapshots -Psnapshot=ruins-themed-site-look`, the
`inside/` cryoarithmetic-engine and soil-nanites shots.
