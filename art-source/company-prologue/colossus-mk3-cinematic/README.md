# Colossus Mk.III: cinematic asset

Status: ACTIVE (pass 7 built 2026-10-19; owner review next).
Written: 2026-10-19

This folder holds source art for cinematics; nothing here ships in the mod. It packs the
textured Colossus Mk.III (`../colossus-mk3-surface/`) as one appendable Blender collection,
with trainable turrets, the anchors a shot needs, a control for the keel grapples, and an
optional effects collection from the shared VFX kit. It is forked from `../colossus-cinematic/`.

She is the pirates' prize ship in the Asharu battle (`asharu-battle-shot.md`): she closes on
the merchant Colossus from above in S9 and fails to take her, then carries the captured Tarsus
clamped under her keel in S10. There are no damage states (owner brief): one clean state, one asset.

## Use

Append the collection, not objects:

```python
with bpy.data.libraries.load(path, link=False) as (src, dst):
    dst.collections = ['COLOSSUS MK.III']          # or ['COLOSSUS MK.III', 'SWFX'] for the effects
scene.collection.children.link(dst.collections[0])
root = bpy.data.objects['COLOSSUS MK.III | root']
```

`colossus-mk3.blend` is built (`finalize.sh`), not committed; the build goes to Drive
(`builds/colossus-mk3/<commit>/`). `manifest.json` and `validation.json` are tracked.

- **Move the root only.** Every part and anchor descends from it. Hull units (+X bow,
  +Y port, +Z dorsal); `metres_per_unit` 0.495, so she is 107 m long (110 x 85 x 44 m over
  the guns and pods at rest). A shot in metres scales the root by 0.495.
- **Main drives.** `Engine 1-6 exhaust anchor`: 1-3 on the port pod, 4-6 starboard, local
  +X down the exhaust.
- **Lift thrusters.** `Engine 7-12 exhaust anchor` are the six ventral lift thrusters (7 and
  8 the hold's big bells, 9-12 one on each cut-down module), +X down, each with its own
  throttle in the effects collection (`thr_throttle_e7`..`e12`).
- **Turrets.** Twelve, at colossus3.ship's small ballistic slots. `Turret <i> pivot` turns
  about its local Z; rest is the slot angle. The gun house, mantlet, barrels and roof
  searchlight ride the pivot; the sponson or tub, barbette, base ring and feed trunk stay.
  Muzzles: `Anchor turret <i> port|starboard gun muzzle` on the twin autocannon (WS 001,
  003, 011, 012), `Anchor turret <i> gun muzzle` on the long cannon (WS 002, 004, 009, 010)
  and the rotary chainguns (WS 013-016). +X along the line of fire.
- **Flare launchers.** WS 005-008 are fixed; `Anchor launcher <i> flare muzzle`. The kit has
  no flare effect, so a shot supplies one there.
- **Launch bay.** `Launch bay WS 017 launch point 1-4`, +X out of the bow bay mouth: where a
  shot launches her converted bay's fighters. Its rim and floor lamps are red.
- **Grapples.** `COLOSSUS MK.III control` has one knob, `grapples`: 0 stowed (the rest pose,
  booms folded flat under the keel), 0-0.5 swings the four booms down onto their receivers'
  line, 0.5-1 runs both telescoping stages out. At 1 each `Grapple <i> clamp anchor` sits on
  the Tarsus's receiver <i> (the pad face 0.15 units off the plate). Simple-expression drivers,
  so nothing needs script auto-run. To carry the Tarsus, parent her root at the manifest's
  `grapples.tarsus_origin_units`, scaled `tarsus_scale_in_these_units` (her 0.50 against these
  0.495 m/u), same heading, dorsal up.
- **Effects (`SWFX`).** LOW_TECH orange plumes on all twelve drives with haze and dust; sixteen
  gun rigs on the declared muzzles (the twins give two each); the running and bay lamps as
  lights. No rig preset: lighting is the shot's.

## Rebuild

`./finalize.sh [--skip-surface] [--turn N] [--samples S]`: the clean geometry and the skin
(`apply_skin.py --wear battle`, then `livery_decal.py`), the export, the effects, `verify_ship.py`
(appended into an empty scene: every turret trains its muzzles rigidly, the drives and lift
thrusters point the right way, the launch points face out, the grapples clamp at 1 and stow
again at 0, the root moves the ship rigidly), then the review renders and sheets in `review/`.
