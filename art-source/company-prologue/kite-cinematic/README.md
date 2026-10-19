# Kite (P): cinematic asset

Status: ACTIVE (pass 7 in owner review). Source art for cinematics; nothing here ships in the mod.
Written: 2026-10-19

The textured pirate Kite (`../kite-surface/`) as one appendable Blender collection, for the
Asharu battle's two boarding shuttles (`asharu-battle-shot.md`: distant in S4, in S7, 350-550 m
off in S9). One condition, clean (no damage states are needed). `../hermes-cinematic/` is the
pattern.

## Use

```python
with bpy.data.libraries.load(path, link=False) as (src, dst):
    dst.collections = ['KITE (P)']                # or ['KITE (P)', 'SWFX'] for the effects
for c in dst.collections:
    scene.collection.children.link(c)
root = bpy.data.objects['KITE (P) | root']
```

`kite-p.blend` is built, not committed (`finalize.sh`); `manifest.json` and `validation.json`
are tracked. The built asset is published on Drive under `builds/kite-p/<commit>/`.

- **Move the root only.** The model is in hull units (+X bow, +Y port, +Z dorsal);
  `metres_per_unit` is 0.80 (on the root and in the manifest), so she is 52 m long and 65 m
  across the wings. A shot in metres scales the root by 0.80; beside the Colossus (0.50 m/u)
  scale it by 0.80 / 0.50 = 1.6.
- **Engines.** `Engine 1..8 exhaust anchor` at all eight kite.ship engine mouths, local +X down
  the exhaust, marked `running`: 1/4 the wing nacelles, 2/3 the main drives, 5/6 the canted
  wing-tip maneuvering jets, 7/8 the tail drives.
- **RCS.** `RCS nose|wing port|starboard 1|2 anchor` at the eight nozzles, +X outward, `working`.
- **Weapons.** `Anchor turret <i> <tag> muzzle`, +X along the line of fire:
  1 the WS 001 Hammer (`tube 1|2`), 2 the WS 002 Swarmer (`tube 1..4`), both fixed hardpoints;
  3 the WS 003 twin light machine gun (`barrel 1|2`). Rotate `Weapon WS 003 pivot` about its
  local Z to train (rest dead ahead, arc 200); `Weapon WS 003 elevation` lifts the guns
  (negative Y rotation lifts).
- **Boarding.** The ventral boarding collar (`Hull ventral boarding collar ring`, hatch inside)
  is what mates to the prize's hull; it does not move.
- **Lamps** are the ship's own emissives: wing-tip nav lights (red port, green starboard), a red
  dorsal beacon, a white stern light, two nose landing lights, the dorsal spotlight and the lit
  cabin windows.
- **Effects** (`SWFX`, optional): the shared kit (`../second-wind-vfx/`) through `vfx_hull.py`:
  MIDLINE orange plumes and haze on all eight drives with per-engine throttles, RCS puffs, launch
  effects on the two launchers and muzzle flash, tracer and brass on the twin MG, and lights on
  the lamps. Controls are custom properties on `SWFX controls`.

`verify_ship.py` appends both collections into an empty scene and checks the root, packing,
counts, hull attributes, every anchor's place and axis, the turret's training and the root's
rigid motion. `review_ship.py --shot` renders the S9 read: 28 mm, 450 m off her starboard bow,
under the battle blockout's sun.
