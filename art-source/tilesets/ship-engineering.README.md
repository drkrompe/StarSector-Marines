# Ship engineering machinery

The 24x22 drive room separates the main reactor/turbine/heat-exchanger skid,
three auxiliary skids, and a control/workshop apron with enclosed spares.
The 20x14 auxiliary plant has cooling and life-support skids across a clear
service aisle from its control board, fabrication bench and switchgear.
Both retain the quiet concrete deck and dark ship bulkheads.

## Geometry and materials

The raw is two generations side by side (2508x1254). The left half, the
original 1254x1254 built-in ImageGen reskin, came from the first guide (1024
square at 48px/cell) with `armory-workshop.raw.png` as material reference and
the request in `ship-engineering.prompt.txt`:

| Assembly | Guide origin | Footprint |
|---|---|---|
| Main drive train | 40,40 | 18x6 |
| Cooling | 40,384 | 6x4 |
| Life support | 360,384 | 6x4 |
| Standby generator | 680,384 | 6x4 |
| Control board | 40,688 | 3x1 |

Pipe centerlines are part of that guide. The main drive's two headers connect
the reactor, turbine and exchanger; each smaller skid carries a complete
local circuit. Pipes do not cross assembly boundaries or service aisles.
Each plinth is one blocking equipment footprint, including the spaces between
its housings. There are no walkable holes inside a skid and no loose pipe tiles
whose independently generated ends need aligning. The left half's cuts are
measured alpha bounds with four pixels of source padding.

The right half, from x=1254, is the real-size refit. A cell is about 0.75 m (a
crewman's shoulders are 0.6 cells), and the original switchgear, spares cabinet
and service bench were two cells, 1.4 m, deep. `ShipEngineeringTemplate` now
draws only those three, one cell deep, at 320 px per cell:

| Piece | Footprint | Real size | Worked face |
|---|---|---|---|
| Switchgear bank | 3x1 | 2.1 x 0.6 m | breaker doors, bottom edge |
| Spares rack | 3x1 | 2.1 x 0.6 m | drawer pulls, bottom edge |
| Service bench | 4x1 | 2.9 x 0.7 m | drawers and vise, bottom edge |

Codex `image_gen` reskinned that guide with the left half as material reference
(`ship-engineering-refit.prompt.txt`, provenance in
`ship-engineering.raw.png.provenance.json`); `real-scale-montage.py` joins the
halves without touching a pixel, and the pieces are cut as full placement
frames. The switchgear came back about a tenth of a cell low in its frame, so
its cut window sits that much lower. Each piece's worked face is its bottom
edge, because a sprite's top edge lies at its footprint's highest row.

The exporter crops/downsamples into the declared 64px/cell footprints. No
keying, repainting or post-export pixel patch is used. No height/normal
companions. The switchgear's damaged and destroyed pair is its own generation,
from `ComposableDamageTemplate` over
`art-source/console-bank/damage/installation-utility-refit.tsv`, joined the
same way onto the right of `installation-utility-damage.raw.png`; the spares
rack and bench have no damage states.

## Layout and verification

Complete assemblies rotate and mirror with the room. The drive room publishes
ten exclusive work positions; auxiliary plant publishes seven. Control boards
publish WATCH and READOUT, sound machinery TEND, service points REPAIR, and
spares STOW. The switchgear, bench and spares stand in line with the control
board and are worked from the row below them. Only the auxiliary workshop publishes FABRICATE. These are ambient
work fixtures; the displayed circuits add no new power or coolant simulation.

Authored layouts retain priority. Ground machinery and smaller compartments
keep the existing fitting. Tests restrict navigation to the actual room and
cover both standard footprints in all eight poses, every legal perimeter
hatch, simultaneous hatches on four sides, complete machinery, exclusive
adjacent work positions, and the clear service aisles.

With JAVA_HOME set as documented in CLAUDE.md:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/ShipEngineeringTemplate.java art-source/templates/ship-engineering.png
python art-source/templates/real-scale-montage.py <original-raw.png> <refit.png> art-source/tilesets/ship-engineering.raw.png
'{"name":"ship-engineering"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=ship-materials'
```

The suite renders the actual generated Valkyrie's drive and auxiliary rooms
at 24 and 64px/cell, adjoining passage views, and four turned views of each
fitting. Evidence uses production render collectors with the headless Java2D
drain, not a live OpenGL capture. Review the pipe elbows and housing connections
at 64px and the room hierarchy at 24px.
