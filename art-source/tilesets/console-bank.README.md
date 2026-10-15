# Console bank prototype

Six one-cell modules: two straight variants, an inside corner, an outside
corner and two end caps. Every exported frame is 128x128 with its complete
cell canvas. The operator side belongs to the authored stroke; placement
rotation belongs to the layout. This kit is registered for rendering and
authoring but no production room fitting selects it yet.

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
.\gradlew.bat createSnapshots '-Psnapshot=console-banks'
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

The prototype proves cell composition, working-side selection and visual
registration. Outside corners have a slightly wider housing than the straight
sections. This is not a room-editor brush, a production room fitting, combat
acceptance or a live OpenGL capture.
