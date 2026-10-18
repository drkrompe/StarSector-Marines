"""The Hegemony armour: the orange and dark grey plates onslaught_hegemony.png lays
over the hull, built as sloped armour that steps down from the gun tiers to the
hull's edges (owner, pass 3: terracing, not flat deck and plating).

  prow shoulders  round 3: one orange band skinned on the prow's shoulder plane
                  (os_hull.fore_top) from the gallery's foot over the upper chine,
                  in three segments V-grooved at their seams (not blocks), and a
                  heavier strap (the prow collar) where the prow meets the core;
  glacis          the dark grey A-legs, thicker, from the prow root aft and
                  outboard to the citadel, and the orange band carried on outboard
                  of them to X 4: a terraced step down from grey to orange;
  arm belts       sloped plates along each arm's outer edge, falling from the
                  gun gallery to the hull edge: orange forward, dark grey
                  amidships, orange over the Devastator, dark grey aft (the
                  sprite's alternation), in segments with seam gaps;
  arm steps       the sprite's dark grey sawtooth blocks stepping down outboard
                  of the aft belt (X 100..2);
  arm coamings    orange along each arm's inner edge facing the notch;
  nose cheeks     grey cheeks flanking each TPC barrel at the arm nose;
  broadside fins  the orange fin at each arm's root by WS 010/011, in two tiers;
  leg stripes     orange along each aft leg's inner edge;
  waist bay       a dark bay frame and amber bay lamps in each waist valley;
  core aft face   buttresses and a lintel above the machinery wells;
  keel strakes    bars following the belly's rounded underside (three strakes and
                  two frames), and under the prow, aft body and tail following
                  each keel curve.
"""
import math
import bpy
from os_kit import arched_mass, ccw, clip_rect, facet_normal, loft_x, mirror_y, plane_cut, prism, strip_plan, _left
from os_hull import (AFT, AFT_OUTER, ARM, ARM_INNER, ARM_OUTER, CORE, CORE_HALF, FORE_STATIONS, FORE_W, FORE_YI, GALLERY_W, LEG_PORT,
                     PROW, PROW_HALF, TAIL, TAIL_HALF, WAIST_DECK, WELL_FLOOR, _table, fore, fore_side_y, fore_top, full,
                     keel_curve)


def offset(points, d):
    """The polyline moved d to the left of travel (inboard, for a port edge listed bow to stern)."""
    out = []
    for i, (x, y) in enumerate(points):
        a, b = points[max(i-1, 0)], points[min(i+1, len(points)-1)]
        nx, ny = _left(b[0]-a[0], b[1]-a[1])
        out.append((x+nx*d, y+ny*d))
    return out


def clip_x(points, x_hi, x_lo):
    """The part of a polyline (X decreasing) between x_hi and x_lo."""
    out = []
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        for x in (x_hi, x_lo):
            if (ax-x)*(bx-x) < 0:
                t = (ax-x)/(ax-bx)
                out.append((x, ay+(by-ay)*t))
        if x_lo <= bx <= x_hi:
            out.append((bx, by))
    if x_lo <= points[0][0] <= x_hi:
        out.insert(0, points[0])
    out.sort(key=lambda p: -p[0])
    return out


def slab(z0, z1, c=1.2):
    return [(0.0, z0), (0.0, z1-c), (c, z1)]


def ridge(z0, foot, crest, width):
    """A sloped armour ridge: plates rising from `foot` to `crest`, rounded over the top."""
    return ('ridge', z0, foot, crest, width)


def band(kit, name, edge, inward, width, profile, mat, s):
    centre = offset(edge, inward+width/2)
    plan = ccw(strip_plan(centre, width))
    if s < 0:
        plan = mirror_y(plan)
    if profile[0] == 'ridge':
        _, z0, foot, crest, w = profile
        return arched_mass(kit, name, plan, z0, crest, tumble_in=w*.5, tumble_h=crest-foot, mat=mat, step=1.5)
    return prism(kit, name, plan, profile, mat, bevel=.2)


def _side(plan, s):
    plan = ccw(plan)
    return plan if s > 0 else mirror_y(plan)


def _bar(kit, name, poly, z0, z1, mat):
    return prism(kit, name, ccw(poly), [(0.0, z0), (0.0, z1-.8), (.8, z1)], mat, bevel=.2)


def bottom(name):
    """The flat bottom face of a built hull mass, as a plan polygon, and its z."""
    ob = bpy.data.objects[name]
    me = ob.data
    f = min(me.polygons, key=lambda p: (p.center.z, -p.area))
    return [tuple((ob.matrix_world @ me.vertices[i].co)[:2]) for i in f.vertices][::-1], (ob.matrix_world @ f.center).z


def shell_strip(kit, name, src, axis, c, w, drop, mat, span=None, xr=None):
    """A bar following a hull's curved underside: a copy of `src` dropped by `drop`,
    clipped to |axis - c| < w (axis 0 = X, 1 = Y) and, given `span`, to |other| < span.
    Its top stays buried in the hull it copies."""
    ob = src.copy()
    ob.data = src.data.copy()
    ob.modifiers.clear()
    bpy.context.collection.objects.link(ob)
    ob.location.z -= drop
    # The cutters are 200-unit cubes: centre them on the source so they span it.
    mid = [sum(v[i] for v in src.bound_box)/8 + src.location[i] for i in range(3)]

    def cut(along, at, sign, tag):
        p = list(mid)
        p[along] = at
        n = [0.0, 0.0, 0.0]
        n[along] = sign
        plane_cut(ob, name+' '+tag, tuple(p), tuple(n), size=600)
    cut(axis, c+w, 1.0, 'side')
    cut(axis, c-w, -1.0, 'side')
    if span:
        cut(1-axis, span, 1.0, 'end')
        cut(1-axis, -span, -1.0, 'end')
    if xr:                          # stop short of the mass's own end faces (no coplanar faces)
        cut(0, xr[1], 1.0, 'end')
        cut(0, xr[0], -1.0, 'end')
    ob.data.materials.clear()
    return kit.finish(ob, name, mat, .2)


def keel_strakes(kit):
    # Bars following each underside's curve: three strakes and two frames on the
    # belly and a centre strake under the bow (the stern's are os_stern's).
    ob = bpy.data.objects
    for k, y in enumerate((0, 22, -22), 1):
        shell_strip(kit, f'Armour core keel strake {k}', ob['Hull belly'], 1, y, 2.6, 1.6 if y else 2.4, kit.grey,
                    xr=(-80, 166))
    for k, x in enumerate((22, 50), 1):
        shell_strip(kit, f'Armour core keel frame {k}', ob['Hull belly'], 0, x, 2.2, 1.2, kit.grey, span=40.0)
    shell_strip(kit, 'Armour prow keel strake', ob['Hull prow'], 1, 0, 2.6, 2.4, kit.grey, xr=(100, 216))


# --- forebody armour skins ------------------------------------------------------
# Owner, round 3: "look closely at the pixel art". The sprite paints the prow's
# flanks as one broad orange band from the bow to the prow root, carried on
# outboard of the A-shaped dark grey glacis legs back to the citadel, broken by
# dark seam lines, not separate blocks. So each band is a skin lofted on the
# forebody's own shoulder plane (os_hull.fore_top): a constant thickness proud
# of it, wrapped over the upper chine with a small lip, and V-grooved at the
# seams and ends so neighbouring segments read as one band with seams. The grey
# legs stand thicker than the orange outboard of them: a terraced step down.

def _skin_section(x, ya, yb, t, wrap, lip=.8):
    f = fore(x)
    ya, yb = max(ya, 0.0), min(yb, f['w'])
    yk = min(max(f['yi'], ya+.2), yb-.2)
    top = [(ya, fore_top(x, ya)+t), (yk, fore_top(x, yk)+t)]
    bot = [(yk, fore_top(x, yk)-.8), (ya, fore_top(x, ya)-.8)]
    if wrap:
        out = [(f['w']+lip, f['zk']+t*.7), (fore_side_y(x, f['zk']-3.0)-.3, f['zk']-3.0)]
    else:
        out = [(yb, fore_top(x, yb)+t), (yb, fore_top(x, yb)-.8)]
    return top+out+bot


def skin(kit, name, x0, x1, inner, outer, t, mat, s, wrap=False, seams=(), lip=.8, thin=.6):
    """An armour skin on the forebody's top between X x0 > x1, from |Y| inner(x) to outer(x)
    (outer=None: the plan edge, wrapped over the chine). Thin at its ends and at each seam."""
    xs = {x0, x1, x0-.6, x1+.6}
    for sx in seams:
        xs |= {sx-.5, sx, sx+.5}
    for bx in [b for b, _ in FORE_W]:      # only where the plan bends: broad plates, not tiles
        if x1 < bx < x0:
            xs.add(bx)
    stations = []
    for x in sorted(xs, reverse=True):
        if not x1 <= x <= x0:
            continue
        groove = abs(x-x0) < .01 or abs(x-x1) < .01 or any(abs(x-sx) < .01 for sx in seams)
        yb = outer(x) if outer else fore(x)['w']
        sec = _skin_section(x, inner(x), yb, thin if groove else t, outer is None or wrap, lip)
        if s < 0:
            sec = [(-y, z) for y, z in reversed(sec)]
        stations.append((x, sec))
    return loft_x(kit, name, stations, mat, bevel=.15)


def glacis_split(x):
    """|Y| where the grey glacis leg gives way to the orange band outboard of it."""
    f = fore(x)
    return f['yi']+.42*(f['w']-f['yi'])


def build(kit):
    for side, s in (('port', 1), ('starboard', -1)):
        # Prow shoulders: the orange band from the gallery's foot over the upper chine.
        gal = lambda x: _table(GALLERY_W, x)+.6
        for k, (xa, xb, seam) in enumerate(((213.0, 175.3, 194.0), (174.7, 140.3, 157.0), (139.7, 111.0, 125.0)), 1):
            skin(kit, f'Armour {side} prow shoulder {k}', xa, xb, gal, None, 1.4, kit.orange, s, seams=(seam,))
        # Prow collar: a heavier strap across the band where the prow meets the core.
        skin(kit, f'Armour {side} prow collar', 111.0, 103.4,
             lambda x: min(gal(x), glacis_split(x)-.3) if x >= 104 else glacis_split(x)-.3, None, 2.6, kit.orange, s,
             lip=1.2)
        # Glacis: the dark grey leg (thicker, so the orange outboard steps down from
        # it) and the orange band carried on outboard of it to the citadel's flank.
        skin(kit, f'Armour {side} glacis grey band', 108.0, 24.0, lambda x: max(fore(x)['yi']-6.0, 9.0),
             glacis_split, 2.8, kit.grey, s, seams=(92, 76, 60, 44), thin=1.4)
        skin(kit, f'Armour {side} glacis band', 104.0, 4.0, lambda x: glacis_split(x)-.4, None, 1.4, kit.orange, s,
             seams=(80, 56, 30))
        # Waist bay: a dark frame on the valley deck carrying three amber bay lamps.
        kit.box(f'Armour {side} waist bay frame', (101.5, s*48.5, WAIST_DECK+.7), (6.0, 7.0, 1.6), kit.dark,
                bevel=.2)
        for k, y in enumerate((46.4, 48.5, 50.6), 1):
            kit.box(f'Armour {side} waist bay lamp {k}', (100.5, s*y, WAIST_DECK+1.85), (1.6, 1.7, .9), kit.lamp,
                    bevel=0)
        # Core aft face above the machinery well: buttresses standing on the well
        # floor, raked aft, their tops following the deck, and a coaming capping
        # the face's top edge.
        for k, y in enumerate((30, 50, 72), 1):
            top = fore_top(-18.0, y)+1.0
            b = prism(kit, f'Armour {side} core aft buttress {k}', _side([(-17.0, y-2.25), (-17.0, y+2.25),
                                                                         (-23.5, y+2.25), (-23.5, y-2.25)], s),
                      [(0.0, WELL_FLOOR-.6), (0.0, top+6.0)], kit.grey, bevel=.3)
            plane_cut(b, 'buttress rake', (-23.5, 0, WELL_FLOOR), (-.99, 0, .15))
            f = fore(-18.0)
            dz = (f['zk']-f['zi'])/(f['w']-f['yi']) if y > f['yi'] else 0.0
            plane_cut(b, 'buttress top', (-18.0, s*y, top), (0, -s*dz, 1.0))
        f = fore(-18.0)
        ys = [22.0, f['yi'], f['w']-1.5]
        sec = [(y, fore_top(-18.0, y)+1.4) for y in ys]+[(y, fore_top(-18.0, y)-2.0) for y in reversed(ys)]
        if s < 0:
            sec = [(-y, z) for y, z in reversed(sec)]
        loft_x(kit, f'Armour {side} core aft lintel', [(-13.5, sec), (-18.8, sec)], kit.grey, bevel=.3)
    keel_strakes(kit)
