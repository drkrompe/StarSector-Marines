"""The six drive units at onslaught.ship's engine slots.

  Engine 1/4  (-82, +-70), width 20, angled 160/200: an armoured drive pod on
              each stern quarter, lying along its exhaust line out of the
              aft body's stepped outer corner;
  Engine 2/3  (-103, +-40), width 20: sunk in an armoured housing on the
              inner well's floor, fused to each blade's inner flank;
  Engine 5/6  (-113, +-11), width 30: the mains (the sprite's two pale bells at
              |Y| 4.5..17.5), heavier and set apart: octagonal armoured sleeves
              run aft out of the tail's square after face (X -105) with a bolted
              thrust collar and gimbal actuators.

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
from os_kit import boolean, ccw, facet_normal, mirror_y, plane_cut
from os_stern import MAINS_FACE_X, blade_inner_y, chined_mass
from os_machinery import Acc, tube, ring

# (x, y, width, contrailSize, length, angle) in onslaught.ship order; index i is position i+1.
ENGINES = [(-82, 70, 20, 64, 64, 160), (-103, 40, 20, 64, 64, 180), (-103, -40, 20, 64, 64, 180),
           (-82, -70, 20, 64, 64, 200), (-113, 11, 30, 64, 100, 180), (-113, -11, 30, 64, 100, 180)]
ENGINE_Z = {20: -8.0, 30: -6.0}
# Hierarchy (owner, round 3: the stern read as six identical rings in a row): the
# mains are the sprite's two pale bells, 13 px across (r 6.5), standing proud of the
# tail's after face on long ribbed bells with a heavy thrust collar; the 20-wide
# drives are smaller (r 4.8) and sunk: 2/3 in armoured housings against the blades'
# inner flanks, 1/4 in the quarter pods on the blades' outer glacis.
MOUTH_R = {20: 4.6, 30: 7.0}
BELL_LEN = {20: 8.0, 30: 14.0}
CAN_LEN = {20: 14.0, 30: 22.0}
MAINS = (5, 6)
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


def _bell_r(rm, L, px):
    """Outer radius of a bell (mouth rm, length L) at local px, as build() shapes it."""
    rt, wall = .45*rm, .45
    t = min(1.0, max(0.0, 1-px/L))
    return rt+wall+(rm-rt-wall)*(.6*(1-(1-t)**2)+.4*t)


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
    if hosted and i in MAINS:
        # The mains stand proud of the tail: a heavy octagonal thrust collar against the
        # tail's after face, bolted, and four gimbal actuators from it to the bell's mid hoop.
        cx = MAINS_FACE_X-x
        rb0 = _r_at(outer, cx)
        collar.add(*ring((cx, 0, 0), (1, 0, 0), rb0-.3, rm+3.6, 1.4, n=8))
        collar.add(*ring((cx-1.4, 0, 0), (1, 0, 0), rb0-.2, rb0+1.6, .9, n=16))
        for k in range(8):
            a = 2*math.pi*(k+.5)/8
            rb = rm+2.6
            collar.add(*tube((cx-1.3, rb*math.cos(a), rb*math.sin(a)), (cx-2.1, rb*math.cos(a), rb*math.sin(a)), .6, n=6))
        for k in range(4):
            a = 2*math.pi*(k+.5)/4
            ca, sa = math.cos(a), math.sin(a)
            r0, r1 = rb0+1.9, _r_at(outer, L*.22)+.7
            extra.add(*tube((cx-1.6, r0*ca, r0*sa), (L*.22, r1*ca, r1*sa), .55, n=6))
            extra.add(*tube((cx-1.6, r0*ca, r0*sa), ((cx-1.6+L*.22)/2, (r0+r1)/2*ca, (r0+r1)/2*sa), .85, n=6))
    elif hosted:
        rs = (rm+.9)/math.cos(math.pi/8)
        collar.add(*ring((-.3, 0, 0), (1, 0, 0), rs+1.2, rs+3.4, 1.3, n=8))
        for k in range(8):
            a = 2*math.pi*k/8
            rb = rs+2.3
            collar.add(*tube((-1.6, rb*math.cos(a), rb*math.sin(a)), (-2.3, rb*math.cos(a), rb*math.sin(a)), .55, n=6))
        if i in (2, 3) and not HOUSED:
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
                            (extra, ('gimbal actuators' if i in MAINS else 'turbopumps') if hosted else 'intake vent',
                             kit.plate)):
        ob = acc.build(kit, f'Engine {i} {label}', mat)
        if ob:
            place(ob, x, y, z, angle)


def _check_pod(i, x, y, z, angle, rp):
    """Report a quarter pod that no longer meets the hull: from stations along its forward
    half, look out radially past its skin for a hull or armour surface. A reshaped aft body
    can leave a pod hanging; the build says so rather than shipping it."""
    from mathutils import Vector
    from mathutils.bvhtree import BVHTree
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    trees = []
    for ob in bpy.context.scene.objects:
        if ob.type == 'MESH' and ob.name.split(' ')[0] in ('Hull', 'Armour'):
            ev = ob.evaluated_get(dg)
            me = ev.to_mesh()
            trees.append(BVHTree.FromPolygons([ev.matrix_world @ v.co for v in me.vertices],
                                              [tuple(p.vertices) for p in me.polygons]))
            ev.to_mesh_clear()
    a = math.radians(angle-180)
    fwd = Vector((math.cos(a), math.sin(a), 0))
    side = Vector((-fwd.y, fwd.x, 0))
    met = 0
    for f in (.5, .75, 1.0):
        c = Vector((x, y, z))+fwd*(POD_LEN*f)
        for k in range(8):
            b = 2*math.pi*k/8
            d = side*math.cos(b)+Vector((0, 0, 1))*math.sin(b)
            if any(t.ray_cast(c, d, rp+3.0)[0] is not None for t in trees):
                met += 1
                break
    if met < 2:
        print(f'ENGINE POD CLEAR OF HULL: Engine {i} (met at {met} of 3 stations)')


HOUSED = True     # drives 2/3 sit in armoured housings against the blades (their turbopumps are inside)


def _housing(kit, i, y):
    """The armoured housing of drive 2 or 3: a chined block on the inner well's floor,
    fused into the blade's inner flank, its after face carrying the sunk bell."""
    s = 1 if y > 0 else -1
    x0, x1 = -103.5, -79.0
    plan = [(x0, 32.4), (x0, blade_inner_y(x0)+3.0), (x1, blade_inner_y(x1)+3.0), (x1, 32.4)]
    plan = ccw(plan) if s > 0 else mirror_y(ccw(plan))
    ob = chined_mass(kit, f'Engine {i} drive housing', plan, (-17.0, 1.5, 3.0, 4.0, 2.5, 3.5, 0.0, 0.0), kit.grey,
                     step=1.5)
    # Its forward top falls to the well floor, so the pipe banks run onto it.
    plane_cut(ob, 'housing fore', (x1+2.0, 0, 1.5), facet_normal((1, 0), 40))
    _housing_keel(kit, i, s)
    return ob


# Round 9 (critic: the stern and front silhouettes were the same W, the drives never broke the
# stern outline): under each drive 2/3 housing the drive's machinery bay stands proud BELOW the
# aft body's swept-up keel as a chined ventral sponson, so from astern, low astern and on the
# stern quarters two drive keels hang between the arm lobes and the tail. Its top is buried in
# the aft body and the blade's keel; its forward end fairs up into the hull, its after face
# rakes forward as it falls, so abeam it is a raked skeg under the drive, not a box.
KEEL_PLAN = (-102.0, -74.0, 30.0, 54.0)       # x0, x1, y0, y1 (port)
KEEL_SPEC = (-38.0, -9.0, 10.0, 13.0, 0.0, 0.0, 0.0, 0.0)
KEEL_FAIR = (-80.0, 32.0)                     # the bottom rises forward from here at this slope
KEEL_AFT_RAKE = 24.0                          # the after face leans forward this much as it falls


def _housing_keel(kit, i, s):
    x0, x1, y0, y1 = KEEL_PLAN
    plan = [(x0, y0), (x0, y1), (x1, y1), (x1, y0)]
    plan = ccw(plan) if s > 0 else mirror_y(ccw(plan))
    ob = chined_mass(kit, f"Engine {i} drive keel", plan, KEEL_SPEC, kit.plate, step=1.5)
    fx, fdeg = KEEL_FAIR
    t = math.tan(math.radians(fdeg))
    plane_cut(ob, 'keel fairing', (fx, 0, KEEL_SPEC[0]), (t, 0, -1))
    r = math.tan(math.radians(KEEL_AFT_RAKE))
    plane_cut(ob, 'keel after rake', (x0, 0, KEEL_SPEC[1]), (-1, 0, -r))
    return ob


def build(kit, hull):
    hosts = {5: hull['tail'], 6: hull['tail']}
    for i, (x, y, w, _, _, angle) in enumerate(ENGINES, 1):
        if i in (2, 3):
            hosts[i] = _housing(kit, i, y)
    for i, (x, y, w, _, _, angle) in enumerate(ENGINES, 1):
        z = ENGINE_Z[w]
        rm, L, Lc = MOUTH_R[w], BELL_LEN[w], CAN_LEN[w]
        rt = .45*rm
        wall = .45
        if i in MAINS:     # bore the tail round the bell's root, behind its thrust collar
            # Round 9 (seam audit: the bore left the tail folded over an 11-unit void just inside
            # its after face): no bore. The bell's root runs into the solid tail and the shroud
            # and thrust collar close the face round it.
            pass
        elif i in hosts:   # sink the bell in a well in its host's after face
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
        if i in MAINS:
            # A heavy octagonal armoured sleeve run aft out of the tail's face round the bell,
            # so each main reads as a gun-barrel-heavy drive set apart from the sunk 20s.
            rs, cx = rm+.5, MAINS_FACE_X-x+1.0
            place(kit.mesh(f'Engine {i} shroud', *revolve([(-.5, rs), (-.5, rs+1.3), (.6, rs+2.2), (cx, rs+2.8),
                                                           (cx, rs)], 8, phase=.5), kit.grey), x, y, z, angle)
        elif i in hosts:
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
        if i not in hosts:
            _check_pod(i, x, y, z, angle, rm+2.6)
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
