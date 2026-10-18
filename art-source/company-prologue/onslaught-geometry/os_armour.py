"""The Hegemony armour: the orange and dark grey bands onslaught_hegemony.png
lays over the hull, as raised slabs proud of the deck edges.

  prow shoulders  an orange slab along each prow edge, X 104..206, standing
                  proud of the prow deck so the turrets sit in a trench
                  between them; it follows the bow's falling deck;
  glacis bands    the shoulders' continuation across the core: an orange band
                  from the prow root aft and outboard to X 0;
  arm belts       along each arm's outer deck edge, orange forward, dark grey
                  amidships, orange over the Devastator, dark grey aft (the
                  sprite's alternation), in segments with seam gaps;
  arm coamings    orange along each arm's inner edge facing the notch;
  broadside fins  the orange fin at each arm's root by WS 010/011;
  leg stripes     orange along each aft leg's inner edge.
"""
import math
from os_kit import ccw, facet_normal, mirror_y, plane_cut, prism, strip_plan, _left
from os_hull import ARM_INNER, ARM_OUTER, CORE_HALF, LEG_PORT, PROW_HALF


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


def band(kit, name, edge, inward, width, z0, z1, mat, s):
    centre = offset(edge, inward+width/2)
    plan = ccw(strip_plan(centre, width))
    if s < 0:
        plan = mirror_y(plan)
    return prism(kit, name, plan, slab(z0, z1), mat, bevel=.2)


def build(kit):
    prow_edge = [p for p in PROW_HALF if p[0] <= 206]
    prow_edge = [(206, 18.0)] + prow_edge
    arm_outer = ARM_OUTER
    arm_inner = list(reversed(ARM_INNER))            # bow to stern
    # The trench deck between the shoulders: a dark grey plate down the prow's
    # centre, where WS 007/008/009/019 stand.
    trench = ccw([(204, 9), (196, 15), (172, 20), (148, 24), (104, 30), (104, -30), (148, -24), (172, -20),
                  (196, -15), (204, -9)])
    deck = prism(kit, 'Armour prow trench deck', trench, slab(14.0, 16.8, .6), kit.grey, bevel=.15)
    plane_cut(deck, 'trench bow', (176, 0, 16.8), facet_normal((1, 0), 14))
    for side, s in (('port', 1), ('starboard', -1)):
        sh = band(kit, f'Armour {side} prow shoulder', clip_x(prow_edge, 206, 102), 4.0, 11.0, 12.0, 20.5,
                  kit.orange, s)
        plane_cut(sh, 'shoulder bow', (176, 0, 20.5), facet_normal((1, 0), 14))
        band(kit, f'Armour {side} glacis band', clip_x(CORE_HALF, 106, -2), 7.0, 13.0, 18.0, 26.5, kit.orange, s)
        # Arm outer belt: the sprite's orange / grey alternation, with seam gaps.
        for k, (xa, xb, mat) in enumerate(((210, 151, kit.orange), (149, 106, kit.grey), (104, 72, kit.orange),
                                           (70, 22, kit.grey), (20, -14, kit.grey)), 1):
            b = band(kit, f'Armour {side} arm belt {k}', clip_x(arm_outer, xa, xb), 2.0, 11.0, 9.0, 18.0, mat, s)
            plane_cut(b, 'belt nose', (184, 0, 18.0), facet_normal((1, 0), 15))
        # Inner coaming, facing the notch (the inner edge listed bow to stern is a
        # starboard-hand edge for the port arm, so it is offset to the right).
        inner = [(x, y) for x, y in arm_inner if x >= 104]
        c = band(kit, f'Armour {side} arm coaming', clip_x(inner, 206, 106), -9.0, 8.0, 9.0, 17.5, kit.orange, s)
        plane_cut(c, 'coaming nose', (184, 0, 17.5), facet_normal((1, 0), 15))
        fin = ccw([(48, 78), (30, 97), (-8, 106), (-14, 92), (-6, 68), (20, 72)])
        prism(kit, f'Armour {side} broadside fin', fin if s > 0 else mirror_y(fin), slab(11.0, 20.0, 2.0),
              kit.orange, bevel=.2)
        leg_inner = [(-36, 52), (-60, 47), (-104, 46), (-112, 47), (-121, 48.5)]
        st = band(kit, f'Armour {side} leg stripe', leg_inner, -6.5, 5.0, 8.0, 15.0, kit.orange, s)
        plane_cut(st, 'stripe aft', (-104, 0, 15.0), facet_normal((-1, 0), 20))
