"""C5 layout: overhead on Second Wind, down in Nessa's yard, as its people walk off.

    blender --background --factory-startup --python c5_scene.py -- [--body 3d|sprite] [--stills t,...]
                                                                   [--step n] [--from s] [--to s] [--report]

Builds C4's scene as it ends (the ship down on Kilo Nine, the yard's vehicles parked at the hull,
the customs car off the stern) and plays C5 on from there, `DURATION` seconds, with the camera
straight down through a 50 mm lens about 220 m up (`c5-v2-closing.md`, "Camera and crowd"). From
18 s it rises straight up to about 1.8 km, until the yard is one corner of the port, and the title
comes up over that: `c5_title.py` lays it on the rendered frames.

Who is on the ground:
  yard vehicles still rolling in from their stand-offs as C5 opens: the tug, the crew carrier,
                the bowser, then the customs car; each stops at the hull in the first five seconds.
  yard workers  Nessa's crew in hi-vis, out of the vehicles once they stop: from the carrier two
                to guide at the foot of the ramp and two to work between it and the hull; from
                the tug its driver, who stays by it, and a runner between it and the hull; from
                the bowser its driver.
  customs       two officers out of their car once it pulls up, to stand either side of the way
                off the ship.
  Nessa         out of her workshop, to stand apart by its door with her arms folded.
  families      off the ramp from 8.5 s, in small groups that walk together, a child among the
                larger ones and bags in most hands, toward the workshop door and inside. It is
                52 m off, so the stream is still crossing as the camera rises. Some peel off near
                the ramp while the camera is close: one family waits off to the north side for
                somebody still aboard and then goes on, one stops to the south and stays. One
                person stops a few steps off the ramp and looks up before going on.

Everyone walks on a map read from the scene (`c5_crowd.Nav`), so nobody passes through a crate,
a vehicle, a wall or the hull; the run prints how many walker-frames ended up inside anything
(should be 0) and --map writes the map with everyone's tracks to build/c5/walk-map.png.

The rest of the port is at work too: `port_people.py` casts it, site by site, each on its own walk
map; --no-port leaves it out (quicker while working on the yard).

--body picks the look: simple 3D figures (default) or flat sprite cards; the choice waits on
the texture pass. --stills renders C5 seconds; otherwise the shot renders at --step (default 24:
one frame a second) to build/c5/frames/.
"""
import json
import math
import random
import sys
import time
from pathlib import Path

import bpy
from mathutils import Vector

sys.path.insert(0, str(Path(__file__).resolve().parent))
import c4x_scene as c4  # noqa: E402
import c5_crowd as crowd  # noqa: E402
import port_people  # noqa: E402

OUT = c4.REPO / 'build' / 'c5'
C4_END = c4.FRAMES - 1              # C5 starts on the frame C4 ends on
DURATION = 33.5
FRAMES = round(DURATION * c4.FPS)
LENS, HEIGHT = 50.0, 220.0
CENTRE = Vector((362.0, 436.0))
# The pull-out (`c5-v2-closing.md`, beats 18-26 s): straight up, easing in and out, drifting west so
# the yard ends as one corner of the port with the jungle along the right edge. Height grows at a
# steady rate of zoom (geometric), and keeps settling the last few percent under the title.
RISE = (18.0, 26.0)
TOP, TOP_CENTRE = 1800.0, Vector((150.0, 430.0))
SETTLE = 1.04                       # how much further it drifts up after the rise, to the end
PARKED = c4.CUSTOMS_ARRIVE - c4.DURATION + 0.5   # every vehicle has stopped by this C5 second
TITLE_T0 = 24.0                     # Castle's last line ends here; the title's reveal is timed from it
NAV_BOX = (298.0, 389.0, 452.0, 472.0)

KX, KY = c4.K9
KH = c4.k9_heading()


def at(u, v):
    return Vector(c4.at(KX, KY, KH, u, v)[:2])


RAMP = at(-36.5, 0)                       # the foot of the stern ramp, between the engine pods
DOOR = Vector((334.0, 410.6))             # Nessa's workshop door, under the awning
DOOR_FRONT = Vector((336.0, 415.0))
# Where a family steps aside, near the ramp so it happens while the camera is still close: one
# waits off the north side for somebody still aboard, one stops to the south to take it in.
SPOTS = {'wait': at(-40, 13), 'aside': at(-44, -12)}
OFFICERS = (Vector((362.0, 434.5)), Vector((358.5, 425.0)))   # either side of the way off the ship
CARRIER = Vector((369.0, 420.5))          # the crew carrier's near side
TUG = Vector((352.0, 444.0))
BOWSER = Vector((430.0, 437.0))
CAR = Vector((355.0, 428.0))


def ground_z(x, y):
    return c4.K9_SLAB if math.hypot(x - KX, y - KY) < c4.K9_R else 0.15


def cast(rng):
    W, G = crowd.Walker, crowd.Group
    groups = []
    ship = (KX, KY)

    def worker(name, start, plan, t0=0.0, look=ship, speed=1.2):
        groups.append(G([W(name, 'worker', speed=speed, look=look)], start, t0, plan))

    # The yard's vehicles are still rolling in when C5 opens (`c4x_scene.K9_ROLL_IN`): each crew
    # climbs out once its vehicle has stopped, the ramp crew from the crew carrier's side door.
    parked = {n: stop - c4.DURATION for n, (_, stop) in c4.K9_ROLL_IN.items()}
    out = parked['Crew carrier'] + 0.6
    guide = RAMP + (RAMP - at(-30, 0))
    for k, (name, spot) in enumerate((('Yard hand guide L', at(-37.5, 6.5)), ('Yard hand guide R', at(-37.5, -6.5)))):
        worker(name, CARRIER, [('go', spot, 0.4), ('stay',)], t0=out + 0.5 * k, look=guide)
    for k in range(2):
        a, b = CARRIER + Vector((k * 2.0 - 1.0, 0)), at(-30, -14 - k * 2)
        worker('Yard hand carrier %d' % k, CARRIER,
               [('go', a, 0.6), ('wait', 1.5 + k), ('go', b, 0.6), ('wait', 3.0), ('go', a, 0.6), ('wait', 2.5)] * 5,
               t0=out + 1.0 + 0.5 * k)
    worker('Yard hand tug', TUG, [('stay',)], t0=parked['Tug'] + 0.6)
    worker('Yard hand tug runner', TUG + Vector((1.5, -1.0)),
           [('go', at(-26, 26), 0.6), ('wait', 3.0), ('go', TUG + Vector((1.5, -1.0)), 0.6), ('wait', 4.0)] * 6,
           t0=parked['Tug'] + 1.1)
    worker('Yard hand bowser', BOWSER, [('stay',)], t0=parked['Fuel bowser'] + 0.6)
    # Customs: out of the car once it has pulled up off the stern, to either side of the way off the ship.
    car = c4.CUSTOMS_ARRIVE - c4.DURATION
    for k, post in enumerate(OFFICERS):
        groups.append(G([W('Customs officer %d' % k, 'customs', speed=1.15, look=RAMP)],
                        CAR + Vector((0.0, 2.6 * (1 if k == 0 else -1))), car + 0.8 + 0.6 * k,
                        [('go', post, 0.5), ('stay',)]))
    # Nessa: out of her door, a few steps, and stands apart.
    groups.append(G([W('Nessa', 'nessa', speed=1.0, look=RAMP)], DOOR_FRONT, 4.5,
                    [('go', Vector((350.0, 419.0)), 0.5), ('stay',)]))
    # The families.
    sizes = [3, 2, 4, 1, 3, 2, 5, 1, 2, 3, 4, 2, 3]
    special = {2: [('go', SPOTS['wait'], 1.5), ('wait', 9.0), ('go', DOOR_FRONT, 2.5), ('enter', DOOR)],
               5: [('go', SPOTS['aside'], 1.2), ('stay',)],
               7: [('go', at(-41, -1.5), 0.5), ('wait', 4.0), ('go', DOOR_FRONT, 2.5), ('enter', DOOR)]}
    t = 8.5
    for gi, n in enumerate(sizes):
        speed = rng.uniform(1.15, 1.4)
        members = []
        for k in range(n):
            child = n >= 3 and k == n - 1
            members.append(W('Family %d %d' % (gi, k), 'family', scale=0.62 if child else rng.uniform(0.95, 1.05),
                             speed=speed, bag=not child and rng.random() < 0.65))
        look = ship if gi == 7 else None
        if look:
            members[0].look = look
        groups.append(G(members, RAMP, t, special.get(gi, [('go', DOOR_FRONT, 2.5), ('enter', DOOR)])))
        t += rng.uniform(1.0, 1.7)
    return groups


def setup(body):
    scene = c4.build()
    for ob in [o for o in bpy.data.objects if o.name.startswith(('Yard hand', 'Landing dust', 'Yard people'))]:
        bpy.data.objects.remove(ob)
    scene.frame_set(C4_END + round(PARKED * c4.FPS))   # the walk map is read with the vehicles parked
    manifest = json.loads((c4.STATES / 'manifest.json').read_text())
    ship_parts = {o.name for o in bpy.data.collections[manifest['append_collection']].all_objects}
    ship_parts |= {o.name for o in bpy.data.objects if o.name.startswith('Drive ')}
    nav = crowd.Nav(*NAV_BOX)
    t0 = time.time()
    nav.read_scene(ship_parts, RAMP, extra=[((KX, KY), (33.0, 26.5), KH)])
    print('C5_CROWD walk map %.1fs' % (time.time() - t0), flush=True)
    rng = random.Random(11)
    groups = cast(rng)
    if '--no-port' not in sys.argv:
        groups += port_people.yard_extras()
    walkers = crowd.simulate(nav, groups, FRAMES, ground_z)
    print('C5_CROWD walked %.1fs' % (time.time() - t0), flush=True)
    if '--report' in sys.argv:
        door = nav.field(DOOR_FRONT)
        print('C5_REPORT door field at ramp %.1f m' % nav.value(door, RAMP), flush=True)
        for g in groups:
            w = g.members[0]
            if not w.track:
                continue
            row = ['%s step=%d/%d' % (w.name, g.step, len(g.plan))]
            for f, x, y, h, ph, shown in w.track[::96]:
                row.append('%d:(%.0f,%.0f)%s' % (f // 24, x, y, '' if shown else 'x'))
            print('C5_REPORT ' + ' '.join(row), flush=True)
    inside = crowd.clip_report(nav, walkers)
    print('C5_CROWD walkers=%d inside-something=%d' % (len(walkers), inside), flush=True)
    OUT.mkdir(parents=True, exist_ok=True)
    crowd.save_map(nav, walkers, OUT / 'walk-map.png')
    if body == 'sprite':
        make = crowd.body_sprite(c4.mesh, c4.SUN_DIR, OUT / 'sprites', c4.box, c4.material)
    else:
        make = crowd.body_3d(c4.mesh, c4.box, c4.material)
    crowd.keyed(walkers, ground_z, C4_END, make)
    if '--no-port' not in sys.argv:
        (OUT / 'walk-maps').mkdir(parents=True, exist_ok=True)
        clips = 0
        for site, pnav, people in port_people.populate(ship_parts, FRAMES, C4_END):
            crowd.save_map(pnav, people, OUT / 'walk-maps' / (site.name + '.png'))
            crowd.keyed(people, lambda x, y, z=site.z: z, C4_END, make)
            clips += crowd.clip_report(pnav, people)
        for z, w in port_people.posed(C4_END, FRAMES):
            crowd.keyed([w], lambda x, y, z=z: z, C4_END, make)
        print('C5_CROWD port inside-something=%d' % clips, flush=True)
    cam = c4.link(bpy.data.objects.new('C5 | overhead', bpy.data.cameras.new('C5 | overhead')))
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = LENS, c4.SENSOR_MM, 'HORIZONTAL'
    cam.data.clip_start, cam.data.clip_end = 5.0, 60000.0
    cam.rotation_euler = (0, 0, 0)
    for f in range(0, FRAMES + 1, 3):
        t = f / c4.FPS
        k = c4.smoothstep(*RISE, t)
        settle = 1 + (SETTLE - 1) * c4.smoothstep(RISE[1] - 2.0, DURATION, t)
        c = CENTRE.lerp(TOP_CENTRE, k)
        cam.location = (c.x, c.y, HEIGHT * (TOP / HEIGHT) ** k * settle)
        cam.keyframe_insert('location', frame=C4_END + min(f, FRAMES - 1))
    scene.camera = cam
    scene.frame_start, scene.frame_end = C4_END, C4_END + FRAMES - 1
    return scene


def main():
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    body = argv[argv.index('--body') + 1] if '--body' in argv else '3d'
    scene = setup(body)
    if '--report' in argv:
        return
    if '--still' in argv or '--stills' in argv:
        key = '--stills' if '--stills' in argv else '--still'
        target = OUT / 'stills'
        target.mkdir(parents=True, exist_ok=True)
        for t in argv[argv.index(key) + 1].split(','):
            f = C4_END + min(FRAMES - 1, round(float(t) * c4.FPS))
            scene.frame_set(f)
            scene.render.filepath = str(target / ('%s-%05d.png' % (body, f - C4_END)))
            bpy.ops.render.render(write_still=True)
            print('C5_FRAME', f - C4_END, flush=True)
        return
    step = int(argv[argv.index('--step') + 1]) if '--step' in argv else 24
    target = OUT / 'frames'
    target.mkdir(parents=True, exist_ok=True)
    for old in target.glob('*.png'):
        old.unlink()
    a = round(float(argv[argv.index('--from') + 1]) * c4.FPS) if '--from' in argv else 0
    b = round(float(argv[argv.index('--to') + 1]) * c4.FPS) if '--to' in argv else FRAMES
    frames = range(C4_END + a, C4_END + min(b, FRAMES), step)
    scene.frame_start, scene.frame_end, scene.frame_step = frames.start, frames.stop - 1, frames.step
    scene.render.filepath = str(target / 'f#####')
    bpy.ops.render.render(animation=True)
    for i, f in enumerate(frames):
        (target / ('f%05d.png' % f)).rename(target / ('%05d.png' % i))


if __name__ == '__main__':
    main()
