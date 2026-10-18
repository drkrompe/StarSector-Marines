"""The bore's exposed machinery: the soft inner face of the ring, plated, piped and lit.

Owner (pass 3): the inner ring is the soft, exposed part of the ship, and the
sprite shows different platings and pipings there. Read off paragon.png at 8x,
the lining band is courses of rectangular plate segments in light, mid and dark
greys with dark gaps, concentric pipe runs between the courses, a conduit round
its outer rim and scattered status lights. Built on the lining's rounded lip
(pg_hull.lining_section) and its flat top:

- plate courses (`Bore plate <course> <k>`): curved segments hugging the lip,
  each a few degrees of arc, their grey chosen per segment, with a seeded share
  missing so the machinery beneath shows;
- pipe runs (`Bore pipe <k>`): tori riding the lip between the courses, and the
  rim conduit on the flat top;
- machinery on the flat top (`Bore drum/pump <k>`): capacitor drums and pump
  boxes between the emitter nodes, clear of the command deck's gantry;
- status lights (`Bore light <k>`): small blue-white lamps on some segments.
The hangar is cut after this, so nothing crosses its mouth. All of it is
detail: none of it counts as enclosed volume.
"""
import math
import random
import bpy
import pg_kit as K
import pg_hull

SEED = 2211
# Plate courses on the lip: (name, lip angle from, to (deg; 90 = the top edge, -90 = the underside)).
COURSES = [('upper', 32.0, 70.0), ('middle', -22.0, 24.0), ('lower', -70.0, -32.0)]
SEG_DEG = 6.5            # arc of one plate segment about the bore axis
GAP_DEG = .9
MISSING = .14            # share of segments left out
# Pipe runs: (lip angle, pipe radius) between and either side of the courses.
PIPES = [(28.0, .55), (26.0, .4), (-26.0, .55), (-28.0, .4), (74.0, .45), (-74.0, .45)]
RIM_CONDUIT = (75.0, .9)  # (radius about the bore centre, pipe radius) on the flat top
NODE_CLEAR_DEG = 6.0      # keep top machinery this far from an emitter node's bearing
GANTRY_DEG = (152.0, 208.0)  # bearings the command deck's gantry covers on the top


def lip(t_deg, off):
    """(r, z) on the lining's lip at lip angle t, offset `off` into the bore along its normal."""
    t = math.radians(t_deg)
    rh, rv = pg_hull.LIP_RH, (pg_hull.LINING_TOP+pg_hull.LINING_BOT)/2
    zc = (pg_hull.LINING_TOP-pg_hull.LINING_BOT)/2
    r = pg_hull.BORE_R+rh*(1-math.cos(t))
    z = zc+rv*math.sin(t)
    dr, dz = rh*math.sin(t), rv*math.cos(t)
    L = math.hypot(dr, dz) or 1.0
    return r-dz/L*off, z+dr/L*off


def revolve_arc(kit, name, loop, a0, a1, mat, steps=None, smooth=40):
    """A closed (r, z) section swept about the bore axis from bearing a0 to a1 (deg), capped."""
    cx, cy = pg_hull.BORE_C
    n = len(loop)
    steps = steps or max(2, int(abs(a1-a0)/1.5))
    verts = []
    for k in range(steps+1):
        a = math.radians(a0+(a1-a0)*k/steps)
        verts += [(cx+r*math.cos(a), cy+r*math.sin(a), z) for r, z in loop]
    faces = []
    for k in range(steps):
        p, q = k*n, (k+1)*n
        faces += [(p+i, p+(i+1) % n, q+(i+1) % n, q+i) for i in range(n)]
    faces.append(tuple(reversed(range(n))))
    faces.append(tuple(range(steps*n, steps*n+n)))
    return kit.mesh(name, verts, faces, mat, 0, smooth)


def _segment_loop(t0, t1, off0, off1, steps=6):
    inner = [lip(t0+(t1-t0)*k/steps, off0) for k in range(steps+1)]
    outer = [lip(t0+(t1-t0)*k/steps, off1) for k in reversed(range(steps+1))]
    return inner+outer


def build(kit):
    rng = random.Random(SEED)
    greys = [kit.slate, kit.bore_mid, kit.bore_light]
    made = []
    # Plate courses: segments round the whole bore.
    for cname, t0, t1 in COURSES:
        a = 0.0
        k = 0
        while a < 360.0-1e-6:
            k += 1
            span = SEG_DEG*(.7+.6*rng.random())
            a1 = min(a+span, 360.0)
            if rng.random() < MISSING:
                made += _gap_machinery(kit, rng, f'{cname} {k}', t0, t1, a, a1)
            else:
                thick = .45+.35*rng.random()
                inset_t = 2.0+2.0*rng.random()
                loop = _segment_loop(t0+inset_t*.3, t1-inset_t*.3, .25, .25+thick)
                seg = revolve_arc(kit, f'Bore plate {cname} {k}', loop, a+GAP_DEG/2, a1-GAP_DEG/2,
                                  rng.choice(greys), steps=max(2, int(span/1.5)), smooth=False)
                made.append(seg)
                if rng.random() < .3 and span > 4.5:
                    # A raised sub-panel on the segment, in another grey.
                    tq = (t1-t0)*.22
                    sub = revolve_arc(kit, f'Bore plate {cname} {k} panel',
                                      _segment_loop(t0+tq, t1-tq, .25+thick-.05, .25+thick+.25),
                                      a+span*.22, a1-span*.22, rng.choice(greys), steps=2, smooth=False)
                    made.append(sub)
                if rng.random() < .16:
                    tm = (t0+t1)/2+(t1-t0)*.3*(rng.random()-.5)
                    r, z = lip(tm, .25+thick+.15)
                    am = math.radians((a+a1)/2)
                    cx, cy = pg_hull.BORE_C
                    lamp = kit.box(f'Bore light {cname} {k}', (cx+r*math.cos(am), cy+r*math.sin(am), z),
                                   (.5, .5, .5), kit.glow_white if rng.random() < .5 else kit.glow, 0)
                    made.append(lamp)
            a = a1
    # Conduits dropping over the lip across the courses, clear of the hangar mouth.
    for i, b in enumerate([11.25+22.5*j for j in range(16)], start=1):
        if min(abs(b), 360-b) < 18:
            continue
        pts = [lip(t, 1.6) for t in range(-72, 73, 6)]
        made.append(_tube(kit, f'Bore conduit {i}', b, pts, .5, kit.steel if i % 3 else kit.slate))
    # Pipe runs between the courses.
    for i, (t, pr) in enumerate(PIPES, start=1):
        r, z = lip(t, pr+.3)
        loop = [(r+pr*math.cos(2*math.pi*j/10), z+pr*math.sin(2*math.pi*j/10)) for j in range(10)]
        made.append(pg_hull.revolve_loop(kit, f'Bore pipe {i}', loop, (*pg_hull.BORE_C, 0), kit.steel if i % 2 else
                                         kit.slate, segments=180, smooth=40))
    # The rim conduit on the flat top, against the main ring.
    rr, pr = RIM_CONDUIT
    z = pg_hull.LINING_TOP+pr
    loop = [(rr+pr*math.cos(2*math.pi*j/12), z+pr*math.sin(2*math.pi*j/12)) for j in range(12)]
    made.append(pg_hull.revolve_loop(kit, 'Bore rim conduit', loop, (*pg_hull.BORE_C, 0), kit.steel, segments=180,
                                     smooth=40))
    # Machinery on the flat top between the emitter nodes.
    import pg_shield
    nodes = pg_shield.NODE_ANGLES
    cx, cy = pg_hull.BORE_C
    k = 0
    for b in range(0, 360, 5):
        if any(min(abs(b-n), 360-abs(b-n)) < NODE_CLEAR_DEG for n in nodes):
            continue
        if GANTRY_DEG[0] <= b <= GANTRY_DEG[1] or rng.random() < .35:
            continue
        k += 1
        a = math.radians(b+rng.uniform(-1.2, 1.2))
        r = rng.uniform(pg_hull.LIP_TOP_R+1.2, pg_hull.LINING_R-2.6)
        x, y = cx+r*math.cos(a), cy+r*math.sin(a)
        zt = pg_hull.LINING_TOP
        if rng.random() < .5:
            h = 1.6+1.4*rng.random()
            o = kit.cylinder(f'Bore drum {k}', (x, y, zt+h/2), .8+.5*rng.random(), h, kit.bore_light, vertices=14,
                             bevel=0, smooth=40)
            cap = kit.cylinder(f'Bore drum {k} cap', (x, y, zt+h+.12), .55, .24, kit.glow if rng.random() < .3 else
                               kit.slate, vertices=14, bevel=0, smooth=40)
            made += [o, cap]
        else:
            h = 1.0+1.0*rng.random()
            o = kit.box(f'Bore pump {k}', (x, y, zt+h/2), (2.2+1.2*rng.random(), 1.4+.8*rng.random(), h), kit.bore_mid,
                        .12)
            o.rotation_euler = (0, 0, a)
            made.append(o)
    kit.detail += made
    return made


def _tube(kit, name, bearing, rz, radius, mat, sides=8):
    """A pipe along (r, z) points in the vertical plane at one bearing about the bore axis."""
    cx, cy = pg_hull.BORE_C
    a = math.radians(bearing)
    ux, uy = math.cos(a), math.sin(a)
    verts, faces = [], []
    n = len(rz)
    for i, (r, z) in enumerate(rz):
        r0, z0 = rz[max(i-1, 0)]
        r1, z1 = rz[min(i+1, n-1)]
        tr, tz = r1-r0, z1-z0
        L = math.hypot(tr, tz) or 1.0
        nr, nz = -tz/L, tr/L      # in-plane normal to the path
        for j in range(sides):
            t = 2*math.pi*j/sides
            dr, dz, dt = nr*math.cos(t)*radius, nz*math.cos(t)*radius, math.sin(t)*radius
            rr = r+dr
            verts.append((cx+rr*ux-uy*dt, cy+rr*uy+ux*dt, z+dz))
    for i in range(n-1):
        p, q = i*sides, (i+1)*sides
        faces += [(p+j, p+(j+1) % sides, q+(j+1) % sides, q+j) for j in range(sides)]
    faces.append(tuple(reversed(range(sides))))
    faces.append(tuple(range((n-1)*sides, n*sides)))
    return kit.mesh(name, verts, faces, mat, 0, 40)


def _gap_machinery(kit, rng, tag, t0, t1, a0, a1):
    """Exposed machinery where a plate segment is missing: a pump lying along the bore, a junction box."""
    cx, cy = pg_hull.BORE_C
    am = math.radians((a0+a1)/2)
    out = []
    tm = (t0+t1)/2
    r, z = lip(tm, 1.0)
    span = math.radians(a1-a0)*r*.7
    pump = kit.cylinder(f'Bore gap {tag} pump', (cx+r*math.cos(am), cy+r*math.sin(am), z), .8, span, kit.bore_mid,
                        axis='X', vertices=12, bevel=0, smooth=40)
    pump.rotation_euler = (math.pi/2, 0, am)
    out.append(pump)
    r2, z2 = lip(tm+(t1-t0)*.3, .7)
    box = kit.box(f'Bore gap {tag} box', (cx+r2*math.cos(am), cy+r2*math.sin(am), z2), (1.2, 1.6, 1.0), kit.slate, .1)
    box.rotation_euler = (0, 0, am)
    out.append(box)
    if rng.random() < .5:
        r3, z3 = lip(tm-(t1-t0)*.3, .9)
        out.append(kit.box(f'Bore gap {tag} light', (cx+r3*math.cos(am), cy+r3*math.sin(am), z3), (.45, .45, .45),
                           kit.glow, 0))
    return out
