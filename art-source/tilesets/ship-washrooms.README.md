# Ship washrooms

Built-in ImageGen reskin of `ShipWashroomTemplate`. The armory sheet supplied
only the muted material reference; no API/CLI generation was used.

- Guide: `art-source/templates/ship-washrooms.png`, 1024-square RGBA.
- Accepted raw: `ship-washrooms.raw.png`, 1254-square RGBA.
- Exact prompt: `ship-washrooms.prompt.txt`.
- Measured cuts: `ship-washrooms.tileset-authoring.json`.
- Runtime: `mod/graphics/tilesets/ship-washrooms.png` and matching tileset JSON.

The kit is one 1x1 basin counter, one 1x2 toilet cubicle, one 1x2 shower cubicle,
and a seamless non-slip resin floor sample. The basin's controls sit at
image-bottom; the cubicles' back panels sit at image-top, so opposing banks
face the common aisle. Each cubicle includes its privacy panels and floor as
one physical fixture. Source cuts preserve generated alpha, with a small margin
around the measured bodies. The floor uses an opaque interior crop to exclude
the sample edge. There are no height or normal companions.

```powershell
$env:JAVA_HOME = 'C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.2/jbr'
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/ShipWashroomTemplate.java art-source/templates/ship-washrooms.png
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
