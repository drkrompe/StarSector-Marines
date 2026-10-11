# Ship firing range

Built-in ImageGen reskin of `FiringRangeTemplate`; the existing armory sheet
provided the muted material reference. No API/CLI generation was used.

- Guide: `art-source/templates/firing-range.png`, 1024-square RGBA.
- Accepted raw: `firing-range.raw.png`, 1254-square RGBA.
- Exact prompt: `firing-range.prompt.txt`.
- Measured cuts: `firing-range.tileset-authoring.json`.
- Runtime: `mod/graphics/tilesets/firing-range.png` and matching tileset JSON.

Four fixtures: a 1x2 bullet trap facing image-left, a 2x1 ballistic divider,
a 2x1 preparation bench and a 2x1 range controller. Two interior crops provide
quiet concrete and darker rubber lane material. Fixture cuts retain generated
alpha and a small margin around each body. There are no height/normal companions.

`firing-range-target.*` retains a second built-in reskin of the template's
512-square target guide. Its 1254-square raw is exported with the full transparent
canvas to keep the plate inset within one cell. The rotationally symmetric
plate is nonblocking paving at the live target focus; it has no cover or ballistic
height and does not impersonate a second target entity. The renderer normally
hides RANGE_TARGET bodies, so the plate supplies the visible aiming mark.
The second exact prompt and authoring document are retained beside the raw.

```powershell
$env:JAVA_HOME = 'C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.2/jbr'
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/FiringRangeTemplate.java art-source/templates/firing-range.png
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/FiringRangeTemplate.java art-source/templates/firing-range-target.png target
'{"name":"firing-range"}' | .\tools\authoring.cmd tileset_export -
'{"name":"firing-range-target"}' | .\tools\authoring.cmd tileset_export -
```

The ship retains its 30x8 L-shaped recipe, two PRACTICE positions and existing
ready-end hookups. Dividers sit beside the shooters, never across the shot ray.
The target focus is one cell in front of the backstop: the scene spawns its real
friendly range target there. The beaten zone starts immediately past the firing
positions and stays closed to walking, open to sight and shot. Solid traps sit
beyond the targets. Preparation benches and a controller face a two-cell aisle;
the opposite wall carries the armory's separate rack, rifle and frame layers.
These supporting fixtures are scenery, not additional staff jobs. Ground ranges
retain their field fitting. Authored room layouts remain the first choice.

`ShipFiringRangeFittingTest` checks all eight poses and each authored single/wide
hookup, simultaneous entrances, reachable practice, empty shot rays, solid
backstops, storage layers, and ground behavior. The existing generated-range
checks and live-fire scene regression also cover the integration.

`createSnapshots -Psnapshot=ship-materials` renders the actual generated room
at 24/64px per cell, four posed fixtures, and a staffed `ShipDeckBattleScene`
through the production render collectors drained by Java2D. These are headless
assembly previews, not live OpenGL captures.
