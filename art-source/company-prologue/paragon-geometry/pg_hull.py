"""Hull masses of the Paragon: the ring, the prow spine, the command core, the two aft pods and the bore lining.

The Paragon's plan is a pointed ring (pg_outline.OUTER, read off the sprite)
round an open bore, with the white command core lying in the ring's aft
half and pushing its nose into the bore, and two drive pods spreading out
behind. Every mass is a smooth heightfield (pg_kit.smooth_mass): Tri-Tachyon
skin, not plate. The ring rises to a ridge midway between its outer edge and
its bore; the core and pods stand proud of it so the side view reads as
three levels: pods and ring, prow, core.
"""
import math
import pg_kit as K
import pg_outline

# The bore: the open centre of the ring, a circle read off the sprite's alpha
# (front at X 53, sides at Y +-57, closed by the core at X -59).
BORE_C = (-3.0, 0.0)
BORE_R = 56.5
# The dark lining band round the bore (the sprite's grey ring with its
# status lights): the shield emitter ring.
LINING_R = 69.0
# The core's plan, port half, nose to stern (read off the sprite's white core).
CORE = [(-20.5, 0.0), (-23, 6), (-27, 11), (-32, 15), (-40, 19.5), (-50, 22), (-62, 23.5), (-80, 25),
        (-100, 26.5), (-115, 30), (-128, 32.5), (-140, 32), (-150, 29), (-158, 24), (-165, 18), (-171, 11),
        (-175, 5), (-176.5, 0.0)]
# The prow spine, port half: the raised grille block at the bow between the two
# hardpoints, running back along the centreline to the bore's front.
PROW = [(171, 0.0), (171, 7), (168.5, 8), (168.5, 25), (171, 27), (171, 41), (162, 44), (150, 46),
        (138, 36), (120, 24), (98, 17), (75, 13), (52, 10), (48, 0.0)]
# The aft pods, port side: from the ring's aft quarter out to the pod wings
# and back to the drive line.
POD_INNER_Y = 34.0
POD_FRONT_X = -108.0
POD_REAR_X = -173.0
RING_AFT_X = -124.0

# The bore ring: the raised, smooth band concentric with the bore that the
# sprite draws as the light rim round the lining, carrying the big turrets.
BORE_RING_R = (61.0, 115.0)       # inner, outer radius: the groove at r 113 on the bow quarters
BORE_RING_Z = (-36.0, 36.0)      # belly, top: the deep structural ring, the hull's backbone
# The armour belt: the outer ring, r 113 to the circular outer edge at r 136,
# carrying the bow-quarter shells and the pods' roots. Lower than the main ring,
# so the groove between them reads; its own hole sits under the main ring.
BELT_HOLE_R = 108.0
# The bridge arch: the white core springs from the stern behind the ring, arches
# over the ring's aft band with clear space beneath, and lands its nose foot on
# the bore lining (owner, pass 1). The arch's underside is a superellipse in XZ.
ARCH = dict(x0=-66.0, x1=-140.0, z_spring=-20.0, z_crown=40.0, p=6.0, half_y=40.0)

Z = {  # keel, deck (units); heights are proposed art
    'ring': (-18.0, 12.0), 'prow': (-32.0, 30.0), 'core': (-34.0, 58.0), 'pod': (-28.0, 20.0),
}


def half_width(x):
    pts = pg_outline.OUTER
    if x >= pts[0][0]:
        return pts[0][1]
    for (x0, h0), (x1, h1) in zip(pts, pts[1:]):
        if x1 <= x <= x0:
            return h0+(h1-h0)*(x-x0)/(x1-x0) if x0 != x1 else max(h0, h1)
    return pts[-1][1]


def outline_between(front, aft, step=2.0):
    """The symmetric outline clipped to front >= X >= aft, as a CCW plan."""
    xs = [front]
    for x, _ in pg_outline.OUTER:
        if aft < x < front:
            xs.append(x)
    xs.append(aft)
    xs = sorted(set(xs), reverse=True)
    port = [(x, half_width(x)) for x in xs]
    return K.ccw(port+[(x, -y) for x, y in reversed(port)])


def symmetric(half):
    """A port half from bow to stern (both ends on the centreline) closed by its starboard mirror."""
    return K.ccw(list(half)+[(x, -y) for x, y in reversed(half) if y > 0])


def pod_plan(side):
    xs = [POD_FRONT_X]+[x for x, _ in pg_outline.OUTER if POD_REAR_X < x < POD_FRONT_X]+[POD_REAR_X]
    xs = sorted(set(xs), reverse=True)
    outer = [(x, half_width(x)-.5) for x in xs]
    plan = [(POD_FRONT_X, POD_INNER_Y)]+outer+[(POD_REAR_X, POD_INNER_Y)]
    if side < 0:
        plan = [(x, -y) for x, y in plan]
    return K.ccw(plan)


def build(kit):
    parts = {}
    k, d = Z['ring']
    parts['ring'] = K.smooth_mass(kit, 'Hull ring', outline_between(166.5, RING_AFT_X), k, d,
                                  holes=[K.circle(*BORE_C, BELT_HOLE_R, 160)], round_in=6, round_h=7,
                                  crown=8, crown_w=22, belly_crown=8, mat=kit.skin)
    parts['bore ring'] = bore_ring(kit)
    k, d = Z['prow']
    parts['prow'] = K.smooth_mass(kit, 'Hull prow', symmetric(PROW), k, d, round_in=6, round_h=7,
                                  crown=4, crown_w=12, belly_crown=2, mat=kit.pale, step=2.0)
    hardpoint_bays(kit, parts['prow'])
    k, d = Z['core']
    parts['core'] = K.smooth_mass(kit, 'Hull core', symmetric(CORE), k, d, round_in=16, round_h=18,
                                  crown=10, crown_w=14, belly_crown=3, mat=kit.white, step=2.0)
    bridge_arch(kit, parts['core'])
    k, d = Z['pod']
    for side, label in ((1, 'port'), (-1, 'starboard')):
        parts['pod '+label] = K.smooth_mass(kit, f'Hull {label} pod', pod_plan(side), k, d, round_in=6, round_h=7,
                                            crown=10, crown_w=32, belly_crown=8, mat=kit.skin)
    parts['lining'] = lining(kit)
    kit.hull += [parts['ring'], parts['bore ring'], parts['prow'], parts['core'], parts['pod port'],
                 parts['pod starboard']]
    return parts


def ring_section(r0, r1, z0, z1, rr):
    """Closed (r, z) loop of a rounded-rectangle section, inner edge r0 to outer r1."""
    pts = []
    for (cr, cz, a0) in ((r1-rr, z1-rr, 0), (r0+rr, z1-rr, 90), (r0+rr, z0+rr, 180), (r1-rr, z0+rr, 270)):
        for s in range(5):
            a = math.radians(a0+90*s/4)
            pts.append((cr+rr*math.cos(a), cz+rr*math.sin(a)))
    return pts


def revolve_loop(kit, name, loop, center, mat, segments=120):
    cx, cy, cz = center
    n = len(loop)
    verts = [(cx+r*math.cos(2*math.pi*k/segments), cy+r*math.sin(2*math.pi*k/segments), cz+z)
             for k in range(segments) for r, z in loop]
    faces = []
    for k in range(segments):
        a, b = k*n, ((k+1) % segments)*n
        faces += [(a+i, a+(i+1) % n, b+(i+1) % n, b+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat, 0, 40)


def lining(kit):
    """The bore lining: a dark technical torus between the bore and the ring skin,
    sitting lower than the ring top; the shield emitter nodes stand on it."""
    loop = ring_section(BORE_R, LINING_R+2, -26.0, 26.0, 5.0)
    return revolve_loop(kit, 'Hull bore lining', loop, (*BORE_C, 0), kit.tech)


def superellipse(rc, zc, a, b, p=2.6, n=40):
    """Closed (r, z) loop: a rounded-square section centred (rc, zc), half-width a, half-height b."""
    out = []
    for k in range(n):
        t = 2*math.pi*k/n
        c, s_ = math.cos(t), math.sin(t)
        out.append((rc+a*math.copysign(abs(c)**(2/p), c), zc+b*math.copysign(abs(s_)**(2/p), s_)))
    return out


def bore_ring(kit):
    r0, r1 = BORE_RING_R
    z0, z1 = BORE_RING_Z
    loop = superellipse((r0+r1)/2, (z0+z1)/2, (r1-r0)/2, (z1-z0)/2)
    return revolve_loop(kit, 'Hull bore ring', loop, (*BORE_C, 0), kit.skin, segments=180)


# The two capital mounts (WS 001/002, LARGE energy hardpoints) sit recessed in
# channels in the prow's armoured face: each bay runs from the face back past
# its slot, so the weapon fires out of the armour rather than standing on it.
BAY = dict(y=16.5, z=0.0, w=21.0, h=22.0, back=128.0)


def hardpoint_bays(kit, prow):
    import bpy
    for side in (1, -1):
        y = BAY['y']*side
        length = 190.0-BAY['back']
        bpy.ops.mesh.primitive_cube_add(size=1, location=(BAY['back']+length/2, y, BAY['z']))
        cut = bpy.context.object
        cut.name = 'Temporary hardpoint bay cutter'
        cut.dimensions = (length, BAY['w'], BAY['h'])
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        b = cut.modifiers.new('round', 'BEVEL')
        b.width, b.segments = 3.0, 3
        bpy.context.view_layer.objects.active = cut
        bpy.ops.object.modifier_apply(modifier=b.name)
        K.boolean(prow, cut)
        # The bay's floor, walls and back face are dark lining.
        kit.box(f'Hull prow bay {"port" if side > 0 else "starboard"} lining',
                (BAY['back']+1.0, y, BAY['z']), (2.0, BAY['w']-.4, BAY['h']-.4), kit.tech, 0)


def bridge_arch(kit, core):
    """Cut the arch under the core: a superellipse tunnel along Y between its two feet."""
    a = ARCH
    cx = (a['x0']+a['x1'])/2
    ax = abs(a['x0']-a['x1'])/2
    bz = a['z_crown']-a['z_spring']
    loop = []
    n = 48
    for k in range(n+1):
        t = math.pi*k/n
        c, s_ = math.cos(t), math.sin(t)
        loop.append((cx+ax*math.copysign(abs(c)**(2/a['p']), c), a['z_spring']+bz*abs(s_)**(2/a['p'])))
    loop += [(a['x1'], -80.0), (a['x0'], -80.0)]
    hy = a['half_y']
    verts = [(x, -hy, z) for x, z in loop]+[(x, hy, z) for x, z in loop]
    m = len(loop)
    faces = [tuple(range(m)), tuple(range(2*m-1, m-1, -1))]
    faces += [(i, (i+1) % m, m+(i+1) % m, m+i) for i in range(m)]
    cut = kit.mesh('Temporary bridge arch cutter', verts, faces, kit.tech)
    K.boolean(core, cut)
    # The nose foot overhangs the bore and lands on the lining: its underside
    # rakes up and forward from the lining (X -62) to the nose.
    K.plane_cut(core, 'nose rake', (-21.0, 0.0, 22.0), (.796, 0.0, -.605), size=300)
    core.data.shade_smooth()
    core.data.set_sharp_from_angle(angle=math.radians(50))
