# Second Wind VFX and lighting kit

Status: ACTIVE — reusable cinematic effects for every condition state of the ship.
Written: 2026-10-16

A Blender kit that appends onto any build of the Second Wind
(`../second-wind-geometry/build_reference.py`, any `--state`). It adds
thrusters, smoke and atmosphere, gun effects, the ship's own lamps, and
lighting rigs. The effects are state-aware. They are procedural and
render-time: volume shaders in container boxes, emissive meshes, and drivers
on `frame` with seeded noise. There are no simulation caches, so the same
file always re-renders the same frames. The kit never edits the geometry
package. It finds what it needs from the stable object names that
`second-wind-pipeline.md` guarantees.

## Use

From a shot, with the ship already in the open file:

```python
import sys; sys.path.insert(0, r'<repo>/art-source/company-prologue/second-wind-vfx')
import swfx, swfx_thrusters, swfx_guns, swfx_smoke
ctx = swfx.apply(state='patched', preset='jangala-approach')
swfx_thrusters.ignition(ctx, 1, ramp_frames=18)
swfx_guns.fire(ctx, 4, 30, shots=12, interval_frames=3)      # the twin alternates
ctx.set('smk_atmosphere', 1)                                  # or ctx.key(name, frame, value)
```

`swfx.apply` clears and rebuilds only the kit's own objects (everything named
`SWFX ...` under the `SWFX` collection), so you can safely call it again. It
ends by re-validating every driver; see "Driver gotcha" below.

From the command line (Blender 5.2 LTS):

```
blender --background --factory-startup --python build_vfx.py -- --state patched --preset deep-space --save shot.blend
blender --background --factory-startup --python build_vfx.py -- --state patched --anchors     # print the anchor table
blender --background --factory-startup --python build_vfx.py -- --state patched --groups "" --save build/_ships/patched.blend   # cache a bare ship
blender --background --factory-startup --python check_kit.py -- --blend build/_ships/patched.blend --state patched
blender --background --factory-startup --python review_kit.py -- --shot approach
```

Without `--blend`, `build_vfx.py` builds the ship from source first (about
60 s) using that state's own geometry build. `--review <group>` renders that
group's review into `build/<group>/`.

## Files

| File | Owns |
|---|---|
| `swfx.py` | `apply()` and `clear()`, the one call a shot makes |
| `swfx_core.py` | the state table, `Context` (knobs, drivers, collections), scale (`METRES_PER_UNIT`, `units()`), builders, render setup |
| `swfx_hull.py` | which hull the kit dresses: the Second Wind, or another hull's `vfx_hull.py` profile |
| `swfx_anchors.py` | finds the effect anchors on the built ship |
| `swfx_thrusters.py` | drive plumes, cores, bell glow, engine glow lights, RCS puffs |
| `swfx_smoke.py` | exhaust haze, landing dust, seal-leak wisps, weld smoke and sparks, conduit sparks |
| `swfx_guns.py` | muzzle flash, flash light, tracers, muzzle puffs, brass |
| `swfx_lighting.py` | ship lamps per state, and the four rig presets |
| `build_vfx.py` | CLI: build the ship, apply the kit, render group reviews, save |
| `check_kit.py` | integration check for one state (see Verification) |
| `review_kit.py` | integrated beauty review, all groups together under the presets |

## Other hulls

The kit defaults to the Second Wind, and its output there is byte-identical
(the same objects, transforms, drivers and knobs). Another hull reaches it in
one of two ways (both under Anchors, Other hulls, below):

- **Declared anchors, no profile.** The kit falls back to the hull's own
  declared engine and muzzle empties, and reads the scale off the export's
  root. This is enough for a few drives and guns: `hound-export/` is the
  worked case.
- **A hull profile.** A folder holding `vfx_hull.py`, chosen by `SWFX_HULL`
  or `swfx.apply(hull=<folder>)`; `hull=''` clears it. `swfx_hull.py` lists
  what a profile may set:
  - its geometry.json (scale), the engine table, the bell radii and the
    port/starboard split;
  - the condition states and the turret mounts (`DECLARED_MUZZLES` takes the
    export's `Anchor turret <i> <tag> muzzle` empties);
  - its lamp list, so `lighting` works;
  - plume colours per engine mode;
  - the art-placed RCS quads.

  Whatever a profile leaves out keeps the Second Wind's value. The
  Mudskipper's profile is `../mudskipper-cinematic/vfx_hull.py`: three orange
  LOW_TECH drives, her two maneuvering jets as geometry RCS anchors, a twin MG
  and her lamps. Engine indices in neither side list read as `centre`.

## Scale

The ship has one real-world scale, `metres_per_unit` in
`../second-wind-geometry/geometry.json` (0.65 m per hull unit at writing,
about 66 m long; see `second-wind-pipeline.md`, Scale). `swfx_core` reads it
into `METRES_PER_UNIT`, and `units(metres)` converts metres to hull units.
Effects tied to the ship are sized in hull units or in multiples of the bell
radius. Anything physical goes through `units()`: RCS nozzles and puffs, spark
and brass speeds, gravity, muzzle velocity, tracer range, gantry heights,
haze per metre, and lamp watts (`swfx_lighting.watts`, because Blender's
light falloff treats a scene unit as a metre). Nothing hard-codes metres, so
a rescale is one edit to `geometry.json` and a re-render.

An exported asset carries its own scale: `swfx.apply` reads `metres_per_unit`
off the asset's root empty (`<ASSET> | <state> | root`) when one is in the
scene. For the Second Wind's exports that is the same 0.65; for another hull
(the Hound's 0.88) it is that hull's.

## Condition states

`swfx_core.STATES` decides what each state allows. The campaign decides which
state a scene shows.

| State | Engines 1-2 (port pod) | Engines 3-4 (starboard pod) | Lamps | Guns | Seal leaks |
|---|---|---|---|---|---|
| `derelict` | dead | dead | all dark | none (stripped) | none |
| `patched` (arrival) | capped, no plume ever | salvaged drive coil | on | fitted | yes, minor |
| `refit` | capped | salvaged | on | fitted | none (the gaskets were replaced) |
| `restored` | clean | clean | on | fitted | none |
| `clean` (reference only) | clean | clean | on | fitted | none |

Canon (`../second-wind-skins/README.txt`): at arrival there is no active fire
and no catastrophic venting. Nothing in the kit makes fire. Seal leaks
default to a faint, intermittent wisp (`smk_seal_wisp` 0.3).

## Anchors

`swfx_anchors.find()` makes empties parented to the ship part they belong to.
Every effect is parented to an anchor, so effects follow the model however a
shot moves it. Use `swfx_anchors.make_ship_root()` to parent the whole ship
to one empty. Effects also follow geometry edits.

Each anchor's local +X is the effect axis and local +Z is as close to dorsal
as possible.

| Anchor | Found from | Position, axis |
|---|---|---|
| `SWFX anchor engine {i}` | `Engine {i} exhaust anchor` if the geometry declares one, else `Engine {i} mouth lip` and `Engine {i} dark throat` | mouth plane; +X runs out of the mouth (aft). Radius comes from the anchor's `radius_m` (via `units()`), else `sw_engines.BELL_R`: 3.9 for the outer pair, 2.6 for the inner pair |
| RCS puffs | `RCS <cluster> <axis> anchor` empties (skipped when `working` is false) | see Thrusters |
| `SWFX anchor turret {i} {tag}` | `Turret {i} {tag} muzzle brake` | brake mouth; +X along the barrel at the slot's resting angle. `tag` is `gun`, or `port gun` / `starboard gun` on the medium twin turret 4 |
| `SWFX anchor lamp <part>` | lamp parts by name (`LAMPS` in the module) | lamp centre; the role is running-port/starboard (by side), masthead, beacon, work, davit or drive-warning |
| `SWFX anchor spark <id> <label>` | `Scar <id> spark anchor`, `Scar <id> spark-2 anchor`, ... (the damage layer's `d.anchor`) | the broken end of a live run; +X is the spray direction. Spray size comes from the anchor's `radius_m` (via `units()`, 0.5 m if absent); `sparking` is that state's flag |

Every scar spark anchor is found and recorded (`ctx.records['anchors']['sparks']`)
whether or not it is sparking, so its id persists across states. The
geometry's anchor empties are unparented; `make_ship_root()` takes them along
with the ship.

Engines are numbered 1 port outer, 2 port inner, 3 starboard inner, 4
starboard outer (`sw_engines.ENGINES` order; +Y is port). The tables only
supply sizes, and serve as a fallback when a part is missing. A part that
doesn't exist yields no anchor, and its group builds nothing for it.

**Other hulls.** When the file holds no Second Wind engine (`Engine 1 exhaust
anchor` or `Engine 1 mouth lip`), the kit takes the hull's own declared engine
empties instead, `Engine <i> <side> exhaust anchor` (radius from `radius_m`,
else `bell_radius`; side from which side of the centreline it sits). When no
Second Wind turret brake is found, it takes declared muzzles, `Anchor weapon
WS 00<i> muzzle` (+X the line of fire, `scale` on the empty). The state's
engine modes and switches still come from `STATES`; a hull with no RCS quads or
landing dust passes `rcs=False, dust=False`, and the lamp table is the Second
Wind's unless a hull profile (Other hulls, above) supplies one, so a hull
without a profile leaves out `lighting`. `hound-export/export_hound.py`
is the worked case. A Second Wind file never reaches either path: its records
are byte-identical with and without them.

## Controls

Every knob is an animatable custom property on the `SWFX controls` empty.
Create one with `ctx.prop`, set it with `ctx.set`, and keyframe it with
`ctx.key`. Group prefixes are `thr_`, `smk_`, `gun_` and `lit_`.

### Thrusters (`swfx_thrusters`)

Each running engine gets these, all scaled by bell radius:

- A short, pale blue-white **core** volume at the mouth. It holds full size
  down to 25% throttle, then shrinks to 0.3 at idle; it is the last part to
  shrink.
- A transparent **plume** of low density and mostly emission. It widens from
  0.78r to about 2r, and its 4D noise is advected aft. Its length scales the
  box along +X from the mouth, so the nozzle never moves.
- An emissive **bell-glow** disc inside the mouth.
- A point **glow light** that lights the can face and the pod.

The two modes:

- **Salvaged** (engines 3-4, patched/refit): a seeded flicker of about 3-4%,
  with brief dips every ~40 frames. Its colour is warmer and less even, and it
  carries grey-brown particulate and soot puffs.
- **Clean**: steady, even blue-white.

| Knob | Range | Default | Meaning |
|---|---|---|---|
| `thr_throttle`, `thr_throttle_e1`..`e4` | 0-1 | 1 | Master and per-engine throttle. Effective throttle = master × engine. Shared with the haze. |
| `thr_instability` | 0-1 | 0.5 | Salvaged flicker and dip depth |
| `thr_plume_length` | 0-3 | 1 | Plume length multiplier |
| `thr_brightness` | 0-4 | 1 | Core/plume emission (raise to ~2 in daylight) |
| `thr_glow` | 0-4 | 1 | Glow light energy |
| `thr_rcs_<quad>` | 0-1 | 0 | Puff age, keyed by `rcs_puff` |
| `thr_rcs_gain` | 0-20 | 1 | RCS puff density/self-light. A real cold-gas puff in a shadowed quarter barely shows, so the review's RCS movie uses 6. |

Helpers:

- `set_throttle(ctx, frame, value, engines=None)`
- `ignition(ctx, start, ramp_frames=18)`
- `shutdown(ctx, start, ramp_frames=12)`
- `rcs_puff(ctx, quad, frame, duration=8)`
- `rcs_pulses(ctx, quads, start, end, pulse_frames=6, gap_frames=5)`: a
  staggered train of puffs for a sustained bank or heading correction.

When the geometry build declares RCS mouths, the kit uses those:

- They are empties named `RCS <cluster> <axis> anchor`, with local +X along
  the exhaust.
- Each one gets a puff, keyed by its label lowercased with `_` separators
  (`RCS Bow port Z anchor` gives `bow_port_z` and the knob `thr_rcs_bow_port_z`).
- The naming was agreed with the damage-states session.

Otherwise the kit places its own quads: `bow_port`, `bow_starboard`,
`stern_port`, `stern_starboard`, `dorsal_fore`, `dorsal_aft`, `ventral_fore`
and `ventral_aft`. Those are art placement, not canon, each snapped to the
nearest hull surface with a small nozzle stub.

`manoeuvre(ctx, move, start, end)` fires the thrusters for a named move
(`MANOEUVRES`). The mapping is the geometry session's:

| Move | Thrusters |
|---|---|
| `roll-starboard-down` (C1's bank) | pod-port down + pod-stbd up |
| `roll-port-down` | the reverse |
| `yaw-starboard` | bow-port port + stern-stbd stbd |
| `yaw-port` | bow-stbd stbd + stern-port port |
| `pitch-up` | ventral down + dorsal up + stern up |
| `pitch-down` | bow up + stern down |

A thruster marked not working in a state is simply left out. Patched has 18
of 24 working; stern-port is missing until restored, so `yaw-port` in
patched/refit is bow-stbd alone.

Either way, a puff is a fast cone that fades as (1-age)³. There are none on
the derelict.

### Smoke and atmosphere (`swfx_smoke`)

- **Exhaust haze**: a soft, mostly non-emissive volume behind each running
  engine. It is grey with soot when salvaged and thin and pale when clean.
  It barely exists in vacuum, and in atmosphere it hangs and bends with the
  wind.
- **Landing dust**: a shallow ring at `smk_ground_z` that follows the hull's
  XY. It rises within 45 units of the ground, scales with throttle, and is a
  damp warm grey-brown. It is authored downwash, because the hull has no
  ventral nozzles.
- **Seal leaks**: 2-4 sites on the patched build's borrowed seal gaskets and
  pressure patches (`Scar <id> borrowed seal gasket`), placed on the rim,
  aimed outward and parented to the part. In vacuum a leak is a short straight
  jet; in atmosphere it is a slow rising wisp.
- **Welds**: `add_weld(ctx, location, normal, start_frame, end_frame,
  part=None, seed=0)` adds a flickering arc light, a spark light, a rising
  wisp, and a geometry-nodes spark shower. The sparks arc under drag and
  gravity in air and fly straight in vacuum. You can also pass
  `build(ctx, welds=[...])`. Neither is added by default.
- **Conduit sparks**: broken live conduit that the patched crew left live
  because nobody could reach it to splice it. One emitter per scar spark
  anchor whose `sparking` is true. By the state convention that is patched
  only: derelict is unpowered, refit isolated the live breaks, and restored
  has none. Anchors that are not sparking get nothing. These are on by
  default; `build(ctx, sparks=False)` (or `swfx.apply(..., sparks=False)`)
  leaves them out, and `add_conduit_sparks(ctx, anchor)` builds one by hand.
  Each emitter has three parts:
  - **bursts** of 3-12 small, bright sparks (the weld spark machinery: the
    same streaks, drag and gravity), white-yellow cooling to orange, living
    5-13 frames. In air they arc down; in vacuum they fly straight. They
    travel roughly `radius_m`;
  - a **flash light** (a 120 W point at the break, flickering) that is lit
    only for the burst's 2-5 frames and gone about 2 frames after;
  - a white-hot **bead** at the break, visible only while it shorts.

  Timing is procedural on `frame`, so there is no keyed horizon and no
  cache. Time is cut into 30-frame cells. Each cell holds at most one burst,
  at a seeded offset in its first 18 frames, and about a quarter of the cells
  stay quiet. Burst to burst is therefore 0.5-2 s, about 1.75-3.2 s across a
  quiet cell, and longer only rarely. The seed is `zlib.crc32` of the
  anchor's name, so anchors never fire in sync and a rebuild repeats exactly.
  The schedule is computed once per frame by simple drivers on the
  emitter's root empty (`swfx_s0/d0/h0` for this cell, `.._1` for the last
  one), and the sparks, the light and the bead all read it, so they cannot
  disagree. `spark_bursts(seed, first, last)` is the Python mirror: it lists
  `(start_frame, frames, sparks)` for choosing shot frames. An emitter's seed
  is in its record and on its root (`swfx_spark_seed`).
  There is no sustained arc, fire or venting.

| Knob | Range | Default | Meaning |
|---|---|---|---|
| `smk_haze` | 0-1 | 1 | Exhaust haze amount |
| `smk_atmosphere` | 0-1 | 1 | 0 = vacuum, 1 = in air |
| `smk_wind_x/y/z` | -1..1 | 0 | Wind in hull axes; 1 bends the haze about 35% of its length |
| `smk_dust` | 0-1 | 1 | Landing dust amount |
| `smk_ground_z` | ±2000 | -1000 | World Z of the ground (-1000 = no ground, no dust) |
| `smk_seal_wisp` | 0-1 | 0.3 | Seal-leak strength; keep it low (canon) |
| `smk_weld` | 0-1 | 1 | Weld intensity (made by `add_weld`) |
| `smk_sparks` | 0-1 | 1 | Conduit spark intensity: spark brightness, flash light and bead. 0 = off |

### Guns (`swfx_guns`)

**Per gun** (t1, t2, t3, and t4 port/starboard):

- **Flash volume**: a hot core, a forward tongue, and two side jets out of
  the brake's lateral port. Its shape is seeded per shot and its colour is
  blackbody, so the decay frames cool to orange. It is hidden except on its
  1-3 flash frames.
- **Flash light**: a warm point light driven by the same envelope.

**Per shot**, made by `fire()`:

- **Tracer**: a travelling additive rod that motion-blurs properly. By
  default every second round is a tracer.
- **Puff**: a soft muzzle puff that grows and fades. In vacuum it is thin and
  short; in air it is denser and rises.
- **Brass**: ejected from the gun-house side wall at barrel height, tumbling
  on a ballistic path. Small mounts eject to the right; the twin ejects
  outboard. Gravity applies only in atmosphere, because in orbit the ship and
  the brass fall together.

Every shot is deterministic.

| Knob | Range | Default | Meaning |
|---|---|---|---|
| `gun_flash_t{i}_{k}`, `gun_seed_t{i}_{k}` | | keyed by `fire()` | Per-gun flash envelope and shape seed. k=2 is the twin's starboard gun. |
| `gun_flash_gain` | 0-4 | 1 | Flash brightness |
| `gun_flash_light` | 0-20000 | 2500 | Flash light watts at envelope 1 |
| `gun_smoke_gain` | 0-4 | 1 | Puff density |
| `gun_tracer_gain` | 0-4 | 1 | Tracer brightness |
| `gun_atmosphere` | 0-1 | 0 | Vacuum vs air (puff behaviour, brass gravity) |
| `gun_tracer_every` | 1-10 | 2 | One tracer per N rounds |
| `gun_muzzle_velocity` | 50-1500 m/s | 400 | Tracer speed. Cinematic: a true ~1000 m/s crosses the ship in one frame. |
| `gun_tracer_range` | 30-3000 m | 280 | Tracer burnout distance |
| `gun_gravity` | 0-30 m/s² | 9.81 | Brass gravity × `gun_atmosphere` |
| `gun_casings` | 0-1 | 1 | Casings on at ≥ 0.5 |

Some knobs are live and some are read once:

- **Live:** the gains, and `gun_atmosphere`'s effect on puff density.
- **Read when `fire()` runs:** velocity, range, gravity, every-N and casings.
  Set these before firing.

Helpers:

- `fire(ctx, turret, start_frame, shots, interval_frames, tag=None)`. The
  twin alternates port first. `tag` can be `'port gun'`, `'starboard gun'`
  or `'both'` (a salvo).
- `burst(ctx, turrets=(1,2,3,4), start_frame, shots, interval_frames,
  stagger_frames, seed)`.
- `build(ctx, fire=[{...}])` fires during the build. The derelict carries no
  guns, so nothing is built there.

### Lighting (`swfx_lighting`)

**Ship lamps.** Each lamp anchor gets a real light, hidden from camera so you
see the lens mesh:

| Lamp | Light |
|---|---|
| running lights | red/green points |
| masthead | point |
| roof beacon | amber point, blinking deterministically on `frame` |
| grapple and bow work floods | amber 75° spots aimed outboard and down |
| davit | green point |
| drive warning | red points |

The geometry's lens materials are driven by the same knobs. Lamps the damage
layer marked `Dead lamp` get no light: the patched port pod's warning lamps
and everything on the derelict. Engine glow belongs to the thrusters.

**Presets** (`PRESETS`, `apply_preset(ctx, name)`, or
`swfx.apply(preset=...)`): each preset owns `SWFX | Lighting rig` and a fresh
world, and replaces the last one. Azimuth is measured in world XY from +X
(the bow) toward +Y (port).

| Preset | Look |
|---|---|
| `deep-space` | A warm star key (sun, 0.6°, elevation 30°, azimuth 100°) and a faint cool rim. Near-black ambient and a sparse procedural starfield, seen only by camera rays. A visible star disc with glare. |
| `jangala-approach` | A physical sky with its sun disc off, so the lamp is the only sun: elevation 55°, azimuth 30°, high from camera-left and slightly behind, as in the approved realism references. A canopy-green bounce plane hidden from camera, and light forward-scattering haze. |
| `jangala-landing` | Lower sun (30°), more aerosol, ground bounce close below, denser haze. |
| `night-berth` | Blue-grey night with a faint sodium horizon. Six gantry floods placed from the ship bounds, blending LED to sodium. Two floor uplights, a concrete floor (optional) and an optional fog box. |

| Knob | Meaning |
|---|---|
| `lit_ship_master`; `lit_running`, `lit_masthead`, `lit_beacon`, `lit_work`, `lit_davit`, `lit_drive_warning` (0-4, default 1) | Lamp levels: the master, then each group |
| `lit_beacon_period` (6-240 frames, default 36) | Beacon flash period |
| `lit_exposure` | View exposure |
| `lit_sun`, `lit_sun_elevation`, `lit_sun_azimuth`, `lit_sky`, `lit_rim` | Sun, sky and rim, per preset |
| `lit_haze`, `lit_fog` (per metre) | Air haze, berth fog |
| `lit_stars`, `lit_star_glare` | Deep-space starfield and star |
| `lit_berth`, `lit_berth_warmth`, `lit_floor_lamps` | Berth floods, their LED-to-sodium colour, and the floor lamps |

Applying a preset resets its knobs to that preset's defaults, unless a knob
is already keyframed.

## Render engine and cost

Everything is rendered in **Cycles** (GPU, OptiX on the RTX 3080), with the
denoiser and AgX. This is because:

- the effects are volumes that emit and scatter, lit by point lights and
  spots inside them;
- the ship's geometry review renders are Cycles too, so the look matches the
  reference stills.

EEVEE would be faster but approximates volumes in froxels, and was not
measured for these effects. Use `use_persistent_data` (set by
`swfx_core.setup_render`) for animations.

Timings were measured with the GPU shared by several other Blender jobs, so
an uncontended machine is faster:

| Render | Samples | Time |
|---|---|---|
| Stills | 48-96 | ~1-4 min each |
| Thruster animation | 28 (volume step rate 4) | ~20-25 s a frame |
| Gun animation | 48 | ~6 s a frame uncontended; 30-60 s contended |
| Landing-dust animation | 24 | ~28 s a frame |
| Integrated action movie | 32 | about 15-25 s a frame |

Volume containers are hidden or zero-density when idle, so effects that are
off cost almost nothing.

## Limits

- These are authored effects, not fluid, thermal or heat-refraction
  simulation, and there is no compositor glare.
- Thrusters are tuned for a dark backdrop. Raise `thr_brightness` in
  daylight; the plume is not attenuated by air.
- The salvaged warmth and soot are deliberately subtle at cinematic
  distance.
- Landing dust:
  - It does not linger after the ship leaves (key `smk_dust` instead).
  - Its offset does not rotate with ship yaw.
  - Its container does not cast a shadow, by design.
  - In the smoke review's landing frames, a faint darker rectangle on the
    ground plane beside the ship remains. It is not the dust container (still
    there with its shadow off), and its cause has not been traced; check it
    before using that ground setup in a shot.
- The haze wind and weld gravity are fixed in hull/world axes at build time.
  Re-apply after rotating the ship a long way.
- Shot debris (tracers, puffs, brass) rides the gun anchor. If a turret is
  trained mid-burst, its debris swings with it. There is no barrel recoil,
  because the barrels belong to the geometry.
- Brass is too small to read in wide shots. At ship-wide framing, gunfire
  needs more than the close-up defaults. `review_kit.py`'s action shot uses:
  - `gun_flash_gain` 2.5 and `gun_flash_light` 6000;
  - `gun_tracer_gain` 2.5 and `gun_tracer_every` 1;
  - `gun_muzzle_velocity` 220, which keeps rounds in frame for a few frames.
- The Jangala presets have a clear, cloudless sky and no visible ground. They
  are lighting, not environment plates, and read a little cool and grey next
  to the approved references. Shots composite the real plates.
- The patched build's port bells are open rather than visibly capped. That
  belongs to the damage layer; the kit simply never lights them.
- The appendable per-state cinematic export collection
  (`second-wind-pipeline.md`, Cinematic output) is a separate step.

## Driver gotcha (fixed in core)

A knob added to `SWFX controls` after the depsgraph last evaluated it is
missing from the evaluated copy. Any driver reading it then reads 0 and stays
flagged invalid. All four groups hit this independently. The fix:

- `ctx.prop` and `ctx.set` re-tag the controls;
- `swfx.apply` finishes with `swfx_core.refresh_drivers`.

Keep driver expressions short and simple: under Blender's 256-character cap,
`pow()` rather than `**`, and no Python-only names. That way they run with
auto-run off. `check_kit.py` fails on any invalid or non-simple driver.

## Verification

`check_kit.py` builds the whole kit on one state's ship and checks:

- that a second apply gives identical counts;
- that plumes exist exactly for the running engines;
- that seal leaks appear only where the state allows them;
- that there is exactly one conduit-spark emitter per scar spark anchor
  whose `sparking` is true;
- that every driver is valid and a simple expression.

At writing it passes on `derelict`, `patched`, `refit` and `restored`:

| State | Drivers | Plumes | Seal leaks |
|---|---|---|---|
| `derelict` | 9 | none | 0 |
| `patched` | 227 | engines 3-4 | 3 |
| `refit` | 205 | engines 3-4 | 0 |
| `restored` | 286 | all four | 0 |

## Review media

These are owner-facing, all 1920x1080 at 100%, in `review/`. Raw frames are
kept under the ignored `build/`.

- `review/kit/`, every group together:
  - `kit-patched-jangala-approach.png`: arrival, the starboard pod only, with
    its haze in the air;
  - `kit-restored-deep-space.png`: all four clean;
  - `kit-derelict-deep-space.png`: cold;
  - `kit-patched-night-berth.png`: drives off, lamps and floods on, faint
    seal steam;
  - `kit-patched-deep-space-action.mp4`: under way on the coil, turrets 2 and
    4 firing, a stern RCS correction (3 s);
  - `kit-patched-rcs-bank-and-yaw.mp4`: C1's starboard-down bank, then the
    bow-to-starboard correction, on the geometry's RCS thrusters (2.5 s,
    `thr_rcs_gain` 6). The bank's pod-stbd up puffs read clearly. The stern
    yaw puffs face the camera in the pod's shadow and barely show; frame a
    yaw shot from aft.
  - `kit-patched-sparks-burst.png` and `kit-patched-sparks-quiet.png`
    (`review_kit.py --shot sparks`): the first live scar spark anchor during
    its biggest burst in 10 s and in a quiet frame, in air, with the key
    dimmed. The camera is placed from the anchor. The shot needs a patched
    ship built with spark anchors.
- `review/thrusters/`:
  - patched and restored stern quarters;
  - starboard close-up;
  - throttle ramp movie (ignition, full, idle);
  - RCS puff still and movie.
- `review/smoke/`:
  - haze in air and in vacuum;
  - landing-dust movie and its approach/contact/settling stills;
  - seal leak in air and in vacuum;
  - weld sparks in air and in vacuum.
- `review/guns/`:
  - single flash close-up;
  - twin alternating burst movie;
  - all-turret movie.
- `review/lighting/`:
  - the patched ship under each preset;
  - night grapple close-up;
  - derelict in deep space;
  - night-berth beacon movie.

The group reviews were rendered by each module's `review()` with its own
simple review lighting, on the geometry from before the detailed drives and
RCS thrusters landed. The art-placed RCS puffs in `review/thrusters/` are
that older fallback. The `kit` set is the integrated pass on the current
geometry, with the drive detail and the geometry's own RCS anchors.
