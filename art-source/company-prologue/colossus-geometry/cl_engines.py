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
# Bolt studs, revolves, rods, bent rails, the RCS quad and the work-lamp housing: the shared kit's.
from ship_detail_kit import (studs as _studs, revolve_at as _revolve_z, rod as _rod, revolve_dir as _revolve_dir,
                             join as _join, bent_rail as _rail, rcs_quad as _rcs_quad, lamp_housing as _lamp_housing)

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
HUMP_X = (-113.0, -88.0)  # dorsal drive housing, aft to fore
STRAKES = ((6.4, 8.0), (-8.0, -6.4))  # rubbing strakes on the outboard wall (z0, z1)
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
    _block(kit, f'Drive pod {side} conduit strap', HUMP_X[1]-.6, -71.4, sy0, sy1, z-.4, z+.55, kit.steel, .2)
    _studs(kit, f'Drive pod {side} conduit strap bolts',
           [(x, Y(r), z+.55) for x in range(-86, -72, 4) for r in (63.95, 66.55)], ('Z', 1), .16, .18, kit.steel)
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
    _block(kit, f'Drive pod {side} wall service cover', -105, -93, w0, w1, -4.6, 5.0, kit.maroon, .15)
    _studs(kit, f'Drive pod {side} wall service cover bolts',
           [(x, wy+s*.3, zz) for x in (-104.3, -99, -93.7) for zz in (-3.9, 4.3)], ('Y', s), .17, .2, kit.steel)
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
    tug(kit, side, s, mats, secs, casing, ys, by0, by1)


# --- pass 3 round 5: the tug drive unit (owner: "our tug boat engine sections need some more work") ---
#
# Each pod is a working tug: below it a drive keel (skeg) between two underslung propellant tanks,
# above it a dorsal drive housing with radiator banks, rubbing strakes let flush into the outboard
# wall, rubber push knees on the nose and a keel thrust shoe where the thrust yoke picks up, RCS
# quads at the corners, ladders and handrails at person scale (3.6 units = 1.8 m), unlit work-lamp
# housings, and in the drive end a bay surround, cheek armour, heat-shield liners, a propellant
# manifold with a turbopump under each chamber, and feed lines from the tanks. Nothing here
# changes the pod's plan outline beyond a unit at the nose pads and the bay posts.

TANK_R = 6.0
TANKS = (('inboard', 55.0, -50.0, -100.0), ('outboard', 71.0, -66.0, -102.0))  # rel y, cylinder x fore/aft
TANK_Z = -21.0
DOME = 4.0
SKEG = (61.6, 64.4)          # rel y of the drive keel's faces
SKEG_XZ = [(-44, -9), (-44, -14), (-53, -27.5), (-59, -31), (-99, -31), (-108, -25), (-115, -11), (-115, -9)]
HUMP_REL = (52.0, 74.0)
HUMP_TOP = 27.0


def _wall_strip(kit, name, s, x0, x1, z0, z1, d0, d1, mat, bevel=.1, taper=1.2):
    """A strip lofted along the outboard wall from x0 aft to x1, wall offset d0..d1 (rel y), z0..z1."""
    xs = [x0, x0-taper]+[x for x, *_ in ENVELOPE if x1+taper < x < x0-taper]+[x1+taper, x1]
    stations = []
    for x in sorted(set(xs)):
        b = _env(x)[1]
        e = (d1-d0)*.55 if x in (x0, x1) else 0      # the ends taper back into the wall
        c = min(.45, (z1-z0)*.3)
        sec = [(b+d0, z0), (b+d1-e-c, z0), (b+d1-e, z0+c), (b+d1-e, z1-c), (b+d1-e-c, z1), (b+d0, z1)]
        stations.append((x, [(cl_hull.y(r, s), z) for r, z in sec]))
    return loft_x(kit, name, stations, mat, bevel)


def _hit(ob, x, y, top=60):
    """Height where a ray dropped at (x, y) meets one object, or None."""
    bpy.context.view_layer.update()
    ev = ob.evaluated_get(bpy.context.evaluated_depsgraph_get())
    hit, p, _, _ = ev.ray_cast(ev.matrix_world.inverted() @ Vector((x, y, top)), Vector((0, 0, -1)))
    return (ev.matrix_world @ p).z if hit else None


def tug(kit, side, s, mats, secs, casing, ys, by0, by1):
    Y = lambda rel: cl_hull.y(rel, s)
    rubber = mats['rubber']

    # Rubbing strakes, let into channels on the outboard wall so they stand flush with the outline.
    for k, (z0, z1) in enumerate(STRAKES, 1):
        _cut(casing, _wall_strip(kit, 'channel', s, -37.6, -112.5, z0-.35, z1+.35, -1.6, 2, kit.dark, 0, .01))
        _wall_strip(kit, f'Drive pod {side} rubbing strake {k}', s, -38, -112, z0, z1, -1.4, .1, kit.steel, .12, 1.4)
        for j, x in enumerate((-46, -58, -80, -94, -106), 1):
            b = _env(x)[1]
            _studs(kit, f'Drive pod {side} rubbing strake {k} bolts {j}',
                   [(x+dx, Y(b+.1), (z0+z1)/2) for dx in (-.9, .9)], ('Y', s), .16, .16, kit.pale)

    # Push knee: a rubber bumper pad on the nose face outboard of where the yoke picks up (the
    # inboard half of the nose is taken by the waist truss).
    for k, (r0, r1, zh) in enumerate(((64.0, 69.6, 6.0),), 1):
        y0, y1 = sorted((Y(r0), Y(r1)))
        _block(kit, f'Drive pod {side} push knee {k} backing plate', -31.0, -29.6, y0-.3, y1+.3, -zh-.6, zh+.6,
               kit.steel, .15)
        _block(kit, f'Drive pod {side} push knee {k} bumper', -29.7, -28.9, y0, y1, -zh, zh, rubber, .25)
        _studs(kit, f'Drive pod {side} push knee {k} bolts',
               [(-29.6, yy, zz) for yy in (y0-.05, y1+.05) for zz in (-zh-.2, 0, zh+.2)], ('X', 1), .14, .2, kit.pale)
    # Keel thrust shoe just aft of where the yoke's keel struts leave the pod.
    y0, y1 = sorted((Y(51), Y(63)))
    _block(kit, f'Drive pod {side} keel thrust shoe', -47.5, -40.2, y0, y1, -18.6, -12.5, kit.steel, .3)
    _block(kit, f'Drive pod {side} keel thrust shoe bearing face', -40.6, -39.9, y0+.8, y1-.8, -18.0, -14.0,
           kit.dark, .1)
    _studs(kit, f'Drive pod {side} keel thrust shoe bolts',
           [(x, yy, -18.6) for x in (-46.5, -43.8, -41.2) for yy in (y0+.7, (y0+y1)/2, y1-.7)], ('Z', -1),
           .2, .2, kit.pale)

    # Drive keel (skeg) between the tanks, with a wear shoe, stiffener ribs and bolted access plates.
    k0, k1 = sorted((Y(SKEG[0]), Y(SKEG[1])))
    keel = _xz_prism(kit, f'Drive pod {side} drive keel', SKEG_XZ, k0, k1, kit.maroon)
    kb = keel.modifiers.new('Plate edge chamfer', 'BEVEL')
    kb.width, kb.segments, kb.limit_method = .3, 2, 'ANGLE'
    _xz_prism(kit, f'Drive pod {side} drive keel wear shoe',
              [(-58.4, -30.6), (-99.4, -30.6), (-99.8, -31.9), (-58.0, -31.9)], k0-.35, k1+.35, kit.steel)
    for j, x in enumerate((-64, -73, -82, -91), 1):
        _block(kit, f'Drive pod {side} drive keel stiffener {j}', x-.4, x+.4, k0-.4, k1+.4, -30.4, -16, kit.steel, .1)
    for j, (xa, xb) in enumerate(((-71.5, -65.5), (-89.5, -83.5)), 1):
        _block(kit, f'Drive pod {side} drive keel access plate {j}', xa, xb, k0-.25, k1+.25, -27.6, -21.6,
               kit.maroon, .12)
        _studs(kit, f'Drive pod {side} drive keel access plate {j} bolts',
               [(x, yy, zz) for x in (xa+.5, (xa+xb)/2, xb-.5) for zz in (-27.1, -22.1) for yy in (k0-.25, k1+.25)],
               ('X', 1), .14, .14, kit.pale)

    # Underslung propellant tanks: domed cylinders in saddle straps, with weld seams.
    for name, rel, xf, xa in TANKS:
        cy = Y(rel)
        prof = [(xa-DOME*math.cos(math.pi/2*k/6), TANK_R*math.sin(math.pi/2*k/6)) for k in range(7)]
        prof += [(xf+DOME*math.sin(math.pi/2*k/6), TANK_R*math.cos(math.pi/2*k/6)) for k in range(7)]
        revolve_x(kit, f'Drive pod {side} {name} propellant tank', prof, cy, TANK_Z, kit.pale, n=36)
        straps = [xf-2.5+(xa-xf+5)*k/3 for k in range(4)]
        for j, x in enumerate(straps, 1):
            revolve_x(kit, f'Drive pod {side} {name} tank strap {j}',
                      [(x-.5, TANK_R-.1), (x-.5, TANK_R+.35), (x-.3, TANK_R+.5), (x+.3, TANK_R+.5), (x+.5, TANK_R+.35),
                       (x+.5, TANK_R-.1)], cy, TANK_Z, kit.steel, n=36)
            a = math.radians(-35)
            _studs(kit, f'Drive pod {side} {name} tank strap {j} tensioner',
                   [(x-.3, cy+s*(TANK_R+.45)*math.cos(a), TANK_Z+(TANK_R+.45)*math.sin(a))],
                   ('X', 1), .45, .6, kit.dark, n=8)
        for j, (p, q) in enumerate(zip(straps, straps[1:]), 1):
            x = (p+q)/2
            revolve_x(kit, f'Drive pod {side} {name} tank weld seam {j}',
                      [(x-.12, TANK_R-.05), (x-.12, TANK_R+.1), (x+.12, TANK_R+.1), (x+.12, TANK_R-.05)],
                      cy, TANK_Z, kit.steel, n=36)
        for j, x in enumerate((xf+DOME-.4, xa-DOME+.4), 1):
            revolve_x(kit, f'Drive pod {side} {name} tank pole fitting {j}',
                      [(x-.7, 0), (x-.7, .9), (x+.7, .9), (x+.7, 0)], cy, TANK_Z, kit.steel, n=20)
        # Feed line from the aft dome up into the drive end's keel.
        tail = xa-DOME
        dy = -2.5 if name == 'outboard' else 1.0
        pts = [(tail+.2, cy, TANK_Z), (tail-3.0, cy, TANK_Z), (tail-6.0, cy+s*dy*.5, TANK_Z+3.5),
               (-114.5, cy+s*dy, -9.5)]
        _rail(kit, f'Drive pod {side} {name} feed line', pts, .55, kit.steel, n=16)
        for j, p in enumerate(pts[1:3], 1):
            revolve_x(kit, f'Drive pod {side} {name} feed line flange {j}',
                      [(p[0]-.25, 0), (p[0]-.25, .85), (p[0]+.25, .85), (p[0]+.25, 0)], p[1], p[2], kit.dark, n=16)

    # Dorsal drive housing: a raised hump over the drive's machinery, radiator banks on both flanks.
    h0, h1 = sorted((Y(HUMP_REL[0]), Y(HUMP_REL[1])))
    # It runs aft down the drive end's sloping top, so its tail is a raked fairing over the aft deck.
    stations = [(HUMP_X[0], octagon(h0+3, h1-3, 7.5, 13.5, 2)),
                (-108.0, octagon(h0+1, h1-1, 10.0, HUMP_TOP-5, 3)),
                (-104.0, octagon(h0, h1, 12.0, HUMP_TOP, 3.4)),
                (HUMP_X[1]-2.5, octagon(h0, h1, 13.5, HUMP_TOP, 3.4)),
                (HUMP_X[1], octagon(h0+1, h1-1, 13.5, HUMP_TOP-3, 2.6))]
    loft_x(kit, f'Drive pod {side} drive housing', stations, kit.maroon, .15)
    _skin(kit, f'Drive pod {side} drive housing band', stations, .25,
          _block(kit, 'clip', -98.2, -95.4, -200, 200, 16.3, 40, kit.pale), kit.pale)
    _skin(kit, f'Drive pod {side} drive housing coaming', stations, .45,
          _block(kit, 'clip', -200, 0, -200, 200, 15.6, 17.0, kit.steel), kit.steel)
    # Bolted plates laid over the raked tail: a hatch on the slope and a service cover on each flank.
    hump = bpy.data.objects[f'Drive pod {side} drive housing']
    r0, r1 = sorted((Y(HUMP_REL[0]+6), Y(HUMP_REL[1]-6)))
    _skin(kit, f'Drive pod {side} drive housing tail hatch', stations, .3,
          _block(kit, 'clip', -111.2, -105.4, r0, r1, 14.5, 40, kit.maroon), kit.maroon, .1)
    for j, (ra, rb) in enumerate(((HUMP_REL[0]-2, HUMP_REL[0]+3.2), (HUMP_REL[1]-3.2, HUMP_REL[1]+2)), 1):
        q0, q1 = sorted((Y(ra), Y(rb)))
        _skin(kit, f'Drive pod {side} drive housing flank cover {j}', stations, .3,
              _block(kit, 'clip', -111.6, -104.6, q0, q1, 13.2, 19.4, kit.maroon), kit.maroon, .1)
    bolts = []
    for x in (-110.6, -108.3, -106.0):
        for rr in (HUMP_REL[0]+6.6, (HUMP_REL[0]+HUMP_REL[1])/2, HUMP_REL[1]-6.6):
            h = _hit(hump, x, Y(rr))
            if h is not None:
                bolts.append((x, Y(rr), h+.28))
    _studs(kit, f'Drive pod {side} drive housing tail hatch bolts', bolts, ('Z', 1), .17, .18, kit.steel)
    xr0, xr1 = -102.6, -91.0
    for face, yy, sg in (('inboard', Y(HUMP_REL[0]), -s), ('outboard', Y(HUMP_REL[1]), s)):
        # Radiator bank: a frame proud of the flank holding a row of vertical fins.
        f0, f1 = sorted((yy-sg*.3, yy+sg*.7))
        _block(kit, f'Drive pod {side} drive housing {face} radiator frame top', xr0-.4, xr1+.4, f0, f1, 24.4, 25.2,
               kit.steel, .1)
        _block(kit, f'Drive pod {side} drive housing {face} radiator frame foot', xr0-.4, xr1+.4, f0, f1, 17.0,
               17.8, kit.steel, .1)
        fins = []
        n = 12
        for j in range(n):
            x = xr0+(xr1-xr0)*(j+.5)/n
            fins.append(_block(kit, 'fin', x-.13, x+.13, *sorted((yy-sg*.3, yy+sg*1.0)), 17.6, 24.6, kit.dark, .04))
        _join(fins, f'Drive pod {side} drive housing {face} radiator fins')
    # Top: bolted access cover, two lifting eyes, a vent and the handrail round it.
    tx0, tx1 = -103.2, -91.0
    ty0, ty1 = sorted((Y(HUMP_REL[0]+3.3), Y(HUMP_REL[1]-3.3)))
    cy0, cy1 = sorted((Y(HUMP_REL[0]+5), Y(HUMP_REL[0]+11)))
    _block(kit, f'Drive pod {side} drive housing top cover', tx0+1.2, tx1-1.2, cy0, cy1, HUMP_TOP-.3,
           HUMP_TOP+.35, kit.maroon, .12)
    _studs(kit, f'Drive pod {side} drive housing top cover bolts',
           [(x, yy, HUMP_TOP+.35) for x in (tx0+1.7, (tx0+tx1)/2, tx1-1.7) for yy in (cy0+.5, cy1-.5)],
           ('Z', 1), .16, .16, kit.steel)
    vy = Y(HUMP_REL[1]-6.5)
    _revolve_z(kit, f'Drive pod {side} drive housing vent', [(0, -.3), (1.5, -.3), (1.5, 1.6), (1.8, 1.8), (1.8, 2.3),
                                                             (0, 2.3)], -96.8, vy, HUMP_TOP, kit.steel, n=24)
    _revolve_z(kit, f'Drive pod {side} drive housing vent mouth', [(0, 2.25), (1.4, 2.25), (1.4, 2.45), (0, 2.45)],
               -96.8, vy, HUMP_TOP, kit.dark, n=24)
    ey = Y(HUMP_REL[1]-3.0)
    for j, x in enumerate((tx0+1.0, tx1-1.0), 1):
        _rail(kit, f'Drive pod {side} drive housing lifting eye {j}',
              [(x-.6, ey, HUMP_TOP-.2), (x-.6, ey, HUMP_TOP+.9), (x+.6, ey, HUMP_TOP+.9), (x+.6, ey, HUMP_TOP-.2)],
              .18, kit.steel)
    rz = HUMP_TOP+2.2
    ring = [(tx0, ty0), (tx1, ty0), (tx1, ty1), (tx0, ty1)]
    _rail(kit, f'Drive pod {side} drive housing handrail', [(x, yy, rz) for x, yy in ring+ring[:1]], .1, kit.pale)
    _rail(kit, f'Drive pod {side} drive housing handrail mid rail', [(x, yy, rz-1.1) for x, yy in ring+ring[:1]], .08,
          kit.pale)
    posts = []
    for (xa, ya), (xb, yb) in zip(ring, ring[1:]+ring[:1]):
        m = max(1, round(math.hypot(xb-xa, yb-ya)/3.6))
        for j in range(m):
            t = j/m
            p = (xa+(xb-xa)*t, ya+(yb-ya)*t)
            posts.append(_rod(kit, 'post', (*p, HUMP_TOP-.1), (*p, rz), .1, kit.pale))
    _join(posts, f'Drive pod {side} drive housing handrail stanchions')
    # Ladder up the housing's inboard flank, from the pod top to the rail.
    lx, ly = -100.8, Y(HUMP_REL[0]-1.0)
    lz0 = _top(kit, lx, Y(HUMP_REL[0]-1.6))
    parts = [_rod(kit, 'stile', (lx+dx, ly, lz0), (lx+dx, ly, rz), .09, kit.steel) for dx in (-.45, .45)]
    z = lz0+.6
    while z < HUMP_TOP+.2:
        parts.append(_rod(kit, 'rung', (lx-.45, ly, z), (lx+.45, ly, z), .07, kit.steel))
        z += .6
    for zz in (lz0+2.5, HUMP_TOP-1.5):
        for dx in (-.45, .45):
            parts.append(_rod(kit, 'standoff', (lx+dx, ly, zz), (lx+dx, Y(HUMP_REL[0]+.3), zz), .07, kit.steel))
    _join(parts, f'Drive pod {side} drive housing ladder')
    # Work-lamp housings on the housing's fore corners, looking forward along the pod top.
    for j, rel in enumerate((HUMP_REL[0]+2.5, HUMP_REL[1]-2.5), 1):
        _lamp_housing(kit, f'Drive pod {side} work lamp {j}', HUMP_X[1]-.9, Y(rel), HUMP_TOP-2.6, (1, 0, -.15),
                      mats['lip'])

    # Ladder up the outboard wall to the top, between the shoulder band and the inspection port.
    lx = -62.6
    b = max(_env(lx-.5)[1], _env(lx+.5)[1])
    ly, wy = Y(b+.75), Y(b-.3)
    parts = [_rod(kit, 'stile', (lx+dx, ly, -9.0), (lx+dx, ly, 9.6), .09, kit.steel) for dx in (-.45, .45)]
    z = -8.6
    while z < 9.4:
        parts.append(_rod(kit, 'rung', (lx-.45, ly, z), (lx+.45, ly, z), .07, kit.steel))
        z += .6
    for zz in (-8.9, 0.0, 9.3):
        for dx in (-.45, .45):
            parts.append(_rod(kit, 'standoff', (lx+dx, wy, zz), (lx+dx, ly, zz), .07, kit.steel))
    _join(parts, f'Drive pod {side} outboard ladder')
    gz = _top(kit, lx, Y(b-3.4))
    for j, dx in enumerate((-.9, .9), 1):
        _rail(kit, f'Drive pod {side} outboard ladder grab handle {j}',
              [(lx+dx, Y(b-3.4), gz-.4), (lx+dx, Y(b-3.4), gz+1.8), (lx+dx, Y(b-.9), 11.6), (lx+dx, Y(b-.9), 10.0)],
              .1, kit.pale)

    # RCS quads at the four corners of the pod top.
    for j, (x, rel, fs) in enumerate(((-40.8, 66.0, 1), (-40.8, 49.5, 1), (-108.2, 78.5, -1), (-108.2, 47.5, -1)), 1):
        yy = Y(rel)
        _rcs_quad(kit, f'Drive pod {side} RCS quad {j}', x, yy, _top(kit, x, yy), s if rel > 60 else -s, fs, mats)

    # Drive end: heavy bay surround posts and lower beam on the aft face.
    for j, (ya, yb) in enumerate(((by0-1.3, by0), (by1, by1+1.3)), 1):
        _block(kit, f'Drive pod {side} bay surround post {j}', -125.9, -124.2, ya, yb, SILL_Z-1.6, 6.4, kit.steel, .18)
        _studs(kit, f'Drive pod {side} bay surround post {j} bolts',
               [(-125.9, (ya+yb)/2, zz) for zz in (SILL_Z-.8, -.8, 3.0, 5.6)], ('X', -1), .17, .17, kit.pale)
    _block(kit, f'Drive pod {side} bay surround lower beam', -125.9, -124.2, by0-1.3, by1+1.3, SILL_Z-2.4, SILL_Z-.8,
           kit.steel, .18)
    # Heat-shield liners on the bay's side walls.
    for j, (ya, yb) in enumerate(((by0, by0+.35), (by1-.35, by1)), 1):
        _block(kit, f'Drive pod {side} bay heat-shield liner {j}', -124.4, -116.8, ya, yb, SILL_Z+.4, 5.8,
               mats['shield'], .08)
    # Armoured cheek: two layered plates on the outboard cheek's flank.
    o0, o1 = sorted((Y(66.5), Y(120)))
    _skin(kit, f'Drive pod {side} cheek armour', secs, .45,
          _block(kit, 'clip', -124.4, -111.6, o0, o1, -6.5, 5.6, kit.steel), kit.steel)
    _skin(kit, f'Drive pod {side} cheek armour outer plate', secs, .8,
          _block(kit, 'clip', -122.6, -114.0, o0, o1, -4.6, 3.8, kit.red), kit.red)
    _skin(kit, f'Drive pod {side} cheek armour wear strip', secs, 1.0,
          _block(kit, 'clip', -122.2, -114.4, o0, o1, -.6, .4, kit.steel), kit.steel)
    pts = []
    for x in (-123.9, -121.0, -118.0, -115.0, -112.2):
        bw = _env(x)[1]
        pts += [(x, Y(bw+.45), zz) for zz in (-6.0, 5.1)]
    for x in (-122.0, -114.6):
        bw = _env(x)[1]
        pts += [(x, Y(bw+.8), zz) for zz in (-3.9, -2.0, 2.0, 3.2)]
    _studs(kit, f'Drive pod {side} cheek armour bolts', pts, ('Y', s), .17, .18, kit.pale)
    # Propellant manifold across the bay floor, a turbopump under each combustion chamber.
    _revolve_dir(kit, f'Drive pod {side} propellant manifold', [(0, 0), (0, .55), (by1-by0-1.2, .55), (by1-by0-1.2, 0)],
                 (-117.3, by0+.6, -3.75), (0, 1, 0), kit.steel, n=16)
    for i, (_, y, _, _) in enumerate(ENGINES, 1):
        if (y > 0) != (s > 0):
            continue
        revolve_x(kit, f'Drive pod {side} turbopump {i}',
                  [(-117.9, 0), (-117.9, .55), (-117.75, .85), (-116.75, .85), (-116.6, .55), (-116.6, 0)],
                  y, -3.45, mats['chamber'], n=20)
        for k, dy in enumerate((-.9, .9), 1):
            _rail(kit, f'Drive pod {side} turbopump {i} feed {k}',
                  [(-117.25, y, -3.0), (-117.25, y+dy, -2.4), (-117.25, y+dy*.9, -1.5)], .16, mats['lip'], n=10)


def nozzle(kit, i, y, mats):
    m, t, w = BELL['mouth_r'], BELL['throat_r'], BELL['wall']
    ss = [k/10 for k in range(11)]
    outer = [(_bell_x(u), _bell_r(u)+w) for u in reversed(ss)]
    inner = [(_bell_x(u), _bell_r(u)) for u in ss]
    revolve_x(kit, f'Engine {i} aft-facing bell', outer+inner, y, POD_Z, kit.steel, n=40)
    lip_x = MOUTH_X
    revolve_x(kit, f'Engine {i} mouth lip', [(lip_x-.18, m-.08), (lip_x-.18, m+.42), (lip_x+.5, m+.42), (lip_x+.5, m-.08)],
              y, POD_Z, mats['lip'], n=40)
    # Liner: a dark skin just inside the bell, closed at the throat.
    ls = [k/10 for k in range(10, -1, -1)]
    liner = [(_bell_x(u*.97), _bell_r(u*.97)-.04) for u in ls]+[(THROAT_X+.12, .01)]
    liner += [(_bell_x(u*.97), _bell_r(u*.97)-.15) for u in reversed(ls)]
    revolve_x(kit, f'Engine {i} dark throat', liner, y, POD_Z, mats['liner'], n=40)
    # Cooling hoops and the heat-shield band round the bell (the sprite's striped nozzles).
    for k, u in enumerate((.86, .3, .76, .12), 1):
        x, r = _bell_x(u), _bell_r(u)+w
        revolve_x(kit, f'Engine {i} cooling hoop {k}', [(x-.22, r-.1), (x-.22, r+.22), (x+.22, r+.22), (x+.22, r-.1)],
                  y, POD_Z, kit.steel, n=40)
    # Eight stiffener ribs running down the bell between the hoops, clear of the actuators.
    for k in range(8):
        a = math.radians(22.5+45*k)
        pts = [(_bell_x(u), y+(_bell_r(u)+w+.1)*math.cos(a), POD_Z+(_bell_r(u)+w+.1)*math.sin(a))
               for u in (.3, .45, .6, .74, .86)]
        _rail(kit, f'Engine {i} stiffener rib {k+1}', pts, .09, kit.steel, n=6)
    ua, ub = .5, .68
    xa, xb = _bell_x(ua), _bell_x(ub)
    revolve_x(kit, f'Engine {i} heat-shield band', [(xa, _bell_r(ua)+w-.05), (xb, _bell_r(ub)+w-.05),
                                                     (xb, _bell_r(ub)+w+.18), (xa, _bell_r(ua)+w+.18)],
              y, POD_Z, mats['shield'], n=40)
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
        'rubber': material('Drive pod fender rubber', (.035, .035, .033), 0, .9),
    }
    for side, s in cl_hull.SIDES:
        pod(kit, side, s, mats)
    for i, (x, y, w, angle) in enumerate(ENGINES, 1):
        nozzle(kit, i, y, mats)
