# Ship provisions and loading bay

Built-in ImageGen reskin of `LoadingBayTemplate`, with the accepted armory
sheet as a material reference only. No API/CLI generation was used.

- Geometry: `art-source/templates/loading-bay.png`, 1024x768; 48px per cell.
- Accepted raw: `loading-bay.raw.png`, 1448x1086 RGBA.
- Exact prompt: `loading-bay.prompt.txt`.
- Measured cuts and footprints: `loading-bay.tileset-authoring.json`.
- Runtime atlas: `mod/graphics/tilesets/loading-bay.png` and matching tileset JSON.

Six slots: manifest desk (4x2), pallet tug (2x3), conveyor (6x2), insulated
tote and strapped cartons (2x2 overlays), and a seamless staging material.
Equipment cuts have padding; square cargo canvases retain clear margins.
The floor uses an opaque interior crop. The tug's open fork gap is transparent;
the complete equipment footprint remains a physical fixture. No height or
normal companions belong to this atlas.

Export through the current authoring wrapper:

```powershell
'{"name":"loading-bay"}' | .\tools\authoring.cmd tileset_export -
```

`ShipLoadingBayFitting` uses a four-cell receiving apron, a three-cell handling
aisle, three staged loads and one layered picking rack. Shelf beds, pallets and
retaining frames reuse main-stores art. It preserves the near-bulkhead hookup
and handed room contract; all fixtures and layers follow the complete room pose.
Ground rooms and smaller bays retain the original fitting. Standard 18x12 rooms
have seven physical assemblies and ten exclusive work positions.

`ShipLoadingBayFittingTest` covers all eight poses, every perimeter hatch,
simultaneous hatches, physical work targets, clear cargo margins and the fork
opening. `createSnapshots -Psnapshot=ship-materials` includes generated Valkyrie
provisions views at 24/64px per cell and four rotated fitting views. These use
the production render collectors with a headless Java2D drain, not live OpenGL.
