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

Pass 2 (plausible mid-distance detail), read off the sprite at 4-8x:
  bilge strakes   one heavy dark rubbing strake per mass along the top of
                  its raised bilge plating (os_hull lays the courses), where
                  a high sun catches its upper face;
  keel strakes    longitudinal bars under the core, prow, arms, aft body and
                  tail that follow each keel curve, with transverse frames
                  across the core's flat bottom, so the belly has rhythm;
  arm steps       the sprite's dark grey sawtooth blocks along each arm's
                  outer edge (X 100..2), standing proud of the belt, and the
                  orange step plate inboard of the forward belt;
  nose cheeks     stepped grey cheeks flanking each TPC barrel at the arm nose;
  fin crests      a second, smaller orange tier on each broadside fin;
  shoulder caps   the prow shoulders' stepped orange cap plates;
  prow collar     the wider orange blocks where the prow meets the core, and
                  the chevron arch step across the trench at X 96..107;
  waist bay       the dark bay frame and amber bay lamps in each waist valley;
  core aft face   buttresses and a lintel on the core's aft face above the
                  machinery wells.
"""
import math
import os_hull
from os_kit import ccw, clip_rect, facet_normal, inset, mirror_y, plane_cut, prism, strip_plan, _left
from os_hull import (AFT, AFT_OUTER, ARM, ARM_INNER, ARM_OUTER, CORE, CORE_HALF, LEG_PORT, PROW, PROW_HALF, TAIL,
                     TAIL_HALF, MIN_CLEAR, SEAM, Shells, full, keel_curve, port_plan)


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
    build_pass2(kit)


# --- pass 2 ------------------------------------------------------------------

STRAKE_LIFT = 2.6
STRAKE_H = 3.0


def bilge_strakes(kit):
    """A heavy rubbing strake along the top of each mass's raised bilge plating."""
    for mass, (skin, edges, cuts, top) in os_hull.SKINS.items():
        if top is None:
            continue
        # Highest band at or below the plating's top that still keeps inside the plan edge.
        q = top-STRAKE_H/2
        while q > 2 and not all(skin.clear(q+STRAKE_H*k/4, STRAKE_LIFT) >= MIN_CLEAR for k in range(5)):
            q -= .5
        acc = Shells()
        for s0, s1 in edges:
            n = max(1, round((s1-s0)/60.0))
            for k in range(n):
                a, b = s0+(s1-s0)*k/n, s0+(s1-s0)*(k+1)/n
                acc.add(*skin.patch(a+SEAM/2, b-SEAM/2, q, q+STRAKE_H, STRAKE_LIFT, STRAKE_LIFT-.4, .8), True)
        ob = acc.build(kit, f'Armour {mass} bilge strake', kit.grey, bevel=.3)
        if ob:
            cuts(ob, .3)


def _bar(kit, name, poly, z0, z1, mat):
    return prism(kit, name, ccw(poly), [(0.0, z0), (0.0, z1-.8), (.8, z1)], mat, bevel=.2)


def keel_strakes(kit):
    """Longitudinal keel bars under each mass, following its keel curve, and
    transverse frames across the core's flat bottom."""
    depth = 3.2
    # Core: flat bottom, no keel curve.
    bottom = inset(full(CORE_HALF), CORE[0][0])
    kz = CORE[0][1]
    for k, y in enumerate((0, 15, -15), 1):
        p = clip_rect(bottom, -200, 300, y-2.6, y+2.6)
        if len(p) > 2:
            _bar(kit, f'Armour core keel strake {k}', p, kz-depth, kz+.5, kit.grey)
    for k, x in enumerate((-6, 22, 50), 1):
        p = clip_rect(bottom, x-2.2, x+2.2, -200, 200)
        if len(p) > 2:
            _bar(kit, f'Armour core keel frame {k}', p, kz-depth+1.2, kz+.5, kit.grey)
    # Prow: one bar on the centreline, rising with the bow keel.
    bottom = inset(full(PROW_HALF), PROW[0][0])
    p = clip_rect(bottom, 90, 230, -2.6, 2.6)
    bar = _bar(kit, 'Armour prow keel strake', p, PROW[0][1]-depth, PROW[0][1]+.5, kit.grey)
    keel_curve(bar, 'prow strake keel', 96, 225, -38.0-depth, 30.0, (1, 0))
    # Arms: one bar down each arm's midline.
    def side_at(edge, x):
        pts = sorted(edge)
        for (ax, ay), (bx, by) in zip(pts, pts[1:]):
            if ax <= x <= bx:
                return ay+(by-ay)*(x-ax)/((bx-ax) or 1)
        return pts[0][1] if x < pts[0][0] else pts[-1][1]
    mid = [(x, (side_at(ARM_OUTER, x)+side_at(ARM_INNER, x))/2) for x in (206, 190, 170, 146, 120, 104, 80, 44, 20, 4, -12)]
    for side, s in (('port', 1), ('starboard', -1)):
        plan = ccw(strip_plan(mid, 5.2))
        bar = _bar(kit, f'Armour {side} arm keel strake', plan if s > 0 else mirror_y(plan), ARM[0][1]-depth,
                   ARM[0][1]+.5, kit.grey)
        keel_curve(bar, 'arm strake keel', 80, 214, -46.0-depth, 38.0, (1, 0))
        keel_curve(bar, 'arm strake heel', -2, -16, -46.0-depth, 8.0, (-1, 0), n=2)
    # Aft body: two bars a side under the stern quarters.
    bottom = inset(full(AFT_OUTER+[(-102, 21)]), AFT[0][0])
    for k, y in enumerate((34, 72, -34, -72), 1):
        p = clip_rect(bottom, -200, 0, y-2.6, y+2.6)
        if len(p) > 2:
            bar = _bar(kit, f'Armour aft keel strake {k}', p, AFT[0][1]-depth, AFT[0][1]+.5, kit.grey)
            keel_curve(bar, 'aft strake keel', -20, -102, -50.0-depth, 34.0, (-1, 0))
    # Tail: the centreline bar.
    bottom = inset(full(TAIL_HALF), TAIL[0][0])
    p = clip_rect(bottom, -200, 0, -2.6, 2.6)
    bar = _bar(kit, 'Armour tail keel strake', p, TAIL[0][1]-depth, TAIL[0][1]+.5, kit.grey)
    keel_curve(bar, 'tail strake keel', -40, -114, -54.0-depth, 34.0, (-1, 0))


def _side(plan, s):
    plan = ccw(plan)
    return plan if s > 0 else mirror_y(plan)


def deck_features(kit):
    """The sprite's protrusions on the arms, fins, prow, waist and core aft face."""
    for side, s in (('port', 1), ('starboard', -1)):
        # Arm outer steps: dark grey blocks standing proud of the belt in a
        # sawtooth, each top falling forward (sprite X 100..2, |Y| 110..131).
        for k, poly in enumerate(([(100, 110), (100, 122), (87, 123), (86, 111)],
                                  [(62, 113), (62, 124.5), (44, 124.5), (44, 114)],
                                  [(40, 115), (40, 125), (24, 129), (24, 117)],
                                  [(19, 114), (19, 127), (6, 121), (6, 113)]), 1):
            top = 22.5-.6*k
            b = prism(kit, f'Armour {side} arm step {k}', _side(poly, s), [(0.0, 12.0), (0.0, top-1.0), (1.0, top)],
                      kit.grey, bevel=.25)
            plane_cut(b, 'step tooth', (poly[0][0], 0, top-.5), facet_normal((1, 0), 28))
        # The orange step plate inboard of the grey belt (sprite X 165..104),
        # kept aft of and below the WS 001/003 gunhouse's barrels.
        band(kit, f'Armour {side} arm step plate', clip_x(ARM_OUTER, 147, 106), 13.5, 4.5, 12.0, 17.0, kit.orange, s)
        # TPC nose cheeks either side of the barrel.
        for k, poly in enumerate(([(213.5, 64.8), (213.5, 66.4), (205.5, 66.2), (205.5, 63.6)],
                                  [(213.5, 73.6), (213.5, 78.6), (205.5, 80.6), (205.5, 74.2)]), 1):
            c = prism(kit, f'Armour {side} nose cheek {k}', _side(poly, s), [(0.0, 2.0), (0.0, 13.0), (.8, 13.8)],
                      kit.grey, bevel=.25)
            plane_cut(c, 'cheek nose', (184, 0, 17.0), facet_normal((1, 0), 16))
        # Broadside fin crest: a smaller second tier on the fin.
        crest = [(38, 83), (26, 95), (-2, 101), (-7, 91), (0, 75), (18, 78)]
        prism(kit, f'Armour {side} broadside fin crest', _side(crest, s), [(0.0, 18.0), (0.0, 21.4), (1.6, 23.0)],
              kit.orange, bevel=.2)
        # Prow shoulder caps: stepped orange cap plates, alternately higher.
        edge = [(206, 18.0)]+[p for p in PROW_HALF if p[0] <= 206]
        for k, (xa, xb) in enumerate(((204, 178), (176, 150), (148, 126), (124, 106)), 1):
            top = 23.0 if k % 2 else 22.0
            cap = band(kit, f'Armour {side} prow shoulder cap {k}', clip_x(edge, xa, xb), 6.0, 7.0, 12.5, top,
                       kit.orange, s)
            plane_cut(cap, 'cap bow', (176, 0, top), facet_normal((1, 0), 14))
        # Prow collar: the wider orange blocks where the prow meets the core.
        collar = [(113, 27.5), (113, 39.0), (106, 40.6), (98, 41.2), (98, 27.5)]
        prism(kit, f'Armour {side} prow collar', _side(collar, s), [(0.0, 12.0), (0.0, 24.0), (1.4, 25.4)],
              kit.orange, bevel=.25)
        # Waist bay: a dark frame on the valley deck carrying three amber bay lamps.
        kit.box(f'Armour {side} waist bay frame', (101.5, s*50.5, 6.4), (6.0, 11.0, 1.6), kit.dark, bevel=.2)
        for k, y in enumerate((47.0, 50.5, 54.0), 1):
            kit.box(f'Armour {side} waist bay lamp {k}', (100.5, s*y, 7.5), (1.6, 2.4, .9), kit.lamp, bevel=0)
        # Core aft face above the machinery well: buttresses and a lintel.
        for k, y in enumerate((30, 50, 72), 1):
            # Standing clear of the well's headers and pipe runs below z 0.
            b = kit.box(f'Armour {side} core aft buttress {k}', (-20.0, s*y, 11.5), (5.0, 4.5, 21.0), kit.grey,
                        bevel=.3)
            plane_cut(b, 'buttress foot', (-22.5, 0, 3.0), (-.6, 0, -.8))
        kit.box(f'Armour {side} core aft lintel', (-19.6, s*53.0, 18.6), (3.6, 58.0, 4.4), kit.grey, bevel=.3)
    # The chevron arch step across the trench at the prow root.
    arch = [(107, 27.5), (101, 27.5), (96, 0), (101, -27.5), (107, -27.5), (102, 0)]
    prism(kit, 'Armour prow arch step', ccw(arch), [(0.0, 14.0), (0.0, 23.6), (1.0, 24.6)], kit.grey, bevel=.25)


def build_pass2(kit):
    bilge_strakes(kit)
    keel_strakes(kit)
    deck_features(kit)
