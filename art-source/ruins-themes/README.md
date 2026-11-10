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
   `mod/data/tilesets/ruins-themes.tileset.json` at 128 px a cell; metadata
   (ids, cover, footprints) is `_atlas.json`.

Shipped: `frost` (cryo rack, frost heatsink), `vats` (culture vat, grow bed),
`patches` (two frost, two growth). The frost and vats sheets' own patches are
concept-only and not exported. `raw/ruins-forge-concept.png` is the forge floor
and shaft head concept (assembly cradle, shaft head, hoist winch), not yet
shipped.

Review: `gradlew.bat createSnapshots -Psnapshot=ruins-themed-site-look`, the
`inside/` cryoarithmetic-engine and soil-nanites shots.
