# Thruster markers

Status: ACTIVE
Written: 2026-10-19

Every flying craft in the arrival cinematic carries one marker per nozzle, so a plume, a glow,
a heat shimmer or an engine sound can be hung off the same point in every shot. This folder is
the convention, the helper every scene imports, and the check that holds the scenes to it.

## The convention

A marker is an empty:

| | |
|---|---|
| Name | `THRUST \| <craft> \| <kind> <nn>` (main drives keep their engine number: `main 03` is Engine 3) |
| Collection | `THRUST MARKERS`, linked to the scene; markers never render |
| Parent | the craft (its root, or the collection-instance empty that places it), so it rides every move |
| Location | the nozzle exit: the bell mouth plane, on the bell's axis |
| Axis | **local +X down the exhaust**, local +Z as near hull-dorsal as that allows |
| `thrust_kind` | `main` (drives), `lift` (jets blowing hull-down: hover and landing), `maneuver` (RCS and other attitude jets) |
| `thrust_craft`, `thrust_index` | which craft, which nozzle |
| `radius_m` | nominal mouth radius in metres: the size to build a plume to |
| `thrust_throttle` | 0..1, keyed over the shot. Plumes, lights and sound follow this one value |
| `thrust_working` | False for a dead bell (Second Wind's crushed Engine 1 and cold Engine 2): its marker stays, at 0 |
| `thrust_source` | what the position was measured from |

+X is the axis the hull geometry's own anchors (`Engine <i> exhaust anchor`, `RCS ... anchor`)
and the VFX kit (`second-wind-vfx`, `SWFX anchor ...`) already use, so an effect built for the
kit sits on a marker unchanged. The craft object also carries `thrust_craft`, `thrust_markers`
(its count) and `thrust_activity`, a few words on what the engines are doing in that shot.

## Where positions come from

Measured, never placed by eye, in this order:

1. the hull's own exhaust and RCS anchors (every published hull has them: Second Wind, Legion,
   Onslaught, Lasher, Hound, Colossus, Hermes, Shepherd, Broadsword); the radius is the anchor's
   `radius_m`, its `radius`/`mouth_radius` in hull units, or measured off the mouth-lip or bell
   mesh;
2. for a hull with no anchors (the skinned Mudskipper), each `Engine <i> bell` mesh: its mouth
   is the vertex rim furthest along the exhaust direction in `mudskipper-geometry/geometry.json`;
3. for a painted card (C1's outbound Atlas), the hot bells painted into the image, with +X in
   the card plane from the hull's centre out through the bell.

The kind comes from the name (RCS anchors are `maneuver`, unless an engine table names a jet)
and from the direction: anything blowing within about 45 degrees of hull-down is `lift`.

A hull that lifts and lands in air but has no belly jets in its model (the port's Hermes
lighters, Hounds and Mudskipper ferry in C4/C5) gets one `lift` marker per main bell, at that
bell's mouth, pointing hull-down with +Z toward the bow: its mains vectored for lift
(`thrust_markers.lift_from_mains`). No nozzle is invented. Second Wind has real belly jets
(its down-facing RCS), so it uses those.

Such a lift marker carries `thrust_vectored` (the name of the main it is the same drive as) and
its direction is keyed, because the drive turns (owner, 2026-10-19: thrusting down is fine
while not moving). With no way on (hovering, going straight up or setting down) it points
hull-down and carries the drive's whole throttle. As the craft picks up horizontal speed it
pitches back toward its main's aft axis, fully aft by `VECTOR_FULL` (6 m/s), and hands its
throttle over to the main marker at the same mouth; slowing down turns it back. The two always
add up to the drive's one throttle (1.0 hovering, 0.6 cruising), so hang a plume off either or
both. A plume parented to the lift marker follows the turn by itself; read `rotation_quaternion`
if you need the direction. Re-keying throttle with `tm.key` leaves the turn alone.

## Using it

```python
import thrust_markers as tm
found = tm.find_craft(scene)          # every craft the view layer shows, found by its nozzles
marks = tm.mark(found['Flotilla 0 | onslaught'], 'Onslaught')
tm.key(marks, [(0, 0.35)], fps=24, kinds=('main',))   # (seconds, throttle) keys
tm.auto(craft, marks, fps, (0, end))  # throttle read off a craft's own keyed motion
```

`thrust_shots.py` is the per-shot half: one call at the end of each scene's build
(`mark_c1`, `mark_c2`, `mark_c4`, which also covers C5) gives the named craft their throttle
from the scene's own constants, and every other craft `auto` (parked: off; climbing, sinking
or (in the port shot) hovering still in the air: lift; travelling: cruise; vectored drives turn between the two). A ship another session adds is found and marked
without a change here. Where a shot already hung a drive glow off an exhaust anchor (C1's and
C4's Second Wind glows, C2's Broadsword flames), it now hangs off the marker.

## No craft thrusts without a plume

Owner, 2026-10-21: plumes match forward momentum or implied acceleration, and a cinematic may not
ship a moving craft without them again (C4's inbound Shepherd once let down through the opening
seconds with nothing firing). The rule, `thrust_markers.plume_faults`:

- every craft with a working `main` or `lift` nozzle whose throttle rises above 0 on any frame of
  the shot must have plume geometry that renders, hung under that nozzle's marker (so it follows
  the marker's throttle), **or** carry its drive effects in its own hierarchy (the VFX kit's
  `SWFX thrusters` volumes, or an object a shot tagged `thrust_plume` as that craft's drive, such as
  C1's stand-in drive glows and C4's Hermes lifter glow);
- `maneuver` jets are attitude puffs and are not held to it;
- a craft whose drives the camera can never see carries `thrust_plume_exempt` with the reason (C2's
  Second Wind, filmed from its own bridge); the build prints every exemption.

`thrust_shots.require_plumes(fps, frames, shot)` applies it: `mark_c1` and `mark_c2` call it at
their end, and `c4x_scene.build` (which C5 builds on) once Second Wind's and the Colossus's plumes are
hung. A fault stops the build with SystemExit naming each craft and its bare nozzles, and a
`render_init` handler asks again as any render starts, so a script that forgets the call still
cannot render past it. A new shot calls it once its last plume is on.

In C4/C5, `mark_c4` gives every craft it marks from motion (`_rest`) `port_fleet.far_plume` on each
main and lift nozzle that fires (`hang_plumes`): the inbound Shepherd on its mains, the lifting and
landing Hermes lighters and freighters on their vectored drives, the ferry leaving north. Each
plume hides when its craft does and is tagged `thrust_plume_added`. `CINE_PLUMES=0` leaves them off
as a control; the guard then only warns, except under `CINE_FINAL=1`, where it still refuses.

## Checking

```
blender --background --factory-startup --python-exit-code 1 --python check_thrust_markers.py -- --shot c1|c2|c4 [--still 9,26]
python draw_glyphs.py <repo>/build/thrust-markers c1-26.0s
python c3_atlas_markers.py [--sheet]          # C3's Atlas layer (2D composite)
```

The check builds the shot the way its own script does and fails if a craft has no markers, a
marker breaks the convention, or an object named like a ship is not a marked craft. It writes
`build/thrust-markers/<shot>.json` (per marker: craft, kind, radius, throttle keys, place on the
craft, and for a vectored lift marker its exhaust direction once a second; per craft: world
position once a second) for the VFX and sound passes. A vectored marker must point down whenever
it thrusts with no way on, and never adds up with its main to more than full throttle. `--still` renders
1920x1080 frames, and `draw_glyphs.py` draws the markers over them.

## Shots without a 3D scene

Entry and C3 are window composites (`patrol-abeam/compose_entry.py`, `first-open-sky/compose_c3.py`)
with no ship model in them, so they have no markers; C3's Atlas lifter is a baked card layer.
The older Blender scenes for those beats (`space-approach`, `landing-refinement`,
`landing-wreck-pass`) open `.blend` files that no longer exist in any checkout or on Drive, so
they cannot be built or marked.
