"""The turrets and hardpoints at onslaught.ship's weapon slots.

Every turret is a fixed barbette seated on the hull with `surface`, and a
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


def seat(kit, x, y, r):
    """Highest hull point under the ring (centre and eight points round it)."""
    best = None
    for k in range(9):
        px, py = (x, y) if k == 8 else (x+.8*r*math.cos(k*math.pi/4), y+.8*r*math.sin(k*math.pi/4))
        hit = kit.surface(px, py, top=120)
        if hit and (best is None or hit[0].z > best):
            best = hit[0].z
    return best if best is not None else 10.0


_DECK = []


def _deck_objects():
    """Every mesh built before the turrets that a barbette could stand on or in:
    hull, armour bands, bridge parts, machinery. Weapons, lights and fittings excluded."""
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    _DECK.clear()
    for ob in bpy.context.scene.objects:
        if ob.type != 'MESH' or ob.name.split(' ')[0] in ('Weapon', 'Light', 'Fitting'):
            continue
        ev = ob.evaluated_get(dg)
        pts = [ev.matrix_world @ Vector(c) for c in ev.bound_box]
        lo = [min(p[i] for p in pts) for i in range(2)]
        hi = [max(p[i] for p in pts) for i in range(2)]
        _DECK.append((ev, lo, hi))


def _footing(x, y, r):
    """(top, low): the highest and lowest deck points under a barbette's footprint, sampled at
    the centre and round .8 of the ring radius (where seat() samples). The barbette stands on the highest, so an
    armour band crossing the ring passes under it rather than through it, and its foot
    reaches the lowest, so a lifted barbette does not float over the deck beside the band."""
    top = low = None
    for rr in (0.0, .8*r):
        for k in range(1 if rr == 0 else 12):
            px, py = x+rr*math.cos(k*math.pi/6), y+rr*math.sin(k*math.pi/6)
            best = None
            for ev, lo, hi in _DECK:
                if not (lo[0] <= px <= hi[0] and lo[1] <= py <= hi[1]):
                    continue
                inv = ev.matrix_world.inverted()
                ok, p, _, _ = ev.ray_cast(inv @ Vector((px, py, 150)), (inv.to_3x3() @ Vector((0, 0, -1))).normalized())
                if ok:
                    z = (ev.matrix_world @ p).z
                    best = z if best is None else max(best, z)
            if best is not None:
                top = best if top is None else max(top, best)
                low = best if low is None else min(low, best)
    return (top, low) if top is not None else (10.0, 10.0)


def _barbette(kit, sid, x, y, r, top, low=None):
    """Fixed barbette: a flared foot sunk into the deck, a drum and a chamfered lip."""
    h = .32*r
    foot = top-2.0 if low is None else max(min(top-2.0, low-.5), top-8.0)
    prof = [(1.18*r, foot), (1.18*r, top+.08*r), (1.02*r, top+.18*r), (1.0*r, top+h-.06*r), (.94*r, top+h)]
    verts, faces = [], []
    n = 16
    for rad, z in prof:
        verts += [(x+rad*math.cos(2*math.pi*(i+.5)/n), y+rad*math.sin(2*math.pi*(i+.5)/n), z) for i in range(n)]
    faces.append(tuple(reversed(range(n))))
    for j in range(len(prof)-1):
        faces += [(j*n+i, j*n+(i+1) % n, (j+1)*n+(i+1) % n, (j+1)*n+i) for i in range(n)]
    faces.append(tuple(range((len(prof)-1)*n, len(prof)*n)))
    kit.mesh(f'Weapon {sid} barbette', verts, faces, kit.plate)
    return top+h


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
    _cyl(kit, f'Weapon {sid} launcher deck', pivot, (0, 0, z0+.07*r), .9*r, .14*r, kit.plate, axis='Z', vertices=16)
    deck = z0+.14*r
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


def _hit(origin, direction, far=400.0):
    """Nearest hit of a ray against the deck objects: (point, normal) or None."""
    o, d = Vector(origin), Vector(direction).normalized()
    best = None
    for ev, lo, hi in _DECK:
        inv = ev.matrix_world.inverted()
        ok, p, n, _ = ev.ray_cast(inv @ o, (inv.to_3x3() @ d).normalized(), distance=far)
        if ok:
            p = ev.matrix_world @ p
            dist = (p-o).length
            if best is None or dist < best[0]:
                n = (ev.matrix_world.to_3x3().inverted().transposed() @ n).normalized()
                best = (dist, p, n if n.dot(d) < 0 else -n)
    return best


def _mount_frame(x, y, angle, r):
    """Where a turret meets the hull: the surface point under its slot, the normal
    there (leaning at most MAX_TILT), and how far its collar must reach down so no
    rim point floats and none is buried. Returns (origin, basis 3x3, depth)."""
    from mathutils import Matrix
    h = _hit((x, y, 150), (0, 0, -1))
    if h is None:
        p, n = Vector((x, y, 10.0)), Vector((0, 0, 1))
    else:
        _, p, n = h
    up = Vector((0, 0, 1))
    tilt = math.degrees(n.angle(up))
    if tilt > MAX_TILT:
        n = up.slerp(n, MAX_TILT/tilt).normalized()
    xa = Vector((1, 0, 0))
    xa = (xa-n*xa.dot(n)).normalized()
    ya = n.cross(xa)
    basis = Matrix((xa, ya, n)).transposed()
    # Surface height above the mount plane under the collar's rim.
    rim = []
    for k in range(12):
        a = 2*math.pi*k/12
        q = p+(xa*math.cos(a)+ya*math.sin(a))*1.05*r
        hh = _hit(q+n*30, -n, 80)
        rim.append(30-hh[0] if hh else -6.0)
    lift = max(0.0, max(rim))+.15
    depth = lift-min(rim)+.8
    return p+n*lift, basis, depth


SKIRT_FLARE = 1.0    # outward run of a skirt per unit of drop (45 deg from the deck)
SKIRT_MAX = 14.0     # a deeper drop is a cliff (a well, a tier's face): the skirt drops straight


def _skirt(origin, basis, r, n=16):
    """Per-angle foot of a barbette's skirt, in the mount's frame: (radius, z). Where the hull
    falls away from the rim the skirt flares down to it as a sloped armour apron, so a mount on
    a tier's edge is carried by an emplacement rather than standing on a drum. The
    flare is smoothed round the ring so neighbouring angles never alternate into spikes.
    Where even the flare finds no hull (a cliff), the collar is undercut back into the hull."""
    xa, ya, nz = basis.col[0], basis.col[1], basis.col[2]
    r0 = 1.06*r

    def drop(rad, a):
        q = origin+(xa*math.cos(a)+ya*math.sin(a))*rad
        hh = _hit(q+nz*30, -nz, 30+SKIRT_MAX+6)
        return None if hh is None else hh[0]-30

    angles = [2*math.pi*(k+.5)/n for k in range(n)]
    d0 = [drop(r0, a) for a in angles]
    ext = []
    for a, d in zip(angles, d0):
        if d is None or d > SKIRT_MAX or d <= 1.5:
            ext.append(0.0)
            continue
        e = SKIRT_FLARE*d
        d2 = drop(r0+e, a)                      # the hull may fall further under the flare
        if d2 is not None and d2 <= SKIRT_MAX:
            e = SKIRT_FLARE*max(d, d2)
        ext.append(e)
    w = (.1, .2, .4, .2, .1)                    # circular smoothing of the flare
    ext = [sum(w[j]*ext[(k+j-2) % n] for j in range(5)) for k in range(n)]
    feet = []
    for k, a in enumerate(angles):
        rad = r0+ext[k]
        d = drop(rad, a) if ext[k] > .3 else d0[k]
        if d is None or d > SKIRT_MAX:
            # Past the flare the hull falls away (a well, a gallery's side): undercut the
            # collar back into the hull like a sponson's bracket instead of hanging a cup.
            d = min(d0[k] if d0[k] is not None else SKIRT_MAX, SKIRT_MAX)
            rad = .6*r if d > 1.5 else r0
        feet.append((rad, -(d+.8)))
    zs = [sum(w[j]*feet[(k+j-2) % n][1] for j in range(5)) for k in range(n)]
    return [(rad, min(z, zk)) for (rad, z), zk in zip(feet, zs)]     # smoothed, never lifted off the hull


def _collar(kit, sid, mount, r, depth, feet=None):
    """The fixed barbette: a low collar against the hull; where the hull falls away under its
    rim the skirt runs down to it (see _skirt). Built in the mount's frame."""
    n = len(feet) if feet else 18
    if not feet:
        feet = [(1.06*r, -depth)]*n
    rings = [[(.25*r, min(z for _, z in feet)-1.0)]*n, feet, [(1.06*r, min(-.5, z+.3) if rad > 1.07*r else -.5)
                                                             for rad, z in feet],
             [(1.06*r, .05*r)]*n, [(1.0*r, .12*r)]*n, [(.93*r, .16*r)]*n]
    verts, faces = [], []
    for ring in rings:
        verts += [(rad*math.cos(2*math.pi*(i+.5)/n), rad*math.sin(2*math.pi*(i+.5)/n), z)
                  for i, (rad, z) in enumerate(ring)]
    faces.append(tuple(reversed(range(n))))
    for j in range(len(rings)-1):
        faces += [(j*n+i, j*n+(i+1) % n, (j+1)*n+(i+1) % n, (j+1)*n+i) for i in range(n)]
    faces.append(tuple(range((len(rings)-1)*n, len(rings)*n)))
    ob = kit.mesh(f'Weapon {sid} barbette', verts, faces, kit.plate)
    ob.parent = mount
    return .16*r


def build(kit):
    _deck_objects()
    for slot in SLOTS:
        sid, (x, y), angle, arc, size = slot[:5]
        r = RING[size]
        origin, basis, depth = _mount_frame(x, y, angle, r)
        mount = bpy.data.objects.new(f'Weapon {sid} mount', None)
        mount.empty_display_type, mount.empty_display_size = 'PLAIN_AXES', 2
        bpy.context.collection.objects.link(mount)
        mount.matrix_world = basis.to_4x4()
        mount.location = origin
        mount['surface_normal'] = [round(v, 4) for v in basis.col[2]]
        top = _collar(kit, sid, mount, r, depth, _skirt(origin, basis, r))
        pivot = bpy.data.objects.new(f'Weapon {sid} pivot', None)
        pivot.empty_display_type, pivot.empty_display_size = 'ARROWS', 3
        bpy.context.collection.objects.link(pivot)
        pivot.parent = mount
        pivot.location = (0, 0, top)
        pivot.rotation_euler = (0, 0, math.radians(angle))
        pivot['slot_angle'] = angle
        pivot['arc'] = arc
        bpy.context.view_layer.update()
        PIVOT_Z[sid] = pivot.matrix_world.translation.z
        weapon = slot[6]
        if weapon == 'typhoon':
            _launcher(kit, slot, pivot, r)
        elif weapon == 'multineedler':
            _needler(kit, slot, pivot, r)
        elif size == 'SMALL':
            _small(kit, slot, pivot, r)
        else:
            _gun(kit, slot, pivot, r)
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
