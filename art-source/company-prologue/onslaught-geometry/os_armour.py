"""The Hegemony armour: the orange and dark grey plates onslaught_hegemony.png lays
over the hull, built as sloped armour that steps down from the gun tiers to the
hull's edges (owner, pass 3: terracing, not flat deck and plating).

  prow lip        round 6: a grey chevron coaming on the needler deck's V riser;
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
  keel strakes    three heavy bars riding the belly's knuckles (keel flat and both
                  lower chines) to the aft knuckle, and under the prow, aft body
                  and tail following each keel curve.
"""
import math
import bpy
from os_kit import arched_mass, ccw, clip_rect, facet_normal, loft_x, mirror_y, plane_cut, prism, strip_plan, _left
from os_hull import (AFT, AFT_OUTER, ARM, ARM_INNER, ARM_OUTER, CORE, CORE_HALF, FORE_STATIONS, FORE_W, FORE_YI, GALLERY_W, LEG_PORT,
                     PROW, PROW_HALF, TAIL, TAIL_HALF, WAIST_DECK, WELL_FLOOR, _table, fore, fore_side_y, fore_top, full,
                     keel_curve, BELLY_KNUCKLE_X, BELLY_STATIONS, belly_section, GALLERY_FLARE, GALLERY_GUN_DECK,
                     GALLERY_V_APEX, gallery_v, STEM_X)


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


def _belly_at(x):
    """The belly's lofted section at X: belly_section at its stations, linear between them
    (exactly the loft's own surface), so a bar sampled here lies on its plates."""
    st = sorted(BELLY_STATIONS, reverse=True)
    for xa, xb in zip(st, st[1:]):
        if xb <= x <= xa:
            t = (xa-x)/(xa-xb)
            return [(ya+(yb-ya)*t, za+(zb-za)*t) for (ya, za), (yb, zb) in zip(belly_section(xa), belly_section(xb))]
    return belly_section(st[0] if x > st[0] else st[-1])


def _unit(a, b):
    d = ((b[0]-a[0])**2+(b[1]-a[1])**2)**.5
    return ((b[0]-a[0])/d, (b[1]-a[1])/d)


def _chine_bar(c, up, down, t, w=3.4, sink=.7):
    """A bar section (port, YZ) riding the convex knuckle at c between the plate running to
    `up` and the plate running to `down`: proud of both by t, `w` along each, its back sunk."""
    u, l = _unit(c, up), _unit(c, down)
    nu, nl = (-u[1], u[0]), (l[1], -l[0])
    # outward: away from the section's inside (toward the hull's centreline above)
    inside = (0.0, max(up[1], down[1])+10.0)
    if (c[0]-inside[0])*nu[0]+(c[1]-inside[1])*nu[1] < 0:
        nu = (-nu[0], -nu[1])
    if (c[0]-inside[0])*nl[0]+(c[1]-inside[1])*nl[1] < 0:
        nl = (-nl[0], -nl[1])
    k = 1.0/(1.0+nu[0]*nl[0]+nu[1]*nl[1])
    # Each end of the bar is a vertical face dropped from its sunk back edge to t (normal)
    # under the plate, so no column sees air between the plate and the bar's end.
    a = (c[0]+u[0]*w-nu[0]*sink, c[1]+u[1]*w-nu[1]*sink)
    b = (c[0]+l[0]*w-nl[0]*sink, c[1]+l[1]*w-nl[1]*sink)

    def under(p, d, n):
        zf = c[1]+d[1]/d[0]*(p[0]-c[0]) if abs(d[0]) > 1e-6 else p[1]
        return (p[0], zf-t/max(abs(n[1]), .3))
    pts = [a, under(a, u, nu),
           (c[0]+(nu[0]+nl[0])*k*t, c[1]+(nu[1]+nl[1])*k*t),
           under(b, l, nl), b,
           (c[0]-(nu[0]+nl[0])*k*sink*2, c[1]-(nu[1]+nl[1])*k*sink*2)]
    return pts


def _keel_chine_bar(c, up, t, w):
    """A bar section (port, YZ) riding the keel chine c - the outboard edge of the flat keel
    band - and wrapping onto the bilge plane running from c to `up`: its flat underside lies
    t under the band, its outer face lies t proud of the bilge plane, and its back is sunk
    into the hull, so every column through it meets solid hull above it."""
    u = _unit(c, up)
    n = (u[1], -u[0])                      # the bilge plane's outward normal (down, outboard)
    a = (c[0]+u[0]*w+n[0]*t*.5, c[1]+u[1]*w+n[1]*t*.5)
    back = (a[0], c[1]+(a[0]-c[0])*u[1]/max(u[0], 1e-3)+1.0)     # straight up into the hull
    return [(c[0]-w, c[1]+1.2), (c[0]-w, c[1]-t+.4), (c[0]-w+.5, c[1]-t), (c[0]+n[0]*t, c[1]-t), a, back,
            (c[0]-.4, c[1]+2.0)]


def keel_strakes(kit):
    """Round 5 (critic: the belly's crossing strakes and frames read as a random grid from
    below, and hung off its after face as bars): three heavy strakes only, lofted on the
    belly's own stations so they follow every knuckle of the keel line exactly.

    Round 7 (critic: three long parallel bars from the forefoot to the stern read as
    stripes): each strake is two armour bars ending square on the keel line's knuckles.
    The fore bar runs from the forefoot over the chest to its after knuckle (X 88); the aft
    bar runs from there to the aft knuckle (os_hull.BELLY_KNUCKLE_X). At the knuckle the two
    lap: the fore bar stands prouder and runs a few units past the aft bar's blunt forward
    end, so the break is an offset lap joint, not a groove in one long stripe.

    Round 11 (critic a: run the strakes parallel to X along the underside's chines): strakes
    2 and 3 ride the keel chines - the edges of the flat keel band, parallel to X from the
    chest aft - and strake 1 is a low keel shoe let into the band's centre, so from below the
    band reads as one plate edged by two heavy bars."""
    runs = ((165.0, 85.5, 2.6, .5), (90.0, BELLY_KNUCKLE_X+.5, 1.8, .35))
    for r, (x0, x1, tc, tk) in enumerate(runs):
        xs = sorted({x for x in BELLY_STATIONS if x1 < x < x0} | {x0, x0-1.2, x1, x1+1.2}, reverse=True)

        def proud(x, full):
            # a short chamfer at each end (1.2 long, down to a third), not a run-out
            return full*.35 if x >= x0-.01 or x <= x1+.01 else full

        keel, chines = [], []
        for x in xs:
            sec = _belly_at(x)
            n = len(sec)//2
            top, chine, kp = sec[n:]            # port half: top, bilge chine, keel chine
            t = proud(x, tk)
            # the shoe: a broad, low plate let into the band, well inside its chines
            kw = max(kp[0]-5.0, 1.5)
            zb = kp[1]
            keel.append((x, [(-kw-.2, zb+1.4), (-kw, zb-t+.3), (-kw+.5, zb-t), (kw-.5, zb-t), (kw, zb-t+.3),
                             (kw+.2, zb+1.4)]))
            chines.append((x, _keel_chine_bar(kp, chine, proud(x, tc), 3.0 if r == 0 else 3.6)))
        tag = '' if r == 0 else ' aft'
        loft_x(kit, f'Armour core keel strake 1{tag}', keel, kit.grey, bevel=.2)
        for name, s in ((f'Armour core keel strake 2{tag}', 1), (f'Armour core keel strake 3{tag}', -1)):
            st = [(x, sec if s > 0 else [(-y, z) for y, z in reversed(sec)]) for x, sec in chines]
            loft_x(kit, name, st, kit.grey, bevel=.2)
    shell_strip(kit, 'Armour prow keel strake', bpy.data.objects['Hull prow'], 1, 0, 2.6, 2.4, kit.grey, xr=(164, 216))


# --- forebody armour skins ------------------------------------------------------
# Owner, round 3: "look closely at the pixel art". The sprite paints the prow's
# flanks as one broad orange band from the bow to the prow root, carried on
# outboard of the A-shaped dark grey glacis legs back to the citadel, broken by
# dark seam lines, not separate blocks. So each band is a skin lofted on the
# forebody's own shoulder plane (os_hull.fore_top): a constant thickness proud
# of it, wrapped over the upper chine with a small lip, and V-grooved at the
# seams and ends so neighbouring segments read as one band with seams. The grey
# legs stand thicker than the orange outboard of them: a terraced step down.

def _skin_section(x, ya, yb, t, wrap, lip=.8, skirt=None, lip_t=None):
    f = fore(x)
    lt = t if lip_t is None else lip_t
    ya, yb = max(ya, 0.0), min(yb, f['w'])
    yk = min(max(f['yi'], ya+.2), yb-.2)
    top = [(ya, fore_top(x, ya)+t), (yk, fore_top(x, yk)+t)]
    bot = [(yk, fore_top(x, yk)-.8), (ya, fore_top(x, ya)-.8)]
    if wrap and skirt is not None:
        # Over the waist the lip comes down as a skirt onto the valley deck, so the
        # band's edge meets the deck instead of overhanging it.
        out = [(f['w']+lip, f['zk']+lt*.7), (f['w']+lip, skirt), (fore_side_y(x, skirt)-.3, skirt)]
    elif wrap:
        out = [(f['w']+lip, f['zk']+lt*.7), (fore_side_y(x, f['zk']-3.0)-.3, f['zk']-3.0)]
    else:
        out = [(yb, fore_top(x, yb)+t), (yb, fore_top(x, yb)-.8)]
    return top+out+bot


def skin(kit, name, x0, x1, inner, outer, t, mat, s, wrap=False, seams=(), lip=.8, thin=.6, skirt=None):
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
        # Round 8: the seams groove the top face only; the lip over the chine keeps one
        # height end to end, so the band's outer edge is one straight line, not notched.
        sec = _skin_section(x, inner(x), yb, thin if groove else t, outer is None or wrap, lip, skirt, lip_t=t)
        if s < 0:
            sec = [(-y, z) for y, z in reversed(sec)]
        stations.append((x, sec))
    return loft_x(kit, name, stations, mat, bevel=.15)


def glacis_split(x):
    """|Y| where the grey glacis leg gives way to the orange band outboard of it."""
    f = fore(x)
    return f['yi']+.42*(f['w']-f['yi'])


# The needler deck's armour lip (round 6, critic A1/C): a dark grey chevron coaming
# along the crest of the V riser (os_hull.gallery_v), apex forward between WS 008
# and 009 and its legs raking aft at the V's angle until the glacis rises past it
# (X 112). Its outer face stands on the V wall's crest line and runs down into the
# gallery, so it never overhangs the battered riser below.
LIP_W = 2.4
LIP_TOP = 27.6
LIP_AFT = 112.0


def prow_lip(kit):
    t = math.tan(math.radians(GALLERY_FLARE))
    back = LIP_W/math.sin(math.radians(GALLERY_FLARE))      # the inner V's apex, aft of the outer
    yo, yi = gallery_v(LIP_AFT), (GALLERY_V_APEX-back-LIP_AFT)*t
    plan = ccw([(GALLERY_V_APEX, 0.0), (LIP_AFT, -yo), (LIP_AFT, -yi), (GALLERY_V_APEX-back, 0.0), (LIP_AFT, yi),
                (LIP_AFT, yo)])
    return prism(kit, 'Armour prow gallery lip', plan, [(0.0, GALLERY_GUN_DECK-.6), (0.0, LIP_TOP-.8), (.8, LIP_TOP)],
                 kit.grey, bevel=.2)


# The prow gallery's glacis flanks (round 8, critic: top steps of 17-21 where the glacis
# crest (X 114..89, up to z 40) stood as a sheer plate wall over the band ends at
# (101, +-25)). Nothing stood in air there - the bands run on under the gallery - but the
# wall read as an unfinished edge. Each flank is now a deliberate tier: a dark grey
# armour cheek, the glacis leg climbing the gallery's flank, its foot bedded in the bands
# a few units outboard of the wall and its face battered up to just under the crest
# chamfer, so the skyline steps band -> sloped grey face -> crest.
CHEEK_X = (115.0, 91.0)
CHEEK_FOOT = 7.0             # how far outboard of the gallery wall the cheek's foot lands
CHEEK_DROP = 2.6             # its top edge below the gallery's crest


def gallery_cheek(kit, side, s):
    from os_hull import GALLERY_Z
    xs = sorted({CHEEK_X[0], CHEEK_X[1], 104.0} | {x for x, _ in GALLERY_W if CHEEK_X[1] < x < CHEEK_X[0]}
                | {x for x, _ in GALLERY_Z if CHEEK_X[1] < x < CHEEK_X[0]}, reverse=True)
    stations = []
    for x in xs:
        gw, zg = _table(GALLERY_W, x), _table(GALLERY_Z, x)
        # its forward end grows out of the needler deck's flank as the glacis rises (no
        # splayed foot over the shoulder band), full width from the crest's foot aft
        k = min(max((CHEEK_X[0]-x)/(CHEEK_X[0]-104.0), 0.0), 1.0)
        yf = gw+1.5+(CHEEK_FOOT-1.5)*k
        zf = fore_top(x, yf)+.5            # bedded: under the bands' top (orange 1.4, grey 2.8)
        zt = max(zg-CHEEK_DROP, zf+2.0)
        zb = zf-2.5
        sec = [(gw-2.0, zb), (yf, zb), (yf, zf), (gw+1.4, zt-.8), (gw+.7, zt), (gw-2.0, zt)]
        if s < 0:
            sec = [(-y, z) for y, z in reversed(sec)]
        stations.append((x, sec))
    ob = loft_x(kit, f'Armour {side} prow gallery cheek', stations, kit.grey, bevel=.2)
    # the forward end raked back 60 deg from its foot, an armour nose, not a square end
    zf0 = min(z for _, z in stations[0][1])+2.5
    plane_cut(ob, 'cheek nose rake', (CHEEK_X[0]-.5, 0.0, zf0), (math.sin(math.radians(60)), 0.0,
                                                                 math.cos(math.radians(60))))
    return ob


def build(kit):
    prow_lip(kit)
    for side, s in (('port', 1), ('starboard', -1)):
        gallery_cheek(kit, side, s)
        # Prow shoulders: the orange band from the gallery's foot over the upper chine.
        gal = lambda x: _table(GALLERY_W, x)+.6
        for k, (xa, xb, seam) in enumerate(((STEM_X-.4, 175.3, 191.0), (174.7, 140.3, 157.0), (139.7, 111.0, 125.0)), 1):
            skin(kit, f'Armour {side} prow shoulder {k}', xa, xb, gal, None, 1.4, kit.orange, s, seams=(seam,))
        # Prow collar: a heavier strap across the band where the prow meets the core.
        skin(kit, f'Armour {side} prow collar', 111.0, 103.4,
             lambda x: min(gal(x), glacis_split(x)-.3) if x >= 104 else glacis_split(x)-.3, None, 2.6, kit.orange, s,
             lip=1.2, skirt=WAIST_DECK-.3)
        # Glacis: the dark grey leg (thicker, so the orange outboard steps down from
        # it) and the orange band carried on outboard of it to the citadel's flank.
        skin(kit, f'Armour {side} glacis grey band', 108.0, 24.0, lambda x: max(fore(x)['yi']-6.0, 9.0),
             glacis_split, 2.8, kit.grey, s, seams=(92, 76, 60, 44), thin=1.4)
        skin(kit, f'Armour {side} glacis band', 104.0, 4.0, lambda x: glacis_split(x)-.4, None, 1.4, kit.orange, s,
             seams=(80, 56, 30), skirt=WAIST_DECK-.3)
        # Waist bay: a dark frame on the valley deck carrying three amber bay lamps.
        # (inboard of the deck's outer fall, so the frame stands on deck all round)
        # Round 5 (seam audit): it stood over the deck's outer fall at X 101; now it lies
        # between the glacis band's skirt (|Y| 43.3) and the fall (|Y| 50.5) with its
        # foot sunk into the deck.
        kit.box(f'Armour {side} waist bay frame', (100.5, s*46.2, WAIST_DECK+.4), (6.0, 5.0, 2.2), kit.dark,
                bevel=.2)
        for k, y in enumerate((44.8, 46.2, 47.6), 1):
            kit.box(f'Armour {side} waist bay lamp {k}', (99.6, s*y, WAIST_DECK+1.75), (1.6, 1.1, .9), kit.lamp,
                    bevel=0)
        # Core aft face above the machinery well: buttresses standing on the well
        # floor, raked aft, their tops following the deck, and a coaming capping
        # the face's top edge.
        # Round 7 (seam audit): buttress 1 stood at |Y| 30, buried in the aft glacis
        # (os_stern, |Y| 15-44); it is now the glacis' outboard end-stop at |Y| 46, and
        # buttress 2 moves out to keep the three evenly spaced to the third.
        for k, y in enumerate((46.5, 59, 72), 1):
            top = fore_top(-18.0, y)+1.0
            b = prism(kit, f'Armour {side} core aft buttress {k}', _side([(-17.0, y-2.25), (-17.0, y+2.25),
                                                                         (-23.5, y+2.25), (-23.5, y-2.25)], s),
                      [(0.0, WELL_FLOOR-.6), (0.0, top+6.0)], kit.grey, bevel=.3)
            plane_cut(b, 'buttress rake', (-23.5, 0, WELL_FLOOR), (-.99, 0, .15))
            f = fore(-18.0)
            dz = (f['zk']-f['zi'])/(f['w']-f['yi']) if y > f['yi'] else 0.0
            plane_cut(b, 'buttress top', (-18.0, s*y, top), (0, -s*dz, 1.0))
        # The lintel caps the centre deck's aft edge only. Round 7 (seam audit): it ran out
        # over the shoulder to |Y| 82 and stood 0.8 proud of the aft face, a slot of daylight
        # 18-29 deep over the outer well; now it ends on the deck's edge (|Y| yi) with its
        # aft face flush on the core's, and its outboard end is battered down the shoulder.
        f = fore(-18.0)
        ys = [22.0, f['yi']-3.0, f['yi']]
        sec = [(ys[0], fore_top(-18.0, ys[0])+1.4), (ys[1], fore_top(-18.0, ys[1])+1.4),
               (ys[2], fore_top(-18.0, ys[2])-1.0), (ys[2], fore_top(-18.0, ys[2])-2.0),
               (ys[0], fore_top(-18.0, ys[0])-2.0)]
        if s < 0:
            sec = [(-y, z) for y, z in reversed(sec)]
        loft_x(kit, f'Armour {side} core aft lintel', [(-13.5, sec), (-18.15, sec)], kit.grey, bevel=.3)
    keel_strakes(kit)
