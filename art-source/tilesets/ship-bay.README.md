# Ship bay material study

A geometry-first ImageGen kit previewed over the production `VehicleBayFitting`.
The catalog makes the pieces available; production rooms do not select them yet.

`ShipBayReskinTemplate` draws the 1024x768 guide retained at
`art-source/templates/ship-bay.png`: four columns, three rows, 256px pitch,
16px inset and 224px square canvases. Row-major part names live in its `PARTS`.
Pipe ports sit at edge midpoints; the straight is W/E, elbow W/S, tee W/E/S,
and terminal W in image coordinates. Placement supplies exact quarter turns.

The built-in ImageGen edit used that guide and `urban-tileset.raw.png` as a
separate material reference. Its exact prompt is `ship-bay.prompt.txt`, and the
unchanged 1448x1086 result is `ship-bay.raw.png`. The authoring document records
measured 308px square cuts inside the ragged sprite rims. Generated alpha is
preserved, including its slight translucency; no color key or alpha replacement
is applied. The ordinary exporter downsamples to 64px cells. This sheet has no
height/normal companions.

The pipes represent recessed service channels, and every covering has zero
cover and zero ballistic half-height. Art does not change navigation. Raised
pipe obstacles would need separate tactical definitions and placement rules.

Reproduce from the session worktree with the `JAVA_HOME` in `CLAUDE.md`:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/ShipBayReskinTemplate.java art-source/templates/ship-bay.png
'{"name":"ship-bay"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=ship-bay'
```

The snapshot suite uses the ordinary ground and prop collectors with the
headless Java2D drain. It fits the same 24x16 vehicle room twice, frames its first
vacant 5x7 bay, vestibule and adjoining two-cell passage, and changes only art.
It supplies a 48px/cell comparison, a 32px/cell view, a complete room and a
connection study containing all four elbow poses and repeated grating. This is
a controlled room fixture, not a capture of the player's live ship or proof of
OpenGL lighting. The old bulkheads and machinery are retained for comparison.

The new-sheet path preserves floor-before-fixture order. The legacy control
retains its old sheet grouping, which can hide parts of fixtures under its
urban-sheet floor covering; that difference is renderer ordering, not new props.
The source deck has a repeated diagonal wear mark and the grating is fine at
32px/cell. Those remain art-direction choices for review before ship-wide use.
