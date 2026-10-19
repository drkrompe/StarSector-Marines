"""The Paragon's ten drive units, at the engine slots of paragon.ship.

Each is a whole drive unit, not a nozzle: a faceted block (`Engine <i> drive
pod`) sunk into the aft pod and standing out past it as one of the castellated
blocks the sprite shows along the stern, with radiator louvres across its top
(`Engine <i> louvre <k>`), a chamfered-rectangle nozzle (aft-facing bell, mouth
lip) and the dark throat (VFX lights it), and an `Engine <i> exhaust` empty at the mouth
whose +X points along the exhaust. Sizes come from the slot widths (12 for the
four big drives, 8 for the three small ones each side); the small drives sit 7-9
units apart, so their pods touch and read as one block of three.
"""
import math
import pg_kit as K

ENGINES = [  # x, y, width, contrailSize, length, angle (from paragon.ship)
    (-179, 140, 12, 48, 48, 180),
    (-187, 105, 12, 48, 48, 180),
    (-189, 73, 8, 48, 32, 180),
    (-189, 64, 8, 48, 32, 180),
    (-187, 57, 8, 48, 32, 180),
    (-187, -56, 8, 48, 32, 180),
    (-189, -64, 8, 48, 32, 180),
    (-189, -72, 8, 48, 32, 180),
    (-187, -104, 12, 48, 48, 180),
    (-179, -139, 12, 48, 48, 180),
]
POD_FRONT_X = -146.0   # nacelles run forward into the aft pods to here
Z_AXIS = -2.0          # drive centreline height
RECORDS = []


def nacelle_section(hw, hh, n=20):
    """A superellipse YZ section, half-width hw and half-height hh: sleek but full."""
    pts = []
    for k in range(n):
        a = 2*math.pi*k/n
        c, s = math.cos(a), math.sin(a)
        pts.append((hw*math.copysign(abs(c)**.7, c), hh*math.copysign(abs(s)**.7, s)))
    return pts


def _rect(cy, cz, hw, hh, c):
    """A chamfered rectangle (octagon) in YZ about (cy, cz): half-width hw, half-height hh, chamfer c."""
    return [(cy-hw+c, cz-hh), (cy+hw-c, cz-hh), (cy+hw, cz-hh+c), (cy+hw, cz+hh-c),
            (cy+hw-c, cz+hh), (cy-hw+c, cz+hh), (cy-hw, cz+hh-c), (cy-hw, cz-hh+c)]


def _tube(kit, name, x0, x1, cy, cz, hw0, hh0, hw1, hh1, t, mat):
    """A hollow chamfered-rectangle tube from x0 (hw0, hh0) to x1 (hw1, hh1), wall t: a flared bell or a lip."""
    rings = []
    for x, hw, hh in ((x0, hw0, hh0), (x1, hw1, hh1)):
        c = .3*min(hw, hh)
        rings.append([(x, py, pz) for py, pz in _rect(cy, cz, hw+t, hh+t, c+t*.4)])
        rings.append([(x, py, pz) for py, pz in _rect(cy, cz, hw, hh, c)])
    verts = [v for r in rings for v in r]
    o0, i0, o1, i1 = 0, 8, 16, 24
    faces = []
    for k in range(8):
        n = (k+1) % 8
        faces += [(o0+k, o1+k, o1+n, o0+n), (i0+k, i0+n, i1+n, i1+k), (o0+k, o0+n, i0+n, i0+k),
                  (o1+k, i1+k, i1+n, o1+n)]
    return kit.mesh(name, verts, faces, mat, 0, False)


def build(kit):
    """Each drive is a faceted block standing out of the pod's stern face, the sprite's castellation:
    radiator louvres across its top, a chamfered-rectangle nozzle (aft-facing bell, mouth lip, dark
    throat) on its aft face. The small drives' mouths are tall slots; the big ones' broad."""
    RECORDS.clear()
    for i, (x, y, w, contrail, length, angle) in enumerate(ENGINES, start=1):
        r = w*.5
        big = w > 10
        hw, hh = (r*1.5, r*1.45) if big else (r*.95, r*2.2)
        mw, mh = (r*.95, r*.62) if big else (r*.62, r*1.55)   # mouth half-width, half-height
        z = Z_AXIS
        mouth = x+1.5
        face = mouth+4.0          # the block's aft face
        c = .28*min(hw, hh)
        stations = [(xs, [(py, pz) for py, pz in _rect(y, z, hw*f, hh*f, c*f)])
                    for xs, f in ((face, .97), (face+1.2, 1.0), (POD_FRONT_X-6, 1.0), (POD_FRONT_X, .6))]
        stations.sort(key=lambda s: s[0])
        K.loft_x(kit, f'Engine {i} drive pod', stations, kit.slate, bevel=0, smooth=False)
        # Radiator louvres across the block's top, aft of the pod's stern face.
        for k in range(5):
            xl = face+1.6+k*1.5
            kit.box(f'Engine {i} louvre {k+1}', (xl, y, z+hh+.25), (.5, hw*1.7, .7), kit.bore_mid, 0)
        # Nozzle: a flared bell standing aft of the face, a lip round its mouth, the dark throat on the face.
        _tube(kit, f'Engine {i} aft-facing bell', face, mouth, y, z, mw*.86, mh*.86, mw, mh, .5, kit.steel)
        _tube(kit, f'Engine {i} mouth lip', mouth+.6, mouth-.2, y, z, mw+.1, mh+.1, mw+.1, mh+.1, .45, kit.slate)
        kit.box(f'Engine {i} dark throat', (face-.15, y, z), (.3, mw*1.7, mh*1.7), kit.tech, 0)
        ex = kit.empty(f'Engine {i} exhaust', (x, y, z), angle, 'SINGLE_ARROW', w)
        ex['width'], ex['length'], ex['contrail_size'], ex['style'] = w, length, contrail, 'HIGH_TECH'
        RECORDS.append({'index': i, 'slot_location': [x, y], 'width': w, 'length': length, 'contrail_size': contrail,
                        'angle_deg': angle, 'style': 'HIGH_TECH', 'mouth_x': round(mouth, 2), 'axis_z': z,
                        'mouth_radius': r, 'mouth_half_size': [round(mw, 2), round(mh, 2)],
                        'exhaust_direction': [-1, 0, 0], 'anchor': ex.name,
                        'parts': [f'Engine {i} drive pod', f'Engine {i} aft-facing bell', f'Engine {i} mouth lip',
                                  f'Engine {i} dark throat']})


def records():
    return list(RECORDS)
