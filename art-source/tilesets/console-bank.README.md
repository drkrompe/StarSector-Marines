# Modular console banks

Six one-cell modules: two straight variants, an inside corner, an outside
corner and two end caps. Every exported frame is 128x128 with its complete
cell canvas. The operator side belongs to the authored stroke; placement
rotation belongs to the layout. The room editor, ship bridge and ground
command-center fitting share this kit and its path resolver.

## Retained production inputs

`ConsoleBankTemplate` produces an isolated six-piece guide and a connected
neighborhood guide. The latter solved the reskin's tendency to close each
independent piece with a rim: its continuous U and island worktops keep joins
open while providing both corner treatments.

Built-in ImageGen was used for all three passes:

1. `console-bank.prompt.txt`: isolated guide with `ship-bridge.raw.png`
   as the material reference; output retained at
   `../console-bank/first-pass.png`.
2. `console-bank-refine.prompt.txt`: first pass plus isolated guide;
   output retained at `../console-bank/material-study.png`.
3. `console-bank-connected.prompt.txt`: connected guide plus that material
   study; accepted output is `console-bank.raw.png`, unchanged at 1536x1024.

The authoring document records the reviewed 128px cuts from this connected
sheet. Both straight cells and the inside corner come from the U's top row.
The outside corner comes from the island's upper-left cell. Caps come from the
U's feet and retain their source orientations: both connect upward, with
controls right for end-left and left for end-right. These are source poses,
not extra facing metadata. The layout rotates placements to the stroke.
No keying, alpha transfer, repainting, per-piece scaling or atlas patch is used.
No derived height/normal maps are associated with this sheet.

## Reproduce

In a session worktree, with JAVA_HOME set as in CLAUDE.md:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/ConsoleBankTemplate.java art-source/templates/console-bank.png
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/ConsoleBankTemplate.java art-source/templates/console-bank-connected.png connected
'{"name":"console-bank"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=console-banks,console-rooms,console-editor'
.\gradlew.bat :test --tests '*ConsoleBankLayoutTest' --tests '*ProjectTilesetSeedsTest'
```

`ConsoleBankLayout.resolve` takes ordered cardinal cells, whether the stroke
closes, and which side the operator uses. It resolves straight variants,
inside/outside corners, end caps and clockwise image-space turns. It rejects
gaps, duplicate cells and too-short strokes; branched banks are outside this
prototype. Reverse the stroke and reverse the operator side to retain the
same working edge.

The snapshot suite converts those poses into ordinary doodad placements and
uses the production ground/prop collectors through their Java2D backend.
Its L and larger U/island are recomposed from six exported cells, not crops of
the generated assemblies. Both review boards are 1920x1080 at 100% UI scale;
detail views are 64px/cell and small views are 24px/cell. The second board
overlays the placement grid. Retained review copies are under
`../console-bank/review/`.

## Room placement

In Rooms, open a room and advance to **The fixtures**. Choose **Connected
console bank**, click or drag along rows and columns, and click each corner.
Choose the operator side and open ends or a closed island, then **Place bank**.
For a loop, stop beside the first cell; do not repeat it. **Clear path** cancels
the proposal. In **Single fixture / erase**, clicking any bank cell removes the
whole bank and its associated work points. Compare the hull before keeping it.

Banks are saved as paths in room JSON and replay through the ordinary room
fitting guards. Placement preserves reserved circulation and existing reachable
work positions. Closed islands claim their service core so it cannot become
isolated standable floor. The kit itself creates no crew duties.

`ConsoleBankLayout.resolveWorld` handles Y-up room/map coordinates; module
image turns are converted to normalized placement turns, including mirrored
rooms. The ship CIC uses two L banks and two U banks around the square tactical
plot, with a reserved two-cell inner aisle. It keeps all nine existing watch
positions and its hatch sentry. Ground command, industrial control, clinic
monitoring and civic server rooms use short wall-backed runs with two walkable
operator rows and real gaps at openings. Mess-galley stools sit at spaced
stations without blocking movement or adding cover. Large command chambers use
the shared tactical plot. T workshops and L/T freight templates carry seated
L banks; compact rooms that cannot fit a bank keep their single station.
Residential and retail stamps retain their own furniture.

The `console-rooms` suite checks and renders a generated ship and four command
building footprints/frontages. Its `ship-cic.png` pairs a detailed ship-room
render with the real editor's work-position and circulation overlay.
`building-readouts.png` and `building-console-templates.png` show the seated
ground-building consumers; the suite checks all four shell frontages.
`console-editor` drives the actual fixture
controls and grid events. Retained review images are under
`../console-bank/review/`. These are production Java2D renderer and Swing
captures, not live OpenGL evidence. Outside corners retain a slightly wider
housing than the straight sections.

## Damage states

All six modules have registered damaged and destroyed pairs in the separate
`console-bank-damage` sheet. Damaged pieces retain the connected worktop shape;
destroyed pieces leave low separated electronics and housing fragments. The
ordinary prop-damage channel preserves poses and releases the destroyed cell's
cover and fixture obstruction. The source guide, prompts, cuts, storage/clinic
companion sets and before/after assembly reviews are in
`../console-bank/damage/README.md`. Run `createSnapshots -Psnapshot=composable-damage`
to exercise the real damage boundary in all four rotations.
