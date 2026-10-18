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
  keel strakes    bars following the belly's rounded underside (three strakes and
                  two frames), and under the prow, aft body and tail following
                  each keel curve.
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
                    xr=(-79, 159))
    for k, x in enumerate((22, 50), 1):
        shell_strip(kit, f'Armour core keel frame {k}', ob['Hull belly'], 0, x, 2.2, 1.2, kit.grey, span=40.0)
    shell_strip(kit, 'Armour prow keel strake', ob['Hull prow'], 1, 0, 2.6, 2.4, kit.grey, xr=(100, 216))


def build(kit):
    prow_edge = [(206, 18.0)] + [p for p in PROW_HALF if p[0] <= 206]
    for side, s in (('port', 1), ('starboard', -1)):
        # Prow shoulders: sloped orange flanks either side of the prow gallery.
        for k, (xa, xb) in enumerate(((206, 176), (174, 140), (138, 102)), 1):
            sh = band(kit, f'Armour {side} prow shoulder {k}', clip_x(prow_edge, xa, xb), 2.5, 15.0,
                      ridge(-10.0, -2.0, 12.0, 15.0), kit.orange, s)
            plane_cut(sh, 'shoulder bow', (190, 0, 12.0), facet_normal((1, 0), 16))
        # Glacis: the orange ridge and the grey band inboard of it.
        band(kit, f'Armour {side} glacis band', clip_x(CORE_HALF, 106, -2), 3.0, 15.0, ridge(-14.0, 2.0, 19.0, 15.0),
             kit.orange, s)
        band(kit, f'Armour {side} glacis grey band', clip_x(CORE_HALF, 100, 8), 17.0, 9.0, ridge(0.0, 10.0, 20.0, 9.0),
             kit.grey, s)
        # Prow collar: the orange blocks where the prow meets the core.
        collar = [(113, 27.5), (113, 39.0), (106, 40.6), (98, 41.2), (98, 27.5)]
        prism(kit, f'Armour {side} prow collar', _side(collar, s), [(0.0, 0.0), (0.0, 15.0), (1.4, 16.4)],
              kit.orange, bevel=.25)
        # Leg stripe.
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
