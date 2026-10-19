"""Tarsus arms: pass 2 detail on the twin forward cargo arms and their bow drums
(Hull port|starboard arm, Hull port|starboard bow drum), plus the bow masts.

New parts are named 'Hull <side> arm ...', 'Hull <side> bow drum ...' or 'Mast ...'.

The arm is a faceted loft (ts_hull.ARM_SECTION through ARM_STATIONS), so every
fitting here is placed on a named facet of that section and carried through the
stations' pod squeeze and bow height scale (fpt): nothing assumes a vertical wall
or one flat deck. Facets of the port section, in its own order:

  F5 lower pod wall (42,-5)->(43.5,-1)   F6 upper pod wall (43.5,-1)->(43,3)
  F7 pod shoulder (43,3)->(39.5,7.5)     F8 pod deck (39.5,7.5)->(30,8.5)
  F9 step (30,8.5)->(26,6)               F10 strip deck (26,6)->(13,5)
  F11 inboard chamfer (13,5)->(9.5,2.5)  F12 inboard wall; F0-F4 the keel facets

Read off tarsus_base.png at 8x: three armour sections split by ring seams (X 47,
61, 80) cut round every facet; ochre plates on the pod deck, shoulder and lower
wall; pale plating on the strip (the sprite's cream strip) with its yellow lamp
pair; the orange block and dark grille at X 80-88 (port small, starboard spread
over strip, step and pod deck as drawn). Steel strakes run along the creases that
make the section: the pod bulge (a rubbing strake), the shoulder, the step's top
edge and the inboard chamfer. The cargo door folds across the bulge, one leaf per
wall facet; the personnel hatch sits in the shoulder. The bow drum is a faceted
casting with a bolted crown ring, radial top ribs and girth stiffeners; a forward
antenna mast stands on the strip beside it (sprite X 105-118).

Nothing here joins kit.hull, so the turret barbettes and the grapple receivers
(70, rel 26, on the step's foot) keep their seats, and the step and strip are left
bare for X 65.5-74.5 round each receiver. Grooves are shallow MANIFOLD cuts.
"""
import math
from ts_kit import Parts, cut, obj
import ts_hull as H

SEC = H.ARM_SECTION
FULL = max(r for r, _ in SEC)
STATIONS = sorted(H.ARM_STATIONS)
RING_SEAMS = (47.0, 61.0, 80.0)
DECK_SEAMS = (54.0, 70.5)
PATCH = (65.5, 74.5)                       # bare step and strip round the grapple receiver
DOOR_X = (63.5, 76.5)                      # cargo door (13 u = 6.6 m) across F5/F6
HATCH_X = (50.8, 53.0)                     # personnel hatch (1.1 x 2.2 m) in the shoulder
L, U, S, DECK, STEP, STRIP, INCH, INW = 5, 6, 7, 8, 9, 10, 11, 12


def station(x):
    """(outboard rel y, height scale) of the loft at x."""
    if x <= STATIONS[0][0]:
        return STATIONS[0][1:]
    for (x0, r0, z0), (x1, r1, z1) in zip(STATIONS, STATIONS[1:]):
        if x0 <= x <= x1:
            t = (x-x0)/(x1-x0)
            return r0+(r1-r0)*t, z0+(z1-z0)*t
    return STATIONS[-1][1:]


def fpt(x, r, z, s):
    """A full-size section point (rel y, z) carried to the loft at x."""
    ro, zs = station(x)
    if r > H.ARM_POD_IN:
        r = H.ARM_POD_IN+(r-H.ARM_POD_IN)*(ro-H.ARM_POD_IN)/(FULL-H.ARM_POD_IN)
    return (x, H.y(r, s), z*zs)


def facet(fi):
    (r0, z0), (r1, z1) = SEC[fi], SEC[(fi+1) % len(SEC)]
    ln = math.hypot(r1-r0, z1-z0)
    return (r0, z0), ((r1-r0)/ln, (z1-z0)/ln), ((z1-z0)/ln, -(r1-r0)/ln), ln


def fslab(p, fi, xu, o0, o1, s):
    """Hexahedron on facet fi over the (x, u) quad xu (u: 0..1 along the facet), o out of it."""
    (r0, z0), (dr, dz), (nr, nz), ln = facet(fi)
    vs = []
    for o in (o0, o1):
        for x, u in xu:
            vs.append(fpt(x, r0+dr*u*ln+nr*o, z0+dz*u*ln+nz*o, s))
    return p._add(vs, [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)])


def fbox(p, fi, x0, x1, u0, u1, o0, o1, s):
    return fslab(p, fi, [(x0, u0), (x1, u0), (x1, u1), (x0, u1)], o0, o1, s)


def fprism(p, ring, x0, x1, s):
    """Prism along x over a closed full-size section polygon [(rel y, z)], carried by fpt."""
    n = len(ring)
    vs = [fpt(x, r, z, s) for x in (x0, x1) for r, z in ring]
    fs = [tuple(range(n)), tuple(range(n, 2*n))]
    fs += [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
    return p._add(vs, fs)


def fat(fi, u, o):
    """Full-size section point at u along facet fi, o out of it."""
    (r0, z0), (dr, dz), (nr, nz), ln = facet(fi)
    return (r0+dr*u*ln+nr*o, z0+dz*u*ln+nz*o)


def ulen(fi):
    return facet(fi)[3]


def rel_u(fi, r):
    """u on a deck-like facet at full-size rel y r."""
    (r0, _), (dr, _), _, ln = facet(fi)
    return (r-r0)/(dr*ln)


def crease(p, fa, fb, x0, x1, s, out=.5, w=.35):
    """A strake along the crease where facet fa ends and fb begins."""
    fbox(p, fa, x0, x1, 1-w/ulen(fa), 1.0, -.1, out, s)
    fbox(p, fb, x0, x1, 0.0, w/ulen(fb), -.1, out, s)


def seams(kit, side, s):
    g = Parts()
    for x in RING_SEAMS:
        for fi in range(len(SEC)):
            e = .5/ulen(fi)
            j = .004*fi                    # no two passes share a cut plane
            fbox(g, fi, x-.22+j, x+.22+j, -e, 1+e, -.4, 1.5, s)
    for x in DECK_SEAMS:
        fbox(g, DECK, x-.22, x+.22, .03, .97, -.4, 1.5, s)
        if not PATCH[0]-1 < x < PATCH[1]+1:
            fbox(g, STRIP, x-.22, x+.22, .03, .97, -.4, 1.5, s)
    # A strata seam along the shoulder, and plate seams across the bow stations.
    fbox(g, S, 41.5, 90.5, .48, .55, -.35, 1.5, s)
    for x in (91.0, 97.0, 103.0):
        for fi in (L, U, S, DECK):
            fbox(g, fi, x-.2, x+.2, -.05, 1.05, -.35, 1.5, s)
    cut(kit, obj(f'Hull {side} arm'), g, kit.dark)


def plates(kit, side, s):
    n = f'Hull {side} arm'
    ochre, cream, grey, orange, steel = Parts(), Parts(), Parts(), Parts(), Parts()
    wall_spans = [(41.6, 46.7), (47.3, 60.7), (61.3, 79.7), (80.3, 90.6), (91.4, 96.6), (97.4, 102.6)]
    for x0, x1 in wall_spans:
        fbox(ochre, S, x0, x1, .07, .45, -.1, .3, s)
        if not (x0 < HATCH_X[1]+.5 and x1 > HATCH_X[0]-.5):
            fbox(ochre, S, x0, x1, .58, .93, -.1, .3, s)
        else:
            fbox(ochre, S, x0, HATCH_X[0]-.6, .58, .93, -.1, .3, s)
            fbox(ochre, S, HATCH_X[1]+.6, x1, .58, .93, -.1, .3, s)
        if not (x0 < DOOR_X[1]+1 and x1 > DOOR_X[0]-1):
            fbox(ochre, L, x0, x1, .1, .88, -.1, .28, s)
    deck_spans = [(41.6, 46.7), (47.3, 53.7), (54.3, 60.7), (61.3, 70.2), (70.8, 79.7)]
    for x0, x1 in deck_spans+[(91.4, 96.6)]:
        fbox(ochre, DECK, x0, x1, .05, .93, -.1, .45, s)
    fbox(ochre, DECK, 80.6, 88.4, .05, .25, -.1, .45, s)
    fbox(orange, DECK, 80.6, 88.4, .3, .95, -.1, .6, s)
    if s < 0:
        fbox(orange, STEP, 80.6, 88.4, .05, .95, -.1, .45, s)
        fbox(orange, STRIP, 80.6, 88.4, .02, rel_u(STRIP, 23.4), -.1, .4, s)
    # The pale strip: inner and outer runs either side of the lamp line, bare round the receiver.
    a, b = rel_u(STRIP, 25.6), rel_u(STRIP, 19.3)
    c, d = rel_u(STRIP, 18.7), rel_u(STRIP, 13.4)
    for x0, x1 in deck_spans+[(80.3, 88.4)]:
        if x0 >= 80:
            fbox(cream, STRIP, x0, x1, c, d, -.1, .3, s)
            fbox(cream, STRIP, x0, x1, rel_u(STRIP, 23.0) if s < 0 else a, b, -.1, .3, s)
            continue
        # Both runs stop at the patch: the receiver stands on the strip at rel 19.5.
        for u0, u1 in ((x0, min(x1, PATCH[0])), (max(x0, PATCH[1]), x1)):
            if u1 > u0+1:
                fbox(grey if u1 < 47 else cream, STRIP, u0, u1, c, d, -.1, .3, s)
                fbox(cream, STRIP, u0, u1, a, b, -.1, .3, s)
    # Step facet: plates outside the receiver patch, and the port grille block forward.
    for x0, x1 in ((41.6, 46.7), (47.3, 60.7), (61.3, PATCH[0]), (PATCH[1], 79.7)):
        fbox(grey, STEP, x0, x1, .12, .88, -.1, .3, s)
    if s > 0:
        fbox(grey, STEP, 81.0, 88.4, .05, .95, -.1, .45, s)
    ochre.build(kit, f'{n} pod armour plates', kit.ochre, .06)
    cream.build(kit, f'{n} strip deck plating', kit.cream, .05)
    grey.build(kit, f'{n} grey service plates', kit.grey, .05)
    orange.build(kit, f'{n} signal panel', kit.orange, .06)
    # Crease strakes: the pod bulge (broken by the cargo door), the shoulder cap, the step's
    # top edge (clear of the receiver) and the inboard chamfer edge.
    crease(steel, L, U, 42.0, DOOR_X[0]-1.2, s)
    crease(steel, L, U, DOOR_X[1]+1.2, 90.6, s)
    crease(steel, U, S, 42.0, 96.6, s, out=.35, w=.3)
    crease(steel, DECK, STEP, 41.6, PATCH[0]-1.0, s, out=.35, w=.3)
    crease(steel, DECK, STEP, PATCH[1]+1.0, 90.0, s, out=.35, w=.3)
    crease(steel, INCH, INW, 62.0, 104.0, s, out=.45, w=.35)
    steel.build(kit, f'{n} crease strakes', kit.steel, .05)
    lv = Parts()
    for i in range(6):
        x = 81.6+1.15*i
        if s > 0:
            fbox(lv, STEP, x, x+.5, .15, .85, .45, .55, s)
        else:
            fbox(lv, DECK, x, x+.5, .6, .9, .6, .7, s)
    lv.build(kit, f'{n} deck grille louvres', kit.dark)


def deck_fittings(kit, side, s):
    n = f'Hull {side} arm'
    covers, bolts, grilles = Parts(), Parts(), Parts()
    for fi, x0, x1, r0, r1, o in ((DECK, 42.6, 45.8, 32.0, 37.0, .45), (DECK, 55.6, 59.6, 33.2, 37.6, .45),
                                  (STRIP, 82.0, 86.6, 13.6, 17.6, .3), (STRIP, 48.0, 52.2, 20.0, 25.0, .3)):
        u0, u1 = sorted((rel_u(fi, r0), rel_u(fi, r1)))
        fbox(covers, fi, x0, x1, u0, u1, o-.05, o+.3, s)
        du = .45/ulen(fi)
        for x in (x0+.45, x1-.45):
            for u in (u0+du, u1-du):
                fbox(bolts, fi, x-.18, x+.18, u-.4*du, u+.4*du, o+.3, o+.45, s)
    for x0, x1 in ((50.0, 52.6), (64.0, 67.0), (73.0, 76.0)):
        fbox(grilles, DECK, x0, x1, rel_u(DECK, 37.2), rel_u(DECK, 34.8), .45, .55, s)
    covers.build(kit, f'{n} bolted service covers', kit.steel, .04)
    bolts.build(kit, f'{n} cover bolts', kit.steel)
    grilles.build(kit, f'{n} pod vent grilles', kit.dark)
    # Deck vent between the sprite's two yellow strip lamps, and the lamps.
    u0, u1 = rel_u(STRIP, 18.2), rel_u(STRIP, 14.6)
    fbox(Parts(), STRIP, 57.4, 60.2, u0, u1, .25, .8, s).build(kit, f'{n} deck vent', kit.steel, .05)
    lv = Parts()
    for x in (57.7, 58.5, 59.3):
        fbox(lv, STRIP, x, x+.4, u0+.03, u1-.03, .8, .88, s)
    lv.build(kit, f'{n} deck vent louvres', kit.dark)
    hous, lamps = Parts(), Parts()
    for x in (55.6, 61.8):
        fbox(hous, STRIP, x-.8, x+.8, rel_u(STRIP, 17.8), rel_u(STRIP, 16.0), .25, .6, s)
        fbox(lamps, STRIP, x-.5, x+.5, rel_u(STRIP, 17.5), rel_u(STRIP, 16.3), .6, .85, s)
    hous.build(kit, f'{n} deck lamp housings', kit.dark)
    lamps.build(kit, f'{n} deck lamps', kit.lamp)
    # Round deck access hatch on the strip, seated on the facet.
    p, _ = kit.surface(44.2, H.y(22.5, s))
    Parts().cyl((44.2, H.y(22.5, s), p.z-.1), 1.6, .55, 'Z', 16).build(kit, f'{n} deck hatch rim', kit.steel, .05)
    Parts().cyl((44.2, H.y(22.5, s), p.z+.45), 1.2, .16, 'Z', 16).build(kit, f'{n} deck hatch lid', kit.grey)


def stripes(p, fi, x0, x1, u0, u1, s, o=-.12):
    """Diagonal hazard stripes on a frame band (x0..x1, u0..u1) of one facet."""
    pitch, w = 1.6, .55
    x = x0
    while x+w+.6 <= x1:
        fslab(p, fi, [(x, u0), (x+w, u0), (x+w+.6, u1), (x+.6, u1)], o, o+.08, s)
        x += pitch


def cargo_door(kit, side, s):
    """A two-leaf door folded across the pod bulge: lower leaf on F5, upper leaf on F6."""
    n = f'Hull {side} arm'
    x0, x1 = DOOR_X
    band = .7
    lo, hi = .2, .72                       # door bottom on F5, top on F6 (u)
    bl, bu = band/ulen(L), band/ulen(U)
    rec = Parts()
    # One shell across both wall facets, so the fold has no seam between two cuts.
    d = .5
    (r0, z0), (dr0, dz0), (nr0, nz0), _ = facet(L)
    (r1, z1), (dr1, dz1), (nr1, nz1), _ = facet(U)
    # inner crease: the two facets' -d offsets meet
    a = (r1-d*nr1-(r0-d*nr0), z1-d*nz1-(z0-d*nz0))
    t = (a[0]*dz1-a[1]*dr1)/(dr0*dz1-dz0*dr1)
    inner = (r0-d*nr0+dr0*t, z0-d*nz0+dz0*t)
    ring = [fat(L, lo-bl, -d), inner, fat(U, hi+bu, -d), fat(U, hi+bu, 1.5), fat(U, 0, 2.0),
            fat(L, 1, 2.0), fat(L, lo-bl, 1.5)]
    fprism(rec, ring, x0-band, x1+band, s)
    fbox(rec, S, HATCH_X[0]-.3, HATCH_X[1]+.3, .1, .92, -.35, 1.5, s)
    cut(kit, obj(n), rec, kit.steel)
    frame, st = Parts(), Parts()
    fbox(frame, L, x0-band, x1+band, lo-bl, lo, -.5, -.12, s)
    fbox(frame, U, x0-band, x1+band, hi, hi+bu, -.5, -.12, s)
    for a, b in ((x0-band, x0), (x1, x1+band)):
        fbox(frame, L, a, b, lo, 1.0, -.5, -.12, s)
        fbox(frame, U, a, b, 0.0, hi, -.5, -.12, s)
    frame.build(kit, f'{n} cargo door hazard frame', kit.orange)
    stripes(st, L, x0-band, x1+band, lo-bl, lo, s)
    stripes(st, U, x0-band, x1+band, hi, hi+bu, s)
    st.build(kit, f'{n} cargo door hazard stripes', kit.dark)
    leaves, ribs = Parts(), Parts()
    fbox(leaves, L, x0, x1, lo, .985, -.5, -.28, s)
    fbox(leaves, U, x0, x1, .015, hi, -.5, -.28, s)
    for fi, u0, u1 in ((L, lo, .985), (U, .015, hi)):
        m = (u0+u1)/2
        du = .25/ulen(fi)
        for x in (x0+.3, (x0+x1)/2-.6, (x0+x1)/2+.3, x1-.6):
            fbox(ribs, fi, x, x+.3, u0+.05, u1-.05, -.28, -.12, s)
        fbox(ribs, fi, x0+.3, x1-.3, m-du, m+du, -.28, -.14, s)
    fbox(ribs, U, x0, x1, 0.0, .07, -.3, -.05, s)                 # hinge line along the bulge
    leaves.build(kit, f'{n} cargo door leaves', kit.ochre, .05)
    ribs.build(kit, f'{n} cargo door stiffeners', kit.steel)
    # Personnel hatch in the shoulder: a dogged door with a grab rail.
    hx0, hx1 = HATCH_X
    hp, dogs = Parts(), Parts()
    fbox(hp, S, hx0, hx1, .14, .88, -.35, -.12, s)
    hp.build(kit, f'{n} personnel hatch door', kit.grey, .03)
    for u in (.3, .72):
        fbox(dogs, S, hx1-.45, hx1-.1, u-.03, u+.03, -.12, .05, s)
    fbox(dogs, S, hx1+.45, hx1+.7, .2, .8, 0.0, .35, s)
    dogs.build(kit, f'{n} personnel hatch dogs', kit.steel)


def wall_fittings(kit, side, s):
    n = f'Hull {side} arm'
    cv, bo = Parts(), Parts()
    for x0, x1 in ((56.0, 60.0), (79.0, 84.0)):
        fbox(cv, U, x0, x1, .15, .8, -.05, .25, s)
        du = .4/ulen(U)
        for x in (x0+.4, x1-.4):
            for u in (.15+du, .8-du):
                fbox(bo, U, x-.17, x+.17, u-.04, u+.04, .25, .4, s)
    cv.build(kit, f'{n} wall service covers', kit.ochre, .04)
    bo.build(kit, f'{n} wall cover bolts', kit.steel)
    fbox(Parts(), U, 45.0, 48.4, .15, .75, -.05, .15, s).build(kit, f'{n} wall grille frame', kit.steel)
    lv = Parts()
    for u in (.22, .38, .54):
        fbox(lv, U, 45.3, 48.1, u, u+.08, .15, .25, s)
    lv.build(kit, f'{n} wall grille louvres', kit.dark)
    br = Parts()
    for x in (45.8, 58.0, 83.0, 88.0):                           # cleats under the shoulder cap
        fbox(br, U, x-.4, x+.4, .55, .92, -.05, .7, s)
    br.build(kit, f'{n} strake brackets', kit.steel)
    # The sprite's yellow outboard light at X 43.5, on the shoulder's foot.
    fbox(Parts(), S, 42.8, 44.4, .02, .25, -.05, .35, s).build(kit, f'{n} wall lamp housing', kit.dark)
    fbox(Parts(), S, 43.1, 44.1, .05, .22, .35, .55, s).build(kit, f'{n} wall running lamp', kit.lamp)


def bow_drum(kit, side, s):
    """The bow turret sponson: plated like the arm, with a rust barbette deck plate."""
    n = f'Hull {side} bow drum'
    cx, cy = H.BOW_DRUM_X, H.y(H.BOW_DRUM_REL, s)
    N, circ, top = H.DRUM_N, H.BOW_DRUM_CIRC, H.BOW_DRUM_DECK
    wall0, wall1 = H.BOW_DRUM_KEEL+H.BOW_DRUM_KEEL_C, H.BOW_DRUM_DECK-H.BOW_DRUM_SHOULDER_C
    top_circ = circ-H.BOW_DRUM_SHOULDER_C/math.cos(math.pi/N)

    def corner(k, r):
        a = 2*math.pi*(k+.5)/N
        return cx+r*math.cos(a), cy+r*math.sin(a)
    g = Parts()                                            # a seam groove round the wall
    zm = (wall0+wall1)/2
    for k in range(N):
        (ax, ay), (bx, by) = corner(k, circ-.3), corner(k+1, circ-.3)
        (cx1, cy1), (dx, dy) = corner(k+1, circ+1.0), corner(k, circ+1.0)
        g.slab([(ax, ay, zm-.22), (bx, by, zm-.22), (cx1, cy1, zm-.22), (dx, dy, zm-.22)], (0, 0, .44))
    cut(kit, obj(n), g, kit.dark)
    plate_r = 5.9                                          # the sprite's brown disc
    crown = Parts()
    crown.slab([(*corner(i, plate_r), top-.05) for i in range(N)], (0, 0, .35))
    crown.build(kit, f'{n} crown ring', kit.rust, .05)
    bolts = Parts()
    for i in range(20):
        a = 2*math.pi*(i+.5)/20
        bolts.cyl((cx+(plate_r-.7)*math.cos(a), cy+(plate_r-.7)*math.sin(a), top+.3), .2, .15, 'Z', 6)
    bolts.build(kit, f'{n} crown bolts', kit.steel)
    ribs = Parts()                                         # corner strakes on the exposed corners
    for k in range(N):
        a = 2*math.pi*(k+.5)/N
        if math.cos(a) < -.3 or math.sin(a)*s < -.3:       # skip the inboard / aft corners on the arm
            continue
        (px, py), (qx, qy) = corner(k, circ+.08), corner(k, top_circ+.15)
        ribs.beam((px, py, wall0+.3), (px, py, wall1), .55, .55)
        ribs.beam((px, py, wall1), (qx, qy, top-.1), .5, .5)
    ribs.build(kit, f'{n} casting ribs', kit.ochre, .04)
    band = Parts()                                         # a rust band where the keel chamfer meets the wall
    for k in range(N):
        (ax, ay), (bx, by) = corner(k, circ-.2), corner(k+1, circ-.2)
        (cx1, cy1), (dx, dy) = corner(k+1, circ+.25), corner(k, circ+.25)
        band.slab([(ax, ay, wall0-.1), (bx, by, wall0-.1), (cx1, cy1, wall0-.1), (dx, dy, wall0-.1)], (0, 0, .7))
    band.build(kit, f'{n} foot band', kit.rust)


def mast(kit, side, s):
    """Forward antenna mast on the strip beside the drum, and two stubs on the drum face."""
    xm, yy = 106.5, H.y(12.0, s)
    p, _ = kit.surface(xm, yy)
    z = p.z
    n = f'Mast {side} bow'
    Parts().box(xm-1.4, xm+1.4, yy-1.0, yy+1.0, z-1.0, z+2.6).build(kit, f'{n} pedestal', kit.steel, .1)
    boom = Parts()
    boom.beam((xm, yy, z+2.0), (111.5, yy, z+2.7), .7, .7)
    boom.beam((111.4, yy, z+2.7), (117.6, yy, z+3.4), .38, .38)
    boom.beam((114.0, yy-1.3, z+3.0), (114.0, yy+1.3, z+3.0), .22, .22)       # cross yard
    boom.beam((xm-.4, yy, z+2.4), (xm-.4, yy+.001, z+6.0), .25, .25)          # whip
    boom.build(kit, f'{n} antenna boom', kit.grey)
    Parts().box(117.4, 118.2, yy-.35, yy+.35, z+3.1, z+3.8).build(kit, f'{n} tip lamp', kit.lamp)
    Parts().box(113.85, 114.15, yy-1.45, yy-1.15, z+2.85, z+3.15).box(
        113.85, 114.15, yy+1.15, yy+1.45, z+2.85, z+3.15).build(kit, f'{n} yard lamps', kit.lamp)
    stubs = Parts()
    x0 = H.BOW_DRUM_X+H.sponson_apothem(H.BOW_DRUM_CIRC)-.8       # the sponson's forward flat
    for rel in (17.5, 24.5):
        stubs.cyl((x0, H.y(rel, s), 2.0), .3, 113.6-x0, 'X', 8)
        stubs.box(113.3, 114.1, H.y(rel, s)-.4, H.y(rel, s)+.4, 1.6, 2.4)
    stubs.build(kit, f'{n} stub antennas', kit.steel)


def build(kit):
    for side, s in H.SIDES:
        seams(kit, side, s)
        cargo_door(kit, side, s)
        plates(kit, side, s)
        deck_fittings(kit, side, s)
        wall_fittings(kit, side, s)
        bow_drum(kit, side, s)
        mast(kit, side, s)
