# Wolf (P): cinematic asset

Status: ACTIVE — pass 7 in owner review.
Written: 2026-10-19

Source art for the Asharu battle cinematic (`../asharu-battle/asharu-battle-shot.md`);
nothing here ships in the mod. This folder makes the textured Wolf (P)
(`../wolf-surface/`) one appendable Blender file: the ship as one collection,
plus an optional effects collection from the shared VFX kit
(`../second-wind-vfx/`). One condition, `clean` in the kit's terms: the
battle-worn livery with every drive running and the guns live. No damage
states yet.

## Use

Append the collections, not objects:

```python
with bpy.data.libraries.load(path, link=False) as (src, dst):
    dst.collections = ['WOLF_P']             # or ['WOLF_P', 'SWFX'] for the effects
for c in dst.collections:
    scene.collection.children.link(c)
root = bpy.data.objects['WOLF_P | root']
```

`wolf-p.blend` is built, not committed (`finalize.sh`); `manifest.json` and
`validation.json` are tracked. Every image is packed. The built file goes to
the shared Drive under `starsector/builds/wolf-p/<short-commit>/` with its
manifest.

- **Move the root only.** Every part and anchor descends from it. The model is
  in hull units (+X bow, +Y port, +Z dorsal; one sprite pixel per unit);
  `metres_per_unit` is 0.76 (on the root and in the manifest): 72 m from
  prow to stern, 37 m across the missile pods, about 21 m deep. A shot in
  metres scales the root by 0.76.
- **Two variants, one hull.** `wolf-p.blend` (`WOLF_P`, the pirate skin,
  battle wear) and `wolf.blend` (`WOLF`, the convoy's own Wolf: the base hull,
  no pirate armour, its aft WS 002 / WS 003 PD laser turrets in wells
  sunk into low faired sponsons, an independent pale blue-grey livery, service wear; manifest-merchant.json,
  validation-merchant.json). `finalize.sh --variant merchant` builds the second.
  The merchant's WS 004 and WS 005 / WS 006 carry the vanilla wolf_Assault
  heavy blaster and Harpoons, built with the mining blaster and Annihilator
  forms until the turret kit has their own (manifest `built_as`).
- **Engines.** `Engine 1-3 exhaust anchor` at the three bell mouths, local +X
  down the exhaust: 1 port, 2 centre, 3 starboard, HIGH_TECH blue.
- **Turret.** `Turret 1 pivot` (WS 001, the ion cannon) trains about its local
  Z (rests ahead, arc 150 degrees); `Turret 1 elevation` elevates (negative
  local Y lifts the emitter).
- **Hardpoints.** `Weapon WS 004 mount` (the mining blaster, in the prow's trough
  between its jaws) and `Weapon WS 005 / WS 006 mount` (the Annihilator launcher heads on the
  wing tips), fixed,
  +X along the bow.
- **Gun effects.** Kit mounts, in order: 1 ion cannon, 2 mining blaster (both
  `energy`: flash, light and bolt in the weapon's own glow colour, ion blue
  and blaster red), 3 and 4 the Annihilator pods (`launcher`, five tubes each:
  launch bloom and rockets). A shot rebuilds the kit on the appended ship
  (`swfx.apply(state='clean', hull=<this folder>)`) and fires with
  `swfx.module('guns').fire(ctx, mount, start_frame, shots, interval)`, as
  `review_ship.py --fire` does.
- **Lamps.** Two amber neck lamps (the sprite's), red and green pod-tip lights,
  a white stern light.

## Files

| File | What |
|---|---|
| `vfx_hull.py` | the hull profile for the shared kit (engines, mounts and their kinds, energy colours, lamps) |
| `export_effects.py` | the SWFX collection on the packaged textured build -> `wolf-p.blend`, `manifest.json` |
| `verify_ship.py` | appends the asset the way a shot does and checks it -> `validation.json` |
| `review_ship.py` | studio, Asharu S9 and firing renders at 1920x1080 |
| `compose_review.py` | review sheets |
| `finalize.sh` | the whole sequence from `../wolf-surface/build/skinned.blend` |
