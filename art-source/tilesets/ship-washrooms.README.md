# Ship washrooms

The raw is two generations side by side (2508x1254). The left half is the
original built-in ImageGen reskin of the first guide (1024 square), with the
armory sheet only as a muted material reference and the request in
`ship-washrooms.prompt.txt`: one 1x2 toilet cubicle, one 1x2 shower cubicle and
a seamless non-slip resin floor sample. The cubicles' back panels sit at
image-top, so they face the common aisle below them. Each cubicle includes its
privacy panels and floor as one physical fixture. Source cuts preserve
generated alpha, with a small margin around the measured bodies. The floor uses
an opaque interior crop to exclude the sample edge.

The right half, from x=1254, is the real-size basin. A cell is about 0.75 m (a
crewman's shoulders are 0.6 cells), and the original counter filled its cell
at 0.73 m. `ShipWashroomTemplate` draws a 0.5 x 0.45 m basin in its 1x1 frame
at 320 px per cell, its wall strip, faucet and two controls flush with the
bottom edge, and Codex `image_gen` reskinned it with the left half as material
reference (`ship-washrooms-refit.prompt.txt`, provenance in
`ship-washrooms.raw.png.provenance.json`). It came back slightly small and
centred higher than its guide shape, so its square cut is scaled to make the
basin 0.5 m wide and set so its back stays flush with the bulkhead.
`real-scale-montage.py` joins the halves without touching a pixel. The basin
is washed from its top edge: the fitting lines basins along the bottom bulkhead
and stands the user on the aisle above. There are no height or normal
companions.

- Guide: `art-source/templates/ship-washrooms.png` (the basin refit only).
- Accepted raw: `ship-washrooms.raw.png`; cuts and export:
  `ship-washrooms.tileset-authoring.json`.
- Runtime: `mod/graphics/tilesets/ship-washrooms.png` and matching tileset JSON.

```powershell
$env:JAVA_HOME = 'C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.2/jbr'
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/ShipWashroomTemplate.java art-source/templates/ship-washrooms.png
python art-source/templates/real-scale-montage.py <original-raw.png> <refit.png> art-source/tilesets/ship-washrooms.raw.png
'{"name":"ship-washrooms"}' | .\tools\authoring.cmd tileset_export -
```

The ship's existing 6x5 washroom footprint retains two clear aisle rows.
Basins line one bulkhead; toilets and showers form contiguous groups opposite.
Each hatch reserves its full approach before furnishing. The surviving cubicle
positions are divided between toilets and showers so a hatch at one end cannot
erase an entire kind. Each placed fixture publishes one exclusive WASH position
on the aisle. Cubicles retain the existing threshold-based wash activity and
complete blocking footprint; this does not add shower or toilet animations.
Small and ground washrooms keep their existing fitting; authored layouts win.

`ShipWashroomFittingTest` exercises all eight poses, every perimeter hatch,
wide and simultaneous hatches, full aisle width, reachable unique wash positions,
physical fixture blocking and small/ground fallbacks. `createSnapshots
-Psnapshot=ship-materials` includes the actual generated washroom at 24/64px per
cell and four rotated fitting views through the production render collectors
drained by Java2D. These are assembly evidence, not live game captures.
