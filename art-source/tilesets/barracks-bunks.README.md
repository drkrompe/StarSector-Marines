# Ship barracks bunks and personal lockers

Generated barracks and crew quarters use the same quiet graphite and grey-green
kit: two facing ranks of real-length bunks, pillows toward the outer bulkheads,
personal locker banks at the near ends and amid the far rank. The two-cell central aisle and
whole hatch approaches remain open. Locker banks replace loose kit and pipe
props without changing the fitting's rest capacity. Authored room layouts stay
authoritative; ground-side berthing keeps its existing art.

## Geometry and layers

A cell is about 0.75 m (a crewman's shoulders are 0.6 cells). The bunk is one
cell by three: a 0.70 x 2.20 m base round a 0.59 x 2.0 m mattress. It was one by
two, 1.5 m long, and a 1.8 m crewman did not fit. The locker banks (1x2, three
compartments) were already true and keep their original generation.

`barracks-bunks.raw.png` is two generations side by side, joined untouched by
`templates/real-scale-montage.py`. The left 1254 px are the original built-in
ImageGen sheet (`barracks-bunks.prompt.txt`, six 1x2 components from the old
guide); only its two locker banks are cut now. The right half is the real-length
refit: `BarracksBunkTemplate` draws the bunk base, bedding with the pillow at the
image top, bedding with the pillow at the image bottom and the open retaining
frame at 320 px per cell, one 1x3 frame per column on a 1920x1280 guide.
ImageGen reskinned it with the old raw as material reference only
(`barracks-bunks-refit.prompt.txt`; provenance `barracks-bunks.raw.png.provenance.json`).
It returned every piece about 10% large and centred, so each cut is the guide's
full 1x3 frame mapped through the piece's measured box onto the guide's box; the
four layers register with one another. The exporter downsamples to 64 px per cell.
No painting, keying, post-export patches, height maps or normal maps were used.

The base is the same at both ends; the bedding chooses the head. A sprite's top
edge lies at its footprint's highest row, so `bedding-high` heads a bunk at its
high row and `bedding-low` at its low row. Bunks draw base, bedding, then rails.
The complete assembly rotates and mirrors with the room. Only the base owns
cover and physical blocking, which is applied after circulation acceptance.

Berthing is 8x8: two facing ranks of racks, heads to the hull, and a two-cell
passage between them. REST stands on the passage cell at a bunk's foot and
targets the foot cell; the resting hand is drawn lying along the bunk, head on
the pillow (`WorkMotion.SLEEP`). A locker bank is a cell shorter than a bunk, so
it stands against the hull and STOW is worked from the alcove left between its
neighbours. A blocked footprint omits all layers together. Folded clothing is
decorative scenery, not tracked personal inventory. The brig's cells take the
same bunk, three cells deep against the bars.

## Reproduce and inspect

Set JAVA_HOME as documented in CLAUDE.md, then:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/BarracksBunkTemplate.java art-source/templates/barracks-bunks.png
'{"name":"barracks-bunks"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=ship-materials'
```

The suite renders the actual generated Valkyrie barracks at 24 and 64px per cell,
plus all four quarter turns of an 8x8 fitting. The `ship-duty` motion board
follows one sleeper (`SLEEP`). It uses production collectors
and the headless Java2D renderer, not a live-game OpenGL capture. Direct fitting
tests cover all eight rotations/mirrors, preserved berth capacity, clear rear
hatches, reachable rest/stowage and the frame's transparent opening.

The same suite also checks the corrected dark upper armory wall: hull boundary
walls can retain VOID ground, but still need the ship's structural wall material.

## Worn condition

`barracks-bunks-worn.raw.png` is the worn twin, laid out the same way. Its left
half repaints the original sheet (`barracks-bunks-worn.prompt.txt`, provenance
`barracks-bunks-worn.raw.png.provenance.json`): chipped paint, a bare stained
mattress under a tangled blanket, dented and ajar locker doors, unfolded
clothing. Its right half repaints the real-length refit with the refit
generation as geometry authority and the old worn sheet as material reference
(`barracks-bunks-worn-refit.prompt.txt`, provenance
`barracks-bunks-worn-refit.provenance.json`). Each piece's alpha mask registers
with the clean one at IoU 0.95 (the thin open frame) to 0.99, so
`barracks-bunks-worn.tileset-authoring.json` reuses the clean cuts with `-worn`
ids. The deck draws a worn twin over the fitting while Life support is at Deficit.
