# Tarsus: cinematic asset

Status: REVIEW (pass 7, round 1: both states exported, verified and posted for the owner).
Written: 2026-10-19

This folder holds source art for cinematics; nothing here ships in the mod. It packs
the textured Tarsus (`../tarsus-surface/`) as one appendable Blender collection per
condition state, each with the trainable turrets, the anchors a shot needs and an
optional effects collection from the shared VFX kit (`../second-wind-vfx/`).

She is the Asharu battle's prize (`asharu-battle-shot.md`): her drives are shot out,
Kites (P) board her at the two flank collars, and the Colossus Mk.III carries her off
by the four dorsal grapple receivers.

| State | File | What it is |
|---|---|---|
| clean | `clean/tarsus.blend` | In service: all six drives running, lamps lit, guns live |
| boarded | `boarded/tarsus.blend` | The prize: the drive section cratered and scorched (`ts_damage.py`, eleven craters), every drive dead, the drive lamps dark, torch rings at both Kite collars. Not destroyed |

The blends are built, not committed (`finalize.sh`); each state's `manifest.json` and
`validation.json` are tracked.

## Use

Append the collection, not objects:

```python
with bpy.data.libraries.load(path, link=False) as (src, dst):
    dst.collections = ['TARSUS']                  # or ['TARSUS', 'SWFX'] for the effects
scene.collection.children.link(dst.collections[0])
root = bpy.data.objects['TARSUS | root']
```

- **Move the root only.** Every part and anchor descends from it. Hull units (+X bow,
  +Y port, +Z dorsal); `metres_per_unit` 0.50 (manifest and root), so she is 94 m long.
  Beside the Colossus (also 0.50) no rescale is needed.
- **Drives.** `Engine 1-6 exhaust anchor` at the drive mouths, +X aft: 1 and 6 the two
  mains, 2-5 the four auxiliaries under them. `running` is true in clean only.
- **Turrets.** `Turret 1-4 pivot` (WS 001-004, twin light autocannon, arc 250) turn about
  local Z; the rest pose is the slot angle. `Anchor turret <i> port|starboard gun muzzle`
  ride the barrels, +X along the line of fire.
- **Docking.** `Clamp receiver 1-4 anchor` (dorsal; the Mk.III's grapples) and
  `Kite collar port|starboard anchor` (flanks; the Kites' boarding collars), +X out of the
  hull, in both states. The manifest gives their locations in units and metres.
- **Scars (boarded).** The manifest's `scars` is ts_damage's table: id, kind, location,
  outward normal, radius, the parts each crater cut. `Scar <id> smoke anchor` and
  `Scar <id> spark anchor` (+X out of the hull) key the effects.
- **Effects (optional).** `SWFX` from `swfx.apply(state, hull=<this folder>)` with this
  folder's `vfx_hull.py`:
  - clean: LOW_TECH orange plumes, cores, bell glow and haze on all six drives
    (`thr_throttle`, `thr_throttle_e1`..`e6`), muzzle effects on the eight barrels,
    the masthead, mast-tip, arm, collar and stern lamps as lights;
  - boarded: no drive effect; the kit's conduit sparks at the three spark anchors
    (`smk_sparks`) and a smoke wisp at each of the eight smoke anchors
    (`smk_damage_smoke`), built with the kit's own wisp builder.

  Both export with `smk_atmosphere` 0 (vacuum: the battle is in low orbit) and no landing
  dust; a shot of the fall into the atmosphere keys `smk_atmosphere` up.

## Rebuild

`./finalize.sh` (or `--skip-surface` to reuse the skinned builds): geometry, skin,
boarded scars, then export, effects, verify and one S7 review still per state.
