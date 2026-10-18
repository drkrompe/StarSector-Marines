"""The turrets and hardpoints at onslaught.ship's weapon slots.

Every turret is a fixed barbette ring set into a bevelled well cut in the deck under its
slot (ray cast; see MAX_TILT and the round 11 note above RING_OUT), and a
trainable part parented to an empty named 'Weapon <slot> pivot' that sits on
the barbette and rests at the slot angle; rotating the pivot about its local
Z trains the turret (the Legion's convention). Only what stands above the
traverse gap trains.

Fittings follow onslaught_xiv_Elite (the Hegemony XIV fit), at mid-distance
detail only (C1 v2 sees the hull at 80-150 px):
  devastator   WS 018/020  large naval gunhouse, two heavy barrels
  needler      WS 019      large mount, a block of six needle barrels
  gun          medium gunhouses: heavy autocannon (WS 001/003/014/015, two
               barrels), flak (WS 007/010/011, two short), Hypervelocity
               Driver (WS 012/013, one long)
  small        Vulcan and light autocannon domes (WS 002/004/005/006/008/009)
  typhoon      WS 021-024  armoured box launchers on a yoke
  tpc          WS 016/017  the built-in Thermal Pulse Cannons: fixed
               hardpoints in each arm's nose, the barrel run out forward
WS 025 is a decorative hardpoint on the command dome; os_fittings puts the
sensor mast there.
"""
import math
import bpy
from mathutils import Vector
import os_kit

SLOTS = [  # onslaught.ship (the XIV skin changes no slot), with the onslaught_xiv_Elite weapon
    ('WS 001', (158, 71), 70, 150, 'MEDIUM', 'BALLISTIC', 'heavyac'),
    ('WS 002', (134, 74), 65, 120, 'SMALL', 'BALLISTIC', 'vulcan'),
    ('WS 003', (158, -71), -70, 150, 'MEDIUM', 'BALLISTIC', 'heavyac'),
    ('WS 004', (134, -74), -65, 120, 'SMALL', 'BALLISTIC', 'vulcan'),
    ('WS 005', (114, 78), 75, 120, 'SMALL', 'BALLISTIC', 'vulcan'),
    ('WS 006', (114, -78), -75, 120, 'SMALL', 'BALLISTIC', 'vulcan'),
    ('WS 007', (187, 0), 0, 150, 'MEDIUM', 'BALLISTIC', 'dualflak'),
    ('WS 008', (150, -12), -20, 90, 'SMALL', 'BALLISTIC', 'lightac'),
    ('WS 009', (150, 12), 20, 90, 'SMALL', 'BALLISTIC', 'lightac'),
    ('WS 010', (-3, -112), 250, 180, 'MEDIUM', 'BALLISTIC', 'flak'),
    ('WS 011', (-3, 112), 110, 180, 'MEDIUM', 'BALLISTIC', 'flak'),
    ('WS 012', (49, 18), 60, 140, 'MEDIUM', 'BALLISTIC', 'hveldriver'),
    ('WS 013', (49, -18), -60, 140, 'MEDIUM', 'BALLISTIC', 'hveldriver'),
    ('WS 014', (-9, 22), 170, 120, 'MEDIUM', 'BALLISTIC', 'heavyac'),
    ('WS 015', (-9, -22), 190, 120, 'MEDIUM', 'BALLISTIC', 'heavyac'),
    ('WS 018', (77, 88), 80, 135, 'LARGE', 'BALLISTIC', 'devastator'),
    ('WS 019', (126, 0), 0, 150, 'LARGE', 'BALLISTIC', 'multineedler'),
    ('WS 020', (77, -87), -80, 135, 'LARGE', 'BALLISTIC', 'devastator'),
    ('WS 021', (-9, 65), 30, 120, 'MEDIUM', 'MISSILE', 'typhoon'),
    ('WS 022', (8, 40), 20, 120, 'MEDIUM', 'MISSILE', 'typhoon'),
    ('WS 023', (8, -40), -20, 120, 'MEDIUM', 'MISSILE', 'typhoon'),
    ('WS 024', (-9, -65), -30, 120, 'MEDIUM', 'MISSILE', 'typhoon'),
]
HARDPOINTS = [('WS 016', (198, 70), 0, 5, 'LARGE', 'BUILT_IN', 'tpc'),
              ('WS 017', (198, -70), 0, 5, 'LARGE', 'BUILT_IN', 'tpc')]
DECORATIVE = [('WS 025', (17, 0), 0, 120, 'SMALL', 'DECORATIVE', 'sensor mast')]
RING = {'LARGE': 11.0, 'MEDIUM': 7.0, 'SMALL': 4.2}   # read off the sprite's turret rings
GAP = .08
PIVOT_Z = {}


def _place(ob, pivot, loc, rot=(0, 0, 0)):
    ob.parent = pivot
    ob.location = loc
    ob.rotation_euler = rot
    return ob


def _box(kit, name, pivot, loc, dims, mat, bevel=.12):
    return _place(kit.box(name, (0, 0, 0), dims, mat, bevel=bevel), pivot, loc)


def _cyl(kit, name, pivot, loc, radius, depth, mat, axis='X', vertices=12, bevel=.05):
    rot = {'X': (0, math.pi/2, 0), 'Y': (math.pi/2, 0, 0), 'Z': (0, 0, 0)}[axis]
    return _place(kit.cylinder(name, (0, 0, 0), radius, depth, mat, axis='Z', vertices=vertices, bevel=bevel),
                  pivot, loc, rot)


_DECK = []


def _deck_objects():
    """Every mesh built before the turrets that a barbette could stand on or in:
    hull, armour bands, bridge parts, machinery. Weapons, lights and fittings excluded."""
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    _DECK.clear()
    for ob in bpy.context.scene.objects:
        if ob.type != 'MESH' or ob.name.split(' ')[0] in ('Weapon', 'Light', 'Fitting', 'Temporary'):
            continue
        ev = ob.evaluated_get(dg)
        pts = [ev.matrix_world @ Vector(c) for c in ev.bound_box]
        lo = [min(p[i] for p in pts) for i in range(2)]
        hi = [max(p[i] for p in pts) for i in range(2)]
        _DECK.append((ev, lo, hi))


def _gunhouse(kit, sid, pivot, r, mat, roof, length=1.0):
    z0 = GAP*r
    oc = os_kit.octagon
    st = [(-.85*r*length, oc(-.5*r, .5*r, z0, (roof-.08)*r, .14*r, .05*r)),
          (-.6*r*length, oc(-.8*r, .8*r, z0, roof*r, .18*r, .05*r)),
          (.32*r, oc(-.8*r, .8*r, z0, roof*r, .18*r, .05*r)),
          (.66*r, oc(-.6*r, .6*r, z0, (roof-.12)*r, .1*r, .05*r))]
    return _place(os_kit.loft_x(kit, f'Weapon {sid} turret house', st, mat, bevel=.1), pivot, (0, 0, 0))


def _gun(kit, slot, pivot, r):
    sid, weapon = slot[0], slot[6]
    spec = {'devastator': (2, 1.7, .085, .34, .66), 'heavyac': (2, 1.25, .075, .3, .6),
            'dualflak': (2, .95, .06, .3, .55), 'flak': (2, .95, .06, .3, .55),
            'hveldriver': (1, 2.1, .07, 0, .58)}[weapon]
    n, length, rad, spacing, roof = spec
    mat = kit.orange if slot[4] == 'LARGE' or weapon == 'hveldriver' else kit.grey
    _cyl(kit, f'Weapon {sid} traverse ring', pivot, (0, 0, GAP*r/2), .88*r, GAP*r+.05, kit.dark, axis='Z',
         vertices=16, bevel=0)
    _gunhouse(kit, sid, pivot, r, mat, roof)
    face = .66*r
    bz = .3*r
    _box(kit, f'Weapon {sid} turret shield', pivot, (face+.04*r, 0, GAP*r+.24*r), (.12*r, 1.25*r, .46*r), kit.plate)
    for k in range(n):
        y = (k-(n-1)/2)*spacing*r
        L = length*r
        x0 = face+.1*r
        _box(kit, f'Weapon {sid} mantlet {k+1}', pivot, (x0, y, bz), (.14*r, .2*r, .22*r), kit.steel, .06)
        _cyl(kit, f'Weapon {sid} barrel {k+1}', pivot, (x0+L/2, y, bz), rad*r, L, kit.steel, vertices=10)
        _cyl(kit, f'Weapon {sid} barrel jacket {k+1}', pivot, (x0+.18*L, y, bz), rad*1.45*r, .34*L, kit.steel,
             vertices=10)
        _box(kit, f'Weapon {sid} muzzle brake {k+1}', pivot, (x0+L+.04*r, y, bz), (.12*r, rad*2.6*r, rad*2.4*r),
             kit.steel, .05)
    if weapon == 'devastator':
        _heavy_roof(kit, sid, pivot, r, roof, mat)
        # Blast bags: canvas boots where each barrel enters the mantlet.
        for k in range(n):
            y = (k-(n-1)/2)*spacing*r
            _cyl(kit, f'Weapon {sid} blast bag {k+1}', pivot, (face+.22*r, y, bz), .15*r, .18*r, _canvas(),
                 vertices=12, bevel=.03)
        # Rangefinder raised on a hood across the rear roof, with armoured ears at its ends.
        rz = (roof+.2)*r
        _box(kit, f'Weapon {sid} rangefinder hood', pivot, (-.4*r, 0, (roof+.13)*r), (.3*r, .7*r, .1*r), mat, .06)
        _cyl(kit, f'Weapon {sid} rangefinder', pivot, (-.4*r, 0, rz), .08*r, 1.7*r, kit.steel, axis='Y',
             vertices=8)
        for side, s in (('port', 1), ('starboard', -1)):
            _box(kit, f'Weapon {sid} rangefinder ear {side}', pivot, (-.4*r, s*.88*r, rz), (.3*r, .2*r, .26*r),
                 kit.steel, .05)
    else:
        # Rangefinder across the rear roof.
        _cyl(kit, f'Weapon {sid} rangefinder', pivot, (-.4*r, 0, (roof+.1)*r), .08*r, 1.7*r, kit.steel, axis='Y',
             vertices=8)


_CANVAS = []


def _canvas():
    """Matte dark canvas for blast bags (this module's own material)."""
    if not _CANVAS:
        _CANVAS.append(os_kit.material('Gun blast bag canvas', (.07, .06, .05), 0.0, .9))
    return _CANVAS[0]


def _heavy_roof(kit, sid, pivot, r, roof, mat):
    """An extra roof plate over a large gunhouse: flat over the crown, sloping down the face,
    a step narrower than the house so its edge reads as a second layer of armour."""
    oc = os_kit.octagon
    st = [(-.56*r, oc(-.66*r, .66*r, (roof-.03)*r, (roof+.11)*r, .07*r, 0)),
          (.28*r, oc(-.66*r, .66*r, (roof-.03)*r, (roof+.11)*r, .07*r, 0)),
          (.60*r, oc(-.5*r, .5*r, (roof-.16)*r, (roof-.04)*r, .05*r, 0))]
    _place(os_kit.loft_x(kit, f'Weapon {sid} roof armour', st, mat, bevel=.08), pivot, (0, 0, 0))


def _small(kit, slot, pivot, r):
    sid = slot[0]
    z0 = GAP*r
    prof = [(.8*r, z0), (.8*r, z0+.22*r), (.58*r, z0+.46*r), (.28*r, z0+.54*r)]
    n = 10
    verts, faces = [], []
    for rad, z in prof:
        verts += [(rad*math.cos(2*math.pi*(i+.5)/n), rad*math.sin(2*math.pi*(i+.5)/n), z) for i in range(n)]
    faces.append(tuple(reversed(range(n))))
    for j in range(len(prof)-1):
        faces += [(j*n+i, j*n+(i+1) % n, (j+1)*n+(i+1) % n, (j+1)*n+i) for i in range(n)]
    faces.append(tuple(range((len(prof)-1)*n, len(prof)*n)))
    _place(kit.mesh(f'Weapon {sid} turret house', verts, faces, kit.grey), pivot, (0, 0, 0))
    bz = z0+.26*r
    L = (1.1 if slot[6] == 'vulcan' else 1.3)*r
    _box(kit, f'Weapon {sid} turret shield', pivot, (.62*r, 0, bz), (.26*r, .62*r, .36*r), kit.steel, .08)
    _cyl(kit, f'Weapon {sid} barrel 1', pivot, (.74*r+L/2, 0, bz), (.14 if slot[6] == 'vulcan' else .09)*r, L,
         kit.steel, vertices=10)


def _launcher(kit, slot, pivot, r):
    sid = slot[0]
    z0 = GAP*r
    _cyl(kit, f'Weapon {sid} traverse ring', pivot, (0, 0, z0/2), .88*r, z0+.05, kit.dark, axis='Z', vertices=16,
         bevel=0)
    # A thin deck plate inside the barbette's lip (round 11: a full-width disk stacked on the ring
    # read as one more tier of a wedding cake).
    _cyl(kit, f'Weapon {sid} launcher deck', pivot, (0, 0, z0+.04*r), .82*r, .08*r, kit.plate, axis='Z', vertices=16)
    deck = z0+.08*r
    for side, s in (('port', 1), ('starboard', -1)):
        _box(kit, f'Weapon {sid} launcher yoke arm {side}', pivot, (0, s*.62*r, deck+.24*r), (.5*r, .12*r, .48*r),
             kit.orange, .08)
    hz = deck+.42*r
    _box(kit, f'Weapon {sid} launcher house', pivot, (.05*r, 0, hz), (1.35*r, 1.08*r, .52*r), kit.grey, .12)
    _box(kit, f'Weapon {sid} launcher cell face', pivot, (.05*r+.68*r, 0, hz), (.04*r, .9*r, .4*r), kit.dark, 0)
    for k in range(3):
        _box(kit, f'Weapon {sid} launcher cell cover {k+1}', pivot, (.05*r, (k-1)*.32*r, hz+.27*r),
             (1.2*r, .26*r, .04*r), kit.steel, .03)


def _needler(kit, slot, pivot, r):
    sid = slot[0]
    _cyl(kit, f'Weapon {sid} traverse ring', pivot, (0, 0, GAP*r/2), .88*r, GAP*r+.05, kit.dark, axis='Z',
         vertices=16, bevel=0)
    _gunhouse(kit, sid, pivot, r, kit.orange, .5, length=.9)
    bz = .3*r
    _box(kit, f'Weapon {sid} turret shield', pivot, (.7*r, 0, bz), (.12*r, 1.0*r, .42*r), kit.plate)
    L = 1.35*r
    for k in range(6):
        y = ((k % 3)-1)*.2*r
        z = bz+(k//3-.5)*.17*r
        _cyl(kit, f'Weapon {sid} barrel {k+1}', pivot, (.76*r+L/2, y, z), .055*r, L, kit.steel, vertices=8)
    _box(kit, f'Weapon {sid} barrel clamp', pivot, (.76*r+.7*L, 0, bz), (.12*r, .72*r, .52*r), kit.steel, .05)
    _heavy_roof(kit, sid, pivot, r, .5, kit.orange)
    _box(kit, f'Weapon {sid} barrel shroud', pivot, (.76*r+.1*r, 0, bz), (.2*r, .78*r, .56*r), kit.steel, .05)
    _box(kit, f'Weapon {sid} sensor head', pivot, (-.36*r, .34*r, .64*r), (.34*r, .26*r, .2*r), kit.steel, .05)
    for side, s in (('port', 1), ('starboard', -1)):
        _box(kit, f'Weapon {sid} magazine {side}', pivot, (-.22*r, s*.86*r, .26*r), (.62*r, .16*r, .3*r),
             kit.grey, .05)


def _local(frame, t, w, h):
    """World point of (t along the line of fire, w across, h up) in a hardpoint frame."""
    p0, s, n = frame
    return p0+Vector((t, 0, 0))+s*w+n*h


def _deck_h(frame, t, w=0.0):
    """Height of the deck above the frame's base line at (t, w), along the frame's up; None off the hull."""
    p0, s, n = frame
    q = _local(frame, t, w, 0.0)
    h = _hit(q+n*150, -n, 300)
    return None if h is None else (h[1]-q).dot(n)


def _arm_frame(x, y):
    """The arm's pose under a hardpoint, read off the hull: the line of fire stays +X (the slot's
    angle 0); 'up' leans with the arm's roll, measured across the gallery behind the nose."""
    rolls = []
    for px in (x-48, x-40, x-32, x-24):
        a, b = _hit((px, y+5, 150), (0, 0, -1)), _hit((px, y-5, 150), (0, 0, -1))
        if a and b and abs(a[1].z-b[1].z) < 8:
            rolls.append((a[1].z-b[1].z)/10)
    rolls.sort()
    c = rolls[len(rolls)//2] if rolls else 0.0
    c = max(-.6, min(.6, c))
    n = Vector((0, -c, 1)).normalized()
    s = n.cross(Vector((1, 0, 0))).normalized()
    return (Vector((0, y, 0)), s, n)


def _section_solid(kit, name, frame, stations, mat, bevel=.2):
    """A solid lofted along the line of fire through (t, bottom, top, half-width bottom, half-width top)
    stations: a chamfered casing whose roof and keel follow the given heights."""
    verts, faces = [], []
    for t, zb, zt, wb, wt in stations:
        ch = min(.7, .25*(zt-zb))
        ring = [(-wb, zb), (wb, zb), (wb, zt-ch), (wt, zt), (-wt, zt), (-wb, zt-ch)]
        verts += [tuple(_local(frame, t, w, h)) for w, h in ring]
    m = 6
    faces.append(tuple(reversed(range(m))))
    for j in range(len(stations)-1):
        faces += [(j*m+i, j*m+(i+1) % m, (j+1)*m+(i+1) % m, (j+1)*m+i) for i in range(m)]
    faces.append(tuple(range((len(stations)-1)*m, len(stations)*m)))
    return kit.mesh(name, verts, faces, mat, bevel)


def _x_cyl(kit, name, frame, t0, t1, h, radius, mat, vertices=12):
    """Cylinder along the line of fire from t0 to t1 at height h on the frame's centre line."""
    p0, s, n = frame
    ob = kit.cylinder(name, tuple(_local(frame, (t0+t1)/2, 0, h)), radius, abs(t1-t0), mat, axis='X',
                      vertices=vertices)
    return ob


def _box_strip(verts, faces, frame, t0, t1, w0, w1, h0, h1):
    """Append a box (t0..t1 along the line of fire, w0..w1 across, h0..h1 up) to a mesh list."""
    o = len(verts)
    for t in (t0, t1):
        verts += [tuple(_local(frame, t, w, h)) for w, h in ((w0, h0), (w1, h0), (w1, h1), (w0, h1))]
    faces += [(o, o+3, o+2, o+1), (o+4, o+5, o+6, o+7), (o, o+1, o+5, o+4), (o+1, o+2, o+6, o+5),
              (o+2, o+3, o+7, o+6), (o+3, o, o+4, o+7)]


def _hardpoint(kit, slot):
    """A Thermal Pulse Cannon that IS the arm's nose (round 4: the arm noses lead the silhouette).

    The casemate is an armoured box on the line of fire, leaning with the arm's roll, its axis at
    os_arms.TPC_AXIS_Z (under the prow's keel, so in side view the two gun noses jut out beneath
    and beside the ram). It runs from inside the arm (TPC_AFT_X) out through the arm's raked nose
    face to its own face at TPC_FACE_X, the sprite's ladder tip; os_arms rakes the forefoot down
    and aft from its chin, the gallery and deck up and aft from its roof, and lays the stepped
    cheeks against its sides. On the roof, the sprite's ladder: two rails with cooling ribs across
    them. Ahead of the face, a mantlet, the ribbed barrel and the two prongs that flank it."""
    import os_arms
    sid, (x, y) = slot[0], slot[1]
    s = 1 if y > 0 else -1
    up = os_arms.pose(s).to_3x3() @ Vector((0, 0, 1))
    up.x = 0.0
    up.normalize()
    side = up.cross(Vector((1, 0, 0))).normalized()
    frame = (Vector((0.0, y, os_arms.TPC_AXIS_Z)), side, up)
    face, aft, hw = os_arms.TPC_FACE_X, os_arms.TPC_AFT_X, os_arms.TPC_HALF_W
    roof, chin = 8.0, -9.0
    # (t, chin, roof, half-width low, half-width at the roof): a chamfered box, its face with a
    # sloped brow and a chamfered chin.
    stations = [(aft, chin+1.5, roof-2.0, hw-.6, hw-1.8), (aft+8, chin, roof, hw, hw-1.4),
                (face-4.0, chin, roof, hw, hw-1.4), (face-1.4, chin+.3, roof-.9, hw-.1, hw-1.5),
                (face, chin+1.6, roof-3.0, hw-.4, hw-1.8)]
    verts, faces = [], []
    for t, zb, zt, wb, wt in stations:
        c, ch = 1.6, 1.3
        ring = [(-wb+c, zb), (wb-c, zb), (wb, zb+c), (wb, zt-ch), (wt, zt), (-wt, zt), (-wb, zt-ch), (-wb, zb+c)]
        verts += [tuple(_local(frame, t, w, h)) for w, h in ring]
    m = 8
    faces.append(tuple(reversed(range(m))))
    for j in range(len(stations)-1):
        faces += [(j*m+i, j*m+(i+1) % m, (j+1)*m+(i+1) % m, (j+1)*m+i) for i in range(m)]
    faces.append(tuple(range((len(stations)-1)*m, len(stations)*m)))
    kit.mesh(f'Weapon {sid} hardpoint housing', verts, faces, kit.plate, .2)
    # The ladder on the roof: two rails and the cooling ribs across them, aft to fore.
    ribs_v, ribs_f = [], []
    wt = hw-1.4
    for w in (-3.0, 3.0):
        _box_strip(ribs_v, ribs_f, frame, 194.0, face-1.6, w-.5, w+.5, roof-.4, roof+1.2)
    t = 196.0
    while t < face-2.5:
        _box_strip(ribs_v, ribs_f, frame, t-.35, t+.35, -wt-.3, wt+.3, roof-.4, roof+.7)
        t += 2.2
    kit.mesh(f'Weapon {sid} hardpoint cooling ribs', ribs_v, ribs_f, kit.steel)
    # Mantlet on the face, then the ribbed barrel between two prongs, and its muzzle.
    mv, mf = [], []
    _box_strip(mv, mf, frame, face-.4, face+1.8, -5.0, 5.0, -4.6, 4.0)
    kit.mesh(f'Weapon {sid} hardpoint mantlet', mv, mf, kit.steel, .25)
    pv, pf = [], []
    for w in (-4.7, 4.7):
        _box_strip(pv, pf, frame, face+1.0, face+8.5, w-.55, w+.55, -2.2, 2.2)
    kit.mesh(f'Weapon {sid} hardpoint prongs', pv, pf, kit.steel, .15)
    L = 9.0
    t0 = face+1.8
    _x_cyl(kit, f'Weapon {sid} barrel', frame, t0-3.0, t0+L, 0.0, 2.8, kit.steel)
    for k, f in enumerate((.22, .5, .78)):
        _x_cyl(kit, f'Weapon {sid} barrel coil {k+1}', frame, t0+f*L-.8, t0+f*L+.8, 0.0, 3.6, kit.steel)
    _x_cyl(kit, f'Weapon {sid} muzzle collar', frame, t0+L-.6, t0+L+.6, 0.0, 3.5, kit.plate)
    kit.empty(f'Weapon {sid} muzzle', tuple(_local(frame, t0+L+.7, 0, 0.0)), slot[2])
    PIVOT_Z[sid] = _local(frame, t0+L+.7, 0, 0.0).z


MAX_TILT = 25.0      # deg: the steepest a mount leans with the hull under it

# Round 11 (owner: "hull turrets and their mounts are against the hull ... not on hockey pucks";
# critic r9 c: the hex plinths and stepped round collars still read as pucks). A mount is now a
# flush bevelled barbette ring set INTO the deck: its seat plane sits below the highest deck
# under the ring, a bevelled well is cut down to that plane in whatever is under it (a boolean),
# and the ring stands in the well with its top at most one small step (RING_TOP) above the seat.
# Where the deck falls below the seat (a crest, a terrace's shoulder) the ring's foot is one
# straight bevel down to the deck; where it falls away as a cliff, a short bracket tucks under.
RING_OUT = 1.12      # ring outer radius over the slot's ring radius
RING_TOP = .1        # ring top above the seat plane, over r: the one small step
SINK_MIN = .1        # the seat lies at least this far (over r) under the highest deck under the ring
CUT_MAX = .3         # deepest well wall (over r) where the barrels train: they (.4 r over the seat) clear its lip
SETTLE = 1.5         # over r: how far out the mount looks for the deck it is let down toward
CUT_DEEP = .7        # deepest well wall (over r) behind the arc: the house (inside the ring) never meets it
WELL_GAP = .06       # radial clearance between the ring and the well's wall
WELL_BATTER = .7     # outward run of the well's wall per unit of height (about 55 deg)
FLARE_RUN = .7       # outward run of the foot's bevel per unit of drop (about 55 deg)
FLARE_MAX = .45      # longest bevel run (over r); a deeper fall is a cliff and gets a bracket
SKIRT_MAX = 14.0     # a deeper drop under the rim is a cliff (a well, a tier's face)
STRUCTURE_RISE = 6.0 # an object rising this far over the well's planned depth is a structure, not deck
DEBUG = False
ARC_STRUCTURE = .5   # over r: deck rising higher than this in the arc is a structure bounding it
CLEARANCE = .4       # trained parts clear the deck in their arc by this much
NSEG = 24            # segments round a ring and its well
CUTS = []            # (slot, objects cut, depth) for the build log


def _hit(origin, direction, far=400.0):
    """Nearest hit of a ray against the deck objects: (distance, point, normal) or None."""
    o, d = Vector(origin), Vector(direction).normalized()
    e = o+d*far
    sx0, sx1, sy0, sy1 = min(o.x, e.x), max(o.x, e.x), min(o.y, e.y), max(o.y, e.y)
    best = None
    for ev, lo, hi in _DECK:
        if lo[0] > sx1 or hi[0] < sx0 or lo[1] > sy1 or hi[1] < sy0:
            continue
        inv = ev.matrix_world.inverted()
        ok, p, n, _ = ev.ray_cast(inv @ o, (inv.to_3x3() @ d).normalized(), distance=far)
        if ok:
            p = ev.matrix_world @ p
            dist = (p-o).length
            if best is None or dist < best[0]:
                n = (ev.matrix_world.to_3x3().inverted().transposed() @ n).normalized()
                best = (dist, p, n if n.dot(d) < 0 else -n)
    return best


def _mount_frame(x, y, r):
    """Where a turret meets the hull: the surface point under its slot and the hull's lean there
    (a plane fitted through the deck under the ring, at most MAX_TILT). Returns (point, basis)."""
    from mathutils import Matrix
    h = _hit((x, y, 150), (0, 0, -1))
    if h is None:
        p, n = Vector((x, y, 10.0)), Vector((0, 0, 1))
    else:
        _, p, n = h
        # Lean with the ground the ring covers, not the facet under its centre; an edge mount
        # (a cliff under part of its rim) keeps the centre's normal.
        pts = []
        for k in range(12):
            a = 2*math.pi*k/12
            q = _hit((x+1.05*r*math.cos(a), y+1.05*r*math.sin(a), 150), (0, 0, -1))
            if q is not None and p.z-q[1].z < SKIRT_MAX*.6:
                pts.append(q[1])
        if len(pts) == 12:
            sx = sum(q.x-x for q in pts); sy = sum(q.y-y for q in pts); sz = sum(q.z for q in pts)
            xx = sum((q.x-x)**2 for q in pts); yy = sum((q.y-y)**2 for q in pts)
            xy = sum((q.x-x)*(q.y-y) for q in pts)
            xz = sum((q.x-x)*q.z for q in pts); yz = sum((q.y-y)*q.z for q in pts)
            A = Matrix(((xx, xy, sx), (xy, yy, sy), (sx, sy, len(pts))))
            try:
                a_, b_, _ = A.inverted() @ Vector((xz, yz, sz))
                n = Vector((-a_, -b_, 1)).normalized()
            except ValueError:
                pass
    up = Vector((0, 0, 1))
    tilt = math.degrees(n.angle(up))
    if tilt > MAX_TILT:
        n = up.slerp(n, MAX_TILT/tilt).normalized()
    xa = Vector((1, 0, 0))
    xa = (xa-n*xa.dot(n)).normalized()
    ya = n.cross(xa)
    return p, Matrix((xa, ya, n)).transposed()


def _height(p, basis, rad, a, above=40.0):
    """Deck height over the plane through p (along the mount's up) at (rad, angle a); None past a
    cliff (no deck within SKIRT_MAX under the plane)."""
    xa, ya, nz = basis.col[0], basis.col[1], basis.col[2]
    q = p+(xa*math.cos(a)+ya*math.sin(a))*rad
    hh = _hit(q+nz*above, -nz, above+SKIRT_MAX)
    return None if hh is None else above-hh[0]


def _angles(n=NSEG):
    return [2*math.pi*(k+.5)/n for k in range(n)]


def _in_arc(a, angle, arc):
    """True if mount-frame direction a (rad) is within the slot's arc, with a margin for the muzzles."""
    d = (math.degrees(a)-angle+180) % 360-180
    return abs(d) <= arc/2+20


def _seat(p, basis, r, angle, arc):
    """The seat plane's height over p. The ring's top is flush with the highest deck under it
    (SINK_MIN), and on a slope or a crest the seat goes down toward the lowest deck at the rim, so
    the ring is set into the hull rather than built up on it. The well's wall may stand at most
    CUT_MAX over the seat where the barrels train (they must clear its lip), CUT_DEEP behind them.
    Returns (seat, highest deck, rim heights at RING_OUT)."""
    hi = hi_arc = -1e9
    for rad in (0.0, .45*r, .8*r, RING_OUT*r, (RING_OUT+WELL_GAP)*r):
        for a in (_angles(16) if rad else [0.0]):
            h = _height(p, basis, rad, a)
            if h is None:
                continue
            hi = max(hi, h)
            if rad == 0.0 or _in_arc(a, angle, arc):
                hi_arc = max(hi_arc, h)
    rim = [_height(p, basis, RING_OUT*r, a) for a in _angles()]
    # The deck the mount is set down toward: the lowest under its rim and just beyond it, so a
    # mount on a raised housing or a crest is let down into it rather than perched on top.
    valid = [h for h in rim if h is not None]
    valid += [h for h in (_height(p, basis, SETTLE*r, a) for a in _angles(16)) if h is not None]
    lo = min(valid) if valid else hi
    seat = min(max(lo, hi_arc-CUT_MAX*r, hi-CUT_DEEP*r), hi-SINK_MIN*r)
    return seat, hi, rim


def _well(kit, sid, origin, basis, r, depth):
    """Cut the barbette's well: a bevelled countersink from the seat plane up through whatever
    stands above it under the ring. Returns the names of the objects cut."""
    xa, ya, nz = basis.col[0], basis.col[1], basis.col[2]
    r0 = (RING_OUT+WELL_GAP)*r
    # The battered wall up to just over the deepest deck it cuts, then plumb and tall, so no lip
    # of deck beyond the footprint is left overhanging the well.
    top = depth+1.0
    rings = [(r0, -.02), (r0+WELL_BATTER*top, top), (r0+WELL_BATTER*top, top+STRUCTURE_RISE+4)]
    verts = []
    for rad, z in rings:
        verts += [tuple(origin+(xa*math.cos(a)+ya*math.sin(a))*rad+nz*z) for a in _angles()]
    n = NSEG
    faces = [tuple(reversed(range(n))), tuple(range(2*n, 3*n))]
    for j in range(2):
        faces += [(j*n+i, j*n+(i+1) % n, (j+1)*n+(i+1) % n, (j+1)*n+i) for i in range(n)]
    lo = [min(v[i] for v in verts) for i in range(3)]
    hi = [max(v[i] for v in verts) for i in range(3)]
    cut = []
    targets = []
    for ev, blo, bhi in _DECK:
        ob = ev.original
        if not (blo[0] <= hi[0] and bhi[0] >= lo[0] and blo[1] <= hi[1] and bhi[1] >= lo[1]):
            continue
        zs = [(ev.matrix_world @ Vector(c)).z for c in ev.bound_box]
        if max(zs) < lo[2] or min(zs) > hi[2]:
            continue
        # A structure standing tall over the ring's edge (a dome's foot, a drum plate) is not
        # deck: notching its foot would leave an arch, so the well stops against it.
        rise = _rise(ev, origin, basis, r)
        if DEBUG:
            print(f'RISE {sid} {ob.name} {rise:.2f} depth {depth:.2f}')
        if rise < .05 or rise > depth+STRUCTURE_RISE:
            continue    # nothing of it over the seat under the ring (the batter would only nick it)
        targets.append(ob)
    for ob in targets:
        before = len(ob.data.vertices)
        for solver in ('MANIFOLD', 'EXACT'):
            me = bpy.data.meshes.new('Temporary well')
            me.from_pydata(verts, [], faces)
            me.update()
            cutter = bpy.data.objects.new(f'Temporary {sid} well cutter', me)
            bpy.context.collection.objects.link(cutter)
            bpy.context.view_layer.objects.active = ob
            mod = ob.modifiers.new('Barbette well', 'BOOLEAN')
            mod.operation, mod.object, mod.solver = 'DIFFERENCE', cutter, solver
            bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)
            ok = True
            try:
                bpy.ops.object.modifier_apply(modifier=mod.name)
            except RuntimeError:
                ok = False
                ob.modifiers.remove(mod)
            bpy.data.objects.remove(cutter, do_unlink=True)
            if ok and _still_above(ob, origin, basis, r):
                ok = False   # the solver left the deck standing in the well: try the other one
            if ok:
                if DEBUG:
                    print(f'SOLVER {sid} {ob.name} {solver}')
                break
        if len(ob.data.vertices) != before:
            cut.append(ob.name)
    return cut


def _rise(ev, origin, basis, r):
    """Highest point of one object over the seat plane across the well's footprint (-1e9: none)."""
    inv = ev.matrix_world.inverted()
    xa, ya, nz = basis.col[0], basis.col[1], basis.col[2]
    best = -1e9
    for rad in (0.0, .5*r, .9*r, RING_OUT*r, (RING_OUT+WELL_GAP)*r):
        for a in (_angles(16) if rad else [0.0]):
            q = origin+(xa*math.cos(a)+ya*math.sin(a))*rad
            ok, p, _, _ = ev.ray_cast(inv @ (q+nz*80), (inv.to_3x3() @ -nz).normalized(), distance=80+SKIRT_MAX)
            if ok:
                best = max(best, 80-((ev.matrix_world @ p)-(q+nz*80)).length)
    return best


def _still_above(ob, origin, basis, r):
    """True if `ob` still stands more than a hair above the seat plane inside the well."""
    bpy.context.view_layer.update()
    ev = ob.evaluated_get(bpy.context.evaluated_depsgraph_get())
    inv = ev.matrix_world.inverted()
    xa, ya, nz = basis.col[0], basis.col[1], basis.col[2]
    for rad in (0.0, .5*r, .95*RING_OUT*r):
        for a in (_angles(8) if rad else [0.0]):
            q = origin+(xa*math.cos(a)+ya*math.sin(a))*rad
            ok, p, _, _ = ev.ray_cast(inv @ (q+nz*40), (inv.to_3x3() @ -nz).normalized(), distance=40-.15)
            if ok:
                return True
    return False


def _collar(kit, sid, mount, origin, basis, r, rim):
    """The fixed barbette ring standing in its well: a bevelled lip RING_TOP over the seat plane,
    its outer wall down into the deck, and where the deck under the rim lies below the seat, one
    straight bevel down to it (a cliff: a short bracket closing in under the lip)."""
    R = RING_OUT*r
    angs = _angles()
    toe, foot = [], []
    for k, a in enumerate(angs):
        d = None if rim[k] is None else -rim[k]       # drop of the deck below the seat at the rim
        if d is not None and d > .05:
            run = FLARE_RUN*d
            d2 = None
            if run <= FLARE_MAX*r:
                h2 = _height(origin, basis, R+run, a)
                d2 = None if h2 is None else -h2
            if d2 is not None and d2 <= d+FLARE_RUN*run*1.2:
                d2 = max(d2, 0.0)
                toe.append((R+FLARE_RUN*d2, -d2))
                foot.append((R+FLARE_RUN*d2, -d2-1.0))
                continue
            toe.append((R*.97, -1.2))                  # a cliff: the bracket
            foot.append((R*.8, -3.0))
            continue
        # The deck is at or above the seat (the well's floor): the wall goes straight down into it.
        h = _height(origin, basis, .95*R, a)
        dd = 1.0 if h is None else max(-h, 0.0)+1.0
        toe.append((R, -.3))
        foot.append((R, -min(dd, SKIRT_MAX)))
    # Smooth the bevel round the ring so one angle never spikes beside its neighbours.
    def smooth(seq):
        out = []
        for k in range(len(seq)):
            nb = [seq[(k+i) % len(seq)] for i in (-1, 0, 1)]
            out.append((.25*nb[0][0]+.5*nb[1][0]+.25*nb[2][0], min(nb[1][1], .25*nb[0][1]+.5*nb[1][1]+.25*nb[2][1])))
        return out
    toe, foot = smooth(toe), smooth(foot)
    low = min(z for _, z in foot)
    n = NSEG
    rings = [[(.3*r, low-1.0)]*n, foot, toe, [(R, 0.0)]*n, [(R, .04*r)]*n, [(R-.07*r, RING_TOP*r)]*n,
             [(.9*r, RING_TOP*r)]*n]
    verts, faces = [], []
    for ring in rings:
        verts += [(rad*math.cos(a), rad*math.sin(a), z) for a, (rad, z) in zip(angs, ring)]
    faces.append(tuple(reversed(range(n))))
    for j in range(len(rings)-1):
        faces += [(j*n+i, j*n+(i+1) % n, (j+1)*n+(i+1) % n, (j+1)*n+i) for i in range(n)]
    faces.append(tuple(range((len(rings)-1)*n, len(rings)*n)))
    ob = kit.mesh(f'Weapon {sid} barbette', verts, faces, kit.plate)
    ob.parent = mount
    return RING_TOP*r


def _envelope(mount, pivot):
    """The trainable parts' lowest height over the seat plane by distance from the training axis
    (bands of 1 unit), sampled along every edge so a long barrel counts along its whole length."""
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    inv = mount.matrix_world.inverted()
    env = {}
    for c in pivot.children:
        if c.type != 'MESH':
            continue
        ev = c.evaluated_get(dg)
        me = ev.to_mesh()
        pts = [inv @ (ev.matrix_world @ v.co) for v in me.vertices]
        for e in me.edges:
            a, b = pts[e.vertices[0]], pts[e.vertices[1]]
            k = max(1, int((a-b).length/.5))
            for i in range(k+1):
                q = a.lerp(b, i/k)
                band = int(math.hypot(q.x, q.y))
                env[band] = min(env.get(band, 1e9), q.z)
        ev.to_mesh_clear()
    return env


def _clear_seat(p, basis, r, angle, arc, seat, env):
    """Raise the seat until every part the turret trains clears the deck within its arc (by
    CLEARANCE). Deck rising more than ARC_STRUCTURE (over r) above the seat is a structure that
    bounds the arc (a dome, the spine, a tier's face), not deck to clear."""
    R = (RING_OUT+WELL_GAP)*r
    need = seat
    for band, z in env.items():
        rho = band+.5
        if rho <= R:
            continue
        span = arc+2*math.degrees(math.atan2(.35*r, rho))    # the arc plus the barrels' own width
        steps = max(2, int(span/6))
        for i in range(steps+1):
            a = math.radians(angle-span/2+span*i/steps)
            h = _height(p, basis, rho, a)
            if h is None or h-seat > max(ARC_STRUCTURE*r, 3.0):
                continue
            if h-z+CLEARANCE > need:
                need, why = h-z+CLEARANCE, (rho, round(math.degrees(a)), round(h-seat, 2), round(z, 2))
    if DEBUG and need > seat:
        print('CLEAR RAISE', round(need-seat, 2), why)
    return need


def build(kit):
    _deck_objects()
    CUTS.clear()
    for slot in SLOTS:
        sid, (x, y), angle, arc, size = slot[:5]
        r = RING[size]
        p, basis = _mount_frame(x, y, r)
        seat, hi, _ = _seat(p, basis, r, angle, arc)
        mount = bpy.data.objects.new(f'Weapon {sid} mount', None)
        mount.empty_display_type, mount.empty_display_size = 'PLAIN_AXES', 2
        bpy.context.collection.objects.link(mount)
        mount.matrix_world = basis.to_4x4()
        mount.location = p+basis.col[2]*seat
        mount['surface_normal'] = [round(v, 4) for v in basis.col[2]]
        pivot = bpy.data.objects.new(f'Weapon {sid} pivot', None)
        pivot.empty_display_type, pivot.empty_display_size = 'ARROWS', 3
        bpy.context.collection.objects.link(pivot)
        pivot.parent = mount
        pivot.location = (0, 0, RING_TOP*r)
        pivot.rotation_euler = (0, 0, math.radians(angle))
        pivot['slot_angle'] = angle
        pivot['arc'] = arc
        bpy.context.view_layer.update()
        weapon = slot[6]
        if weapon == 'typhoon':
            _launcher(kit, slot, pivot, r)
        elif weapon == 'multineedler':
            _needler(kit, slot, pivot, r)
        elif size == 'SMALL':
            _small(kit, slot, pivot, r)
        else:
            _gun(kit, slot, pivot, r)
        # The barrels must clear the deck across the arc: raise the seat as far as that needs.
        seat = _clear_seat(p, basis, r, angle, arc, seat, _envelope(mount, pivot))
        origin = p+basis.col[2]*seat
        mount.location = origin
        mount['seat_depth'] = round(hi-seat, 2)
        cut = _well(kit, sid, origin, basis, r, hi-seat) if hi-seat > .05 else []
        if cut:
            _deck_objects()
        CUTS.append((sid, cut, round(hi-seat, 2)))
        rim = [_height(origin, basis, RING_OUT*r, a) for a in _angles()]
        # After the cut the well's floor reads 0; below-seat deck keeps its drop.
        rim = [None if h is None else (min(h, 0.0) if h < -.05 else 0.0) for h in rim]
        bpy.context.view_layer.update()
        _collar(kit, sid, mount, origin, basis, r, rim)
        PIVOT_Z[sid] = pivot.matrix_world.translation.z
    for sid, cut, depth in CUTS:
        print(f'TURRET WELL {sid}: depth {depth} into {", ".join(cut) or "-"}')
    for slot in HARDPOINTS:
        _hardpoint(kit, slot)


def records():
    out = []
    for sid, loc, angle, arc, size, typ, weapon in SLOTS:
        out.append({'id': sid, 'mount': 'TURRET', 'location': [*loc, round(PIVOT_Z[sid], 2)], 'angle': angle,
                    'arc': arc, 'size': size, 'type': typ, 'weapon': weapon, 'ring_radius': RING[size],
                    'pivot': f'Weapon {sid} pivot', 'mount_empty': f'Weapon {sid} mount', 'objects': f'Weapon {sid} ...',
                    'trains': 'rotate the pivot about its local Z (the hull normal under it); rest is the slot angle'})
    for sid, loc, angle, arc, size, typ, weapon in HARDPOINTS:
        out.append({'id': sid, 'mount': 'HARDPOINT', 'location': [*loc, round(PIVOT_Z[sid], 2)], 'angle': angle,
                    'arc': arc, 'size': size, 'type': typ, 'weapon': weapon, 'objects': f'Weapon {sid} ...',
                    'muzzle': f'Weapon {sid} muzzle', 'trains': 'fixed'})
    for sid, loc, angle, arc, size, typ, weapon in DECORATIVE:
        out.append({'id': sid, 'mount': 'HARDPOINT', 'location': list(loc), 'angle': angle, 'arc': arc, 'size': size,
                    'type': typ, 'weapon': weapon, 'objects': 'Fitting command dome sensor mast'})
    return out
