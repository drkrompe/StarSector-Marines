# Compact power-plant kit

Full-size turbine-alternator and cooling skids for the eleven-by-five fort power
halls and compact ground utility buildings. `PowerPlantKit` owns their 3x3 and
2x3 placement rectangles; each host owns the surrounding service aisles.
The existing fort reactor remains a separate gameplay structure.

## Art provenance and export

- Geometry source: `../templates/power-plant-guide.py` and its retained PNG.
- Exact built-in ImageGen request: [reskin.prompt.txt](reskin.prompt.txt).
- Style reference: `mod/graphics/tilesets/factory-machines.png`.
- Accepted, unmodified RGBA output: `../tilesets/power-plant.raw.png`.
- Registration: `../tilesets/power-plant.tileset-authoring.json`.
- Shipped atlas: `mod/graphics/tilesets/power-plant.png`, with its tileset catalog.

The built-in ImageGen tool produced a 1024x1536 master with intact, standing
damaged, and low destroyed states. Native transparency is retained. Full square
512px frames register the turbine; 320x480 frames register the cooling unit.
These frames preserve the intended aspect ratios and transparent placement
padding. The normal exporter crops and resamples them at 128px per cell; there
is no hand-painted correction or generated raster hidden outside the workspace.

Re-export with:

```powershell
'{"name":"power-plant"}' | .\tools\authoring.cmd tileset_export -
```

Standing states keep heavy cover and their full obstruction. Destroyed states
release the footprint and sight lines through the ordinary prop-damage path.
Both ends provide service access so the kit can follow either fort orientation.

## Review evidence

```powershell
.\gradlew.bat createSnapshots '-Psnapshot=power-halls,logistics-rooms,composable-damage'
```

`review/` retains the two fort close views, reusable utility buildings, and the
three damage states. All frames are 1920x1080 at 100% UI scale, rendered through
production collectors with a Java2D drain; they are not live OpenGL captures.
Close views change only the camera zoom. Fort views spawn the actual structure
entities so aspect cores are visible, rather than leaving apparently vacant
3x3 footprints. Installation floors now continue beneath sealed cores as well
as doodad fixtures; destruction exposes the same room finish.

The snapshot suites check actual fort placement and clear lanes in all four
orientations. Focused unit tests separately check both hall layouts across all
eight rotation/mirror poses, operator access, complete machine footprints,
damage catalog dimensions, compact utility reuse, and sealed-core floor finish.
