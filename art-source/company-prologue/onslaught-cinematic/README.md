# Onslaught (XIV): cinematic asset

Status: DONE (pass 7 accepted by the owner 2026-10-19)
Written: 2026-10-19

Source art for cinematics; nothing here ships in the mod. This folder makes
the textured Onslaught (`../onslaught-surface/`) one appendable Blender file:
the ship as one collection, plus an optional effects collection from the
shared VFX kit (`../second-wind-vfx/`). The Onslaught is the C1 v2 Hegemony
picket flagship and is never shown damaged, so there is one condition,
`clean`, with every drive running. It has no gear, doors or ramps, so there is
no control empty.

## Use

Append the collections, not objects:

```python
with bpy.data.libraries.load(path, link=False) as (src, dst):
    dst.collections = ['ONSLAUGHT | clean']             # or ['ONSLAUGHT | clean', 'SWFX'] for the effects
for c in dst.collections:
    scene.collection.children.link(c)
root = bpy.data.objects['ONSLAUGHT | clean | root']
```

`onslaught.blend` is built, not committed (`finalize.sh`); `manifest.json`
and `validation.json` are tracked. Every image is packed.

- **Move the root only.** Every part and anchor descends from it. The model
  is in hull units (+X bow, +Y port, +Z dorsal; one sprite pixel per unit).
  `metres_per_unit` is 0.86 (in the manifest and on the root), so she is
  302 m long, 230 m across the arms and 116 m deep. A shot in metres scales
  the root by 0.86. Beside the Second Wind (0.65 m/u), scale it by
  0.86 / 0.65 = 1.32.
- **Engines.** `Engine 1-6 exhaust anchor` sit at the six bell mouths, local
  +X down the exhaust (aft), each carrying `exhaust_direction`, `mouth_radius`
  and `slot_width`. In engine-slot order: 1 and 4 the quarter pods (width 20,
  canted 20 degrees outboard), 2 and 3 the blade drives (width 20), 5 and 6
  the mains (width 30). Port (+Y) is 1, 2 and 5; starboard is 3, 4 and 6.
- **Turrets.** 22 turrets, each `Weapon WS nnn pivot`, which trains about its
  local Z (the hull normal under it; rest pose is the slot angle, the pivot
  carries `slot_angle` and `arc`), and `Weapon WS nnn elevation` under it,
  which elevates about its local Y (negative lifts). The turret kit's muzzle
  empties `Weapon WS nnn muzzle anchor <k>` ride the elevation, +X along the
  line of fire.
- **TPC hardpoints.** WS 016 and 017, the two built-in thermal pulse cannons
  at the arm tips, are fixed: `Weapon WS 016|017 muzzle`, +X forward.
- **Kit gun rigs.** For the effects, every ballistic barrel also carries an
  `Anchor turret <rig> <tag> muzzle` child at zero offset (so it trains and
  elevates with its turret). Rig numbers are the kit's, not slot numbers:
  `manifest.json` `gun_rigs` maps each rig to its slot, weapon and barrels.
  A single barrel is one rig (`gun`), a twin one rig (`port gun` /
  `starboard gun`, by the side of the line of fire), a triple two rigs (its
  outer pair as a twin, its centre barrel alone). 26 rigs over 38 barrels,
  the TPCs as rigs 25 and 26. Fire one with
  `swfx_guns.fire(ctx, rig, start_frame, shots, interval_frames)`.
- **Lamps.** The 21 running lights (`Light ...`, emissive lenses in two
  shared materials, red and white), each with `running_light_colour`,
  `blink_period_s`, `blink_phase_s` and `blink_duty`: red at the stem, mast
  head, arm noses, leg tips and stern quarters; white along both arm edges
  and at the stern. Without the effects collection nothing adds a light
  object.
- **Effects (optional).** `SWFX` is the shared kit
  (`swfx.apply(state='clean', hull=<this folder>, rcs=False, dust=False)`
  with this folder's `vfx_hull.py` profile). It provides:
  - LOW_TECH orange plumes, white-hot cores, bell glow and a glow light on all
    six drives, each sized to its bell (radius 4.6 or 7.0 units);
  - exhaust haze behind each drive. The asset's default is vacuum
    (`smk_atmosphere` 0), where the kit's haze is faint by design; raise it
    for a shot that wants the drives to read as smoky;
  - flash volumes and flash lights on all 26 gun rigs (hidden until
    `swfx_guns.fire` keys them);
  - the running lights as real lights: the red ones as the kit's blinking
    beacon (`lit_beacon_period` 48 frames, the build's 2 s blink), the white
    ones steady.

  Everything is animatable from `SWFX controls` (`thr_throttle_e1`..`e6`,
  `thr_plume_length`, `thr_brightness`, `lit_*`, `gun_*`; see
  `../second-wind-vfx/README.md`). Append it in the same load as the ship.
- `manifest.json` holds the names, bounds, engines, weapon slots, gun rigs,
  running lights, lamps, an effects summary and source hashes.

The meshes are baked (bevels applied), so the asset costs nothing to
evaluate when a shot moves it.

## Profile (`vfx_hull.py`)

Without a profile the kit would take the Onslaught for the Second Wind: its
engine empties carry the Second Wind's own names, so the kit would dress four
of them with the Second Wind's bell radii. The profile sets:
- `GEOMETRY_JSON` (the scale), all six `ENGINES` with `BELL_R` from the
  geometry's mouth radii, and the port/starboard split by slot position;
- one state, `clean`: every drive running, powered, guns live, no seal wisps;
- `COLOURS`: LOW_TECH orange (vanilla 255,125,25), the Shepherd's palette;
  the Legion, the other Hegemony capital, declares the same style;
- `MOUNTS` and `DECLARED_MUZZLES` from `RIGS` (the gun rig table above);
- `LAMPS`, one entry per running light with its own colour;
- `RCS_QUADS` empty: the Onslaught has no maneuvering jets.

## Build

`./finalize.sh` replays everything from `../onslaught-surface/build/skinned.blend`
(run `../onslaught-surface/rebuild.sh` first if it is missing):
1. `../onslaught-geometry/export_onslaught.py` on the textured build, into
   `build/export/` (the one collection, images packed);
2. `export_effects.py`: the kit gun anchors and the `SWFX` collection, saved
   as `onslaught.blend` with `manifest.json`;
3. `verify_ship.py` -> `validation.json`;
4. `review_ship.py` (Cycles, 32 samples, GPU when there is one) and
   `compose_review.py`: `review/whole.png` (beauty three-quarter and stern
   quarter, without and with effects), `review/effects.png` (the six drives
   close, studio on and off; the stern quarter with lamps and drive glow
   only; a 2x crop of the formation frame), `review/formation-1.5km.png` (the
   C1 v2 frame: 24 mm, 1.5 km, one hard sun, no fill, with effects),
   `review/turn.mp4` and `review/turn-sheet.png` (72 frames, quarter
   elevation, with effects).

`--turn N` changes the turn length (0 skips it), `--samples S` the samples,
`--no-review` stops after verification. Renders go to `build/review/`; the
sheets and movie to `review/`; both are ignored.

## Checks

`verify_ship.py` appends `ONSLAUGHT | clean` into a factory-empty scene and
checks:
- everything is under the root, nothing is linked, every image is packed;
  mesh and polygon counts match the manifest; the root's `metres_per_unit`
  equals geometry.json's;
- the six engine anchors sit at their slot mouths and point aft;
- every one of the 22 turrets trains 30 degrees about its pivot's local Z
  (muzzles swing, barbette stays) and elevates;
- the TPC muzzles point forward; every kit gun anchor rides its turret muzzle;
- the 21 running lights carry their blink data;
- moving the root moves the ship rigidly.

It then appends the ship and `SWFX` together and checks that each of the six
drives has a plume, core, haze and glow light, each plume starting at its
mouth along its exhaust direction and sized to its bell; 38 gun anchors and
21 lamp lights; every driver valid; and that moving the root carries the
effects.

## Left out

- No RCS puffs (the hull has no maneuvering jets) and no landing dust (she
  never lands).
- The four Typhoon missile racks (WS 021-024) have no rig: the kit has gun
  effects only, no missile launch or contrail.
- No lighting-rig preset is baked in: lighting is the shot's.
- No damaged or other condition states.

## Known issues

- The kit has one beacon clock, so every red light blinks together at phase
  0, not at its own `blink_phase_s`; the white lights are steady. The lenses
  stay lit (the kit drives only its own lamp materials), so a blink shows as
  the light on the hull, not the lens.
- Gun flashes are sized by rig (`SIZE_SCALE` by slot size, the TPC at 2.4)
  against the Second Wind's small mount; they were not reviewed firing.
- At 1.5 km on the formation camera the plumes are a few pixels, and from
  the bow quarter the hull hides most of them.
