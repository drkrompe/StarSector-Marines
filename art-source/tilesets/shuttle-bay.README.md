# Ship shuttle bay

Built-in ImageGen reskin of `ShuttleBayTemplate`; the accepted armory sheet was
used only as the material reference. No API/CLI image generation was used.

- Geometry: `art-source/templates/shuttle-bay.png`, 1024 square, 64px/cell.
- Raw: `shuttle-bay.raw.png`, accepted 1254x1254 RGBA output.
- Exact prompt: `shuttle-bay.prompt.txt`.
- Cuts, physical footprints and export: `shuttle-bay.tileset-authoring.json`.
- Runtime: `mod/graphics/tilesets/shuttle-bay.png` and the matching tileset JSON.

The 3x3 guide holds seven assets: flight desk (2x3), service cart (2x3),
ground-power unit (4x2), fluid skid (4x2), wheel cradle overlay (2x2),
landing-deck material (1x1), and symmetric flush tie-down overlay (1x1).
Source coordinates were measured from returned alpha, not assumed from guide
resolution. Four equipment cuts include four pixels of padding. Wheel and
marking cuts retain square canvases and clear margins; the floor sample uses
an opaque interior crop, excluding the generated ragged rim.

Export using the checked-in wrapper:

```powershell
'{"name":"shuttle-bay"}' | .\tools\authoring.cmd tileset_export -
```

Shelving reuses the main-stores physical bed and retaining frame, electrical
loads from machine-shop, and cartons from main-stores. The ship-only variant
of BoatBayFitting shares all berth arithmetic with the existing arrangement.
It retains six boats in 24x30 and one gig in 16x10. Equipment stays within the
existing two-cell working bands, with complete groups omitted at hatches.
Landing surfaces and tie-downs claim no collision; equipment does.

`createSnapshots -Psnapshot=ship-materials` produces generated Valkyrie bay
views at 24/64px per cell, occupied views at 24/48px, and four posed fitting
views. These collect the production renderer and drain via Java2D, not live
OpenGL. The focused fitting test covers all eight poses, every perimeter hatch,
simultaneous hatches, berth capacity, service access and overlay transparency.
No height or normal companions belong to this atlas.
