"""Tarsus fittings: the port pressure tanks and their blue pipe run, the
starboard cylinder, the four dorsal grapple receivers the Colossus Mk.III
clamps to, and the two flank boarding collars the Kites (P) dock on.

The tanks are the sprite's two big white capsules on the port side of the
central body (X -1..29 at Y 14..30, X -28..4 at Y 22..40) and the grey
cylinder at X -13..-3, Y -30..-40; the blue pipe runs diagonally from the fore
tank's aft end toward the spine. Receivers and collars are not drawn on the
sprite: they are the towing and boarding hardpoints a hazard-duty freighter
carries, placed on clear deck and flank (agreed with the Mk.III session).

Pass 2 (about 0.51 m per unit): each capsule stands in two saddles (chocks
either side on bolted base plates, under strap bands) with a third band
amidships; its free end carries a bolted manway flange and a valve manifold
(standpipe, header, two valves with handwheels) whose feed line drops to the
deck and runs inboard to a gland at the spine. The blue transfer run is a
round pipe entering the fore tank's aft dome through a flanged nozzle, with
flanges, pipe shoes and clamp bands, ending in a valve block at the spine. The
starboard cylinder has dished end caps with bolted cover plates, saddles,
bands and a relief valve. Each grapple receiver is a bolted socket ring on a
doubler plate, with a dark socket well, a striped hazard ring and two lock-pin
bosses. Each Kite collar is a striped hazard frame round a docking ring and a
recessed hatch (hinges, locking dogs, handwheel), grab rails either side and a
lamp above.

The anchors keep their pass-1 positions. New parts are named 'Tank ...',
'Pipe ...', 'Clamp receiver ...' or 'Kite collar ...'."""
import math
from mathutils import Vector
from ts_kit import revolve_x, Parts
import ts_hull as H

TANKS = [  # name, x aft, x fore, y, radius, sink below the deck
    ('Tank port fore', -1.0, 29.0, 22.0, 8.0, 5.0),
    ('Tank port aft', -26.0, 4.0, 31.0, 8.5, 5.0),   # aft end clear of the port aft drum
]
# Per tank: strap band stations (the outer two carry saddles) and which end has the manway.
TANK_FIT = {'Tank port fore': ((6.5, 14.0, 22.0), 'fore'), 'Tank port aft': ((-21.0, -12.5, -4.0), 'aft')}
# Dorsal grapple receivers, (x, rel y, side): two on the arms, two on the aft body's deck
# inboard of the drums and aft of the port tanks.
RECEIVERS = [(70.0, 19.5, 1), (70.0, 19.5, -1), (-30.0, 14.0, 1), (-30.0, 14.0, -1)]
# Flank boarding collars: (side, x, outward sign) on the central body's walls.
COLLARS = [('starboard', 0.0, -1), ('port', -12.0, 1)]
ANCHORS = {}
SPINE_Y = 4.2      # where the deck feed lines reach the spine


def _capsule(x0, x1, r, n=10):
    prof = [(x0, .01)]
    for k in range(1, n):
        a = math.pi/2*k/n
        prof.append((x0+r*.55*(1-math.cos(a)), r*math.sin(a)))
    prof += [(x1-r*.55*(1-math.cos(math.pi/2*k/n)), r*math.sin(math.pi/2*k/n)) for k in range(n-1, 0, -1)]
    prof.append((x1, .01))
    return prof


def _dome_r(r, off):
    """Radius of a _capsule dome at distance off from its apex."""
    if off >= r*.55:
        return r
    c = 1-off/(r*.55)
    return r*math.sqrt(max(0.0, 1-c*c))


def _frame(axis):
    axis = Vector(axis).normalized()
    s = Vector((0, 0, 1)) if abs(axis.z) < .9 else Vector((1, 0, 0))
    u = (s-axis*s.dot(axis)).normalized()
    return axis, u, axis.cross(u)


def rev(parts, c, axis, loop, n=16):
    """Closed (d, r) outline revolved about the line through c along axis."""
    a, u, w = _frame(axis)
    c = Vector(c)
    verts = []
    for d, r in loop:
        verts += [tuple(c+a*d+u*(r*math.cos(2*math.pi*i/n))+w*(r*math.sin(2*math.pi*i/n))) for i in range(n)]
    m = len(loop)
    faces = []
    for k in range(m):
        j = (k+1) % m
        faces += [(k*n+i, k*n+(i+1) % n, j*n+(i+1) % n, j*n+i) for i in range(n)]
    return parts._add(verts, faces)


def pipe(parts, p0, p1, r, n=12):
    p0, p1 = Vector(p0), Vector(p1)
    d = p1-p0
    return rev(parts, p0, d, [(0, .01), (0, r), (d.length, r), (d.length, .01)], n)


def washer(parts, c, axis, r0, r1, w, n=24):
    return rev(parts, c, axis, [(-w/2, r0), (-w/2, r1), (w/2, r1), (w/2, r0)], n)


def bolt_circle(parts, c, axis, r, count, h=.14, size=.13, phase=0.0):
    a, u, w = _frame(axis)
    for k in range(count):
        t = phase+2*math.pi*k/count
        p = Vector(c)+(u*math.cos(t)+w*math.sin(t))*r
        rev(parts, p, a, [(0, .01), (0, size), (h, size), (h, .01)], 6)


def _deck(kit, x, y):
    hit = kit.surface(x, y)
    return hit[0].z if hit else H.BODY_DECK


def _chock(parts, deck, zc, r, top_deg, place, thick):
    """One saddle wing: a wedge from the deck up the vessel's side to top_deg below its axis.

    place(dy, z) maps lateral distance from the axis and height to world (along-axis set by the caller);
    the wing is a slab thick along the vessel's axis."""
    R = r+.03
    zb = deck-.3
    sb = max(-.99, (zb-zc)/R)
    tb, tt = math.asin(sb), -math.radians(top_deg)
    ztop = zc+R*math.sin(tt)
    pts = [(r+1.1, zb), (r+1.1, ztop)]
    for k in range(7):
        t = tt+(tb-tt)*k/6
        pts.append((R*math.cos(t), zc+R*math.sin(t)))
    poly, ext = place(pts)
    parts.slab(poly, ext)


def _manifold(lines, valves, wheels, flanges, x, yc, ztop, y_drop, deck, spine_x=None):
    """Standpipe off a dome top, a header across to y_drop, a drop to the deck and a deck run to the spine."""
    zh = ztop+1.3
    pipe(lines, (x, yc, ztop-.6), (x, yc, zh), .42)
    end = yc+.0+(1.6 if y_drop < yc else -1.6)
    pipe(lines, (x, end, zh), (x, y_drop, zh), .38)
    washer(flanges, (x, end, zh), (0, 1, 0), .2, .6, .25)
    washer(flanges, (x, yc, ztop+.1), (0, 0, 1), .3, .75, .3)
    step = (y_drop-yc)/3
    for k in (1, 2):
        vy = yc+step*k
        valves.box(x-.5, x+.5, vy-.45, vy+.45, zh-.5, zh+.5)
        pipe(valves, (x, vy, zh+.5), (x, vy, zh+1.1), .09, 6)
        washer(wheels, (x, vy, zh+1.15), (0, 0, 1), .38, .58, .12, 12)
    zd = deck+.75
    pipe(lines, (x, y_drop, zh+.38), (x, y_drop, zd), .38)
    washer(flanges, (x, y_drop, (zh+zd)/2), (0, 0, 1), .2, .6, .25)
    xs = x if spine_x is None else spine_x
    if spine_x is not None:
        pipe(lines, (x, y_drop, zd), (xs, y_drop, zd), .38)
        valves.box(xs-.5, xs+.5, y_drop-.5, y_drop+.5, deck-.1, zd+.5)   # elbow block
    else:
        valves.box(x-.5, x+.5, y_drop-.5, y_drop+.5, deck-.1, zd+.5)
    pipe(lines, (xs, y_drop, zd), (xs, SPINE_Y, zd), .38)
    span = y_drop-SPINE_Y
    for k in range(1, int(abs(span)//3.2)+1):
        sy = y_drop-math.copysign(3.2*k, span)
        valves.box(xs-.45, xs+.45, sy-.25, sy+.25, deck-.1, zd)          # pipe brackets
        washer(flanges, (xs, sy, zd), (0, 1, 0), .38, .55, .3, 12)
    valves.box(xs-.9, xs+.9, SPINE_Y-1.0, SPINE_Y+.2, deck-.1, deck+.55)  # deck gland
    washer(flanges, (xs, SPINE_Y+.4, zd), (0, 1, 0), .2, .7, .3)


def build(kit):
    # --- Port pressure tanks: capsules, bands, saddles, manways and manifolds. ---
    bands, saddles, bolts = Parts(), Parts(), Parts()
    man, lines, valves, wheels, flanges = Parts(), Parts(), Parts(), Parts(), Parts()
    for name, x0, x1, yy, r, sink in TANKS:
        zc = H.BODY_DECK+r-sink
        revolve_x(kit, name, _capsule(x0, x1, r), yy, zc, kit.white, n=32)
        stations, end = TANK_FIT[name]
        for k, bx in enumerate(stations):
            washer(bands, (bx, yy, zc), (1, 0, 0), r-.25, r+.25, .8, 40)
            bands.box(bx-.55, bx+.55, yy-.45, yy+.45, zc+r+.05, zc+r+.45)        # band tensioner
            if k == 1:
                continue
            for side in (1, -1):
                deck = _deck(kit, bx, yy+side*(r+1.1))
                _chock(saddles, deck, zc, r, 8,
                       lambda pts, side=side, bx=bx: ([(bx-.8, yy+side*dy, z) for dy, z in pts], (1.6, 0, 0)), 1.6)
                y0, y1 = yy+side*(r-.4), yy+side*(r+1.9)
                saddles.box(bx-1.3, bx+1.3, y0, y1, deck-.15, deck+.25)
                for ox in (-.9, .9):
                    bolts.cyl((bx+ox, yy+side*(r+1.55), deck+.25), .14, .14, 'Z', 6)
        # The free end: bolted manway at the apex, a valve manifold on the dome top.
        sx = 1 if end == 'fore' else -1
        apex = x1 if end == 'fore' else x0
        rev(man, (apex, yy, zc), (sx, 0, 0), [(-.6, .01), (-.6, 1.9), (.15, 1.9), (.15, 1.55),
                                             (.35, 1.55), (.35, .01)], 24)
        bolt_circle(bolts, (apex+sx*.15, yy, zc), (sx, 0, 0), 1.72, 12)
        mx = apex-sx*1.2
        ztop = zc+_dome_r(r, 1.2)
        if end == 'fore':
            _manifold(lines, valves, wheels, flanges, mx, yy, ztop, yy-6.0, _deck(kit, mx, yy-6.0))
        else:
            _manifold(lines, valves, wheels, flanges, mx, yy, ztop, yy-9.0, _deck(kit, mx, yy-9.0), spine_x=-24.0)

    # --- Starboard cylinder: dished heads, cover plates, saddles, bands, relief valve. ---
    cx, cz, cr, ya, yb = -8.0, H.BODY_DECK+2.0, 5.0, -30.5, -41.0
    L = ya-yb
    t = Parts()
    rev(t, (cx, ya, cz), (0, -1, 0), [(0, .01), (0, 2.2), (.15, 3.4), (.45, 4.4), (.9, 4.9), (1.3, cr),
                                      (L-1.3, cr), (L-.9, 4.9), (L-.45, 4.4), (L-.15, 3.4), (L, 2.2), (L, .01)], 28)
    t.build(kit, 'Tank starboard cylinder', kit.grey)
    caps = Parts()
    for yy, d in ((ya, 1), (yb, -1)):
        rev(caps, (cx, yy, cz), (0, d, 0), [(-.2, .01), (-.2, 1.7), (.18, 1.7), (.18, .01)], 20)
        bolt_circle(bolts, (cx, yy+d*.18, cz), (0, d, 0), 1.45, 10, .12, .12)
    for by in (-32.8, -38.6):
        washer(caps, (cx, by, cz), (0, 1, 0), cr-.2, cr+.25, .6, 32)
        for side in (1, -1):
            deck = _deck(kit, cx+side*(cr+1.1), by)
            _chock(saddles, deck, cz, cr, 10,
                   lambda pts, side=side, by=by: ([(cx+side*dx, by-.7, z) for dx, z in pts], (0, 1.4, 0)), 1.4)
            saddles.box(cx+side*(cr-.4), cx+side*(cr+1.9), by-1.1, by+1.1, deck-.15, deck+.25)
            bolts.cyl((cx+side*(cr+1.55), by, deck+.25), .14, .14, 'Z', 6)
    pipe(caps, (cx, -35.7, cz+cr-.3), (cx, -35.7, cz+cr+1.0), .32)
    valves.box(cx-.45, cx+.45, -36.15, -35.25, cz+cr+1.0, cz+cr+1.8)            # relief valve
    pipe(valves, (cx, -35.7, cz+cr+1.8), (cx+1.3, -35.7, cz+cr+1.8), .2, 8)     # vent
    caps.build(kit, 'Tank starboard cylinder caps and bands', kit.steel)

    # --- Blue transfer run: a round pipe from the fore tank's aft dome to the spine. ---
    zp = H.BODY_DECK+1.75
    a, b, c = Vector((1.5, 15.6, zp)), Vector((-1.0, 15.6, zp)), Vector((-15.0, 3.0, zp))
    run = Parts()
    pipe(run, a, b, 1.2, 16)
    pipe(run, b, c, 1.2, 16)
    rev(run, b, (0, 0, 1), [(-1.2, .01), (-1.2, 1.25), (1.2, 1.25), (1.2, .01)], 16)      # elbow
    run.build(kit, 'Pipe blue transfer run', kit.blue, .05)
    pf, shoes = Parts(), Parts()
    washer(pf, (.1, 15.6, zp), (1, 0, 0), .9, 1.65, .45)                                  # tank nozzle flange
    bolt_circle(bolts, (-.15, 15.6, zp), (-1, 0, 0), 1.45, 8, .12, .11)
    d = c-b
    for f in (.33, .72):
        p = b+d*f
        washer(pf, p, d, .9, 1.6, .45)
        bolt_circle(bolts, p+d.normalized()*.22, d, 1.42, 8, .12, .11)
    for f in (.14, .4, .65, .9):
        p = b+d*f
        dz = _deck(kit, p.x, p.y)
        side = Vector((-d.y, d.x, 0)).normalized()
        q = [p+side*1.3+Vector((0, 0, dz-.1-p.z)), p-side*1.3+Vector((0, 0, dz-.1-p.z)),
             p-side*.9+Vector((0, 0, -.5)), p+side*.9+Vector((0, 0, -.5))]
        dn = d.normalized()*.8
        shoes.slab([tuple(v-dn) for v in q], tuple(dn*2))                                    # pipe shoe
    end = c+(c-b).normalized()*1.0
    dz = _deck(kit, end.x, end.y)
    shoes.box(end.x-1.4, end.x+1.4, end.y-1.4, end.y+1.4, dz-.1, zp+1.3)                  # valve block
    pipe(shoes, (end.x, end.y, zp+1.3), (end.x, end.y, zp+2.3), .14, 6)
    washer(wheels, (end.x, end.y, zp+2.35), (0, 0, 1), .7, .95, .15, 16)
    pf.build(kit, 'Pipe blue transfer flanges', kit.steel)
    shoes.build(kit, 'Pipe supports and valve block', kit.grey, .04)
    man.build(kit, 'Tank manway flanges', kit.steel)
    lines.build(kit, 'Pipe tank feed lines', kit.grey)
    valves.build(kit, 'Pipe valves and brackets', kit.steel, .03)
    wheels.build(kit, 'Pipe valve handwheels', kit.orange)
    flanges.build(kit, 'Pipe feed line flanges', kit.steel)
    bands.build(kit, 'Tank strap bands', kit.steel)
    saddles.build(kit, 'Tank saddles', kit.grey, .04)

    # --- Dorsal grapple receivers. The socket ring's top is the anchor plane. ---
    rings, plates, doubler, wells, stripes, pins = Parts(), Parts(), Parts(), Parts(), Parts(), Parts()
    for i, (x, rel, s) in enumerate(RECEIVERS, 1):
        yy = H.y(rel, s)
        p, _ = kit.surface(x, yy)
        z = p.z
        oct_ = [(x+4.6*math.cos(math.pi/8+k*math.pi/4), yy+4.6*math.sin(math.pi/8+k*math.pi/4), z-.12)
                for k in range(8)]
        doubler.slab(oct_, (0, 0, .32))
        for k in range(8):
            t_ = math.pi/8+k*math.pi/4
            bolts.cyl((x+4.05*math.cos(t_), yy+4.05*math.sin(t_), z+.2), .15, .13, 'Z', 6)
        washer(rings, (x, yy, z+.25), (0, 0, 1), 2.75, 3.75, .12, 40)        # hazard ring
        for k in range(10):                                                  # its black stripes
            t0, t1 = 2*math.pi*k/10, 2*math.pi*k/10+math.pi/10
            arc = [(x+rr*math.cos(t0+(t1-t0)*j/3), yy+rr*math.sin(t0+(t1-t0)*j/3), z+.31)
                   for rr, js in ((2.8, range(4)), (3.7, range(3, -1, -1))) for j in js]
            stripes.slab(arc, (0, 0, .03))
        washer(plates, (x, yy, z+.375), (0, 0, 1), 1.55, 2.7, .35, 32)       # socket ring, top at z+.55
        rev(plates, (x, yy, z), (0, 0, 1), [(.2, .01), (.2, 1.6), (.25, 1.6), (.25, .01)], 32)
        rev(wells, (x, yy, z), (0, 0, 1), [(.25, .01), (.25, 1.56), (.32, 1.56), (.32, .01)], 32)
        bolt_circle(bolts, (x, yy, z+.55), (0, 0, 1), 2.12, 12, .06, .13)
        for e in (1, -1):                                                    # lock-pin bosses fore and aft
            bx = x+e*3.0
            plates.box(bx-.6, bx+.6, yy-.6, yy+.6, z+.2, z+.85)
            pins.cyl((bx+e*.6, yy, z+.52), .26, e*.05, 'X', 10)
            pins.cyl((bx-e*.6, yy, z+.52), .3, -e*.05, 'X', 10)
        kit.empty(f'Clamp receiver {i} anchor', (x, yy, z+.55), 90).rotation_euler = (0, -1.5707963, 0)
        ANCHORS[f'Clamp receiver {i}'] = ((x, yy, round(z+.55, 2)), (0.0, 0.0, 1.0))
    rings.build(kit, 'Clamp receiver dorsal hazard rings', kit.orange)
    plates.build(kit, 'Clamp receiver dorsal plates', kit.steel, .04)
    doubler.build(kit, 'Clamp receiver doubler plates', kit.grey, .05)
    wells.build(kit, 'Clamp receiver socket wells', kit.dark)
    stripes.build(kit, 'Clamp receiver hazard stripes', kit.dark)
    pins.build(kit, 'Clamp receiver lock-pin sockets', kit.dark)

    # --- Kite collars: hazard frame, docking ring, recessed hatch, grab rails, lamp. ---
    frame, door, ring, fit, rails, lamps, housing = Parts(), Parts(), Parts(), Parts(), Parts(), Parts(), Parts()
    for side, x, s in COLLARS:
        wall = H.YC+s*42.5
        zc = -1.0
        out = Vector((0, s, 0))

        def W(u, o, v):
            return (x+u, wall+s*o, zc+v)
        sq = [(-4.4, -3.6), (-3.6, -4.4), (3.6, -4.4), (4.4, -3.6), (4.4, 3.6), (3.6, 4.4), (-3.6, 4.4), (-4.4, 3.6)]
        frame.slab([W(u, -.05, v) for u, v in sq], (0, s*.3, 0))
        # Black chevrons round the frame's border band.
        for (u0, v0), (u1, v1), (iu, iv) in (((-3.6, -4.4), (3.6, -4.4), (0, 1)), ((3.6, 4.4), (-3.6, 4.4), (0, -1)),
                                               ((-4.4, 3.6), (-4.4, -3.6), (1, 0)), ((4.4, -3.6), (4.4, 3.6), (-1, 0))):
            n_ = 6
            for k in range(n_):
                f0, f1 = k/n_+.03, k/n_+.5/n_
                pa = (u0+(u1-u0)*f0, v0+(v1-v0)*f0)
                pb = (u0+(u1-u0)*f1, v0+(v1-v0)*f1)
                sk = (.35*(u1-u0)/7.2, .35*(v1-v0)/7.2)
                q = [pa, pb, (pb[0]+iu*.75+sk[0], pb[1]+iv*.75+sk[1]), (pa[0]+iu*.75+sk[0], pa[1]+iv*.75+sk[1])]
                fit.slab([W(u, .25, v) for u, v in q], (0, s*.04, 0))
        washer(ring, W(0, .5, 0), out, 2.7, 3.5, .6, 40)                     # docking ring, face at .8
        bolt_circle(fit, W(0, .25, 0), out, 3.95, 16, .12, .14)
        rev(door, W(0, 0, 0), out, [(.2, .01), (.2, 2.3), (.62, 2.3), (.62, .01)], 32)
        for v in (-1.1, 1.1):                                                # hinge knuckles
            rev(fit, W(-2.48, .45, v), (0, 0, 1), [(-.45, .01), (-.45, .2), (.45, .2), (.45, .01)], 8)
        for k in range(6):                                                   # locking dogs
            t_ = math.pi/6+k*math.pi/3
            cu, cv = 2.45*math.cos(t_), 2.45*math.sin(t_)
            fit.slab([W(cu-.18, .45, cv-.18), W(cu+.18, .45, cv-.18), W(cu+.18, .45, cv+.18), W(cu-.18, .45, cv+.18)],
                     (0, s*.25, 0))
        washer(fit, W(0, .7, 0), out, .6, .8, .1, 16)                         # handwheel
        for k in range(3):
            t_ = k*math.pi/3
            rails.beam(W(-.65*math.cos(t_), .7, -.65*math.sin(t_)), W(.65*math.cos(t_), .7, .65*math.sin(t_)), .1, .1)
        for e in (-1, 1):                                                    # grab rails either side
            u = e*5.1
            for v in (-2.4, 1.9):
                rails.beam(W(u, 0, v), W(u, .7, v), .2, .2)
            rails.beam(W(u, .7, -2.5), W(u, .7, 2.0), .2, .2)
        housing.slab([W(-.7, -.05, 4.9), W(.7, -.05, 4.9), W(.7, -.05, 5.7), W(-.7, -.05, 5.7)], (0, s*.6, 0))
        lamps.slab([W(-.45, .55, 5.05), W(.45, .55, 5.05), W(.45, .55, 5.5), W(-.45, .55, 5.5)], (0, s*.12, 0))
        kit.empty(f'Kite collar {side} anchor', (x, wall+s*.8, -1.0), 90 if s > 0 else -90)
        ANCHORS[f'Kite collar {side}'] = ((x, round(wall+s*.8, 2), -1.0), (0.0, float(s), 0.0))
    frame.build(kit, 'Kite collar flank frames', kit.orange)
    door.build(kit, 'Kite collar flank hatches', kit.steel, .05)
    ring.build(kit, 'Kite collar docking rings', kit.grey, .05)
    fit.build(kit, 'Kite collar hatch fittings', kit.dark)
    rails.build(kit, 'Kite collar grab rails', kit.steel)
    housing.build(kit, 'Kite collar lamp housings', kit.steel, .04)
    lamps.build(kit, 'Kite collar lamps', kit.lamp)
    bolts.build(kit, 'Tank and fitting bolts', kit.dark)
