"""The Hegemony armour: the orange and dark grey plates onslaught_hegemony.png lays
over the hull, built as sloped armour that steps down from the gun tiers to the
hull's edges (owner, pass 3: terracing, not flat deck and plating).

  prow shoulders  orange ridges along each prow edge, X 102..206, rising from the
                  hull edge toward the prow gallery, so the gallery's turrets sit
                  between two sloped armour flanks; they follow the bow's fall;
  glacis          the shoulders' continuation across the core: an orange ridge
                  from the prow root aft and outboard to X 0, with the dark grey
                  band inboard of it rising toward the round core terrace;
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
  keel strakes    bars under the core, prow, arms, aft body and tail following
                  each keel curve, with frames across the core's flat bottom.
"""
import math
import bpy
from os_kit import arched_mass, ccw, clip_rect, facet_normal, mirror_y, plane_cut, prism, strip_plan, _left
from os_hull import (AFT, AFT_OUTER, ARM, ARM_INNER, ARM_OUTER, CORE, CORE_HALF, LEG_PORT, PROW, PROW_HALF, TAIL,
                     TAIL_HALF, full, keel_curve)


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


def keel_strakes(kit):
    depth = 3.2
    plan, kz = bottom('Hull core')
    for k, y in enumerate((0, 15, -15), 1):
        p = clip_rect(ccw(plan), -200, 300, y-2.6, y+2.6)
        if len(p) > 2:
            _bar(kit, f'Armour core keel strake {k}', p, kz-depth, kz+.5, kit.grey)
    for k, x in enumerate((22, 50), 1):
        p = clip_rect(ccw(plan), x-2.2, x+2.2, -200, 200)
        if len(p) > 2:
            _bar(kit, f'Armour core keel frame {k}', p, kz-depth+1.2, kz+.5, kit.grey)
    plan, kz = bottom('Hull prow')
    p = clip_rect(ccw(plan), 90, 230, -2.6, 2.6)
    if len(p) > 2:
        bar = _bar(kit, 'Armour prow keel strake', p, kz-depth, kz+.5, kit.grey)
        keel_curve(bar, 'prow strake keel', 96, 225, PROW[0]-depth, 30.0, (1, 0))
    plan, kz = bottom('Hull aft body')
    for k, y in enumerate((34, 64, -34, -64), 1):
        p = clip_rect(ccw(plan), -200, 0, y-2.6, y+2.6)
        if len(p) > 2:
            bar = _bar(kit, f'Armour aft keel strake {k}', p, kz-depth, kz+.5, kit.grey)
            keel_curve(bar, 'aft strake keel', -20, -102, AFT[0]-depth, 34.0, (-1, 0))
    plan, kz = bottom('Hull tail')
    p = clip_rect(ccw(plan), -200, 0, -2.6, 2.6)
    if len(p) > 2:
        bar = _bar(kit, 'Armour tail keel strake', p, kz-depth, kz+.5, kit.grey)
        keel_curve(bar, 'tail strake keel', -40, -114, TAIL[0]-depth, 34.0, (-1, 0))


def build(kit):
    prow_edge = [(206, 18.0)] + [p for p in PROW_HALF if p[0] <= 206]
    for side, s in (('port', 1), ('starboard', -1)):
        # Prow shoulders: sloped orange flanks either side of the prow gallery.
        for k, (xa, xb) in enumerate(((206, 176), (174, 140), (138, 102)), 1):
            sh = band(kit, f'Armour {side} prow shoulder {k}', clip_x(prow_edge, xa, xb), 2.5, 15.0,
                      ridge(-10.0, -2.0, 12.0, 15.0), kit.orange, s)
            plane_cut(sh, 'shoulder bow', (190, 0, 12.0), facet_normal((1, 0), 16))
        # Glacis: the orange ridge and the grey band inboard of it.
        band(kit, f'Armour {side} glacis band', clip_x(CORE_HALF, 106, -2), 3.0, 15.0, ridge(-14.0, 0.0, 17.0, 15.0),
             kit.orange, s)
        band(kit, f'Armour {side} glacis grey band', clip_x(CORE_HALF, 100, 8), 17.0, 9.0, ridge(0.0, 8.0, 15.0, 9.0),
             kit.grey, s)
        # Arm belts: sloped plates from the gallery down to the outer edge.
        for k, (xa, xb, mat) in enumerate(((210, 151, kit.orange), (149, 106, kit.grey), (104, 72, kit.orange),
                                           (70, 22, kit.grey), (20, -14, kit.grey)), 1):
            b = band(kit, f'Armour {side} arm belt {k}', clip_x(ARM_OUTER, xa, xb), 1.5, 13.0,
                     ridge(-9.0, -2.0, 12.0, 13.0), mat, s)
            plane_cut(b, 'belt nose', (192, 0, 12.0), facet_normal((1, 0), 18))
        # Arm steps: grey blocks stepping down outboard of the aft belt, tops falling forward.
        for k, poly in enumerate(([(100, 110), (100, 122), (87, 123), (86, 111)],
                                  [(62, 113), (62, 124.5), (44, 124.5), (44, 114)],
                                  [(40, 115), (40, 125), (24, 129), (24, 117)],
                                  [(19, 114), (19, 127), (6, 121), (6, 113)]), 1):
            top = 10.0-.5*k
            b = prism(kit, f'Armour {side} arm step {k}', _side(poly, s), [(0.0, -8.0), (0.0, top-1.0), (1.0, top)],
                      kit.grey, bevel=.25)
            plane_cut(b, 'step tooth', (poly[0][0], 0, top-.5), facet_normal((1, 0), 28))
        # Inner coaming facing the notch (an edge listed bow to stern on the arm's
        # inner side is a right-hand edge, so it is offset to the right).
        inner = [p for p in reversed(ARM_INNER) if p[0] >= 104]
        c = band(kit, f'Armour {side} arm coaming', clip_x(inner, 206, 106), -10.0, 9.0, ridge(-9.0, -2.0, 10.0, 9.0),
                 kit.orange, s)
        plane_cut(c, 'coaming nose', (192, 0, 10.0), facet_normal((1, 0), 18))
        # TPC nose cheeks either side of the barrel.
        for k, poly in enumerate(([(213.5, 64.8), (213.5, 66.4), (205.5, 66.2), (205.5, 63.6)],
                                  [(213.5, 73.6), (213.5, 78.6), (205.5, 80.6), (205.5, 74.2)]), 1):
            ch = prism(kit, f'Armour {side} nose cheek {k}', _side(poly, s), [(0.0, -2.0), (0.0, 9.0), (.8, 9.8)],
                       kit.grey, bevel=.25)
            plane_cut(ch, 'cheek nose', (190, 0, 12.0), facet_normal((1, 0), 16))
        # Broadside fin, two tiers.
        fin = [(48, 78), (30, 97), (-8, 106), (-14, 92), (-6, 68), (20, 72)]
        prism(kit, f'Armour {side} broadside fin', _side(fin, s), [(0.0, 2.0), (0.0, 12.0), (2.0, 14.0)],
              kit.orange, bevel=.2)
        crest = [(38, 83), (26, 95), (-2, 101), (-7, 91), (0, 75), (18, 78)]
        prism(kit, f'Armour {side} broadside fin crest', _side(crest, s), [(0.0, 13.0), (0.0, 16.4), (1.6, 18.0)],
              kit.orange, bevel=.2)
        # Prow collar: the orange blocks where the prow meets the core.
        collar = [(113, 27.5), (113, 39.0), (106, 40.6), (98, 41.2), (98, 27.5)]
        prism(kit, f'Armour {side} prow collar', _side(collar, s), [(0.0, 0.0), (0.0, 15.0), (1.4, 16.4)],
              kit.orange, bevel=.25)
        # Leg stripe.
        leg_inner = [(-36, 52), (-60, 47), (-104, 46), (-112, 47), (-121, 48.5)]
        st = band(kit, f'Armour {side} leg stripe', leg_inner, -6.5, 5.0, slab(8.0, 15.0), kit.orange, s)
        plane_cut(st, 'stripe aft', (-104, 0, 15.0), facet_normal((-1, 0), 20))
        # Waist bay: a dark frame on the valley deck carrying three amber bay lamps.
        kit.box(f'Armour {side} waist bay frame', (101.5, s*50.5, 2.4), (6.0, 11.0, 1.6), kit.dark, bevel=.2)
        for k, y in enumerate((47.0, 50.5, 54.0), 1):
            kit.box(f'Armour {side} waist bay lamp {k}', (100.5, s*y, 3.5), (1.6, 2.4, .9), kit.lamp, bevel=0)
        # Core aft face above the machinery well: buttresses and a lintel.
        for k, y in enumerate((30, 50, 72), 1):
            b = kit.box(f'Armour {side} core aft buttress {k}', (-20.0, s*y, 6.0), (5.0, 4.5, 12.0), kit.grey, bevel=.3)
            plane_cut(b, 'buttress foot', (-22.5, 0, 1.0), (-.6, 0, -.8))
        kit.box(f'Armour {side} core aft lintel', (-19.6, s*53.0, 9.6), (3.6, 58.0, 3.4), kit.grey, bevel=.3)
    keel_strakes(kit)
