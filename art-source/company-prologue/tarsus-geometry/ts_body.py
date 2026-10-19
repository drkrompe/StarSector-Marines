"""Tarsus body: pass 2 detail on the central body, aft body and aft drums
(Hull central body, Hull aft body, Hull port|starboard aft drum).

New parts are named 'Hull central body ...', 'Hull aft body ...' or 'Hull <side> aft drum ...'.

Read off tarsus_base.png at 8x (model X -56..41):
- the central spine (Y -3..10) is a machinery trunk: ladder frames, pipe runs and
  a grey valve block, ending at a dark recessed forward bay (X 31..41, between the
  arms, behind the command module) lit orange along its aft wall;
- the starboard deck is pale cream plating with panel seams and a light grey
  striped walkway band (Y -14..-22);
- yellow lamps stand only on the port deck edge (Y 40.5, X 3.5..-14.5); the
  sprite has none on the starboard edge, so none are modelled there;
- the aft body carries a pale deck panel, a triangular strut frame (apex forward
  at X -22), dark grilles and two round vents, ochre armour plates aft, and
  small red marks on the drums; the drums are heavy ribbed castings.

Nothing changes the planform except two fender strakes, placed only where the
sprite's outline stands proud of the blockout wall. Seams and recesses are
shallow boolean grooves, so the scale solids stay closed. Clear patches are kept
round the Kite collars (X 0 starboard, X -12 port) and the dorsal receivers
(-30, rel +-14), which ts_fittings owns, as are the tanks and the blue pipe."""
import math
from ts_kit import Parts, cut, obj, material
import ts_hull as H

y = H.y
WALL, WALL_FWD = 42.5, 39.0            # central body wall, rel y: aft of X 9 / X 12..33
WALL_Z = (-6.0, 7.0)                   # vertical part of the central body wall
DECK = H.BODY_DECK
AFT_DECK = H.AFT_DECK
COLLARS = {1: -12.0, -1: 0.0}          # Kite collar X by side sign (ts_fittings)
COLLAR_PANEL = {1: (-14.5, -5.5), -1: (-5.5, 6.5)}   # the clear wall panel each collar sits in
# Bolted service covers on the walls (x0, x1, z0, z1) by side sign.
COVERS = {1: [(4.8, 8.2, -.5, 3.2), (-4.6, -1.0, -4.6, -1.4)], -1: [(-19.0, -15.6, -.5, 3.2), (-19.0, -15.6, -4.6, -1.8)]}
PORT_LAMPS_X = (3.0, 0.0, -4.0, -12.0, -14.5)
# Wall openings (x0, x1, z0, z1, kind) by side sign; the wall rel follows from x.
DOORS = {
    1: [(15.0, 27.0, -4.8, 3.2, 'cargo'), (1.0, 3.4, -4.6, -.4, 'hatch'), (-21.0, -18.6, -4.6, -.4, 'hatch')],
    -1: [(15.0, 27.0, -4.8, 3.2, 'cargo'), (-11.2, -8.8, -4.6, -.4, 'hatch')],
}
_MATS = {}


def _mat(name, rgb, metallic=.3, rough=.6, emission=None, strength=2.0):
    if name not in _MATS:
        _MATS[name] = material(name, rgb, metallic, rough, emission, strength)
    return _MATS[name]


def rb(p, x0, x1, r0, r1, z0, z1, s):
    """Box given in rel y on one side."""
    return p.box(x0, x1, y(r0, s), y(r1, s), z0, z1)


def _wall_rel(x):
    return WALL_FWD if x >= 12.0 else WALL


def _split(x0, x1, holes):
    """The run x0..x1 split around the (a, b) holes."""
    runs, a = [], x0
    for h0, h1 in sorted(holes):
        if h1 <= a or h0 >= x1:
            continue
        if h0 > a:
            runs.append((a, h0))
        a = max(a, h1)
    if a < x1:
        runs.append((a, x1))
    return [(u, v) for u, v in runs if v-u > .8]


def _hazard(p, u0, u1, z0, z1, band, face, depth, s):
    """Diagonal stripes over the border band of an opening on a wall at rel `face`, outward +s."""
    pitch, w = 1.6, .55
    put = lambda u, z: (u, y(face, s), z)
    ext = (0, s*depth, 0)
    ou0, ou1, oz0, oz1 = u0-band, u1+band, z0-band, z1+band
    for za, zb in ((oz0, z0), (z1, oz1)):
        u = ou0
        while u+w+band <= ou1:
            p.slab([put(u, za), put(u+w, za), put(u+w+band, zb), put(u+band, zb)], ext)
            u += pitch
    for ua, ub in ((ou0, u0), (u1, ou1)):
        z = z0
        while z+w+band <= z1:
            p.slab([put(ua, z), put(ub, z+band), put(ub, z+band+w), put(ua, z+w)], ext)
            z += pitch


def _annulus(p, cx, cy, r0, r1, z0, z1, n=48):
    """A closed flat ring r0..r1 between z0 and z1."""
    vs = []
    for rr, zz in ((r0, z0), (r1, z0), (r1, z1), (r0, z1)):
        vs += [(cx+rr*math.cos(2*math.pi*i/n), cy+rr*math.sin(2*math.pi*i/n), zz) for i in range(n)]
    fs = []
    for a in range(4):
        b = (a+1) % 4
        fs += [(a*n+i, a*n+(i+1) % n, b*n+(i+1) % n, b*n+i) for i in range(n)]
    return p._add(vs, fs)


def body_walls(kit, body):
    """Strata, plate seams, cargo doors, personnel hatches and fender strakes on the flanks."""
    grooves = Parts()
    for side, s in H.SIDES:
        collar = COLLAR_PANEL[s]
        doors = [(d[0]-1.6, d[1]+1.6) for d in DOORS[s]]
        for (a, b), rel in (((-19.5, 8.5), WALL), ((12.5, 29.5), WALL_FWD)):
            for z, holes in ((-5.35, doors+[collar]), (4.8, [collar])):        # strata lines
                for u, v in _split(a, b, holes):
                    rb(grooves, u, v, rel-.4, rel+1, z-.25, z+.25, s)
        for x in (-14.5, -5.5, 6.5, 14.0, 28.5):                              # vertical plate seams
            if any(d[0]-2 < x < d[1]+2 for d in DOORS[s]) or collar[0] < x < collar[1]:
                continue
            rel = _wall_rel(x)
            rb(grooves, x-.25, x+.25, rel-.4, rel+1, WALL_Z[0]+.4, WALL_Z[1]-.4, s)
    cut(kit, body, grooves, kit.dark)
    recess = Parts()
    for side, s in H.SIDES:
        for x0, x1, z0, z1, kind in DOORS[s]:
            band = 1.0 if kind == 'cargo' else .45
            rb(recess, x0-band, x1+band, _wall_rel(x0)-.5, _wall_rel(x0)+1, z0-band, z1+band, s)
    cut(kit, body, recess, kit.steel)
    frames, stripes, leaves, ribs, hatches, handles = Parts(), Parts(), Parts(), Parts(), Parts(), Parts()
    for side, s in H.SIDES:
        for x0, x1, z0, z1, kind in DOORS[s]:
            w = _wall_rel(x0)
            if kind == 'cargo':
                band = 1.0
                rb(frames, x0-band, x1+band, w-.5, w-.15, z1, z1+band, s)
                rb(frames, x0-band, x1+band, w-.5, w-.15, z0-band, z0, s)
                rb(frames, x0-band, x0, w-.5, w-.15, z0, z1, s)
                rb(frames, x1, x1+band, w-.5, w-.15, z0, z1, s)
                _hazard(stripes, x0, x1, z0, z1, band, w-.15, .08, s)
                mid = (x0+x1)/2
                for a, b in ((x0, mid-.2), (mid+.2, x1)):
                    rb(leaves, a, b, w-.5, w-.25, z0, z1, s)
                    for z in (z0+2.0, z1-2.0):
                        rb(ribs, a+.6, b-.6, w-.25, w-.1, z-.3, z+.3, s)
                    for xx in (a+1.5, b-1.5):
                        rb(ribs, xx-.25, xx+.25, w-.25, w-.1, z0+.6, z1-.6, s)
                rb(ribs, mid-.7, mid+.7, w-.25, w-.05, (z0+z1)/2-.8, (z0+z1)/2+.8, s)   # lock box
            else:
                band = .45
                rb(frames, x0-band, x1+band, w-.5, w-.35, z0-band, z1+band, s)
                rb(hatches, x0, x1, w-.5, w-.2, z0, z1, s)
                rb(ribs, x0+.5, x1-.5, w-.2, w-.1, z1-1.3, z1-.6, s)               # vision slot
                rb(handles, x1-.6, x1-.35, w-.2, w+.1, (z0+z1)/2-.6, (z0+z1)/2+.6, s)
                rb(handles, x0-.6, x1+.6, w-.5, w+.45, z0-1.1, z0-.75, s)          # foot step
    frames.build(kit, 'Hull central body door frames', kit.orange)
    stripes.build(kit, 'Hull central body door hazard stripes', kit.dark)
    leaves.build(kit, 'Hull central body cargo door leaves', kit.ochre, .06)
    hatches.build(kit, 'Hull central body personnel hatches', kit.grey, .05)
    ribs.build(kit, 'Hull central body door stiffeners', kit.steel)
    handles.build(kit, 'Hull central body hatch handles and steps', kit.steel)
    # Fender strakes, only where the sprite's outline stands proud of the wall
    # (port aft of X 9 reaches rel 43; starboard forward of X 12 reaches rel 40).
    strakes = Parts()
    for u, v in _split(-19.5, 8.5, [COLLAR_PANEL[1]]):
        rb(strakes, u, v, WALL-.3, WALL+.5, 5.6, 6.5, 1)
    rb(strakes, 12.5, 29.5, WALL_FWD-.3, WALL_FWD+.6, 5.6, 6.5, -1)
    strakes.build(kit, 'Hull central body fender strakes', kit.steel, .1)
    covers, bolts = Parts(), Parts()
    for side, s in H.SIDES:
        for x0, x1, z0, z1 in COVERS[s]:
            w = _wall_rel(x0)
            rb(covers, x0, x1, w-.2, w+.18, z0, z1, s)
            for xx in (x0+.4, x1-.4):
                for zz in (z0+.4, z1-.4):
                    bolts.cyl((xx, y(w+.18, s), zz), .2, s*.14, 'Y', 6)
    covers.build(kit, 'Hull central body service covers', kit.grey, .06)
    bolts.build(kit, 'Hull central body service cover bolts', kit.steel)


def body_deck(kit, body):
    """Deck seams, the starboard walkway band, hatches, a grille and the port lamp row."""
    g = Parts()
    for x in (25.5, 12.0, -2.0, -16.0):                    # transverse plate seams
        lim = 36.0 if x > 11 else 38.5
        g.box(x-.25, x+.25, y(lim, -1), y(lim, 1), DECK-.4, DECK+1)
    for yy in (-10.5, -24.5, 38.0):                         # longitudinal seams
        g.box(-21.0, 30.0, yy-.25, yy+.25, DECK-.4, DECK+1)
    cut(kit, body, g, kit.dark)
    # The light grey striped walkway band along the starboard deck.
    pale = _mat('Light grey deck band', (.55, .55, .53), .25, .55)
    band, lines = Parts(), Parts()
    band.box(-9.0, 30.0, -21.8, -14.2, DECK, DECK+.22)
    for a, b in ((-14.6, -14.2), (-21.8, -21.4), (-18.2, -17.8)):
        lines.box(-9.0, 30.0, a, b, DECK+.22, DECK+.3)
    for x in range(-8, 30, 3):
        lines.box(x-.12, x+.12, -21.4, -14.6, DECK+.22, DECK+.27)
    band.build(kit, 'Hull central body walkway band', pale, .05)
    lines.build(kit, 'Hull central body walkway band edging', kit.grey)
    # Deck hatches: rim, lid, hinge and dogs.
    rims, lids, dogs = Parts(), Parts(), Parts()
    for x, yy, w in ((22.0, -29.0, 4.2), (5.0, -29.5, 4.2), (-19.0, -21.0, 3.4)):
        h = w/2
        rims.box(x-h-.5, x+h+.5, yy-h-.5, yy+h+.5, DECK, DECK+.45)
        lids.box(x-h, x+h, yy-h, yy+h, DECK+.45, DECK+.75)
        dogs.box(x-h-.5, x+h+.5, yy+h-.1, yy+h+.5, DECK+.45, DECK+.95)          # hinge
        for dx in (-h*.6, h*.6):
            dogs.box(x+dx-.3, x+dx+.3, yy-h-.7, yy-h+.2, DECK+.45, DECK+.85)
    rims.box(-6.5, -.5, -13.2, -11.0, DECK, DECK+.3)          # the dark grille beside the spine
    grille = Parts()
    for x in (-6.0, -5.0, -4.0, -3.0, -2.0):
        grille.box(x, x+.5, -12.9, -11.3, DECK+.3, DECK+.42)
    rims.build(kit, 'Hull central body deck hatch rims', kit.steel, .06)
    lids.build(kit, 'Hull central body deck hatch lids', kit.grey, .06)
    dogs.build(kit, 'Hull central body deck hatch hinges and dogs', kit.steel)
    grille.build(kit, 'Hull central body deck grille', kit.dark)
    # Port deck-edge lamp row: the sprite's yellow pixels at Y 40.5.
    housings, lamps = Parts(), Parts()
    for x in PORT_LAMPS_X:
        p = kit.surface(x, 40.6)[0]
        housings.box(x-.8, x+.8, 39.8, 41.4, p.z-.2, p.z+.35)
        lamps.box(x-.5, x+.5, 40.1, 41.1, p.z+.35, p.z+.7)
    housings.build(kit, 'Hull central body port lamp housings', kit.dark)
    lamps.build(kit, 'Hull central body port deck lamps', kit.lamp)


def spine(kit):
    """The dorsal machinery trunk down the centreline (Y -2.5..9), X -22..29."""
    y0, y1, x0, x1 = -2.5, 9.0, -22.0, 29.0
    blue_gap = (-12.5, -5.5)            # where the blue transfer run crosses the port rail
    bed, rails, rungs = Parts(), Parts(), Parts()
    bed.box(x0, x1, y0, y1, DECK, DECK+.8)
    for a, b in ((y0, y0+.8), (y1-.8, y1)):
        for u, v in (_split(x0, x1, [blue_gap]) if b == y1 else [(x0, x1)]):
            rails.box(u, v, a, b, DECK+.8, DECK+2.6)
    x = x0+1.2
    while x < x1-.6:
        if not (-17.6 < x < -4.0):                       # the manifold and the blue run's crossing
            rungs.box(x-.25, x+.25, y0+.8, y1-.8, DECK+2.0, DECK+2.6)
        x += 2.4
    x = x0+1.2
    while x < x1:                                        # posts carrying the rails
        for a in (y0, y1-.8):
            if not (a > 0 and blue_gap[0]-.5 < x < blue_gap[1]+.5):
                rails.box(x-.4, x+.4, a-.15, a+.95, DECK+.8, DECK+2.8)
        x += 4.8
    bed.build(kit, 'Hull central body spine trunk bed', kit.steel, .06)
    rails.build(kit, 'Hull central body spine trunk rails', kit.grey, .06)
    rungs.build(kit, 'Hull central body spine trunk frames', kit.grey)
    # Pipe runs: the sprite's pale duct forward, three service lines the full length.
    duct, lines, flanges = Parts(), Parts(), Parts()
    duct.cyl((4.0, 3.2, DECK+2.3), 1.35, x1-4.0+.5, 'X', 16)
    for yy, r in ((-1.0, .5), (.4, .45), (7.4, .55)):
        lines.cyl((x0+.5, yy, DECK+1.4), r, x1-x0-.5, 'X', 10)
    for x in (6.0, 13.0, 20.0, 27.0):
        flanges.cyl((x, 3.2, DECK+2.3), 1.65, .5, 'X', 16)
    duct.build(kit, 'Hull central body spine duct', kit.white, .05)
    lines.build(kit, 'Hull central body spine service lines', kit.steel)
    flanges.build(kit, 'Hull central body spine duct flanges', kit.grey)
    # The manifold the blue transfer run drains into.
    Parts().box(-18.0, -13.0, .5, 6.0, DECK+.8, DECK+3.2).build(kit, 'Hull central body spine manifold', kit.grey, .12)
    # The grey valve block (sprite X -4..-12, Y -1..-6) with handwheels.
    Parts().box(-12.0, -4.0, -6.2, -.6, DECK, DECK+3.0).build(kit, 'Hull central body valve block', kit.grey, .15)
    wheels, stems = Parts(), Parts()
    for x, yy in ((-10.0, -4.6), (-6.0, -4.6), (-8.0, -2.0)):
        stems.cyl((x, yy, DECK+3.0), .2, .9, 'Z', 8)
        wheels.tube(x, yy, DECK+3.65, DECK+3.9, .85, .85, .25, 16)
    for yy in (-5.4, -1.4):
        stems.cyl((-12.0, yy, DECK+1.6), .4, -2.2, 'X', 10)        # pipe stubs aft
    stems.build(kit, 'Hull central body valve stems', kit.steel)
    wheels.build(kit, 'Hull central body valve handwheels', _mat('Valve red', (.45, .08, .05), .2, .5))


def forward_bay(kit, body):
    """The dark recessed docking/loading bay between the arms, lit orange along its aft wall."""
    bx0, by0, by1, bz = 31.0, -8.0, 10.0, 6.0
    cut(kit, body, Parts().box(bx0, 42.0, by0, by1, bz, DECK+2), kit.dark)
    glow = _mat('Bay glow orange', (.3, .12, .03), 0, .4, emission=(1.0, .45, .1), strength=8.0)
    floor, strip, cradle, lamps = Parts(), Parts(), Parts(), Parts()
    floor.box(bx0+.2, 40.8, by0+.2, by1-.2, bz, bz+.25)
    strip.box(bx0+.75, bx0+1.0, by0+.6, by1-.6, bz+2.2, bz+2.9)         # light strip on the buffer beam
    for yy in (-7.0, 9.0):
        strip.box(bx0+1.2, 40.0, yy-.12, yy+.12, bz+.25, bz+.45)        # floor guide lights
    for yy in (-6.0, 8.0):                                              # cradle rails either side of the cab
        cradle.box(bx0+1.0, 40.6, yy-.6, yy+.6, bz+.25, bz+1.0)
        for x in (33.5, 36.5, 40.0):
            cradle.box(x-.4, x+.4, yy-1.4, yy+1.4, bz+.25, bz+1.6)
    cradle.box(bx0+.2, bx0+.75, by0+.4, by1-.4, bz+.25, bz+3.2)         # aft wall buffer beam
    for yy in (-7.5, 7.5):                                              # the sprite's yellow lamps at the lip
        lamps.box(29.6, 30.4, yy-.4, yy+.4, DECK, DECK+.6)
    floor.build(kit, 'Hull central body forward bay floor', kit.steel)
    strip.build(kit, 'Hull central body forward bay glow', glow)
    cradle.build(kit, 'Hull central body forward bay cradle', kit.grey, .05)
    lamps.build(kit, 'Hull central body forward bay lip lamps', kit.lamp)


def cplate(p, x0, x1, y0, y1, z0, z1, c):
    """A plate with its plan corners chamfered by c: an octagon slab, not a rectangle."""
    pts = [(x0+c, y0), (x1-c, y0), (x1, y0+c), (x1, y1-c), (x1-c, y1), (x0+c, y1), (x0, y1-c), (x0, y0+c)]
    return p.slab([(a, b, z0) for a, b in pts], (0, 0, z1-z0))


def strut_frame(kit):
    """The sprite's triangular strut frame on the aft deck forward of the castle, apex forward."""
    z = AFT_DECK
    g = Parts()
    g.box(-25.75, -25.25, y(40.5, -1), y(40.5, 1), z-.4, z+1)     # transverse plate seam
    cut(kit, obj('Hull aft body'), g, kit.dark)
    apex, pa, pb = (-21.8, 1.0), (-30.0, -6.5), (-30.0, 8.5)
    zt = z+2.4
    truss, feet = Parts(), Parts()
    for p, q in ((apex, pa), (apex, pb), (pa, pb)):
        truss.beam((p[0], p[1], zt), (q[0], q[1], zt), 1.0, .9)
    for p in (apex, pa, pb):
        truss.box(p[0]-.45, p[0]+.45, p[1]-.45, p[1]+.45, z, zt+.35)
        cplate(feet, p[0]-1.0, p[0]+1.0, p[1]-1.0, p[1]+1.0, z, z+.3, .4)
    mid = ((pa[0]+pb[0])/2, (pa[1]+pb[1])/2)
    truss.beam((apex[0], apex[1], zt), (mid[0], mid[1], zt), .5, .5)            # king strut
    for p in (pa, pb):                                                          # diagonal braces
        truss.beam((p[0], p[1], z+.3), ((apex[0]+p[0])/2, (apex[1]+p[1])/2, zt), .35, .35)
    truss.build(kit, 'Hull aft body strut frame', kit.grey, .05)
    feet.build(kit, 'Hull aft body strut frame feet', kit.steel)


CF, CB, CH, CT = H.CASTLE_FRONT, H.CASTLE_BACK, H.CASTLE_HALF, H.CASTLE_DECK
CW = (AFT_DECK, CT-3.5)                 # castle wall seen above the aft deck (vertical part)


def castle(kit):
    """The raised aft castle: seams, shoulder louvres, deck panel, grilles, vents, rails and a stair."""
    c = obj('Hull aft castle')
    g = Parts()
    zs = 12.0                                                    # strata round the walls
    for s in (1, -1):
        rb(g, CB+.6, CF-4.4, CH-.4, CH+1, zs-.22, zs+.22, s)
        for x in (-44.5, -50.0):                                 # side wall seams
            rb(g, x-.22, x+.22, CH-.4, CH+1, AFT_DECK, CW[1]-.2, s)
    for a, b in ((y(14.4, -1), -1.8), (3.8, y(14.4, 1))):        # front, clear of the stair
        g.box(CF-1, CF+.4, a, b, zs-.22, zs+.22)
    g.box(CB-1, CB+.4, y(19.4, -1), y(19.4, 1), zs-.22, zs+.22)  # aft face
    cut(kit, c, g, kit.dark)
    g = Parts()                                                  # top deck seams
    for x in (-43.8, -51.6):
        g.box(x-.22, x+.22, y(16.0, -1), y(16.0, 1), CT-.35, CT+1)
    cut(kit, c, g, kit.dark)
    # Louvre vents on the side shoulders (the 45 degree chamfer from the wall head to the deck).
    louv, slats = Parts(), Parts()
    for s in (1, -1):
        n = (0, s*.7071, .7071)

        def on(x, t, off):                                       # t: 0 at the wall head, 1 at the deck edge
            rr, zz = CH-3.5*t, CW[1]+3.5*t
            return (x, y(rr, s)+n[1]*off, zz+n[2]*off)
        for x0, x1 in ((-41.5, -44.0), (-45.2, -49.3), (-50.6, -53.6)):
            louv.slab([on(x0, .18, 0), on(x1, .18, 0), on(x1, .82, 0), on(x0, .82, 0)], (0, n[1]*.25, n[2]*.25))
            t = .25
            while t < .78:
                slats.slab([on(x0+.25, t, .25), on(x1-.25, t, .25), on(x1-.25, t+.07, .25), on(x0+.25, t+.07, .25)],
                           (0, n[1]*.12, n[2]*.12))
                t += .13
    louv.build(kit, 'Hull aft castle shoulder louvres', kit.dark)
    slats.build(kit, 'Hull aft castle louvre slats', kit.steel)
    # Pale deck panel (chamfered), panel lines, grilles, vents and the orange service hatch.
    panel, lines = Parts(), Parts()
    cplate(panel, -51.2, -40.0, -12.0, 14.0, CT, CT+.35, 1.5)
    lines.box(-46.0, -45.7, -11.0, 13.0, CT+.35, CT+.4)
    lines.box(-50.5, -40.7, .85, 1.15, CT+.35, CT+.4)
    panel.build(kit, 'Hull aft castle deck panel', kit.cream, .05)
    lines.build(kit, 'Hull aft castle deck panel lines', kit.dark)
    zt = CT+.35
    grilles, slats = Parts(), Parts()
    for a, b in ((-7.0, -1.5), (3.5, 9.0)):
        cplate(grilles, -50.6, -46.6, a, b, zt, zt+.55, .5)
        x = -50.2
        while x < -46.9:
            slats.box(x, x+.3, a+.35, b-.35, zt+.55, zt+.65)
            x += .7
    caps, tops = Parts(), Parts()
    for yy in (10.5, -8.5):
        caps.cyl((-42.6, yy, zt), 2.0, 1.0, 'Z', 8)
        caps.cyl((-42.6, yy, zt+1.0), 1.6, .4, 'Z', 8)
        tops.cyl((-42.6, yy, zt+1.4), 1.3, .1, 'Z', 8)
    grilles.build(kit, 'Hull aft castle grille wells', kit.dark)
    slats.build(kit, 'Hull aft castle grille slats', kit.steel)
    caps.build(kit, 'Hull aft castle vent caps', kit.grey, .06)
    tops.build(kit, 'Hull aft castle vent grilles', kit.dark)
    Parts().box(-51.0, -49.4, -10.8, -8.4, zt, zt+.25).build(kit, 'Hull aft castle orange service hatch', kit.orange, .04)
    # Guard rails round the deck edge (1 m), open at the stair head.
    ti = 3.5
    xf = CF-ti+.3                                        # front deck edge
    re = CH-ti-.3                                        # side deck edge, rel
    xc = CF-4-ti+1.2                                     # where the corner chamfer meets the side
    pts = [(xf, y(1.6, 1)), (xf, y(11.0, 1)), (xc, y(re, 1)), (CB+ti-.3, y(re, 1)),
           (CB+ti-.3, y(re, -1)), (xc, y(re, -1)), (xf, y(11.0, -1)), (xf, H.YC-1.6)]
    rails = Parts()
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        rails.beam((ax, ay, CT+2.0), (bx, by, CT+2.0), .22, .22)
        rails.beam((ax, ay, CT+1.0), (bx, by, CT+1.0), .14, .14)
        L = math.hypot(bx-ax, by-ay)
        k = max(1, int(L/2.6))
        for i in range(k+1):
            px, py = ax+(bx-ax)*i/k, ay+(by-ay)*i/k
            rails.box(px-.13, px+.13, py-.13, py+.13, CT, CT+2.1)
    rails.build(kit, 'Hull aft castle guard rails', kit.steel)
    # Inclined stair up the castle's front face from the aft deck, on the centreline.
    x0, x1 = CF+4.0, CF-2.2                              # foot on the aft deck, head at the deck edge
    w0, w1 = H.YC-1.3, H.YC+1.3
    stair, treads = Parts(), Parts()
    for yy in (w0, w1):
        stair.beam((x0, yy, AFT_DECK), (x1, yy, CT+.2), .25, .55)
        stair.beam((x0, yy, AFT_DECK+2.0), (x1, yy, CT+2.0), .14, .14)                  # handrails
        stair.box(x0-.12, x0+.12, yy-.07, yy+.07, AFT_DECK, AFT_DECK+2.0)
        stair.box(x1-.12, x1+.12, yy-.07, yy+.07, CT, CT+2.0)
    n = 8
    for i in range(1, n):
        t = i/n
        xx, zz = x0+(x1-x0)*t, AFT_DECK+(CT-AFT_DECK)*t
        treads.box(xx-.45, xx+.45, w0+.15, w1-.15, zz-.12, zz+.06)
    landing = Parts()
    cplate(landing, x1-1.6, x1+.4, w0-.2, w1+.2, CT, CT+.2, .3)
    stair.build(kit, 'Hull aft castle stair stringers and rails', kit.grey)
    treads.build(kit, 'Hull aft castle stair treads', kit.steel)
    landing.build(kit, 'Hull aft castle stair landing', kit.steel)


def ventral_keel(kit):
    """The belly hold: bottom seams, keel strakes, wall seams and two ventral cargo hatches."""
    k = obj('Hull ventral keel')
    zb = H.KEEL_BOTTOM
    hatches = [(3.0, 13.0), (-29.0, -19.0)]            # X spans, rel <= 7
    g = Parts()
    for x in (17.0, -1.5, -16.0, -33.0):
        g.box(x-.25, x+.25, y(12.6, -1), y(12.6, 1), zb-1, zb+.4)
    for s in (1, -1):
        for x in (8.0, -8.0, -24.0, -40.0):
            rb(g, x-.25, x+.25, H.KEEL_HALF-.4, H.KEEL_HALF+1, -14.6, -10.6, s)
    cut(kit, k, g, kit.dark)
    band = 1.0
    rec = Parts()
    for a, b in hatches:
        rec.box(a-band, b+band, y(7+band, -1), y(7+band, 1), zb-1, zb+.4)
    cut(kit, k, rec, kit.steel)
    frame, leaves, ribs, stripes = Parts(), Parts(), Parts(), Parts()
    zf = zb+.4
    for a, b in hatches:
        ya, yb = y(7, -1), y(7, 1)
        frame.box(a-band, b+band, ya-band, ya, zf-.25, zf)
        frame.box(a-band, b+band, yb, yb+band, zf-.25, zf)
        frame.box(a-band, a, ya, yb, zf-.25, zf)
        frame.box(b, b+band, ya, yb, zf-.25, zf)
        for yy0, yy1 in ((ya-band, ya), (yb, yb+band)):         # hazard stripes on the long sides
            u = a-band
            while u+1.55 <= b+band:
                stripes.slab([(u, yy0, zf-.31), (u+.55, yy0, zf-.31), (u+1.55, yy1, zf-.31), (u+1.0, yy1, zf-.31)],
                             (0, 0, .07))
                u += 1.6
        for c0, c1 in ((ya, H.YC-.15), (H.YC+.15, yb)):
            leaves.box(a, b, c0, c1, zf-.35, zf)
            for xx in (a+1.5, (a+b)/2, b-1.5):
                ribs.box(xx-.25, xx+.25, c0+.5, c1-.5, zf-.5, zf-.35)
    frame.build(kit, 'Hull ventral keel cargo hatch frames', kit.orange)
    stripes.build(kit, 'Hull ventral keel cargo hatch hazard stripes', kit.dark)
    leaves.build(kit, 'Hull ventral keel cargo hatch leaves', kit.ochre)
    ribs.build(kit, 'Hull ventral keel cargo hatch stiffeners', kit.steel)
    # Keel strakes: bilge bars at the edge of the flat, rubbing strakes on the walls, a centreline bar.
    strakes = Parts()
    for s in (1, -1):
        rb(strakes, -42.0, 22.0, 11.2, 12.4, zb-.5, zb+.05, s)
        rb(strakes, -42.0, 19.0, H.KEEL_HALF-.3, H.KEEL_HALF+.45, -13.2, -12.4, s)
    for u, v in _split(-43.0, 23.0, [(a-band-.5, b+band+.5) for a, b in hatches]):
        strakes.box(u, v, H.YC-.6, H.YC+.6, zb-.7, zb+.05)
    strakes.build(kit, 'Hull ventral keel strakes', kit.steel, .06)


def drums(kit):
    """Each aft drum tower as a heavy ribbed casting fitted to its faceted profile."""
    red = _mat('Hazard red mark', (.5, .07, .04), .2, .55)
    nf = 28
    R = H.DRUM_R
    apo = R*math.cos(math.pi/nf)                       # flat-to-centre distance of the girth
    zg0, zg1 = H.DRUM_KEEL+4.0, H.DRUM_DECK-3.5       # girth: the straight faceted band
    top = H.DRUM_DECK
    rtop = H.DRUM_PROFILE[-1][0]
    for side, s in H.SIDES:
        d = obj(f'Hull {side} aft drum')
        cx, cy = H.DRUM_X, y(H.DRUM_REL, s)
        out = math.pi/2*s
        g = Parts()
        for k in range(nf):                            # a flute down each exposed outboard facet
            a = 2*math.pi*(k+1)/nf
            if math.cos(a-out) < .42:
                continue
            ux, uy = math.cos(a), math.sin(a)
            vx, vy = -uy*.3, ux*.3
            p0 = (cx+ux*(apo-.4), cy+uy*(apo-.4))
            p1 = (cx+ux*(R+1), cy+uy*(R+1))
            g.slab([(p0[0]-vx, p0[1]-vy, zg0+1.5), (p0[0]+vx, p0[1]+vy, zg0+1.5),
                    (p1[0]+vx, p1[1]+vy, zg0+1.5), (p1[0]-vx, p1[1]-vy, zg0+1.5)], (0, 0, zg1-zg0-3.6))
        cut(kit, d, g, kit.dark)
        rings = Parts()
        for zc in (zg1-1.0, zg0+.8):                   # strata at the head and foot of the flutes
            _annulus(rings, cx, cy, apo-.4, R+1, zc-.25, zc+.25)
        cut(kit, d, rings, kit.dark)
        # Crown buttresses: wedge ribs on every other facet of the crown chamfer.
        gus = Parts()
        for k in range(0, nf, 2):
            a = 2*math.pi*(k+1)/nf
            ux, uy = math.cos(a), math.sin(a)
            vx, vy = -uy*.35, ux*.35

            def P(r, z):
                return (cx+ux*r-vx, cy+uy*r-vy, z)
            gus.slab([P(rtop-.3, top-.05), P(apo+.25, zg1-.2), P(apo+.25, top-.4)], (vx*2, vy*2, 0))
        gus.build(kit, f'Hull {side} aft drum crown buttresses', kit.rust)
        lip = Parts()
        _annulus(lip, cx, cy, rtop-1.5, rtop-.05, top, top+.7, nf)
        lip.build(kit, f'Hull {side} aft drum top lip', kit.rust)
        ribs, bolts = Parts(), Parts()
        for k in range(14):
            a = 2*math.pi*(k+.5)/14
            ux, uy = math.cos(a), math.sin(a)
            ribs.beam((cx+ux*5.0, cy+uy*5.0, top+.5), (cx+ux*(rtop-1.4), cy+uy*(rtop-1.4), top+.5), .8, 1.0)
        for k in range(28):
            a = 2*math.pi*(k+.5)/28
            bolts.cyl((cx+math.cos(a)*(rtop-.75), cy+math.sin(a)*(rtop-.75), top+.7), .25, .18, 'Z', 6)
            if k % 2 == 0:
                bolts.cyl((cx+math.cos(a)*4.4, cy+math.sin(a)*4.4, top), .25, .25, 'Z', 6)
        ribs.build(kit, f'Hull {side} aft drum radial ribs', kit.rust, .06)
        bolts.build(kit, f'Hull {side} aft drum bolts', kit.steel)
        ring = Parts()
        _annulus(ring, cx, cy, 8.0, 8.6, top, top+.25, nf)
        ring.build(kit, f'Hull {side} aft drum race ring', kit.steel)
        # The sprite's small red marks on the forward crown (X -31, Y +32..38 / -33..-36).
        marks = Parts()
        for rr in (33.0, 35.5):
            marks.box(-32.2, -30.8, y(rr, s)-.7, y(rr, s)+.7, top, top+.12)
        marks.build(kit, f'Hull {side} aft drum red marks', red)


def build(kit):
    body = obj('Hull central body')
    forward_bay(kit, body)
    body_walls(kit, body)
    body_deck(kit, body)
    spine(kit)
    strut_frame(kit)
    castle(kit)
    ventral_keel(kit)
    drums(kit)
