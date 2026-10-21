# Ship gym

Codex `image_gen` reskin of `ShipGymTemplate` (`tools/codex_imagegen.py`), with
the armory sheet as material reference only.

- Guide: `art-source/templates/ship-gym.png`, 1280 square, 128 px per cell,
  whole-cell frames with gutters (layout in the template's Javadoc).
- Accepted raw: `ship-gym.raw.png`, 1254 square RGBA (125.4 px per guide cell),
  with `ship-gym.raw.png.provenance.json`.
- Exact prompt: `ship-gym.prompt.txt`.
- Cuts: `ship-gym.tileset-authoring.json`, full placement frames for fixtures.
- Runtime: `mod/graphics/tilesets/ship-gym.png` and matching tileset JSON.

The equipment is real size against a crewman whose shoulders are 0.6 cells, so a
cell is about 0.75 m: a 1x3 treadmill (0.5 m belt), a 1x3 rower whose seat is
its own 1x1 piece (`ship-gym.rower-seat`, slid by `deck-prop-motions`), a 3x2
bench press (2.2 m bar racked over a narrow bench) and a 4x1 dumbbell rack.
Three opaque interior samples supply rubber mats, pale non-slip coping and muted
teal pool water. The first generation at 64 px per cell came back with every
machine about 1.3x its frame; drawing the guide at 128 px per cell and saying
the cut is mechanical held it to the guide. No height or normal companions
belong to this atlas.

```powershell
'{"name":"ship-gym"}' | .\tools\authoring.cmd tileset_export -
```

The generated 14x10 gym separates cardio, strength and mats from a 4x4 basin.
A complete walking surround, central aisle and perimeter route remain clear.
Nine exclusive EXERCISE positions use dry floor: two treadmills and two rowers
worked from the aisle at their feet, the press from the foot of its bench, two
on the mat and two at the rack. Water is nonwalkable,
see-through terrain, not a wall or a swimming affordance. Furniture, surfaces
and work points share the room pose. Small and ground gyms keep their original
fitting; authored layouts retain priority.

Ship views invoke the existing water shader when water is visible. Structural
displacement, surface relief, shadows and lighting stay disabled in that host;
the color target preserves transparency outside the hull. Other hosts retain
their existing rendering settings. The deck's absolute clock drives the water,
so repeated render passes share one phase; dismissal releases its GPU targets.

`ShipGymFittingTest` checks all eight poses, every perimeter hatch, simultaneous
hatches, connected circulation, nine reachable exercise positions, pool
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
