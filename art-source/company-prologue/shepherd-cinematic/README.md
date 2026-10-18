# Shepherd (Morrow): cinematic asset

Status: ACTIVE — pass 7 (appendable export and effects) is with the owner for review.
Written: 2026-10-18

Source art for cinematics; nothing here ships in the mod. This folder packs
the textured Shepherd (`../shepherd-surface/`) as one appendable Blender
collection. It carries the moving turret, the anchors a shot needs, and an
optional effects collection from the shared VFX kit. Pass 5 is deferred, so
there is one condition, clean, and one asset. The Hermes's export
(`../hermes-cinematic/`) is the pattern. The Shepherd has no gear or doors,
so there is no control empty.

## Use

Append the collection, not objects:

```python
with bpy.data.libraries.load(path, link=False) as (src, dst):
    dst.collections = ['SHEPHERD']                # or ['SHEPHERD', 'SWFX'] for the effects
scene.collection.children.link(dst.collections[0])
root = bpy.data.objects['SHEPHERD | root']
```

`shepherd.blend` is built, not committed (`finalize.sh`); `manifest.json`
and `validation.json` are tracked.

- **Move the root only.** Every part and anchor descends from it. The model
  is in hull units (+X bow, +Y port, +Z dorsal). `metres_per_unit` is 0.78
  (in the manifest and on the root), so she is 67 m long. A shot in metres
  scales the root by 0.78. Beside the Second Wind (0.65 m/u), scale the
  Shepherd root by 0.78 / 0.65 = 1.2.
- **Engines.**
  - `Engine 1|2 exhaust anchor` sit at the two main drive mouths (1 port,
    2 starboard), local +X down the exhaust, marked `running`.
  - The four maneuvering jets are `RCS port|starboard forward|aft jet
    anchor`, marked `working`.
- **Turret.**
  - `Turret 1 pivot` (WS 007, a twin small ballistic mount) turns about its
    local Z. The rest pose is the slot angle, -75 degrees (trained to
    starboard and a little forward), with an arc of 200 degrees.
  - The turntable, gun house, mantlet, barrels and feed ride the pivot; the
    deck ring and barbette stay.
  - `Anchor turret 1 port|starboard gun muzzle` ride the barrels, +X along
    the line of fire. Port is the barrel left of the line of fire, the kit's
    twin-mount tag.
- **Hardpoint.** WS 008, the universal mount on the gantry's bow bed, is
  fixed. `Anchor turret 2 gun muzzle` sits on its barrel, +X forward. The kit
  numbers every declared muzzle as a turret, so this is gun rig 2.
- **Drone bay.** WS 003 has three openings. Each one has a `Drone bay WS 003
  opening <i> mouth` at the rim and a `launch point <i>` on the floor. A
  drone lifts out along +Z, then turns forward. The manifest records the
  clear radius of each.
- **Lights** are the ship's own emissive lamps: running lamps (red to
  port, green to starboard), masthead,
  boom nav lamp, the bay's floodlights and glow strips, and the lit
  viewports. Nothing adds a light object unless the effects collection is
  appended.
- **Effects (optional).** `SWFX` comes from the shared kit
  (`../second-wind-vfx/`, `swfx.apply(state='clean', hull=<this folder>)`
  with this folder's `vfx_hull.py` profile). It provides:
  - LOW_TECH orange plumes, cores and bell glow on both drives;
  - cold-gas puffs on the four jets, and exhaust haze;
  - muzzle effects on the turret's two barrels and the hardpoint;
  - the running lamps, masthead, boom beacon and the bay floodlights as
    lights.

  Everything is animatable from `SWFX controls`. Append it in the same load
  as the ship.
- `manifest.json` holds the names, rest bounds, every anchor above, the lamps,
  an effects summary and source hashes.

The meshes are baked (bevels applied), and every mesh carries `hull_rest`
(rest position, hull units) and `hull_normal` (face normal, hull space).

## Build

`./finalize.sh` replays everything:
1. the geometry (rest pose) and the skin, with light service wear
   (`../second-wind-surface/apply_skin.py --hull ../shepherd-surface --wear
   service`; no Codex calls, because the part sources are the committed
   generations);
2. `export_ship.py`, `export_effects.py` and `verify_ship.py`;
3. `review_ship.py`: whole-ship stills, the turret at rest and trained, the
   drone bay, effects and studio-off; `--turn 96` adds a 96-frame turn (opt-in,
   because it is most of the run time);
4. `compose_review.py`.

`--skip-surface` reuses the current skin. Renders go to
`../shepherd-surface/build/final/`, and the sheets and turn go to `review/`;
both are ignored.

## Checks

`verify_ship.py` appends `SHEPHERD` into a factory-empty scene and checks:
- everything is under the root, nothing is linked, and every image is packed;
- object and polygon counts match the manifest;
- every mesh has its hull attributes, and `hull_rest` is the rest position;
- there are two drive anchors pointing aft and four working jet anchors;
- turning the turret pivot 30 degrees swings both its muzzles 30 degrees and
  leaves its barbette fixed;
- the hardpoint muzzle points along its slot angle;
- the three drone-bay mouth and launch-point anchors are present;
- moving the root moves the ship rigidly.

## Known limits

- The drone bay has no drones. A shot that launches one supplies it at a
  launch point.
