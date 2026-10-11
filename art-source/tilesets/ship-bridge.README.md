# Ship bridge

The 13x11 diamond command compartment has a 3x3 central tactical plot,
three-cell helm and systems banks, two inward-facing side banks, and enclosed
data cabinets in the side pockets. Eight console watch positions stand
outboard of their controls; the ninth stands at the plot's control edge.
The inner walkway, chamfered perimeter and hatch approaches remain clear.

## Geometry and art

`ShipBridgeTemplate` draws the retained 1024x1024 RGBA guide at 64px/cell.
Six slots use origins (48,40), a 480x304 pitch, and two columns:

1. 3x3 plotting table; 3x1 helm, controls at image top.
2. 3x1 systems console, controls at image bottom; 1x3 console, controls right.
3. 1x3 console, controls left; 1x2 enclosed data cabinet.

Built-in ImageGen textured the guide using `armory-workshop.raw.png` solely
as the material reference. `ship-bridge.prompt.txt` retains the exact prompt.
The accepted 1254x1254 RGBA output is retained unchanged as
`ship-bridge.raw.png`. The authoring JSON records measured alpha bounds with
four pixels of source padding. The normal exporter crops/downsamples to the
declared 64px/cell footprints. No keying, repainting or post-export pixel patch
is used; this sheet has no height/normal companions.

## Placement and evidence

`ShipBridgeFitting` uses complete posed assemblies, including their visual
control edges. Furniture blocks only after circulation acceptance. The room
publishes WATCH activity, with no extra work assigned to cabinets. Authored
layouts retain priority; ground bridges and smaller footprints retain the
existing generic bridge fitting.

With JAVA_HOME set as documented in CLAUDE.md:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/ShipBridgeTemplate.java art-source/templates/ship-bridge.png
'{"name":"ship-bridge"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=ship-materials'
```

The suite shows the actual generated Valkyrie bridge at 24 and 64px/cell,
its adjoining passage, and four turned fitting views. These use production
render collectors with the headless Java2D drain, not a live OpenGL capture.
Direct tests restrict navigation to the diamond itself and cover all eight
poses, every legal hatch, simultaneous hatches on four sides, all four command
banks, nine exclusive reachable WATCH positions, and the clear plot walkway.
