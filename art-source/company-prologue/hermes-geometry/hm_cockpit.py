"""Hermes cockpit: the glazed nose, the flight deck behind it, and its lamps.

The lights layer (graphics/ships/hermes/hermes_lights.png) places the
glazing: a crescent band across the nose top at X 38..41.5, brightest at
|Y| < 3 and wrapping back onto the cheeks, two small lamps at X 36.5,
Y +-1.5 just aft of it, and a lamp either side at X 34..35, |Y| 7..8.
Glazing and emissives go only there.

The windscreen is six panes in a gunmetal frame: two centre panes split by
a centre mullion, a side pane either side on the glacis, and a cheek pane
either side across the glacis/cheek ridge, the band sweeping aft as it
wraps. The panes are cut through the nose into a shallow flight-deck tub
(forward of X 30.7, above the nose-gear well) holding the two crew seats
abreast, the instrument console under the windscreen and a centre
pedestal; the glazing is part-transparent so the deck reads through it.
Above the seats a roof escape hatch with grab rails is the crew's own way
in and out; the fire team uses the ventral ramp.

The sprite's rounded nose dome is read as faceted plate: a brow facet
between the glacis and the bow face and a lower cheek facet either side,
each a bounded cut that leaves the plan outline untouched. The bow face
carries a bolted nose cap plate with a faceted sensor radome and two
pitot probes.

Everything on the outside is seated by ray casts against the hull taken
before the windows are cut; nothing hard-codes a Z that sits on the hull.
"""
import math
import bpy
from mathutils import Vector
from hm_kit import material, boolean, facet, plane_cut, blocks, crisp
from hm_hull import SPINE_DECK, KEEL

# Glazing patches kept from pass 1 for reference: (x0, x1, y0, y1).
GLAZING = [(37.5, 41.2, -3.0, 3.0), (36.5, 39.5, 3.4, 6.2), (36.5, 39.5, -6.2, -3.4)]
LAMPS = [(36.5, 1.5), (36.5, -1.5), (34.5, 7.5), (34.5, -7.5)]

# Windscreen boundary lines, port half, each (aft/top point, fore/bottom
# point) in plan. Panes are the cells between consecutive lines.
SCREEN_LINES = [((38.2, 0.0), (41.0, 0.0)),
                ((38.1, 3.1), (40.9, 3.1)),
                ((37.4, 5.65), (40.2, 5.65)),     # on the glacis/cheek ridge
                ((36.6, 7.0), (38.9, 7.0))]

# The hull's nose planes, duplicated from hm_hull.build_shell (not exported
# there): (name, point, plan direction the plane falls along, angle).
HULL_FACETS = [('Bow glacis', (30, 0, SPINE_DECK), (1, 0), 34),
               ('Chin rake', (34, 0, KEEL), (1, 0), 140),
               ('Port nose cheek', (37, 8, 3), (1, 1.4), 50),
               ('Starboard nose cheek', (37, -8, 3), (1, -1.4), 50)]
# New pass-2 facets on the dome, each bounded to the nose: (facet, x_min).
DOME_FACETS = [(('Nose brow', (42, 0, 1.4), (1, 0), 60), 40.0),
               (('Port lower nose cheek', (37, 8, -3), (1, 1.4), 130), 31.0),
               (('Starboard lower nose cheek', (37, -8, -3), (1, -1.4), 130), 31.0)]

TUB_WALL = .7          # plate between the flight deck and the outside
TUB_AFT = 30.7         # aft bulkhead of the flight deck
FLOOR = -5.4           # flight-deck floor (nose-gear well top is at -7.8)


# --- Ray casts -------------------------------------------------------------

def cast(kit, origin, direction, obj=None):
    """(point, normal) of the first hull hit along a ray, or None.
    The hull objects sit at the world origin, so local space is world."""
    obj = obj or kit.hull
    bpy.context.view_layer.update()
    ev = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
    hit, p, n, _ = ev.ray_cast(Vector(origin), Vector(direction).normalized())
    return (p, n) if hit else None


def top(kit, x, y):
    hit = kit.surface_any(x, y)
    if hit is None:
        raise RuntimeError(f'cockpit: no hull under ({x}, {y})')
    return hit


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0])*t, a[1] + (b[1] - a[1])*t)


# --- Mesh builders ---------------------------------------------------------

def solid_from_sections(sections):
    """Loft closed quad sections (lists of 4 verts) into one capped solid."""
    verts, faces = [], []
    for s in sections:
        verts += [tuple(v) for v in s]
    m = len(sections)
    for i in range(m - 1):
        a, b = 4*i, 4*(i + 1)
        faces += [(a + k, a + (k + 1) % 4, b + (k + 1) % 4, b + k) for k in range(4)]
    faces += [(3, 2, 1, 0), (4*(m-1), 4*(m-1) + 1, 4*(m-1) + 2, 4*(m-1) + 3)]
    return verts, faces


def strip_sections(kit, p0, p1, width, height, sink=.1, step=.45, extend=0):
    """Sections of a surface-hugging bar along plan segment p0 -> p1,
    lengthened by `extend` at each end so bars meeting at a corner close."""
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    length = math.hypot(dx, dy)
    if extend:
        ux, uy = dx/length*extend, dy/length*extend
        p0, p1 = (p0[0] - ux, p0[1] - uy), (p1[0] + ux, p1[1] + uy)
        dx, dy = p1[0] - p0[0], p1[1] - p0[1]
        length = math.hypot(dx, dy)
    qx, qy = -dy/length*width/2, dx/length*width/2
    n = max(2, math.ceil(length/step) + 1)
    out = []
    for i in range(n):
        cx, cy = lerp(p0, p1, i/(n - 1))
        pa, na = top(kit, cx - qx, cy - qy)
        pb, nb = top(kit, cx + qx, cy + qy)
        out.append([pa - na*sink, pb - nb*sink, pb + nb*height, pa + na*height])
    return out


def ribbon_sections(kit, pts, width, height, sink=.1, step=.45):
    """Sections of one continuous bar along a plan polyline, mitred at its
    corners so the bends close without overlapping boxes."""
    def perp(a, b):
        dx, dy = b[0] - a[0], b[1] - a[1]
        length = math.hypot(dx, dy)
        return (-dy/length, dx/length)
    out = []
    for i in range(len(pts) - 1):
        a, b = pts[i], pts[i + 1]
        n = max(2, math.ceil(math.hypot(b[0] - a[0], b[1] - a[1])/step) + 1)
        for k in range(n if i == len(pts) - 2 else n - 1):
            t = k/(n - 1)
            qx, qy = perp(a, b)
            if k == 0 and i > 0:          # mitre with the previous segment
                px, py = perp(pts[i - 1], a)
                mx, my = qx + px, qy + py
                m = math.hypot(mx, my)
                cos_half = (mx*qx + my*qy)/m
                qx, qy = mx/m/cos_half, my/m/cos_half
            cx, cy = lerp(a, b, t)
            ox, oy = qx*width/2, qy*width/2
            pa, na = top(kit, cx - ox, cy - oy)
            pb, nb = top(kit, cx + ox, cy + oy)
            out.append([pa - na*sink, pb - nb*sink, pb + nb*height, pa + na*height])
    return out


def ribbon(kit, name, polylines, width, height, mat, sink=.1, bevel=0):
    verts, faces = [], []
    for pts in polylines:
        v, f = solid_from_sections(ribbon_sections(kit, pts, width, height, sink))
        b = len(verts)
        verts += v
        faces += [tuple(i + b for i in face) for face in f]
    return kit.mesh(name, verts, faces, mat, bevel)


def strips(kit, name, segments, width, height, mat, sink=.1, bevel=0, extend=0):
    """Many surface-hugging bars as one object: segments are plan (p0, p1)."""
    verts, faces = [], []
    for p0, p1 in segments:
        v, f = solid_from_sections(strip_sections(kit, p0, p1, width, height, sink, extend=extend))
        b = len(verts)
        verts += v
        faces += [tuple(i + b for i in face) for face in f]
    return kit.mesh(name, verts, faces, mat, bevel)


def quad_patch(kit, name, corners, mat, offset, thickness, count=6, bevel=0):
    """A plate over a plan quad (corners in order), seated by vertical casts."""
    a, b, c, d = corners
    verts = []
    for i in range(count):
        u = i/(count - 1)
        left, right = lerp(a, d, u), lerp(b, c, u)
        for j in range(count):
            x, y = lerp(left, right, j/(count - 1))
            p, n = top(kit, x, y)
            verts.append(tuple(p + n*offset))
    faces = [(i*count + j, i*count + j + 1, (i+1)*count + j + 1, (i+1)*count + j)
             for i in range(count - 1) for j in range(count - 1)]
    ob = kit.mesh(name, verts, faces, mat)
    if thickness:
        sol = ob.modifiers.new('Plate thickness', 'SOLIDIFY')
        sol.thickness, sol.offset = thickness, 1
    if bevel:
        bev = ob.modifiers.new('Plate edge chamfer', 'BEVEL')
        bev.width, bev.segments, bev.limit_method = bevel, 1, 'ANGLE'
    # Wind faces so the solidify grows outward whichever way the quad runs.
    me = ob.data
    me.update()
    if me.polygons[0].normal.dot(Vector(top(kit, *lerp(a, c, .5))[1])) < 0:
        for poly in me.polygons:
            poly.flip()
    return ob


def dir_patch(kit, name, corners3, direction, mat, offset, thickness, count=4, bevel=0):
    """A plate over a 3D quad cast along `direction` onto the hull."""
    a, b, c, d = [Vector(v) for v in corners3]
    verts = []
    for i in range(count):
        u = i/(count - 1)
        left, right = a.lerp(d, u), b.lerp(c, u)
        for j in range(count):
            hit = cast(kit, left.lerp(right, j/(count - 1)), direction)
            if hit is None:
                raise RuntimeError(f'cockpit: {name} missed the hull')
            p, n = hit
            verts.append(tuple(p + n*offset))
    faces = [(i*count + j, i*count + j + 1, (i+1)*count + j + 1, (i+1)*count + j)
             for i in range(count - 1) for j in range(count - 1)]
    ob = kit.mesh(name, verts, faces, mat)
    ob.data.update()
    if ob.data.polygons[0].normal.dot(-Vector(direction)) < 0:
        for poly in ob.data.polygons:
            poly.flip()
    sol = ob.modifiers.new('Plate thickness', 'SOLIDIFY')
    sol.thickness, sol.offset = thickness, 1
    if bevel:
        bev = ob.modifiers.new('Plate edge chamfer', 'BEVEL')
        bev.width, bev.segments, bev.limit_method = bevel, 1, 'ANGLE'
    return ob


def studs(kit, name, seats, radius, height, mat, sides=6):
    """Bolt heads / studs: short prisms standing on (point, normal) seats."""
    verts, faces = [], []
    for p, n in seats:
        p, n = Vector(p), Vector(n).normalized()
        t = n.orthogonal().normalized()
        s = n.cross(t)
        b = len(verts)
        for h in (-.05, height):
            for k in range(sides):
                a = math.tau*k/sides
                verts.append(tuple(p + n*h + (t*math.cos(a) + s*math.sin(a))*radius))
        faces.append(tuple(b + k for k in reversed(range(sides))))
        faces.append(tuple(b + sides + k for k in range(sides)))
        faces += [(b + k, b + (k+1) % sides, b + sides + (k+1) % sides, b + sides + k) for k in range(sides)]
    return kit.mesh(name, verts, faces, mat)


def rod(kit, name, p0, p1, radius, mat, sides=8):
    p0, p1 = Vector(p0), Vector(p1)
    d = p1 - p0
    ob = kit.cylinder(name, tuple((p0 + p1)/2), radius, d.length, mat, 'Z', sides, 0)
    ob.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    return ob


def wedge_x(kit, name, profile, y0, y1, mat, bevel=.05):
    """A prism of an XZ profile (CCW seen from +Y) extruded from y0 to y1."""
    n = len(profile)
    verts = [(x, y0, z) for x, z in profile] + [(x, y1, z) for x, z in profile]
    faces = [tuple(range(n)), tuple(n + k for k in reversed(range(n)))]
    faces += [(k, n + k, n + (k+1) % n, (k+1) % n) for k in range(n)]
    ob = kit.mesh(name, verts, faces, mat, bevel)
    ob.data.update()
    # Make the normals outward whatever the profile's winding.
    if ob.data.polygons[0].normal.y > 0:
        for poly in ob.data.polygons:
            poly.flip()
    return ob


def convex_from_planes(kit, name, box, planes, mat):
    """An axis box trimmed by half-spaces (point, outward normal)."""
    (x0, x1), (y0, y1), (z0, z1) = box
    ob = blocks(kit, name, [(x0, x1, y0, y1, z0, z1)], mat)
    for k, (point, normal) in enumerate(planes):
        plane_cut(ob, f'{name} {k}', Vector(point), Vector(normal))
    return ob


# --- Build -----------------------------------------------------------------

def nose_dome(kit):
    """Bounded facets that round the dome without moving the plan outline."""
    for (name, point, down, angle), x_min in DOME_FACETS:
        _, p, n = facet(name, point, down, angle)
        # The cutter carries the hull's own plate: the solver keeps its material.
        cutter = blocks(kit, f'Temporary {name} cutter', [(x_min, 60, -30, 30, -30, 30)], kit.cream)
        plane_cut(cutter, name + ' keep', p, -n)     # keep only the outward side
        boolean(kit.hull, cutter, 'DIFFERENCE')


def windscreen_cells():
    """Plan quads of the six panes, port centre outward then starboard."""
    port = [[a0, b0, b1, a1] for (a0, b0), (a1, b1) in zip(SCREEN_LINES, SCREEN_LINES[1:])]
    star = [[(x, -y) for x, y in reversed(q)] for q in port]
    return port, star


def inset_quad(q, d):
    cx = sum(p[0] for p in q)/4
    cy = sum(p[1] for p in q)/4
    out = []
    for x, y in q:
        dx, dy = cx - x, cy - y
        length = math.hypot(dx, dy)
        out.append((x + dx/length*d*1.4, y + dy/length*d*1.4))
    return out


def build_windscreen(kit):
    port, star = windscreen_cells()
    # Glass: tinted, part-transparent so the flight deck reads through it,
    # keeping pass 1's faint glazing glow (the lights layer's band).
    bsdf = kit.glass.node_tree.nodes['Principled BSDF']
    bsdf.inputs['Alpha'].default_value = .3
    bsdf.inputs['Roughness'].default_value = .05
    bsdf.inputs['Emission Strength'].default_value = .12
    panes = {'Cockpit centre glazing': [port[0], star[0]],
             'Cockpit port glazing': port[1:],
             'Cockpit starboard glazing': star[1:]}
    for name, quads in panes.items():
        parts = [quad_patch(kit, f'{name} pane {k}', q, kit.glass, -.12, .0) for k, q in enumerate(quads)]
        join(parts, name)

    # Frame: outer lip (top, bottom, outboard ends) and mullions.
    tops = [a for a, _ in SCREEN_LINES]
    bots = [b for _, b in SCREEN_LINES]
    tops = [(x, -y) for x, y in reversed(tops[1:])] + tops
    bots = [(x, -y) for x, y in reversed(bots[1:])] + bots
    ends = [SCREEN_LINES[-1], tuple((x, -y) for x, y in SCREEN_LINES[-1])]
    lip = ribbon(kit, 'Cockpit windscreen frame', [tops, bots], .55, .32, kit.steel)
    sides = strips(kit, 'Cockpit windscreen frame ends', ends, .55, .32, kit.steel, extend=.27)
    lip = join([lip, sides], 'Cockpit windscreen frame')
    crisp(lip, .06)
    mull = [SCREEN_LINES[0]] + [l for line in SCREEN_LINES[1:-1] for l in (line, tuple((x, -y) for x, y in line))]
    strips(kit, 'Cockpit windscreen mullions', mull, .42, .26, kit.steel, bevel=.05)

    # Rain deflector: a raised gutter lip a hand's width aft of the screen.
    defl = [(37.6, 0), (37.5, 3.1), (36.8, 5.65), (36.05, 7.0)]
    defl = [(x, -y) for x, y in reversed(defl[1:])] + defl
    ribbon(kit, 'Cockpit windscreen rain deflector', [defl], .26, .42, kit.panel, bevel=.04)

    # Wipers, parked along the lower edge of each centre pane.
    for side, s in (('port', 1), ('starboard', -1)):
        pp, pn = top(kit, 40.85, 2.75*s)
        tp, tn = top(kit, 40.7, .55*s)
        boss = kit.cylinder(f'Cockpit {side} wiper pivot', tuple(pp + pn*.08), .24, .32, kit.steel, 'Z', 10, 0)
        boss.rotation_euler = pn.to_track_quat('Z', 'Y').to_euler()
        rod(kit, f'Cockpit {side} wiper arm', pp + pn*.26, tp + tn*.22, .08, kit.steel, 6)
        b0, n0 = top(kit, 40.55, 2.55*s)
        b1, n1 = top(kit, 40.55, .45*s)
        rod(kit, f'Cockpit {side} wiper blade', b0 + n0*.13, b1 + n1*.13, .07, kit.dark, 6)
    return port + star


def build_roof_hatch(kit):
    """Crew escape and boarding hatch over the seats, with grab rails."""
    hatch = [(31.0, 2.3), (34.3, 2.3), (34.3, -2.3), (31.0, -2.3)]
    quad_patch(kit, 'Cockpit roof hatch', hatch, kit.panel, .02, .16, 5, bevel=.06)
    # Seam ring round the hatch reads as the gap the lid closes into.
    ring = [((30.75, 2.55), (34.55, 2.55)), ((34.55, 2.55), (34.55, -2.55)),
            ((34.55, -2.55), (30.75, -2.55)), ((30.75, -2.55), (30.75, 2.55))]
    strips(kit, 'Cockpit roof hatch seam', ring, .2, .03, kit.dark, sink=.06)
    h0, n0 = top(kit, 31.25, 1.8)
    h1, n1 = top(kit, 31.25, -1.8)
    rod(kit, 'Cockpit roof hatch hinge', h0 + n0*.3, h1 + n1*.3, .2, kit.steel, 8)
    # Recessed pull handle at the forward edge of the lid.
    a0, m0 = top(kit, 33.75, .7)
    a1, m1 = top(kit, 33.75, -.7)
    rod(kit, 'Cockpit roof hatch handle', a0 + m0*.42, a1 + m1*.42, .1, kit.steel, 6)
    studs(kit, 'Cockpit roof hatch handle standoffs', [(a0 + m0*.1, m0), (a1 + m1*.1, m1)], .1, .3, kit.steel)
    for side, s in (('port', 1), ('starboard', -1)):
        r0, q0 = top(kit, 31.3, 3.15*s)
        r1, q1 = top(kit, 34.0, 3.15*s)
        rod(kit, f'Cockpit {side} grab rail', r0 + q0*.55, r1 + q1*.55, .17, kit.steel, 8)
        rod(kit, f'Cockpit {side} grab rail aft standoff', r0 - q0*.05, r0 + q0*.6, .15, kit.steel, 8)
        rod(kit, f'Cockpit {side} grab rail fore standoff', r1 - q1*.05, r1 + q1*.6, .15, kit.steel, 8)


def build_glacis_detail(kit):
    """Panel seams on the glacis and a comms blade."""
    seams = [((35.55, -5.4), (35.55, 5.4)),
             ((30.3, 4.1), (35.55, 4.1)), ((30.3, -4.1), (35.55, -4.1))]
    strips(kit, 'Nose glacis panel seams', seams, .14, .03, kit.dark, sink=.06)
    bp, bn = top(kit, 32.4, -5.3)
    blade = kit.box('Nose comms blade antenna', tuple(bp + bn*.75), (1.5, .16, 1.4), kit.steel, .05)
    blade.rotation_euler = (0, math.radians(-14), 0)
    kit.box('Nose comms blade antenna base', tuple(bp + bn*.06), (1.9, .55, .22), kit.steel, .05)


def build_lamps(kit):
    for k, (x, y) in enumerate(LAMPS, 1):
        p, n = top(kit, x, y)
        lamp = kit.cylinder(f'Nose lamp {k}', tuple(p + n*.1), .45, .3, kit.lamp, 'Z', 12, 0)
        lamp.rotation_euler = n.to_track_quat('Z', 'Y').to_euler()
        bez = kit.cylinder(f'Nose lamp {k} bezel', tuple(p + n*.02), .62, .2, kit.steel, 'Z', 12, 0)
        bez.rotation_euler = n.to_track_quat('Z', 'Y').to_euler()


def build_nose_cap(kit):
    """Bolted cap plate on the bow face, a faceted radome and two pitots."""
    plate = dir_patch(kit, 'Nose cap plate', [(50, 4.0, .9), (50, -4.0, .9), (50, -4.0, -2.7), (50, 4.0, -2.7)],
                      (-1, 0, 0), kit.panel, .0, .14, 4, bevel=.05)
    face = cast(kit, (50, 0, -.9), (-1, 0, 0))[0].x + .14
    bolts = []
    for y in (-3.6, -1.8, 0, 1.8, 3.6):
        for z in (.55, -2.35):
            bolts.append(((face, y, z), (1, 0, 0)))
    for y in (-3.6, 3.6):
        bolts.append(((face, y, -.9), (1, 0, 0)))
    studs(kit, 'Nose cap plate bolts', bolts, .17, .12, kit.steel)
    # Radome: a shallow truncated pyramid, kept short of the sprite's bow.
    base = [(-2.9, .25), (2.9, .25), (2.9, -2.05), (-2.9, -2.05)]
    crown = [(-1.9, -.2), (1.9, -.2), (1.9, -1.6), (-1.9, -1.6)]
    verts = [(face - .02, y, z) for y, z in base] + [(face + .26, y, z) for y, z in crown]
    faces = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
    dielectric = material('Nose radome dielectric', (.5, .48, .42), 0, .85)
    kit.mesh('Nose sensor radome', verts, faces, dielectric, .04)
    for side, s in (('port', 1), ('starboard', -1)):
        hit = cast(kit, (50, 4.6*s, .5), (-1, 0, 0))
        p = hit[0]
        rod(kit, f'Nose {side} pitot boss', p + Vector((-.05, 0, 0)), p + Vector((.35, 0, 0)), .24, kit.steel, 8)
        rod(kit, f'Nose {side} pitot probe', p + Vector((.3, 0, 0)), p + Vector((.95, 0, 0)), .09, kit.steel, 6)


def build_cheek_panels(kit):
    """Bolted access panels on the lower cheek facets (avionics bays)."""
    for side, s in (('port', 1), ('starboard', -1)):
        _, _, n = facet('lower cheek', (37, 8*s, -3), (1, 1.4*s), 130)
        t1 = Vector((-.814, .581*s, 0))
        t2 = n.cross(t1).normalized()
        if t2.z < 0:
            t2 = -t2
        c = Vector((37.7, 7.6*s, -2.9))
        corners = [c + t1*1.3 + t2*.8, c - t1*1.3 + t2*.8, c - t1*1.3 - t2*.8, c + t1*1.3 - t2*.8]
        dir_patch(kit, f'Nose {side} avionics access panel', [v + n*4 for v in corners], -n, kit.panel,
                  .0, .12, 4, bevel=.04)
        seats = []
        for u in (-1.05, 0, 1.05):
            for w in (-.58, .58):
                hit = cast(kit, c + t1*u + t2*w + n*4, -n)
                if hit:
                    seats.append((hit[0] + hit[1]*.12, hit[1]))
        studs(kit, f'Nose {side} avionics access panel bolts', seats, .14, .1, kit.steel)


def tub_planes(wall):
    """The hull's nose planes moved `wall` inward: the flight deck's skin."""
    planes = []
    for name, point, down, angle in HULL_FACETS + [f for f, _ in DOME_FACETS]:
        _, p, n = facet(name, point, down, angle)
        planes.append((p - n*wall, n))
    for s in (1, -1):        # the plan's bow corners: x + |y| <= 47
        n = Vector((1, s, 0)).normalized()
        planes.append((Vector((42, 5*s, 0)) - n*wall, n))
    return planes


def cut_flight_deck(kit, cells):
    """Hollow the flight deck and open the window cells into it."""
    lining = material('Cockpit interior lining', (.26, .27, .28), .2, .7)
    kit.hull.data.materials.append(lining)
    slot = len(kit.hull.data.materials) - 1
    planes = tub_planes(TUB_WALL)
    tub = convex_from_planes(kit, 'Temporary flight deck cutter',
                             ((TUB_AFT, 42 - TUB_WALL), (-7.6, 7.6), (FLOOR, SPINE_DECK + 5)), planes, kit.dark)
    for poly in tub.data.polygons:
        poly.material_index = slot
    # Window openings: vertical prisms through the skin into the tub.
    openings = []
    for k, q in enumerate(cells):
        q = inset_quad(q, .1)
        zs = [top(kit, x, y)[0].z for x, y in q]
        z0, z1 = min(zs) - 2.5, max(zs) + 3
        openings.append(blocks_prism(kit, f'Temporary window cutter {k}', q, z0, z1, kit.dark))
    for ob in [tub] + openings:
        ob.data.materials.clear()
        for m in kit.hull.data.materials:
            ob.data.materials.append(m)
        for poly in ob.data.polygons:
            poly.material_index = slot
    boolean(kit.hull, tub, 'DIFFERENCE')
    for ob in openings:
        boolean(kit.hull, ob, 'DIFFERENCE')


def blocks_prism(kit, name, quad, z0, z1, mat):
    q = quad
    area = sum(q[i-1][0]*q[i][1] - q[i][0]*q[i-1][1] for i in range(4))
    if area < 0:
        q = list(reversed(q))
    verts = [(x, y, z0) for x, y in q] + [(x, y, z1) for x, y in q]
    faces = [(3, 2, 1, 0), (4, 5, 6, 7)] + [(k, (k+1) % 4, 4 + (k+1) % 4, 4 + k) for k in range(4)]
    return kit.mesh(name, verts, faces, mat)


def build_interior(kit):
    """Two seats abreast, the console under the windscreen, a centre pedestal."""
    seat = material('Cockpit seat upholstery', (.30, .27, .22), 0, .85)
    screen = material('Cockpit console screens', (.05, .09, .1), .1, .25)
    convex_from_planes(kit, 'Cockpit deck plate', ((TUB_AFT + .1, 41), (-7.4, 7.4), (FLOOR + .01, FLOOR + .16)),
                       tub_planes(TUB_WALL + .12), kit.dark)
    # Seats well forward, eyes close behind the screen; legs run under the console.
    for side, s in (('port', 1), ('starboard', -1)):
        y = 2.9*s
        kit.box(f'Cockpit {side} seat pedestal', (34.6, y, (FLOOR - 2.7)/2), (2.0, 1.6, -2.7 - FLOOR), kit.steel, .06)
        kit.box(f'Cockpit {side} seat cushion', (34.8, y, -2.3), (3.2, 3.5, .8), seat, .2)
        wedge_x(kit, f'Cockpit {side} seat back', [(32.8, -2.6), (33.8, -2.6), (33.35, 3.6), (32.95, 3.6)],
                y - 1.75, y + 1.75, seat, .15)
        kit.box(f'Cockpit {side} seat headrest', (33.15, y, 4.45), (.8, 2.3, 1.4), seat, .15)
        rod(kit, f'Cockpit {side} control stick', (36.9, y, -2.7), (37.05, y, -.7), .16, kit.steel, 8)
        kit.box(f'Cockpit {side} control stick grip', (37.05, y, -.45), (.45, .45, .7), kit.dark, .08)
    wedge_x(kit, 'Cockpit instrument console', [(38.0, -2.2), (40.6, -2.2), (40.6, .4), (39.2, .4), (38.0, -.4)],
            -5.0, 5.0, kit.panel, .08)
    displays = []
    for y in (-3.9, -2.4, -.75, .75, 2.4, 3.9):
        displays.append(wedge_x(kit, 'tmp', [(38.12, -.33), (39.1, .32), (39.16, .24), (38.18, -.41)],
                                y - .6, y + .6, screen, 0))
    join(displays, 'Cockpit console displays')
    kit.box('Cockpit centre pedestal', (36.2, 0, (FLOOR - 1.6)/2), (3.6, 1.5, -1.6 - FLOOR), kit.panel, .08)
    kit.box('Cockpit throttle levers', (36.3, 0, -1.3), (.5, .9, .7), kit.dark, .04)
    kit.box('Cockpit aft bulkhead door', (TUB_AFT + .06, 0, (FLOOR + 3.5)/2), (.12, 3.2, 3.5 - FLOOR), kit.steel, .05)


def join(parts, name):
    bpy.ops.object.select_all(action='DESELECT')
    for p in parts:
        p.select_set(True)
    bpy.context.view_layer.objects.active = parts[0]
    if len(parts) > 1:
        bpy.ops.object.join()
    ob = bpy.context.view_layer.objects.active
    ob.name = name
    ob.data.name = name
    return ob


def build(kit):
    nose_dome(kit)
    # Everything outside is seated before the windows are cut.
    cells = build_windscreen(kit)
    build_lamps(kit)
    build_roof_hatch(kit)
    build_glacis_detail(kit)
    build_nose_cap(kit)
    build_cheek_panels(kit)
    cut_flight_deck(kit, cells)
    build_interior(kit)
