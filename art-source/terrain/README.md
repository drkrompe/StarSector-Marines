# Natural terrain authoring

Run `gradlew.bat layerAuthoring` and choose **Terrain**. The page opens on the
freight-pass example, using the production battle renderer.

- Drag to raise a continuous terrain mass; right-drag (or select Erase) to cut a pass.
- The wheel changes radius. Each drag is one undo/redo operation.
- Rock / grass-topped finish and face depth affect appearance. Ruggedness and seed reshape the edge.
- Show blocked cells to compare the smooth surface with the actual navigation grid.
- Road context loads a `.road.json` of the same dimensions. New creates the desired extent.
- Save / Open round-trip a `.terrain.json`; `previewRoad` is optional editor context, relative to the project root.
  The runtime plan reader ignores that guide: mission code owns road and terrain composition.

Every raised mass blocks ground travel, sight and direct fire, and refuses wall damage.
Summits are inaccessible. This tool does not create playable elevations or ramps.

For mission integration, load the JSON through the host's normal content lifecycle,
read `TerrainPlan`, then call `TerrainBrush.stamp(plan, grid, topology, reserved)`
during generation after road reservation/stamping and before final topology setup.
The optional cell mask reserves landing areas, objective access and circulation.
Placement refuses all overlap before mutation. The owning recipe must still
validate connectivity, vehicle clearance, objectives and arrival routes afterwards.
Rebuild from clean ground to apply edits; do not restamp a running battle.
The freight-pass example remains a tool fixture. Open the Junction, Relay Nine
and Thunder Run instead consume their own documents under `mod/data/world/terrain/`, installed
at application startup by `TerrainCatalog`. Open those documents in Terrain to
edit their strokes with the matching road context. Road context does not include
mission buildings: after edits, run the production map evidence to check the
whole layout before accepting a change.

`freight-pass.terrain.json` and its separate road document demonstrate the format.
`gradlew.bat createSnapshots '-Psnapshot=terrain'` produces production frames and
exercises actual paint, erase, undo, redo, save and reopen interactions at
1920x1080 / 100%, plus obstruction and freight-clearance checks.

`gradlew.bat createSnapshots '-Psnapshot=story-roads'` renders the actual story
maps at 1920x1080 and validates final natural collision, connected outdoor
space, objective access, road ownership and clear western boat approaches.
Junction and Relay Nine are checked with three verdant seeds and the other
surface palettes. `commanderEvidence` with `-Pmission=junction` or
`-Pmission=relay-nine` exercises their fitted-boat arrivals and mission play.

`gradlew.bat createSnapshots '-Psnapshot=thunder-terrain'` renders Thunder Run's
hill-framed freight corridor at 1920x1080 / 100%. Six production maps check
connected outdoor ground, northern and southern infantry flanks, clear boat
approaches, and actual cargo/APC/tank clearance. The road stays traversable,
but closing its checkpoint band leaves only an infantry bypass.
`commanderEvidence '-Pmission=thunder-run'` exercises the named campaign squads,
fitted player boats, mechanized breakthrough and freight settlement.

`python art-source/terrain/bake_materials.py` reproduces the shipped material atlas
and catalog from `materials.recipe.json`, using only Python's standard library.
The script is their only producer. Ground UV clipping, not new artwork, creates
individual cliff shapes.
