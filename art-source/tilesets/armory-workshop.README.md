# Ship armory workshop

The generated ship armory uses a coordinated muted steel kit: an issue counter,
a recessed weapons-storage run, a repair bench, ready-use lockers and a separate
ammunition cabinet. The public apron, staff gate, working aisle and rear rack
alley stay open. This replaces the ship fitting's old orientation-dependent
fallback props; ground-side armories keep their existing fitting.

## Geometry and layers

`ArmoryWorkshopTemplate` produces `art-source/templates/armory-workshop.png`, a
1024x1024 RGBA guide with eight component slots in two columns and four rows.
The canonical source cell is 96px; origins are x=64/576 and y=32/288/544/800.
The counter covers 2x1 cells, weapon bed/rifles/frame each cover 3x1, the
bench/tools/ready lockers cover 3x2 and the ammunition cabinet covers 2x2.

Built-in ImageGen textured the guide using `warehouse-racks-muted.raw.png` as
a material reference. The exact request is retained in `armory-workshop.prompt.txt`.
Its unchanged 1254x1254 RGBA result is `armory-workshop.raw.png`. The generated
spacing differs from the guide: measured component canvases in the authoring
document register the overlays to their bases. The rifles retain a padded
transparent canvas so their barrels and stocks sit inside the three rack bays.
No pixels were repainted or keyed. The ordinary exporter crops these canvases
and downsamples to 64px per world cell, preserving alpha. No height or normal
companions are present.

Weapon racks draw bed, rifles, then retaining frame. The workbench draws its
empty top and rubber mat before the rifle, vise and organized tools. These
contents are decorative layers with zero additional cover; the base owns the
physical footprint. Assemblies rotate and mirror together with the room.
Furniture becomes blocking only after the fitting passes circulation checks;
a doorway that intersects a fixture omits the entire assembly. Counter issue,
bench repair and cabinet/rack stowage points stand on reachable clear deck.
The displayed weapons and ammunition are scenery, not campaign inventory.

## Reproduce and inspect

Set `JAVA_HOME` as documented in `CLAUDE.md`, then:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/ArmoryWorkshopTemplate.java art-source/templates/armory-workshop.png
'{"name":"armory-workshop"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=ship-materials'
```

The snapshot suite shows the actual generated Valkyrie armory at 24 and 64px
per cell, with its adjoining passage, plus four quarter-turn fitting views.
It uses production collectors and the headless Java2D renderer; these are not
live-game OpenGL captures. Focused tests cover all rotations and mirrors,
fallback doorway clearance, reachable work and transparent frame openings.
