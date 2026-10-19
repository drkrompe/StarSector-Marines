"""The command deck's interior: Second Wind's bridge (owner brief, 2026-10-18).

The exterior is sw_bridge's: a low armoured superstructure with slit glazing about 55 cm
tall (z 18.6 to 19.45) behind heavy mullions, under a visor brow and an armoured roof. Its
shell is only about 2.2 m tall, so the room is sunk into the waist housing below: the floor
is at z 16.6 and the ceiling under the roof at 20.3, about 2.4 m of headroom. That puts the
slits at standing eye height, about 1.3 m above the floor: this is a standing bridge.

The front of the room is a raised flight dais (z 17.55, about 60 cm up), because the housing's
solid top runs forward under the bulkhead's raked skirt and the hull pinches in below it: a
room floor that far forward would break out of the hull. The pilot sits on the dais at a low
console with eyes in the slit band (the owner calls it a crow's nest); standing crew work
from the main floor behind a rail along the dais edge, with the step up to port of it.

Layout, a compact version of the ship-interiors bridge (central plot, inward-facing banks):
- the flight stations on the dais: one low console run across the forward face and both
  chamfers, with the one flight seat (the helm) to starboard; the port side of the dais is clear,
  where a crewman steps up to lean over the console and look out (C2's forward station);
- the comms and sensor bank along the port side (Castle's station, the one C2 turns to);
- the systems bank along the starboard side;
- a small plot table aft of centre and the standing rail along the dais edge;
- the hatch in the aft wall; lamps, an overhead panel and a cable tray on the ceiling.

The room is cut out of the exterior solids by one hidden cutter, as live Boolean modifiers
named 'Bridge interior cavity', so the exterior is unchanged (every outer face keeps its
position and normal; only buried faces where two exterior parts meet are re-tessellated, which
shifts their z-fighting in a Workbench diff) and damage Booleans layered on
later cut the same evaluated shells. Cut faces keep each object's own materials; the room's
paint is a separate lining skin just inside them, open across the slit band.

dress(kit, state) adds the condition dressing after damage: the jury-rigged details of the
escape in 'patched', the same plus dead displays and an open panel in 'derelict', tidier
cable runs in 'refit', nothing in 'restored' or 'clean'. Names are identifiers, as on the
exterior: 'Bridge ...' for the base room, 'Bridge jury-rig ...' for state dressing,
'Bridge display ...' for every screen (the damage pass darkens them when the power is off).
"""
import math
from pathlib import Path

import bpy
import sw_kit
from sw_bridge import plan

FLOOR, SILL, HEADER, CEILING = 16.6, 18.6, 19.45, 20.3
CONSOLE_TOP = 18.25                        # about 1.07 m above the floor: a standing console
# The housing's solid top runs forward under the bulkhead's raked skirt (from x 15.9 at its top,
# z 17.5, and the hull pinches in below the skirt), so the room is two levels: the main floor
# aft of KNEE, and the flight dais forward of it at STEP, standing on the housing, about 60 cm
# up. The pilots sit on the dais at low consoles; standing crew work from the main floor behind
# a rail. Both plans share every vertex but the two forward ones, and the lower plan's forward
# chamfer lies on the upper plan's, so the walls run straight up.
STEP, KNEE = 17.55, 15.85
FRONT = 17.72                              # forward wall; the hull reaches about 17.76 at STEP
KNEE_CF = KNEE - (FRONT - 1.95)            # keeps the two chamfers collinear
CAVITY = plan(9.15, FRONT, 4.65, 1.95, 0.75)
CUT = ('Command deck housing', 'Command deck lower bulkhead', 'Viewport sill ledge',
       'Command deck visor brow', 'Command deck armoured roof')
GLAZING = 'Command deck viewport slits'

_mats = {}


def mats():
    if not _mats:
        m = sw_kit.material
        _mats.update(
            lining=m('Bridge interior lining', (.15, .16, .145), .15, .7),
            deck=m('Bridge deck plating', (.06, .062, .06), .6, .55),
            casing=m('Bridge console casing', (.10, .108, .112), .4, .5),
            face=m('Bridge console face', (.03, .034, .038), .3, .4),
            display=m('Bridge display', (.02, .035, .035), 0, .3, emission=(.22, .62, .5)),
            amber=m('Bridge annunciator', (.05, .03, .01), 0, .4, emission=(.9, .45, .12)),
            dead=m('Bridge display unpowered', (.015, .018, .02), 0, .25),
            seat=m('Bridge seat padding', (.08, .055, .04), 0, .8),
            lamp=m('Bridge ceiling lamp', (.8, .76, .62), 0, .5, emission=(1.0, .86, .62)),
            primer=m('Bridge jury-rig primer', (.30, .12, .05), .2, .7),
            salvage=m('Bridge jury-rig salvaged casing', (.20, .19, .15), .3, .55),
            cable=m('Bridge jury-rig cable', (.025, .025, .028), 0, .6),
            tape=m('Bridge jury-rig tape', (.55, .48, .12), 0, .8),
        )
    return _mats


def prism(kit, name, poly, z0, z1, mat, bevel=0):
    n = len(poly)
    verts = [(x, y, z0) for x, y in poly] + [(x, y, z1) for x, y in poly]
    faces = [tuple(reversed(range(n)))] + [(i, (i + 1) % n, n + (i + 1) % n, n + i) for i in range(n)] \
        + [tuple(range(n, 2 * n))]
    return kit.mesh(name, verts, faces, mat, bevel=bevel)


def stepped_rings(inset=0.0):
    """The room's two plans, inset by `inset`: (knee ring of 8, upper ring of 10). The upper ring
    carries the knee ring's two forward corners as extra points on its chamfers, at indices 2
    and 5; every other knee vertex is the same tuple in both (KNEE_IN_UPPER)."""
    lo = plan(9.15 + inset, KNEE - inset, 4.65 - inset, KNEE_CF, .75)
    hi = plan(9.15 + inset, FRONT - inset, 4.65 - inset, 1.95, .75)   # chamfer collinear with lo's
    return lo, lo[:3] + hi[2:4] + lo[3:]


KNEE_IN_UPPER = (0, 1, 2, 5, 6, 7, 8, 9)


def stepped_solid(kit, name, inset, z0, z1, mat, open_band=None):
    """The stepped room as one manifold solid (or, with open_band=(za, zb), a skin of its walls,
    floor and ceiling left open and the band za..zb left out, for the lining)."""
    lo, hi = stepped_rings(inset)
    up = KNEE_IN_UPPER
    verts, faces = [], []

    def ring(pts, z):
        base = len(verts)
        verts.extend((x, y, z) for x, y in pts)
        return list(range(base, base + len(pts)))

    def band(a, b):                                # quads joining two rings of equal length
        n = len(a)
        faces.extend((a[i], a[(i + 1) % n], b[(i + 1) % n], b[i]) for i in range(n))

    if open_band is None:
        bot = ring(lo, z0)
        mid = ring(hi, STEP)
        top = ring(hi, z1)
        faces.append(tuple(reversed(bot)))
        band(bot, [mid[k] for k in up])
        faces.append((mid[2], mid[3], mid[4], mid[5]))     # the step's underside, facing down
        band(mid, top)
        faces.append(tuple(top))
    else:
        za, zb = open_band
        bot = ring(lo, z0)
        mid = ring(hi, STEP)
        band(bot, [mid[k] for k in up])
        faces.append((mid[2], mid[3], mid[4], mid[5]))
        band(mid, ring(hi, za))
        band(ring(hi, zb), ring(hi, z1))
    ob = kit.mesh(name, verts, faces, mat)
    import bmesh
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    if open_band is not None:                      # a skin is seen from inside: face it inward
        bmesh.ops.reverse_faces(bm, faces=bm.faces)
    bm.to_mesh(ob.data)
    bm.free()
    return ob


def clip_x(poly, xmax):
    """The part of a convex polygon with x <= xmax (Sutherland-Hodgman, one edge)."""
    out = []
    for i, p in enumerate(poly):
        q = poly[(i + 1) % len(poly)]
        pin, qin = p[0] <= xmax, q[0] <= xmax
        if pin:
            out.append(p)
        if pin != qin:
            t = (xmax - p[0]) / (q[0] - p[0])
            out.append((xmax, p[1] + (q[1] - p[1]) * t))
    return out


def tilted(kit, name, loc, dims, mat, tilt_deg, yaw_deg=0.0, bevel=.04):
    """A box tipped back by tilt_deg about its own Y axis, then turned by yaw_deg."""
    ob = kit.box(name, loc, dims, mat, bevel)
    ob.rotation_euler = (0, math.radians(tilt_deg), math.radians(yaw_deg))
    return ob


def bank(kit, name, centre, length, depth, yaw_deg, screens):
    """A console run: casing to CONSOLE_TOP, a sloped face toward the operator, screens on it.
    centre is the casing's footprint centre; yaw_deg turns its length axis (0 = along X);
    the operator stands on the casing's -Y side before the turn."""
    M = mats()
    cx, cy = centre
    yaw = math.radians(yaw_deg)
    c, s = math.cos(yaw), math.sin(yaw)
    foot = [(cx + c * u - s * w, cy + s * u + c * w)
            for u, w in ((-length / 2, -depth / 2), (length / 2, -depth / 2),
                         (length / 2, depth / 2), (-length / 2, depth / 2))]
    if max(x for x, _ in foot) <= KNEE - .03:
        body = prism(kit, name, foot, FLOOR, CONSOLE_TOP, M['casing'], .05)
    else:
        # Reaches over the knee wall: the console stands on the step, with a base under the
        # part of it that is over the floor.
        body = prism(kit, name, foot, STEP, CONSOLE_TOP, M['casing'], .05)
        base = clip_x(foot, KNEE - .03)
        if len(base) >= 3:
            prism(kit, name + ' base', base, FLOOR, STEP, M['casing'], .05)
    # The operator's side is -Y in the bank's own frame; the sloped face sits on the top.
    ox, oy = math.sin(yaw), -math.cos(yaw)              # unit vector toward the operator
    face = kit.box(name + ' face', (cx + ox * .05, cy + oy * .05, CONSOLE_TOP + .12),
                   (length - .1, depth * .92, .12), M['face'], .03)
    face.rotation_euler = (math.radians(16), 0, yaw)   # high edge away from the operator
    for k, (u, w) in enumerate(screens):
        ux, uy = math.cos(yaw), math.sin(yaw)
        sx, sy = cx + ux * u + ox * .08, cy + uy * u + oy * .08
        s = kit.box('Bridge display %s %d' % (name.split('Bridge ', 1)[1], k + 1), (sx, sy, CONSOLE_TOP + .2),
                    (w, depth * .55, .02), M['display'], 0)
        s.rotation_euler = (math.radians(16), 0, yaw)
    return body


def seat(kit, name, x, y, facing_deg, base=FLOOR, pan_h=1.18):
    """A seat on a pedestal: a high saddle with a foot ring on the main floor, or a low flight
    seat on the dais (pan_h above `base`)."""
    M = mats()
    kit.cylinder(name + ' pedestal', (x, y, base + (pan_h - .08) / 2), .16, pan_h - .08, kit.steel, vertices=16, bevel=.02)
    pan = kit.box(name + ' pan', (x, y, base + pan_h), (.8, .8, .16), M['seat'], .06)
    yaw = math.radians(facing_deg)
    bx, by = x - .42 * math.cos(yaw), y - .42 * math.sin(yaw)
    back = kit.box(name + ' back', (bx, by, base + pan_h + .57), (.14, .78, 1.0), M['seat'], .05)
    back.rotation_euler[2] = yaw
    pan.rotation_euler[2] = yaw
    if base == FLOOR:
        ring_x, ring_y = x + .35 * math.cos(yaw), y + .35 * math.sin(yaw)
        kit.cylinder(name + ' foot ring', (ring_x, ring_y, FLOOR + .55), .35, .06, kit.steel, vertices=20, bevel=0)


FLIGHT_SEAT_X, FLIGHT_SEAT_PAN = 16.3, .5      # pan about 35 cm up: a seated eye mid-slit (z ~19.35)


def build(kit):
    """The bridge room, cut into the command deck, and its base fittings."""
    M = mats()
    cutter = stepped_solid(kit, 'Bridge interior cavity', 0, FLOOR, CEILING, M['lining'])
    cutter.display_type = 'WIRE'
    cutter.hide_render = True
    for ob in bpy.data.objects:
        base = ob.name.split('.')[0]
        if ob.type != 'MESH' or (base not in CUT and base != GLAZING):
            continue
        mod = ob.modifiers.new('Bridge interior cavity', 'BOOLEAN')
        mod.operation, mod.solver, mod.object = 'DIFFERENCE', 'EXACT', cutter
        # Cut faces keep the object's own materials: transferring the cutter's lining also
        # repainted outward faces of the housing. The lining is its own skin, below.
        mod.material_mode = 'INDEX'
    cutter.hide_viewport = True

    prism(kit, 'Bridge deck plating', stepped_rings(.05)[0], FLOOR, FLOOR + .08, M['deck'])
    lo, hi = stepped_rings(.05)
    prism(kit, 'Bridge dais plating', hi[2:6], STEP, STEP + .06, M['deck'])
    kit.box('Bridge dais step', (KNEE - .25, 1.65, FLOOR + .24), (.5, 1.4, .48), M['casing'], .03)
    # The room's painted lining, a hair inside the cut faces: knee wall, step, the walls below
    # and above the slits and the ceiling, open across the slit band so the glass stays clear.
    stepped_solid(kit, 'Bridge lining', .02, FLOOR, CEILING - .02, M['lining'],
                  open_band=(SILL - .02, HEADER + .02))
    ceiling = stepped_rings(.02)[1]
    kit.mesh('Bridge lining ceiling', [(x, y, CEILING - .02) for x, y in ceiling], [tuple(reversed(range(len(ceiling))))],
             M['lining'])

    # Flight stations: the forward run and both chamfer runs, screens facing aft.
    bank(kit, 'Bridge forward console', (17.43, 0), 5.5, .5, -90, [(-2.0, .9), (-.7, .9), (.7, .9), (2.0, .9)])
    for s, side in ((1, 'port'), (-1, 'starboard')):
        bank(kit, 'Bridge %s chamfer console' % side, (16.45, s * 3.36), 2.7, .9, -90 + s * 45, [(0, 1.0)])
    seat(kit, 'Bridge helm seat', FLIGHT_SEAT_X, -1.7, 0, STEP, FLIGHT_SEAT_PAN)

    # Side banks: comms and sensors to port (Castle's), systems to starboard.
    bank(kit, 'Bridge port comms bank', (12.75, 4.2), 5.0, .9, 0, [(-1.6, .9), (0, 1.1), (1.6, .9)])
    bank(kit, 'Bridge starboard systems bank', (12.75, -4.2), 5.0, .9, 180, [(-1.6, .9), (0, 1.1), (1.6, .9)])
    for k, x in enumerate((11.3, 14.1)):
        kit.box('Bridge port annunciator %d' % (k + 1), (x, 4.48, CONSOLE_TOP + .45), (.5, .06, .12), M['amber'], 0)

    # Plot table aft of centre; the standing rail along the dais edge.
    kit.cylinder('Bridge plot table', (11.9, 0, FLOOR + .66), 1.05, 1.32, M['casing'], vertices=24, bevel=.04)
    kit.cylinder('Bridge display plot', (11.9, 0, FLOOR + 1.335), .9, .02, M['display'], vertices=24, bevel=0)
    # The rail runs from the starboard chamfer to the centreline; the step up is in the gap to
    # port, where the dais is clear.
    kit.cylinder('Bridge standing rail', (15.6, -.85, FLOOR + 1.55), .07, 3.3, kit.steel, axis='Y', vertices=12, bevel=0)
    for y in (-2.45, -.85, .75):
        kit.cylinder('Bridge standing rail post', (15.6, y, FLOOR + .78), .06, 1.55, kit.steel, vertices=12, bevel=0)

    # Aft hatch in the aft wall, onto the spine.
    kit.box('Bridge aft hatch frame', (9.22, 0, FLOOR + 1.55), (.16, 2.0, 3.1), kit.steel, .05)
    kit.box('Bridge aft hatch leaf', (9.3, 0, FLOOR + 1.5), (.08, 1.5, 2.7), kit.dark, .04)
    kit.box('Bridge aft hatch wheel', (9.37, 0, FLOOR + 1.6), (.06, .55, .55), kit.steel, .1)

    # Ceiling: two lamps, the overhead panel over the flight seats, a cable tray down the centre.
    for y in (-1.6, 1.6):
        kit.box('Bridge ceiling lamp', (13.2, y, CEILING - .06), (2.4, .32, .1), M['lamp'], .02)
    kit.box('Bridge overhead panel', (16.5, 0, CEILING - .18), (1.0, 4.0, .34), M['casing'], .04)
    kit.box('Bridge display overhead', (16.5, 0, CEILING - .36), (.7, 2.6, .02), M['amber'], 0)
    kit.box('Bridge cable tray', (12.6, 0, CEILING - .1), (6.4, .5, .12), kit.steel, .02)


def dress(kit, state):
    """Condition dressing, layered after damage (see the module docstring)."""
    if state in ('clean', 'restored'):
        return
    M = mats()
    powered = state != 'derelict'
    # In 'derelict' sw_damage's ship-power override has already darkened every emissive part
    # of the room; the dressing added here follows it.
    # The escape crew's salvaged display, strapped to the forward console beside the helm.
    disp = kit.box('Bridge jury-rig salvaged display', (17.45, -2.75, CONSOLE_TOP + .4), (.2, 1.1, .6),
                   M['salvage'], .04)
    disp.rotation_euler = (0, math.radians(-12), 0)
    scr = kit.box('Bridge display salvaged', (17.34, -2.75, CONSOLE_TOP + .42), (.02, .9, .46),
                  M['display'] if powered else M['dead'], 0)
    scr.rotation_euler = (0, math.radians(-12), 0)
    for z in (CONSOLE_TOP + .22, CONSOLE_TOP + .6):
        kit.box('Bridge jury-rig strap', (17.45, -2.75, z), (.26, 1.18, .05), M['tape'], 0)
    if state in ('patched', 'derelict'):
        # Loose cable runs taped across the ceiling and down the port wall to the comms bank.
        runs = [((10.0, -1.0, CEILING - .12), (17.0, -1.0, CEILING - .12)),
                ((10.4, 1.1, CEILING - .16), (16.0, 1.4, CEILING - .2)),
                ((10.6, 1.12, CEILING - .16), (10.6, 4.45, CEILING - .25)),
                ((10.6, 4.45, CEILING - .25), (10.6, 4.45, CONSOLE_TOP + .5))]
        for k, (a, b) in enumerate(runs):
            mid = [(p + q) / 2 for p, q in zip(a, b)]
            vec = [q - p for p, q in zip(a, b)]
            length = math.sqrt(sum(c * c for c in vec))
            c = kit.cylinder('Bridge jury-rig cable run %d' % (k + 1), mid, .06, length, M['cable'], vertices=8, bevel=0)
            c.rotation_euler = (0, math.atan2(math.hypot(vec[0], vec[1]), vec[2]), math.atan2(vec[1], vec[0]))
            for u in (.25, .6, .9):
                kit.box('Bridge jury-rig tape', [p + (q - p) * u for p, q in zip(a, b)], (.13, .13, .13), M['tape'], 0)
        # A replaced face panel in primer on the systems bank, and a clamp lamp on the rail.
        kit.box('Bridge jury-rig replacement panel', (13.6, -3.72, FLOOR + .9), (1.4, .04, 1.0), M['primer'], .02)
        kit.cylinder('Bridge jury-rig clamp lamp', (15.6, -1.2, FLOOR + 1.75), .12, .3, M['lamp'] if powered else M['dead'],
                     vertices=12, bevel=0)
    if state == 'derelict':
        # One systems-bank face panel gone: a dark recess where it was.
        kit.box('Bridge jury-rig open panel', (11.6, -3.72, FLOOR + .9), (1.2, .05, .9), kit.dark, 0)
    if state == 'refit':
        kit.box('Bridge jury-rig cable loom', (12.6, 1.2, CEILING - .14), (6.0, .3, .1), M['cable'], .02)


# ---------------------------------------------------------------- figures (never built into the ship)
# Simple 1.8 m mannequins for review renders and for shots on the bridge (C2): the pilot seated
# at the helm, and Castle standing at the port comms bank. Like every hull's scale figure they
# are added by the caller and never saved into a geometry .blend. Castle is the MakeHuman figure
# from castle-figure/ (build_castle.py) when its .blend has been built, posed for this spot,
# and her mannequin otherwise.

CASTLE_BLEND = Path(__file__).resolve().parents[1] / 'castle-figure' / 'castle-figure.blend'


def castle_figure(name, at, facing_deg, metres_per_unit, parent=None, blend=CASTLE_BLEND):
    """Append the 'Castle figure' collection under an empty named `name`; None if not built.

    The figure is in metres facing its root's +X, so the empty takes the mannequin's place,
    turn and the metres-to-units scale."""
    blend = Path(blend)
    if not blend.exists():
        return None
    with bpy.data.libraries.load(str(blend), link=False) as (src, dst):
        dst.collections = ['Castle figure']
    col = dst.collections[0]
    holder = bpy.data.objects.new(name, None)
    bpy.context.collection.objects.link(holder)
    holder.parent = parent
    holder.location = at
    holder.rotation_euler[2] = math.radians(facing_deg)
    holder.scale = (1.0 / metres_per_unit,) * 3
    for ob in col.all_objects:
        bpy.context.collection.objects.link(ob)
        if ob.name.startswith('Castle figure root'):
            ob.parent = holder
    bpy.data.collections.remove(col)
    return holder

def _limb(name, a, b, r, mat, parent):
    import mathutils
    a, b = mathutils.Vector(a), mathutils.Vector(b)
    d = b - a
    bpy.ops.mesh.primitive_cylinder_add(vertices=10, radius=r, depth=d.length, location=(a + b) / 2)
    ob = bpy.context.object
    ob.name = name
    ob.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    ob.data.materials.append(mat)
    ob.parent = parent
    return ob


def _ball(name, c, r, mat, parent):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=16, ring_count=8, radius=r, location=c)
    ob = bpy.context.object
    ob.name = name
    ob.data.materials.append(mat)
    ob.parent = parent
    return ob


def figures(metres_per_unit, parent=None, castle=True):
    """Build 'Bridge figure pilot' and 'Bridge figure Castle' in hull units; returns their roots.

    castle=True uses the MakeHuman Castle when castle-figure.blend exists (else the mannequin);
    False always uses the mannequin."""
    u = 1.0 / metres_per_unit
    suit = sw_kit.material('Bridge figure flight suit', (.11, .12, .10), 0, .8)
    skin = sw_kit.material('Bridge figure skin', (.33, .23, .18), 0, .6)
    roots = []

    def person(name, at, facing_deg, pose):
        root = bpy.data.objects.new(name, None)
        bpy.context.collection.objects.link(root)
        root.parent = parent
        root.location = at
        root.rotation_euler[2] = math.radians(facing_deg)
        P = {k: tuple(c * u for c in v) for k, v in pose.items()}       # metres -> units, local
        for side in ('l', 'r'):
            _limb(name + ' thigh ' + side, P['hip_' + side], P['knee_' + side], .075 * u, suit, root)
            _limb(name + ' shin ' + side, P['knee_' + side], P['ankle_' + side], .06 * u, suit, root)
            _limb(name + ' upper arm ' + side, P['shoulder_' + side], P['elbow_' + side], .05 * u, suit, root)
            _limb(name + ' forearm ' + side, P['elbow_' + side], P['hand_' + side], .042 * u, suit, root)
            _ball(name + ' hand ' + side, P['hand_' + side], .045 * u, skin, root)
        _limb(name + ' torso', P['pelvis'], P['neck'], .17 * u, suit, root)
        _ball(name + ' head', P['head'], .105 * u, skin, root)
        roots.append(root)

    # Local frame: +X is where the figure faces, +Y its left, z up from its feet (metres).
    standing = dict(pelvis=(0, 0, .95), neck=(0, 0, 1.5), head=(.02, 0, 1.66),
                    hip_l=(0, .1, .92), hip_r=(0, -.1, .92), knee_l=(.02, .1, .5), knee_r=(.02, -.1, .5),
                    ankle_l=(0, .11, .08), ankle_r=(-.05, -.11, .08),
                    shoulder_l=(0, .2, 1.43), shoulder_r=(0, -.2, 1.43),
                    elbow_l=(.2, .24, 1.18), elbow_r=(.12, -.24, 1.15),
                    hand_l=(.42, .2, 1.08), hand_r=(.08, -.16, 1.62))     # right hand at the headset
    seated = dict(pelvis=(0, 0, .86), neck=(.06, 0, 1.42), head=(.1, 0, 1.58),
                  hip_l=(0, .1, .84), hip_r=(0, -.1, .84), knee_l=(.44, .12, .84), knee_r=(.44, -.12, .84),
                  ankle_l=(.42, .12, .38), ankle_r=(.46, -.12, .38),
                  shoulder_l=(.05, .2, 1.36), shoulder_r=(.05, -.2, 1.36),
                  elbow_l=(.3, .23, 1.15), elbow_r=(.3, -.23, 1.15),
                  hand_l=(.55, .18, 1.12), hand_r=(.55, -.18, 1.12))
    m = metres_per_unit
    # Hips on the helm seat's pan, on the dais; feet on the dais.
    person('Bridge figure pilot', (FLIGHT_SEAT_X - .05, -1.7, STEP + FLIGHT_SEAT_PAN + .08 - .84 / m), 0, seated)
    real = castle and castle_figure('Bridge figure Castle', (10.9, 3.25, FLOOR), 90, m, parent)
    if real:
        roots.append(real)
    else:
        person('Bridge figure Castle', (10.9, 3.25, FLOOR), 90, standing)
    return roots
