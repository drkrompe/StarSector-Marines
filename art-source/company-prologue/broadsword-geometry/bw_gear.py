"""Broadsword landing gear (pass 3, owner 2026-10-19: "get some gear on it").

Retractable tricycle gear, so the fighter can stand on a flight deck and
still fly clean past a bridge. A nose leg under the forward fuselage and a
main leg under each nacelle, each an oleo strut, piston, torque link, drag
brace, axle and a pair of wheels. Every leg folds aft about a Y-axis hinge
into the solid above it (the fuselage or the nacelle), and a pair of doors
under it closes over the well.

The legs are built down. Each leg's parts are parented to the empty
"Hull <leg> hinge"; each door to "Hull <leg> door <side> hinge". The empties
carry how to retract them as custom properties (open_axis, retract_deg for
the legs, close_deg for the doors). retract(t) poses them all: 0 is gear
down, 1 is gear up; the cinematic export drives the same rotations from
its control empty's gear_down knob.
"""
import math
import bpy
from mathutils import Matrix, Vector

# Parked deck, level in the hull frame: about a metre under the nacelles.
DECK_Z = -5.2
# (leg, hinge x, hinge y, hinge z, wheel radius, door half width, door length aft of the hinge)
LEGS = [('nose gear', 9.6, 0.0, -1.55, .48, .68, 4.3),
        ('port main gear', -5.6, 5.5, -2.45, .6, .8, 3.7),
        ('starboard main gear', -5.6, -5.5, -2.45, .6, .8, 3.7)]
RETRACT_DEG = 90.0   # every leg swings aft: +Y rotation carries its foot toward -X
DOOR_DEG = 88.0      # doors open hanging nearly straight down on their outboard edges
FEET = [f'Hull {leg} wheel {s}' for leg, *_ in LEGS for s in ('port', 'starboard')]


def rod(kit, name, p0, p1, r, mat, sides=10):
    """A round rod between two points: struts, braces, links."""
    a, b = Vector(p0), Vector(p1)
    ob = kit.cylinder(name, (0, 0, 0), r, (b-a).length, mat, 'Z', sides, 0)
    ob.matrix_world = Matrix.Translation((a+b)/2) @ (b-a).to_track_quat('Z', 'Y').to_matrix().to_4x4()
    return ob


def belly_z(kit, x, y):
    """Lowest hull skin under (x, y): a ray cast up from below."""
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    best = None
    for ob in kit.hull:
        ev = ob.evaluated_get(dg)
        inv = ev.matrix_world.inverted()
        hit, p, _, _ = ev.ray_cast(inv @ Vector((x, y, -40)), (inv.to_3x3() @ Vector((0, 0, 1))).normalized())
        if hit:
            z = (ev.matrix_world @ p).z
            best = z if best is None else min(best, z)
    if best is None:
        raise RuntimeError(f'no belly under ({x}, {y})')
    return best


def hinge(name, loc, **props):
    ob = bpy.data.objects.new(name, None)
    ob.empty_display_type = 'SINGLE_ARROW'
    ob.empty_display_size = 1.0
    ob.location = loc
    bpy.context.collection.objects.link(ob)
    for k, v in props.items():
        ob[k] = v
    return ob


def adopt(parent, objs):
    bpy.context.view_layer.update()
    inv = parent.matrix_world.inverted()
    for ob in objs:
        mw = ob.matrix_world.copy()
        ob.parent = parent
        ob.matrix_parent_inverse = inv
        ob.matrix_world = mw


def door(kit, name, x0, x1, y_in, y_out, mat, gap=.02, t=.07):
    """A thin door following the belly, from the centre line of the well out to its hinge edge."""
    xs = (x0, (x0+x1)/2, x1)
    ys = (y_in, y_out)
    z = {(x, y): belly_z(kit, x, y)-gap for x in xs for y in ys}
    verts = []
    for x in xs:
        for y in ys:
            verts += [(x, y, z[x, y]), (x, y, z[x, y]-t)]
    # index: 4*i (x) + 2*j (y) + k (outer/inner skin)
    v = lambda i, j, k: 4*i+2*j+k
    faces = []
    for i in range(2):
        faces += [(v(i, 0, 1), v(i, 1, 1), v(i+1, 1, 1), v(i+1, 0, 1)),   # outer skin
                  (v(i, 0, 0), v(i+1, 0, 0), v(i+1, 1, 0), v(i, 1, 0)),   # inner skin
                  (v(i, 0, 0), v(i, 0, 1), v(i+1, 0, 1), v(i+1, 0, 0)),
                  (v(i, 1, 0), v(i+1, 1, 0), v(i+1, 1, 1), v(i, 1, 1))]
    faces += [(v(0, 0, 0), v(0, 1, 0), v(0, 1, 1), v(0, 0, 1)), (v(2, 0, 0), v(2, 0, 1), v(2, 1, 1), v(2, 1, 0))]
    return kit.mesh(name, verts, faces, mat, .02)


def build(kit):
    S, B, D = kit.steel, kit.bright, kit.dark
    for leg, x, y, top, r, half, run in LEGS:
        n = f'Hull {leg}'
        axle = DECK_Z+r
        parts = [
            rod(kit, f'{n} strut', (x, y, top+.2), (x, y, axle+r*1.9), .3, S, 12),
            rod(kit, f'{n} gland', (x, y, axle+r*1.95), (x, y, axle+r*1.65), .35, D, 12),
            rod(kit, f'{n} piston', (x, y, axle+r*1.8), (x, y, axle+.05), .19, B, 12),
            rod(kit, f'{n} drag brace', (x-run*.55, y, top+.15), (x, y, axle+r*2.4), .12, S, 8),
            rod(kit, f'{n} torque link', (x+.38, y, axle+r*1.75), (x+.38, y, axle+r*.75), .07, B, 6),
            rod(kit, f'{n} axle', (x, y-.72, axle), (x, y+.72, axle), .1, B, 8),
            kit.cylinder(f'{n} trunnion', (x, y, top+.1), .24, 1.0, S, 'Y', 12, .02),
        ]
        for side, s in (('port', 1), ('starboard', -1)):
            parts.append(kit.cylinder(f'{n} wheel {side}', (x, y+s*.45, axle), r, .38, D, 'Y', 24, .05))
            parts.append(kit.cylinder(f'{n} wheel {side} hub', (x, y+s*.47, axle), r*.48, .42, B, 'Y', 12, .02))
        h = hinge(f'{n} hinge', (x, y, top), open_axis='Y', retract_deg=RETRACT_DEG,
                  note='parts built down; rotate +retract_deg about local Y to stow (foot swings aft)')
        adopt(h, parts)
        # The well: a dark liner just under the skin, seen only with the doors open.
        x0, x1 = x+.5, x-run-.1
        door(kit, f'{n} well', x0-.08, x1+.08, y-half+.06, y+half-.06, D, gap=.004, t=.01)
        # Doors: a pair over the well, each hinged on its outboard edge, built open.
        for side, s in (('port', 1), ('starboard', -1)):
            d = door(kit, f'{n} door {side}', x0, x1, y, y+s*half, S)
            hz = belly_z(kit, (x0+x1)/2, y+s*half)-.02
            dh = hinge(f'{n} door {side} hinge', ((x0+x1)/2, y+s*half, hz), open_axis='X', open_deg=s*DOOR_DEG,
                       note='built closed; rotate open_deg about local X to open (it hangs from its outboard edge)')
            adopt(dh, [d])
            dh.rotation_euler[0] = math.radians(dh['open_deg'])


def pose(gear_down):
    """0 = up (stowed, doors shut), 1 = down. Rotations only; nothing else moves."""
    t = max(0.0, min(1.0, gear_down))
    for leg, *_ in LEGS:
        n = f'Hull {leg}'
        h = bpy.data.objects[f'{n} hinge']
        h.rotation_euler[1] = math.radians(h['retract_deg'])*(1-t)
        for side in ('port', 'starboard'):
            dh = bpy.data.objects[f'{n} door {side} hinge']
            # doors lead the legs out and close after them
            dh.rotation_euler[0] = math.radians(dh['open_deg'])*min(1.0, t*3)
    bpy.context.view_layer.update()
