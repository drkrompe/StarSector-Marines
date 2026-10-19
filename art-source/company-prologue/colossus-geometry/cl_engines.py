"""Colossus drives: two drive pods aft, port and starboard, three LOW_TECH drives each.

Engine slots from colossus.ship, in file order (index i = 1..6), all angle 180,
width 15, length 56: port 1 (-124, 64), 2 (-124, 56), 3 (-124, 49); starboard
4 (-124, -61), 5 (-124, -54), 6 (-124, -46). Read off colossus.png, each pod is
one long armoured drive unit (X -31..-125) that widens past a diagonal shoulder
at X -56..-66 and ends in a row of three nozzles on its inboard half: the pod is
the drive, and the bells are its mouths, not nozzles on the hull.

Detail read off the sprite at 8-10x (pass 2), the same on both pods:
- a steel fore collar where the pod meets the waist truss (X -32..-36);
- segmented armour: transverse seam grooves round the whole casing at
  X -40, -74, -88 and -102, a fore-aft seam along the inboard rim, and a
  strata groove along the outboard wall;
- the diagonal pale shoulder band, a raised plate running from the outboard
  shoulder (X ~-50) back to the inboard rim (X ~-72) where the pod widens;
- an inspection ring on the top just aft of the band (-69, rel 62.5) with a
  raised conduit strap running aft from it, and a round inspection port on
  the outboard shoulder (X -71) that the sprite draws breaking the outline;
- a heat-exchange fin bank in a recess on the inboard rim (X -91..-101);
- bolted service covers, on the top inboard panel and the outboard wall;
- a round coupling on the inboard side (X -104.5) where the sprite's outline
  bulges toward the spine;
- a lighter aft deck plate (X -102..-116) over the thrust frame;
- the aft end: the casing is notched open between two cheeks from X -116.5
  aft (the sprite shows the nozzle bodies from above), with a steel thrust
  frame on the aft bulkhead and a heat-shield sill under the bells. Each
  drive is a contoured bell with liner, mouth lip, cooling hoops, heat-shield
  band, throat ring, combustion chamber, gimbal ring and two actuators.
The flare launchers WS 007 (-83, 83) and WS 008 (-84, -81) seat on the pods'
outboard shoulders; nothing here is placed within 4 units of them.

Names: 'Drive pod <side> casing' for the pod, 'Drive pod <side> ...' for its
fittings, and per slot the Second Wind's 'Engine <i> aft-facing bell',
'Engine <i> mouth lip', 'Engine <i> dark throat' and 'Engine <i> exhaust
anchor' (an empty on the mouth whose +X is the exhaust direction), plus
'Engine <i> ...' for the nozzle's other parts.
"""
import math
import bpy
from mathutils import Vector
from cl_kit import loft_x, octagon, revolve_x, material, inset
import cl_hull

# (x, y, width, angle) exactly as colossus.ship lists them.
ENGINES = [(-124, 64, 15, 180), (-124, 56, 15, 180), (-124, 49, 15, 180),
           (-124, -61, 15, 180), (-124, -54, 15, 180), (-124, -46, 15, 180)]
POD_Z = 0.0
# Pod envelope fore to aft: (x, inboard rel y, outboard rel y, half-height).
ENVELOPE = [(-30, 47, 71, 10), (-33, 44, 73.5, 14), (-55, 44, 75, 15), (-60, 43, 79.5, 15.5), (-67, 42, 84, 16),
            (-108, 40, 83, 16), (-111, 40, 80, 15.5), (-116, 40, 74.5, 14.5), (-122.5, 40.5, 70.5, 12.5),
            (-125, 43, 69, 11)]
# Nozzle: mouth plane, throat plane, inner mouth / throat radii, wall.
MOUTH_X, THROAT_X = -124.5, -119.6
BELL = {'mouth_r': 2.95, 'throat_r': 1.45, 'wall': .22}
BULKHEAD_X = -116.5     # aft bulkhead / thrust frame face
SILL_Z = -4.4           # floor of the nozzle bay
SEAMS = (-40, -74, -88, -102)
LAUNCHER_PATCHES = [(-83, 83), (-84, -81)]  # WS 007 / WS 008: keep clear
ANCHORS = {}


# --- local helpers (cl_kit stays untouched) ---

def _sections(side_sign):
    """Pod YZ sections aft to fore (increasing x): [(x, [(y, z) x8])]."""
    out = []
    for x, a, b, h in reversed(ENVELOPE):
        y0, y1 = sorted((cl_hull.y(a, side_sign), cl_hull.y(b, side_sign)))
        out.append((x, octagon(y0, y1, POD_Z-h, POD_Z+h, min(6, h*.45))))
    return out


def _section_at(secs, x):
    for (xa, va), (xb, vb) in zip(secs, secs[1:]):
        if xa <= x <= xb:
            t = (x-xa)/(xb-xa)
            return [(p[0]+(q[0]-p[0])*t, p[1]+(q[1]-p[1])*t) for p, q in zip(va, vb)]
    raise ValueError(x)


def _env(x):
    """(inboard rel, outboard rel, half-height) at x."""
    for (xa, a0, b0, h0), (xb, a1, b1, h1) in zip(ENVELOPE, ENVELOPE[1:]):
        if xb <= x <= xa:
            t = (x-xa)/(xb-xa)
            return a0+(a1-a0)*t, b0+(b1-b0)*t, h0+(h1-h0)*t
    raise ValueError(x)


def _cut(target, cutter, op='DIFFERENCE'):
    """MANIFOLD boolean that keeps the cutter's material on the new faces."""
    bpy.context.view_layer.objects.active = target
    mod = target.modifiers.new('Pod boolean', 'BOOLEAN')
    mod.operation = op
    mod.object = cutter
    mod.solver = 'MANIFOLD'
    mod.material_mode = 'TRANSFER'
    bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)
    bpy.ops.object.modifier_apply(modifier=mod.name)
    bpy.data.objects.remove(cutter, do_unlink=True)


def _ring_x(kit, name, sec, x0, x1, depth, out, mat):
    """Hollow 8-sided ring along X: the section grown by `out` minus shrunk by `depth`."""
    outer, inner = inset(sec, -out), inset(sec, depth)
    n = len(sec)
    verts = [(x, y, z) for x in (x0, x1) for poly in (outer, inner) for y, z in poly]
    o0, i0, o1, i1 = 0, n, 2*n, 3*n
    faces = []
    for k in range(n):
        j = (k+1) % n
        faces += [(o0+k, o0+j, o1+j, o1+k), (i0+k, i1+k, i1+j, i0+j),
                  (o0+k, i0+k, i0+j, o0+j), (o1+k, o1+j, i1+j, i1+k)]
    return kit.mesh(name, verts, faces, mat)


def _block(kit, name, x0, x1, y0, y1, z0, z1, mat, bevel=0):
    v = [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    f = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    return kit.mesh(name, v, f, mat, bevel)


def _plan_prism(kit, name, plan, z0, z1, mat):
    n = len(plan)
    v = [(x, y, z) for z in (z0, z1) for x, y in plan]
    f = [tuple(range(n)), tuple(range(n, 2*n))]+[(k, (k+1) % n, n+(k+1) % n, n+k) for k in range(n)]
    return kit.mesh(name, v, f, mat)


def _xz_prism(kit, name, poly, y0, y1, mat):
    """A prism standing on an (x, z) outline, spanning y0..y1."""
    n = len(poly)
    v = [(x, y, z) for y in (y0, y1) for x, z in poly]
    f = [tuple(range(n)), tuple(range(n, 2*n))]+[(k, (k+1) % n, n+(k+1) % n, n+k) for k in range(n)]
    return kit.mesh(name, v, f, mat)


# Aft-end profile cuts (x, z): the top falls to the thrust frame, the keel tapers up to the sill.
AFT_TOP = [(-127, 6), (BULKHEAD_X, 6), (-102.5, 17), (-102.5, 40), (-127, 40)]
AFT_KEEL = [(-127, -40), (-102.5, -40), (-102.5, -17), (-117, -8.5), (-127, -8.5)]


def _skin(kit, name, secs, grow, clip, mat, bevel=.08):
    """A plate laid over the casing: the casing grown by `grow`, kept inside `clip`."""
    skin = loft_x(kit, name, [(x, inset(s, -grow)) for x, s in secs], mat, 0)
    _cut(skin, clip, 'INTERSECT')
    if bevel:
        b = skin.modifiers.new('Plate edge chamfer', 'BEVEL')
        b.width, b.segments, b.limit_method = bevel, 1, 'ANGLE'
    return skin


def _studs(kit, name, points, axis, r, h, mat, n=6):
    """Bolt heads: one mesh of short n-gon prisms at points, standing along axis ('X'|'Y'|'Z', sign)."""
    ax, sign = axis
    verts, faces = [], []
    for p in points:
        base = len(verts)
        for lvl in (0, h*sign):
            for k in range(n):
                a = 2*math.pi*k/n
                c, s = r*math.cos(a), r*math.sin(a)
                if ax == 'Z':
                    verts.append((p[0]+c, p[1]+s, p[2]+lvl))
                elif ax == 'Y':
                    verts.append((p[0]+c, p[1]+lvl, p[2]+s))
                else:
                    verts.append((p[0]+lvl, p[1]+c, p[2]+s))
        faces.append(tuple(base+k for k in range(n)))
        faces.append(tuple(base+n+k for k in range(n)))
        faces += [(base+k, base+(k+1) % n, base+n+(k+1) % n, base+n+k) for k in range(n)]
    return kit.mesh(name, verts, faces, mat)


def _revolve_z(kit, name, profile, cx, cy, z, mat, n=24):
    """Closed (r, dz) outline revolved about a vertical axis at (cx, cy, z)."""
    verts = [(cx+r*math.cos(2*math.pi*i/n), cy+r*math.sin(2*math.pi*i/n), z+dz) for r, dz in profile for i in range(n)]
    m = len(profile)
    faces = []
    for a in range(m):
        b = (a+1) % m
        faces += [(a*n+i, a*n+(i+1) % n, b*n+(i+1) % n, b*n+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat)


def _rod(kit, name, p0, p1, r, mat, n=8):
    p0, p1 = Vector(p0), Vector(p1)
    d = p1-p0
    bpy.ops.mesh.primitive_cylinder_add(vertices=n, radius=r, depth=d.length, location=(p0+p1)/2)
    ob = bpy.context.object
    ob.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    return kit.finish(ob, name, mat, 0)


def _top(kit, x, y):
    hit = kit.surface(x, y)
    return hit[0].z if hit else POD_Z


def _bell_r(s):
    """Inner bell radius, s = 0 at the throat to 1 at the mouth (a contoured, not conical, bell)."""
    return BELL['throat_r']+(BELL['mouth_r']-BELL['throat_r'])*s**.55


def _bell_x(s):
    return THROAT_X+(MOUTH_X-THROAT_X)*s


# --- the pod ---

def pod(kit, side, s, mats):
    secs = _sections(s)
    casing = loft_x(kit, f'Drive pod {side} casing', secs, kit.maroon)
    kit.hull.append(casing)
    Y = lambda rel: cl_hull.y(rel, s)

    # Segment seams: shallow dark grooves round the whole casing.
    for k, x in enumerate(SEAMS, 1):
        _cut(casing, _ring_x(kit, f'Seam {k}', _section_at(secs, x), x-.35, x+.35, .45, 2, kit.dark))
    # Fore-aft seam along the inboard rim, aft of the shoulder band.
    a, b, h = _env(-87)
    y0, y1 = sorted((Y(47.2), Y(47.8)))
    _cut(casing, _block(kit, 'rim seam', -101.6, -74, y0, y1, h-.45, h+2, kit.dark))
    # Strata groove along the outboard wall, below the launcher shoulder.
    b0, b1 = _env(-68)[1], _env(-107)[1]
    strata = cl_hull.ccw([(-68, Y(b0-.45)), (-68, Y(b0+2)), (-107, Y(b1+2)), (-107, Y(b1-.45))])
    _cut(casing, _plan_prism(kit, 'strata', strata, -3.3, -2.7, kit.dark))
    # Recess for the inboard heat-exchange fins.
    a, b, h = _env(-96)
    fy0, fy1 = sorted((Y(46.6), Y(50.8)))
    _cut(casing, _block(kit, 'fin recess', -101.2, -90.8, fy0, fy1, h-1.1, h+3, kit.dark))
    # The nozzle bay: notched open between two cheeks from the aft bulkhead aft.
    ys = [y for _, y, _, _ in ENGINES if (y > 0) == (s > 0)]
    by0, by1 = min(ys)-4.4, max(ys)+4.4
    _cut(casing, _xz_prism(kit, 'aft top', AFT_TOP, -200, 200, kit.maroon))
    _cut(casing, _xz_prism(kit, 'aft keel', AFT_KEEL, -200, 200, kit.maroon))
    _cut(casing, _block(kit, 'nozzle bay', -127, BULKHEAD_X, by0, by1, SILL_Z, 30, kit.steel))

    # Fore collar where the pod meets the waist truss.
    _skin(kit, f'Drive pod {side} fore collar', secs, .4,
          _block(kit, 'clip', -36.2, -32.2, -200, 200, -40, 40, kit.steel), kit.steel)
    # Diagonal pale shoulder band: outboard-fore to inboard-aft where the pod widens.
    xu = lambda rel: -50-.52*(66-rel)
    rels = (34, 92)
    band = [(xu(r), Y(r)) for r in rels]+[(xu(r)-9.5, Y(r)) for r in reversed(rels)]
    band = cl_hull.ccw(band)
    _skin(kit, f'Drive pod {side} shoulder band', secs, .3, _plan_prism(kit, 'clip', band, -2, 40, kit.pale), kit.pale)
    # Aft deck plate over the thrust frame (the sprite's lighter aft panel).
    deck = _skin(kit, f'Drive pod {side} aft deck plate', secs, .25,
                 _block(kit, 'clip', -116.2, -102.4, -200, 200, 3, 40, mats['deck']), mats['deck'], 0)
    _cut(deck, _xz_prism(kit, 'aft top', [(x, z+.25) for x, z in AFT_TOP], -200, 200, mats['deck']))
    b = deck.modifiers.new('Plate edge chamfer', 'BEVEL')
    b.width, b.segments, b.limit_method = .08, 1, 'ANGLE'
    # Bolted access hatch on the fore top, ahead of the shoulder band.
    fz = _top(kit, -45.5, Y(55.5))
    hy0, hy1 = sorted((Y(51), Y(60)))
    _block(kit, f'Drive pod {side} fore access hatch', -49, -42.2, hy0, hy1, fz-.3, fz+.3, kit.maroon, .15)
    _studs(kit, f'Drive pod {side} fore access hatch bolts',
           [(x, Y(r), fz+.3) for x in (-48.4, -42.8) for r in (51.6, 55.5, 59.4)], ('Z', 1), .17, .2, kit.steel)
    # Inspection ring on the top, just aft of the band.
    ix, iy = -69, Y(62.5)
    z = _top(kit, ix, iy)
    _revolve_z(kit, f'Drive pod {side} inspection ring', [(1.7, -.3), (2.7, -.3), (2.7, .35), (2.3, .55), (1.7, .55)],
               ix, iy, z, kit.steel, n=20)
    _revolve_z(kit, f'Drive pod {side} inspection hatch', [(0, -.2), (1.75, -.2), (1.75, .2), (0, .3)], ix, iy, z,
               kit.dark, n=20)
    _studs(kit, f'Drive pod {side} inspection ring bolts',
           [(ix+2.25*math.cos(a), iy+2.25*math.sin(a), z+.5) for a in (k*math.pi/4 for k in range(8))],
           ('Z', 1), .17, .2, kit.steel)
    # Conduit strap running aft from the ring.
    sy0, sy1 = sorted((Y(63.5), Y(67)))
    _block(kit, f'Drive pod {side} conduit strap', -106, -71.4, sy0, sy1, z-.4, z+.55, kit.steel, .2)
    _studs(kit, f'Drive pod {side} conduit strap bolts',
           [(x, Y(r), z+.55) for x in range(-104, -72, 4) for r in (63.95, 66.55)], ('Z', 1), .16, .18, kit.steel)
    # Round inspection port on the outboard shoulder, breaking the outline.
    px, pz = -71, 1.5
    a, b, h = _env(px)
    ya, yb = Y(b-1.5), Y(b+1.2)
    _rod(kit, f'Drive pod {side} outboard inspection port', (px, ya, pz), (px, yb, pz), 2.7, kit.steel, n=16)
    _rod(kit, f'Drive pod {side} outboard inspection port flange', (px, Y(b-.4), pz), (px, Y(b+.5), pz), 3.2,
         kit.steel, n=16)
    _rod(kit, f'Drive pod {side} outboard inspection port cover', (px, Y(b+1.1), pz), (px, Y(b+1.45), pz), 1.9,
         kit.dark, n=16)
    _studs(kit, f'Drive pod {side} outboard inspection port bolts',
           [(px+2.3*math.cos(a), Y(b+1.2), pz+2.3*math.sin(a)) for a in (k*math.pi/3 for k in range(6))],
           ('Y', s), .16, .18, kit.steel)
    # Heat-exchange fins standing in the inboard recess.
    a, b, h = _env(-96)
    for k, x in enumerate((-100.2, -98.1, -96.0, -93.9, -91.8), 1):
        _block(kit, f'Drive pod {side} heat-exchange fin {k}', x-.35, x+.35, fy0+.25, fy1-.25, h-1.05, h+.05,
               kit.steel, .08)
    # Bolted service cover on the top inboard panel (between the strap and the rim).
    cz = _top(kit, -81, Y(55))
    cy0, cy1 = sorted((Y(49.4), Y(60.8)))
    _block(kit, f'Drive pod {side} top service cover', -86.6, -75.6, cy0, cy1, cz-.3, cz+.3, kit.maroon, .15)
    _studs(kit, f'Drive pod {side} top service cover bolts',
           [(x, Y(r), cz+.3) for x in (-86, -81.1, -76.2) for r in (50, 55.1, 60.2) if not (x == -81.1 and r == 55.1)],
           ('Z', 1), .17, .2, kit.steel)
    # Bolted service cover on the outboard wall, aft of the launcher.
    a, b, h = _env(-99)
    wy = Y(b)
    w0, w1 = sorted((wy-s*.3, wy+s*.3))
    _block(kit, f'Drive pod {side} wall service cover', -105, -93, w0, w1, -1.8, 8.2, kit.maroon, .15)
    _studs(kit, f'Drive pod {side} wall service cover bolts',
           [(x, wy+s*.3, zz) for x in (-104.3, -99, -93.7) for zz in (-1.1, 7.5)], ('Y', s), .17, .2, kit.steel)
    # Round coupling on the inboard side where the outline bulges toward the spine.
    a, b, h = _env(-104.5)
    _rod(kit, f'Drive pod {side} inboard coupling', (-104.5, Y(a+2), 0), (-104.5, Y(a-2.2), 0), 3.0, kit.steel, n=16)
    _rod(kit, f'Drive pod {side} inboard coupling collar', (-104.5, Y(a-1.4), 0), (-104.5, Y(a-2.5), 0), 2.0,
         kit.dark, n=16)

    # Thrust frame on the aft bulkhead: ribs between and beside the bells, a beam above.
    edges = [by0+.5]+[(p+q)/2 for p, q in zip(sorted(ys), sorted(ys)[1:])]+[by1-.5]
    for k, yy in enumerate(edges, 1):
        _block(kit, f'Drive pod {side} thrust frame rib {k}', BULKHEAD_X-1.1, BULKHEAD_X+.4, yy-.45, yy+.45,
               SILL_Z, 5.8, kit.steel, .1)
    _block(kit, f'Drive pod {side} thrust frame beam', BULKHEAD_X-1.0, BULKHEAD_X+.4, by0+.1, by1-.1, 4.1, 5.1,
           kit.steel, .1)
    # Heat-shield sill under the bells: a raised lip at the bay's aft edge.
    _block(kit, f'Drive pod {side} heat-shield sill', -125.3, -123.0, by0+.05, by1-.05, SILL_Z-.4, SILL_Z+.45,
           mats['shield'], .12)


def nozzle(kit, i, y, mats):
    m, t, w = BELL['mouth_r'], BELL['throat_r'], BELL['wall']
    ss = [k/10 for k in range(11)]
    outer = [(_bell_x(u), _bell_r(u)+w) for u in reversed(ss)]
    inner = [(_bell_x(u), _bell_r(u)) for u in ss]
    revolve_x(kit, f'Engine {i} aft-facing bell', outer+inner, y, POD_Z, kit.steel, n=24)
    lip_x = MOUTH_X
    revolve_x(kit, f'Engine {i} mouth lip', [(lip_x-.18, m-.08), (lip_x-.18, m+.42), (lip_x+.5, m+.42), (lip_x+.5, m-.08)],
              y, POD_Z, mats['lip'], n=24)
    # Liner: a dark skin just inside the bell, closed at the throat.
    ls = [k/10 for k in range(10, -1, -1)]
    liner = [(_bell_x(u*.97), _bell_r(u*.97)-.04) for u in ls]+[(THROAT_X+.12, .01)]
    liner += [(_bell_x(u*.97), _bell_r(u*.97)-.15) for u in reversed(ls)]
    revolve_x(kit, f'Engine {i} dark throat', liner, y, POD_Z, mats['liner'], n=24)
    # Cooling hoops and the heat-shield band round the bell (the sprite's striped nozzles).
    for k, u in enumerate((.86, .3), 1):
        x, r = _bell_x(u), _bell_r(u)+w
        revolve_x(kit, f'Engine {i} cooling hoop {k}', [(x-.22, r-.1), (x-.22, r+.22), (x+.22, r+.22), (x+.22, r-.1)],
                  y, POD_Z, kit.steel, n=24)
    ua, ub = .5, .68
    xa, xb = _bell_x(ua), _bell_x(ub)
    revolve_x(kit, f'Engine {i} heat-shield band', [(xa, _bell_r(ua)+w-.05), (xb, _bell_r(ub)+w-.05),
                                                     (xb, _bell_r(ub)+w+.18), (xa, _bell_r(ua)+w+.18)],
              y, POD_Z, mats['shield'], n=24)
    # Throat ring, combustion chamber and gimbal ring back to the thrust frame.
    revolve_x(kit, f'Engine {i} throat ring', [(THROAT_X-.35, t), (THROAT_X-.35, t+.75), (THROAT_X+.35, t+.75),
                                                (THROAT_X+.35, t)], y, POD_Z, kit.steel, n=20)
    revolve_x(kit, f'Engine {i} combustion chamber', [(THROAT_X+.3, 0), (THROAT_X+.3, 1.85), (BULKHEAD_X+.3, 1.85),
                                                       (BULKHEAD_X+.3, 0)], y, POD_Z, mats['chamber'], n=16)
    gx = THROAT_X+1.4
    revolve_x(kit, f'Engine {i} gimbal ring', [(gx-.3, 1.95), (gx-.3, 2.6), (gx+.3, 2.6), (gx+.3, 1.95)],
              y, POD_Z, kit.steel, n=20)
    # Two actuators from the thrust frame to the forward cooling hoop, splayed over the top.
    ux = .3
    hx, hr = _bell_x(ux), _bell_r(ux)+w+.22
    for k, ang in enumerate((40, -40), 1):
        a = math.radians(ang)
        end = (hx, y+hr*math.sin(a), POD_Z+hr*math.cos(a))
        root = (BULKHEAD_X-.9, y+1.95*math.sin(a)*1.05, POD_Z+2.9)
        mid = tuple(p+(q-p)*.5 for p, q in zip(root, end))
        _rod(kit, f'Engine {i} actuator {k} cylinder', root, mid, .34, kit.steel)
        _rod(kit, f'Engine {i} actuator {k} rod', mid, end, .17, mats['lip'])
    loc = (MOUTH_X, y, POD_Z)
    kit.empty(f'Engine {i} exhaust anchor', loc, 180)
    ANCHORS[i] = (loc, (-1.0, 0.0, 0.0))


def build(kit):
    ANCHORS.clear()
    mats = {
        'liner': material('Drive bell liner', (.085, .09, .095), .75, .42),
        'lip': material('Drive nozzle bright metal', (.42, .41, .4), .8, .35),
        'shield': material('Drive heat-shield band', (.30, .27, .25), .6, .5),
        'deck': material('Drive pod worn aft plate', (.46, .33, .30), .25, .55),
        'chamber': material('Drive chamber dark metal', (.07, .07, .075), .7, .5),
    }
    for side, s in cl_hull.SIDES:
        pod(kit, side, s, mats)
    for i, (x, y, w, angle) in enumerate(ENGINES, 1):
        nozzle(kit, i, y, mats)
