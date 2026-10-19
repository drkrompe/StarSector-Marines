"""C1 civilian lane traffic in 3D: the finished freighter hulls in place of painted cards.

Called from `c1v2_scene.add_traffic`. Each hull is the cinematic model as published, appended once
and instanced, at its own metres per unit, with the stand-in orange drive glows and capped lamps the
picket uses. Every hull keeps a constant velocity for the whole shot (`c1v2_scene.fly`).

  Atlas outbound  replaced: a Colossus 5.2 km out where the painted Atlas card was (top left of the
                  final view), outbound and climbing away from the station: about 55 px long.
  station queue   inbound to Jangala Station's docks, 8.0-8.9 km out, ahead of and below the
                  station card: a Colossus (about 34 px), a Shepherd in Ko Combine colours, a
                  Mudskipper and a second Shepherd, each 18-22 px, crawling in at 6-12 m/s.

At these ranges every hull is a few dozen pixels, so they read as working freighters by shape and
drive glow; nothing here is close enough to need a closer texture look than its package gives.

Inputs, from build/c1v2/inputs/ (as the scene's other hulls): colossus.blend, shepherd.blend and
mudskipper-skinned.blend, each cinematic hull optionally with its `<hull>-manifest.json`; without one
the published collection, root and scale below are used. A hull whose file is missing is skipped with
C1_FLEET missing, so the scene still builds.
"""
import json
import math

from mathutils import Vector

# hull: (blend, collection, root, metres per unit, engine count (main drives), drive glow energy)
HULLS = {
    'colossus': ('colossus.blend', 'COLOSSUS', 'COLOSSUS | root', 0.50, 6, 2.0e5),
    'shepherd': ('shepherd.blend', 'SHEPHERD', 'SHEPHERD | root', 0.78, 2, 2.0e5),
    'mudskipper': ('mudskipper.blend', 'MUDSKIPPER', 'MUDSKIPPER | root', 0.85, 1, 2.0e5),
}
# (hull, px, py in the final view at the shot's end, range m, velocity in world m/s (x right, y down the lane, z up), roll deg)
LANE = (
    ('colossus', 170, 95, 5200.0, (-4.0, 2.0, 1.5), 6),          # the old Atlas card's place: outbound
    ('colossus', 700, 330, 8600.0, (6.0, 2.0, 0.0), -3),          # station queue, the big one
    ('shepherd', 640, 350, 8200.0, (7.0, 3.0, 0.5), 4),
    ('mudskipper', 860, 345, 8400.0, (-8.0, 2.0, 0.0), -6),
    ('shepherd', 920, 315, 8900.0, (-5.0, 1.0, 0.0), 2),
)


def add(c1):
    colls = {}
    for hull, (fname, cname, root, mpu, drives, energy) in HULLS.items():
        path = c1.INPUTS / fname
        if not path.exists():
            print('C1_FLEET missing', hull, path, flush=True)
            continue
        side = c1.INPUTS / ('%s-manifest.json' % hull)
        if side.exists():
            m = json.loads(side.read_text())
            cname, root, mpu = m['append_collection'], m['root'], m['metres_per_unit']
            engines = [(e['anchor'], e['radius']) for e in m['engines'] if e.get('role') == 'main drive']
        else:
            engines = [('Engine %d exhaust anchor' % i, 3.0) for i in range(1, drives + 1)]
        coll = c1.hull_collection(path, cname)
        c1.drive_glows(coll, root, engines, energy)
        c1.quiet_lamps(coll, 6.0)
        colls[hull] = (coll, mpu)
    mud = c1.INPUTS / 'mudskipper-skinned.blend'
    if 'mudskipper' not in colls and mud.exists():   # until the finished Mudskipper is staged
        colls['mudskipper'] = (c1.hull_collection(mud), 0.85)
    for i, (hull, px, py, rng, vel, roll) in enumerate(LANE):
        if hull not in colls:
            continue
        coll, mpu = colls[hull]
        e = c1.instance(coll, mpu, 'Lane %d | %s' % (i, hull))
        e.rotation_mode = 'XYZ'
        p = c1.direction_at_px(px, py) * rng
        v = Vector(vel)
        # (px, py) is where it ends, in the final view. Bow along the velocity, as the scene's own
        # Mudskipper flies.
        c1.fly(e, 0.0, c1.DURATION, p - v * c1.DURATION, p, roll=math.radians(roll))
        print('C1_FLEET %s at %.0f m, about %.0f px long' % (
            e.name, rng, {'colossus': 108.5, 'shepherd': 67.1, 'mudskipper': 67.2}[hull] / rng * c1.F_PX), flush=True)
