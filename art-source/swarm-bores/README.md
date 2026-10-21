# Swarm bores and spore colonies

Geometry-first ImageGen (`art-source/geometry-first-imagegen.md`) for the
Colony Breach bore bodies (`structure.swarm-bore`, `structure.swarm-mother-bore`)
and the three spore-colony grades (`structure.spore-colony-fissure`, `-bore`,
`-maw`), in the Runner's chitin (`art-source/alien-modular-topdown/`).

| Step | Command | Writes |
| --- | --- | --- |
| Guides | `python art-source/swarm-bores/make_guide.py` | `art-source/templates/swarm-<part>-guide.png` |
| Reskin one part (one paid call) | `bash art-source/swarm-bores/generate.sh <part>` | `raw/<part>.raw.png` + provenance |
| Export | `python art-source/swarm-bores/export.py` | `mod/graphics/structures/<name>.png` and `<name>-glow.png` |

Parts: `bore`, `mother-bore`, `spore-colony-fissure`, `spore-colony-bore`,
`spore-colony-maw`.

Each part is one centre-pivoted, upright `StructureArt` layer, so the whole
square canvas ships and its centre is the structure's position; the layer's
`cells` in `turret-emplacements.turret.json` sets the size on the map (bore 3.0,
mother bore 3.8, colonies 1.0 / 1.35 / 1.7). The exporter fits each raw's opaque
extent back onto its guide and prints the silhouette IoU, the registration check.
The `-glow` layer is the reskin's own amber pixels lifted out by colour and drawn
over the body with a pulse; the structures set `groundPad: false`, so they stand
on the ground rather than on a hardstanding square.

Art direction: the swarm is one engineered organism (`colony-breach-nouns.md`,
Origin), so a bore and a colony are grown out of the same chitin, sinew and amber
nodes as the strains, never plants or machines. The grades differ in body, not
only scale: one sac, a triad, a spined crown.

Review: `gradlew.bat createSnapshots -Psnapshot=swarm-spore-colonies,swarm-bore-collapse`.
