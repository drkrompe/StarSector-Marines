# Ship provisions and loading bay

The raw is two generations side by side (2702x1254). The left half is the
original built-in ImageGen reskin of the first guide (1024x768, 48px per cell),
with the accepted armory sheet as material reference and the request in
`loading-bay.prompt.txt`: the 6x2 conveyor, the insulated tote and strapped
cartons (2x2 overlays with clear margins) and the staging-deck material (an
opaque interior crop).

The right half, from x=1448, is the real-size refit. A cell is about 0.75 m (a
crewman's shoulders are 0.6 cells), and the original 4x2 manifest desk and 2x3
pallet tug were nearly twice their real size. `LoadingBayTemplate` draws the
desk (1.45 x 0.66 m, 2x1) and the tug (0.7 x 1.9 m, 1x3) at 320 px per cell,
and Codex `image_gen` reskinned it with the left half as material reference
(`loading-bay-refit.prompt.txt`, provenance in
`loading-bay.raw.png.provenance.json`). Both came back within a few percent of
the guide and are cut as full placement frames; `real-scale-montage.py` joins
the halves without touching a pixel. Each piece's worked face is its bottom
edge: the desk's operator fascia, and the tug's tiller with its forks pointing
away. The tug's fork gap is transparent; its whole footprint is a physical
fixture. No height or normal companions belong to this atlas, and no damage
family covers it.

Regenerate the guide, join the raw and export through the current authoring
wrapper:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/LoadingBayTemplate.java art-source/templates/loading-bay.png
python art-source/templates/real-scale-montage.py <original-raw.png> <refit.png> art-source/tilesets/loading-bay.raw.png
'{"name":"loading-bay"}' | .\tools\authoring.cmd tileset_export -
```

`ShipLoadingBayFitting` uses a four-cell receiving apron, a three-cell handling
aisle, three staged loads and one layered picking rack. Shelf beds, pallets and
retaining frames reuse main-stores art. It preserves the near-bulkhead hookup
and handed room contract; all fixtures and layers follow the complete room pose.
The clerk works the desk from the row below it, facing into the hold, and the
tug is tended from the handling aisle below its tiller.
Ground rooms and smaller bays retain the original fitting. Standard 18x12 rooms
have seven physical assemblies and ten exclusive work positions.

`ShipLoadingBayFittingTest` covers all eight poses, every perimeter hatch,
simultaneous hatches, physical work targets, clear cargo margins and the fork
opening. `createSnapshots -Psnapshot=ship-materials` includes generated Valkyrie
provisions views at 24/64px per cell and four rotated fitting views. These use
the production render collectors with a headless Java2D drain, not live OpenGL.
