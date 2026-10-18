# Mudskipper: cinematic asset

Status: ACTIVE. Source art for cinematics; nothing here ships in the mod.
Written: 2026-10-18

The textured Mudskipper (`../mudskipper-surface/`) as one appendable Blender
collection, with its landing kit (legs, plug cargo doors, freight ramps), the
anchors a shot needs and an optional effects collection from the shared VFX
kit. She has one condition, clean (pass 5: no damage states needed yet), so
there is one asset rather than one per state. The Second Wind's per-state
export (`second-wind-cinematic/second-wind-states/`) is the pattern.

## Use

Append the collection, not objects:

```python
with bpy.data.libraries.load(path, link=False) as (src, dst):
    dst.collections = ['MUDSKIPPER']              # or ['MUDSKIPPER', 'SWFX'] for the effects
scene.collection.children.link(dst.collections[0])
root = bpy.data.objects['MUDSKIPPER | root']
```

`mudskipper.blend` is built, not committed (`finalize.sh`); `manifest.json`
and `validation.json` are tracked.

- **Move the root only.** Every part and anchor descends from it. The model
  is in hull units (+X bow, +Y port, +Z dorsal); `metres_per_unit` is 0.85 (in
  the manifest and on the root), so she is 67 m long. A shot in metres scales
  the root by 0.85.
- **Landing.** Custom properties on the `Landing control` empty (0..1),
  keyframe them to animate: `deploy` (legs out), `load` (her weight on the
  legs: each ram strokes up 0.5 m), `door_port` / `door_starboard` (the plug
  leaves move inboard, then run into hull pockets), `ramp_port` /
  `ramp_starboard` (the ramp runs out through the sill slot, then tips down;
  its angle is solved from `deploy` and `load` so the toe meets the ground).
  Landed (`deploy` 1, `load` 1) the ground is at `landing.ground_z_landed`
  (-12.9) in hull space: put the root that far above the ground plane. A pose
  set from Python needs `bpy.data.objects['Landing control'].update_tag()`
  before the frame is evaluated; a keyframed knob needs nothing.
- **Engines.** `Engine 1..3 exhaust anchor` at the drive bell mouths (1 the
  centre drum, 2 port pod, 3 starboard pod), local +X down the exhaust,
  `running`. The two maneuvering jets are `RCS port|starboard maneuvering jet
  anchor`, `working`.
- **Turret.** `Turret 1 pivot` (slot WS 003) turns about its local Z; the
  rest pose is the slot angle, trained aft. The gun house, mantlet and both
  guns ride it; the barbette and collar stay. `Anchor turret 1 port|starboard
  gun muzzle` ride the barrels, +X along the line of fire.
- **Landing anchors.** `Anchor Port|Starboard cargo bay door` (opening centre,
  +X outboard), `Anchor Port|Starboard freight ramp toe` (rides the ramp), and
  `Anchor leg <name> sole` (rides each foot, +X down) for dust and figures.
- **Lights** are the ship's own emissive lamps and ports; nothing adds a light
  object unless the effects collection is appended.
- **Effects (optional).** `SWFX`, from the shared kit
  (`../second-wind-vfx/`, `swfx.apply(state='clean', hull=<this folder>)`
  with this folder's `vfx_hull.py` profile): orange LOW_TECH plumes, cores and
  bell glow on the three drives, cold-gas puffs on the two jets, exhaust haze,
  landing dust, muzzle effects on the twin MG and her lamps, all animatable
  from `SWFX controls`. Append it in the same load as the ship.
- `manifest.json`: names, rest bounds, engine, RCS, turret, bay-door, ramp and
  leg anchors, the landing record (knobs, travels, ground planes, ramp angle),
  lamps, effects summary and source hashes.

The meshes are baked (bevels applied), every mesh carries `hull_rest` (rest
position, hull units) and `hull_normal` (face normal, hull space).

## Build

`./finalize.sh` replays everything: the geometry (rest pose) and the skin
(`../second-wind-surface/apply_skin.py --hull ../mudskipper-surface`, no
Codex calls: the part sources are the committed generations), then
`export_ship.py`, `export_effects.py`, `verify_ship.py`, `review_ship.py`
(flight and landed stills, effects, studio-off, a 96-frame landed turn) and
`compose_review.py`. `--skip-surface` reuses the current skin. Renders go to
`../mudskipper-surface/build/final/` and the sheets and turn to `review/`;
both are ignored.

## Checks

`verify_ship.py` appends `MUDSKIPPER` into a factory-empty scene and checks:
- everything is under the root, nothing is linked, every image is packed;
- object and polygon counts match the manifest;
- every mesh has its hull attributes, and `hull_rest` is the rest position;
- there are three drive anchors pointing aft and two working jet anchors;
- turning the turret pivot 30 degrees swings both muzzles 30 degrees and
  leaves the barbette fixed;
- landed, every sole sits on `ground_z_landed`;
- open doors clear the opening by 7.6 units;
- both ramps put their lowest point on that ground about 39 units out;
- all knobs back at 0 restore the rest pose exactly;
- moving the root moves the ship rigidly.

## Known limits

- The open bay shows the dark door frame, not a modelled interior.
- A landed shot lowers the root itself; nothing ties the hull's height to
  `load`.
