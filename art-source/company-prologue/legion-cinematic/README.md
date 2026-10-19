# Legion (XIV): cinematic asset

Status: REVIEW (pass 7 with the owner, 2026-10-19). Source art for cinematics; nothing here ships in the mod.
Written: 2026-10-19

The textured Legion (XIV) battlecarrier (`../legion-surface/`, texture pass 2)
as one appendable Blender collection. It carries its moving parts (fifteen
trainable, elevating turrets and every keyable hangar part), the anchors a
shot needs, and an optional effects collection from the shared VFX kit. The
patrol is damage-free, so there is one condition, clean, and one asset. The
Shepherd's and Hermes's exports (`../shepherd-cinematic/`,
`../hermes-cinematic/`) are the pattern.

## Use

Append the collection, not objects:

```python
with bpy.data.libraries.load(path, link=False) as (src, dst):
    dst.collections = ['LEGION']                  # or ['LEGION', 'SWFX'] for the effects
scene.collection.children.link(dst.collections[0])
root = bpy.data.objects['LEGION | root']
```

`legion.blend` is built, not committed (`finalize.sh`); `manifest.json` and
`validation.json` are tracked.

- **Move the root only.** Every part and anchor descends from it. The model
  is in hull units (+X bow, +Y port, +Z dorsal); `metres_per_unit` is 0.99 (in
  the manifest and on the root), so she is 292 m long. A shot in metres scales
  the root by 0.99. Beside the Second Wind (0.65 m/u), scale the Legion root
  by 0.99 / 0.65 = 1.523.
- **Turrets.** The geometry's own names, numbered 1..15 in `weapon_slots`
  order in the manifest:
  - `Weapon <slot> pivot` trains about its local Z. The rest pose is the slot
    angle. The house rides it; the barbette stays.
  - `Weapon <slot> elevation` elevates about its local Y. Negative lifts the
    muzzles. The guns (or the launcher) ride it.
  - `Anchor turret <i> barrel <k> muzzle` rides each barrel, +X along the line
    of fire: one per barrel, so the heavy autocannons carry three and the
    Vulcans two. The squall (WS 000, turret 1) and hurricane (WS 001, turret 2)
    are missile launchers; their anchors are `Anchor turret <i> tube <k>
    muzzle`, one per tube (five and one). Each anchor carries its effect
    `scale` and `kind`.
- **Engines.** `Engine 1..10 exhaust anchor` sit at the drive mouths in
  `legion.ship` engine order, local +X aft, marked `running`, with
  `radius_m`.
- **Flight decks.** `Flight deck <slot> mouth` and `Flight deck <slot> launch
  point 1..4` for WS 010-013 (aft port and starboard, forward port and
  starboard). A launch point's +X is its deck's launch direction; the manifest
  records the mouths' clear sizes and the recovery approach.
- **Controls.** Custom properties on the `Legion control` empty; keyframe them
  to animate. All 0 is the built pose.
  - `deflector_ws010..ws013` (0..1): the blast deflector behind each station
    rises 50 degrees.
  - `barrier_port|starboard` (0..1): the recovery barrier rises from flat.
  - `transfer_door_<side>_<1..3>` (0..1): the door leaf rises 10.6 units into
    the inboard wall.
  - `traverser_<side>_<1..3>` (-1..1): 0 parked at its door, 1 out at the
    launch points, -1 in at the lock centre.
  - `crane_port|starboard` (-1..1): the gantry crane runs forward (1) or aft
    (-1) along its rails.

  A pose set from Python needs `bpy.data.objects['Legion control'].update_tag()`
  before the frame is evaluated; a keyframed knob needs nothing.
- **Lights** are the ship's own emissive lamps: 27 running lights, red
  obstruction and extremity lights and white deck-edge and flank lights. Each
  lamp carries its blink (`blink_period_s`, `blink_phase_s`, `blink_duty`) from
  `geometry.json`. The lens materials are shared, so a shot that blinks them
  gives each lamp its own copy.
- **Effects (optional).** `SWFX` comes from the shared kit
  (`../second-wind-vfx/`, `swfx.apply(state='clean', hull=<this folder>,
  dust=False)` with this folder's `vfx_hull.py` profile). It provides:
  - LOW_TECH orange plumes, cores and bell glow on the ten drives, and
    exhaust haze, set to vacuum (`smk_atmosphere` 0: in the kit's default
    atmosphere ten drives' haze hangs round the stern as a white cloud);
  - a muzzle rig on each of the 25 gun barrels (`gun_flash_t<i>_<k>`);
  - a launch rig on each of the six launcher tubes (`gun_launch_t<i>_<k>`):
    `fire()` on a launcher launches missiles (a launch bloom with backblast,
    then a missile accelerating on its motor with a smoke trail), never a
    muzzle flash, tracer or brass;
  - the running lights as lights, red on `lit_running`, white on
    `lit_masthead`.

  Everything is animatable from `SWFX controls`. Append it in the same load as
  the ship. To fire, a shot rebuilds the kit on the appended ship and calls
  `swfx.module('guns').fire(ctx, turret, start_frame, shots, interval)`
  (`review_ship.py --fire` is the worked case).
- `manifest.json` holds:
  - the names, rest bounds, and the engine, turret (pivot, elevation,
    muzzles, kind, effect scale) and flight-deck anchors;
  - the control record (knobs and the part each drives, with its travel);
  - the lamps with their blink, an effects summary and source hashes.

The meshes are baked (bevels applied) and single-user, and every mesh carries
`hull_rest` (rest position, hull units) and `hull_normal` (face normal, hull
space).

## Build

`./finalize.sh` replays everything, given the graded textures that
`../legion-surface/rebuild.sh` makes from the committed Codex sheets:
1. the geometry (rest pose), cut along the tile lines into the surfacing
   build (`../legion-surface/make_groups.py`), and the skin with light
   service wear (`../second-wind-surface/apply_skin.py --hull
   ../legion-surface --wear service`; no Codex calls);
2. `export_ship.py`, `export_effects.py`, `verify_ship.py` and the kit's own
   `check_kit.py` on the export;
3. `review_ship.py`: the four quarters and four close views, the ops pose
   (every knob set, every turret trained and elevated), effects, a firing pass
   through the kit (at half samples and without the exhaust haze, which
   multiplies the cost of every muzzle puff and smoke trail), studio-off, and
   an opt-in turn (`--turn 96`);
4. `compose_review.py`.

`--skip-surface` reuses the current skin. Renders go to
`../legion-surface/build/final/`, and the sheets go to `review/`; both are
ignored. On Windows `make_groups.py` rewrites `../legion-surface/groups/*.py`
with CRLF line ends; the content is unchanged, and `git checkout` on them
clears the noise.

## Checks

`verify_ship.py` appends `LEGION` into a factory-empty scene and checks:
- everything is under the root, nothing is linked, and every image is packed;
- object and polygon counts match the manifest;
- every mesh has its hull attributes, and `hull_rest` is the rest position;
- there are ten engine anchors pointing aft;
- every turret's muzzles point along its slot angle at rest; turning its
  pivot 30 degrees swings every muzzle 30 degrees and leaves its barbette
  fixed; elevating it 15 degrees lifts every muzzle about the trunnion and
  pitches its line of fire up 15 degrees; launchers carry tubes, guns barrels;
- the flight-deck mouths and launch points are there and aimed;
- every running light carries its blink;
- every knob drives its part by the travel `geometry.json` gives, every driver
  is a simple expression, and all knobs back at 0 restore the rest pose;
- moving the root moves the ship rigidly;
- appended together with `SWFX`, every effect rides the root.

`../second-wind-vfx/check_kit.py --hull . --fire` then checks the kit on the
export: one knob label per barrel, a launch rig and no muzzle flash on every
tube, and missiles (never tracers or brass) from the launchers.

## Known limits

- The running lights do not blink by themselves: the blink is data on each
  lamp for a shot to key.
- The flight decks' elevator lifts are not on the control empty
  (`geometry.json` declares no travel for them).
- The weapon effects are sized from each mount's ring (guns) or missile
  (launchers) against the Second Wind's mount; at the Legion's size a single
  flash reads small in a whole-ship frame.
