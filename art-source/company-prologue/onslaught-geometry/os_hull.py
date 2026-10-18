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
PROW_GALLERY_TOP = 40.0           # the prow gallery's glacis crest, under the spine (round 6)
TERRACE_TOP = 36.0
INNER_TOP = 40.0
# The citadel's gun shelves (port plans, mirrored): the aft shelf under WS 014/015 and
# the beam shelves under WS 022/023, each cut down from TERRACE_TOP with a battered riser
# (SHELF_BATTER in over SHELF_BATTER up). Plans are the risers' tops; their feet lie
# SHELF_BATTER inside, clear of each mount's footing ring.
SHELF_BATTER = 5.0
AFT_SHELF = [(4.0, 9.0), (4.0, 80.0), (-40.0, 80.0), (-40.0, 9.0)]
AFT_SHELF_Z = TERRACE_TOP-2.0
BEAM_SHELF = [(25.0, 28.0), (25.0, 80.0), (-9.0, 80.0), (-9.0, 28.0)]
BEAM_SHELF_Z = TERRACE_TOP-5.0
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


# Round 7 (critic 1.2: in bow-detail the stem read as a faceted ball - five short stations
# between X 225 and 212 curled its top over and rounded its flanks): the stem is ONE ruled
# span from the stem head (X 225) to STEM_X, so its faces are flat armour plates meeting on
# hard chines: the top chamfer rises aft at STEM_TOP deg from the stem head to the bow deck
# (it runs on up the bow gallery's nose to 8.5), the sides rake about 27 deg off the
# centreline in plan between the upper and lower chines, and the forefoot runs straight
# down and aft to the keel. Each chine runs parallel at both ends of the span, so every
# face is one true plane. The shoulder skins (os_armour) end on the chamfer's chine.
STEM_X = 205.0
STEM_TOP = 35.0
STEM_DECK_Z = 8.5-2.0*math.tan(math.radians(STEM_TOP))
STEM_HEAD_Z = STEM_DECK_Z-(225.0-STEM_X)*math.tan(math.radians(STEM_TOP))
FORE_W = [(225, 8.0), (205, 17.7), (200, 18.5), (196, 22.5), (192, 25.5), (172, 29.5),
          (148, 34.5), (120, 39.5), (104, 41.5), (60, 58), (20, 74), (0, 82), (-18, 84)]
FORE_YI = [(225, 2.0), (205, 5.4), (200, 6), (186, 8), (150, 12), (126, 13), (104, 12), (80, 22), (60, 30), (40, 40),
           (20, 50), (0, 56), (-18, 58)]
FORE_ZI = [(225, STEM_HEAD_Z), (STEM_X, STEM_DECK_Z), (200, 7.5), (172, 8.5), (150, 11), (126, 16), (110, 23),
           (104, 27), (80, 28), (60, 29), (30, 28), (0, 26), (-18, 25)]
FORE_ZK = [(225, STEM_HEAD_Z-6.0*(STEM_DECK_Z-1.0)/(17.7-5.4)), (205, 1.0), (200, 1.5), (172, 2.5), (148, 3.5), (120, 4), (104, 4), (60, 3.5), (20, 2.5),
           (0, 1.5), (-18, 1)]
FORE_ZL = [(225, -12.5), (205, -14.1), (200, -15), (172, -17), (104, -18), (60, -18), (0, -16), (-18, -15)]
FORE_FL = [(225, .6), (205, 3.3), (196, 4), (150, 6), (104, 8), (60, 10), (0, 12), (-18, 12)]
FORE_KW = [(225, 2), (205, 3.2), (104, 5), (-18, 5)]
FORE_KEEL = [(225, -15.6), (205, -20.5), (104, -34), (60, -34), (0, -30), (-18, -28)]
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
           (-52, 46), (-64, 38)]
# Round 5 (owner: "the central body doesn't need to be as strangely elongated down
# under the ship"; critic: the belly's after face was a blank V wall under the stern
# with strakes hanging off it). The keel line is four straight runs meeting on hard
# knuckles, no deeper than the chest needs to be (-50, about a deck's depth under
# the arms' keels): the FOREFOOT runs straight down and aft from the prow's keel
# (X 168) to the chest knuckle (X 128), so the stem leads; the CHEST keel runs level
# under the glacis to X 88; the keel then rises gently aft under the citadel to the AFT
# KNUCKLE at X -40, where the belly's after end sweeps up steeply (about 30 deg)
# into the aft body's keel (-32, os_stern) and ends buried in it, so from astern
# the belly is a short sloped counter running into the stern, not a hanging wall.
BELLY_KNUCKLE_X = -40.0
BELLY_KEEL = [(168, -26.0), (128, -50.0), (88, -50.0), (BELLY_KNUCKLE_X, -40.0), (-64, -29.0)]
BELLY_STATIONS = [170, 168, 150, 128, 104, 88, 80, 52, 24, 6, 0, -12, -18, -20, -22, -24, -30, BELLY_KNUCKLE_X,
                  -52, -64]


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
# multineedler WS 019), then the glacis running up into the citadel.
# Round 6 (critic A1: forward of the bridge the abeam profile read as one flat
# barge-like shelf, the multineedler bridging the last tier to the spine at one
# height): the skyline now climbs in hard steps from the stem, the lowest dorsal
# point. The bow deck (WS 007) lies at 8.5 and the forward gun deck (WS 008/009) at
# 17, each about five lower than round 5. The needler deck (25.5) stands on it as
# a raised V (critic C: the sprite's prow bands form a forward-pointing V round
# the needler, not straight strips): its nose rises between WS 008 and 009 at
# X 160 and its battered walls run aft and outboard at GALLERY_FLARE from the
# centreline, so the riser is one hard chevron step with an armour lip along its
# crest (os_armour), and the forward gun deck runs on aft beside it as two dark
# flanking decks until the V meets the gallery's edge at about X 108, where the
# glacis legs (os_armour.glacis_split, diverging at about the same angle) carry
# the V on aft to the citadel. Aft of the needler the glacis rises at about 45
# deg (X 114..100) to a crest at 40, level with the spine (os_bridge), which runs
# on level-ish to the citadel: a landing rather than one long ramp, so the abeam
# skyline reads as a step there too. Aft of the crest the gallery narrows into the
# spine and ends in a raked step down onto the core's centre deck, so the grey
# glacis legs come out from under it at once.
GALLERY_W = [(STEM_X, 7.6), (200, 9.0), (186, 11.0), (171, 14.0), (150, 16.8), (126, 21.0), (114, 24.4),
             (104, 25.2), (100, 25.0), (93, 19.0), (89, 16.0)]
GALLERY_Z = [(STEM_X, STEM_DECK_Z), (STEM_X-2.0, 8.5), (177, 8.5), (171, 17.0), (160, 17.0), (157, 25.5), (114, 25.5), (100, 40.0),
             (93, 40.0), (89, 31.0)]
GALLERY_STATIONS = sorted({x for x, _ in GALLERY_W} | {x for x, _ in GALLERY_Z}, reverse=True)
GALLERY_GUN_DECK = 17.0           # the forward gun deck, and the flanking decks beside the V
GALLERY_V_APEX = 160.0            # X of the needler deck's nose, on the centreline
GALLERY_FLARE = 25.0              # deg from the centreline: the V's walls
GALLERY_V_BATTER = 3.0


def gallery_v(x):
    """|Y| of the needler deck's V wall (its crest edge) at X."""
    return max(0.0, (GALLERY_V_APEX-x)*math.tan(math.radians(GALLERY_FLARE)))


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
WAIST_RAKE = 58.0                 # deg from horizontal: the waist's raked forward end (round 7)
WAIST_RAKE_X = 97.0               # where that rake leaves the waist's lowest line (z -28)


def waist_section(x):
    yin, yout, w = _table(WAIST_IN, x), _table(WAIST_OUT, x), fore(x)['w']
    ym = min(w+8.0, yout-4.0)
    # The outer edge runs down into the lowered arm (os_arms: its inner top edge
    # lies near z -15 here), so the valley floor meets the arm with no slot.
    # Round 7 (seam audit: the waist's bottom hung about 20 below the core's lower
    # chine over X 7..103, a keel of its own beside the core's side): its inboard
    # bottom edge now starts on the core's lower chine (buried just inside it, where
    # the belly's upper chine also lies), so the underside runs on from the core's
    # side face as one deadrise falling outboard into the arm.
    f = fore(x)
    yc, zc = f['w']-f['fl']-.4, f['zl']+.4
    return [(yc, zc), (yout-4.0, -28.0), (yout+1.0, -19.0), (ym, WAIST_DECK), (yin, WAIST_DECK)]


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
        # Its forward end, open to the notch between prow and arm, is raked up and
        # forward as one armour face rather than a square end hanging under the prow.
        pad_cut(waists[side], 'waist fore rake', (WAIST_RAKE_X, 0.0, -28.0), (math.sin(math.radians(WAIST_RAKE)), 0.0,
                                                                             -math.cos(math.radians(WAIST_RAKE))))

    # --- the tiers ---------------------------------------------------------
    tiers = []
    tiers.append(loft_x(kit, 'Hull prow gallery', [(x, gallery_section(x)) for x in GALLERY_STATIONS], kit.plate,
                        bevel=.2))
    # The needler deck's V: everything outboard of its walls is cut down to the gun deck.
    xa, xb = GALLERY_V_APEX+6.0, 100.0
    for sgn in (1, -1):
        plan = ccw([(xa, 0.0), (GALLERY_V_APEX, 0.0), (xb, sgn*gallery_v(xb)), (xb, sgn*50.0), (xa, sgn*50.0)])
        cutter = prism(kit, 'Temporary prow gallery V cutter', plan,
                       [(GALLERY_V_BATTER, GALLERY_GUN_DECK), (0.0, GALLERY_GUN_DECK+GALLERY_V_BATTER), (0.0, 70.0)],
                       kit.plate, bevel=0)
        boolean(tiers[0], cutter)
    cx, cy = TERRACE_C
    # The citadel: a many-sided armoured drum with a straight sloped batter (its
    # foot buried in the core's centre deck), the inner tier stepping up again
    # under the command tower; the gun ring stands on the drum's flat top.
    tiers.append(prism(kit, 'Hull core terrace', disc(cx, cy, TERRACE_R, 20, x_min=-18.0),
                       [(0.0, 18.0), (0.0, 24.0), (6.5, TERRACE_TOP-1.0), (7.5, TERRACE_TOP)], kit.plate, bevel=.2))
    # Its back is raked down toward the core's aft face over the machinery wells,
    # a sloped armour step rather than a sheer wall.
    plane_cut(tiers[-1], 'citadel aft rake', (-14.0, 0, TERRACE_TOP), facet_normal((-1, 0), 55))
    # Round 5 (owner: "turrets against the hull ... not all on one plane"; critic: the
    # tier turrets all sat on one height ring): the citadel's top is terraced where its
    # guns fall, so each mount stands on its own shelf and the drum reads as stepped
    # armour, not a plate. The drivers (WS 012/013) keep the forward top (36), the
    # heavy autocannon (WS 014/015) stand on an aft shelf one step down, and the
    # Typhoons (WS 022/023) on beam shelves let into the drum's flanks another step
    # down. Every riser is a battered plate (45 deg), so the steps read as armour.
    for s in (1, -1):
        for tag, plan, floor in (('aft', AFT_SHELF, AFT_SHELF_Z), ('beam', BEAM_SHELF, BEAM_SHELF_Z)):
            pl = ccw([(x, s*y) for x, y in plan])
            cutter = prism(kit, f'Temporary citadel {tag} shelf cutter', pl,
                           [(SHELF_BATTER, floor), (0.0, floor+SHELF_BATTER), (0.0, 60.0)], kit.plate, bevel=0)
            boolean(tiers[1], cutter)
    tiers.append(prism(kit, 'Hull core inner terrace', disc(*INNER_C, INNER_R2, 16),
                       [(0.0, TERRACE_TOP-2.0), (0.0, TERRACE_TOP), (2.5, INNER_TOP-.8), (3.3, INNER_TOP)],
                       kit.plate, bevel=.2))

    kit.hull += [prow, core, belly, *waists.values(), *tiers]

    return {'prow': prow, 'core': core, 'belly': belly}
