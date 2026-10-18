"""Scars: dorsal area. Rows and kinds owned by this module (see sw_damage).

The open dorsal decks the other areas leave bare: the two waist decks
between the forebody and the drive pods, the forward deck ahead of the
spine, and the port forebody's outboard deck strip. The deck faced the
drift, so these are the big events seen along the top: plate fields ripped
away by tumbling contact, leaving torn edges curled up and back, the frames
and stringers beneath, and the service runs in the void between them broken.

Only one of them is over occupied space (the forward deck's aft edge sits on
the command deck, sw_scars_spine.PRESSURE_ZONE): the escape crew welded a
pressure plate over it. The rest stayed open through the escape, two of them
with live bus conduit nobody could reach to splice (the sparks), and the
refit isolated those breaks and closed the one it ran new services through.
"""
import math
import random
from mathutils import Vector
from sw_damage import Scar, Geo, Site, TAU, UNCUT, basis
from sw_scars_spine import PRESSURE_ZONE

V = Vector
Z = V((0, 0, 1))


def mats(d):
    """This module's scar materials (made once per build)."""
    if getattr(d, '_dorsal_mats', None):
        return d._dorsal_mats
    m = d.m
    d._dorsal_mats = {
        'frame': m('Scar deck frame primer', (.15, .16, .145), .35, .72),
        'edge': m('Scar torn plate edge', (.3, .29, .27), .5, .75),
        'void': d.dark,
        'floor': d.backing,
        'lagging': m('Scar pipe lagging', (.36, .35, .32), 0, .85),
        'steel': d.steel,
        'conduit': m('Scar bus conduit sheath', (.11, .12, .12), .55, .6),
        'copper_dull': m('Scar oxidised copper', (.26, .15, .08), .7, .7),
        'copper': d.copper,
        'cut': m('Scar cut steel end', (.5, .5, .47), .75, .55),
        'bore': d.dark,
        'cable': d.cable,
        'primer_back': m('Scar plate back primer', (.15, .17, .13), .2, .75),
    }
    return d._dorsal_mats


# ---- local frame ----------------------------------------------------------
class Frame:
    """A flat deck patch: centre on the clean deck, u along the rip, v across,
    n up. P(x, y, h) is a point in it."""
    def __init__(self, d, scar):
        P = scar.params
        cx, cy = P['centre']
        ang = math.radians(P.get('angle', 0))
        site = d.site(scar, (cx+.4, cy+.3, 60), (cx, cy, 0))
        self.ok = site is not None
        if not self.ok:
            return
        self.site = site
        self.c = site.p
        self.n = Z
        self.u = V((math.cos(ang), math.sin(ang), 0))
        self.v = self.n.cross(self.u)
        self.ob = site.ob

    def P(self, x, y, h=0.):
        return self.c+self.u*x+self.v*y+self.n*h


def outline(scar):
    """The torn outline in local (x, y), following the plate grid.

    `shape` is the rectilinear run of plate seams the panels came away along
    (counter-clockwise corners, on the frame/stringer pitch). Seam edges stay
    straight or very slightly wavy; only the edges named in `jags` (the side
    that took the strike) tear through the neighbouring plate, in a few
    unequal jags of unequal spacing. Returns outer, inner (the void edge,
    inset square to each seam), outward normals and the seam edges
    [(a, b, outward normal, jagged)]."""
    P = scar.params
    C = [V((x, y, 0)) for x, y in P['shape']]
    n = len(C)
    area = sum(C[i].x*C[(i+1) % n].y-C[(i+1) % n].x*C[i].y for i in range(n))
    assert area > 0, f'{scar.id}: shape must run counter-clockwise'
    rng = random.Random(scar.id+' outline')
    inset = P.get('inset', .35)
    jags = P.get('jags', {})
    edges = []
    for i in range(n):
        a, b = C[i], C[(i+1) % n]
        t = (b-a).normalized()
        edges.append((a, b, V((t.y, -t.x, 0)), i in jags))
    convex = [(C[i]-C[i-1]).cross(C[(i+1) % n]-C[i]).z > 0 for i in range(n)]
    outer, inner, outs = [], [], []
    for i, (a, b, nrm, jagged) in enumerate(edges):
        L = (b-a).length
        # The void edge stays square to the seam and never runs past the
        # inner corner of a convex turn (that would twist the shelf).
        lo = inset/L if convex[i] else 0.
        hi = 1-inset/L if convex[(i+1) % n] else 1.
        prev = edges[i-1][2]
        m = (prev+nrm).normalized()
        miter = m/max(.5, m.dot(nrm))
        # Station list: even steps along the seam, plus each jag's feet and
        # peak so the tear points are crisp.
        stations = {round(j*.35/L, 4) for j in range(int(L/.35)+1) if j*.35 < L-.05}
        profile = []
        if jagged:
            count = jags[i]
            spots = []
            tries = 0
            while len(spots) < count and tries < 60:
                tries += 1
                c = rng.uniform(.14, .86)
                if all(abs(c-o) > rng.uniform(.14, .3) for o in spots):
                    spots.append(c)
            for c in spots:
                # A ragged bite into the next plate: steep on one side, a
                # long uneven run back to the seam on the other, a shoulder
                # part-way down.
                wl, wr = rng.uniform(.25, .7)/L, rng.uniform(.6, 1.6)/L
                if rng.random() < .5:
                    wl, wr = wr, wl
                amp = rng.uniform(.3, .85)
                sh = rng.uniform(.35, .65)
                profile.append((c, wl, wr, amp, sh, rng.uniform(.45, .8)))
                stations |= {round(max(.01, c-wl), 4), round(c, 4), round(min(.99, c+wr), 4),
                             round(min(.99, c+wr*sh), 4), round(max(.01, c-wl*.5), 4)}
        wave = 0. if jagged else rng.uniform(0, .05)
        phase = rng.uniform(0, TAU)
        for st in sorted(stations):
            p = a.lerp(b, st)
            disp = wave*math.sin(math.pi*st)*math.sin(phase+st*3.1)
            for c, wl, wr, amp, sh, hold in profile:
                if c-wl < st <= c:
                    f = (st-(c-wl))/wl
                    disp += amp*(f*1.3 if f < .5 else .65+.35*(f-.5)/.5)
                elif c < st < c+wr:
                    f = (st-c)/wr
                    disp += amp*(1-(1-hold)*f/sh if f < sh else hold*(1-f)/(1-sh))
            corner = st == 0
            outer.append(p+nrm*disp)
            inner.append(a-miter*inset if corner else a.lerp(b, min(hi, max(lo, st)))-nrm*inset)
            outs.append(miter if corner else nrm)
    return outer, inner, outs, edges


def inside(poly, x, y):
    c = False
    for i in range(len(poly)):
        a, b = poly[i], poly[i-1]
        if (a.y > y) != (b.y > y) and x < (b.x-a.x)*(y-a.y)/(b.y-a.y+1e-12)+a.x:
            c = not c
    return c


def targets(d, F, poly):
    """Base objects the deck rip may cut: whatever a down-cast meets inside
    the outline (the deck skin), never anything on the UNCUT list."""
    xs, ys = [q.x for q in poly], [q.y for q in poly]
    names = set()
    x = min(xs)
    while x <= max(xs):
        y = min(ys)
        while y <= max(ys):
            if inside(poly, x, y):
                q = F.P(x, y)
                hit = d.cast(q+Z*8, q-Z*6)
                if hit and abs((hit[0]-F.c).dot(Z)) < .6 and not any(k in hit[2].name for k in UNCUT):
                    names.add(hit[2].name)
            y += .4
        x += .4
    return sorted(names)


def annulus(F, outer, inner, h0, h1):
    """A closed ring solid between two outlines of the same count."""
    k = len(outer)
    pts = ([F.P(q.x, q.y, h1) for q in outer]+[F.P(q.x, q.y, h1) for q in inner]
           + [F.P(q.x, q.y, h0) for q in outer]+[F.P(q.x, q.y, h0) for q in inner])
    faces = []
    for i in range(k):
        j = (i+1) % k
        faces += [(i, j, k+j, k+i), (2*k+i, 3*k+i, 3*k+j, 2*k+j),
                  (i, 2*k+i, 2*k+j, j), (k+i, k+j, 3*k+j, 3*k+i)]
    geo = Geo()
    geo.add(pts, faces)
    return geo


def solid(d, F, only, x, y, h):
    """True if the point h below the deck at (x, y) is inside the deck's
    solid: a ray up from it first meets the deck top (not a chamfer or a
    step face below it)."""
    q = F.P(x, y, h)
    hit = d.cast(q, q+F.n*4, only)
    return hit is not None and abs((hit[0]-F.c).dot(F.n)) < .15


def fit(d, F, names, outer, inner, depth):
    """Pull each outline point in toward the centre until the void under it
    stays inside the deck solid, so a tear near a deck edge never breaks out
    through the edge's chamfer or step face."""
    only = set(names)
    for k, p in enumerate(outer):
        t = 1.
        while t > .35 and not (solid(d, F, only, p.x*t, p.y*t, -depth-.1)
                               and solid(d, F, only, p.x*t*1.08, p.y*t*1.08, -depth*.5)):
            t -= .05
        outer[k] = p*t
        inner[k] = inner[k]*t


def reach(d, F, names, x0, y0, x1, y1, step=.2, back=.2, h=-.6):
    """How far a member laid from (x0, y0) toward (x1, y1) stays under the
    deck skin: the parameter (0..1) of the last point with skin over it,
    pulled back a little, so frames and runs never stand out past a deck
    edge or a step."""
    L = math.hypot(x1-x0, y1-y0)
    k = max(1, int(L/step))
    only = set(names)
    good = 0.
    for i in range(k+1):
        t = i/k
        if not solid(d, F, only, x0+(x1-x0)*t, y0+(y1-y0)*t, h):
            break
        good = t
    return max(0., good-back/max(L, 1e-6))


def span(d, F, names, x0, y0, x1, y1, cx=None, cy=None, h=-.6):
    """The part of the line (x0, y0)-(x1, y1) under the deck, grown outward
    both ways from (cx, cy) (default the midpoint)."""
    cx = (x0+x1)/2 if cx is None else cx
    cy = (y0+y1)/2 if cy is None else cy
    ta = reach(d, F, names, cx, cy, x0, y0, h=h)
    tb = reach(d, F, names, cx, cy, x1, y1, h=h)
    return ((cx+(x0-cx)*ta, cy+(y0-cy)*ta), (cx+(x1-cx)*tb, cy+(y1-cy)*tb))


def band(geo, F, outer, inner, h0, h1):
    """A flat ring between two outlines, laid as one closed slab per segment
    so a corner can never lose a face."""
    k = len(outer)
    for i in range(k):
        j = (i+1) % k
        ring = [F.P(q.x, q.y, h0) for q in (outer[i], outer[j], inner[j], inner[i])]
        geo.flat(ring, F.n, h1-h0)


def prism(F, poly, h0, h1):
    geo = Geo()
    geo.loft([[F.P(q.x, q.y, h0) for q in poly], [F.P(q.x, q.y, h1) for q in poly]])
    return geo


# ---- pieces -----------------------------------------------------------------
def tube(geo, path, r, sides=8, squash=None, up=Z):
    """A tube along path; squash[i] flattens ring i toward `up` (a crushed
    section)."""
    path = [V(p) for p in path]
    rings = []
    for i, p in enumerate(path):
        t = (path[min(i+1, len(path)-1)]-path[max(i-1, 0)]).normalized()
        a = up-t*up.dot(t)
        a = a.normalized() if a.length > .1 else basis(t)[0]
        b = t.cross(a)
        s = squash[i] if squash else 1.
        rings.append([p+(a*math.cos(TAU*(k+.5)/sides)*s*r+b*math.sin(TAU*(k+.5)/sides)*r/max(s, .4)**.3)
                      for k in range(sides)])
    geo.loft(rings)


def end_cap(d, scar, M, p, t, r, core=None, strands=0, rng=None, part='cut end'):
    """A severed end: a light cut-metal ring and a dark bore, or a conductor
    core standing proud with a few splayed strands."""
    t = t.normalized()
    d.part(scar, part, M['cut'], .004).disc(p+t*.02, t, r*1.04, .05, 8)
    if core is None:
        d.part(scar, 'pipe bore', M['bore'], .002).disc(p+t*.05, t, r*.62, .03, 8)
        return
    d.part(scar, 'conductor core', core, .004).disc(p+t*.07, t, r*.62, .12, 8)
    a, b = basis(t)
    wires = d.part(scar, 'loose strand', core, .002)
    for k in range(strands):
        ang = TAU*(k+rng.uniform(-.2, .2))/max(strands, 1)
        side = a*math.cos(ang)+b*math.sin(ang)
        q0 = p+t*.1+side*r*.35
        q1 = q0+t*rng.uniform(.12, .22)+side*rng.uniform(.08, .16)
        q2 = q1+t*rng.uniform(.05, .12)+side*rng.uniform(.06, .14)-Z*.03
        tube(wires, [q0, q1, q2], .018, 4)


def flap(d, scar, F, A, B, w, mode, length, rng, mat, thick=.07, under=None):
    """A torn plate edge peeled from the hinge A-B, as one bent sheet. w:
    in-plane direction into the void. 'back': curled up and back over the
    deck; 'up': standing proud; 'down': pushed into the void; 'lip': a short
    raised tooth. Each side tears back unevenly toward a ragged tip."""
    n = F.n
    angles = {'back': (24, 55, 88, 118, 142), 'up': (28, 52, 68, 80), 'down': (-20, -38, -52, -62),
              'lip': (24, 36),
              # A whole panel folded back on one crisp crease at its seam.
              'hero': (132, 168),
              # A long strip rolled up and over itself.
              'curl': (34, 78, 118, 150, 170)}[mode]
    fracs = {'hero': (.55, .45)}.get(mode)
    k = len(angles)
    rows = [(A, B)]
    thetas = []
    along = (B-A).normalized()
    for i, ang in enumerate(angles):
        th = math.radians(ang+rng.uniform(-7, 7))
        thetas.append(th)
        dirv = w*math.cos(th)+n*math.sin(th)
        a0, b0 = rows[-1]
        wid = (b0-a0).length
        seg = length*fracs[i] if fracs else length/k*rng.uniform(.85, 1.15)
        skew = along*rng.uniform(-.12, .12)*wid*(.15 if mode in ('hero', 'curl') else 1)
        last = i == k-1
        big = mode in ('hero', 'curl')
        ta = rng.uniform(0, .04) if big and not last else rng.uniform(.06, .2)+(.3 if last and not big else 0)
        tb = rng.uniform(0, .04) if big and not last else rng.uniform(.06, .2)+(.3 if last and not big else 0)
        if big and last:
            ta, tb = rng.uniform(.04, .22), rng.uniform(.02, .12)
        rows.append((a0+along*wid*ta+dirv*seg+skew, b0-along*wid*tb+dirv*seg+skew))
    # The painted face's normal follows the fold, so a panel folded past
    # upright shows its primed back to the sky.
    paint = lambda th: (n*math.cos(th)-w*math.sin(th)).normalized()
    normals = [paint(thetas[0])]+[(paint(thetas[i-1])+paint(thetas[min(i, k-1)])).normalized()
                                  for i in range(1, k)]+[paint(thetas[-1])]
    def sheet(part, m, top, bot, edge):
        verts, faces = [], []
        for (a, b), nn in zip(rows, normals):
            verts += [a+nn*top, b+nn*top, a+nn*bot, b+nn*bot]
        for i in range(k):
            o, p = 4*i, 4*(i+1)
            faces += [(o, o+1, p+1, p), (o+2, p+2, p+3, o+3), (o, p, p+2, o+2), (o+1, o+3, p+3, p+1)]
        faces += [(0, 2, 3, 1), (4*k, 4*k+1, 4*k+3, 4*k+2)]
        d.part(scar, part, m, edge).add(verts, faces)
    sheet('torn flap', mat, thick/2, -thick/2, .015)
    if under is not None:
        sheet('torn flap primed back', under, -thick/2+.004, -thick/2-.014, .004)


def frames(d, scar, F, M, poly, P, strike, names):
    """Transverse frames (deep webs with a flange) and shallow stringers
    under the lost skin, at real spacing; the frame nearest the strike is
    broken and bent down into the void."""
    a, b = P['a']+.8, P['b']+.8
    t = P.get('skin', .14)
    web, fs = P.get('web', .6), P.get('frame_step', 1.6)
    xs = [k*fs for k in range(-int(a/fs), int(a/fs)+1)]
    dent = lambda x, y: -P.get('dent', .45)*max(0., 1-((x-strike.x)**2+(y-strike.y)**2)/P.get('dent_r', 2.6)**2)**1.5
    broke = min(xs, key=lambda q: abs(q-strike.x)) if P.get('broken_frame', True) else None
    fg = d.part(scar, 'deck frame', M['frame'], .015)
    for x in xs:
        top = -t-web/2
        (_, ya), (_, yb) = span(d, F, names, x, -b, x, b, x, strike.y if x == broke else 0, -t-.03)
        if yb-ya < .3:
            continue
        if x != broke:
            # Pushed in round the strike: laid as short spans so it kinks.
            k = max(1, int((yb-ya)/.45))
            ys = [ya+(yb-ya)*i/k for i in range(k+1)]
            for y0, y1 in zip(ys, ys[1:]):
                p0, p1 = F.P(x, y0, top+dent(x, y0)), F.P(x, y1, top+dent(x, y1))
                fg.bar(p0, p1, F.n, .1, web)
                fg.bar(p0-F.n*(web/2-.03), p1-F.n*(web/2-.03), F.n, .26, .06)
            continue
        # Broken: torn through at the strike; both halves folded down into
        # the void, the web twisted where it tore.
        y0 = strike.y
        for s, yr in ((-1, ya), (1, yb)):
            if (y0-yr)*s > -.3:
                continue
            root = F.P(x, yr, top)
            knee = F.P(x+s*.05, yr+(y0-yr)*.45, top+dent(x, yr+(y0-yr)*.45)*.6)
            tip = F.P(x+s*.25, y0-s*.3, top-.95-.25*(s > 0))
            for p0, p1 in ((root, knee), (knee, tip)):
                fg.bar(p0, p1, F.n, .1, web)
                fg.bar(p0-F.n*(web/2-.03), p1-F.n*(web/2-.03), F.n, .26, .06)
            d.part(scar, 'torn frame end', M['cut'], .004).obox(tip, (tip-knee).normalized(), F.u, F.n, .03, .07, web/2)
    sg = d.part(scar, 'deck stringer', M['frame'], .01)
    ss = P.get('stringer_step', 1.6)
    for y in [k*ss for k in range(-int(b/ss), int(b/ss)+1)]:
        (xa, _), (xb, _) = span(d, F, names, -a, y, a, y, 0, y, -t-.03)
        if xb-xa < .3:
            continue
        k = max(1, int((xb-xa)/.45))
        xs2 = [xa+(xb-xa)*i/k for i in range(k+1)]
        for x0, x1 in zip(xs2, xs2[1:]):
            if abs(y-strike.y) < .6 and min(abs(x0-strike.x), abs(x1-strike.x)) < .5:
                continue   # torn through at the strike
            sg.bar(F.P(x0, y, -t-.1+dent(x0, y)), F.P(x1, y, -t-.1+dent(x1, y)), F.n, .07, .2)
    # The void floor: the inner pressure skin / backing, plain and dark.
    depth = P.get('depth', 1.5)


def pipes(d, scar, F, M, P, strike, status, names):
    """Service runs crossing the void along u, with breaks: crushed where the
    strike landed, severed with one end sprung up out of the void."""
    a = P['a']+1.2
    rng = random.Random(scar.id+' pipes')
    # Live only while the escape crew has power on and the break unspliced.
    live = d.state == 'patched'
    ends = []
    for k, run in enumerate(P.get('pipes', ())):
        y, r, kind, h = run['y'], run['r'], run['mat'], -run['h']
        mat = {'lagging': M['lagging'], 'steel': M['steel'], 'conduit': M['conduit'],
               'copper': M['copper_dull']}[kind]
        geo = d.part(scar, f'pipe run {k+1}', mat, .01)
        brk = run.get('break')
        (lo, _), (hi, _) = span(d, F, names, -a, y, a, y, brk or 0, y, h+r+.03)
        spans = [(lo, hi)] if brk is None else [(lo, brk-run.get('gap', .5)/2), (brk+run.get('gap', .5)/2, hi)]
        for j, (x0, x1) in enumerate(spans):
            free = None if brk is None else (x1 if j == 0 else x0)
            sprung = brk is not None and run.get('spring') == ('-' if j == 0 else '+')
            step = .35
            xs = [x0+(x1-x0)*i/max(1, int(abs(x1-x0)/step)) for i in range(int(abs(x1-x0)/step)+1)]
            cr = run.get('crush')
            if cr:
                mid = cr[2] if len(cr) > 2 else (cr[0]+cr[1])/2
                xs = sorted(set(xs) | {q for q in (cr[0], mid, cr[1]) if min(x0, x1) < q < max(x0, x1)})
            pts, sq = [], []
            for x in xs:
                hh, s = h, 1.
                cr = run.get('crush')
                if cr and cr[0] < x < cr[1]:
                    # A V kinked down at the impact point, flattened most there.
                    mid = cr[2] if len(cr) > 2 else (cr[0]+cr[1])/2
                    f = (x-cr[0])/(mid-cr[0]) if x <= mid else (cr[1]-x)/(cr[1]-mid)
                    hh -= .5*f
                    s = 1-.68*f**.7
                if sprung:
                    # The last stretch toward the break springs up out of the void.
                    dist = abs(x-free)
                    if dist < run.get('rise_len', 1.6):
                        f = 1-dist/run.get('rise_len', 1.6)
                        hh += (abs(h)+run.get('rise', .45))*f**1.6
                pts.append(F.P(x, y+(run.get('jog', 0)*(1-min(1, abs(x-free)/1.6)) if sprung else 0), hh))
                sq.append(s)
            tube(geo, pts, r, 8, sq, F.n)
            if free is None:
                continue
            end = pts[-1] if j == 0 else pts[0]
            prev = pts[-2] if j == 0 else pts[1]
            tdir = (end-prev).normalized()
            is_live = run.get('live') and sprung
            if kind in ('conduit', 'copper'):
                core = M['copper'] if (is_live and live) else M['copper_dull']
                end_cap(d, scar, M, end, tdir, r, core, 5, rng)
            else:
                end_cap(d, scar, M, end, tdir, r)
            if is_live:
                label = run['live']
                spray = (tdir*.55+F.n*.85).normalized()
                d.anchor(scar, label, end+tdir*.18, spray, sparking=live,
                         radius_m=run.get('spray_m', 1.2))
                ends.append((end, tdir, r))
    return ends


def boots(d, scar, F, ends):
    """Refit isolation: a rubber boot over each live end, a tag on the run."""
    for p, t, r in ends:
        boot = d.part(scar, 'isolation boot', d.rubber, .01)
        tube(boot, [p-t*.3, p+t*.08, p+t*.26], r*1.4, 8)
        boot.disc(p+t*.28, t, r*1.15, .06, 8)
        side = t.cross(F.n)
        side = side.normalized() if side.length > .1 else F.v
        d.part(scar, 'isolation tag', d.rail, .005).obox(p-t*.5+side*(r*1.4+.03), t, side, t.cross(side), .16, .02, .11)


def cables(d, scar, F, M, P):
    """Loose cable ends hanging into the void from a torn edge."""
    rng = random.Random(scar.id+' cables')
    geo = d.part(scar, 'loose cable', M['cable'], .005)
    for (x, y, dx, dy) in P.get('cables', ()):
        root = F.P(x, y, -.3)
        out = F.P(x+dx*.5, y+dy*.5, -.75)
        end = F.P(x+dx, y+dy, -1.15+rng.uniform(-.1, .1))
        tail = F.P(x-dx*.3, y-dy*.3, -.32)
        tube(geo, [tail, root, out, end], .06, 6)
        t = (end-out).normalized()
        d.part(scar, 'conductor core', M['copper_dull'], .002).disc(end+t*.04, t, .04, .08, 6)


# ---- the rip -----------------------------------------------------------------
def kind_rip(d, scar, status):
    """A dorsal plate field ripped away by tumbling contact.
    params: centre (x, y), angle (deg of u), a, b (half sizes), depth, skin,
            jit, inset, strike (local x, y: where it hit hardest),
            flaps [(perimeter fraction, span in outline points, mode, length)],
            pipes [{y, r, mat, h, break, gap, spring '+'|'-', crush (x0, x1),
                    rise, rise_len, jog, live: anchor label, spray_m}],
            cables [(x, y, dx, dy)], patch 'none'|'pressure', panel (margin).
    open: torn outline (a plate-thickness ledge of torn edge round a deep dark
    void), frames and stringers at real spacing, the void backing, broken
    runs; flaps peeled up and back. Live ends carry spark anchors (sparking
    only while patched). patched: as open, or (patch='pressure') a salvage
    pressure plate welded over the void with strongbacks, the torn surround
    and flaps left showing. refit (status open): the live ends isolated
    under rubber boots. replaced/faded: a proper plate over the rip (primer,
    or repainted to near match), seams showing."""
    P = scar.params
    F = Frame(d, scar)
    if not F.ok:
        return
    outer, inner, outs, edges = outline(scar)
    hist = scar.history(d.state)
    if status in ('replaced', 'faded'):
        xs, ys = [q.x for q in outer], [q.y for q in outer]
        m = P.get('panel', .35)
        c = V(((max(xs)+min(xs))/2, (max(ys)+min(ys))/2, 0))
        site = Site(F.P(c.x, c.y), F.n, F.u, F.v, F.ob)
        d.panel(scar, site, (max(xs)-min(xs))/2+m, (max(ys)-min(ys))/2+m, status, hist)
        return
    M = mats(d)
    names = targets(d, F, outer)
    fit(d, F, names, outer, inner, P.get('depth', 1.5))
    skin = P.get('skin', .14)
    depth = P.get('depth', 1.5)
    # The void (one cut: overlapping cutters leave internal faces), then the
    # torn plate edge as a band round its rim and the shelf the skin was
    # landed on, in bare torn metal.
    over = any(PRESSURE_ZONE[0] < q.x < PRESSURE_ZONE[1] and PRESSURE_ZONE[2] < q.y < PRESSURE_ZONE[3]
               and q.z > PRESSURE_ZONE[4] for q in (F.P(o.x, o.y) for o in outer))
    assert over == (P.get('patch') == 'pressure'), f'{scar.id}: pressure patch must match the pressure zone'
    pressure = status == 'patched' and P.get('patch') == 'pressure'
    if pressure:
        # The plate seats on the frames round the void; the torn ledge
        # beyond its edge stays as it was.
        d.cut(prism(F, inner, -depth, 1.0), names, M['void'])
        shrink = [q-(q-sum(inner, V())/len(inner)).normalized()*.05 for q in inner]
        d.cut(annulus(F, outer, shrink, -skin, 1.0), names, M['edge'])
    else:
        d.cut(prism(F, outer, -depth, 1.0), names, M['void'])
    strike = V(P.get('strike', (0, 0))+(0,))
    pull = lambda poly, k: [q-o*k for q, o in zip(poly, outs)]
    rim = d.part(scar, 'torn edge', M['edge'], .004)
    # The plate's torn thickness: a thin wall just inside the cut, per segment.
    po, pi = pull(outer, .012), pull(outer, .05)
    for i in range(len(po)):
        j = (i+1) % len(po)
        rim.flat([F.P(q.x, q.y, -skin) for q in (po[i], po[j], pi[j], pi[i])], F.n, skin-.004)
    if not pressure:
        band(rim, F, pull(outer, .02), inner, -skin-.07, -skin)
    rng = random.Random(scar.id+' flaps')
    torn = d.torn(d.base_mat(F.ob))
    hinged = {}
    for ei, t0, t1, mode, length in P.get('flaps', ()):
        a, b, nrm, _ = edges[ei]
        if pressure and mode not in ('hero', 'curl'):
            continue   # cut away to seat the plate
        A, B = a.lerp(b, t0), b.lerp(a, 1-t1)
        hinged.setdefault(ei, []).append((t0, t1))
        flap(d, scar, F, F.P(A.x, A.y, -.02), F.P(B.x, B.y, -.02), F.u*-nrm.x+F.v*-nrm.y, mode, length,
             rng, torn, .09 if mode in ('hero', 'curl') else .07, M['primer_back'])
    # Clean seam edges keep the row of holes their fasteners tore out of.
    only = set(names)
    holes = d.part(scar, 'torn fastener holes', M['bore'], .002)
    for ei, (a, b, nrm, jagged) in enumerate(edges):
        if jagged:
            continue
        L = (b-a).length
        for j in range(1, int(L/.42)):
            st = j*.42/L
            if any(t0-.03 < st < t1+.03 for t0, t1 in hinged.get(ei, ())):
                continue
            q = a.lerp(b, st)+nrm*.17
            if solid(d, F, only, q.x, q.y, -.06):
                holes.disc(F.P(q.x, q.y, -.005), F.n, .05, .04, 6)
    if pressure:
        pressure_plate(d, scar, F, M, inner, P)
        return
    frames(d, scar, F, M, inner, P, strike, names)
    ends = pipes(d, scar, F, M, P, strike, status, names)
    if d.state == 'refit':
        boots(d, scar, F, ends)
    cables(d, scar, F, M, P)


def pressure_plate(d, scar, F, M, inner, P):
    """A salvage plate cut to cover the void with a margin, corners clipped,
    seated on the frames at deck level, welded all round, two flat-bar
    strongbacks welded across it."""
    xs, ys = [q.x for q in inner], [q.y for q in inner]
    m = P.get('plate_margin', .3)
    x0, x1, y0, y1 = min(xs)-m, max(xs)+m, min(ys)-m, max(ys)+m
    cl = .6
    plan = [(x0+cl, y0), (x1-cl, y0), (x1, y0+cl), (x1, y1-cl), (x1-cl, y1), (x0+cl, y1), (x0, y1-cl), (x0, y0+cl)]
    ring = [F.P(x, y, -.1) for x, y in plan]
    mat = d.salvage[sum(map(ord, scar.id)) % 3]
    d.part(scar, 'pressure plate', mat, .03).flat(ring, F.n, .12)
    beads = d.part(scar, 'weld bead', d.weld, .01)
    for q0, q1 in zip(ring, ring[1:]+ring[:1]):
        beads.bead(q0+F.n*.12, q1+F.n*.12, F.n, .12)
    backs = d.part(scar, 'strongback', d.salvage[1], .02)
    for f in (.3, .7):
        x = x0+(x1-x0)*f
        backs.bar(F.P(x, y0+.3, .02+.15), F.P(x, y1-.3, .02+.15), F.n, .22, .3)
        beads.bead(F.P(x+.11, y0+.3, .02), F.P(x+.11, y1-.3, .02), F.n, .07)
        beads.bead(F.P(x-.11, y0+.3, .02), F.P(x-.11, y1-.3, .02), F.n, .07)


KINDS = {'rip': kind_rip}

SCARS = [
    Scar('ds-port-waist-rip', 'rip', 'open open open faded',
         'Windward port waist deck: a tumbling rock tore five deck panels away '
         'along their seams, folding one back over the deck and breaking the frame '
         'under the strike; the waist service runs broke in the void. Not '
         'pressure, thrust or navigation: left open through the escape (the bus '
         'conduit arcing where nobody could reach it in vacuum) and the refit '
         '(live ends booted); re-plated and painted when restored.',
         centre=(6.6, 16.7), angle=-6, a=4.8, b=3.2, depth=1.6,
         shape=[(-4.8, -3.2), (4.8, -3.2), (4.8, 0), (1.6, 0), (1.6, 3.2), (-4.8, 3.2)],
         jags={0: 3, 1: 1}, strike=(.4, -1.3),
         flaps=[(4, .08, .52, 'hero', 3.0), (5, .55, .93, 'back', 2.0)],
         pipes=[dict(y=-1.7, r=.2, mat='lagging', h=.85, crush=(-.9, 1.6, .4)),
                dict(y=-.9, r=.14, mat='conduit', h=.7, **{'break': -.2}, gap=.7, spring='-',
                     live='spark', rise=.8, rise_len=1.9, jog=.25, spray_m=1.4),
                dict(y=-.1, r=.12, mat='steel', h=1.0, crush=(-.7, 1.5, .45)),
                dict(y=.7, r=.1, mat='copper', h=.6, **{'break': 1.1}, gap=.6, spring='-',
                     live='spark-2', rise=.7, rise_len=1.6, jog=-.2, spray_m=1.0),
                dict(y=1.5, r=.17, mat='steel', h=.95, **{'break': .4}, gap=.9, spring='+',
                     rise=.2, rise_len=1.2, jog=.3)],
         cables=[(-3.4, 2.0, .9, -.4), (-3.0, -2.1, .7, .5), (1.2, 2.4, -.8, -.4)]),
    Scar('ds-stbd-waist-rip', 'rip', 'open open replaced faded',
         'Starboard waist deck: four panels torn off in the same tumble, one '
         'folded back over the inboard seam, a coolant line crushed and the '
         'starboard bus conduit severed. Left open for the escape (arcing); the '
         'refit ran the new berthing services through this void and closed it '
         'with a proper plate.',
         centre=(5.4, -16.5), angle=8, a=4.8, b=3.2, depth=1.5,
         shape=[(-4.8, -1.6), (-1.6, -1.6), (-1.6, -3.2), (3.2, -3.2), (3.2, 3.2), (-4.8, 3.2)],
         jags={3: 2, 2: 2}, strike=(1.7, -.9),
         flaps=[(4, .38, .82, 'hero', 2.6), (1, .1, .9, 'up', 1.0)],
         pipes=[dict(y=-1.1, r=.18, mat='lagging', h=.9, crush=(.5, 2.9, 1.7)),
                dict(y=-.2, r=.13, mat='conduit', h=.65, **{'break': .3}, gap=.6, spring='+',
                     live='spark', rise=.75, rise_len=1.7, jog=-.25, spray_m=1.2),
                dict(y=.8, r=.11, mat='steel', h=.9, **{'break': -.9}, gap=.5, spring='-',
                     rise=.1, rise_len=1.0, jog=.2)],
         cables=[(2.7, -1.6, -.7, .4), (-2.6, 1.5, .6, -.5)]),
    Scar('ds-fwd-deck-rip', 'rip', 'open patched replaced faded',
         'Forward deck over the command deck\'s forward end: deck armour panels '
         'torn off down to the frames, one folded back along its seam. Over '
         'occupied space, so the escape crew welded a salvage pressure plate '
         'across it, strongbacked, the torn surround and the folded panel left as '
         'they were; a proper plate in the refit.',
         centre=(35.3, -1.0), angle=0, a=3.6, b=2.4, depth=1.3, inset=.45,
         shape=[(-3.6, -2.4), (3.2, -2.4), (3.2, 2.4), (-.8, 2.4), (-.8, .8), (-3.6, .8)],
         jags={0: 3}, strike=(-1.6, -.8), patch='pressure', plate_margin=.2,
         flaps=[(1, .12, .88, 'hero', 2.1), (2, .15, .75, 'back', 1.4)],
         pipes=[dict(y=-1.0, r=.15, mat='steel', h=.8, crush=(-2.8, -.2, -1.5)),
                dict(y=.6, r=.11, mat='conduit', h=.6, **{'break': .8}, gap=.6, spring='+',
                     rise=.15, rise_len=1.0, jog=.15)],
         cables=[(-2.6, .4, .6, -.6)]),
    Scar('ds-port-shoulder-peel', 'rip', 'open open open faded',
         'Port forebody outboard deck: a glancing contact along the deck edge '
         'caught one long deck plate at its forward end and rolled it back up '
         'and over along its outboard seam, the cable tray beneath torn. Outside '
         'the pressure hull: left; re-plated when restored.',
         centre=(22.2, 20.9), angle=0, a=3.6, b=1.1, depth=1.0, inset=.3,
         shape=[(-3.6, -1.1), (3.6, -1.1), (3.6, 1.1), (-3.6, 1.1)],
         jags={1: 2}, strike=(2.4, .1), web=.45, frame_step=1.6, stringer_step=1.1, dent=.3, dent_r=1.6,
         flaps=[(2, .04, .9, 'curl', 2.1)],
         pipes=[dict(y=-.4, r=.09, mat='conduit', h=.55, **{'break': -1.5}, gap=.7, spring='-',
                     rise=.25, rise_len=1.2, jog=.1),
                dict(y=.35, r=.12, mat='steel', h=.75, crush=(1.0, 3.4, 2.4))],
         cables=[(-3.2, .6, .9, -.4), (3.0, -.5, -.8, .3), (1.6, .7, .2, -.6)]),
    Scar('ds-stbd-shoulder-peel', 'rip', 'open open open faded',
         'Starboard forebody outboard deck: the same tumble dragged along the '
         'starboard deck edge and rolled one long deck plate up and outboard on '
         'its seam, a conduit run snapped beneath. Outside the pressure hull: '
         'left; re-plated when restored.',
         centre=(24.0, -21.6), angle=0, a=4.0, b=1.05, depth=1.0, inset=.3,
         shape=[(-4.0, -1.05), (4.0, -1.05), (4.0, 1.05), (-4.0, 1.05)],
         jags={3: 2}, strike=(-2.8, -.2), web=.45, frame_step=1.6, stringer_step=1.05, dent=.3, dent_r=1.6,
         flaps=[(0, .12, .95, 'curl', 2.0)],
         pipes=[dict(y=.35, r=.1, mat='conduit', h=.55, **{'break': .9}, gap=.6, spring='+',
                     rise=.3, rise_len=1.2, jog=-.1),
                dict(y=-.35, r=.13, mat='lagging', h=.75, crush=(-3.8, -1.4, -2.8))],
         cables=[(-3.4, -.6, .8, .4), (3.5, .5, -.9, -.3)]),
]
