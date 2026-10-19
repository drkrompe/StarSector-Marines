"""Phaeton deck fittings (pass 2): the tanker's deck piping, cargo manifolds,
catwalks and ladders over the tank domes, deck lights and vent masts.

Read off tanker_medium.png (bow right, port up in model XY) and a Moss-type
LNG carrier:
- grey pipe bundles run fore-aft on the centreline in the gaps between the
  three domes (X 16..29 and 59..72) and aft of the aft dome: the pipe trunk.
  On a Moss ship the cargo lines and a catwalk climb each dome to the crown,
  so the trunk here climbs every dome to a small railed crown platform
  carrying the dome hatch and its valve gear;
- grey conduits curve across the deck in both gaps, out to the deck edges
  (near X 18..30 to starboard and port, X 58..70 to the starboard manifold
  and the gallery block): transverse cross headers under the trunk;
- dark pipe runs line both deck edges (port Y +24 aft of the sponson,
  starboard Y -23 the length of the tank deck), stood on saddles, with
  vertical expansion loops and valve wheels;
- the transfer manifolds sit amidships: starboard X 61.5..70 clear of the
  WS 003 sponson, port X 47..54 squeezed between the WS 002 sponson and the
  gallery block, each a railed platform with flanged pipe ends over a drip
  tray at the deck edge;
- a gas vent riser stands beside each dome, taller than the domes.
Everything stays inside the hull's planform, so the top silhouette is not
changed. Turret sponsons, the crane (port bow) and the bridge (starboard bow)
are left clear; nothing tall stands inside a turret's firing arc.
Names: 'Deck ...'.
"""
import math
import bpy
from mathutils import Vector

DECK = 6.0
# Domes as ph_hull.TANKS: (short name, centre x, radius).
DOMES = [('aft', 0.0, 15.0), ('mid', 44.0, 14.5), ('fore', 88.0, 15.5)]
TRUNK_AFT, TRUNK_FORE = -26.0, 112.0
# Cargo lines on the trunk: (name, y, radius). Port of them, the catwalk.
LINES = [('spray line', -3.7, .3), ('liquid line A', -2.9, .55), ('liquid line B', -1.7, .55), ('vapour line', -.4, .78)]
BEAM_TOP = 2.9               # rack crossbeam top above the deck
CAT_Y0, CAT_Y1 = .6, 3.6     # catwalk edges (3 units wide)
CAT_Y = (CAT_Y0+CAT_Y1)/2
RAIL_H = 3.3                 # 1.05 m
PLATFORM_R = 5.0             # crown platform radius
PIPE_OFF = .35               # pipe saddle clearance over a dome
STAIR_OFF = 1.0              # stair stringer clearance over a dome
EDGE_RUNS = {  # side: (y, x0, x1, expansion-loop xs, valve xs)
    'port': (24.0, -26.0, 47.0, (-12.0, 18.0), (-20.0, 6.0, 40.0)),
    'starboard': (-23.0, -26.0, 95.0, (-12.0, 20.0, 76.0), (-20.0, 6.0, 46.0, 88.0)),
}
VENTS = [(-21.0, -8.0), (19.0, -8.0), (68.0, -8.0), (106.0, 7.0)]


# --- Geometry helpers ---------------------------------------------------------

def _fillet(pts, rad, seg=5):
    """Round every interior corner of a polyline with a quadratic curve."""
    pts = [Vector(p) for p in pts]
    out = [pts[0]]
    for i in range(1, len(pts)-1):
        a, b, c = pts[i-1], pts[i], pts[i+1]
        u, v = (b-a), (c-b)
        if u.length < 1e-6 or v.length < 1e-6 or u.normalized().dot(v.normalized()) > .999:
            out.append(b)
            continue
        d = min(rad, u.length*.45, v.length*.45)
        p0, p2 = b-u.normalized()*d, b+v.normalized()*d
        for k in range(seg+1):
            t = k/seg
            out.append(p0*(1-t)**2+b*2*t*(1-t)+p2*t*t)
    out.append(pts[-1])
    return out


def _tube(kit, name, pts, r, mat, n=10, closed=False, fillet=None):
    """Pipe swept along a polyline (parallel-transport frames), capped unless closed."""
    if fillet:
        pts = _fillet(pts, fillet)
    pts = [Vector(p) for p in pts]
    m = len(pts)
    tangents = []
    for i in range(m):
        if closed:
            t = pts[(i+1) % m]-pts[i-1]
        else:
            t = pts[min(i+1, m-1)]-pts[max(i-1, 0)]
        tangents.append(t.normalized())
    ref = Vector((0, 0, 1)) if abs(tangents[0].z) < .9 else Vector((1, 0, 0))
    nrm = (ref-tangents[0]*ref.dot(tangents[0])).normalized()
    verts = []
    for i in range(m):
        t = tangents[i]
        nrm = (nrm-t*nrm.dot(t)).normalized()
        b = t.cross(nrm)
        for k in range(n):
            a = 2*math.pi*k/n
            verts.append(tuple(pts[i]+r*(math.cos(a)*nrm+math.sin(a)*b)))
    faces = []
    rings = m if closed else m-1
    for i in range(rings):
        j = (i+1) % m
        faces += [(i*n+k, i*n+(k+1) % n, j*n+(k+1) % n, j*n+k) for k in range(n)]
    if not closed:
        faces += [tuple(reversed(range(n))), tuple(range((m-1)*n, m*n))]
    return kit.mesh(name, verts, faces, mat)


def _boxes(kit, name, specs, mat):
    """Many boxes as one object: specs [(centre, (dx, dy, dz), yaw radians), ...]."""
    verts, faces = [], []
    for c, (dx, dy, dz), yaw in specs:
        cs, sn = math.cos(yaw), math.sin(yaw)
        base = len(verts)
        for sx, sy, sz in [(-1, -1, -1), (1, -1, -1), (1, 1, -1), (-1, 1, -1),
                           (-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)]:
            x, y = sx*dx/2, sy*dy/2
            verts.append((c[0]+x*cs-y*sn, c[1]+x*sn+y*cs, c[2]+sz*dz/2))
        faces += [(base+a, base+b, base+c_, base+d) for a, b, c_, d in
                  [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]]
    return kit.mesh(name, verts, faces, mat)


def _circle(c, r, n=16, z=None, a0=0.0, a1=2*math.pi, axis='Z'):
    """Points on a circle about c; axis is the circle's normal."""
    out = []
    for i in range(n+1 if a1-a0 < 2*math.pi-1e-6 else n):
        a = a0+(a1-a0)*i/(n if a1-a0 < 2*math.pi-1e-6 else n)
        u, v = r*math.cos(a), r*math.sin(a)
        if axis == 'Z':
            out.append((c[0]+u, c[1]+v, c[2]))
        elif axis == 'Y':
            out.append((c[0]+u, c[1], c[2]+v))
        else:
            out.append((c[0], c[1]+u, c[2]+v))
    return out


def _deck(kit, x, y):
    hit = kit.surface(x, y)
    return hit[0].z if hit else DECK


def _rail(kit, name, feet, mat, spacing=2.6, posts=True):
    """Handrail over a walking line: posts, a mid rail and a top rail."""
    dense = [Vector(feet[0])]
    for q in feet[1:]:
        q = Vector(q)
        k = max(1, int((q-dense[-1]).length/spacing+.999))
        a = dense[-1].copy()
        dense += [a.lerp(q, (i+1)/k) for i in range(k)]
    feet = dense
    _tube(kit, f'{name} top rail', [p+Vector((0, 0, RAIL_H)) for p in feet], .11, mat, 6)
    _tube(kit, f'{name} mid rail', [p+Vector((0, 0, RAIL_H*.5)) for p in feet], .08, mat, 6)
    if posts:
        specs, run = [], 0.0
        for i, p in enumerate(feet):
            if i:
                run += (p-feet[i-1]).length
            if i == 0 or i == len(feet)-1 or run >= spacing:
                specs.append(((p.x, p.y, p.z+RAIL_H/2), (.16, .16, RAIL_H), 0))
                run = 0.0
        _boxes(kit, f'{name} posts', specs, mat)


def _valve(kit, name, loc, mat_body, mat_wheel, axis='Z', size=1.0):
    """A valve body, stem and hand wheel standing up from loc (wheel normal = axis)."""
    x, y, z = loc
    kit.box(f'{name} body', (x, y, z), (1.0*size, 1.0*size, .9*size), mat_body, .1)
    top = z+.45*size+.7*size
    kit.cylinder(f'{name} stem', (x, y, z+.45*size+.35*size), .1*size, .7*size, mat_body, 'Z', 6, 0)
    _tube(kit, f'{name} hand wheel', _circle((x, y, top), .55*size, 12, axis=axis), .07*size, mat_wheel, 6, closed=True)


def _lamp(kit, name, loc, facing=0.0):
    """A deck floodlight: a steel box housing with a lit face (about 0.2 m)."""
    x, y, z = loc
    h = kit.box(f'{name} housing', (x, y, z), (.7, .7, .55), kit.steel, .06)
    h.rotation_euler[2] = facing
    f = kit.box(f'{name} lens', (x+.36*math.cos(facing), y+.36*math.sin(facing), z), (.08, .5, .38), kit.lamp, 0)
    f.rotation_euler[2] = facing


# --- The pipe trunk, its catwalk and the dome crowns --------------------------

def _dome_arc(x0, rr, z0, z1, side, steps):
    """Points on a vertical circle of radius rr about (x0, DECK) from height z0 to z1 on one side."""
    a0 = math.asin(max(-1, min(1, (z0-DECK)/rr)))
    a1 = math.asin(max(-1, min(1, (z1-DECK)/rr)))
    return [(x0+side*rr*math.cos(a0+(a1-a0)*i/steps), DECK+rr*math.sin(a0+(a1-a0)*i/steps)) for i in range(steps+1)]


def _crowns(kit):
    """Per dome: crown platform top height, its railed platform, hatch and valve gear."""
    tops = {}
    for tag, x, r in DOMES:
        p = f'Deck {tag} dome crown'
        zc = _deck(kit, x, 0)        # sphere top, or a crown trunk if the hull adds one
        zc = max(zc, DECK+r)
        top = zc+1.6
        tops[tag] = top
        kit.cylinder(f'{p} pedestal', (x, 0, zc+.2), 2.6, 2.4, kit.steel, 'Z', 16, .1)
        kit.cylinder(f'{p} platform', (x, 0, top-.2), PLATFORM_R, .4, kit.grey, 'Z', 24, .1)
        kit.cylinder(f'{p} platform toe ring', (x, 0, top+.05), PLATFORM_R, .12, kit.dark, 'Z', 24, 0)
        # Rail ring, open where the stairs arrive (at +Y fore and aft) and where the lines come up (-Y).
        for k, (a0, a1) in enumerate([(math.radians(52), math.radians(128)), (math.radians(236), math.radians(304))]):
            _rail(kit, f'{p} rail {k+1}', _circle((x, 0, top), PLATFORM_R-.2, 10, a0=a0, a1=a1), kit.grey)
        # Dome hatch and its bolt ring, the cargo valves on the -Y side.
        kit.cylinder(f'{p} dome hatch', (x, 1.6, top+.35), 1.2, .7, kit.cream, 'Z', 16, .1)
        kit.cylinder(f'{p} dome hatch dogs', (x, 1.6, top+.75), .9, .12, kit.dark, 'Z', 12, 0)
        for i, (name, y, rad) in enumerate(LINES):
            if rad < .4:
                continue
            _valve(kit, f'{p} {name} valve', (x+(-2.0 if i % 2 else 2.0), y, top+.55), kit.steel, kit.cream, 'Z', .9)
        kit.box(f'{p} gauge cabinet', (x, 3.9, top+.7), (1.6, .6, 1.4), kit.steel, .08)
        _lamp(kit, f'{p} lamp port', (x, PLATFORM_R-.2, top+RAIL_H+.35), math.pi/2)
        _lamp(kit, f'{p} lamp starboard', (x, -PLATFORM_R+.2, top+RAIL_H+.35), -math.pi/2)
    return tops


def _stair(kit, name, x0, r, top, side, zcat):
    """A stair climbing the dome from the trunk catwalk to the crown platform."""
    rr_b = math.sqrt((r+STAIR_OFF)**2-CAT_Y**2)
    a_b = math.asin((zcat-DECK)/rr_b)
    pr = math.sqrt(PLATFORM_R**2-CAT_Y**2)
    rr_t = math.hypot(pr, top-DECK)
    a_t = math.atan2(top-DECK, pr)
    steps = 22
    feet = []
    for i in range(steps+1):
        t = i/steps
        a, rr = a_b+(a_t-a_b)*t, rr_b+(rr_t-rr_b)*t
        feet.append((x0+side*rr*math.cos(a), DECK+rr*math.sin(a)))
    treads = [((fx, CAT_Y, fz-.08), (.75, CAT_Y1-CAT_Y0, .16), 0) for fx, fz in feet[1:]]
    _boxes(kit, f'{name} treads', treads, kit.steel)
    for k, yy in enumerate((CAT_Y0+.15, CAT_Y1-.15)):
        _tube(kit, f'{name} stringer {k+1}', [(fx, yy, fz-.35) for fx, fz in feet], .14, kit.grey, 6)
    for k, yy in enumerate((CAT_Y0, CAT_Y1)):
        _rail(kit, f'{name} rail {k+1}', [(fx, yy, fz) for fx, fz in feet], kit.grey)
    return feet[0]


def _trunk(kit, tops):
    zdeck = DECK
    zbeam = zdeck+BEAM_TOP
    zcat = zbeam+.3
    # Flat stretches of the trunk between dome feet (catwalk ends where a stair starts).
    feet = {}
    for tag, x, r in DOMES:
        rr = math.sqrt((r+STAIR_OFF)**2-CAT_Y**2)
        dx = rr*math.cos(math.asin((zcat-DECK)/rr))
        feet[tag] = (x-dx, x+dx)
    spans = [('aft', TRUNK_AFT, feet['aft'][0]), ('gap A', feet['aft'][1], feet['mid'][0]),
             ('gap B', feet['mid'][1], feet['fore'][0]), ('fore', feet['fore'][1], TRUNK_FORE)]

    # Rack frames: two posts and a crossbeam every ~3.6 units along each span.
    posts, beams = [], []
    for _, a, b in spans:
        n = max(2, int((b-a)/3.6)+1)
        for i in range(n):
            fx = a+.5+(b-a-1)*i/(n-1)
            for yy in (-4.2, 3.8):
                z0 = _deck(kit, fx, yy)
                posts.append(((fx, yy, (z0+zbeam)/2), (.35, .35, zbeam-z0), 0))
            beams.append(((fx, -.2, zbeam-.2), (.45, 8.4, .4), 0))
    _boxes(kit, 'Deck trunk rack posts', posts, kit.steel)
    _boxes(kit, 'Deck trunk rack crossbeams', beams, kit.grey)

    # Catwalk gratings and their rails on the flat spans.
    for label, a, b in spans:
        p = f'Deck trunk {label} catwalk'
        kit.box(f'{p} grating', ((a+b)/2, CAT_Y, zcat-.15), (b-a, CAT_Y1-CAT_Y0, .3), kit.dark, .05)
        kit.box(f'{p} edge plate', ((a+b)/2, CAT_Y1, zcat+.1), (b-a, .1, .35), kit.grey, 0)
        for k, yy in enumerate((CAT_Y0, CAT_Y1)):
            _rail(kit, f'{p} rail {k+1}', [(a, yy, zcat), (b, yy, zcat)], kit.grey)

    # Stairs up both flanks of every dome.
    for tag, x, r in DOMES:
        for side, end in ((-1, 'aft'), (1, 'fore')):
            _stair(kit, f'Deck {tag} dome {end} stair', x, r, tops[tag], side, zcat)

    # End ladders down to the deck at both ends of the trunk.
    for label, xe, s in (('aft', TRUNK_AFT, -1), ('fore', TRUNK_FORE, 1)):
        p = f'Deck trunk {label} ladder'
        for k, yy in enumerate((CAT_Y0+.3, CAT_Y1-.3)):
            kit.box(f'{p} stile {k+1}', (xe+s*.4, yy, (zdeck+zcat)/2+.6), (.15, .15, zcat-zdeck+1.2), kit.grey, 0)
        rungs = [((xe+s*.4, CAT_Y, zdeck+.4+i*.75), (.12, CAT_Y1-CAT_Y0-.6, .12), 0)
                 for i in range(int((zcat-zdeck)/.75))]
        _boxes(kit, f'{p} rungs', rungs, kit.grey)

    # Cargo lines: flat on the rack, climbing each dome flank to its crown valves.
    for name, y, rad in LINES:
        zp = zbeam+rad
        pts = []
        # Aft end: the lines come up out of the deck (pump room) at the trunk's aft end.
        pts += [(TRUNK_AFT+.6, y, zdeck-.5), (TRUNK_AFT+.6, y, zp)]
        for tag, x, r in DOMES:
            rr = math.sqrt((r+PIPE_OFF+rad)**2-y**2)
            px = math.sqrt(max(PLATFORM_R**2-y**2, 0))+.6
            zt = DECK+math.sqrt(max(rr**2-px**2, 0))
            top = tops[tag]
            up = _dome_arc(x, rr, zp, zt, -1, 8)
            pts += [(ux, y, uz) for ux, uz in up]
            # Up past the platform edge, in over its deck to the valve, and back out.
            pts += [(x-px, y, top+.55), (x-2.8, y, top+.55)]
            pts += [(x+2.8, y, top+.55), (x+px, y, top+.55)]
            down = _dome_arc(x, rr, zt, zp, 1, 8)
            pts += [(ux, y, uz) for ux, uz in down]
        pts += [(TRUNK_FORE-.6, y, zp), (TRUNK_FORE-.6, y, zdeck-.5)]
        _tube(kit, f'Deck trunk {name}', pts, rad, kit.grey, 10 if rad > .4 else 6, fillet=1.2)
        # Flange collars where the line meets the rack ends.
        for fx in (TRUNK_AFT+3, TRUNK_FORE-3):
            kit.cylinder(f'Deck trunk {name} flange {"aft" if fx < 0 else "fore"}', (fx, y, zp), rad*1.45, .25,
                         kit.steel, 'X', 10, 0)

    # Rack lamps on posts every other span end.
    for label, a, b in spans:
        _lamp(kit, f'Deck trunk {label} lamp', ((a+b)/2, CAT_Y1, zcat+RAIL_H+.35), math.pi/2)
    return zbeam, zcat


# --- Cross headers, deck edge runs, manifolds, vents ---------------------------

def _edge_runs(kit):
    for side, (y, x0, x1, loops, valves) in EDGE_RUNS.items():
        s = 1 if y > 0 else -1
        p = f'Deck {side} edge'
        z0 = _deck(kit, (x0+x1)/2, y)
        for k, (dy, rad, hloop, wloop) in enumerate(((0, .55, 3.2, 3.4), (s*1.25, .38, 2.4, 2.4))):
            yy = y+dy
            zc = z0+.6+rad
            pts = [(x0, yy, z0-.4), (x0, yy, zc)]
            for lx in loops:
                pts += [(lx-wloop/2, yy, zc), (lx-wloop/2, yy, zc+hloop), (lx+wloop/2, yy, zc+hloop), (lx+wloop/2, yy, zc)]
            pts += [(x1, yy, zc)]
            _tube(kit, f'{p} pipe {k+1}', pts, rad, kit.dark if k == 0 else kit.steel, 10, fillet=.9)
        # Saddles every ~5 units.
        saddles = []
        x = x0+2
        while x < x1-1:
            if all(abs(x-lx) > 2.4 for lx in loops):
                saddles.append(((x, y+s*.6, z0+.3), (.5, 2.6, .6), 0))
            x += 5
        _boxes(kit, f'{p} pipe saddles', saddles, kit.steel)
        for i, vx in enumerate(valves):
            _valve(kit, f'{p} valve {i+1}', (vx, y, z0+1.15), kit.steel, kit.cream, 'Z', 1.0)
        for i, lx in enumerate(loops):
            kit.box(f'{p} loop {i+1} stay', (lx, y+s*.6, z0+.6+.55+3.2), (.3, 2.2, .3), kit.steel, 0)


def _cross_headers(kit, zbeam):
    """Transverse conduits under the trunk, curving out to the deck edges in the gaps."""
    zh = DECK+.9
    # Gap A: the sprite's diagonal conduits, starboard edge run to port edge run.
    pts = [(29.0, -23.0, zh), (26.0, -16.0, zh), (22.5, -8.0, zh), (22.5, 8.0, zh), (25.0, 16.0, zh), (21.0, 24.0, zh)]
    _tube(kit, 'Deck gap A cross header', pts, .6, kit.grey, 10, fillet=3.0)
    # Gap B: starboard manifold header to the trunk and on to the gallery block's base.
    pts = [(65.5, -19.0, zh), (64.5, -9.0, zh), (64.5, -3.0, zh), (64.5, 6.0, zh), (60.5, 11.0, zh), (60.5, 22.4, zh)]
    _tube(kit, 'Deck gap B cross header', pts, .6, kit.grey, 10, fillet=3.0)
    # Drops from the vapour and liquid lines into each header.
    for label, x in (('A', 22.5), ('B', 64.5)):
        for name, y, rad in LINES[1:]:
            _tube(kit, f'Deck gap {label} {name} drop', [(x, y, zbeam+rad), (x, y, zh)], rad*.7, kit.grey, 8)
        _valve(kit, f'Deck gap {label} header valve', (x+1.6, 5.8, zh+.2), kit.steel, kit.cream, 'Z', 1.0)


def _manifold(kit, side, x0, x1, y_in, y_out, lines):
    """A transfer manifold: crossover lines ending in flanged connections over a drip tray."""
    s = 1 if y_out > 0 else -1
    p = f'Deck {side} manifold'
    z0 = _deck(kit, (x0+x1)/2, (y_in+y_out)/2)
    zl = z0+2.3
    # Drip tray along the deck edge under the connections.
    ty = y_out-s*2.2
    kit.box(f'{p} drip tray', ((x0+x1)/2, ty, z0+.2), (x1-x0+1, 4.6, .4), kit.steel, .05)
    kit.box(f'{p} drip tray well', ((x0+x1)/2, ty, z0+.38), (x1-x0+.3, 3.9, .1), kit.dark, 0)
    # Header along X on the inboard side, fed by the cross header.
    kit.cylinder(f'{p} header', ((x0+x1)/2, y_in, zl), .7, x1-x0, kit.grey, 'X', 10, .05)
    for i, lx in enumerate(lines):
        n = f'{p} line {i+1}'
        _tube(kit, n, [(lx, y_in, zl), (lx, y_out, zl)], .5, kit.grey, 10)
        kit.cylinder(f'{n} flange', (lx, y_out, zl), .85, .3, kit.steel, 'Y', 12, 0)
        kit.cylinder(f'{n} blank', (lx, y_out+s*.2, zl), .7, .12, kit.dark, 'Y', 12, 0)
        _valve(kit, f'{n} valve', (lx, (y_in+y_out)/2, zl+.4), kit.steel, kit.cream, 'Z', .9)
        # A short stand under each line.
        kit.box(f'{n} stand', (lx, y_out-s*1.4, (z0+zl)/2), (.4, .4, zl-z0), kit.steel, 0)
    # The crew platform on the inboard side, railed on three sides.
    py = y_in-s*1.8
    kit.box(f'{p} platform', ((x0+x1)/2, py, z0+.9), (x1-x0+1, 2.6, .25), kit.dark, .03)
    kit.box(f'{p} platform legs', ((x0+x1)/2, py, z0+.4), (x1-x0, 2.0, .8), kit.steel, .03)
    zf = z0+1.0
    _rail(kit, f'{p} rail', [(x0-.4, y_out, zf), (x0-.4, py-s*1.3, zf), (x1+.4, py-s*1.3, zf), (x1+.4, y_out, zf)],
          kit.grey)
    _lamp(kit, f'{p} lamp', (x1+.4, y_out-s*.6, zf+RAIL_H+.4), s*math.pi/2)


def _vents(kit):
    for i, (x, y) in enumerate(VENTS):
        p = f'Deck vent mast {i+1}'
        z0 = _deck(kit, x, y)
        h = 27.0
        kit.cylinder(f'{p} foot', (x, y, z0+.6), 1.3, 1.2, kit.steel, 'Z', 12, .08)
        kit.cylinder(f'{p} riser', (x, y, z0+h/2), .6, h, kit.grey, 'Z', 10, 0)
        for k, zz in enumerate((z0+9.0, z0+18.0)):
            kit.cylinder(f'{p} band {k+1}', (x, y, zz), .72, .4, kit.steel, 'Z', 10, 0)
        kit.cylinder(f'{p} flame screen', (x, y, z0+h+.6), 1.0, 1.2, kit.steel, 'Z', 12, .08)
        kit.cylinder(f'{p} rain cap', (x, y, z0+h+1.4), 1.4, .3, kit.dark, 'Z', 12, .05)
        _lamp(kit, f'{p} lamp', (x+.9, y, z0+h-1.5), 0.0)
        # A rung ladder up the riser's port face, for servicing the screen.
        rungs = [((x, y+.95, z0+1.6+k*1.2), (.9, .12, .12), 0) for k in range(int((h-3)/1.2))]
        _boxes(kit, f'{p} rungs', rungs, kit.steel)


def build(kit):
    tops = _crowns(kit)
    zbeam, zcat = _trunk(kit, tops)
    _cross_headers(kit, zbeam)
    _edge_runs(kit)
    _vents(kit)
    _manifold(kit, 'starboard', 61.5, 70.0, -19.0, -28.4, (63.0, 65.0, 67.0, 69.0))
    _manifold(kit, 'port', 47.0, 53.4, 19.0, 29.6, (48.2, 50.2, 52.2))
    # Deck edge floodlights between the fittings, facing inboard over the deck.
    for i, (x, y) in enumerate([(-18, 27), (8, 27), (-18, -27), (8, -27), (70, -27), (90, -27)]):
        z0 = _deck(kit, x, y)
        kit.box(f'Deck edge lamp {i+1} stanchion', (x, y, z0+.7), (.25, .25, 1.4), kit.steel, 0)
        _lamp(kit, f'Deck edge lamp {i+1}', (x, y, z0+1.6), -math.pi/2 if y > 0 else math.pi/2)
