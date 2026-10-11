# Ship machine shop

The 14x10 ship machine shop pairs a lathe and mill with an assembly/repair
bench and a separate inspection bench. The workstations face the central
working floor. One side rack holds grouped bar stock, bearings and electrical
modules beside its own picking aisle; the perimeter keeps hatch access open.

## Geometry and art

`MachineShopTemplate` draws the retained 1024x1024 RGBA guide at 64px/cell.
Seven row-major slots use origin (32,32) and a 336x320 pitch:

1. 4x2 lathe, 4x2 mill, 4x2 assembly bench.
2. 4x2 inspection bench, 2x2 bearing trays, 2x2 electrical parts.
3. 2x2 retained bar stock.

Built-in ImageGen reskinned this guide using `armory-workshop.raw.png` as the
material reference. The exact prompt is `machine-shop.prompt.txt`; the accepted
1254x1254 RGBA output is retained unchanged as `machine-shop.raw.png`.
The authoring document records measured workstation cuts with four pixels of
source padding, and square padded canvases for the three shelf-load overlays.
The ordinary exporter crops/downsamples to 64px/cell, preserving source alpha.
No keying, repainting or post-export pixel patch is used. No height/normal maps.

## Placement and evidence

The parts rack reuses the established `main-stores.rack` bed and
`main-stores.frame`. Three separate load overlays sit at two-cell intervals,
with the frame drawn last. Only the bed owns blocking; layers have no cover or
ballistic height. Contents stay inside the frame margins. Complete assemblies
and their work positions follow every room pose.

Two reachable FABRICATE positions belong to the lathe and mill, one REPAIR to
the assembly bench, one READOUT to inspection, and three STOW to the rack.
These are ambient workshop jobs, not a new crafting or inventory interface.
Authored layouts retain priority. Ground parts cages and the small 6x4 utility
cage retain the existing storage fitting; the ship's standard machine-shop
recipe still uses the PARTS_CAGE purpose.

With JAVA_HOME set as documented in CLAUDE.md:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/MachineShopTemplate.java art-source/templates/machine-shop.png
'{"name":"machine-shop"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=ship-materials'
```

The `ship-materials-parts_cage` images show the actual generated Valkyrie shop
at 24 and 64px/cell, its adjoining passage, and four turned fitting views.
They use production render collectors with the headless Java2D drain, not live
OpenGL. Direct tests cover all eight poses, every legal hatch, simultaneous
hatches on four sides, exclusive adjacent work positions, clear aisles,
storage layer order, utility-cage fallback and transparent overlay rims.
