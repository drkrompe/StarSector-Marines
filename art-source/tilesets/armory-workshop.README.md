# Ship armory workshop

The generated ship armory uses a coordinated muted steel kit: an issue counter,
a recessed weapons-storage run, a repair bench, ready-use lockers and a separate
ammunition cabinet. The public apron, staff gate, working aisle and rear rack
alley stay open. This replaces the ship fitting's old orientation-dependent
fallback props; ground-side armories keep their existing fitting.

## Geometry and layers

The raw is two generations side by side (2508x1254). The left half is the
original built-in ImageGen reskin of the first guide (1024 square, 96 px source
cells), with `warehouse-racks-muted.raw.png` as material reference and the
request in `armory-workshop.prompt.txt`: the 2x1 counter, the 3x1 weapon bed,
rifles and retaining frame, and the 2x2 ammunition cabinet. Its spacing differs
from that guide, so measured component canvases register the overlays to their
bases.

The right half, from x=1254, is the real-size refit. A cell is about 0.75 m (a
crewman's shoulders are 0.6 cells), and the original 3x2 repair bench and
locker bank were 1.4 m deep. `ArmoryWorkshopTemplate` draws the bench (2.1 x
0.7 m), its tool overlay and the locker bank (2.1 x 0.5 m) as 3x1 frames at
320 px per cell, and Codex `image_gen` reskinned it with the left half as
material reference (`armory-workshop-refit.prompt.txt`, provenance in
`armory-workshop.raw.png.provenance.json`). At 128 px per cell the same pieces
came back far too deep twice; at 320 they held to the guide, so they are cut as
full placement frames. `real-scale-montage.py` joins the halves without
touching a pixel. Each piece's worked face is its bottom edge: drawers and
locker doors face the aisle below, the row a user stands on.

The exporter downsamples to 64px per world cell, preserving alpha. No height or
normal companions are present. The damaged and destroyed pairs of the three
refit pieces are their own generation, from `ComposableDamageTemplate` over
`art-source/console-bank/damage/armory-workshop-refit.tsv`, joined the same way
onto the right of `armory-workshop-damage.raw.png`.

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
python art-source/templates/real-scale-montage.py <original-raw.png> <refit.png> art-source/tilesets/armory-workshop.raw.png
'{"name":"armory-workshop"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=ship-materials'
```

The snapshot suite shows the actual generated Valkyrie armory at 24 and 64px
per cell, with its adjoining passage, plus four quarter-turn fitting views.
It uses production collectors and the headless Java2D renderer; these are not
live-game OpenGL captures. Focused tests cover all rotations and mirrors,
fallback doorway clearance, reachable work and transparent frame openings.
