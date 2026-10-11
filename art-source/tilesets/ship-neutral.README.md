# Neutral Mech Lab materials

The `ship-neutral` review scene keeps the concrete bays and layered warehouse
layout, with two material changes: graphite/galvanized shelving and a new
neutral ship bulkhead. Red service equipment and yellow bay markings remain
the principal accents. The neutral bulkhead is accepted and now supplies the
ship generator's default shell. The layered rack arrangement remains a review
fixture; see `ship-interior-materials.README.md` for the adopted material scope.

## Layered shelving

`warehouse-racks-muted.raw.png` is a built-in ImageGen edit of the original
`warehouse-racks.raw.png`, using `warehouse-racks-muted.prompt.txt`. The output
is 1447x1087 RGBA; its component registration remains compatible with the
original full-frame cuts. The authoring document retains those cuts and the
same dimensions, layer order and physical definitions. Blue uprights/bins become
graphite; orange beams become muted galvanized metal; cartons and cases are
desaturated. Frame openings remain transparent. The original palette is retained
as a comparison. See `warehouse-racks.README.md` for the layer contract.

## Bulkhead

`ShipBulkheadTemplate` uses the same sixteen exposed-face masks as the colony
wall workflow in `WallReskinTemplate`. A 1024x1024 guide has four rows/columns,
256px pitch, 16px inset and 224px opaque cell squares. The 32px exposed-edge
bands surround a dark structural core only where topology exposes a face.
Mask bits are N=1, E=2, S=4, W=8, in row-major order; joining sides have no rim.

Built-in ImageGen produced `sources/ship-bulkheads-first-pass.png` from the guide and
`ship-bulkheads.prompt.txt`. A targeted edit using
`ship-bulkheads-readability.prompt.txt` produced the accepted
`ship-bulkheads.raw.png`. Both outputs are 1254x1254 RGB. The first enamel value
was too close to the concrete floor; the accepted pass raises its brightness
without adding color accents. Source crops are recorded in
`ship-bulkheads.tileset-authoring.json`. The ordinary exporter produces the
64px/cell atlas. No hand-painted patching or post-export recoloring is used.

`ship.neutral-bulkhead` uses `wall-faces-4x4`; the ordinary wall resolver selects
faces from adjacent wall cells, including both sides of a partition. No renderer
change is needed. Matching door/window companions now live in
`ship-interior-materials`. There are no dedicated rubble or height/normal maps;
destruction retains the existing generic rubble fallback.

## Reproduce and inspect

With the repository's documented `JAVA_HOME`:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/ShipBulkheadTemplate.java art-source/templates/ship-bulkheads.png
'{"name":"warehouse-racks-muted"}' | .\tools\authoring.cmd tileset_export -
'{"name":"ship-bulkheads"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=ship-neutral'
```

The suite emits a 24px/cell before/after, 40px/cell room/detail views, and a
comparison against the existing `colony.ceramic-wall` with the same muted racks.
A separate assembly exercises a long room perimeter, four corners, exposed
ends, a T partition, an isolated pier and a solid 3x3 core. Outputs use the
production collectors with the headless Java2D drain, not a live ship or an
OpenGL lighting capture. Layout, circulation and fixture properties match the
preceding warehouse study.
