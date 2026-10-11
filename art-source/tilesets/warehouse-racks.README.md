# Layered Mech Lab warehouse shelving

This review kit replaces the auto-shop study's right-hand checkerboard of
individual fixtures with two parallel warehouse rack rows. Each row has two
2x6 racks separated by the room's two-cell central cross-aisle. A two-cell
longitudinal aisle lies between the rows; the north and south ends also remain
open. The existing four mech bays and adjoining passage retain their art.

The rack is three draw layers, in placement order:

1. A complete 2x6 empty shelf bed. This owns the rack's cover and footprint.
2. Independently selected 2x2 stock sprites in three shelf slots: cartons,
   open parts bins, a crated engine, pipe stock, a partly picked tray or cases.
   A null slot leaves the underlying shelf visible.
3. The complete retaining frame, with transparent openings, drawn over stock.

Stock and the foreground frame are visual overlays with zero additional cover.
The room fixture blocks each rack footprint and explicitly checks its long,
cross and end aisles remain walkable. This is a controlled layout preview,
not a production fitting change or inventory implementation. Selection is fixed
for visual review; no simulated storage counts or tasks are attached to it.

`WarehouseRackTemplate` generates the retained 1024x768 geometry guide at
`art-source/templates/warehouse-racks.png`. Built-in ImageGen textures it using
the exact `warehouse-racks.prompt.txt` prompt. The unchanged returned image is
`warehouse-racks.raw.png` (1448x1086 RGBA). ImageGen changed component spacing:
the authoring document records measured full-frame cuts for rack layers and
transparent inset canvases for stock. No pixels are repainted, keyed or patched.
The ordinary exporter downsamples the eight sprites to 64px per world cell.
There are no height or normal companions.

Reproduce with the repository's documented `JAVA_HOME`:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/WarehouseRackTemplate.java art-source/templates/warehouse-racks.png
'{"name":"warehouse-racks"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=warehouse-racks'
```

The suite emits whole-room views at 24 and 40px/cell, a storage detail, a
before/after comparison against `auto-shop`, the guide and a layer breakdown.
The breakdown shows an empty shelf bed, bed with contents, and the same stocked
bed with its retaining frame. Source frame openings were checked for alpha;
the exported composition was inspected on the actual concrete floor.

Rendering uses production ground/prop collectors and the headless Java2D drain,
not a live ship capture or OpenGL lighting check. Tall pallet-rack character is
suggested by uprights, recessed shelves and beam shading in the overhead art;
this does not add 3D rack height or independently rendered vertical levels.
