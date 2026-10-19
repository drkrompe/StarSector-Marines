# Broadsword: cinematic asset

Status: DONE (pass 7 accepted as final for C2, 2026-10-19). Source art for cinematics; nothing here ships in the mod.
Written: 2026-10-19

The textured Broadsword (`../fighters-surface/broadsword/`) as one appendable
Blender collection, built for C2, where two of them pass 55-80 m off the
Legion's bridge glass. It carries its retractable landing gear, the anchors a
shot needs, four running lights, and an optional effects collection from the
shared VFX kit. It has one condition, clean, so there is one asset. The
Hermes's export (`../hermes-cinematic/`) is the pattern.

## Use

Append the collection, not objects:

```python
with bpy.data.libraries.load(path, link=False) as (src, dst):
    dst.collections = ['BROADSWORD']              # or ['BROADSWORD', 'SWFX'] for the effects
scene.collection.children.link(dst.collections[0])
root = bpy.data.objects['BROADSWORD | root']
```

`broadsword.blend` is built, not committed (`finalize.sh`); `manifest.json`
and `validation.json` are tracked. The built file is on Drive under
`builds/broadsword/<commit>/`.

- **Move the root only.** Every part and anchor descends from it. The model
  is in hull units (+X nose, +Y port, +Z dorsal); `metres_per_unit` is 0.50
  (in the manifest and on the root), so she is 15.5 m long and 14.1 m across
  the blades. A shot in metres scales the root by 0.50. Beside the Legion
  (0.99 m/u), scale the Broadsword root by 0.50 / 0.99 = 0.505.
- **Control.** One custom property on the `Broadsword control` empty, 0..1;
  keyframe it to animate:
  - `gear_down`: 0 is the rest pose, gear up and doors shut (flight; C2 needs
    nothing else). 1 is down: the doors open over the first third, the legs
    swing down through the whole range.

  At 1 the wheels stand on `landing.ground_z` (-5.2) in hull space, so put the
  root that far above a deck. A pose set from Python needs
  `bpy.data.objects['Broadsword control'].update_tag()` before the frame is
  evaluated; a keyframed knob needs nothing.
- **Engines.** `Engine 1|2 exhaust anchor` (1 port, 2 starboard) sit at the
  drive mouths, local +X down the exhaust, marked `running`.
- **Guns.** The variant's two linked light machine guns sit in hidden
  hardpoints (WS 001/002) and are fixed: aim the fighter. `Anchor turret 1|2
  gun muzzle` is on each barrel tip, +X along the line of fire (the kit's
  declared-muzzle name; there is no turret). The geometry's own `Weapon WS
  <id> anchor|muzzle` empties stay, with the empty missile slots and the flare
  launcher (WS 005).
- **Gear.** `Anchor gear <leg> sole` rides each leg, +X down, for dust and
  figures.
- **Lights** are the fighter's own emissive running lights: red on the port
  blade tip, green on the starboard, a white tail light and a white
  anti-collision strobe behind the canopy (positions, colours and blink in
  `running_lights`). Nothing adds a light object unless the effects collection
  is appended.
- **Effects (optional).** `SWFX` comes from the shared kit
  (`../second-wind-vfx/`, `swfx.apply(state='clean', hull=<this folder>)` with
  this folder's `vfx_hull.py` profile). It provides LOW_TECH orange plumes,
  cores and bell glow on the two drives, exhaust haze, muzzle flash, tracers
  and brass on the two guns, and the four running lights as lights (the strobe
  blinks). Everything is animatable from `SWFX controls`. Append it in the same
  load as the ship.

The meshes are baked (bevels applied), and every mesh carries `hull_rest`
(rest position, hull units) and `hull_normal` (face normal, hull space).

## Build

`./finalize.sh` replays everything:
1. the geometry (gear up) and the skin, with light service wear
   (`../second-wind-surface/apply_skin.py --hull ../fighters-surface/broadsword
   --wear service`; no Codex calls, because the part sources are the
   committed generations);
2. `export_ship.py`, `export_effects.py` and `verify_ship.py`;
3. `review_ship.py`: flight stills, landed and gear stills, close-ups,
   effects, studio-off, and a 96-frame turn;
4. `compose_review.py`.

`--skip-surface` reuses the current skin. Renders go to
`../fighters-surface/broadsword/build/final/`, and the sheets and turn go to
`review/`; both are ignored.

## Checks

`verify_ship.py` appends `BROADSWORD` into a factory-empty scene and checks:
- everything is under the root, nothing is linked, and every image is packed;
- object and polygon counts match the manifest;
- every mesh has its hull attributes, and `hull_rest` is the rest position;
- two drive anchors point aft, and two gun muzzle anchors sit on the barrel
  tips pointing forward;
- the four running lights are emissive;
- at rest the stowed gear sits above its shut doors; `gear_down` 1 stands all
  six wheels on `ground_z` and opens all six doors, and the sole anchors reach
  the ground; back at 0 the rest pose is restored exactly;
- moving the root moves the fighter rigidly.

## Known limits

- With the doors open, the wells show a dark liner, not modelled bays.
- A shot that lowers the gear in flight and then lands must place the root
  itself. Nothing ties the hull's height to `gear_down`.
