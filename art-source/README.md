# Art source

Pre-pack art inputs and the scripts that turn them into shipped assets.

`mod/` is what ships: `deployMod` is a `Sync` of that whole folder into the
Starsector mods directory, so anything left inside it is copied to every install.
Raw generated sheets, ImageGen masters and derivation scripts are inputs to the
shipped art, not part of it — roughly 50 MB of them — so they live here instead.

They stay version-controlled rather than local-only, because a sheet has to be
re-derivable and re-annotatable later, and because the annotation of a sheet is
work that cannot be redone mechanically.

| Directory | Holds |
|-----------|-------|
| `tilesets/` | Raw tileset sheets, their normalization and atlas-packing scripts, and the material sources those scripts consume. |
| `doodads/` | ImageGen masters and raw prop renders, with the scripts that derive shipped frames from them. |

Tileset authoring documents — one per annotated sheet, written by the Tilesets
page of `gradlew.bat layerAuthoring` — live in `tilesets/` beside the sheet they
annotate, as `<name>.tileset-authoring.json`.

Every script here addresses its outputs from the repository root, not from a
sibling directory, so moving art source never silently retargets a write.
`RawArtStaysOutOfModTest` enforces the boundary from the other side.
