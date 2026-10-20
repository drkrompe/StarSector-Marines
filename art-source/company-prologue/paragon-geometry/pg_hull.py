"""Hull masses of the Paragon, each with a job, laid out by the sprite's tones.

The sprite's three tones are three kinds of part (gauntlet.py scores them):
blue-grey skin is armour, white is the command deck, the bow module and the
reactor domes, dark is the bore lining, the turret wells, machinery and every
steep face. Read that way the hull is:

- the bore lining (`Hull bore lining`): the shield emitter ring, a dark band
  r 56.5-76 round the open bore with emitter blocks on it (pg_shield);
- the main ring (`Hull main ring`): the structural ring, r 74-130, the deepest
  mass and the backbone the rest hangs on, plated in flat segments. The hull
  carries its depth above its waterline-like mid-plane: the belly is a shallow
  armoured plane, so the ship's fullness over its plan matches the Onslaught's
  and Legion's rather than 1.3-1.7x it (owner, scale review);
- the armour belt (`Hull belt`): out to the circular outer edge (r 136) and the
  bow, a sloped armour face all round (the sprite's dark rim), rising inboard
  in a glacis to meet the main ring;
- the prow: a dark grille block (`Hull prow`) carrying the two capital mounts
  (WS 001/002) recessed in bays, the white bow module behind it, and two skin
  armour horns flanking it;
- the command deck: raised above the ring and the bore on a dark gantry
  (`Hull core trench`), the sprite's dark band round the white, stepping up in
  white tiers to the bridge; behind it the skin aft body between the pods;
- the aft pods: drive pods with a white reactor dome in a dark well each.

Masses are faceted (pg_kit.profile_mass, flat-shaded revolves): crisp armour
planes on curved plans, not a moulded skin (owner, pass 1).
"""
import math
import bpy
import pg_kit as K
import pg_outline

BORE_C = (-3.0, 0.0)
BORE_R = 56.5
LINING_R = 76.0
LINING_TOP = 24.0
LINING_BOT = 12.0
# The bore is a rounded doughnut hole, not a straight cut (owner, pass 3): the
# lining's inner face rolls over a lip of LIP_RH units into the bore, reaches the
# bore radius at mid-height, and rolls back under; its flat top runs from the lip
# to the main ring.
LIP_RH = 11.0
LIP_TOP_R = BORE_R+LIP_RH
# Main ring section (r, z), closed: inner face above the lining, flat top,
# outer glacis down into the belt; mirrored below.
MAIN_RING = [(74, -14), (80, -18), (100, -18), (130, -8), (130, 24), (127, 27.5), (99, 28.5), (96, 36), (78, 36),
             (74, 31)]
# The gun shelf: the main ring's outer course (r 99-130) steps down to a level
# shelf at z ~28 below the ring's crown (36). The big beam turrets and the beam
# mediums sit level on it with the ring's crown behind them, outside their arcs,
# and the shelf edge is the sprite's light arc round the ring.
BELT_HOLE_R = 100.0
BELT_TOP = [(0, 2), (5, 12), (24, 19), (50, 22)]
BELT_BOT = [(0, -3), (5, -8), (24, -11), (50, -12)]

# The core's plan, port half, nose to stern (read off the sprite's white deck and skin aft body).
CORE = [(-20.5, 0.0), (-23, 6), (-27, 11), (-32, 15), (-40, 19.5), (-50, 22), (-62, 23.5), (-80, 25),
        (-100, 26.5), (-115, 30), (-128, 32.5), (-140, 32), (-150, 29), (-158, 24), (-165, 18), (-171, 11),
        (-175, 5), (-176.5, 0.0)]
DECK_AFT_X = -104.0       # the white command deck runs from the nose to here
SPINE = [(-96, 0.0), (-96, 14), (-124, 14), (-130, 8), (-131, 0.0)]  # the white spine running aft onto the aft body
DECK_Z = 40.0             # its underside: above the main ring's top (36), clear of the bore
TRENCH_PAD = 6.0          # the gantry shows this far beyond the deck on each side
# The gantry stops at the bore's edge: forward of it the deck's nose cantilevers
# clear over the bore, raised above the loop, with its underside detailed (pg_bridge).
GANTRY_FRONT_X = -69.0   # on the bore lining's flat top, so its bulkhead stands on the lining

# The prow, port halves.
GRILLE = [(171, 0.0), (171, 29), (136, 29), (136, 0.0)]
BOW_MODULE = [(141, 0.0), (141, 20), (128, 22), (110, 18), (92, 14), (81, 8), (79, 0.0)]
HORN = [(171, 27), (171, 41), (162, 45), (150, 47), (132, 42), (116, 32), (120, 22), (140, 26)]

POD_INNER_Y = 34.0
POD_FRONT_X = -108.0
POD_REAR_X = -173.0
RING_AFT_X = -124.0
REACTOR = (-118.0, 88.0)
REACTOR_TOP = {}  # the reactor well's top face height per side, set at build

# The two capital mounts (WS 001/002) sit recessed in bays in the grille face.
BAY = dict(y=16.5, z=2.0, w=21.0, h=22.0, back=128.0)


def half_width(x):
    pts = pg_outline.OUTER
    if x >= pts[0][0]:
        return pts[0][1]
    for (x0, h0), (x1, h1) in zip(pts, pts[1:]):
        if x1 <= x <= x0:
            return h0+(h1-h0)*(x-x0)/(x1-x0) if x0 != x1 else max(h0, h1)
    return pts[-1][1]


def outline_between(front, aft):
    xs = sorted({front, aft, *[x for x, _ in pg_outline.OUTER if aft < x < front]}, reverse=True)
    port = [(x, half_width(x)) for x in xs]
    return K.ccw(port+[(x, -y) for x, y in reversed(port)])


def symmetric(half):
    return K.ccw(list(half)+[(x, -y) for x, y in reversed(half) if y > 0])


def half_between(half, front, aft, pad=0.0, scale=1.0):
    """The part of a nose-to-stern half outline between front and aft X, widened by pad, scaled in Y."""
    def at(x):
        for (x0, y0), (x1, y1) in zip(half, half[1:]):
            if x1 <= x <= x0:
                return y0+(y1-y0)*(x-x0)/(x1-x0)
        return 0.0
    xs = sorted({front, aft, *[x for x, _ in half if aft < x < front]}, reverse=True)
    pts = [(x, max(0.0, at(x)*scale+pad)) for x in xs]
    pts = [(xs[0], 0.0)]+[p for p in pts if p[1] > 0]+[(xs[-1], 0.0)]
    return pts


def pod_plan(side):
    xs = sorted({POD_FRONT_X, POD_REAR_X, *[x for x, _ in pg_outline.OUTER if POD_REAR_X < x < POD_FRONT_X]},
                reverse=True)
    plan = [(POD_FRONT_X, POD_INNER_Y)]+[(x, half_width(x)-.5) for x in xs]+[(POD_REAR_X, POD_INNER_Y)]
    return K.ccw([(x, y*side) for x, y in plan])


def lining_section(steps=14):
    """Closed (r, z) loop of the bore lining: flat underside, the rounded lip, flat top."""
    zc = (LINING_TOP-LINING_BOT)/2
    rv = (LINING_TOP+LINING_BOT)/2
    loop = [(LINING_R, -LINING_BOT)]
    for k in range(steps+1):
        t = math.radians(-90+180*k/steps)
        loop.append((BORE_R+LIP_RH*(1-math.cos(t)), zc+rv*math.sin(t)))
    loop.append((LINING_R, LINING_TOP))
    return loop


def revolve_loop(kit, name, loop, center, mat, segments=72, smooth=False):
    """A closed (r, z) section revolved about a vertical axis, flat-shaded: one plate per segment."""
    cx, cy, cz = center
    n = len(loop)
    verts = [(cx+r*math.cos(2*math.pi*k/segments), cy+r*math.sin(2*math.pi*k/segments), cz+z)
             for k in range(segments) for r, z in loop]
    faces = []
    for k in range(segments):
        a, b = k*n, ((k+1) % segments)*n
        faces += [(a+i, a+(i+1) % n, b+(i+1) % n, b+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat, 0, smooth)


def build(kit):
    parts = {}
    parts['belt'] = K.profile_mass(kit, 'Hull belt', outline_between(166.5, RING_AFT_X), BELT_TOP, BELT_BOT,
                                   holes=[K.circle(*BORE_C, BELT_HOLE_R, 144)], mat=kit.skin)
    parts['main ring'] = revolve_loop(kit, 'Hull main ring', MAIN_RING, (*BORE_C, 0), kit.skin)
    parts['lining'] = revolve_loop(kit, 'Hull bore lining', lining_section(), (*BORE_C, 0), kit.tech, segments=144,
                                   smooth=40)
    # Prow.
    parts['prow'] = K.profile_mass(kit, 'Hull prow', symmetric(GRILLE), [(0, 8), (3, 20), (10, 23)],
                                   [(0, -5), (3, -12), (10, -14)], mat=kit.pale, step=2.0)
    parts['bow module'] = K.profile_mass(kit, 'Hull bow module', symmetric(BOW_MODULE), [(0, 14), (3, 21), (8, 24)],
                                         [(0, -6), (8, -10)], mat=kit.white, step=2.0)
    hardpoint_bays(kit, (parts['belt'], parts['bow module'], parts['prow']))
    for side, label in ((1, 'port'), (-1, 'starboard')):
        parts['horn '+label] = K.profile_mass(kit, f'Hull {label} bow horn', K.ccw([(x, y*side) for x, y in HORN]),
                                              [(0, 6), (4, 21), (7, 24)], [(0, -5), (4, -10), (14, -12)],
                                              mat=kit.skin, step=2.0)
        # The bow-quarter armour lobe outside the groove: a raised shell for WS 011/017.
        parts['lobe '+label] = K.profile_mass(kit, f'Hull {label} bow lobe', lobe_plan(side),
                                              [(0, 8), (4, 24), (6, 26)], [(0, -5), (4, -10), (10, -12)],
                                              mat=kit.skin, step=2.0)
    # Command deck, gantry and aft body.
    # The gantry the deck stands on: dark machinery under and just outside the
    # deck, standing on the main ring; the sprite's dark band round the white.
    parts['trench'] = K.profile_mass(kit, 'Hull core trench', symmetric(half_between(CORE, GANTRY_FRONT_X, DECK_AFT_X-4, TRENCH_PAD)),
                                     [(0, DECK_Z-.5), (1.0, DECK_Z+1)], [(0, 20)], mat=kit.tech, step=2.0)
    # The aft body between the pods: its steep armoured flanks are the sprite's
    # dark ring round the skin "egg", its top a low faceted dome.
    parts['aft body'] = K.profile_mass(kit, 'Hull aft body', symmetric(half_between(CORE, -92, -176.5)),
                                       [(0, 6), (4, 24), (12, 40), (24, 47)], [(0, -6), (4, -14), (16, -20)],
                                       mat=kit.skin, step=2.0)
    # The command deck in white tiers, chamfered at 45 degrees so they read white from above.
    parts['deck'] = K.profile_mass(kit, 'Hull command deck', symmetric(half_between(CORE, -20.5, DECK_AFT_X)),
                                   [(0, DECK_Z+4), (6, DECK_Z+10), (10, DECK_Z+12)], [(0, DECK_Z)],
                                   mat=kit.white, step=2.0)
    parts['spine'] = K.profile_mass(kit, 'Hull command spine', symmetric(SPINE), [(0, 44), (4, 50), (7, 52)],
                                    [(0, 36)], mat=kit.white, step=1.5)
    parts['deck upper'] = K.profile_mass(kit, 'Hull command deck upper',
                                         symmetric(half_between(CORE, -28, DECK_AFT_X+6, 0, .66)),
                                         [(0, DECK_Z+15), (4, DECK_Z+18), (7, DECK_Z+19)], [(0, DECK_Z+8)],
                                         mat=kit.white, step=1.5)
    parts['bridge'] = K.profile_mass(kit, 'Bridge tower', symmetric([(-46, 0.0), (-48, 7), (-54, 10), (-80, 10),
                                                                     (-86, 6), (-86, 0.0)]),
                                     [(0, DECK_Z+23), (3, DECK_Z+27), (5, DECK_Z+28)], [(0, DECK_Z+17)],
                                     mat=kit.white, step=1.2)
    # Aft pods with their reactor domes.
    for side, label in ((1, 'port'), (-1, 'starboard')):
        parts['pod '+label] = K.profile_mass(kit, f'Hull {label} pod', pod_plan(side), [(0, 4), (5, 16), (20, 24)],
                                             [(0, -5), (5, -13), (20, -18)], mat=kit.skin)
    kit.hull += [p for k, p in parts.items()]
    for side, label in ((1, 'port'), (-1, 'starboard')):
        bow_groove(kit, [parts['belt'], parts['main ring']], side, label)
    for side, label in ((1, 'port'), (-1, 'starboard')):
        x, y = REACTOR[0], REACTOR[1]*side
        z = kit.surface(x, y)[0].z
        REACTOR_TOP[label] = z+.6
        K.revolve(kit, f'Hull {label} reactor well', [(0, -6), (15, -6), (15, .6), (0, .6)], kit.tech,
                  center=(x, y, z), segments=48, smooth=False)
        K.revolve(kit, f'Hull {label} reactor dome', [(0, -1.5), (8.5, -1.5), (8, 1.2), (6, 3.2), (3, 4.3), (0, 4.6)],
                  kit.white, center=(x, y, z+.6), segments=48, smooth=False)
    return parts


def hardpoint_bays(kit, masses):
    """The two bow hardpoint bays, cut through every mass they pass through (the belt and
    the bow module filled their back half until 2026-10-19), and lined dark."""
    for side in (1, -1):
        y = BAY['y']*side
        length = 190.0-BAY['back']
        bpy.ops.mesh.primitive_cube_add(size=1, location=(BAY['back']+length/2, y, BAY['z']))
        cut = bpy.context.object
        cut.name = 'Temporary hardpoint bay cutter'
        cut.dimensions = (length, BAY['w'], BAY['h'])
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        for i, mass in enumerate(masses):
            K.boolean(mass, cut, keep=i < len(masses)-1)
        # Line the bay dark: back wall, floor, roof and outboard wall, a skin's depth inside the cut.
        side_name = 'port' if side > 0 else 'starboard'
        b, w, h, z = BAY['back'], BAY['w'], BAY['h'], BAY['z']
        L = 171.0-b
        for part, loc, dims in (('back', (b+.5, y, z), (1.0, w, h)),
                                ('floor', (b+L/2, y, z-h/2+.6), (L, w, 1.2)),
                                ('roof', (b+L/2, y, z+h/2-.6), (L, w, 1.2)),
                                ('wall', (b+L/2, y+side*(w/2-.6), z), (L, 1.2, h)),
                                ('inner wall', (b+L/2, y-side*(w/2-.6), z), (L, 1.2, h))):
            kit.box(f'Hull prow {side_name} bay {part}', loc, dims, kit.tech, 0)


# The groove between the main ring and the bow-quarter armour lobes (the sprite's
# dark arc at r 114-122): angles from the bow, measured about the bore centre.
GROOVE = dict(r0=114.0, r1=122.0, a0=16.0, a1=50.0, width=4.5, depth=6.0)


def rib(kit, name, pts, width, h):
    """A strip following the hull surface along a polyline, standing h proud."""
    plan = K.strip_plan(pts, width)
    zc = []
    for x, y in pts:
        hit = kit.surface(x, y)
        zc.append(hit[0].z if hit else 0.0)
    zs = zc+list(reversed(zc))  # strip_plan runs one side forward and the other back
    verts = [(x, y, z+h) for (x, y), z in zip(plan, zs)]+[(x, y, z-4.0) for (x, y), z in zip(plan, zs)]
    m = len(plan)
    faces = [tuple(range(m)), tuple(range(2*m-1, m-1, -1))]
    faces += [(i, m+i, m+(i+1) % m, (i+1) % m) for i in range(m)]
    return kit.mesh(name, verts, faces, kit.pale, 0, False)


def bow_groove(kit, targets, side, label):
    g = GROOVE
    pts = []
    for k in range(13):
        t = k/12
        a = math.radians(g['a0']+(g['a1']-g['a0'])*t)
        r = g['r0']+(g['r1']-g['r0'])*t
        pts.append((BORE_C[0]+r*math.cos(a), side*(BORE_C[1]+r*math.sin(a))))
    plan = K.strip_plan(pts, g['width'])
    verts = [(x, y, 60.0) for x, y in plan]+[(x, y, -60.0) for x, y in plan]
    m = len(plan)
    top = []
    for x, y in pts:
        hit = kit.surface(x, y)
        top.append(hit[0].z if hit else 20.0)
    floor = min(top)-g['depth']
    verts = [(x, y, 60.0) for x, y in plan]+[(x, y, floor) for x, y in plan]
    faces = [tuple(range(m)), tuple(range(2*m-1, m-1, -1))]
    faces += [(i, m+i, m+(i+1) % m, (i+1) % m) for i in range(m)]
    for i, t in enumerate(targets):
        cut = kit.mesh('Temporary groove cutter', verts, faces, kit.tech)
        K.boolean(t, cut)
    fl = [(x, y, floor+.4) for x, y in plan]+[(x, y, floor-2) for x, y in plan]
    kit.mesh(f'Hull {label} bow groove', fl, faces, kit.tech, 0, False)


def lobe_plan(side):
    """The bow-quarter lobe: from the groove's outer edge out to the hull outline, over the groove's angles."""
    g = GROOVE
    inner, outer = [], []
    for k in range(13):
        t = k/12
        a = math.radians(g['a0']-2+(g['a1']+4-(g['a0']-2))*t)
        r = g['r0']+(g['r1']-g['r0'])*t+g['width']/2+.5
        x, y = BORE_C[0]+r*math.cos(a), BORE_C[1]+r*math.sin(a)
        inner.append((x, y))
        # March outward to the outline along the same bearing.
        rr = r
        while True:
            xx, yy = BORE_C[0]+(rr+1)*math.cos(a), BORE_C[1]+(rr+1)*math.sin(a)
            if abs(yy) > half_width(xx)-1.5:
                break
            rr += 1
        outer.append((BORE_C[0]+rr*math.cos(a), BORE_C[1]+rr*math.sin(a)))
    plan = inner+list(reversed(outer))
    return K.ccw([(x, y*side) for x, y in plan])
