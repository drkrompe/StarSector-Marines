# Ship gym

Built-in ImageGen reskin of `ShipGymTemplate`, using the armory sheet as a muted
material reference. No API/CLI generation was used.

- Guide: `art-source/templates/ship-gym.png`, 1024 square, 64px/cell equipment.
- Accepted raw: `ship-gym.raw.png`, 1254 square RGBA.
- Exact prompt: `ship-gym.prompt.txt`.
- Measured cuts and export: `ship-gym.tileset-authoring.json`.
- Runtime: `mod/graphics/tilesets/ship-gym.png` and matching tileset JSON.

The four physical assemblies are a 2x3 treadmill, 2x3 rower, 2x3 bench press
and 4x1 dumbbell rack. Three opaque interior samples supply rubber mats, pale
non-slip coping and muted teal pool water. Object cuts follow returned alpha;
floor cuts exclude their border and transparent margins. No height or normal
companions belong to this atlas.

```powershell
'{"name":"ship-gym"}' | .\tools\authoring.cmd tileset_export -
```

The generated 14x10 gym separates cardio, strength and mats from a 4x4 basin.
A complete walking surround, central aisle and perimeter route remain clear.
Seven exclusive EXERCISE positions use dry floor. Water is nonwalkable,
see-through terrain, not a wall or a swimming affordance. Furniture, surfaces
and work points share the room pose. Small and ground gyms keep their original
fitting; authored layouts retain priority.

Ship views invoke the existing water shader when water is visible. Structural
displacement, surface relief, shadows and lighting stay disabled in that host;
the color target preserves transparency outside the hull. Other hosts retain
their existing rendering settings. The deck's absolute clock drives the water,
so repeated render passes share one phase; dismissal releases its GPU targets.

`ShipGymFittingTest` checks all eight poses, every perimeter hatch, simultaneous
hatches, connected circulation, seven reachable exercise positions, pool
topology and the dry surround. `createSnapshots -Psnapshot=ship-materials`
includes generated 24/64px-per-cell views and four rotated fitting views through
production render collectors drained by headless Java2D (static water).
`shaderEvidence --tests '*ShipGymWaterGlEvidence'` exercises the actual deck-host
OpenGL path: successive frames changed 36,665 water pixels and zero dry pixels
on the RTX 3080 / NVIDIA 591.86, while preserving the background outside the hull.
Frames are written under `build/surface-relief/gym-water-{0,1}.png`.

The broader `ShipDeckBattleSceneTest` run passed ten cases but failed
`aShipsCrewTurnHerBoatsRound` (no boat hull repaired within three minutes).
That same case also failed with the original `RoomFittings` dispatcher restored,
so it is not a regression from selecting the new gym layout.
