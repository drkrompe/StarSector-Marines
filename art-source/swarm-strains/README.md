# Swarm strains: Brute and Spitter bodies

Geometry-first ImageGen (`art-source/geometry-first-imagegen.md`) for the two
Colony Breach strains, in the Runner's chitin (`art-source/alien-modular-topdown/`).

| Step | Command | Writes |
| --- | --- | --- |
| Live-layer guides | `python art-source/swarm-strains/make_guide.py` | `art-source/templates/swarm-<strain>-<part>-guide.png`, `geometry.json` |
| Reskin one part (one paid call) | `bash art-source/swarm-strains/generate.sh <strain>-<part>` | `raw/<part>.raw.png` + provenance |
| Export live layers | `python art-source/swarm-strains/export.py` | `mod/graphics/battle/swarm-{brute,spitter}-modular-topdown/*.png` |
| Corpse guides (from the accepted raws) | `python art-source/swarm-strains/make_corpse_guide.py` | `art-source/templates/swarm-<strain>-corpse-guide.png` |
| Reskin corpses | `tools/codex_imagegen.py` with `prompts/<strain>-corpse.prompt.txt`, the corpse guide as geometry and `mod/graphics/battle/alien-dead.png` as material reference | `raw/<strain>-corpse.raw.png` |
| Export corpse sheets | `python art-source/swarm-strains/export_corpses.py` | `mod/graphics/battle/swarm-{brute,spitter}-dead.png` |

Layers keep the infantry compositor's registration: bodies are cut from a fixed
window at the 150 px shoulder width (the Spitter's window is square so its acid
sac sits behind shoulders that stay at the pivot), heads at the Runner's 72 px.
`export.py` fits each raw's opaque extent back onto its guide before cutting and
prints the silhouette IoU, the registration check. The Brute has its own foot
and crushing fore-claw; the Spitter walks and strikes on the Runner's.

Corpse sheets are four poses on one strip separated by transparent gaps, which
is what `SpriteSheetSlicer` splits on; the corpse sweep fits each frame's long
side to the body size, so the sheet's pixel scale only sets resolution.

Review: `gradlew.bat createSnapshots -Psnapshot=swarm-strains-skirmish,swarm-brute-breach`.
