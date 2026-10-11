# Ship engineering machinery

The 24x22 drive room separates the main reactor/turbine/heat-exchanger skid,
three auxiliary skids, and a control/workshop apron with enclosed spares.
The 20x14 auxiliary plant has cooling and life-support skids across a clear
service aisle from its control board, fabrication bench and switchgear.
Both retain the quiet concrete deck and dark ship bulkheads.

## Geometry and materials

`ShipEngineeringTemplate` draws a 1024x1024 RGBA guide at 48px/cell:

| Assembly | Guide origin | Footprint |
|---|---|---|
| Main drive train | 40,40 | 18x6 |
| Cooling | 40,384 | 6x4 |
| Life support | 360,384 | 6x4 |
| Standby generator | 680,384 | 6x4 |
| Control board | 40,688 | 3x1 |
| Service bench | 360,688 | 4x2 |
| Switchgear | 680,688 | 3x2 |
| Enclosed spares | 40,864 | 3x2 |

Pipe centerlines are part of this guide. The main drive's two headers connect
the reactor, turbine and exchanger; each smaller skid carries a complete
local circuit. Pipes do not cross assembly boundaries or service aisles.
Each plinth is one blocking equipment footprint, including the spaces between
its housings. There are no walkable holes inside a skid and no loose pipe tiles
whose independently generated ends need aligning.

Built-in ImageGen reskinned the guide with `armory-workshop.raw.png` as its
material reference. The exact prompt is `ship-engineering.prompt.txt`; the
unchanged 1254x1254 RGBA result is `ship-engineering.raw.png`. The authoring JSON
records measured alpha bounds with four pixels of source padding. The ordinary
exporter crops/downsamples into the declared 64px/cell footprints. No keying,
repainting or post-export pixel patch is used. No height/normal companions.

## Layout and verification

Complete assemblies rotate and mirror with the room. The drive room publishes
ten exclusive work positions; auxiliary plant publishes seven. Control boards
publish WATCH and READOUT, sound machinery TEND, service points REPAIR, and
spares STOW. Only the auxiliary workshop publishes FABRICATE. These are ambient
work fixtures; the displayed circuits add no new power or coolant simulation.

Authored layouts retain priority. Ground machinery and smaller compartments
keep the existing fitting. Tests restrict navigation to the actual room and
cover both standard footprints in all eight poses, every legal perimeter
hatch, simultaneous hatches on four sides, complete machinery, exclusive
adjacent work positions, and the clear service aisles.

With JAVA_HOME set as documented in CLAUDE.md:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/ShipEngineeringTemplate.java art-source/templates/ship-engineering.png
'{"name":"ship-engineering"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=ship-materials'
```

The suite renders the actual generated Valkyrie's drive and auxiliary rooms
at 24 and 64px/cell, adjoining passage views, and four turned views of each
fitting. Evidence uses production render collectors with the headless Java2D
drain, not a live OpenGL capture. Review the pipe elbows and housing connections
at 64px and the room hierarchy at 24px.
