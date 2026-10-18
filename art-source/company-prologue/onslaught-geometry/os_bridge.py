"""The bone-white central spine and its two domes.

onslaught_hegemony.png draws a pale spine down the middle of the core, from
the arch at X ~96 back to X ~-60, swelling into two domes:
  command dome  X ~6..44, the brightest part of the sprite, with a
                checkerboard arc across its forward face at X 30..39: the
                bridge glazing, looking forward along the prow;
  aft dome      X ~-48..-16, a quieter helmet shape over the reactor spaces.
WS 025 (a decorative small hardpoint at X 17) sits on the command dome's
crown and is fitted here as the main sensor mast (os_fittings).
"""
import math
from os_kit import ccw, prism, section

SPINE_HALF = [(98, 6), (88, 9), (60, 10), (46, 16), (40, 22), (10, 26), (-6, 22), (-16, 18), (-46, 18),
              (-58, 13), (-66, 8)]
SPINE = section(16.0, 25.0, 2.0, 2.5)
CMD = {'x': 24.0, 'y': 0.0, 'r': 21.0, 'z0': 24.0, 'z1': 38.0}     # command dome
AFT = {'x': -31.0, 'y': 0.0, 'r': 16.5, 'z0': 24.0, 'z1': 33.0}    # aft dome


def _dome(kit, name, d, mat, n=20, rings=((1.0, 0.0), (.97, .35), (.84, .7), (.6, .92), (.28, 1.0)), stretch=1.0):
    """A faceted low dome: (radius fraction, height fraction) rings, capped. stretch elongates along X."""
    verts, faces = [], []
    for rf, hf in rings:
        r = d['r']*rf
        z = d['z0']+(d['z1']-d['z0'])*hf
        verts += [(d['x']+stretch*r*math.cos(2*math.pi*(i+.5)/n), d['y']+r*math.sin(2*math.pi*(i+.5)/n), z)
                  for i in range(n)]
    faces.append(tuple(reversed(range(n))))
    for k in range(len(rings)-1):
        faces += [(k*n+i, k*n+(i+1) % n, (k+1)*n+(i+1) % n, (k+1)*n+i) for i in range(n)]
    top = (len(rings)-1)*n
    faces.append(tuple(range(top, top+n)))
    return kit.mesh(name, verts, faces, mat)


def _glazing(kit, name, d, a0, a1, h0, h1, n=10, proud=.35):
    """A band of glass on the dome's forward face between bearings a0..a1 (deg from the bow)."""
    verts, faces = [], []
    pts = []
    for hf in (h0, h1):
        rf = 1-(1-.84)*(hf-.35)/(.7-.35) if hf > .35 else .97
        r = d['r']*rf+proud
        z = d['z0']+(d['z1']-d['z0'])*hf
        for i in range(n+1):
            a = math.radians(a0+(a1-a0)*i/n)
            pts.append((d['x']+r*math.cos(a), d['y']+r*math.sin(a), z))
    inner = [(x-(x-d['x'])*.08, y-(y-d['y'])*.08, z) for x, y, z in pts]
    verts = pts+inner
    m = n+1
    for i in range(n):
        faces.append((i, i+1, m+i+1, m+i))                    # outer face
        faces.append((2*m+i, 2*m+m+i, 2*m+m+i+1, 2*m+i+1))    # inner face
    for i in (0, n):
        faces.append((i, m+i, 2*m+m+i, 2*m+i))
    for i in range(n):
        faces.append((i+1, i, 2*m+i, 2*m+i+1))
        faces.append((m+i, m+i+1, 2*m+m+i+1, 2*m+m+i))
    return kit.mesh(name, verts, faces, kit.glass)


def build(kit):
    half = SPINE_HALF
    plan = ccw(list(half)+[(x, -y) for x, y in reversed(half)])
    spine = prism(kit, 'Bridge spine', plan, SPINE, kit.bone)
    cmd = _dome(kit, 'Bridge command dome', CMD, kit.bone, stretch=1.0)
    aft = _dome(kit, 'Bridge aft dome', AFT, kit.bone, stretch=1.15)
    # The bridge: a glazed band across the command dome's forward face.
    _glazing(kit, 'Bridge glazing', CMD, -38, 38, .38, .62)
    kit.hull += [spine, cmd, aft]
    return {'spine': spine, 'command dome': cmd, 'aft dome': aft}
