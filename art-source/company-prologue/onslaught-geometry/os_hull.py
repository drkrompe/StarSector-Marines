"""The Onslaught's hull: base masses and the gun tiers on them.

Plans are read off onslaught_hegemony.png (identical silhouette to
onslaught_base.png) in model units: X = 244 - row, Y = 144 - col about the
.ship centre (144, 140 from the bottom). Heights are proposed art.

Owner, pass 3: the top was too flat; the sprite's gun emplacements must be
read with curvature and terracing; the arms were "super tall, for what?"; the
flanks showed only plating. So the hull is built in tiers, the way the
sprite's shading stacks it:

  base masses   rounded hulls, low at their edges. The core keeps the deep,
                curving belly the owner asked for ("thick"); the arms are
                shallow gun platforms hanging off it, not walls.
  gun galleries the raised spine of each arm along its turret line (WS 001,
                002, 005 and the Devastator WS 018), and the prow's central
                gallery carrying WS 007, 008/009 and the multineedler WS 019;
                crowned and chamfered, so their tops curve.
  core terrace  the sprite's ring round the command dome: WS 012/013, 014/015
                and 022/023 all stand on one round raised terrace centred near
                X 14, with an inner tier under the domes; the orange and grey
                glacis is a ridge rising round it (os_armour).
  turrets       stand straight on whatever surface their slot falls on, at
                that height and tilted with it (os_turrets); owner, round 2:
                "hull turrets and their mounts are against the hull. They don't
                need to be flat, and they don't need to be on the same plane".

The masses:
  prow      X 96..225;  core  X -18..104;  arms  X -16..214 (the TPC
  hardpoints at the tip); aft body with the stern machinery wells cut into
  it (os_machinery fills them); tail X -10..-113; legs either side of the
  tail notch; waists, the low valley between glacis and arm.
Every mass is its own object so a plane cut on one never clips another.
"""
import math
from os_kit import arched_mass, boolean, ccw, mirror_y, plane_cut, prism, facet_normal

# --- plans (port halves, listed bow to stern along the outer edge) ---------

PROW_HALF = [(225, 7.5), (220, 10.5), (216, 14.5), (212, 16.5), (200, 18.5), (196, 22.5), (192, 25.5),
             (172, 29.5), (148, 34.5), (120, 39.5), (104, 41.5), (96, 41.5)]
# The core ends in a flat aft face at X -18: behind it the deck drops into the
# machinery wells (os_machinery) and only the tail carries on aft.
CORE_HALF = [(104, 41.5), (60, 58), (20, 74), (0, 82), (-18, 84), (-18, 22)]
# The sprite's small outer steps (X 146-150 and the notch at X 68) are left to
# the armour blocks; the rounded bilge insets the plan more than they are deep.
ARM_OUTER = [(214, 79.5), (204, 85), (176, 95), (146, 102), (104, 127.5), (80, 128.5), (44, 128.5),
             (20, 134.5), (8, 125), (-16, 118.5)]
ARM_INNER = [(-16, 90), (0, 88), (40, 70), (104, 56.5), (140, 51.5), (176, 48.5), (196, 54.5), (208, 62),
             (214, 64.5)]          # stern to bow
AFT_OUTER = [(-4, 124), (-16, 118.5), (-24, 114.5), (-40, 109.5), (-60, 103.5), (-70, 100.5), (-72, 84),
             (-80, 74.5), (-88, 65.5), (-96, 68.5), (-101, 69), (-102, 42)]
TAIL_HALF = [(-10, 22), (-50, 20.5), (-104, 20.5), (-108, 18.5), (-112, 17.5), (-113.5, 14), (-113.5, 0)]
LEG_PORT = [(-36, 60), (-56, 63), (-90, 66), (-100, 69.5), (-112, 63.5), (-120, 60.5), (-124, 56.5), (-126, 52.5),
            (-121, 48.5), (-112, 47), (-104, 46), (-60, 47), (-36, 52)]
# The machinery well (port; mirrored), cut into the aft body's deck down to WELL_FLOOR.
WELL_PORT = [(-18, 23), (-18, 104), (-40, 101), (-64, 94), (-71, 81), (-82, 66), (-96, 62), (-100, 44),
             (-100, 23)]
WELL_FLOOR = -10.0
# The low deck between the glacis and the arm (the sprite's dark valley, with
# amber bay lamps at its forward end where it opens into the notch).
WAIST_PORT = [(106, 40), (106, 58), (40, 72), (2, 89), (2, 79), (60, 55)]

# --- base sections: (keel z, deck z, bilge inset, bilge height, shoulder inset,
# shoulder height). No vertical wall: each side is one curve, a rounded bilge
# running into a rounded shoulder. The core is the deep one (keel 62 down, the
# "thick" belly); the arms are shallow platforms (keel 22 down), so from the
# side the belly reads as one heavy curving hull with the gun tiers on top.
# Then a crown: (rise, over what distance inboard of the shoulder). The tops are
# heightfields over the plan (os_kit.arched_mass), so every deck arches.
PROW = (-36.0, 6.0, 12.0, 22.0, 16.0, 18.0, 3.0, 12.0)
CORE = (-62.0, 10.0, 26.0, 42.0, 30.0, 26.0, 5.0, 24.0)
ARM = (-22.0, 8.0, 9.0, 12.0, 14.0, 16.0, 3.0, 10.0)
AFT = (-50.0, 10.0, 16.0, 30.0, 10.0, 20.0, 0.0, 0.0)
TAIL = (-54.0, 18.0, 9.0, 34.0, 8.0, 30.0, 2.0, 8.0)
LEG = (-30.0, 12.0, 6.0, 18.0, 4.0, 18.0, 1.0, 4.0)
WAIST = (-20.0, 2.0, 3.0, 8.0, 1.0, 3.0, 0.0, 0.0)


def mass(kit, name, plan, spec, mat):
    keel, deck, bi, bh, ti, th, crown, cw = spec
    return arched_mass(kit, name, plan, keel, deck, bi, bh, ti, th, crown, cw, mat=mat, step=3.0)


# --- the tiers ---------------------------------------------------------------
# Gun gallery along each arm: (x, y, half-width) stations through its turret
# line, bow to stern (port).
ARM_GALLERY = [(210, 70.0, 8.0), (196, 70.5, 11.0), (158, 71.0, 13.0), (134, 74.0, 13.0), (114, 78.0, 13.5),
               (92, 84.0, 14.0), (60, 92.0, 14.0), (30, 98.0, 13.0), (4, 104.0, 12.0), (-12, 106.0, 10.0)]
PROW_GALLERY = [(214, 0, 5.5), (200, 0, 9.0), (186, 0, 11.0), (150, 0, 17.0), (126, 0, 18.5), (102, 0, 20.0)]
TERRACE_C = (14.0, 0.0)           # centre of the round core terrace (the sprite's ring round the dome)
TERRACE_R = 47.0
INNER_R = 27.0
GALLERY_TOP = 17.0
TERRACE_TOP = 19.0
INNER_TOP = 22.0


DECK = {'prow': 6.0, 'core': 10.0, 'arm': 8.0, 'aft': 10.0, 'tail': 18.0, 'leg': 12.0, 'waist': 2.0,
        'gallery': GALLERY_TOP, 'terrace': TERRACE_TOP, 'inner': INNER_TOP}


def full(half):
    """A symmetric CCW plan from a port half listed bow to stern."""
    return ccw(list(half) + [(x, -y) for x, y in reversed(half) if abs(y) > 1e-6])


def port_plan(outer, inner):
    return ccw(list(outer) + list(inner))


def keel_cut(target, name, point, back, angle, pad=0.0):
    """Remove everything below a plane rising toward the plan direction `back` at `angle` deg.
    `pad` moves the plane into the kept side, so a part replaying the cut stops just short of it."""
    a = math.radians(angle)
    dx, dy = back
    length = (dx*dx+dy*dy)**.5
    n = (dx/length*math.sin(a), dy/length*math.sin(a), -math.cos(a))
    plane_cut(target, name, tuple(p-c*pad for p, c in zip(point, n)), n)


def pad_cut(target, name, point, normal, pad=0.0):
    """plane_cut, with the plane moved `pad` into the kept side."""
    length = sum(c*c for c in normal)**.5
    plane_cut(target, name, tuple(p-c/length*pad for p, c in zip(point, normal)), normal)


def keel_curve(target, name, x0, x1, keel, rise, back, n=4, pad=0.0):
    """Curve the keel line up by `rise` between X x0 (tangent, at depth `keel`) and x1,
    along a circular arc cut as n facets; `back` is (+-1, 0), the way the curve runs."""
    span = abs(x1-x0)
    radius = (span*span+rise*rise)/(2*rise)
    end = math.asin(span/radius)
    pts = [(x0+back[0]*radius*math.sin(end*k/n), keel+radius*(1-math.cos(end*k/n))) for k in range(n+1)]
    for (ax, az), (bx, bz) in zip(pts, pts[1:]):
        angle = math.degrees(math.atan2(bz-az, abs(bx-ax)))
        if angle > .5:
            keel_cut(target, name, (ax, 0, az), back, angle, pad)


# --- the cuts each mass takes after its prism --------------------------------

def prow_cuts(ob, pad=0.0):
    keel_curve(ob, 'bow keel', 96, 225, -36.0, 30.0, (1, 0), pad=pad)
    for s in (1, -1):
        pad_cut(ob, 'bow chine', (214, s*13.5, 0.0), (0, s*math.sin(math.radians(38)), math.cos(math.radians(38))), pad)


def arm_cuts(ob, pad=0.0):
    keel_curve(ob, 'arm keel', 120, 214, -22.0, 18.0, (1, 0), pad=pad)
    keel_curve(ob, 'arm heel', 4, -16, -22.0, 8.0, (-1, 0), n=2, pad=pad)


def core_cuts(ob, pad=0.0):
    keel_curve(ob, 'core keel fore', 50, 104, -62.0, 34.0, (1, 0), pad=pad)
    keel_curve(ob, 'core keel aft', 4, -18, -62.0, 14.0, (-1, 0), n=2, pad=pad)


def aft_cuts(ob, pad=0.0):
    keel_curve(ob, 'aft keel', -20, -102, -50.0, 34.0, (-1, 0), pad=pad)


def tail_cuts(ob, pad=0.0):
    pad_cut(ob, 'tail upper', (-92, 0, 18.0), facet_normal((-1, 0), 18), pad)
    keel_curve(ob, 'tail keel', -40, -114, -54.0, 34.0, (-1, 0), pad=pad)


def leg_cuts(ob, pad=0.0):
    pad_cut(ob, 'leg aft', (-104, 0, 12.0), facet_normal((-1, 0), 20), pad)
    keel_curve(ob, 'leg keel', -40, -127, -30.0, 22.0, (-1, 0), pad=pad)


# --- tier plans --------------------------------------------------------------

def gallery_plan(stations, mirror=False):
    """A CCW plan round a polyline of (x, y, half-width) stations, square-ended."""
    left, right = [], []
    for i, (x, y, w) in enumerate(stations):
        a = stations[max(i-1, 0)]
        b = stations[min(i+1, len(stations)-1)]
        dx, dy = b[0]-a[0], b[1]-a[1]
        length = (dx*dx+dy*dy)**.5
        nx, ny = -dy/length, dx/length
        left.append((x+nx*w, y+ny*w))
        right.append((x-nx*w, y-ny*w))
    plan = ccw(right+list(reversed(left)))
    return mirror_y(plan) if mirror else plan


def disc(cx, cy, r, n=28, x_min=None):
    pts = [(cx+r*math.cos(2*math.pi*(k+.5)/n), cy+r*math.sin(2*math.pi*(k+.5)/n)) for k in range(n)]
    if x_min is not None:
        out = []
        for p in [(max(x, x_min), y) for x, y in pts]:
            if not out or abs(p[0]-out[-1][0])+abs(p[1]-out[-1][1]) > 1e-6:
                out.append(p)
        pts = out
    return ccw(pts)


def build(kit):
    prow = mass(kit, 'Hull prow', full(PROW_HALF), PROW, kit.plate)
    prow_cuts(prow)
    core = mass(kit, 'Hull core', full(CORE_HALF), CORE, kit.plate)
    core_cuts(core)

    aft = mass(kit, 'Hull aft body', full(AFT_OUTER + [(-102, 21)]), AFT, kit.plate)
    aft_cuts(aft)
    for s in (1, -1):
        plan = ccw(WELL_PORT) if s > 0 else mirror_y(ccw(WELL_PORT))
        cutter = prism(kit, 'Temporary machinery well cutter', plan, [(0.0, WELL_FLOOR), (0.0, 30.0)], kit.dark, bevel=0)
        boolean(aft, cutter)
    tail = mass(kit, 'Hull tail', full(TAIL_HALF), TAIL, kit.plate)
    tail_cuts(tail)

    legs, waists = {}, {}
    for side, s in (('port', 1), ('starboard', -1)):
        plan = ccw(LEG_PORT) if s > 0 else mirror_y(ccw(LEG_PORT))
        leg = mass(kit, f'Hull {side} aft leg', plan, LEG, kit.plate)
        leg_cuts(leg)
        legs[side] = leg
        waists[side] = mass(kit, f'Hull {side} waist', ccw(WAIST_PORT) if s > 0 else mirror_y(ccw(WAIST_PORT)),
                            WAIST, kit.plate)

    # --- the tiers ---------------------------------------------------------
    tiers = []
    pg = arched_mass(kit, 'Hull prow gallery', gallery_plan(PROW_GALLERY), DECK['prow']-6, GALLERY_TOP,
                     tumble_in=6.0, tumble_h=7.0, crown=1.5, crown_w=8.0, mat=kit.plate, step=2.0)
    # The prow gallery climbs aft from the bow to the core terrace.
    plane_cut(pg, 'gallery rise', (110, 0, GALLERY_TOP+1.5), facet_normal((1, 0), 4.0))
    plane_cut(pg, 'gallery bow', (190, 0, GALLERY_TOP-5.0), facet_normal((1, 0), 16))
    tiers.append(pg)
    cx, cy = TERRACE_C
    tiers.append(arched_mass(kit, 'Hull core terrace', disc(cx, cy, TERRACE_R, 40, x_min=-17.0), DECK['core']-6,
                             TERRACE_TOP, tumble_in=6.0, tumble_h=6.0, crown=2.0, crown_w=16.0, mat=kit.plate, step=2.5))
    tiers.append(arched_mass(kit, 'Hull core inner terrace', disc(cx, cy, INNER_R, 32, x_min=-17.0),
                             TERRACE_TOP-1, INNER_TOP, tumble_in=4.0, tumble_h=4.0, crown=1.0, crown_w=8.0,
                             mat=kit.plate, step=2.0))

    kit.hull += [prow, core, aft, tail, *legs.values(), *waists.values(), *tiers]

    return {'prow': prow, 'core': core, 'aft': aft, 'tail': tail, 'legs': legs}
