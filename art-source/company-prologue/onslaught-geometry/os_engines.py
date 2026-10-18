"""The six drive units at onslaught.ship's engine slots.

  Engine 1/4  (-82, +-70), width 20, angled 160/200: an armoured drive pod on
              each stern quarter, lying along its exhaust line out of the
              aft body's stepped outer corner;
  Engine 2/3  (-103, +-40), width 20: set into the aft body's after face,
              firing aft down the notch between the tail and each leg;
  Engine 5/6  (-113, +-11), width 30: the mains, side by side in the tail's
              after face (the sprite's two pale bells at |Y| 4.5..17.5).

Each is a whole drive unit, not a nozzle on a wall: a drive can running
forward into the hull, a flaring bell sunk in a well, a thick mouth lip at
the slot, a dark throat and an armoured shroud where the bell leaves the
hull. Each is built along local +X (exhaust toward local -X) and turned to
its slot angle about the mouth. Names are the standing identifiers:
`Engine <i> aft-facing bell / mouth lip / dark throat`, and the empty
`Engine <i> exhaust anchor` at the mouth with +X along the exhaust.
"""
import math
import bpy
from os_kit import boolean
from os_machinery import Acc, tube, ring

# (x, y, width, contrailSize, length, angle) in onslaught.ship order; index i is position i+1.
ENGINES = [(-82, 70, 20, 64, 64, 160), (-103, 40, 20, 64, 64, 180), (-103, -40, 20, 64, 64, 180),
           (-82, -70, 20, 64, 64, 200), (-113, 11, 30, 64, 100, 180), (-113, -11, 30, 64, 100, 180)]
ENGINE_Z = {20: -8.0, 30: -6.0}
MOUTH_R = {20: 5.6, 30: 5.8}       # read off the sprite: the mains' bells are 13 px across, not 30
BELL_LEN = {20: 9.0, 30: 12.5}
CAN_LEN = {20: 16.0, 30: 22.0}
POD_LEN = 30.0                    # the quarter pods' housing, forward of the mouth


def revolve(profile, n, phase=.5):
    """Closed solid of revolution about local X from a closed (x, r) loop."""
    verts = [(x, r*math.cos(2*math.pi*(k+phase)/n), r*math.sin(2*math.pi*(k+phase)/n)) for x, r in profile
             for k in range(n)]
    m = len(profile)
    faces = [(j*n+k, j*n+(k+1) % n, ((j+1) % m)*n+(k+1) % n, ((j+1) % m)*n+k) for j in range(m) for k in range(n)]
    return verts, faces


def solid(profile, n, phase=.5):
    """Capped solid of revolution about local X from an open (x, r) polyline."""
    verts = [(x, r*math.cos(2*math.pi*(k+phase)/n), r*math.sin(2*math.pi*(k+phase)/n)) for x, r in profile
             for k in range(n)]
    m = len(profile)
    faces = [tuple(reversed(range(n))), tuple(range((m-1)*n, m*n))]
    faces += [(j*n+k, j*n+(k+1) % n, (j+1)*n+(k+1) % n, (j+1)*n+k) for j in range(m-1) for k in range(n)]
    return verts, faces


def place(ob, x, y, z, angle):
    """Put a part built along local +X (exhaust to -X) at the mouth, turned to the slot angle."""
    ob.location = (x, y, z)
    ob.rotation_euler = (0, 0, math.radians(angle-180))
    return ob


def well_cutter(kit, x, y, z, angle, r, depth):
    ob = kit.mesh('Temporary drive well cutter', *solid([(-6, r), (depth, r)], 24), kit.dark)
    place(ob, x, y, z, angle)
    bpy.context.view_layer.update()
    return ob


def _r_at(profile, x):
    """Outer bell radius at local x, from the bell's (x, r) profile (x falling)."""
    for (x0, r0), (x1, r1) in zip(profile, profile[1:]):
        if x1 <= x <= x0:
            return r0+(r1-r0)*(x-x0)/(x1-x0)
    return profile[-1][1]


def detail(kit, i, x, y, z, angle, rm, rt, wall, L, Lc, outer, hosted):
    """Mid-distance detail on one drive unit, built along local +X and placed like the bell.

    Every unit: stiffening hoops and ribs on the bell, a gimbal ring with its
    trunnions where the bell meets the can, flanged bands on the can. Hosted
    drives (2/3 in the aft body's after face, 5/6 in the tail) get a heavy
    armoured collar round the well with bolt bosses; 2/3, whose bell and can lie
    open in the inner machinery well, also get their turbopumps and feed lines.
    The quarter pods (1/4) get armour bands and a louvred intake/heat vent on
    their dorsal-outboard quarter."""
    hoops, gimbal, collar, extra = Acc(), Acc(), Acc(), Acc()
    for f in (.22, .5, .78):
        px = L*f
        r = _r_at(outer, px)
        hoops.add(*ring((px, 0, 0), (1, 0, 0), r-.15, r+.6, .38, n=24))
    for k in range(8):
        a = 2*math.pi*(k+.5)/8
        ca, sa = math.cos(a), math.sin(a)
        pts = [(px, _r_at(outer, px)+.28) for px in (L*.08, L*.35, L*.65, L*.92)]
        for (x0, r0), (x1, r1) in zip(pts, pts[1:]):
            hoops.add(*tube((x0, r0*ca, r0*sa), (x1, r1*ca, r1*sa), .32, n=4, phase=0))
    rg = rt+wall
    gimbal.add(*ring((L+.9, 0, 0), (1, 0, 0), rg+.1, rg+1.5, .65, n=16))
    for side in (1, -1):              # trunnions across the ring
        gimbal.add(*tube((L+.9, side*(rg+1.2), 0), (L+.9, side*(rg+2.6), 0), .9, n=8))
    rc = 1.45*rt+.6
    for px in (L+2.0, L+Lc*.55, L+Lc-.6):
        gimbal.add(*ring((px, 0, 0), (1, 0, 0), rc-.1, rc+.65, .5, n=16))
    if hosted:
        rs = (rm+.9)/math.cos(math.pi/8)
        collar.add(*ring((-.3, 0, 0), (1, 0, 0), rs+1.2, rs+3.4, 1.3, n=8))
        for k in range(8):
            a = 2*math.pi*k/8
            rb = rs+2.3
            collar.add(*tube((-1.6, rb*math.cos(a), rb*math.sin(a)), (-2.3, rb*math.cos(a), rb*math.sin(a)), .55, n=6))
        if i in (2, 3):
            for side in (1, -1):      # turbopumps along the can, lying in the open well
                c0 = (L+3.0, side*(rc+1.7), 1.6)
                extra.add(*tube(c0, (L+11.5, c0[1], c0[2]), 1.6, n=10))
                extra.add(*tube((L+11.5, c0[1], c0[2]), (L+13.0, c0[1], c0[2]), 1.6, .9, n=10))
                extra.add(*ring((L+7.2, c0[1], c0[2]), (1, 0, 0), 1.5, 2.2, .45, n=10))
                extra.add(*tube((L+4.0, c0[1], c0[2]), (L+2.6, side*(rg+.6), .6), .7, n=6))  # discharge to the bell
    else:
        rp = rm+2.6
        rs = (rm+.9)/math.cos(math.pi/8)
        collar.add(*ring((-1.0, 0, 0), (1, 0, 0), rs-.2, rs+2.6, 1.8, n=8))     # proud mouth collar
        for k in range(8):
            a = 2*math.pi*k/8
            rb = rs+1.6
            collar.add(*tube((-2.8, rb*math.cos(a), rb*math.sin(a)), (-3.5, rb*math.cos(a), rb*math.sin(a)), .55, n=6))
        so = 1 if angle < 180 else -1          # local +Y points outboard on Engine 1, inboard on 4
        for px in (8.0, 16.0, 24.0):
            collar.add(*ring((px, 0, 0), (1, 0, 0), rp+.4, rp+2.0, 1.1, n=8))
        # Intake / heat vent on the dorsal-outboard quarter: a raised housing with louvres.
        e = (0, so*math.cos(math.radians(45)), math.sin(math.radians(45)))
        t = (0, -e[2], e[1])
        R = (rp+.8)*math.cos(math.pi/8)-.4

        def block(x0, x1, w0, w1, h0, h1):
            vs = [(px, e[1]*h+t[1]*w, e[2]*h+t[2]*w) for h in (h0, h1) for px, w in
                  ((x0, w0), (x1, w0), (x1, w1), (x0, w1))]
            extra.add(vs, [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)])
        block(9.5, 22.5, -3.0, 3.0, R, R+1.8)
        for k in range(8):
            px = 10.5+k*1.5
            block(px, px+.6, -2.5, 2.5, R+1.8, R+2.6)
    for acc, label, mat in ((hoops, 'bell hoops and ribs', kit.steel), (gimbal, 'gimbal ring and can bands', kit.grey),
                            (collar, 'armoured collar' if hosted else 'pod armour bands', kit.grey),
                            (extra, 'turbopumps' if hosted else 'intake vent', kit.plate)):
        ob = acc.build(kit, f'Engine {i} {label}', mat)
        if ob:
            place(ob, x, y, z, angle)


def build(kit, hull):
    hosts = {2: hull['aft'], 3: hull['aft'], 5: hull['tail'], 6: hull['tail']}
    for i, (x, y, w, _, _, angle) in enumerate(ENGINES, 1):
        z = ENGINE_Z[w]
        rm, L, Lc = MOUTH_R[w], BELL_LEN[w], CAN_LEN[w]
        rt = .45*rm
        wall = .45
        if i in hosts:     # sink the bell in a well in its host's after face
            boolean(hosts[i], well_cutter(kit, x, y, z, angle, rm+.9, L+.5))
        outer = [(L*(1-t), rt+wall+(rm-rt-wall)*(.6*(1-(1-t)**2)+.4*t)) for t in (k/8 for k in range(9))]
        inner = [(px, pr-wall) for px, pr in reversed(outer)]
        place(kit.mesh(f'Engine {i} aft-facing bell', *revolve(outer+inner, 24), kit.steel), x, y, z, angle)
        lip = [(1.0, rm-.2), (1.0, rm+.55), (.4, rm+.9), (0, rm+.6), (0, rm-wall-.1), (.5, rm-wall-.3)]
        place(kit.mesh(f'Engine {i} mouth lip', *revolve(lip, 24), kit.plate), x, y, z, angle)
        place(kit.mesh(f'Engine {i} dark throat', *solid([(L-.3, rt+.3), (L+.1, rt+.3)], 16), kit.dark),
              x, y, z, angle)
        place(kit.mesh(f'Engine {i} drive can', *solid([(L-.5, rt+wall+.2), (L+1.5, 1.45*rt+.6),
                                                        (L+Lc, 1.45*rt+.6)], 16), kit.steel), x, y, z, angle)
        if i in hosts:
            # Armoured octagonal shroud round the well mouth.
            rs = (rm+.9)/math.cos(math.pi/8)
            place(kit.mesh(f'Engine {i} shroud', *revolve([(-.4, rs), (-.4, rs+1.6), (2.0, rs+1.6), (2.8, rs+.6),
                                                           (2.8, rs)], 8, phase=.5), kit.grey), x, y, z, angle)
        else:
            # The quarter pods: a whole armoured nacelle along the exhaust line,
            # its after end a heavy collar round the bell.
            rp = rm+2.6
            pod = [(-.6, rm+.9), (-.6, rp), (3.0, rp+.8), (POD_LEN*.7, rp+.8), (POD_LEN, rp-1.5), (POD_LEN, rm+.9)]
            place(kit.mesh(f'Engine {i} drive pod', *revolve(pod, 8, phase=.5), kit.grey), x, y, z, angle)
            boolean(bpy.data.objects[f'Engine {i} drive pod'],
                    well_cutter(kit, x, y, z, angle, rm+.9, L+.5))
            # The slot sits 1.5 units inside the aft body's stepped corner face:
            # bore the hull too, so the bell and its collar read as a whole mouth.
            boolean(hull['aft'], well_cutter(kit, x, y, z, angle, rm+.9, L+.5))
        detail(kit, i, x, y, z, angle, rm, rt, wall, L, Lc, outer, i in hosts)
        anchor = kit.empty(f'Engine {i} exhaust anchor', (x, y, z), angle)
        anchor['exhaust_direction'] = [round(math.cos(math.radians(angle)), 4), round(math.sin(math.radians(angle)), 4), 0.0]
        anchor['mouth_radius'] = rm
        anchor['slot_width'] = w


def records():
    return [{'index': i, 'objects': f'Engine {i} ...', 'anchor': f'Engine {i} exhaust anchor',
             'location': [x, y, ENGINE_Z[w]], 'width': w, 'length': length, 'contrailSize': c, 'style': 'LOW_TECH',
             'angle': angle, 'exhaust_direction': [round(math.cos(math.radians(angle)), 4),
                                                   round(math.sin(math.radians(angle)), 4), 0.0],
             'mouth_radius': MOUTH_R[w], 'bell': f'Engine {i} aft-facing bell', 'lip': f'Engine {i} mouth lip',
             'throat': f'Engine {i} dark throat', 'drive_glow': 'emit from the anchor along exhaust_direction'}
            for i, (x, y, w, c, length, angle) in enumerate(ENGINES, 1)]
