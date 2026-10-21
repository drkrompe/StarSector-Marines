# Ship barracks bunks and personal lockers

Generated barracks and crew quarters use the same quiet graphite and grey-green
kit: two facing bunk ranks, pillows toward the outer bulkheads, personal locker
banks at the near ends and amid the far rank. The two-cell central aisle and
whole hatch approaches remain open. Locker banks replace loose kit and pipe
props without changing the fitting's rest capacity. Authored room layouts stay
authoritative; ground-side berthing keeps its existing art.

## Geometry and layers

`BarracksBunkTemplate` produces a 1024x1024 RGBA guide containing six 1x2-cell
components. Full guide canvases are 160x320px at x=72/392/712 and y=48/560.
Reading across then down: bunk base, bedding with pillow at image top, bedding
with pillow at image bottom, open retaining frame, closed three-compartment
locker bank, and matching bank with one open compartment of folded clothing.

Built-in ImageGen textured that guide using `armory-workshop.raw.png` as the
material reference. The exact request is `barracks-bunks.prompt.txt`; its unchanged
1254x1254 RGBA result is `barracks-bunks.raw.png`. Measured full component canvases
are retained in the authoring JSON. The bedding cuts include transparent padding
so the mattress fits inside the frame. The ordinary exporter crops and downsamples
to 64px per world cell, preserving alpha. No painting, keying, post-export patches,
height maps or normal maps were used.

Bunks draw base, bedding, then rails. Image top corresponds to high world Y;
low-facing bunks therefore use `bedding-low`. The complete assembly rotates and
mirrors with the room. Only the base owns cover and physical blocking, which is
applied after circulation acceptance. REST and STOW points stand in the aisle
and target the adjacent fixture. A blocked footprint omits all layers together.
Folded clothing is decorative scenery, not tracked personal inventory.

## Reproduce and inspect

Set JAVA_HOME as documented in CLAUDE.md, then:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/BarracksBunkTemplate.java art-source/templates/barracks-bunks.png
'{"name":"barracks-bunks"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=ship-materials'
```

The suite renders the actual generated Valkyrie barracks at 24 and 64px per cell,
plus all four quarter turns of a small fitting. It uses production collectors
and the headless Java2D renderer, not a live-game OpenGL capture. Direct fitting
tests cover all eight rotations/mirrors, preserved berth capacity, clear rear
hatches, reachable rest/stowage and the frame's transparent opening.

The same suite also checks the corrected dark upper armory wall: hull boundary
walls can retain VOID ground, but still need the ship's structural wall material.

## Worn condition

`barracks-bunks-worn.raw.png` repaints this sheet as the same six components
worn: chipped paint, a bare stained mattress under a tangled blanket, dented and
ajar locker doors, unfolded clothing. ImageGen was given `barracks-bunks.raw.png`
as geometry and identity authority and the cargo-billets still as material
reference (`barracks-bunks-worn.prompt.txt`). Each component's alpha mask
registers with the original at IoU 0.86 (the thin open frame) to 0.98, so
`barracks-bunks-worn.tileset-authoring.json` reuses these cuts with `-worn` ids.
The deck draws a worn twin over the fitting while Life support is at Deficit.
