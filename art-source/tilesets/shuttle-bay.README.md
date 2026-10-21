# Ship shuttle bay

The raw is two generations side by side (2508x1254). The left half is the
original built-in ImageGen reskin of the first guide (1024 square, 64px per
cell), with the accepted armory sheet as material reference and the request in
`shuttle-bay.prompt.txt`: the ground-power unit and fluid skid (4x2), the wheel
cradle overlay (2x2, clear square canvas) and the landing-deck material (an
opaque interior crop, excluding the generated ragged rim). Those cuts were
measured from returned alpha.

The right half, from x=1254, is the real-size refit. A cell is about 0.75 m (a
crewman's shoulders are 0.6 cells); the original flight desk and service cart
filled 2x3 frames (1.4 x 2.2 m) and the tie-down marking was 0.56 m across.
`ShuttleBayTemplate` draws the desk (1.45 x 0.68 m, 2x1), the cart (0.66 x 1.4
m, 1x2) and a 0.26 m flush tie-down plate centred in its cell, at 320 px per
cell, and Codex `image_gen` reskinned it (`shuttle-bay-refit.prompt.txt`,
provenance in `shuttle-bay.raw.png.provenance.json`). The material reference
is the loading-bay sheet, not this one: with the old ring-and-bar marking in
either the guide or the reference, every request was refused as violence (it
reads as a gun sight), so the new plate is square. The generation came back
uniformly zoomed by 1.11-1.16x, so each piece is cut from a frame scaled by
the same factor about it; that keeps the guide's real sizes. Each piece's
worked face is its bottom edge: the desk's operator fascia and the cart's push
handle.

Regenerate the guide, join the raw and export with the checked-in wrapper:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/ShuttleBayTemplate.java art-source/templates/shuttle-bay.png
python art-source/templates/real-scale-montage.py <original-raw.png> <refit.png> art-source/tilesets/shuttle-bay.raw.png
'{"name":"shuttle-bay"}' | .\tools\authoring.cmd tileset_export -
```

Shelving reuses the main-stores physical bed and retaining frame, electrical
loads from machine-shop, and cartons from main-stores. The ship-only variant
of BoatBayFitting shares all berth arithmetic with the existing arrangement.
It retains six boats in 24x30 and one gig in 16x10. Equipment stays within the
existing two-cell working bands, with complete groups omitted at hatches.
Landing surfaces and tie-downs claim no collision; equipment does. The flight
desk stands across the band and is worked from the band cell below it; each
cart is parked against the bulkhead and worked from the cell below its handle.

`createSnapshots -Psnapshot=ship-materials` produces generated Valkyrie bay
views at 24/64px per cell, occupied views at 24/48px, and four posed fitting
views. These collect the production renderer and drain via Java2D, not live
OpenGL. The focused fitting test covers all eight poses, every perimeter hatch,
simultaneous hatches, berth capacity, service access and overlay transparency.
No height or normal companions belong to this atlas.
