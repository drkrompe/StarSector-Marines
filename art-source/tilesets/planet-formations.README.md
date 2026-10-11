# Planet landscape art

Rock and ice landscapes share placement rules but have different materials and
shapes. `MineralFormation` owns four overhead polygon silhouettes: paired
boulders, three rock spires, paired ice blocks, and three ice pinnacles. The
complete body blocks movement, sight and direct fire. Terrain ridges are
permanent; these separate formations accept structural damage and leave low
passable rubble. Placement never paints supporting ground.

## Retained production inputs

Both sheets were made with the **built-in ImageGen tool**, following
`art-source/geometry-first-imagegen.md`.

- `PlanetFormationTemplate` renders the 1536x1152 guide under
  `art-source/templates/planet-formations.png`. Columns follow enum order;
  rows are intact, damaged and destroyed. The original prompt is
  `planet-formations.prompt.txt`, with its output retained as
  `planet-landscape-sources/planet-formations-first.png`. The correction prompt uses the guide as
  image 1 and that first pass as image 2. Its accepted 1448x1086 result is
  `planet-formations.raw.png`.
- `IceTerrainTemplate` renders the 1536x1536 nine-swatch guide under
  `art-source/templates/ice-terrain.png`. Reading order is summit, talus, then
  seven cliff-face values from dark to light. The original prompt/output and
  correction prompt are retained as `ice-terrain.prompt.txt`,
  `planet-landscape-sources/ice-terrain-first.png` and `ice-terrain.correction.prompt.txt`. The
  correction takes the first output as its reference. The accepted
  `ice-terrain.raw.png` is 1254x1254. Quiet granular detail avoids obvious
  repetition when the terrain renderer repeats a material across large masses.

The corresponding `.tileset-authoring.json` files are the export authority.
Generated output can shift registration as well as resolution. Formation cuts
were measured against the guide, with one full-footprint frame per column
shared by all damage states; their note records exact coordinates. No alpha
was keyed, masked, painted or trimmed. The ice cuts are inset within the nine
opaque samples. Template mains preserve existing measured source documents
when regenerating guides.

## Export and verify

With the configured `JAVA_HOME`, run from the repository root in PowerShell:

```powershell
'{"name":"planet-formations"}' | tools/authoring.cmd tileset_export -
'{"name":"ice-terrain"}' | tools/authoring.cmd tileset_export -
.\gradlew.bat :test --tests '*PlanetFormationArtTest' --tests '*FormationBrushTest' --tests '*MineralDamageTest'
.\gradlew.bat createSnapshots '-Psnapshot=planet-landscapes'
```

The exporter owns `mod/graphics/doodads/planet-formations.png`,
`mod/graphics/tilesets/ice-terrain.png` and their runtime tileset catalogs.
Both sheets are registered in the built-in registry and provider catalog.
Fog masks come from the code-owned silhouettes; destroyed variants have none.

The art test checks every visible exported pixel against the actual collision
cells and requires exposed ground between debris. Placement and combat tests
cover all four rotations, supporting-ground preservation and once-per-blast
damage. The visual suite uses actual map generation, navigation and player fog:
two countryside frames, two production Junction maps, and a live intact/cracked/
destroyed comparison. Paired landscape-on/off maps cover ten mission layouts
per planet palette and verify connected ground, objectives and landing access.
Dense facilities may correctly omit optional formations where no wild space fits.
