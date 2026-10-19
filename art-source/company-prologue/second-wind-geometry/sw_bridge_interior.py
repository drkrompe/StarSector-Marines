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

import bmesh
import bpy
from mathutils import Euler, Matrix, Vector
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
            hardware=m('Bridge console hardware', (.24, .25, .26), .7, .35),
            label=m('Bridge label plate', (.52, .51, .46), .1, .5),
            knob=m('Bridge console knob', (.012, .012, .014), 0, .35),
            status=m('Bridge status lamp', (.02, .05, .02), 0, .4, emission=(.25, .85, .3)),
            webbing=m('Bridge harness webbing', (.17, .16, .10), 0, .9),
            gasket=m('Bridge hatch gasket', (.01, .01, .01), 0, .8),
            grille=m('Bridge vent grille', (.03, .033, .035), .5, .5),
            seam=m('Bridge lining seam', (.035, .037, .034), 0, .8),
            tread=m('Bridge tread plate', (.2, .21, .2), .7, .4),
            nosing=m('Bridge safety nosing', (.55, .42, .06), 0, .6),
            loom=m('Bridge cable loom', (.04, .042, .045), 0, .6),
            scuff=m('Bridge jury-rig wear', (.36, .37, .36), .8, .3),
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


class Parts:
    """Many small solids gathered into one mesh object: switch rows, bolt heads, cages, webbing.
    One object per group keeps the room's object count sane while every piece stays a real solid
    that a close camera can see the edges of. Rotations are 3x3 matrices (or None)."""

    def __init__(self):
        self.bm = bmesh.new()

    def box(self, at, dims, rot=None):
        bmesh.ops.create_cube(self.bm, size=1, matrix=Matrix.LocRotScale(Vector(at), rot, Vector(dims)))
        return self

    def cyl(self, at, r, depth, rot=None, segments=12, r2=None):
        """A cylinder (or a cone frustum with r2) along the rotated Z axis."""
        bmesh.ops.create_cone(self.bm, cap_ends=True, cap_tris=False, segments=segments, radius1=r,
                              radius2=r if r2 is None else r2, depth=depth,
                              matrix=Matrix.LocRotScale(Vector(at), rot, None))
        return self

    def tube(self, points, r, sides=8, closed=False):
        """A round tube swept through points (parallel-transported rings, capped unless closed)."""
        pts = [Vector(p) for p in points]
        n, rings, normal = len(pts), [], None
        for i, p in enumerate(pts):
            if closed:
                a, b = pts[i - 1], pts[(i + 1) % n]
            else:
                a, b = pts[max(i - 1, 0)], pts[min(i + 1, n - 1)]
            t = (b - a).normalized()
            if normal is None:
                ref = Vector((0, 0, 1)) if abs(t.z) < .9 else Vector((1, 0, 0))
                normal = t.cross(ref).normalized()
            else:
                normal = (normal - t * normal.dot(t)).normalized()
            bi = t.cross(normal)
            rings.append([self.bm.verts.new(p + r * (math.cos(k * math.tau / sides) * normal
                                                     + math.sin(k * math.tau / sides) * bi))
                          for k in range(sides)])
        for i in (range(n) if closed else range(n - 1)):
            A, B = rings[i], rings[(i + 1) % n]
            for k in range(sides):
                self.bm.faces.new((A[k], A[(k + 1) % sides], B[(k + 1) % sides], B[k]))
        if not closed:
            self.bm.faces.new(rings[0])
            self.bm.faces.new(list(reversed(rings[-1])))
        return self

    def ring(self, centre, axis, radius, r, sides=6, segments=24):
        """A torus-like closed tube of the given radius around `axis` through `centre`."""
        axis = Vector(axis).normalized()
        u = axis.cross(Vector((0, 0, 1)) if abs(axis.z) < .9 else Vector((1, 0, 0))).normalized()
        v = axis.cross(u)
        c = Vector(centre)
        return self.tube([c + radius * (math.cos(k * math.tau / segments) * u + math.sin(k * math.tau / segments) * v)
                          for k in range(segments)], r, sides, closed=True)

    def strap(self, a, b, width, thick, side):
        """A flat strip from a to b, its width along `side` (made square to the strip)."""
        a, b, side = Vector(a), Vector(b), Vector(side)
        t = (b - a).normalized()
        s = (side - t * t.dot(side)).normalized()
        return self.box((a + b) / 2, ((b - a).length, width, thick), Matrix((t, s, t.cross(s))).transposed())

    def build(self, kit, name, mat, smooth=False, bevel=0):
        if not self.bm.verts:
            self.bm.free()
            return None
        bmesh.ops.recalc_face_normals(self.bm, faces=self.bm.faces)
        me = bpy.data.meshes.new(name)
        self.bm.to_mesh(me)
        self.bm.free()
        ob = bpy.data.objects.new(name, me)
        bpy.context.collection.objects.link(ob)
        for poly in me.polygons:
            poly.use_smooth = smooth
        return kit.finish(ob, name, mat, bevel)


def rot(x=0.0, y=0.0, z=0.0):
    """Rotation matrix for Blender's XYZ Euler, in degrees."""
    return Euler((math.radians(x), math.radians(y), math.radians(z))).to_matrix()


def hung(points, droop=.06):
    """A cable hung through `points`: straight between them in plan, each span sagging by up to
    `droop` (less on a short span). A span that is mostly vertical hangs straight."""
    out = []
    for a, b in zip(points, points[1:]):
        a, b = Vector(a), Vector(b)
        d = b - a
        sag = 0 if abs(d.z) > d.length * .7 else droop * min(1.0, d.length / 1.2)
        for k in range(10):
            t = k / 10
            p = a.lerp(b, t)
            p.z -= 4 * sag * t * (1 - t)
            out.append(p)
    out.append(Vector(points[-1]))
    return out


def console_face(kit, name, centre, length, depth, yaw, screens):
    """Close-up detail on a bank's sloped face: a bezel round every screen, label plates, a switch
    row along the operator's edge, indicator lamps along the far edge, and knobs or a keypad in the
    gaps between screens. Everything is placed in the face's own tilted frame."""
    M = mats()
    cx, cy = centre
    ox, oy = math.sin(yaw), -math.cos(yaw)
    R = Euler((math.radians(16), 0, yaw)).to_matrix()
    Fc = Vector((cx + ox * .05, cy + oy * .05, CONSOLE_TOP + .12))

    def at(u, w, h):                                       # h above the face's top surface
        return Fc + R @ Vector((u, w, .06 + h))

    Lf, Df, sd, b = (length - .1) / 2, depth * .92 / 2, depth * .55, .035
    bezels, labels, switches, knobs, keys, amber, green = (Parts() for _ in range(7))
    taken, ws = [], 0.0
    for u, sw in screens:
        S = Vector((cx + math.cos(yaw) * u + ox * .08, cy + math.sin(yaw) * u + oy * .08, CONSOLE_TOP + .2))
        loc = R.transposed() @ (S - Fc)
        ws, top = loc.y, loc.z - .06 + .01 + .008           # bezel stands just proud of the glass
        for du, dw, lu, lw in ((0, sd / 2 + b / 2, sw + 2 * b, b), (0, -sd / 2 - b / 2, sw + 2 * b, b),
                               (sw / 2 + b / 2, 0, b, sd), (-sw / 2 - b / 2, 0, b, sd)):
            bezels.box(at(loc.x + du, ws + dw, top / 2), (lu, lw, top), R)
        taken.append((loc.x - sw / 2 - b, loc.x + sw / 2 + b))
        labels.box(at(loc.x, ws + sd / 2 + b + .03, .003), (min(.5, sw * .5), .034, .006), R)

    # Operator's edge: switches in groups of five, each group with its label plate nearer the edge.
    near_lo, near_hi = -Df, ws - sd / 2 - b
    roomy = near_hi - near_lo >= .09
    w_sw = near_lo + (.072 if roomy else (near_hi - near_lo) / 2)
    pitch, size = (.05, 1.0) if roomy else (.042, .8)
    u0, k = -Lf + .08, 0
    while u0 + 4 * pitch <= Lf - .08:
        for j in range(5):
            u = u0 + j * pitch
            switches.box(at(u, w_sw, .006), (.034 * size, .03 * size, .012), R)
            tip = R @ rot(x=22 if (k * 5 + j * 3) % 7 < 3 else -22)
            switches.box(at(u, w_sw, .012) + tip @ Vector((0, 0, .022 * size)), (.01, .01, .045 * size), tip)
        if roomy:
            labels.box(at(u0 + 2 * pitch, near_lo + .025, .003), (4 * pitch + .03, .026, .005), R)
        u0 += 5 * pitch + .12
        k += 1

    # Far edge: a row of indicator lamps, one in four amber.
    far_lo = ws + sd / 2 + b
    if Df - far_lo >= .1:
        for j, u in enumerate(x / 100 for x in range(int((-Lf + .08) * 100), int((Lf - .08) * 100), 9)):
            (amber if j % 4 == 1 else green).cyl(at(u, Df - .035, .006), .014, .012, R, 10)

    # Gaps between screens: a keypad where there is room, knobs otherwise.
    edges = [-Lf + .04] + [e for span in sorted(taken) for e in span] + [Lf - .04]
    for a, c in zip(edges[::2], edges[1::2]):
        W, m = c - a, (a + c) / 2
        knob_us = []
        if W >= .4:
            ku = m - .1 if W >= .6 else m
            bezels.box(at(ku, ws, .004), (.2, .26, .008), R)
            for col in (-1, 0, 1):
                for row in (-1.5, -.5, .5, 1.5):
                    keys.box(at(ku + col * .055, ws + row * .055, .016), (.042, .042, .018), R)
            if W >= .6:
                knob_us = [m + .2]
        elif W >= .1:
            knob_us = [m]
        for ku in knob_us:
            r = .04 if W >= .2 else .028
            for dw in ((-sd / 4, sd / 4) if W >= .2 else (0,)):
                bezels.cyl(at(ku, ws + dw, .005), r + .012, .01, R, 16)
                knobs.cyl(at(ku, ws + dw, .026), r * .75, .034, R, 14)
                labels.box(at(ku, ws + dw + r * .4, .044), (.008, r * .7, .004), R)
    bezels.build(kit, name + ' bezels', M['hardware'])
    labels.build(kit, name + ' labels', M['label'])
    switches.build(kit, name + ' switches', M['hardware'])
    knobs.build(kit, name + ' knobs', M['knob'], smooth=True)
    keys.build(kit, name + ' keypad', M['label'])
    amber.build(kit, name + ' warning lamps', M['amber'])
    green.build(kit, name + ' status lamps', M['status'])


def console_casing(kit, name, centre, length, depth, yaw, z0, glands=()):
    """The operator side of a bank's casing: access-panel seams with screw heads, a kick plate, and
    cable glands where looms come up out of the deck plating into the console."""
    M = mats()
    cx, cy = centre
    Rz = rot(z=math.degrees(yaw))
    screw_rot = Rz @ rot(x=90)                              # a cylinder's axis out of the face

    def at(u, z, e):                                        # e out from the casing face
        return Vector((cx, cy, z)) + Rz @ Vector((u, -depth / 2 - e, 0))

    deck = z0 + (.08 if z0 == FLOOR else .06)
    seams, screws, kick, gland, loom = (Parts() for _ in range(5))
    panels = max(2, round(length / 1.2))
    pw = length / panels
    tall = CONSOLE_TOP - .08 - (deck + .12)
    for k in range(1, panels):
        seams.box(at(-length / 2 + k * pw, deck + .12 + tall / 2, .003), (.014, .01, tall), Rz)
    for z in (deck + .11, CONSOLE_TOP - .08):
        seams.box(at(0, z, .003), (length - .14, .01, .014), Rz)
    for k in range(panels):
        for du in (.08, pw - .08):
            for z in (deck + .2, CONSOLE_TOP - .15):
                screws.cyl(at(-length / 2 + k * pw + du, z, .004), .014, .01, screw_rot, 8)
    if z0 == FLOOR:
        # Not on the dais: a kick plate just above the dais plating catches a snap ray of the
        # spine strike cluster's insert on the skirt below (sw_damage casts through the room),
        # which moves that insert on the outside of the refit and restored ship.
        kick.box(at(0, deck + .05, .006), (length - .1, .014, .1), Rz)
    for u in glands:
        z = deck + .17
        gland.cyl(at(u, z, .015), .05, .03, screw_rot, 6)
        gland.cyl(at(u, z, .045), .036, .04, screw_rot, 12)
        gland.cyl(at(u, deck + .008, .19), .065, .016, None, 16)        # floor grommet
        loom.tube([at(u, z, .05), at(u, z, .1), at(u, z - .03, .15), at(u, z - .08, .18),
                   at(u, deck + .03, .19), at(u, deck - .01, .19)], .028, 10)
    seams.build(kit, name + ' seams', M['seam'])
    screws.build(kit, name + ' screws', M['hardware'])
    kick.build(kit, name + ' kick plate', kit.dark)
    gland.build(kit, name + ' cable glands', M['hardware'])
    loom.build(kit, name + ' loom', M['loom'], smooth=True)


def bank(kit, name, centre, length, depth, yaw_deg, screens, glands=()):
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
        z0 = FLOOR
    else:
        # Reaches over the knee wall: the console stands on the step, with a base under the
        # part of it that is over the floor.
        body = prism(kit, name, foot, STEP, CONSOLE_TOP, M['casing'], .05)
        z0 = STEP
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
    console_face(kit, name, centre, length, depth, yaw, screens)
    if z0 == FLOOR or glands:                          # the chamfer runs' faces are mostly base
        console_casing(kit, name, centre, length, depth, yaw, z0, glands)
    return body


def seat(kit, name, x, y, facing_deg, base=FLOOR, pan_h=1.18):
    """A seat on a swivel pedestal: a high saddle with a foot ring on the main floor, or a low
    flight seat on the dais (pan_h above `base`). Steel frame and reclined back, cushions with
    side bolsters, headrest, armrests, and a five-point harness lying unbuckled on the cushions."""
    M = mats()
    yaw = math.radians(facing_deg)
    Rz = rot(z=facing_deg)
    O = Vector((x, y, base))

    def L(u, v, z):
        return O + Rz @ Vector((u, v, z))

    kit.cylinder(name + ' pedestal', (x, y, base + (pan_h - .08) / 2), .16, pan_h - .08, kit.steel, vertices=16, bevel=.02)
    Rb = Rz @ rot(y=-8)                                     # the back, reclined 8 degrees
    P = L(-.42, 0, pan_h + .02)

    def B(a, v, s):
        return P + Rb @ Vector((a, v, s))

    frame, pads, harness, metal = Parts(), Parts(), Parts(), Parts()
    frame.cyl(L(0, 0, .075), .3, .03, Rz, 24)                          # deck flange
    for k in range(6):
        a = k * math.tau / 6 + .3
        metal.cyl(L(.24 * math.cos(a), .24 * math.sin(a), .1), .025, .022, Rz, 6)
    frame.cyl(L(0, 0, pan_h - .12), .2, .06, Rz, 20)                    # swivel head
    for v in (-.12, .12):
        frame.box(L(0, v, pan_h - .075), (.7, .05, .03), Rz)           # slide rails
    frame.box(L(0, 0, pan_h - .045), (.82, .8, .04), Rz)               # pan tray
    for v in (-.4, .4):
        frame.box(L(0, v, pan_h - .02), (.8, .04, .07), Rz)            # side rails
        frame.box(B(-.1, v * .9, .55), (.05, .05, 1.1), Rb)            # back uprights
    frame.box(B(-.085, 0, .55), (.025, .72, 1.05), Rb)                 # back shell
    for s in (.04, 1.08):
        frame.box(B(-.1, 0, s), (.05, .76, .05), Rb)
    for v in (-.12, .12):
        frame.box(B(-.05, v, 1.17), (.025, .025, .16), Rb)             # headrest posts
    ha = pan_h + .3
    for v in (-.44, .44):
        frame.box(L(.2, v, (pan_h - .02 + ha) / 2), (.04, .04, ha - pan_h + .02), Rz)    # arm post
        frame.strap(B(-.08, v, .28), L(.22, v, ha), .04, .04, Rz @ Vector((0, 1, 0)))     # arm bar
        pads.box(L(-.05, v, ha + .035), (.5, .08, .05), Rz)                                 # arm pad
        pads.box(L(.02, v * .84, pan_h + .07), (.7, .08, .12), Rz)                          # pan bolster
        pads.box(B(.03, v * .82, .5), (.14, .08, .8), Rb)                                   # back bolster
        metal.box(L(-.13, v * .95, pan_h + .03), (.08, .012, .07), Rz)                      # lap anchor
        metal.cyl(L(-.13, v * .95 + (.008 if v > 0 else -.008), pan_h + .03), .014, .01, Rz @ rot(x=90), 6)
    for v in (-.15, .15):
        metal.box(B(.0, v, 1.0), (.14, .09, .03), Rb)                  # shoulder harness guides
    pan = kit.box(name + ' pan', L(.02, 0, pan_h + .03), (.74, .68, .12), M['seat'], .05)
    pan.rotation_euler[2] = yaw
    back = kit.box(name + ' back', B(0, 0, .55), (.12, .66, .92), M['seat'], .05)
    back.rotation_euler = (0, math.radians(-8), yaw)
    head = kit.box(name + ' headrest', B(0, 0, 1.31), (.12, .4, .22), M['seat'], .04)
    head.rotation_euler = (0, math.radians(-8), yaw)
    # The harness, unbuckled: shoulder straps down the back cushion and onto the pan, lap belts
    # over the bolsters, the crotch strap from the front edge; buckle halves where they fell.
    side, along = Rz @ Vector((0, 1, 0)), Rz @ Vector((1, 0, 0))
    top = pan_h + .095
    for v in (-1, 1):
        harness.strap(B(.066, .15 * v, 1.02), B(.066, .14 * v, .1), .07, .008, side)
        harness.strap(B(.066, .14 * v, .1), L(-.2, .15 * v, top), .07, .008, side)
        harness.strap(L(-.2, .15 * v, top), L(.04, .17 * v, top), .07, .008, side)
        metal.box(L(.07, .17 * v, top + .004), (.06, .05, .012), Rz)
        harness.strap(L(-.13, .42 * v, pan_h + .03), L(-.12, .37 * v, pan_h + .135), .06, .008, along)
        harness.strap(L(-.12, .37 * v, pan_h + .135), L(.06, .24 * v, top), .06, .008, side)
    metal.box(L(.1, -.24, top + .006), (.09, .075, .02), Rz)          # buckle
    harness.strap(L(.39, 0, pan_h + .02), L(.3, 0, top), .06, .008, side)
    harness.strap(L(.3, 0, top), L(.14, 0, top), .06, .008, side)
    frame.build(kit, name + ' frame', kit.steel, bevel=.008)
    pads.build(kit, name + ' bolsters', M['seat'], bevel=.025)
    harness.build(kit, name + ' harness', M['webbing'])
    metal.build(kit, name + ' fittings', M['hardware'])
    if base == FLOOR:
        ring_x, ring_y = x + .35 * math.cos(yaw), y + .35 * math.sin(yaw)
        kit.cylinder(name + ' foot ring', (ring_x, ring_y, FLOOR + .55), .35, .06, kit.steel, vertices=20, bevel=0)


FLIGHT_SEAT_X, FLIGHT_SEAT_PAN = 16.3, .5      # pan about 35 cm up: a seated eye mid-slit (z ~19.35)


def ring_band(kit, name, outer, inner, z0, z1, mat):
    """A closed band between two plan rings (outer and inner) from z0 to z1: trim round the room."""
    n = len(outer)
    verts = [(x, y, z) for ring, z in ((outer, z0), (outer, z1), (inner, z0), (inner, z1)) for x, y in ring]
    O0, O1, I0, I1 = (list(range(k * n, (k + 1) * n)) for k in range(4))
    faces = []
    for i in range(n):
        j = (i + 1) % n
        faces += [(O0[i], O0[j], O1[j], O1[i]), (I0[j], I0[i], I1[i], I1[j]),
                  (O1[i], O1[j], I1[j], I1[i]), (O0[j], O0[i], I0[i], I0[j])]
    ob = kit.mesh(name, verts, faces, mat)
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(ob.data)
    bm.free()
    return ob


def grille(parts, slats, centre, normal, side, width, height, slat_count=6):
    """A vent grille on a wall or ceiling: a frame (into parts) and slats behind it (into slats).
    normal points into the room, side runs along the grille's width."""
    c, n, s = Vector(centre), Vector(normal).normalized(), Vector(side).normalized()
    up = n.cross(s)
    R = Matrix((s, up, n)).transposed()
    for dv, lw, lh in ((height / 2, width, .03), (-height / 2, width, .03)):
        parts.box(c + up * dv + n * .015, (lw + .03, lh, .03), R)
    for du in (width / 2, -width / 2):
        parts.box(c + s * du + n * .015, (.03, height, .03), R)
    for k in range(slat_count):
        dv = -height / 2 + (k + .5) * height / slat_count
        slats.box(c + up * dv + n * .01, (width, .012, .03), R @ rot(x=35))


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
    bank(kit, 'Bridge forward console', (17.43, 0), 5.5, .5, -90, [(-2.0, .9), (-.7, .9), (.7, .9), (2.0, .9)],
         glands=(-2.35, 2.35))
    for s, side in ((1, 'port'), (-1, 'starboard')):
        bank(kit, 'Bridge %s chamfer console' % side, (16.45, s * 3.36), 2.7, .9, -90 + s * 45, [(0, 1.0)])
    seat(kit, 'Bridge helm seat', FLIGHT_SEAT_X, -1.7, 0, STEP, FLIGHT_SEAT_PAN)

    # Side banks: comms and sensors to port (Castle's), systems to starboard.
    bank(kit, 'Bridge port comms bank', (12.75, 4.2), 5.0, .9, 0, [(-1.6, .9), (0, 1.1), (1.6, .9)],
         glands=(-2.15, 2.15))
    bank(kit, 'Bridge starboard systems bank', (12.75, -4.2), 5.0, .9, 180, [(-1.6, .9), (0, 1.1), (1.6, .9)],
         glands=(-2.15, 2.15))
    frames = Parts()
    for k, x in enumerate((11.3, 14.1)):
        kit.box('Bridge port annunciator %d' % (k + 1), (x, 4.48, CONSOLE_TOP + .45), (.5, .06, .12), M['amber'], 0)
        for dz, lw, lh in ((.075, .56, .03), (-.075, .56, .03)):
            frames.box((x, 4.44, CONSOLE_TOP + .45 + dz), (lw, .05, lh))
        for dx in (-.265, .265):
            frames.box((x + dx, 4.44, CONSOLE_TOP + .45), (.03, .05, .18))
    frames.build(kit, 'Bridge port annunciator bezels', M['hardware'])

    # A grab bar along the forward console's lip, for whoever leans over it to look out.
    bar = Parts()
    bar.tube([(17.1, y, CONSOLE_TOP - .04) for y in (-2.45, 2.45)], .025, 10)
    for y in (-2.45, -1.2, 0, 1.2, 2.45):
        bar.box((17.15, y, CONSOLE_TOP - .04), (.08, .03, .03))
    bar.build(kit, 'Bridge forward console grab bar', kit.steel, smooth=True)

    # Plot table aft of centre; the standing rail along the dais edge.
    kit.cylinder('Bridge plot table', (11.9, 0, FLOOR + .66), 1.05, 1.32, M['casing'], vertices=24, bevel=.04)
    kit.cylinder('Bridge display plot', (11.9, 0, FLOOR + 1.335), .9, .02, M['display'], vertices=24, bevel=0)
    plot = Parts()
    plot.ring((11.9, 0, FLOOR + 1.345), (0, 0, 1), .925, .025, 6, 48)          # bezel round the plot
    plot.ring((11.9, 0, FLOOR + 1.2), (0, 0, 1), 1.16, .03, 8, 48)             # grab ring
    for k in range(6):
        a = k * math.tau / 6
        plot.box((11.9 + 1.1 * math.cos(a), 1.1 * math.sin(a), FLOOR + 1.2), (.14, .03, .03), rot(z=math.degrees(a)))
    plot.build(kit, 'Bridge plot table fittings', M['hardware'], smooth=True)
    kit.cylinder('Bridge plot table kick ring', (11.9, 0, FLOOR + .14), 1.07, .1, kit.dark, vertices=24, bevel=0)

    # The rail runs from the starboard chamfer to the centreline; the step up is in the gap to
    # port, where the dais is clear.
    kit.cylinder('Bridge standing rail', (15.6, -.85, FLOOR + 1.55), .07, 3.3, kit.steel, axis='Y', vertices=12, bevel=0)
    for y in (-2.45, -.85, .75):
        kit.cylinder('Bridge standing rail post', (15.6, y, FLOOR + .78), .06, 1.55, kit.steel, vertices=12, bevel=0)
    brackets, bolts = Parts(), Parts()
    for y in (-2.45, -.85, .75):
        brackets.cyl((15.6, y, FLOOR + .095), .12, .03, None, 16)              # deck flange
        for a in (45, 135, 225, 315):
            bolts.cyl((15.6 + .09 * math.cos(math.radians(a)), y + .09 * math.sin(math.radians(a)), FLOOR + .115),
                      .018, .014, None, 6)
        brackets.cyl((15.6, y, FLOOR + 1.55), .095, .16, rot(x=90), 12)         # T-collar on the rail
        brackets.box(((15.66 + KNEE - .02) / 2, y, FLOOR + .7), (KNEE - .02 - 15.66, .05, .05))    # wall stay
        brackets.box((KNEE - .025, y, FLOOR + .7), (.01, .16, .16))                                # wall plate
        for dy in (-.05, .05):
            for dz in (-.05, .05):
                bolts.cyl((KNEE - .035, y + dy, FLOOR + .7 + dz), .014, .012, rot(y=90), 6)
    for y in (-2.5, .8):
        brackets.cyl((15.6, y + (.02 if y > 0 else -.02), FLOOR + 1.55), .08, .04, rot(x=90), 12)  # end caps
    brackets.build(kit, 'Bridge standing rail brackets', kit.steel)
    bolts.build(kit, 'Bridge standing rail bolts', M['hardware'])

    # The step up to the dais: tread plate and safety nosing on the step and along the dais edge,
    # and a grab pole beside it.
    tread, lozenge, nosing = Parts(), Parts(), Parts()
    tread.box((KNEE - .25, 1.65, FLOOR + .486), (.46, 1.36, .012))
    tread.box((KNEE + .3, 1.65, STEP + .066), (.6, 1.36, .012))
    for x0, z in ((KNEE - .46, FLOOR + .492), (KNEE + .02, STEP + .072)):
        for i in range(6 if z < STEP else 8):
            for j in range(19):
                lozenge.box((x0 + .035 + i * .07, 1.65 - .63 + j * .07, z + .003), (.05, .012, .006),
                            rot(z=45 if (i + j) % 2 else -45))
    nosing.box((KNEE - .48, 1.65, FLOOR + .48), (.06, 1.42, .03))
    nosing.box((KNEE - .5, 1.65, FLOOR + .4), (.012, 1.4, .12))
    nosing.box((KNEE + .0, 0, STEP + .068), (.1, 8.8, .016))
    tread.build(kit, 'Bridge dais step tread', M['tread'])
    lozenge.build(kit, 'Bridge dais step tread pattern', M['tread'])
    nosing.build(kit, 'Bridge dais nosing', M['nosing'])
    pole = Parts()
    pole.cyl((15.6, 2.65, (FLOOR + .08 + CEILING - .02) / 2), .045, CEILING - .1 - FLOOR, None, 14)
    pole.cyl((15.6, 2.65, FLOOR + .095), .1, .03, None, 16)
    pole.cyl((15.6, 2.65, CEILING - .035), .1, .03, None, 16)
    pole.build(kit, 'Bridge step grab pole', kit.steel, smooth=True)

    # Aft hatch in the aft wall, onto the spine: frame with bolted coaming, leaf on three hinges,
    # a gasket line round the leaf, six dogs and a handwheel; handholds either side.
    kit.box('Bridge aft hatch frame', (9.22, 0, FLOOR + 1.55), (.16, 2.0, 3.1), kit.steel, .05)
    kit.box('Bridge aft hatch leaf', (9.3, 0, FLOOR + 1.5), (.08, 1.5, 2.7), kit.dark, .04)
    hz = FLOOR + 1.6
    wheel, hub = Parts(), Parts()
    wheel.ring((9.42, 0, hz), (1, 0, 0), .26, .025, 8, 32)
    wheel.build(kit, 'Bridge aft hatch wheel', kit.steel, smooth=True)
    hub.cyl((9.38, 0, hz), .06, .1, rot(y=90), 16)
    for k in range(4):
        a = k * math.tau / 4 + math.pi / 4
        hub.strap((9.41, 0, hz), (9.42, .25 * math.cos(a), hz + .25 * math.sin(a)), .03, .02, (1, 0, 0))
    gasket, hinges, dogs, coaming, leafwork = Parts(), Parts(), Parts(), Parts(), Parts()
    for dz, lw, lh in ((1.375, 1.6, .05), (-1.375, 1.6, .05)):
        gasket.box((9.315, 0, FLOOR + 1.5 + dz), (.03, lw, lh))
    for dy in (.775, -.775):
        gasket.box((9.315, dy, FLOOR + 1.5), (.03, .05, 2.8))
    for z in (FLOOR + .45, FLOOR + 1.5, FLOOR + 2.55):
        hinges.cyl((9.38, .8, z), .05, .3, None, 12)
        hinges.box((9.355, .62, z), (.02, .34, .22))                     # leaf strap
        hinges.box((9.32, .9, z), (.06, .16, .22))                       # frame strap
    for y, z, horizontal in ((-.72, FLOOR + .55, False), (-.72, FLOOR + 1.5, False), (-.72, FLOOR + 2.45, False),
                             (0, FLOOR + 2.8, True), (0, FLOOR + .2, True), (.55, FLOOR + 2.8, True)):
        dogs.cyl((9.36, y, z), .045, .05, rot(y=90), 12)
        lever = rot(x=30 if horizontal else -60)
        dogs.box(Vector((9.4, y, z)) + lever @ Vector((0, 0, .1)), (.03, .045, .2), lever)
        dogs.box((9.33, y - .08 if not horizontal else y, z - .08 if horizontal else z), (.04, .06, .06))
    for y in (-.9, .9):
        for k in range(9):
            coaming.cyl((9.305, y, FLOOR + .25 + k * .33), .02, .016, rot(y=90), 6)
    for z in (FLOOR + 3.03,):
        for k in range(6):
            coaming.cyl((9.305, -.75 + k * .3, z), .02, .016, rot(y=90), 6)
    for z in (FLOOR + .8, FLOOR + 2.3):
        leafwork.box((9.35, 0, z), (.03, 1.3, .07))                     # stiffeners
    leafwork.box((9.35, 0, FLOOR + 2.0), (.008, .5, .12))                # label plate
    hub.build(kit, 'Bridge aft hatch wheel hub', kit.steel)
    gasket.build(kit, 'Bridge aft hatch gasket', M['gasket'])
    hinges.build(kit, 'Bridge aft hatch hinges', kit.steel, bevel=.008)
    dogs.build(kit, 'Bridge aft hatch dogs', kit.steel, bevel=.006)
    coaming.build(kit, 'Bridge aft hatch coaming bolts', M['hardware'])
    leafwork.build(kit, 'Bridge aft hatch stiffeners', kit.dark, bevel=.01)
    holds = Parts()
    for y in (-1.3, 1.3):
        holds.tube([(9.17, y, 17.35), (9.27, y, 17.38), (9.32, y, 17.48), (9.32, y, 18.27),
                    (9.27, y, 18.37), (9.17, y, 18.4)], .028, 10)
        for z in (17.36, 18.39):
            holds.box((9.18, y, z), (.02, .1, .1))
    holds.build(kit, 'Bridge aft handholds', kit.steel, smooth=True)

    # Ceiling: two lamps in caged housings, the overhead panel over the flight seat, a cable tray
    # down the centre, transverse frames, panel seams and vents.
    housing, cage = Parts(), Parts()
    for y in (-1.6, 1.6):
        kit.box('Bridge ceiling lamp', (13.2, y, CEILING - .06), (2.4, .32, .1), M['lamp'], .02)
        for dy in (-.19, .19):
            housing.box((13.2, y + dy, CEILING - .09), (2.5, .04, .14))
        for dx in (-1.23, 1.23):
            housing.box((13.2 + dx, y, CEILING - .09), (.04, .42, .14))
        for dy in (-.12, 0, .12):
            cage.box((13.2, y + dy, CEILING - .155), (2.44, .014, .014))
        for k in range(9):
            x = 13.2 - 1.2 + k * .3
            cage.strap((x, y - .19, CEILING - .13), (x, y - .1, CEILING - .158), .014, .014, (1, 0, 0))
            cage.box((x, y, CEILING - .16), (.014, .21, .014))
            cage.strap((x, y + .1, CEILING - .158), (x, y + .19, CEILING - .13), .014, .014, (1, 0, 0))
    housing.build(kit, 'Bridge ceiling lamp housings', kit.steel, bevel=.006)
    cage.build(kit, 'Bridge ceiling lamp cages', M['hardware'])
    kit.box('Bridge overhead panel', (16.5, 0, CEILING - .18), (1.0, 4.0, .34), M['casing'], .04)
    kit.box('Bridge display overhead', (16.5, 0, CEILING - .36), (.7, 2.6, .02), M['amber'], 0)
    over, over_sw, over_lamps = Parts(), Parts(), Parts()
    oz = CEILING - .35                                                  # the panel's underside
    for dx, lw, lh in ((.37, .04, 2.68), (-.37, .04, 2.68)):
        over.box((16.5 + dx, 0, oz - .012), (lw, lh, .03))
    for dy in (1.32, -1.32):
        over.box((16.5, dy, oz - .012), (.7, .04, .03))
    for x in (16.06, 16.94):
        for k in range(30):
            y = -1.8 + k * .124
            over_sw.box((x, y, oz - .006), (.03, .034, .012))
            over_sw.box((x + (.012 if k % 3 else -.012), y, oz - .03), (.01, .01, .045), rot(y=25 if k % 3 else -25))
    for y in (-1.66, 1.66):
        for dx in (-.2, 0, .2):
            over.cyl((16.5 + dx, y, oz - .005), .05, .01, None, 16)
            over_sw.cyl((16.5 + dx, y, oz - .025), .032, .035, None, 14)
        for dx in (-.3, -.1, .1, .3):
            over_lamps.cyl((16.5 + dx, y + (.2 if y > 0 else -.2), oz - .005), .014, .012, None, 10)
    over.build(kit, 'Bridge overhead panel bezels', M['hardware'])
    over_sw.build(kit, 'Bridge overhead panel switches', M['hardware'])
    over_lamps.build(kit, 'Bridge overhead panel lamps', M['status'])
    kit.box('Bridge cable tray', (12.6, 0, CEILING - .1), (6.4, .5, .12), kit.steel, .02)
    tray = Parts()
    for x in (9.5, 15.7):
        tray.box((x, 0, CEILING - .1), (.03, .54, .15))
    tray.cyl((15.82, 0, CEILING - .12), .05, .04, rot(y=90), 6)       # loom gland into the panel run
    tray.build(kit, 'Bridge cable tray fittings', M['hardware'])
    loom = Parts()
    loom.tube([(15.8, 0, CEILING - .12), (15.9, 0, CEILING - .12), (15.97, .1, CEILING - .12), (16.0, .2, CEILING - .12)],
              .04, 10)
    loom.build(kit, 'Bridge overhead loom', M['loom'], smooth=True)

    beams, ribs, seams = Parts(), Parts(), Parts()
    wall = 4.65 - .02
    for x in (10.6, 11.7, 14.9):
        beams.box((x, 0, CEILING - .06), (.12, 2 * wall - .02, .08))
        for s in (-1, 1):
            ribs.box((x, s * (wall - .03), (HEADER + .03 + CEILING - .02) / 2), (.12, .06, CEILING - .02 - HEADER - .03))
    for y in (-2.6, -.9, .9, 2.6):
        seams.box((12.85, y, CEILING - .022), (6.3, .015, .006))
    for s in (-1, 1):                                       # side walls above the header
        seams.box((12.6, s * (wall - .002), 19.9), (5.2, .006, .015))
    for y in (-3.4, -2.2, 2.2, 3.4):                       # aft wall, below the sill and above the header
        seams.box((9.15 + .02 + .002, y, (FLOOR + .1 + SILL - .1) / 2), (.006, .015, SILL - FLOOR - .2))
    for z in (17.6, 20.15):
        for s in (-1, 1):
            seams.box((9.172, s * 2.6, z), (.006, 2.4, .015))
    for y in (-3.0, -1.0, 1.0, 3.0):                       # knee riser
        seams.box((KNEE - .022, y, (FLOOR + .1 + STEP - .05) / 2), (.006, .015, STEP - FLOOR - .15))
    beams.build(kit, 'Bridge ceiling frames', M['casing'], bevel=.01)
    ribs.build(kit, 'Bridge wall ribs', M['casing'], bevel=.01)
    seams.build(kit, 'Bridge lining seams', M['seam'])
    # Trim along the sill and header: a ledge under the glass and a lip over it.
    outer, inner = stepped_rings(.02)[1], stepped_rings(.07)[1]
    ring_band(kit, 'Bridge sill trim', outer, inner, SILL - .08, SILL - .005, kit.steel)
    ring_band(kit, 'Bridge header trim', outer, inner, HEADER + .025, HEADER + .07, kit.steel)

    vents, slats, roof_vents, roof_slats = Parts(), Parts(), Parts(), Parts()
    for s in (-1, 1):
        grille(vents, slats, (9.17, s * 2.6, 19.9), (1, 0, 0), (0, 1, 0), .8, .28)
        grille(roof_vents, roof_slats, (10.2, s * 2.6, CEILING - .02), (0, 0, -1), (1, 0, 0), .6, .6, 8)
    grille(vents, slats, (KNEE - .02, -1.65, FLOOR + .45), (-1, 0, 0), (0, 1, 0), .8, .3)
    vents.build(kit, 'Bridge vent grille frames', M['hardware'])
    slats.build(kit, 'Bridge vent grilles', M['grille'])
    roof_vents.build(kit, 'Bridge ceiling vent frames', M['hardware'])
    roof_slats.build(kit, 'Bridge ceiling vents', M['grille'])

    # Overhead grab rails along both side walls, on standoff brackets.
    rails = Parts()
    for s in (-1, 1):
        rails.tube([(10.4, s * 4.5, 20.0), (15.2, s * 4.5, 20.0)], .03, 10)
        for x in (10.45, 12.05, 13.65, 15.15):
            rails.box((x, s * (4.5 + wall) / 2, 20.0), (.04, wall - 4.5, .04))
            rails.box((x, s * (wall - .01), 20.0), (.12, .02, .12))
    rails.build(kit, 'Bridge overhead grab rails', kit.steel, smooth=True)


def cable(kit, name, points, r, mat, tapes=(), ties=(), clips=()):
    """A jury-rig cable through `points` (already hung), with tape wraps sized to it at the
    fractions in `tapes`, zip ties at `ties`, and ceiling clips at `clips` (all fractions of the
    run)."""
    M = mats()
    Parts().tube(points, r, 10).build(kit, name, mat, smooth=True)
    n = len(points)

    def frame(f):
        i = min(max(1, round(f * (n - 1))), n - 2)
        return Vector(points[i]), (Vector(points[i + 1]) - Vector(points[i - 1])).normalized()

    for f in tapes:
        p, t = frame(f)
        Parts().tube([p - t * .055, p + t * .055], r * 1.4, 10).build(kit, 'Bridge jury-rig tape', M['tape'], smooth=True)
    tie, clip = Parts(), Parts()
    for f in ties:
        p, t = frame(f)
        tie.ring(p, t, r + .006, .006, 4, 16)
        side = t.cross(Vector((0, 0, 1)))
        side = side.normalized() if side.length > .1 else Vector((1, 0, 0))
        tie.box(p + side * (r + .016), (.02, .02, .02))
    for f in clips:
        p, t = frame(f)
        top = CEILING - .02
        clip.box((p.x, p.y, (p.z + r + top) / 2), (.03, .05, top - p.z - r + .01))
        clip.ring(p, t, r + .01, .009, 4, 16)
        clip.cyl((p.x, p.y, top - .008), .02, .016, None, 6)
    tie.build(kit, name.replace('cable run', 'zip ties'), M['webbing'])
    clip.build(kit, name.replace('cable run', 'cable clips'), M['salvage'])


def dress(kit, state):
    """Condition dressing, layered after damage (see the module docstring)."""
    if state in ('clean', 'restored'):
        return
    M = mats()
    powered = state != 'derelict'
    # In 'derelict' sw_damage's ship-power override has already darkened every emissive part
    # of the room; the dressing added here follows it.
    # The escape crew's salvaged display, strapped to the forward console beside the helm, its
    # casing bolted shut.
    disp = kit.box('Bridge jury-rig salvaged display', (17.45, -2.75, CONSOLE_TOP + .4), (.2, 1.1, .6),
                   M['salvage'], .04)
    disp.rotation_euler = (0, math.radians(-12), 0)
    scr = kit.box('Bridge display salvaged', (17.34, -2.75, CONSOLE_TOP + .42), (.02, .9, .46),
                  M['display'] if powered else M['dead'], 0)
    scr.rotation_euler = (0, math.radians(-12), 0)
    for z in (CONSOLE_TOP + .22, CONSOLE_TOP + .6):
        kit.box('Bridge jury-rig strap', (17.45, -2.75, z), (.26, 1.18, .05), M['tape'], 0)
    Rd = rot(y=-12)
    dc = Vector((17.45, -2.75, CONSOLE_TOP + .4))
    bolts = Parts()
    # (its starboard end is buried in the chamfer wall, so its bolts are on the visible part)
    for y, z in ((.51, -.26), (.51, .26), (.51, 0), (0, -.27), (0, .27), (-.18, -.27), (-.18, .27)):
        bolts.cyl(dc + Rd @ Vector((-.104, y, z)), .02, .014, Rd @ rot(y=90), 6)
    bolts.build(kit, 'Bridge jury-rig display bolts', kit.steel)
    if state in ('patched', 'derelict'):
        # Loose cable runs from a junction box tapped into the aft wall, hung along the ceiling
        # frames and sagging between ties: two forward into the overhead panel, one across the
        # ceiling and down the port mullion into the comms bank's annunciator.
        kit.box('Bridge jury-rig junction box', (9.24, 0, 19.95), (.14, .9, .34), M['salvage'], .02)
        box_bolts = Parts()
        for y in (-.4, .4):
            for z in (19.81, 20.09):
                box_bolts.cyl((9.312, y, z), .018, .012, rot(y=90), 6)
        box_bolts.build(kit, 'Bridge jury-rig junction box bolts', kit.steel)
        frames = (10.6, 11.7, 13.3, 14.9)
        run1 = hung([(9.33, -.3, 19.92), (9.55, -.55, 20.02), (9.85, -.95, 20.1), (10.6, -1.0, 20.14),
                     (11.7, -1.0, 20.14), (13.3, -1.0, 20.14), (14.9, -1.0, 20.14), (15.7, -1.0, 20.13),
                     (15.99, -1.0, 20.12)])
        run2 = hung([(9.33, .3, 19.92), (9.55, .55, 20.02), (9.85, .95, 20.1), (10.6, 1.0, 20.14),
                     (11.7, 1.05, 20.14), (13.3, 1.12, 20.14), (14.9, 1.18, 20.14), (15.7, 1.2, 20.13),
                     (15.99, 1.2, 20.12)])
        run3 = hung([(11.45, 1.04, 20.13), (11.45, 2.2, 20.14), (11.45, 3.3, 20.14), (11.45, 4.05, 20.12),
                     (11.45, 4.25, 20.06), (11.45, 4.33, 19.96)])
        run4 = [Vector(p) for p in ((11.45, 4.33, 19.96), (11.45, 4.34, 19.8), (11.45, 4.32, 19.5), (11.45, 4.31, 19.2),
                                    (11.45, 4.33, 18.98), (11.45, 4.39, 18.9), (11.45, 4.44, 18.84), (11.45, 4.45, 18.8))]

        def at_frames(pts):
            return [min(range(len(pts)), key=lambda i: abs(pts[i].x - x)) / (len(pts) - 1) for x in frames]

        cable(kit, 'Bridge jury-rig cable run 1', run1, .05, M['cable'], tapes=(.12, .47, .8),
              ties=(.3, .62, .9), clips=at_frames(run1))
        cable(kit, 'Bridge jury-rig cable run 2', run2, .05, M['cable'], tapes=(.14, .55, .86),
              ties=(.35, .7), clips=at_frames(run2))
        cable(kit, 'Bridge jury-rig cable run 3', run3, .042, M['cable'], tapes=(.5,), ties=(.25, .75), clips=(.2, .4))
        cable(kit, 'Bridge jury-rig cable run 4', run4, .042, M['cable'], tapes=(.35,), ties=(.15, .6))
        glands = Parts()
        for y in (-1.0, 1.2):
            glands.cyl((15.985, y, 20.12), .075, .03, rot(y=90), 6)       # into the overhead panel
        glands.cyl((11.45, 4.45, 18.815), .06, .03, None, 6)              # into the annunciator's top
        glands.cyl((11.45, 1.04, 20.13), .07, .12, rot(y=90), 8)          # the splice where run 3 leaves run 2
        glands.build(kit, 'Bridge jury-rig glands', M['salvage'])
        # A replaced face panel in primer on the systems bank, bolted on (two bolts never found),
        # and a clamp lamp on the rail.
        kit.box('Bridge jury-rig replacement panel', (13.6, -3.72, FLOOR + .9), (1.4, .04, 1.0), M['primer'], .02)
        pb, holes = Parts(), Parts()
        spots = [(12.96 + k * .182, FLOOR + z) for z in (.46, 1.34) for k in range(8)] \
            + [(x, FLOOR + .9 + dz) for x in (12.96, 14.24) for dz in (-.22, 0, .22)]
        for k, (x, z) in enumerate(spots):
            if k in (5, 19):
                holes.cyl((x, -3.699, z), .016, .004, rot(x=90), 10)
                continue
            pb.cyl((x, -3.698, z), .03, .004, rot(x=90), 12)
            pb.cyl((x, -3.69, z), .022, .014, rot(x=90), 6)
        pb.build(kit, 'Bridge jury-rig panel bolts', kit.steel)
        holes.build(kit, 'Bridge jury-rig panel bolt holes', kit.dark)
        kit.cylinder('Bridge jury-rig clamp lamp', (15.6, -1.2, FLOOR + 1.75), .12, .3, M['lamp'] if powered else M['dead'],
                     vertices=12, bevel=0)
    if state in ('patched', 'derelict', 'refit'):
        # Wear where hands go: bright metal through the paint on the console lips at the helm
        # and Castle's station, the grab bars and rail where they are held, the step pole, the
        # hatch wheel and the step's nosing.
        hz = FLOOR + 1.6
        wear = Parts()
        wear.box((10.9, 3.747, CONSOLE_TOP - .03), (.8, .006, .06))
        wear.box((17.177, -1.7, CONSOLE_TOP - .015), (.006, .7, .05))
        wear.box((17.177, 1.2, CONSOLE_TOP - .015), (.006, .5, .05))
        for y in (-1.7, 1.2):
            wear.tube([(17.1, y - .2, CONSOLE_TOP - .04), (17.1, y + .2, CONSOLE_TOP - .04)], .0275, 10)
        for y in (-1.6, .1):
            wear.tube([(15.6, y - .17, FLOOR + 1.55), (15.6, y + .17, FLOOR + 1.55)], .0725, 12)
        wear.tube([(15.6, 2.65, FLOOR + 1.0), (15.6, 2.65, FLOOR + 1.6)], .0475, 14)
        for a0 in (30, 200):
            wear.tube([(9.42, .26 * math.cos(math.radians(a)), hz + .26 * math.sin(math.radians(a)))
                       for a in range(a0, a0 + 55, 5)], .0275, 8)
        wear.box((KNEE - .48, 1.65, FLOOR + .497), (.064, .5, .004))
        wear.build(kit, 'Bridge jury-rig wear', M['scuff'], smooth=True)
    if state == 'derelict':
        # One systems-bank face panel gone: a dark recess where it was.
        kit.box('Bridge jury-rig open panel', (11.6, -3.72, FLOOR + .9), (1.2, .05, .9), kit.dark, 0)
    if state == 'refit':
        kit.box('Bridge jury-rig cable loom', (12.6, 1.2, CEILING - .14), (6.0, .3, .1), M['cable'], .02)


# ---------------------------------------------------------------- figures (never built into the ship)
# Simple 1.8 m mannequins for review renders and for shots on the bridge (C2): the pilot seated
# at the helm, and Castle standing at the port comms bank. Like every hull's scale figure they
# are added by the caller and never saved into a geometry .blend. Each is the MakeHuman figure
# from its own package when that .blend has been built (castle-figure/build_castle.py,
# pilot-figure/build_pilot.py), and the mannequin otherwise.

CASTLE_BLEND = Path(__file__).resolve().parents[1] / 'castle-figure' / 'castle-figure.blend'
PILOT_BLEND = Path(__file__).resolve().parents[1] / 'pilot-figure' / 'pilot-figure.blend'


def _append_figure(blend, collection, root_name, name, at, facing_deg, metres_per_unit, parent):
    """Append a figure package's collection under an empty named `name`; None if not built.

    A package is in metres facing its root's +X, so the empty takes the mannequin's place,
    turn and the metres-to-units scale."""
    blend = Path(blend)
    if not blend.exists():
        return None
    with bpy.data.libraries.load(str(blend), link=False) as (src, dst):
        dst.collections = [collection]
    col = dst.collections[0]
    holder = bpy.data.objects.new(name, None)
    bpy.context.collection.objects.link(holder)
    holder.parent = parent
    holder.location = at
    holder.rotation_euler[2] = math.radians(facing_deg)
    holder.scale = (1.0 / metres_per_unit,) * 3
    for ob in col.all_objects:
        bpy.context.collection.objects.link(ob)
        if ob.name.startswith(root_name):
            ob.parent = holder
    bpy.data.collections.remove(col)
    return holder


def castle_figure(name, at, facing_deg, metres_per_unit, parent=None, blend=CASTLE_BLEND):
    """Castle standing, her root at her feet."""
    return _append_figure(blend, 'Castle figure', 'Castle figure root', name, at, facing_deg,
                          metres_per_unit, parent)


def pilot_figure(name, at, facing_deg, metres_per_unit, parent=None, blend=PILOT_BLEND):
    """The pilot seated, his root on the dais plating at the helm seat's axis."""
    return _append_figure(blend, 'Pilot figure', 'Pilot figure root', name, at, facing_deg,
                          metres_per_unit, parent)

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


def figures(metres_per_unit, parent=None, castle=True, pilot=True):
    """Build 'Bridge figure pilot' and 'Bridge figure Castle' in hull units; returns their roots.

    castle=True uses the MakeHuman Castle when castle-figure.blend exists, pilot=True the
    MakeHuman pilot when pilot-figure.blend exists (else each is a mannequin); False always
    uses the mannequin."""
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
    # On the helm seat: the MakeHuman pilot's root is on the dais plating at the seat's axis;
    # the mannequin's hips are on the pan, feet on the dais.
    real = pilot and pilot_figure('Bridge figure pilot', (FLIGHT_SEAT_X, -1.7, STEP + .06), 0, m, parent)
    if real:
        roots.append(real)
    else:
        person('Bridge figure pilot', (FLIGHT_SEAT_X - .05, -1.7, STEP + FLIGHT_SEAT_PAN + .08 - .84 / m), 0, seated)
    real = castle and castle_figure('Bridge figure Castle', (10.9, 3.25, FLOOR), 90, m, parent)
    if real:
        roots.append(real)
    else:
        person('Bridge figure Castle', (10.9, 3.25, FLOOR), 90, standing)
    return roots
