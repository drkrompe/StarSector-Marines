# Ship main stores

Main stores uses four parallel 2x6 shelf banks in the standard 18x12 room.
Two-cell picking aisles and end cross-aisles connect to a separate handling
lane beside three pallet positions. A tally desk and packing/recovery bench
occupy the shallow end margins, moving to another clear position when a hatch
takes their preferred site. Cartons, freight cases and sealed drums are grouped
by bank. A picked slot exposes the shelf beneath it rather than extra floor.

## Art contract

`MainStoresTemplate` draws the retained 1024x1024 RGBA guide at 64px/cell:
2x6 shelf bed and retaining frame at (48,48)/(272,48); four 2x2 canvases
at x=496/752, y=48/304 for cartons, cases, drums and a pallet; 3x1 tally desk
and packing bench at (48,592)/(48,784). Stock is inset within its canvas.

Built-in ImageGen reskinned that guide using `armory-workshop.raw.png` as the
material reference and the exact `main-stores.prompt.txt` prompt. The unchanged
accepted output is `main-stores.raw.png`, 1254x1254 RGBA. The authoring JSON
records measured cuts, including full padded stock canvases and matched rack
canvases. The ordinary exporter crops/downsamples to 64px/cell; no pixels are
repainted, keyed or patched, and there are no height/normal companions.

The rack base owns the physical footprint and cover. It receives three
independently selected 2x2 stock slots, then the transparent retaining frame.
The pallet uses the same stock overlays. Stock and frame add no extra cover or
ballistic height. All layers turn and mirror with the room. The assembly is
placed whole or omitted whole; an empty stock slot is still blocked shelving.
Physical blocking applies only after the circulation check accepts the room.

## Integration and evidence

The generated ship's STOCKROOM uses this fitting; authored layouts take priority
and ground-side stockrooms retain their own fitting. Small utility stores use a
worked pallet bank beside a gangway. STOW belongs beside shelves and pallets,
READOUT beside the tally desk, and REPAIR beside the recovery bench. Displayed
stock is visual dressing, not a campaign inventory or a new cargo capacity.

With JAVA_HOME set as documented in CLAUDE.md:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/MainStoresTemplate.java art-source/templates/main-stores.png
'{"name":"main-stores"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=ship-materials'
```

The suite includes the actual generated Valkyrie stores at 24 and 64px/cell,
a neighbouring-room view, and four posed fitting views. These use production
render collectors and the headless Java2D drain, not a live OpenGL capture.
Direct tests cover all eight rotations/mirrors, hatch approaches, utility
stores, accessible work, physical empty shelves, frame holes and stock padding.
