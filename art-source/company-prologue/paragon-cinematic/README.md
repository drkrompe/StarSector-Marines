# Paragon: cinematic asset

Status: REVIEW (drives, beams and point defence accepted by the owner 2026-10-19; the Fortress Shield in review, round 2 posted 2026-10-19)
Written: 2026-10-19

Source art for cinematics; nothing here ships in the mod. This folder makes
the textured Paragon (`../paragon-surface/`) one appendable Blender file: the
ship as one collection, plus an optional effects collection from the shared
VFX kit (`../second-wind-vfx/`). One condition, `clean`, with every drive
running and every weapon live. The hangar doors stay as the geometry left
them (their slide empties are in the anchors collection).

## Use

Append the collections, not objects:

```python
with bpy.data.libraries.load(path, link=False) as (src, dst):
    dst.collections = ['PARAGON | clean']               # or ['PARAGON | clean', 'SWFX'] for the effects
for c in dst.collections:
    scene.collection.children.link(c)
root = bpy.data.objects['PARAGON | clean | root']
```

`paragon.blend` is built, not committed (`finalize.sh`); `manifest.json` and
`validation.json` are tracked. Every image is packed.

- **Move the root only.** Every part and anchor descends from it. The model
  is in hull units (+X bow, +Y port, +Z dorsal; one sprite pixel per unit).
  `metres_per_unit` is 0.87 (in the manifest and on the root): 312 m of hull,
  353 m to the tips of the bow lances. A shot in metres scales the root by
  0.87; beside the Onslaught (0.86) it is 0.87 / 0.86.
- **Engines.** `Engine 1-10 exhaust` sit at the ten slot points, local +X
  aft. The effects export adds `Engine <i> exhaust anchor` under each, moved
  forward onto the nozzle face, carrying `radius_m`, `slot_width` and
  `exhaust_direction`. Port (+Y) is 1-5, starboard 6-10.
- **Turrets.** 21 turrets, each `Weapon WS nnn pivot`, which trains about its
  local Z (rest pose is the slot angle), and `Weapon WS nnn elevation` under
  it, which elevates about its local Y (negative lifts) within its
  `elevation_min_deg`/`elevation_max_deg`. The turret kit's
  `Weapon WS nnn muzzle anchor <k>` ride the elevation, +X along the line of
  fire. A graviton emitter sits on its trunnion axis, so elevating it turns
  the beam without moving the emitter.
- **Bow lances.** WS 001 and 002 are fixed hardpoints:
  `Weapon WS 001|002 muzzle anchor 1`, +X forward.
- **Shield.** `Shield emitter` at the Fortress Shield centre and ten emitter
  nodes on the bore lining; the radius is in the manifest's `shield`. The
  effect is in `SWFX` (below).
- **Effects (optional).** `SWFX` is the shared kit (`swfx.apply(state='clean',
  groups=(thrusters, smoke, guns, beams, shield), hull=<this folder>, rcs=False,
  dust=False)`). It provides:
  - HIGH_TECH blue plumes (vanilla 100,165,255), blue-white cores, bell glow
    and a glow light on all ten drives, each sized to its mouth;
  - exhaust haze, at the vacuum default (`smk_atmosphere` 0);
  - the two heavy needlers as kit gun rigs (rig 1 is WS 007, rig 2 WS 008,
    each a twin), hidden until `swfx_guns.fire` keys them;
  - a beam on all 17 energy weapons (two bow tachyon lances, two turret
    lances, four graviton beams, four tactical lasers, five PD lasers) in the
    vanilla colours, widths, ranges and charge timings, dark until fired.
    `swfx_beams.fire_beam(ctx, 'ws003', frame)` keys a shot; `beam_len_<id>`
    is the distance to the target. `manifest.json` `beams` lists every id;
  - the Fortress Shield (`swfx_shield`, dark until raised): a shell fitted to
    the hull (its convex hull pushed out 12 units, about 10 m, and smoothed:
    227 x 167 x 63 half-extents against the game's 270-unit circle, which in
    3D would be a sphere far larger than the flat ship), centred on
    `Shield emitter` in plan, in the vanilla colours (HIGH_TECH ring
    255,255,255 and inner 125,125,255; the fortressshield system's inner
    255,100,255), unfolding from the bow both ways and closing aft as an OMNI
    shield does in the game; four aimable hit flashes; a glow and a light on
    each of the ten emitter nodes, which light first. `shield_t` raises it,
    `shield_fortress` blends to the Fortress Shield: the shell goes nearly
    clear and steady, and the magenta lives in the hits, which spread wider
    and darken what lies behind them; the nodes burn hotter.
    `shield_facing` turns where it unfolds from.
    `swfx_shield.raise_shield(ctx, frame, hold_s)`, `fortress(ctx, frame,
    hold_s)` (the system's 1.5 s charge up and down) and `hit(ctx, k, frame,
    azimuth, elevation)` key them; `manifest.json` `shield_fx` lists the
    parts. In the game a ship cannot fire while the Fortress Shield is on.
- **Aiming (`aim.py`).** `aim.engage(man, target)` trains every turret whose
  vanilla arc holds the target and returns which beams may fire, their
  lengths, and why the rest hold: outside the arc, unable to lay on it within
  the elevation limits, or a line from the muzzle to the target that crosses
  the ship (`swfx_beams.clear_line`). A beam never cuts the hull. The five PD
  lasers (vanilla's only PD-hinted weapon here) never take the main target:
  `aim.point_defense(man, threats)` gives each the nearest incoming missile or
  fighter inside its 500-unit reach, arc and a clear line. Dead ahead 4 of 12
  main weapons bear (the bow lances and the forward gravitons; the turret
  lances are masked by the needler turrets in front of them); high on the port
  bow 5 bear. Fire only what `engage` and `point_defense` return.

  Everything is animatable from `SWFX controls` (`thr_*`, `smk_*`, `gun_*`,
  `beam_*`, `shield_*`; see `../second-wind-vfx/README.md`). Append it in the same load as
  the ship.

The meshes are baked (bevels applied), so the asset costs nothing to evaluate
when a shot moves it.

## Profile (`vfx_hull.py`)

The build carries `Engine <i> mouth lip` parts, which tell the kit these are
Second-Wind-shaped engines, so the kit looks for `Engine <i> exhaust anchor`
first; the effects export adds them. The profile sets `GEOMETRY_JSON` (the
scale), the ten `ENGINES` and their mouth radii, the port/starboard split, one
state `clean`, HIGH_TECH `COLOURS`, the needler `RIGS`, the `BEAMS` table
(read off the vanilla `.wpn` files and `weapon_data.csv`), the `SHIELD`
(geometry.json's `shield`, the HIGH_TECH hull style's shield colours and the
fortressshield system's), no lamps and no RCS.

## Build

`./finalize.sh` replays everything from `../paragon-surface/build/skinned.blend`
(run `../paragon-surface/rebuild.sh` first if it is missing):
1. `../paragon-geometry/export_paragon.py` on the textured build, into
   `build/export/` (the one collection, images packed);
2. `export_effects.py`: engine and kit gun anchors and the `SWFX` collection,
   saved as `paragon.blend` with `manifest.json`;
3. `verify_ship.py` -> `validation.json`;
4. `review_ship.py` (Cycles, 32 samples, GPU when there is one) and
   `compose_review.py`: `review/whole.png` (beauty three-quarter and stern
   quarter, without and with effects), `review/effects.png` (the ten drives,
   studio on and off; the stern quarter with only the ship's glow and the
   drives; a 2x crop of the formation frame), `review/formation-1.5km.png`,
   `review/beams.png`, `review/beam-broadside.png`, `review/beam-bow.png` and
   `review/beam-port.png` (`aim.engage` on a point 870 m ahead and on one
   high on the port bow, every main weapon that can bear fired at it),
   `review/beam-pd.png` (`aim.point_defense`: the PD lasers alone against
   seven incoming missiles, one beyond reach),
   `review/lance-sequence.png` (the bow lances through one shot), and
   `review/shield.png` with `review/shield-half.png`, `shield-raised.png`
   and `shield-fortress.png` (`--shield`: half raised, raised with two hits,
   and the Fortress Shield with three).

`--samples S` changes the samples, `--no-review` stops after verification.
Renders go to `build/review/`, sheets to `review/`; both are ignored.

## Checks

`verify_ship.py` appends `PARAGON | clean` into a factory-empty scene and
checks:
- everything is under the root, nothing is linked, every image is packed;
  mesh and polygon counts match the manifest; the root's `metres_per_unit`
  equals geometry.json's;
- the ten engine empties sit at their slots and point aft;
- every one of the 21 turrets trains 30 degrees about its pivot's local Z
  (muzzles swing, barbette stays) and elevates;
- the bow lance muzzles point forward; every kit gun anchor rides its needler
  muzzle;
- moving the root moves the ship rigidly.

It then appends the ship and `SWFX` together and checks: a plume, core, haze
and glow light on each of the ten drives, starting at its nozzle face, along
its exhaust and sized to its mouth; four gun anchors; all 17 beams dark until
fired, and fired, running from the muzzle along the line of fire to their
range with the impact glow at the end; a beam training with its turret; the
lance's charge lighting the emitter before the beam; on a target ahead and
one on the port bow, the PD lasers never taking the main target, the aft
turrets holding fire ahead and a ray along every fired beam meeting no part of
the ship but its own weapon; at least four PD lasers engaging missiles inside
their reach along clear lines while one beyond reach is left alone; the shield:
its shell centred on `Shield emitter` in plan, clear of every hull vertex by at
least a quarter of its margin and inside the vanilla sphere, dark (and the
nodes unlit) until raised, the ten nodes fully lit a third of the way up, the Fortress
Shield burning them hotter in its own colour, a hit aimed by azimuth and
elevation landing on the shell in the hull frame whatever the facing,
facing turning the unfold (both read by the bubble's shader), and the shell
enclosing every drive and turret; every driver valid; and the root carrying the
effects and the shield.

## Left out

- No missile effects: the four Reaper racks have no launch or wake.
- No running lights (the Paragon has none) and no lighting-rig preset (the
  shot's).
- No RCS puffs and no landing dust.
- No damaged or other condition states.

## Known issues

- The beam rig itself does not stop at the hull: keyed by hand past
  `aim.engage`, a turret trained across the ship would fire through it.
- Arcs are vanilla's flat 2D arcs measured in the hull's plane; the 3D hull
  masks more than the game's sprite does, which the clear-line rule handles.
- The shield's raise and lower times (1 s) are the cinematic's own: the
  game's shield unfold rate is not a data column.
- Gun flashes for the needlers are sized against the Second Wind's mount and
  were not reviewed firing.
