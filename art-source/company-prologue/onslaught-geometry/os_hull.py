"""The Onslaught's hull: base masses and the gun tiers on them.

Plans are read off onslaught_hegemony.png (identical silhouette to
onslaught_base.png) in model units: X = 244 - row, Y = 144 - col about the
.ship centre (144, 140 from the bottom). Heights are proposed art.

Owner, pass 3: the top was too flat; the sprite's gun emplacements must be
read with curvature and terracing; the arms were "super tall, for what?"; the
flanks showed only plating. So the hull is built in tiers, the way the
sprite's shading stacks it:

  base masses   round 3: the prow and core are one hard-chined forebody lofted
                along X (fore_section): a centre deck, the sloped shoulder/glacis
                plane to the upper chine at the plan edge, a side falling inboard
                to a lower chine, and a deadrise to the keel; a low ram bow with
                the stem raked back. Under it the belly is a deep armoured wedge
                (two more chines and a keel flat) whose keel line runs on from
                the forefoot in straight runs. The waists are flat valley decks
                tucked under the core's chine, falling outboard into the arms.
  gun galleries the prow gun deck climbs aft in three raked steps (WS 007, WS
                008/009, the multineedler WS 019) to the core's centre deck; the
                arm galleries are os_arms'.
  core terrace  the citadel: a twenty-sided armoured drum with a straight batter
                (TERRACE_R about X 14) carrying WS 012-015 and 022/023, its back
                raked toward the machinery wells; an inner tier centred under the
                command tower (os_bridge), the high point of the skyline.
  turrets       stand straight on whatever surface their slot falls on, at
                that height and tilted with it (os_turrets); owner, round 2:
                "hull turrets and their mounts are against the hull. They don't
                need to be flat, and they don't need to be on the same plane".

The masses:
  prow      X 104..225;  core  X -18..104 (one shared station);  belly X -84..170 under them;  arms  X -16..214 (the TPC
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
# running into a rounded shoulder. Then a crown: (rise, over what distance
# inboard of the shoulder). The tops are heightfields over the plan
# (os_kit.arched_mass), so every deck arches.
#
# Owner, round 2: "the central body doesn't need to be as strangely elongated
# down under the ship"; "a bulky ship ... a slugger / brawler". The core used to
# hang a flat-bottomed block 62 down below everything else. Now the upper masses
# are only moderately deep, and one rounded BELLY runs under the prow root, the
# core and the aft body (below): a single heavy hull, deepest under the core
# terrace and curving up into the bow and stern, so the underside reads as one
# coherent hunched mass rather than a fin or a box. The core itself humps up
# (deck 12 with a tall crown) toward the citadel and the bridge.
PROW = (-32.0, 6.0, 18.0, 18.0, 16.0, 18.0, 3.0, 12.0)
CORE = (-30.0, 12.0, 14.0, 14.0, 30.0, 24.0, 8.0, 26.0)
ARM = (-22.0, 8.0, 9.0, 12.0, 14.0, 16.0, 3.0, 10.0)
AFT = (-32.0, 10.0, 14.0, 12.0, 10.0, 20.0, 0.0, 0.0)
TAIL = (-34.0, 18.0, 14.0, 14.0, 8.0, 30.0, 2.0, 8.0)
LEG = (-30.0, 12.0, 6.0, 18.0, 4.0, 18.0, 1.0, 4.0)
WAIST = (-20.0, 2.0, 3.0, 8.0, 1.0, 3.0, 0.0, 0.0)
# The belly: one rounded lower hull under prow root, core and aft body. Its top
# (deck -14) is buried in the masses above; its sides fall in a long bilge to a
# keel 46 down under the terrace. It stays inboard of the arms, waists and legs
# (which another pass poses) and forward of the drive cans (X -87 and aft).
BELLY_HALF = [(162, 8), (150, 20), (128, 30), (104, 37), (80, 45), (52, 53), (24, 60), (0, 63), (-22, 62),
              (-40, 52), (-58, 42), (-72, 33), (-80, 20), (-82, 0)]
BELLY = (-46.0, -14.0, 44.0, 28.0, 0.0, 0.0, 0.0, 0.0)
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
TERRACE_R = 52.0                  # its batter (R 44..52) lies outboard of the gun ring (R 32..40)
INNER_R = 27.0
GALLERY_TOP = 17.0                # the arm galleries (os_arms)
PROW_GALLERY_TOP = 31.5           # the prow gallery top tier, climbing aft to the spine glacis
TERRACE_TOP = 36.0
INNER_TOP = 40.0
INNER_C = (24.0, 0.0)            # the inner tier is centred under the command tower
INNER_R2 = 25.0


DECK = {'prow': 8.0, 'core': 28.0, 'arm': 8.0, 'aft': 10.0, 'tail': 18.0, 'leg': 12.0, 'waist': 2.0,
        'gallery': GALLERY_TOP, 'prow gallery': PROW_GALLERY_TOP, 'terrace': TERRACE_TOP, 'inner': INNER_TOP}


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
    keel_curve(ob, 'bow keel', 96, 225, PROW[0], 26.0, (1, 0), pad=pad)
    for s in (1, -1):
        pad_cut(ob, 'bow chine', (214, s*13.5, 0.0), (0, s*math.sin(math.radians(38)), math.cos(math.radians(38))), pad)


def arm_cuts(ob, pad=0.0):
    keel_curve(ob, 'arm keel', 120, 214, -22.0, 18.0, (1, 0), pad=pad)
    keel_curve(ob, 'arm heel', 4, -16, -22.0, 8.0, (-1, 0), n=2, pad=pad)


def core_cuts(ob, pad=0.0):
    keel_curve(ob, 'core keel fore', 70, 104, CORE[0], 10.0, (1, 0), pad=pad)


def belly_cuts(ob, pad=0.0):
    """The belly's keel line: a long sweep up into the bow, a shorter one up into the stern."""
    keel_curve(ob, 'belly keel fore', 50, 162, BELLY[0], 30.0, (1, 0), n=5, pad=pad)
    keel_curve(ob, 'belly keel aft', -6, -82, BELLY[0], 22.0, (-1, 0), n=4, pad=pad)


def aft_cuts(ob, pad=0.0):
    keel_curve(ob, 'aft keel', -40, -102, AFT[0], 14.0, (-1, 0), pad=pad)


def tail_cuts(ob, pad=0.0):
    pad_cut(ob, 'tail upper', (-92, 0, 18.0), facet_normal((-1, 0), 18), pad)
    keel_curve(ob, 'tail keel', -60, -114, TAIL[0], 14.0, (-1, 0), pad=pad)


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


# --- the forebody: prow and core as hard-chined lofts -------------------------
# Owner, round 3: "this still needs a lot of silhouette work"; the side view was
# a featureless lozenge and the front three balloons. So the prow, the core and
# the belly under them are lofted along X from straight-sided sections rather
# than rounded heightfields: every station is the same eight-point armoured
# section, so the two masses meet on one shared station (X 104) with nothing to
# line up, and the faces between stations are flat sloped plates.
#
#   (-yi, zi) ------- (yi, zi)            centre deck under the gallery/citadel
#   /                          \           shoulder / glacis plane (the orange)
# (-w, zk)                    (w, zk)      upper chine: the plan edge
#   \                          /           side, falling inboard
#  (-w+fl, zl)          (w-fl, zl)         lower chine
#        \                /                deadrise
#     (-kw, zkeel)--(kw, zkeel)            keel flat
#
# All tables are (X, value), bow to stern, interpolated linearly. The skyline
# they draw in side view: a low ram bow, the stem raked back to the prow deck,
# the gun deck climbing to the glacis ridge, then (os_hull tiers, os_bridge)
# the citadel, the command tower as the high point, and a step down aft.

def _table(tbl, x):
    if x >= tbl[0][0]:
        return tbl[0][1]
    for (xa, va), (xb, vb) in zip(tbl, tbl[1:]):
        if xb <= x <= xa:
            return va+(vb-va)*(x-xa)/(xb-xa)
    return tbl[-1][1]


FORE_W = [(225, 7.5), (220, 10.5), (216, 14.5), (212, 16.5), (200, 18.5), (196, 22.5), (192, 25.5), (172, 29.5),
          (148, 34.5), (120, 39.5), (104, 41.5), (60, 58), (20, 74), (0, 82), (-18, 84)]
FORE_YI = [(225, 2.5), (216, 4), (200, 6), (186, 8), (150, 12), (126, 13), (104, 12), (80, 22), (60, 30), (40, 40),
           (20, 50), (0, 56), (-18, 58)]
FORE_ZI = [(225, -3), (218, 6), (214, 7), (200, 7.5), (172, 8.5), (150, 11), (126, 16), (110, 23),
           (104, 27), (80, 28), (60, 29), (30, 28), (0, 26), (-18, 25)]
FORE_ZK = [(225, -4), (216, 0), (200, 1.5), (172, 2.5), (148, 3.5), (120, 4), (104, 4), (60, 3.5), (20, 2.5),
           (0, 1.5), (-18, 1)]
FORE_ZL = [(225, -8), (216, -12), (200, -15), (172, -17), (104, -18), (60, -18), (0, -16), (-18, -15)]
FORE_FL = [(225, 1), (216, 2.5), (196, 4), (150, 6), (104, 8), (60, 10), (0, 12), (-18, 12)]
FORE_KW = [(225, 2), (216, 3), (104, 5), (-18, 5)]
FORE_KEEL = [(225, -10), (216, -19), (104, -34), (60, -34), (0, -30), (-18, -28)]
# Stations at every table breakpoint, so each face between two is one flat plate
# and the armour skins (os_armour), sampled on the same planes, lie flush on it.
FORE_STATIONS = sorted({x for t in (FORE_W, FORE_YI, FORE_ZI, FORE_ZK, FORE_ZL, FORE_FL, FORE_KW, FORE_KEEL)
                        for x, _ in t}, reverse=True)


def fore(x):
    """The forebody's section numbers at X: dict of w, yi, zi, zk, zl, fl, kw, keel."""
    return {k: _table(t, x) for k, t in (('w', FORE_W), ('yi', FORE_YI), ('zi', FORE_ZI), ('zk', FORE_ZK),
                                         ('zl', FORE_ZL), ('fl', FORE_FL), ('kw', FORE_KW), ('keel', FORE_KEEL))}


def fore_top(x, y):
    """Height of the forebody's top surface (centre deck or shoulder plane) at (x, y)."""
    f = fore(x)
    y = abs(y)
    if y <= f['yi']:
        return f['zi']
    return f['zi']+(min(y, f['w'])-f['yi'])/(f['w']-f['yi'])*(f['zk']-f['zi'])


def fore_side_y(x, z):
    """|Y| of the forebody's side face at height z (between its two chines)."""
    f = fore(x)
    t = min(max((f['zk']-z)/(f['zk']-f['zl']), 0.0), 1.0)
    return f['w']-f['fl']*t


def fore_section(x):
    f = fore(x)
    half = [(f['yi'], f['zi']), (f['w'], f['zk']), (f['w']-f['fl'], f['zl']), (f['kw'], f['keel'])]
    return [(-y, z) for y, z in reversed(half)]+half


# The belly: a deep armoured wedge under the core, not a pod. Its top is buried
# in the forebody; below the forebody's lower chine it shows as two more chines
# stepping in to a keel flat, deepest under the citadel, and its keel line
# carries on from the prow's forefoot in straight runs: down from X 170, level
# under the citadel, up to the stern. From ahead it is the wedge between the arms.
BELLY_W = [(170, 4), (150, 20), (128, 30), (104, 37), (80, 45), (52, 53), (24, 60), (0, 63), (-22, 62), (-40, 52),
           (-58, 42), (-72, 33), (-80, 20), (-84, 6)]
# Round 4 (owner: below the deck the side read as one long lozenge): the belly is
# deepest forward, a CHEST under the glacis (X 124..80) whose face drops steeply
# below the arms' keels (X 140..124) and whose forefoot runs on up to the prow; from the chest a straight keel line
# rises gently aft and then, aft of the citadel, sweeps up to the stern.
BELLY_KEEL = [(168, -26.0), (140, -46.0), (124, -60.0), (100, -62.0), (80, -62.0), (-24, -46.0), (-60, -33.0),
              (-84, -22.0)]
BELLY_STATIONS = [170, 168, 150, 140, 128, 124, 104, 100, 80, 66, 52, 24, 6, 0, -12, -18, -20, -22, -24, -30, -40, -58, -60, -72,
                  -80, -84]


def belly_section(x):
    bw, zb = _table(BELLY_W, x), _table(BELLY_KEEL, x)
    if x >= -18:
        # Under the forebody the belly's upper chine lies on the forebody's own
        # bottom (its lower chine, or on the deadrise where the belly is narrower),
        # so the belly carries the hull's underside on down with no undercut slot.
        f = fore(x)
        yc = min(bw, f['w']-f['fl']-.3)
        y0, z0, y1, z1 = f['w']-f['fl'], f['zl'], f['kw'], f['keel']
        zc = z0+(z1-z0)*(y0-yc)/(y0-y1)+.3
        top = (yc*.8, zc+5.0)
    else:
        # Aft of the core it stays under the machinery wells' floor (WELL_FLOOR).
        yc, zc = bw, -17.0-(-18-x)*.05
        top = (bw*.85, -12.0)
    mid = (zc+zb)/2-2.0
    half = [top, (yc, zc), (yc*.5, mid), (min(5.0, yc*.4), zb)]
    return [(-y, z) for y, z in reversed(half)]+half


# The prow gun deck: three stepped tiers climbing aft (WS 007, WS 008/009, the
# multineedler WS 019), each riser a raked armour step of about nine units, the
# last running on under the spine's arch (X 99) where the spine's glacis
# (os_bridge) takes the climb on up to the citadel. Round 4 (owner: the deck line
# must climb with purpose): bow deck 9, then 13.5, 22.5, 31.5.
GALLERY_W = [(216, 5.0), (214, 5.5), (200, 9.0), (186, 11.0), (150, 17.0), (126, 18.5), (104, 20.0), (99, 20.0)]
GALLERY_Z = [(216, 9.0), (212, 12.5), (177, 13.5), (171, 22.0), (145, 22.5), (139, 31.0), (110, 31.5), (99, 31.5)]
GALLERY_STATIONS = [216, 214, 212, 200, 186, 177, 171, 150, 145, 139, 126, 110, 104, 99]


def gallery_section(x):
    gw, zg, f = _table(GALLERY_W, x), _table(GALLERY_Z, x), fore(x)
    c = 1.2
    base = f['zk']-1.0
    half = [(gw-c, zg), (gw, zg-c), (gw+2.5, base)]
    return [(-y, z) for y, z in reversed(half)]+half


# The waist: the low valley between the glacis and the arm; a flat deck tucked
# under the core's upper chine, falling outboard in one plate to meet the
# lowered arm's inner edge (os_arms).
WAIST_IN = [(106, 38), (60, 53), (2, 77)]
WAIST_OUT = [(106, 58), (40, 72), (2, 89)]
WAIST_DECK = 1.6


def waist_section(x):
    yin, yout, w = _table(WAIST_IN, x), _table(WAIST_OUT, x), fore(x)['w']
    ym = min(w+8.0, yout-4.0)
    # The outer edge runs down into the lowered arm (os_arms: its inner top edge
    # lies near z -15 here), so the valley floor meets the arm with no slot.
    return [(yin, -24.0), (yout-4.0, -28.0), (yout+1.0, -19.0), (ym, WAIST_DECK), (yin, WAIST_DECK)]


def build(kit):
    from os_kit import loft_x
    prow = loft_x(kit, 'Hull prow', [(x, fore_section(x)) for x in FORE_STATIONS if x >= 104], kit.plate, bevel=.2)
    core = loft_x(kit, 'Hull core', [(x, fore_section(x)) for x in FORE_STATIONS if x <= 104], kit.plate, bevel=.2)
    belly = loft_x(kit, 'Hull belly', [(x, belly_section(x)) for x in BELLY_STATIONS], kit.plate, bevel=.2)

    waists = {}
    stations = [106, 100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 2]
    for side, s in (('port', 1), ('starboard', -1)):
        st = []
        for x in stations:
            sec = waist_section(x)
            st.append((x, sec if s > 0 else [(-y, z) for y, z in reversed(sec)]))
        waists[side] = loft_x(kit, f'Hull {side} waist', st, kit.plate, bevel=.2)

    # --- the tiers ---------------------------------------------------------
    tiers = []
    tiers.append(loft_x(kit, 'Hull prow gallery', [(x, gallery_section(x)) for x in GALLERY_STATIONS], kit.plate,
                        bevel=.2))
    cx, cy = TERRACE_C
    # The citadel: a many-sided armoured drum with a straight sloped batter (its
    # foot buried in the core's centre deck), the inner tier stepping up again
    # under the command tower; the gun ring stands on the drum's flat top.
    tiers.append(prism(kit, 'Hull core terrace', disc(cx, cy, TERRACE_R, 20, x_min=-18.0),
                       [(0.0, 18.0), (0.0, 24.0), (6.5, TERRACE_TOP-1.0), (7.5, TERRACE_TOP)], kit.plate, bevel=.2))
    # Its back is raked down toward the core's aft face over the machinery wells,
    # a sloped armour step rather than a sheer wall.
    plane_cut(tiers[-1], 'citadel aft rake', (-14.0, 0, TERRACE_TOP), facet_normal((-1, 0), 55))
    tiers.append(prism(kit, 'Hull core inner terrace', disc(*INNER_C, INNER_R2, 16),
                       [(0.0, TERRACE_TOP-2.0), (0.0, TERRACE_TOP), (2.5, INNER_TOP-.8), (3.3, INNER_TOP)],
                       kit.plate, bevel=.2))

    kit.hull += [prow, core, belly, *waists.values(), *tiers]

    return {'prow': prow, 'core': core, 'belly': belly}
