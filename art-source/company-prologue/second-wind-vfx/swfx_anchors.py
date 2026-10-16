"""Effect anchors found on the built ship.

Anchors are empties parented to the ship part they belong to, so effects
follow the model wherever a shot moves it and survive geometry edits: a
nozzle anchor rides the engine's mouth lip, a muzzle anchor the gun's muzzle
brake, a lamp anchor the lamp. Each anchor's local +X is the effect axis
(exhaust direction aft out of the bell; line of fire out of the barrel) and
local +Z is as near hull-dorsal as that allows.

Positions come from the parts themselves (by the stable names in
sw_engines.py / sw_turrets.py / sw_bridge.py / sw_port_module.py); the
ENGINES / MOUNTS tables only supply sizes and serve as the fallback when a
part is missing. A part missing in a state (the derelict's stripped guns)
simply yields no anchor.
"""
import math
import sys
from pathlib import Path
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
GEOMETRY = HERE.parent / 'second-wind-geometry'
if str(GEOMETRY) not in sys.path:
    sys.path.insert(0, str(GEOMETRY))

import swfx_core  # noqa: E402

try:  # tables only; importing them builds nothing
    from sw_engines import ENGINES, BELL_R  # noqa: E402
    from sw_turrets import MOUNTS  # noqa: E402
except Exception:  # pragma: no cover - a renamed table must not stop a shot
    ENGINES = [(-41, 31, 12), (-39, 24, 8), (-39, -24, 8), (-41, -31, 12)]
    BELL_R = {12: 3.9, 8: 2.6}
    MOUNTS = [(-11, 20, 90, 1.0, False), (-11, -20, -90, 1.0, False),
              (41, 11, 30, 1.0, False), (36, -16, -30, 1.3, True)]

ANCHORS = 'Anchors'

# Ship lamps: (object name prefix, role, colour). Port/starboard is split
# by the lamp's side of the centreline where the role needs it.
LAMPS = [
    ('Command deck running light', 'running', None),
    ('Crosstree signal light', 'running', None),
    ('Masthead light', 'masthead', (1.0, .97, .9)),
    ('Roof beacon', 'beacon', (1.0, .6, .15)),
    ('Grapple sponson work light', 'work', (1.0, .62, .25)),
    ('Sponson bow work light', 'work', (1.0, .62, .25)),
    ('Davit position light', 'davit', (.25, 1.0, .35)),
    ('Drive warning light', 'drive-warning', (1.0, .08, .04)),
]
RED, GREEN = (1.0, .08, .04), (.1, 1.0, .3)


class EngineAnchor:
    def __init__(self, index, empty, radius, width):
        self.index = index
        self.empty = empty            # at the bell mouth centre, +X aft
        self.radius = radius          # bell mouth radius (hull units)
        self.width = width            # vanilla slot width (12 outer, 8 inner)
        self.side = 'port' if index in swfx_core.PORT_ENGINES else 'starboard'

    def __repr__(self):
        return f'<engine {self.index} {self.side} r={self.radius:.2f}>'


class GunAnchor:
    def __init__(self, turret, tag, empty, scale, twin):
        self.turret = turret          # 1..4 (sw_turrets MOUNTS order)
        self.tag = tag                # 'gun', or 'port gun' / 'starboard gun' on turret 4
        self.empty = empty            # at the muzzle brake's mouth, +X along the barrel
        self.scale = scale            # mount scale (1.0 small, 1.3 the medium twin)
        self.twin = twin

    def __repr__(self):
        return f'<turret {self.turret} {self.tag}>'


class LampAnchor:
    def __init__(self, name, role, empty, color, part):
        self.name = name
        self.role = role
        self.empty = empty
        self.color = color
        self.part = part

    def __repr__(self):
        return f'<lamp {self.role} {self.name}>'


class Anchors:
    def __init__(self):
        self.engines = []
        self.guns = []
        self.lamps = []
        self.bounds = (Vector((-48, -40, -10)), Vector((52, 40, 24)))
        self.hull = None

    def engine(self, i):
        return next((e for e in self.engines if e.index == i), None)

    def record(self):
        bpy.context.view_layer.update()
        w = lambda e: [round(v, 3) for v in e.empty.matrix_world.translation]
        return {
            'engines': [{'index': e.index, 'side': e.side, 'radius': e.radius, 'mouth': w(e),
                         'axis': [round(v, 3) for v in e.empty.matrix_world.to_3x3().col[0]]} for e in self.engines],
            'guns': [{'turret': g.turret, 'tag': g.tag, 'muzzle': w(g),
                      'axis': [round(v, 3) for v in g.empty.matrix_world.to_3x3().col[0]]} for g in self.guns],
            'lamps': [{'name': l.name, 'role': l.role, 'at': w(l)} for l in self.lamps],
            'bounds': [list(map(lambda v: round(v, 2), b)) for b in self.bounds],
        }


def frame(axis, up=Vector((0, 0, 1))):
    """3x3 with +X along axis and +Z as near `up` as possible."""
    x = Vector(axis).normalized()
    z = Vector(up) - x * x.dot(up)
    if z.length < 1e-6:
        z = Vector((0, 1, 0)) - x * x.dot(Vector((0, 1, 0)))
    z.normalize()
    y = z.cross(x)
    return Matrix((x, y, z)).transposed()


def place(name, coll, part, location, axis, size=1.0):
    """An anchor empty at world location/axis, parented to part so it rides it."""
    ob = swfx_core.empty(name, coll, size)
    world = Matrix.Translation(location) @ frame(axis).to_4x4()
    if part is not None:
        ob.parent = part
        # With the inverse set to parent^-1, basis == world at build time.
        ob.matrix_parent_inverse = part.matrix_world.inverted()
    ob.matrix_basis = world
    return ob


def named(prefix):
    return sorted((o for o in bpy.data.objects if o.name == prefix or o.name.startswith(prefix + '.')),
                  key=lambda o: o.name)


def bbox_centre(ob):
    pts = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
    return sum(pts, Vector()) / len(pts)


def ship_objects():
    return [o for o in bpy.data.objects if o.type == 'MESH' and not o.name.startswith('SWFX')
            and not any(c.name.startswith(swfx_core.ROOT) for c in o.users_collection)]


def find(scene=None, coll=None):
    """Build (or rebuild) every anchor on the ship currently in the file."""
    bpy.context.view_layer.update()
    root = swfx_core.collection(swfx_core.ROOT, (scene or bpy.context.scene).collection)
    coll = coll or swfx_core.collection(f'{swfx_core.ROOT} | {ANCHORS}', root)
    for ob in list(coll.objects):
        bpy.data.objects.remove(ob, do_unlink=True)
    a = Anchors()

    parts = ship_objects()
    if parts:
        pts = [o.matrix_world @ Vector(c) for o in parts for c in o.bound_box]
        a.bounds = (Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts))),
                    Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts))))
    a.hull = bpy.data.objects.get('Main hull') or bpy.data.objects.get('Hull')

    # --- engines: bell mouth centre and aft axis -------------------------
    for i, (x, y, w) in enumerate(ENGINES, 1):
        lip = bpy.data.objects.get(f'Engine {i} mouth lip')
        throat = bpy.data.objects.get(f'Engine {i} dark throat')
        if lip is not None:
            mouth = lip.matrix_world.translation.copy()
            axis = (mouth - throat.matrix_world.translation) if throat else Vector((-1, 0, 0))
        else:
            mouth, axis = Vector((x, y, 0)), Vector((-1, 0, 0))
        ob = place(f'SWFX anchor engine {i}', coll, lip, mouth, axis, BELL_R[w])
        a.engines.append(EngineAnchor(i, ob, BELL_R[w], w))

    # --- guns: muzzle brake mouth and barrel axis ------------------------
    for i, (x, y, angle, s, heavy) in enumerate(MOUNTS, 1):
        tags = ('port gun', 'starboard gun') if heavy else ('gun',)
        for tag in tags:
            brake = bpy.data.objects.get(f'Turret {i} {tag} muzzle brake')
            if brake is None:
                continue
            rot = brake.matrix_world.to_3x3()
            axis = (rot @ Vector((1, 0, 0))).normalized()
            # The brake mesh is a box along its local X; its mouth is the +X face.
            half = max(abs(Vector(c).x) for c in brake.bound_box) * rot.col[0].length
            muzzle = brake.matrix_world.translation + axis * half
            ob = place(f'SWFX anchor turret {i} {tag}', coll, brake, muzzle, axis, .8 * s)
            a.guns.append(GunAnchor(i, tag, ob, s, heavy))

    # --- lamps -----------------------------------------------------------
    for prefix, role, color in LAMPS:
        for part in named(prefix):
            at = bbox_centre(part)
            side = 'port' if at.y > 0 else 'starboard'
            c = color or (RED if side == 'port' else GREEN)
            r = f'{role}-{side}' if role == 'running' else role
            ob = place(f'SWFX anchor lamp {part.name}', coll, part, at, Vector((1, 0, 0)), .4)
            a.lamps.append(LampAnchor(part.name, r, ob, c, part))
    return a


def make_ship_root(name='SW ship root'):
    """Parent every unparented ship part to one empty at the hull origin, so a
    shot can move the whole ship (and with it every anchored effect)."""
    root = bpy.data.objects.get(name)
    if root is None:
        root = bpy.data.objects.new(name, None)
        bpy.context.scene.collection.objects.link(root)
        root.empty_display_type = 'PLAIN_AXES'
        root.empty_display_size = 20
    for ob in ship_objects():
        if ob.parent is None:
            mw = ob.matrix_world.copy()
            ob.parent = root
            ob.matrix_parent_inverse = root.matrix_world.inverted()
            ob.matrix_world = mw
    return root
