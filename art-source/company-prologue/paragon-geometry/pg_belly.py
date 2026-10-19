"""The Paragon's belly: what a laser-heavy capital carries underneath.

The game's top-down sprite shows none of it, so it is designed from the ship's
job, in the same language as the top:
- pod radiators (`Belly <side> pod radiator <k> ...`): big finned panels under
  each drive pod, where the drives' heat is;
- the strake (`Belly strake`, lights): a raised ring on the belt's underside,
  the gun shelf's echo below, with running lights;
- radiator banks (`Belly radiator <k> ...`): twelve recessed banks of fins round
  the main ring's flat underside, each with a faint glow strip at its inner end;
  a beam-armed ship sheds a great deal of heat, and the underside, clear of every
  turret, is where it can;
- docking ports (`Belly port <name> ...`): ventral collars with iris doors and
  glow rings under the aft body and under each pod, for boats and transfers;
- sensor blisters (`Belly blister <name>`): glazed domes under the prow and the
  bow quarters;
- the keel (`Belly keel ...`): a strake along the aft body's centreline with
  ventral running lights.
Everything faces down and is seated with pg_kit.surface_below.
"""
import math
import bpy
from mathutils import Vector
import pg_kit as K
import pg_hull

RADIATOR_BEARINGS = [15+30*k for k in range(12)]
RADIATOR = dict(r0=81.0, r1=99.0, width=21.0, depth=1.0, fins=14)
# Radiator panels under each drive pod (port side; starboard mirrors): centre, length x width.
POD_RADIATORS = [((-128.0, 112.0), 26.0, 12.0), ((-158.0, 64.0), 22.0, 12.0)]
STRAKE_R = (108.0, 112.0)   # the ventral strake ring on the belt's underside
PORTS = [('aft body', (-136.0, 0.0), 3.4), ('port pod', (-146.0, 100.0), 2.8), ('starboard pod', (-146.0, -100.0), 2.8)]
BLISTERS = [('prow', (150.0, 0.0), 2.6), ('port bow quarter', (96.0, 62.0), 2.0), ('starboard bow quarter', (96.0, -62.0), 2.0)]


def _below(kit, x, y):
    hit = K.surface_below(kit, x, y)
    return hit[0].z if hit else None


def _prism(kit, name, plan, z0, z1, mat):
    plan = K.ccw(plan)
    n = len(plan)
    verts = [(x, y, z0) for x, y in plan]+[(x, y, z1) for x, y in plan]
    faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))]+[(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat, 0, False)


def radiators(kit, parts):
    cx, cy = pg_hull.BORE_C
    R = RADIATOR
    made = []
    for k, b in enumerate(RADIATOR_BEARINGS, start=1):
        a = math.radians(b)
        ux, uy, vx, vy = math.cos(a), math.sin(a), -math.sin(a), math.cos(a)
        w = R['width']/2
        plan = [(cx+ux*r+vx*s_*w, cy+uy*r+vy*s_*w) for r, s_ in ((R['r0'], -1), (R['r1'], -1), (R['r1'], 1), (R['r0'], 1))]
        zs = [_below(kit, px, py) for px, py in plan]
        if any(z is None for z in zs):
            continue
        ceil = max(zs)+R['depth']
        for ob in (parts['main ring'], parts['belt']):
            cut = _prism(kit, 'Temporary radiator cutter', plan, -120.0, ceil, ob.material_slots[0].material)
            K.boolean(ob, cut)
        made.append(_prism(kit, f'Belly radiator {k} back', plan, ceil-.05, ceil+.8, kit.tech))
        for f in range(R['fins']):
            t = -w+.8+(2*w-1.6)*f/(R['fins']-1)
            rm = (R['r0']+R['r1'])/2
            fin = kit.box(f'Belly radiator {k} fin {f+1}', (cx+ux*rm+vx*t, cy+uy*rm+vy*t, ceil-R['depth']/2-.05),
                          (R['r1']-R['r0']-1.2, .3, R['depth']-.15), kit.bore_mid, 0)
            fin.rotation_euler = (0, 0, a)
            made.append(fin)
        glow = kit.box(f'Belly radiator {k} glow', (cx+ux*(R['r0']+.6), cy+uy*(R['r0']+.6), ceil-R['depth']+.1),
                       (.5, 2*w-1.6, .15), kit.glow, 0)
        glow.rotation_euler = (0, 0, a)
        made.append(glow)
    return made


def ports(kit):
    made = []
    for name, (x, y), r in PORTS:
        z = _below(kit, x, y)
        if z is None:
            continue
        tag = f'Belly port {name}'
        made.append(K.revolve(kit, tag+' collar', [(r+1.4, .3), (r+1.4, -.6), (r+.9, -1.0), (r, -1.0), (r, .3)], kit.pale,
                              center=(x, y, z), segments=32, smooth=False))
        made.append(K.revolve(kit, tag+' iris', [(0, -.7), (r, -.7), (r, -.5), (0, -.5)], kit.hatch, center=(x, y, z),
                              segments=32, smooth=False))
        made.append(K.revolve_loop(kit, tag+' glow ring', [(r+.15, -1.05), (r+.6, -1.05), (r+.6, -.95), (r+.15, -.95)],
                                   kit.glow, center=(x, y, z), segments=32, smooth=False))
        for j in range(6):
            t = 2*math.pi*j/6
            g = kit.box(f'{tag} leaf {j+1}', (x+math.cos(t)*r*.5, y+math.sin(t)*r*.5, z-.75), (r*.95, .12, .05),
                        kit.tech, 0)
            g.rotation_euler = (0, 0, t+math.radians(25))
            made.append(g)
    return made


def blisters(kit):
    made = []
    for name, (x, y), r in BLISTERS:
        z = _below(kit, x, y)
        if z is None:
            continue
        made.append(K.revolve(kit, f'Belly blister {name} collar', [(0, .2), (r+.8, .2), (r+.8, -.4), (0, -.4)],
                              kit.slate, center=(x, y, z), segments=24, smooth=False))
        dome = [(0, -.4)]+[(r*math.cos(math.radians(a)), -.4-r*.8*math.sin(math.radians(a))) for a in range(0, 91, 15)]
        made.append(K.revolve(kit, f'Belly blister {name}', dome, kit.glass, center=(x, y, z), segments=24, smooth=40))
    return made


def keel(kit):
    made = []
    x0, x1 = -104.0, -168.0
    zs = [_below(kit, x, 0) for x in (x0, (x0+x1)/2, x1)]
    if None in zs:
        return made
    z = max(zs)
    made.append(kit.box('Belly keel strake', ((x0+x1)/2, 0, z-1.0), (x0-x1, 2.4, 2.4), kit.slate, .2))
    for j, x in enumerate((-110.0, -126.0, -150.0, -164.0), start=1):
        made.append(kit.box(f'Belly keel light {j}', (x, 0, z-2.3), (.7, .7, .3), kit.glow_white, 0))
    return made


def pod_radiators(kit, parts):
    made = []
    for side, label in ((1, 'port'), (-1, 'starboard')):
        pod = parts['pod '+label]
        for k, ((x, y), l, w) in enumerate(POD_RADIATORS, start=1):
            y *= side
            plan = [(x-l/2, y-w/2), (x+l/2, y-w/2), (x+l/2, y+w/2), (x-l/2, y+w/2)]
            zs = [_below(kit, px, py) for px, py in plan]
            if any(z is None for z in zs):
                continue
            ceil = max(zs)+1.0
            cut = _prism(kit, 'Temporary pod radiator cutter', plan, -120.0, ceil, pod.material_slots[0].material)
            K.boolean(pod, cut)
            tag = f'Belly {label} pod radiator {k}'
            made.append(_prism(kit, tag+' back', plan, ceil-.05, ceil+.8, kit.tech))
            n = int(l/1.6)
            for f in range(n):
                fx = x-l/2+.8+(l-1.6)*f/(n-1)
                made.append(kit.box(f'{tag} fin {f+1}', (fx, y, ceil-.55), (.3, w-1.0, .9), kit.bore_mid, 0))
            made.append(kit.box(tag+' glow', (x, y-side*(w/2-.5), ceil-.9), (l-1.2, .4, .15), kit.glow, 0))
    return made


def strake(kit):
    """A raised ring strake on the belt's underside with running lights: the gun shelf's echo below."""
    cx, cy = pg_hull.BORE_C
    r0, r1 = STRAKE_R
    zs = [_below(kit, cx+(r0+r1)/2*math.cos(math.radians(b)), cy+(r0+r1)/2*math.sin(math.radians(b))) for b in
          range(0, 360, 30)]
    zs = [z for z in zs if z is not None]
    if not zs:
        return []
    z = min(zs)
    made = [pg_hull.revolve_loop(kit, 'Belly strake', [(r0, z+1.0), (r1, z+1.0), (r1-.6, z-1.2), (r0+.6, z-1.2)],
                                 (cx, cy, 0), kit.slate, segments=160)]
    for k in range(24):
        a = math.radians(7.5+15*k)
        r = (r0+r1)/2
        made.append(kit.box(f'Belly strake light {k+1}', (cx+r*math.cos(a), cy+r*math.sin(a), z-1.3), (.6, .6, .2),
                            kit.glow_white if k % 2 else kit.glow, 0))
    return made


def build(kit, parts):
    made = radiators(kit, parts)+pod_radiators(kit, parts)+strake(kit)+ports(kit)+blisters(kit)+keel(kit)
    kit.detail += made
    return made
