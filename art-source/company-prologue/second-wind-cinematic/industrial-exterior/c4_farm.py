"""C4's farmland at work: what the sky pan opens on, before the belt.

`c4x_scene.add_farmland` lays the fields out; this fills them in for the first four seconds of the
shot, seen from about 820 m. Everything is sized to read at that height, about 1.2 pixels a metre:

- field detail: tramlines down the growing crops, headlands, stubble and straw windrows where the
  harvest is done, a field half ploughed, a block of flooded rice paddies fed by an irrigation
  channel off the belt's ditch, a centre-pivot circle, drainage ditches and field-edge tracks;
- three farmsteads with barns, sheds, silos and yard trees;
- machines at work, moving through the shot: two combines cutting a ripe field with a grain cart
  alongside, each leaving its cut swath and a trail of chaff dust; a tractor ploughing, its
  furrows growing behind it; a sprayer with its boom out; a hover crop tender
  spraying; a grain truck at the field edge;
- traffic: farm trucks and pickups on the field tracks, more haulers, tankers and vans on the
  freight road, and a second train.

Static additions are painted with the ground (`ortho_paint.py`, `ortho/tiles-port.json`); anything
animated is left out of that paint and wears its own (`farm_paint.py`). A growing swath is
animated, so it takes its colour from the painted ground of the already-cut part of its own field.

    c4x_scene.build() calls add(c4) once the belt and its traffic exist.
"""
import math
import os
import random
import sys
from pathlib import Path

import bmesh
import bpy
from mathutils import Matrix

HERE = Path(__file__).resolve().parent
HAZE_SKIP = {'Farm dust'}      # blended cards: no haze veil over their empty parts

# Fields are add_farmland's grid, keyed by their south-west corner; each is inset 4 m from its lines.
HARVEST = (-230, -640)       # ripe grain, two combines cutting north up it
PLOUGH = (330, -640)         # stubble going under, a tractor ploughing south
STUBBLE = (610, -640)        # cut, straw in windrows
PADDY = (-230, -400)         # flooded rice paddies by the belt
PIVOT = (-520, -640)         # a centre-pivot circle
SPRAY = (60, -400)           # a sprayer working up it
TENDER = (-780, -400)        # a hover crop tender working across it
SPECIAL = {HARVEST, PLOUGH, STUBBLE, PADDY, PIVOT}

CUT_X = -110.0               # the harvest: west of this is already cut
COMBINE_V = 4.5              # m/s; real combines run 2, but the pan only sees four seconds
PLOUGH_X = 470.0             # the plough: east of this is already turned
PLOUGH_V = 4.0
FARMSTEADS = [(676.0, -588.0, 0.0), (-596.0, -462.0, math.pi / 2), (-166.0, -712.0, 0.0)]


def in_farmyard(x, y, margin=4.0):
    """True inside a farmstead's yard (118 x 86 m, turned with it)."""
    for fx, fy, h in FARMSTEADS:
        u = (x - fx) * math.cos(h) + (y - fy) * math.sin(h)
        v = -(x - fx) * math.sin(h) + (y - fy) * math.cos(h)
        if abs(u) < 59 + margin and abs(v) < 43 + margin:
            return True
    return False


def add(c4):
    """Build the working farmland into c4x_scene's scene. `c4` is that module."""
    m = mats(c4)
    fields(c4, m)
    paddies(c4, m)
    pivot(c4, m)
    edges(c4, m)
    for i, (x, y, h) in enumerate(FARMSTEADS):
        farmstead(c4, m, i, x, y, h)
    machines(c4, m)
    traffic(c4, m)
    if str(HERE) not in sys.path:
        sys.path.insert(0, str(HERE))
    import farm_paint                     # painted machine tops, once accepted
    farm_paint.apply()


# ---------------------------------------------------------------- materials

def mats(c4):
    mat = c4.material
    m = {
        'soil': mat('Tramline soil', (0.26, 0.21, 0.14), 0.95),
        'stubble': mat('Stubble', (0.55, 0.47, 0.30), 0.95),
        'windrow': mat('Straw windrow', (0.70, 0.62, 0.40), 0.95),
        'ploughed': mat('Ploughed soil', (0.17, 0.12, 0.08), 0.95),
        'bund': mat('Paddy bund', (0.30, 0.30, 0.18), 0.95),
        'water': mat('Paddy water', (0.14, 0.19, 0.17), 0.6),      # muddy and stirred: a sheen, never a sun glint
        'rice': mat('Rice', (0.22, 0.38, 0.10), 0.85),
        'ricey': mat('Rice ripening', (0.42, 0.42, 0.16), 0.85),
        'channel': mat('Irrigation water', (0.12, 0.16, 0.16), 0.5),
        'pivot': mat('Pivot crop', (0.17, 0.33, 0.09), 0.9),
        'ring': mat('Pivot wheel track', (0.30, 0.25, 0.17), 0.95),
        'edge': mat('Field-edge track', (0.44, 0.38, 0.28), 0.95),
        'yard': mat('Farmyard', (0.40, 0.35, 0.26), 0.95),
        'concrete': bpy.data.materials['Concrete'],
        'silo': mat('Silo steel', (0.64, 0.66, 0.66), 0.4, 0.6),
        'house': mat('Farmhouse roof', (0.50, 0.24, 0.16), 0.7),
        'bale': mat('Hay bales', (0.66, 0.58, 0.36), 0.9),
        'trees': bpy.data.materials['Tree crowns'],
        'galv': mat('Pivot steel', (0.42, 0.44, 0.45), 0.55, 0.4),
    }
    for k in ('Roof rust', 'Roof grey', 'Roof white', 'Roof green', 'Steel', 'Asphalt', 'Hauler cab',
              'Hauler load', 'Rail wagon', 'Tank white'):
        m[k] = bpy.data.materials[k]
    return m


def field_rect(key):
    o = bpy.data.objects.get('Field %d %d' % key)
    xs = [v.co.x for v in o.data.vertices]
    ys = [v.co.y for v in o.data.vertices]
    return min(xs), max(xs), min(ys), max(ys), o


def strips(c4, name, rects, mat, z):
    """One mesh of flat rectangles (x0, x1, y0, y1)."""
    bm = bmesh.new()
    for x0, x1, y0, y1 in rects:
        bm.faces.new([bm.verts.new(c) for c in ((x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z))])
    return c4.mesh(name, bm, mat)


# ---------------------------------------------------------------- the fields

def fields(c4, m):
    """Tramlines and headlands in the growing crops; stubble, windrows and bales where the harvest is
    in; the ploughed half of the plough field."""
    tram, head = [], {}
    for o in [o for o in bpy.data.objects if o.name.startswith('Field ')]:
        key = tuple(int(v) for v in o.name.split()[1:3])
        mname = o.data.materials[0].name
        if key in SPECIAL or not mname.startswith('Crop ') or mname[5] == '2':
            continue
        x0, x1, y0, y1, _ = field_rect(key)
        along_y = mname.endswith('X')            # bands across x: the rows run up the field
        # Sprayer wheelings every 24 m, stopping short of the headland the machines turn on.
        if along_y:
            x = x0 + 18
            while x < x1 - 10:
                tram.append((x - 1.2, x + 1.2, y0 + 14, y1 - 14))
                x += 24
        else:
            y = y0 + 18
            while y < y1 - 10:
                tram.append((x0 + 14, x1 - 14, y - 1.2, y + 1.2))
                y += 24
        # Headlands: the rows turn round the field's two ends, drilled the other way.
        other = bpy.data.materials.get(mname[:-1] + ('Y' if along_y else 'X'))
        bands = ([(x0, x1, y0, y0 + 12), (x0, x1, y1 - 12, y1)] if along_y
                 else [(x0, x0 + 12, y0, y1), (x1 - 12, x1, y0, y1)])
        head.setdefault(other.name, (other, []))[1].extend(bands)
    for name, (mat, rects) in head.items():
        strips(c4, 'Headlands ' + name, rects, mat, 0.035)
    strips(c4, 'Tramlines', tram, m['soil'], 0.04)
    # The harvest field: cut west of CUT_X, straw lying in windrows behind the earlier passes.
    x0, x1, y0, y1, _ = field_rect(HARVEST)
    strips(c4, 'Harvest stubble', [(x0, CUT_X, y0, y1)], m['stubble'], 0.04)
    rows = [(x - 0.9, x + 0.9, y0 + 8, y1 - 8) for x in [x0 + 10 + 12 * k for k in range(9)]]
    x0s, x1s, y0s, y1s, _ = field_rect(STUBBLE)
    for x in [x0s + 9 + 11 * k for k in range(int((x1s - x0s - 14) / 11))]:
        lo = y0s + 10 if not in_farmyard(x, y0s + 10) else -538.0      # stopping at the farmyard
        rows.append((x - 0.9, x + 0.9, lo, y1s - 10))
    strips(c4, 'Straw windrows', rows, m['windrow'], 0.05)
    strips(c4, 'Stubble field', [(x0s, x1s, y0s, y1s)], m['stubble'], 0.035)
    r = random.Random(71)
    bales = bmesh.new()                      # round bales left along the windrows, waiting for the loader
    for x in [x0s + 9 + 11 * k for k in range(0, 24, 2)]:
        y = y0s + r.uniform(20, 40)
        while y < y1s - 20:
            if not in_farmyard(x, y):
                c4.cylinder(bales, 0.8, 1.2, (x, y, 0), 8)
            y += r.uniform(18, 40)
    c4.mesh('Round bales', bales, m['bale'])
    # The plough field: stubble, the east part already turned into dark furrows.
    x0, x1, y0, y1, _ = field_rect(PLOUGH)
    strips(c4, 'Plough stubble', [(x0, PLOUGH_X, y0, y1)], m['stubble'], 0.04)
    strips(c4, 'Ploughed ground', [(PLOUGH_X, x1, y0, y1)], m['ploughed'], 0.045)


def paddies(c4, m):
    """Flooded rice by the belt: bunded basins at three stages, fed from an irrigation channel."""
    x0, x1, y0, y1, _ = field_rect(PADDY)
    strips(c4, 'Paddy bunds', [(x0, x1, y0, y1)], m['bund'], 0.04)
    chan = 6.0
    strips(c4, 'Irrigation channel', [(x0 + 1, x0 + 1 + chan, y0, y1 + 12),       # down from the ditch
                                      (x0 + 1, x1, y1 - chan - 1, y1 - 1)], m['channel'], 0.05)
    gx0, gy1 = x0 + chan + 4, y1 - chan - 4
    stage = {'water': [], 'rice': [], 'ricey': []}
    r = random.Random(5)

    def cuts(a, b, lo, hi):              # basin edges from a to b, each lo..hi wide, the last takes the rest
        out = [a]
        while b - out[-1] > hi + lo:
            out.append(out[-1] + r.uniform(lo, hi))
        return out + [b]
    xs = cuts(gx0, x1 - 2, 22, 34)
    for xa, xb in zip(xs, xs[1:]):
        ys = cuts(y0 + 2, gy1, 24, 40)                    # each column of basins steps on its own
        for ya, yb in zip(ys, ys[1:]):
            k = r.random()
            s = 'water' if k < 0.3 else ('ricey' if k > 0.8 else 'rice')
            stage[s].append((xa + 1.2, xb - 1.2, ya + 1.2, yb - 1.2))
    strips(c4, 'Paddies flooded', stage['water'], m['water'], 0.05)
    strips(c4, 'Paddies growing', stage['rice'], m['rice'], 0.05)
    strips(c4, 'Paddies ripening', stage['ricey'], m['ricey'], 0.05)


def pivot(c4, m):
    """A centre-pivot circle and its arm, turning slowly through the shot."""
    x0, x1, y0, y1, _ = field_rect(PIVOT)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    rad = min(x1 - x0, y1 - y0) / 2 - 3
    bm = bmesh.new()
    bmesh.ops.create_circle(bm, cap_ends=True, segments=72, radius=rad)
    bmesh.ops.translate(bm, vec=(cx, cy, 0.04), verts=bm.verts)
    c4.mesh('Pivot circle', bm, m['pivot'])
    rings = bmesh.new()
    for rr in [rad * k / 5 for k in range(1, 5)] + [rad - 2]:       # the towers' wheel tracks
        for a in range(64):
            a0, a1 = 2 * math.pi * a / 64, 2 * math.pi * (a + 1) / 64
            p = [(cx + (rr + s) * math.cos(t), cy + (rr + s) * math.sin(t), 0.05) for s, t in
                 ((-0.8, a0), (0.8, a0), (0.8, a1), (-0.8, a1))]
            rings.faces.new([rings.verts.new(c) for c in p])
    c4.mesh('Pivot wheel tracks', rings, m['ring'])
    arm = bmesh.new()
    c4.cylinder(arm, 2.0, 5, (0, 0, 0), 10)                             # the pivot itself
    n = 5
    for k in range(1, n + 1):
        x = (rad - 2) * k / n
        c4.box(arm, (1.4, 3.2, 3.0), (x, 0, 1.5))                       # a tower on its wheels
        c4.strut(arm, ((rad - 2) * (k - 1) / n, 0, 4.5), (x, 0, 4.5), 0.7)   # the span pipe
        c4.strut(arm, ((rad - 2) * (k - 0.5) / n, -1.2, 3.2), ((rad - 2) * (k - 0.5) / n, 1.2, 3.2), 0.3)
    ob = c4.mesh('Pivot arm', arm, m['galv'])
    ob.location = (cx, cy, 0)
    for f, a in ((0, math.radians(200)), (c4.FRAMES - 1, math.radians(200 - 14))):
        ob.rotation_euler = (0, 0, a)
        ob.keyframe_insert('rotation_euler', frame=f)
    linear(ob)


def edges(c4, m):
    """Drainage ditches down two field lines into the belt's ditch, and grass tracks along field
    edges and to the farmsteads."""
    strips(c4, 'Field drains', [(58.5, 61.5, -900, -168), (-1100, 1200, -901.5, -898.5),
                                (328.5, 331.5, -900, -168)], m['channel'], 0.045)
    strips(c4, 'Field-edge tracks', [
        (-1100, 326, -409, -405),              # along the north edge of the fields by the track
        (64, 69, -636, -404),                  # down the east side of the harvest field's drain
        (-606, -601, -636, -500),              # the west farmstead's lane to the track
        (334, 339, -896, -644)], m['edge'], 0.055)


def gable(bm, w, d, wall, ridge, centre, rot):
    """A barn: walls to `wall`, a pitched roof to `ridge`, the ridge along its length w."""
    cx, cy = centre
    c, s = math.cos(rot), math.sin(rot)

    def p(u, v, z):
        return bm.verts.new((cx + u * c - v * s, cy + u * s + v * c, z))
    hw, hd = w / 2, d / 2
    b = [p(-hw, -hd, 0), p(hw, -hd, 0), p(hw, hd, 0), p(-hw, hd, 0)]
    t = [p(-hw, -hd, wall), p(hw, -hd, wall), p(hw, hd, wall), p(-hw, hd, wall)]
    r = [p(-hw, 0, ridge), p(hw, 0, ridge)]
    for a, bb in ((0, 1), (1, 2), (2, 3), (3, 0)):
        bm.faces.new([b[a], b[bb], t[bb], t[a]])
    bm.faces.new([t[0], t[1], r[1], r[0]])
    bm.faces.new([r[0], r[1], t[2], t[3]])
    bm.faces.new([t[1], t[2], r[1]])
    bm.faces.new([t[3], t[0], r[0]])


def farmstead(c4, m, i, x, y, h):
    """A working farm: yard, house, barns, an open machine shed, silos, a water tank, yard trees."""
    r = random.Random(40 + i)

    def at(u, v):
        return (x + u * math.cos(h) - v * math.sin(h), y + u * math.sin(h) + v * math.cos(h))
    yard = bmesh.new()
    c4.box(yard, (118, 86, 0.1), (x, y, 0.05), h)
    c4.mesh('Farmyard %d' % i, yard, m['yard'])
    pad = bmesh.new()
    c4.box(pad, (40, 18, 0.1), (*at(-22, -26), 0.1), h)
    c4.mesh('Farm silo pad %d' % i, pad, m['concrete'])
    roofs = [m['Roof rust'], m['Roof grey'], m['Roof green']]
    bms = [bmesh.new() for _ in roofs]
    gable(bms[i % 3], 44, 22, 6, 11, at(18, 22), h)                          # the big barn
    gable(bms[(i + 1) % 3], 30, 16, 5, 8.5, at(-28, 22), h)                  # the old barn
    shed = bmesh.new()
    c4.box(shed, (40, 14, 0.5), (*at(30, -24), 6.5), h)                     # open machine shed: a roof on posts
    for u in range(-18, 19, 6):
        for v in (-6, 6):
            c4.strut(shed, (*at(30 + u, -24 + v), 0), (*at(30 + u, -24 + v), 6.3), 0.4)
    c4.mesh('Machine shed %d' % i, shed, m['Roof grey'])
    for bm, mat in zip(bms, roofs):
        if len(bm.verts):
            c4.mesh('Barns %d %s' % (i, mat.name), bm, mat)
        else:
            bm.free()
    house = bmesh.new()
    gable(house, 16, 11, 4, 7, at(-40, -4), h + math.pi / 2)
    c4.mesh('Farmhouse %d' % i, house, m['house'])
    silo = bmesh.new()
    for k in range(3 + i % 2):                                              # grain bins in a row
        sx, sy = at(-36 + k * 11, -26)
        c4.cylinder(silo, 4.5, 13, (sx, sy, 0), 20)
        c4.cylinder(silo, 4.7, 3.5, (sx, sy, 13), 20, 0.6)
    tx, ty = at(-12, -26)
    c4.cylinder(silo, 2.6, 10, (tx, ty, 0), 14)                             # the water tank
    c4.mesh('Grain bins %d' % i, silo, m['silo'])
    trees = bmesh.new()
    for _ in range(14):
        u, v = r.uniform(-58, -46), r.uniform(-40, 40)
        rad = r.uniform(3.5, 6.5)
        res = bmesh.ops.create_icosphere(trees, subdivisions=1, radius=rad)
        bmesh.ops.translate(trees, vec=(*at(u, v), rad + 2), verts=res['verts'])
    c4.mesh('Farm trees %d' % i, trees, m['trees'])


# ---------------------------------------------------------------- machines

def linear(ob):
    """Constant speed between keys: a machine working a field does not ease in and out."""
    ad = ob.animation_data
    if ad is None or ad.action is None:
        return
    act = ad.action
    curves = list(getattr(act, 'fcurves', []) or [])
    for layer in getattr(act, 'layers', []):
        for strip in layer.strips:
            for bag in strip.channelbags:
                curves += list(bag.fcurves)
    for c in curves:
        for k in c.keyframe_points:
            k.interpolation = 'LINEAR'


def machine(c4, name, parts, keys, heading, z=0.0):
    """A machine under one root keyed at (seconds, x, y), front toward +x before `heading` turns it.
    A part is (size, centre, material) for a box, (('tube', r, length), ...) for a tank lying along it,
    or (('cyl', r, height), base, ...) for an upright drum. Parts sharing a material are one mesh."""
    root = c4.link(bpy.data.objects.new(name, None))
    by_mat = {}
    for size, centre, mat in parts:
        bm = by_mat.setdefault(mat.name, (bmesh.new(), mat))[0]
        if size[0] == 'tube':
            res = bmesh.ops.create_cone(bm, cap_ends=True, segments=16, radius1=size[1], radius2=size[1],
                                        depth=size[2])
            bmesh.ops.transform(bm, matrix=Matrix.Translation(centre) @ Matrix.Rotation(math.pi / 2, 4, 'Y'),
                                verts=res['verts'])
        elif size[0] == 'cyl':
            c4.cylinder(bm, size[1], size[2], centre, 16)
        else:
            c4.box(bm, size, centre)
    for i, (bm, mat) in enumerate(by_mat.values()):
        c4.mesh('%s %d' % (name, i), bm, mat).parent = root
    for t, x, y in keys:
        root.location = (x, y, z)
        root.keyframe_insert('location', frame=min(c4.FRAMES - 1, round(t * c4.FPS)))
    root.rotation_euler = (0, 0, heading)
    root.keyframe_insert('rotation_euler', frame=0)
    linear(root)
    return root


def wheels(c4, xs, half_track, r, w=0.5):
    return [((r * 2, w, r * 2), (x, s * half_track, r), c4.tyre()) for x in xs for s in (-1, 1)]


def combine_parts(c4, paint, grain):
    return [((2.6, 12.0, 1.1), (5.7, 0, 1.0), paint),          # the header, cutting 12 m
            ((2.0, 3.0, 1.4), (3.7, 0, 1.6), paint),           # feeder house
            ((7.4, 3.6, 3.0), (-0.6, 0, 2.4), paint),          # body
            ((2.2, 2.6, 1.6), (2.3, 0, 4.3), paint),           # the cab
            ((3.4, 3.2, 0.5), (-0.6, 0, 4.15), grain),         # open grain tank, full
            ((2.0, 3.0, 0.6), (-3.4, 0, 4.2), paint)           # engine deck
            ] + wheels(c4, (2.0,), 1.7, 0.9, 0.8) + wheels(c4, (-3.2,), 1.4, 0.55, 0.5)


def tractor_parts(c4, paint, x=0.0):
    return [((6.0, 2.4, 1.2), (x, 0, 1.5), paint),             # chassis and engine
            ((2.2, 2.3, 1.6), (x - 0.8, 0, 2.9), paint)        # the cab
            ] + wheels(c4, (x + 2.0, x - 1.9), 1.25, 0.9, 0.7)


def cart_parts(c4, paint, cart, grain):
    return tractor_parts(c4, paint, 2.9) + [
        ((7.0, 3.0, 2.4), (-4.2, 0, 2.2), cart),                # the grain cart
        ((6.6, 2.6, 0.1), (-4.2, 0, 3.45), grain)] + wheels(c4, (-4.2,), 1.7, 0.8, 0.6)


def plough_parts(c4, paint, steel):
    return tractor_parts(c4, paint, 3.0) + [((6.0, 3.6, 0.7), (-3.4, 0, 0.7), steel)]   # five-furrow plough


def sprayer_parts(c4, paint, tank, boom):
    return tractor_parts(c4, paint, 3.4) + [
        (('tube', 1.2, 5.2), (-2.8, 0, 2.0), tank),             # the trailed tank
        ((0.8, 2.6, 0.6), (-2.8, 0, 0.9), tank),
        ((0.6, 20.0, 0.4), (-5.6, 0, 2.2), boom)] + wheels(c4, (-2.8,), 1.2, 0.7, 0.4)   # boom unfolded


def tender_parts(c4, cream, orange, steel):
    """The hover crop tender (after `managed-land-realism/agriculture_props.py`): a low heavy keel on
    four lift shrouds, a reagent tank on top, spray spars out either side."""
    p = [((16.0, 8.0, 2.4), (0, 0, 0), steel),                 # keel
         ((13.0, 7.0, 2.0), (-0.6, 0, 2.0), cream),            # upper casing
         ((4.0, 4.6, 2.6), (5.2, 0, 3.2), orange),             # control cabin
         (('tube', 2.0, 7.0), (-2.6, 0, 3.6), cream),          # reagent tank
         ((1.0, 26.0, 0.8), (-2.0, 0, 0.3), steel),            # spray spars
         ((1.1, 22.0, 0.15), (-2.0, 0, 0.75), orange)]
    for x in (-5.5, 5.0):
        for s in (-1, 1):
            p.append((('cyl', 2.4, 1.4), (x, s * 6.0, -1.2), orange))
    return p


def grain_truck_parts(c4, paint, box_, grain):
    return [((2.4, 2.5, 2.3), (3.8, 0, 1.9), paint), ((7.0, 2.5, 2.4), (-1.2, 0, 2.0), box_),
            ((6.6, 2.2, 0.1), (-1.2, 0, 3.25), grain)] + wheels(c4, (3.8, -2.4, -3.6), 1.0, 0.5, 0.45)


def pickup_parts(c4, paint):
    return [((5.3, 2.0, 0.8), (0, 0, 0.8), paint), ((2.0, 1.9, 0.7), (0.5, 0, 1.55), paint)
            ] + wheels(c4, (-1.7, 1.7), 0.85, 0.38, 0.3)


def farm_truck_parts(c4, paint, bed, load):
    return [((2.2, 2.4, 2.2), (3.4, 0, 1.8), paint), ((6.5, 2.5, 0.4), (-1.4, 0, 1.2), bed),
            ((6.0, 2.3, 1.6), (-1.4, 0, 2.2), load)] + wheels(c4, (3.4, -2.6), 1.0, 0.5, 0.45)


def hauler_parts(c4, cab, load, trailers=2):
    return [((3.4, 3.0, 3.4), (0, 0, 2.1), cab)] + [((12.5, 3.0, 3.2), (-9 - k * 13.5, 0, 2.4), load)
                                                    for k in range(trailers)]


def tanker_parts(c4, cab, tank):
    return [((3.4, 3.0, 3.4), (0, 0, 2.1), cab)] + [(('tube', 1.4, 12.0), (-9 - k * 13.0, 0, 2.4), tank)
                                                    for k in range(2)]


def box_truck_parts(c4, cab, box_):
    return [((2.4, 2.5, 2.6), (4.3, 0, 1.7), cab), ((8.0, 2.6, 3.2), (-1.2, 0, 2.2), box_)
            ] + wheels(c4, (4.3, -3.4), 1.05, 0.5, 0.45)


def van_parts(c4, paint):
    return [((5.5, 2.1, 2.2), (0, 0, 1.4), paint)] + wheels(c4, (-1.8, 1.8), 0.9, 0.4, 0.3)


def dust_material():
    m = bpy.data.materials.new('Farm dust')
    m.use_nodes = True
    m.surface_render_method = 'BLENDED'
    nt = m.node_tree
    p = nt.nodes['Principled BSDF']
    p.inputs['Base Color'].default_value = (0.60, 0.50, 0.32, 1)     # chaff and field dust
    p.inputs['Roughness'].default_value = 1.0
    _soft_alpha(nt, p, 0.45)
    return m


def _soft_alpha(nt, p, peak):
    """Boiling noise thinned toward the rim, as the landing dust does it."""
    coord = nt.nodes.new('ShaderNodeTexCoord')
    noise = nt.nodes.new('ShaderNodeTexNoise')
    noise.noise_dimensions = '4D'
    noise.inputs['Scale'].default_value = 1.6
    noise.inputs['Detail'].default_value = 4.0
    d = noise.inputs['W'].driver_add('default_value').driver
    d.type = 'SCRIPTED'
    d.expression = 'frame / 40'
    soft = nt.nodes.new('ShaderNodeMapRange')
    soft.inputs['From Min'].default_value = 0.35
    soft.inputs['From Max'].default_value = 0.7
    soft.inputs['To Max'].default_value = peak
    edge = nt.nodes.new('ShaderNodeLayerWeight')
    rim = nt.nodes.new('ShaderNodeMath')
    rim.operation = 'SUBTRACT'
    rim.inputs[0].default_value = 1.0
    mul = nt.nodes.new('ShaderNodeMath')
    mul.operation = 'MULTIPLY'
    nt.links.new(coord.outputs['Object'], noise.inputs['Vector'])
    nt.links.new(noise.outputs['Fac'], soft.inputs['Value'])
    nt.links.new(edge.outputs['Facing'], rim.inputs[1])
    nt.links.new(soft.outputs['Result'], mul.inputs[0])
    nt.links.new(rim.outputs[0], mul.inputs[1])
    nt.links.new(mul.outputs[0], p.inputs['Alpha'])


def puffs(c4, root, mat, trail, name):
    """Soft flattened puffs hanging behind a machine: [(x, y, z, length, width)] in its frame."""
    for k, (x, y, z, ln, wd) in enumerate(trail):
        bm = bmesh.new()
        bmesh.ops.create_icosphere(bm, subdivisions=2, radius=1.0)
        ob = c4.mesh('%s %s %d' % (name, root.name, k), bm, mat)
        ob.parent = root
        ob.location = (x, y, z)
        ob.scale = (ln, wd, min(ln, wd) * 0.3)


def ground_sample(name, offset, fallback):
    """A material showing the painted ground `offset` metres away: a swath that grows through the
    shot is animated, so the overhead paint leaves it out, and it borrows the paint of the part of
    its field that was cut before the shot began. Falls back to `fallback` until that is painted."""
    sys.path.insert(0, str(HERE))
    import ortho_paint
    if os.environ.get('ORTHO_PAINT_OFF'):
        return fallback
    tile = None
    for t in ortho_paint.tiles().values():
        if t.get('accepted') and t['name'].startswith('port-farm'):
            (cx, cy), (w, h) = t['centre'], t['size']
            if abs(offset[2] - cx) < w / 2 - 15 and abs(offset[3] - cy) < h / 2 - 15:
                tile = t
    path = tile and ortho_paint.ORTHO / 'raw' / (tile['name'] + '.png')
    if tile is None or not path.exists():
        return fallback
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    p = nt.nodes['Principled BSDF']
    p.inputs['Roughness'].default_value = 0.95
    (cx, cy), (w, h) = tile['centre'], tile['size']
    geo = nt.nodes.new('ShaderNodeNewGeometry')
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    nt.links.new(geo.outputs['Position'], sep.inputs[0])
    uv = nt.nodes.new('ShaderNodeCombineXYZ')
    for axis, off, c, size in (('X', offset[0], cx, w), ('Y', offset[1], cy, h)):
        n = nt.nodes.new('ShaderNodeMath')
        n.operation = 'MULTIPLY_ADD'
        n.inputs[1].default_value = 1.0 / size
        n.inputs[2].default_value = 0.5 + (off - c) / size
        nt.links.new(sep.outputs[axis], n.inputs[0])
        nt.links.new(n.outputs[0], uv.inputs[axis])
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = bpy.data.images.load(str(path), check_existing=True)
    tex.extension = 'CLIP'
    nt.links.new(uv.outputs[0], tex.inputs['Vector'])
    nt.links.new(tex.outputs['Color'], p.inputs['Base Color'])
    return m


def growing_strip(c4, name, x0, x1, y_fixed, y_end, mat, z=0.06):
    """A strip from y_fixed to y_end(t) (seconds), keyed through the shot."""
    sign = 1.0 if y_end(0.0) > y_fixed else -1.0         # grows north or south; keeps facing up
    bm = bmesh.new()
    vs = [bm.verts.new(c) for c in ((x0, 0, 0), (x1, 0, 0), (x1, sign, 0), (x0, sign, 0))]
    bm.faces.new(vs if sign > 0 else vs[::-1])
    ob = c4.mesh(name, bm, mat)
    for t in (0.0, c4.DURATION):
        ob.location = (0, y_fixed, z)
        ob.scale = (1, abs(y_end(t) - y_fixed), 1)
        ob.keyframe_insert('location', frame=min(c4.FRAMES - 1, round(t * c4.FPS)))
        ob.keyframe_insert('scale', frame=min(c4.FRAMES - 1, round(t * c4.FPS)))
    linear(ob)
    return ob


def machines(c4, m):
    mat = c4.material
    dust = dust_material()
    orange = mat('Combine orange', (0.80, 0.40, 0.08), 0.5)
    grain = mat('Grain', (0.80, 0.66, 0.30), 0.9)
    green = mat('Tractor green', (0.16, 0.36, 0.14), 0.5)
    red = mat('Tractor red', (0.62, 0.12, 0.08), 0.5)
    cart = mat('Grain cart', (0.20, 0.30, 0.18), 0.6)
    steel = m['Steel']
    tank = mat('Sprayer tank', (0.86, 0.86, 0.80), 0.4)
    boom = mat('Sprayer boom', (0.20, 0.20, 0.20), 0.5, 0.5)
    cream = mat('Tender cream', (0.58, 0.56, 0.43), 0.5, 0.35)
    oxide = mat('Tender oxide', (0.50, 0.18, 0.06), 0.5, 0.3)
    T = c4.DURATION
    # The harvest: two combines in echelon cutting north, the cart running beside the leader.
    x0, x1, y0, y1, _ = field_rect(HARVEST)
    ya = -598.0
    lead = lambda t: ya + COMBINE_V * t                                     # noqa: E731
    second = lambda t: ya - 26 + COMBINE_V * t                               # noqa: E731
    # 40 m west is a painted pass of the same width, its windrow down the middle: each new swath
    # comes out with the straw the combine drops behind it.
    stub = ground_sample('Cut swath', (-40.0, 0.0, -140.0, -520.0), m['stubble'])
    growing_strip(c4, 'Swath lead', CUT_X, CUT_X + 12, y0, lambda t: lead(t) - 4, stub)
    growing_strip(c4, 'Swath second', CUT_X + 12, CUT_X + 24, y0, lambda t: second(t) - 4, stub)
    for name, cx, f in (('Combine lead', CUT_X + 6, lead), ('Combine second', CUT_X + 18, second)):
        root = machine(c4, name, combine_parts(c4, orange, grain), [(0, cx, f(0)), (T, cx, f(T))], math.pi / 2)
        puffs(c4, root, dust, [(-7, 0, 3, 6, 5), (-13, 0.5, 4, 8, 7), (-21, 1.2, 5, 10, 9)], 'Farm dust')
        if name == 'Combine lead':                                           # unloading on the move
            auger = bmesh.new()
            c4.box(auger, (0.7, 6.6, 0.7), (-0.6, 4.9, 4.9))
            c4.mesh('Combine lead auger', auger, orange).parent = root
    # The cart keeps pace on the cut side, under the leader's auger (left of a combine heading north).
    machine(c4, 'Grain cart', cart_parts(c4, green, cart, grain),
            [(0, CUT_X - 3.4, lead(0) + 2.0), (T, CUT_X - 3.4, lead(T) + 2.0)], math.pi / 2)
    machine(c4, 'Grain cart to the farm', cart_parts(c4, green, cart, grain),       # a full one going home
            [(0, -30, -641), (T, -30 - 7 * T, -641)], math.pi)
    machine(c4, 'Farm grain truck at the harvest', grain_truck_parts(c4, red, cart, grain),
            [(0, -172, -626)], math.pi / 2)     # waiting on the stubble by the track
    # The plough: turning the stubble south, the dark furrows growing behind it.
    x0, x1, y0, y1, _ = field_rect(PLOUGH)
    yp = -440.0
    plo = lambda t: yp - PLOUGH_V * t                                        # noqa: E731
    turned = ground_sample('Turned furrows', (60.0, 0.0, 530.0, -520.0), m['ploughed'])
    growing_strip(c4, 'Furrows', PLOUGH_X - 6.5, PLOUGH_X, y1, lambda t: plo(t) + 5.5, turned)
    root = machine(c4, 'Plough tractor', plough_parts(c4, red, steel),
                   [(0, PLOUGH_X - 1.6, plo(0)), (T, PLOUGH_X - 1.6, plo(T))], -math.pi / 2)
    # The sprayer: up the field with its boom out.
    machine(c4, 'Field sprayer', sprayer_parts(c4, green, tank, boom),
            [(0, 196, -366), (T, 196, -366 + 6.0 * T)], math.pi / 2)
    # The crop tender, hovering low across the west field, spars out.
    machine(c4, 'Crop tender', tender_parts(c4, cream, oxide, steel),     # spray drift does not read from above
            [(0, -720, -300), (T, -720 + 5.5 * T, -300)], 0.0, z=6.5)
    # Parked at the farmsteads: a tractor in each machine shed's mouth, a pickup by each house.
    for i, (x, y, h) in enumerate(FARMSTEADS):
        def at(u, v):
            return (x + u * math.cos(h) - v * math.sin(h), y + u * math.sin(h) + v * math.cos(h))
        px, py = at(30, -36)
        machine(c4, 'Farm tractor parked %d' % i, tractor_parts(c4, (green, red, green)[i]),
                [(0, px, py)], h + math.pi / 2)
        px, py = at(-30, -14)
        machine(c4, 'Farm pickup parked %d' % i, pickup_parts(c4, mat('Pickup %d' % i,
                ((0.70, 0.70, 0.66), (0.20, 0.28, 0.40), (0.55, 0.12, 0.08))[i], 0.5)), [(0, px, py)], h)


def traffic(c4, m):
    """Farm vehicles on the tracks, more freight on the belt road, a second train on the sidings."""
    mat = c4.material
    T = c4.DURATION
    white = mat('Pickup white', (0.74, 0.74, 0.70), 0.5)
    blue = mat('Farm truck blue', (0.18, 0.26, 0.40), 0.5)
    deck = m['Steel']
    hay = m['bale']
    produce = mat('Produce crates', (0.46, 0.34, 0.20), 0.8)
    # The east-west track at y -640; the north-south tracks at x -230 (the gate hauler's) and 610.
    machine(c4, 'Farm pickup east', pickup_parts(c4, white), [(0, -760, -642), (T, -760 + 10 * T, -642)], 0.0)
    machine(c4, 'Farm truck hay', farm_truck_parts(c4, blue, deck, hay),
            [(0, 420, -638), (T, 420 - 8 * T, -638)], math.pi)
    machine(c4, 'Farm truck produce', farm_truck_parts(c4, mat('Farm truck red', (0.55, 0.14, 0.08), 0.5),
                                                        deck, produce),
            [(0, 610, -820), (T, 610, -820 + 9 * T)], math.pi / 2)
    machine(c4, 'Farm pickup lane', pickup_parts(c4, mat('Pickup green', (0.24, 0.32, 0.20), 0.5)),
            [(0, -603, -620), (2.0, -603, -620), (T, -603, -620 + 7 * (T - 2))], math.pi / 2)
    # The freight road: lanes y -90 east, y -80 west, among the haulers c4x_scene already runs.
    tanker = mat('Road tanker', (0.70, 0.72, 0.72), 0.4, 0.4)
    cabs = [mat('Road cab %d' % i, c, 0.5) for i, c in
            enumerate(((0.16, 0.30, 0.50), (0.70, 0.66, 0.58), (0.50, 0.12, 0.08), (0.20, 0.36, 0.18)))]
    boxes = mat('Box body', (0.84, 0.82, 0.76), 0.6)
    container = bpy.data.materials.get('Container 1') or m['Hauler load']
    road = [('Road hauler east 0', hauler_parts(c4, cabs[0], container), -820, 18, -90),
            ('Road tanker east 1', tanker_parts(c4, cabs[1], tanker), 250, 18, -90),
            ('Box truck east 2', box_truck_parts(c4, cabs[2], boxes), 640, 19, -90),
            ('Road van east 3', van_parts(c4, boxes), -620, 18, -90),
            ('Road hauler west 0', hauler_parts(c4, cabs[3], m['Hauler load']), 720, -16, -80),
            ('Road tanker west 1', tanker_parts(c4, cabs[0], tanker), -340, -16, -80),
            ('Box truck west 2', box_truck_parts(c4, cabs[1], boxes), -660, -15, -80),
            ('Road van west 3', van_parts(c4, cabs[1]), 480, -16, -80)]
    for name, parts, x0, v, lane in road:
        machine(c4, name, parts, [(0, x0, lane), (T, x0 + v * T, lane)], 0.0 if v > 0 else math.pi)
    # A second train, west on the north siding: a locomotive, tank wagons, then container flats.
    loco = mat('Locomotive', (0.62, 0.42, 0.10), 0.5, 0.3)
    parts = [((20, 3.2, 4.2), (0, 0, 2.6), loco)]
    for k in range(10):
        parts.append((('tube', 1.5, 14), (-21 - k * 15.5, 0, 2.6), m['Tank white']))
    for k in range(8):
        parts.append(((14, 2.8, 2.6), (-176 - k * 15.5, 0, 2.4), bpy.data.materials.get('Container %d' % (k % 6))
                      or m['Hauler load']))
    machine(c4, 'Second freight train', parts, [(0, 520, -10), (T, 520 - 5 * T, -10)], math.pi)
