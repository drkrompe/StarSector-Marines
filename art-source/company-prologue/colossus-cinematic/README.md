# Colossus: cinematic asset

Status: DONE (pass 7 accepted 2026-10-19 at round 1: shipped for now, until the crashed state is needed).
Written: 2026-10-19

This folder holds source art for cinematics; nothing here ships in the mod. It
packs the textured Colossus (`../colossus-surface/`) as one appendable Blender
collection, with:

- the trainable turrets and the anchors a shot needs;
- an optional effects collection from the shared VFX kit.

Pass 5 is deferred, so there is one condition, clean, and one asset. The
crashed state (broken against Asharu's northern canyon wall, where the Colossus
went down with the Shepherd) waits for a cinematic that needs it; the hull is
already split into named modules for that. The Shepherd's export
(`../shepherd-cinematic/`) is the pattern. The Colossus has no gear or doors
that move, so there is no control empty.

## Use

Append the collection, not objects:

```python
with bpy.data.libraries.load(path, link=False) as (src, dst):
    dst.collections = ['COLOSSUS']                # or ['COLOSSUS', 'SWFX'] for the effects
scene.collection.children.link(dst.collections[0])
root = bpy.data.objects['COLOSSUS | root']
```

`colossus.blend` is built, not committed (`finalize.sh`). `manifest.json` and
`validation.json` are tracked.

- **Move the root only.** Every part and anchor descends from it.
  - The model is in hull units (+X bow, +Y port, +Z dorsal).
  - `metres_per_unit` is 0.50 (the owner's override, in the manifest and on the
    root), so she is 108 m long. A shot in metres scales the root by 0.50.
  - Beside the Shepherd (0.78 m/u), scale the Colossus root by 0.50 / 0.78 =
    0.64.
- **Main drives.** `Engine 1-6 exhaust anchor` sit at the six drive mouths:
  1-3 on the port pod, 4-6 on the starboard pod. Local +X points down the
  exhaust (aft), and each is marked `running`.
- **Lift thrusters.** `Engine 7-16 exhaust anchor` are the ten ventral lift
  thrusters (geometry.json lift thrusters 1-10), local +X pointing down.
  - 7 and 8 are the hold's two big bells; 9-16 are the side modules' eight
    small ones.
  - They are declared as drives so the kit gives each its own plume and its
    own throttle.
- **Turrets.**
  - `Turret 1-4 pivot` (WS 001-004, twin light autocannon) each turn about
    their local Z. The rest pose is the slot angle: 1 and 2 trained to port,
    3 and 4 to starboard, with arcs of 130-140 degrees.
  - The shield, mantlet, barrels and roof fittings ride the pivot; the
    barbette, base ring and feed trunk stay.
  - `Anchor turret <i> port|starboard gun muzzle` ride the barrels, +X along
    the line of fire. Port is the barrel left of the line of fire, the kit's
    twin-mount tag.
- **Flare launchers.** WS 005-008 are fixed. `Anchor launcher <i> flare muzzle`
  sits on each muzzle plate, +X along the tube rack. The kit has no flare
  effect, so a shot supplies one there.
- **Lights** are the ship's own warm-white emissive lamps:
  - the bridge masthead, yard and frame lamps;
  - the stern lamps, the aft band edge lamps and the hatch edge lamps;
  - the lit bridge glazing.

  Nothing adds a light object unless the effects collection is appended.
- **Effects (optional).** `SWFX` comes from the shared kit
  (`../second-wind-vfx/`, `swfx.apply(state='clean', hull=<this folder>)` with
  this folder's `vfx_hull.py` profile). It provides:
  - LOW_TECH orange plumes, cores and bell glow on all sixteen drives;
  - exhaust haze;
  - muzzle effects on the four turrets' eight barrels;
  - the masthead, yard, stern and hatch lamps as lights.

  Everything is animatable from `SWFX controls`:
  - `thr_throttle` is the master throttle;
  - `thr_throttle_e1`..`e6` are the main drives;
  - `thr_throttle_e7`..`e16` are the lift thrusters.

  A cruising shot sets e7-e16 to 0; a lift-off runs them. Append `SWFX` in the
  same load as the ship.
- `manifest.json` holds the names, rest bounds, every anchor above, the lamps,
  an effects summary and source hashes.

The meshes are baked (bevels applied), and every mesh carries `hull_rest`
(rest position, hull units) and `hull_normal` (face normal, hull space).

## Build

`./finalize.sh` replays everything:
1. the geometry (rest pose) and the skin, with service wear
   (`../second-wind-surface/apply_skin.py --hull ../colossus-surface --wear
   service`; no Codex calls, because the part sources are the committed
   generations);
2. `export_ship.py`, `export_effects.py` and `verify_ship.py`;
3. `review_ship.py`:
   - whole-ship stills, a turret at rest and trained, and the drive end;
   - cruise (`--cruise`: main drives only) and lift-off (`--effects`);
   - studio off;
   - `--turn 96` adds a 96-frame turn (opt-in, because it is most of the run
     time);
4. `compose_review.py`.

`--skip-surface` reuses the current skin. Renders go to
`../colossus-surface/build/final/`, and the sheets and turn go to `review/`;
both are ignored.

## Checks

`verify_ship.py` appends `COLOSSUS` into a factory-empty scene and checks:
- everything is under the root, nothing is linked, and every image is packed;
- object and polygon counts match the manifest;
- every mesh has its hull attributes, and `hull_rest` is the rest position;
- there are six running drive anchors pointing aft and ten running lift
  anchors pointing down;
- turning each turret pivot 30 degrees swings both its muzzles 30 degrees and
  leaves its barbette fixed;
- each flare launcher muzzle points along its rack;
- moving the root moves the ship rigidly.

## Known limits

- The cargo hatch leaves, the bow cargo door and the ventral docking clamps
  are modelled closed and do not move. A shot that opens them needs hinge
  empties added in the geometry first, as pass 3 does for moving parts.
- The kit has no flare-launch effect; the launcher anchors are only places.
