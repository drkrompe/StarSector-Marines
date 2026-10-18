# Hermes: cinematic asset

Status: DONE (pass 7 accepted 2026-10-18). Source art for cinematics; nothing here ships in the mod.
Written: 2026-10-18

The textured Hermes (`../hermes-surface/`) as one appendable Blender
collection. It carries its moving parts (landing gear with doors, two side
boarding doors, three turrets), the anchors a shot needs, and an optional
effects collection from the shared VFX kit. It has one condition, clean (pass
5 is deferred until a cinematic needs the wrecked or patched boat), so there is
one asset. The Mudskipper's export (`../mudskipper-cinematic/`) is the pattern.

## Use

Append the collection, not objects:

```python
with bpy.data.libraries.load(path, link=False) as (src, dst):
    dst.collections = ['HERMES']                  # or ['HERMES', 'SWFX'] for the effects
scene.collection.children.link(dst.collections[0])
root = bpy.data.objects['HERMES | root']
```

`hermes.blend` is built, not committed (`finalize.sh`); `manifest.json` and
`validation.json` are tracked.

- **Move the root only.** Every part and anchor descends from it. The model
  is in hull units (+X bow, +Y port, +Z dorsal); `metres_per_unit` is 0.11 (in
  the manifest and on the root), so she is 9.0 m long. A shot in metres scales
  the root by 0.11. Beside the Second Wind (0.65 m/u), scale the Hermes root by
  0.11 / 0.65 = 0.169.
- **Controls.** Custom properties on the `Hermes control` empty, 0..1;
  keyframe them to animate:
  - `gear_up`: 0 is extended (built), 1 is stowed. Each leg folds into its
    well, and its two well doors close by their own drivers as it gets there.
  - `door_port` / `door_starboard`: the boarding door swings out and aft, up
    to 100 degrees.

  At rest (all 0) the gear stands on `landing.ground_z` (-13) in hull space,
  so put the root that far above the ground plane. A pose set from Python
  needs `bpy.data.objects['Hermes control'].update_tag()` before the frame is
  evaluated; a keyframed knob needs nothing.
- **Engines.**
  - `Engine 1..3 exhaust anchor` sit at the main drive mouths (1 port,
    2 centre, 3 starboard), local +X down the exhaust, marked `running`.
  - The two maneuvering jets are `RCS port|starboard maneuvering jet anchor`,
    marked `working`.
- **Turrets.**
  - `Turret 1..3 pivot` (slots WS 001-003) turn about their local Z. The rest
    pose is the slot angle: 1 trained 45 degrees to port, 2 trained 45 degrees
    to starboard, 3 trained aft.
  - The gun house, mantlet, guns, feed boxes and sight ride the pivot. The
    ring, barbette, collar and well stay.
  - `Anchor turret <i> port|starboard gun muzzle` ride the barrels, +X along
    the line of fire. Port is the barrel left of the line of fire, which is
    the kit's twin-mount tag.
- **Door and gear anchors.**
  - `Anchor Port|Starboard boarding door`: the opening centre, +X outboard.
  - `Anchor gear <leg> sole`: rides each leg, +X down, for dust and figures.
- **Lights** are the ship's own emissive lamps: four nose lamps and the troop
  bay strip. Nothing adds a light object unless the effects collection is
  appended.
- **Effects (optional).** `SWFX` comes from the shared kit
  (`../second-wind-vfx/`, `swfx.apply(state='clean', hull=<this folder>)` with
  this folder's `vfx_hull.py` profile). It provides:
  - MIDLINE warm-orange plumes, cores and bell glow on the three drives;
  - cold-gas puffs on the two jets, and exhaust haze;
  - muzzle effects on the six barrels;
  - the nose lamps as lights: the outer pair are red/green running lights,
    the inner pair white work lights.

  Everything is animatable from `SWFX controls`. Append it in the same load as
  the ship.
- `manifest.json` holds:
  - the names, rest bounds, and the engine, RCS, turret, boarding-door and
    gear anchors;
  - the control record (knobs and the hinges each drives);
  - the ground plane, lamps, an effects summary and source hashes.

The meshes are baked (bevels applied), and every mesh carries `hull_rest`
(rest position, hull units) and `hull_normal` (face normal, hull space).

## Build

`./finalize.sh` replays everything:
1. the geometry (rest pose) and the skin, with light service wear
   (`../second-wind-surface/apply_skin.py --hull ../hermes-surface --wear
   service`; no Codex calls, because the part sources are the committed
   generations);
2. `export_ship.py`, `export_effects.py` and `verify_ship.py`;
3. `review_ship.py`: flight stills (gear stowed), landed stills (doors open),
   effects, studio-off, and a 96-frame landed turn;
4. `compose_review.py`.

`--skip-surface` reuses the current skin. Renders go to
`../hermes-surface/build/final/`, and the sheets and turn go to `review/`;
both are ignored.

## Checks

`verify_ship.py` appends `HERMES` into a factory-empty scene and checks:
- everything is under the root, nothing is linked, and every image is packed;
- object and polygon counts match the manifest;
- every mesh has its hull attributes, and `hull_rest` is the rest position;
- there are three drive anchors pointing aft and two working jet anchors;
- turning each turret pivot 30 degrees swings both its muzzles 30 degrees and
  leaves its barbette fixed;
- at rest every gear sole stands on `ground_z`;
- `gear_up` 1 lifts every sole into the hull and closes all six gear doors;
- each boarding door swings clear of its opening;
- all knobs back at 0 restore the rest pose exactly;
- moving the root moves the ship rigidly.

## Known limits

- With a boarding door open, the passage shows the liner, not a lit interior
  beyond it.
- A shot that retracts the gear while landed must raise the root itself.
  Nothing ties the hull's height to `gear_up`.
