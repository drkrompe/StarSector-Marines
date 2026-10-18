"""The Onslaught's hull masses: prow, core, the two gun arms, aft body, tail and aft legs.

Plans are read off onslaught_hegemony.png (identical silhouette to
onslaught_base.png) in model units: X = 244 - row, Y = 144 - col about the
.ship centre (144, 140 from the bottom). Heights are proposed art: the
sprite gives none. The sprite reads as five masses, and each is one prism
with one keel chamfer, one wall and one shoulder chamfer:

  prow       the long central wedge, X 96..225, carrying WS 007/008/009/019
             in a dark trench between two orange armour shoulders;
  core       the arrowhead behind it, X -70..104: its orange glacis bands run
             from the prow's shoulders aft and outboard to the broadside
             sponsons; the bone spine and the two domes stand on it (os_bridge);
  arms       the two gun arms, X -16..214, each a long armoured block with the
             thermal pulse cannon hardpoints at the tip, a row of turrets along
             it and the large Devastator barbette at its root; the notch
             between arm and prow is open to space forward of X 104;
  aft body   the stern quarters aft of the arms: a thick outer rim round two
             recessed machinery wells open to space, one outboard of each leg
             (diagonal pipe bundles) and one inboard (fore-and-aft pipe banks
             beside the tail); os_machinery fills them;
  tail       the central main-engine housing, X -50..-113;
  legs       the two orange-striped aft legs either side of the tail notch.

Every mass is its own object so a plane cut on one never clips another.
"""
import math
from mathutils import Vector
from os_kit import boolean, ccw, curved, mirror_y, plane_cut, prism, section, facet_normal, inset

# --- plans (port halves, listed bow to stern along the outer edge) ---------

PROW_HALF = [(225, 7.5), (220, 10.5), (216, 14.5), (212, 16.5), (200, 18.5), (196, 22.5), (192, 25.5),
             (172, 29.5), (148, 34.5), (120, 39.5), (104, 41.5), (96, 41.5)]
# The core ends in a flat aft face at X -18: behind it the deck drops into the
# machinery wells (os_machinery) and only the tail carries on aft.
CORE_HALF = [(104, 41.5), (60, 58), (20, 74), (0, 82), (-18, 84), (-18, 22)]
# The sprite's small outer steps (X 146-150 and the notch at X 68) are left to
# the armour blocks of pass 2: the curved bilge insets the plan by up to 10
# units, more than those steps are deep.
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

# --- sections ---------------------------------------------------------------
# The owner: "thick, with large curving hull depth". Every mass is deep, and
# its lower hull turns under on a rounded bilge rather than one keel chamfer;
# the core is the deepest, a keel 62 units down under a 30-unit bilge. In
# profile the keel line curves up toward the stem and the stern as well
# (keel_curve: a run of facets on a circular arc), so the side view curves too.
PROW = curved(-38.0, 16.0, 16.0, 32.0, 6.0, 10.0)
CORE = curved(-62.0, 22.0, 30.0, 50.0, 8.0, 14.0)
ARM = curved(-46.0, 14.0, 12.0, 36.0, 5.0, 12.0)
AFT = curved(-50.0, 10.0, 18.0, 36.0, 4.0, 8.0)
TAIL = curved(-54.0, 18.0, 10.0, 40.0, 4.0, 8.0)
LEG = curved(-30.0, 12.0, 6.0, 20.0, 2.5, 5.0)
WAIST = section(-20.0, 6.0, 3.0, 1.5)

DECK = {'prow': 16.0, 'core': 22.0, 'arm': 14.0, 'aft': 10.0, 'tail': 18.0, 'leg': 12.0, 'waist': 6.0}


def full(half):
    """A symmetric CCW plan from a port half listed bow to stern."""
    return ccw(list(half) + [(x, -y) for x, y in reversed(half) if abs(y) > 1e-6])


def port_plan(outer, inner):
    return ccw(list(outer) + list(inner))


def keel_cut(target, name, point, back, angle):
    """Remove everything below a plane rising toward the plan direction `back` at `angle` deg."""
    a = math.radians(angle)
    dx, dy = back
    length = (dx*dx+dy*dy)**.5
    plane_cut(target, name, point, (dx/length*math.sin(a), dy/length*math.sin(a), -math.cos(a)))


def keel_curve(target, name, x0, x1, keel, rise, back, n=4):
    """Curve the keel line up by `rise` between X x0 (tangent, at depth `keel`) and x1,
    along a circular arc cut as n facets; `back` is (+-1, 0), the way the curve runs."""
    span = abs(x1-x0)
    # Circle tangent to the keel at x0, passing `rise` above it at x1.
    radius = (span*span+rise*rise)/(2*rise)
    end = math.asin(span/radius)
    pts = [(x0+back[0]*radius*math.sin(end*k/n), keel+radius*(1-math.cos(end*k/n))) for k in range(n+1)]
    for (ax, az), (bx, bz) in zip(pts, pts[1:]):
        angle = math.degrees(math.atan2(bz-az, abs(bx-ax)))
        if angle > .5:
            keel_cut(target, name, (ax, 0, az), back, angle)


def build(kit):
    prow = prism(kit, 'Hull prow', full(PROW_HALF), PROW, kit.plate)
    # Bow: the deck falls toward the stem and the keel rises to meet it; the
    # sides close in with a chine either side of the stem.
    plane_cut(prow, 'bow upper', (176, 0, 16.0), facet_normal((1, 0), 14))
    keel_curve(prow, 'bow keel', 96, 225, -38.0, 30.0, (1, 0))
    for s in (1, -1):
        plane_cut(prow, 'bow chine', (214, s*13.5, 4.0), (0, s*math.sin(math.radians(38)), math.cos(math.radians(38))))

    core = prism(kit, 'Hull core', full(CORE_HALF), CORE, kit.plate)

    arms = {}
    for side, s in (('port', 1), ('starboard', -1)):
        plan = port_plan(ARM_OUTER, ARM_INNER)
        if s < 0:
            plan = mirror_y(plan)
        arm = prism(kit, f'Hull {side} arm', plan, ARM, kit.plate)
        # The arm's nose falls toward the hardpoint cheeks, and its keel rises.
        plane_cut(arm, 'arm nose', (184, 0, 14.0), facet_normal((1, 0), 15))
        keel_curve(arm, 'arm keel', 80, 214, -46.0, 38.0, (1, 0))
        keel_curve(arm, 'arm heel', -2, -16, -46.0, 8.0, (-1, 0), n=2)
        arms[side] = arm

    aft = prism(kit, 'Hull aft body', full(AFT_OUTER + [(-102, 21)]), AFT, kit.plate)
    keel_curve(aft, 'aft keel', -20, -102, -50.0, 34.0, (-1, 0))
    for s in (1, -1):
        plan = ccw(WELL_PORT) if s > 0 else mirror_y(ccw(WELL_PORT))
        cutter = prism(kit, 'Temporary machinery well cutter', plan, [(0.0, WELL_FLOOR), (0.0, 30.0)], kit.dark, bevel=0)
        boolean(aft, cutter)
    tail = prism(kit, 'Hull tail', full(TAIL_HALF), TAIL, kit.plate)
    plane_cut(tail, 'tail upper', (-92, 0, 18.0), facet_normal((-1, 0), 18))
    keel_curve(tail, 'tail keel', -40, -114, -54.0, 34.0, (-1, 0))

    legs, waists = {}, {}
    for side, s in (('port', 1), ('starboard', -1)):
        plan = ccw(LEG_PORT) if s > 0 else mirror_y(ccw(LEG_PORT))
        leg = prism(kit, f'Hull {side} aft leg', plan, LEG, kit.plate)
        plane_cut(leg, 'leg aft', (-104, 0, 12.0), facet_normal((-1, 0), 20))
        keel_curve(leg, 'leg keel', -40, -127, -30.0, 22.0, (-1, 0))
        legs[side] = leg
        waists[side] = prism(kit, f'Hull {side} waist', ccw(WAIST_PORT) if s > 0 else mirror_y(ccw(WAIST_PORT)),
                             WAIST, kit.plate)

    kit.hull += [prow, core, *arms.values(), aft, tail, *legs.values(), *waists.values()]
    return {'prow': prow, 'core': core, 'arms': arms, 'aft': aft, 'tail': tail, 'legs': legs}
