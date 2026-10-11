# Mech Lab automotive service shop study

Geometry-first successor to the `ship-bay` material study. The entire fitted
room uses light sealed concrete, with green-grey painted concrete in its
adjoining passage. Each 5x7 bay is one floor sprite: solid yellow boundaries,
an open mouth, broad steel lift tracks and red anchor pads. The facing ranks
open onto the original central service lane. Red tool chests, blue diagnostics
and substantial red head columns reskin selected existing fixtures. The old
gantry side rails, stores, plant and bulkheads remain.

`AutoShopTemplate` draws the retained 1024x1024 geometry guide. The built-in
ImageGen tool textures that guide using the exact `auto-shop.prompt.txt` prompt.
The untouched result is `auto-shop.raw.png` (1254x1254 RGBA).
`auto-shop.tileset-authoring.json` owns the measured crop rectangles and exports
through the ordinary tileset exporter at 64px/cell. Floor cuts exclude the
ragged outer rims; fixture cuts retain the full guide cell and transparent
padding. Generated alpha is preserved. The small repeating concrete samples
use the exporter's existing 3px mirrored-border treatment, recorded in the
authoring document. There are three concrete variants and one passage variant.

The 2x6 service assembly is a **single closed multi-cell sprite**. Its elbow,
tee, couplings and terminal caps are generated together, so there are no
independently generated pipe ports to align. This solves the displayed route;
it does not repair or validate the older modular pipe family for arbitrary
networks. The continuous assembly and bay floor have zero cover and ballistic
height, as recessed floor treatments. The review fixture retains original
navigation and the tactical properties of every reskinned equipment placement.

Reproduce with the repository's documented `JAVA_HOME`:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/AutoShopTemplate.java art-source/templates/auto-shop.png
'{"name":"auto-shop"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=auto-shop'
```

The `auto-shop` snapshot suite uses the same production `VehicleBayFitting`
fixture as `ship-bay`, replacing its art in a controlled review scene. It emits
a whole-room comparison at 24px/cell, the new whole room at 40px/cell, a 48px/cell
detail and the guide. Ground and props go through the production collectors
and headless Java2D drain. This is not a live-ship capture or an OpenGL lighting
check. No campaign room selects the new kit yet, and no ship layout or fitting
behavior changes. The old study remains available for comparison.

Review limitations: the concrete retains visible mottling, and legacy side
rails still use fine mesh. Machines are absent because this scene tests the
vacant bay materials; the actual ship's roster supplies occupied berths.
