# Manticore (P): cinematic asset

Status: ACTIVE (pass 7, effects and cinematic export; verified 31/31 on the skinned battle build;
missing RCS, lamp lens glow and shield). Source art for cinematics; nothing here ships in the mod.
Written: 2026-10-20

The textured pirate Manticore (`../manticore-surface/`, on `../manticore-geometry/`) as one
appendable Blender collection, for the Asharu battle's long-range destroyer
(`asharu-battle-shot.md`: S5 on an 85 mm lens along her line of fire, S4 and S6 at about 4 km).
One condition, `battle` (the surfacing's battle wear; every drive running, guns live).
`../kite-cinematic/` is the pattern. This package renders nothing.

## Use

```python
with bpy.data.libraries.load(path, link=False) as (src, dst):
    dst.collections = ['MANTICORE (P)']           # or ['MANTICORE (P)', 'SWFX'] for the effects
for c in dst.collections:
    scene.collection.children.link(c)
root = bpy.data.objects['MANTICORE (P) | root']
```

`build/export/manticore-p.blend` is built, not committed (`finalize.sh`); `manifest.json` and
`validation.json` are tracked. Publish the built asset on Drive under
`builds/manticore-p/<commit>/`.

- **Move the root only.** The model is in hull units (+X bow, +Y port, +Z dorsal);
  `metres_per_unit` is 0.59 (owner override; on the root and in the manifest), so she is 91 m
  long, 95 m across the gun nacelles and 35 m keel to mast. A shot in metres scales the root by
  0.59; beside the Colossus Mk.III (0.495 m/u) scale it by 0.59 / 0.495 = 1.19.
- **Engines.** `Engine 1..5 exhaust anchor` at the five manticore.ship drive mouths, local +X
  down the exhaust (aft), marked `running`, `radius_m` on each: 1/2/3 the width-20 main drives
  (port, centre, starboard; mouth radius 6.0 units, 3.5 m), 4/5 the width-12 outer drives (port,
  starboard; 3.6 units, 2.1 m). LOW_TECH.
- **Turrets.** `Anchor turret <i> barrel <k> muzzle`, +X along the line of fire, numbered as the
  VFX profile's mounts: 1 the WS 000 Hellbore (the main gun; its barrel tip is the launch/muzzle
  anchor), 2 and 4 the WS 001/003 light guns, 3 the WS 002 twin needler (`barrel 1|2`). Rotate
  `Weapon WS 00x pivot` about its local Z (hull +Z) to train: the rest pose is the slot angle
  (WS 000 0 deg, arc 90; WS 001 80 and WS 003 -80, arc 235; WS 002 0, arc 235).
  `Weapon WS 00x elevation` lifts the gun about its local Y (negative lifts). The barbette stays.
- **Launchers.** `Anchor turret 5|6 tube 1..3 muzzle`, the WS 004 (port) and WS 005 (starboard)
  Harpoon pods, one launch anchor per tube, +X out of the tube (dead ahead). Fixed hardpoints:
  their pivots are never trained (arc 5).
- **System slots.** `Weapon WS 006..009 anchor`, the hidden canister-flak mounts, +X along the
  slot angle (-45, 45, 135, 225).
- **Lamps.** The sprite has no lights layer, so the lamps are the geometry's: the four running
  lamps (`Light port wing tip 1` red, `Light starboard wing tip 1` green, `Light centre stern 1`
  white, `Light centre prow 1` the white anti-collision strobe), each an empty at the lens with its
  `<name> lens` mesh, and the two red mast beacons (`Bridge mast beacon 1|2`). No RCS anchors are
  declared (the RCS quads are detail meshes).
- **Effects** (`SWFX`, optional): the shared kit (`../second-wind-vfx/`) through `vfx_hull.py`:
  LOW_TECH orange plumes and haze on all five drives, muzzle flash, tracer and brass on the four
  turrets' five barrels, launch blooms and missiles on the six Harpoon tubes, and lights on the six
  lamps. Controls are custom properties on `SWFX controls`: `thr_throttle` and per-engine
  `thr_throttle_e1..e5`, the `gun_` knobs per mount and barrel (`t<i>_<k>`), and `lit_running`,
  `lit_masthead`, `lit_beacon` for the lamps. No RCS puffs or landing dust.

`verify_ship.py` appends both collections into an empty scene and checks the root and its
`metres_per_unit`, packing, counts and bounds, hull attributes, every anchor's place and axis
against the manifest, each turret's training and elevation, the Harpoons staying fixed, the
effects' anchors, throttles and lights, and the root's rigid motion; it writes `validation.json`
and exits non-zero on any `BAD`.

## Rebuild

```sh
./finalize.sh                        # surfacing tail, export, effects, verify
./finalize.sh --skip-surface         # export the existing ../manticore-surface/build/skinned.blend
./finalize.sh --in <blend> --state clean   # any build with the geometry's names (a clean geometry build)
```

The steps by hand, from this folder:
`sh ../../blender.sh --background --factory-startup --python export_ship.py -- [--in PATH] [--state battle|clean]`,
then `export_effects.py`, then `verify_ship.py`.
