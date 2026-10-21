# Swarm bores and spore colonies

Geometry-first ImageGen (`art-source/geometry-first-imagegen.md`) for the
Colony Breach bore bodies (`structure.swarm-bore`, `structure.swarm-mother-bore`)
and the three spore-colony grades (`structure.spore-colony-fissure`, `-bore`,
`-maw`), in the Runner's chitin (`art-source/alien-modular-topdown/`).

| Step | Command | Writes |
| --- | --- | --- |
| Guides | `python art-source/swarm-bores/make_guide.py` | `art-source/templates/swarm-<part>-<n>-guide.png` |
| Reskin one variant (one paid call) | `bash art-source/swarm-bores/generate.sh <part> <n>` | `raw/<part>-<n>.raw.png` + provenance |
| Export | `python art-source/swarm-bores/export.py` | `mod/graphics/structures/<name>-<n>.png` and `<name>-<n>-glow.png` |
| Recolour preview | `python art-source/swarm-bores/export.py --palette chitin` | `build/swarm-bores/chitin/` |

Parts and variant counts are `make_guide.VARIANTS`: `bore` 3, `mother-bore` 2,
`spore-colony-fissure`, `spore-colony-bore`, `spore-colony-maw` 3 each. The
structure's art block declares `"variants": N` and names its layers with
`{variant}`; `StructureArt.variantFor` picks one per structure from its entity
id, the same for the body and its glow.

**Geometry and light come from code.** Each guide is a seeded heightfield of
lopsided primitives (plates of differing size and lean, tusks, tilted paving
slabs, earth, sinew roots; sacs, buds, spines) lit by the battle's default sun
(`SunLight`: azimuth 135 degrees, from the upper left of the screen; elevation
38 degrees) with ray-marched shadows, so the shaft's far wall is lit and its
near wall dark, plates shadow one another, and the body shadows the ground.
The lighting is baked into the albedo, which is why the runtime never rotates
or mirrors these sprites. The reskin is asked to keep that light and shadow.

**The ground layer is not painted.** `make_guide.ground_layer` draws the cast
shadow, a contact shade and the cracks the body heaved into the ground from
the same heightfield, translucent and dark so it reads on paving, grass or
creep; `export.py` lays it under the fitted reskin. The canvas is larger than
the body (`CANVAS_CELLS`) so the shadow fits; the layer's `cells` in
`turret-emplacements.turret.json` is that canvas size.

**Palette.** The shipped sprites are in the current runner palette. The
guide's material map records which pixels are creature (chitin, sinew, bone,
flesh, sac) and which are earth or paving, so `--palette chitin` recolours
only the creature to the Rav Bloom chitin proposal by a luminance gradient
map. When the creature redesign lands, the recolour is that switch (or a new
ramp), not a new generation.

The `-glow` layer is the reskin's own amber pixels lifted out by colour and
drawn over the body with a pulse; the structures set `groundPad: false`, so
they stand on the ground rather than on a hardstanding square.

Art direction: the swarm is one engineered organism (`colony-breach-nouns.md`,
Origin), so a bore and a colony are grown out of the same chitin, sinew and amber
nodes as the strains, never plants or machines. They erupted, so nothing is
symmetric: a bore has an upheaved side and a collapsed side, and a colony
grows round one dominant leaning sac.

Review: `gradlew.bat createSnapshots -Psnapshot=swarm-spore-colonies,swarm-bore-collapse`,
and `python art-source/swarm-bores/review_sheet.py` for the side-by-side sheets.
