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


def _hardpoint(kit, slot):
    """A Thermal Pulse Cannon run out through the arm's nose: a fixed armoured
    housing and a long ribbed barrel with its muzzle at the arm's tip."""
    sid, (x, y) = slot[0], slot[1]
    top = seat(kit, x-10, y, 6)
    z = top-3.0
    kit.box(f'Weapon {sid} hardpoint housing', (x-8, y, z), (24, 13, 9), kit.grey, bevel=.4)
    kit.box(f'Weapon {sid} hardpoint mantlet', (x+5, y, z), (3, 9, 7), kit.steel, bevel=.25)
    kit.cylinder(f'Weapon {sid} barrel', (x+13, y, z), 1.9, 18, kit.steel, axis='X', vertices=12)
    for k, bx in enumerate((x+9, x+14, x+19)):
        kit.cylinder(f'Weapon {sid} barrel coil {k+1}', (bx, y, z), 2.6, 1.6, kit.steel, axis='X', vertices=12)
    kit.cylinder(f'Weapon {sid} muzzle collar', (x+22.5, y, z), 2.4, 1.2, kit.plate, axis='X', vertices=12)
    kit.empty(f'Weapon {sid} muzzle', (x+23.2, y, z), slot[2])
    PIVOT_Z[sid] = z


def build(kit):
    _deck_objects()
    for slot in SLOTS:
        sid, (x, y), angle, arc, size = slot[:5]
        r = RING[size]
        deck, low = _footing(x, y, r)
        top = _barbette(kit, sid, x, y, r, max(deck, seat(kit, x, y, r)), low)
        PIVOT_Z[sid] = top
        pivot = kit.empty(f'Weapon {sid} pivot', (x, y, top), angle)
        pivot['slot_angle'] = angle
        pivot['arc'] = arc
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
                    'pivot': f'Weapon {sid} pivot', 'objects': f'Weapon {sid} ...',
                    'trains': 'rotate the pivot about its local Z; rest is the slot angle'})
    for sid, loc, angle, arc, size, typ, weapon in HARDPOINTS:
        out.append({'id': sid, 'mount': 'HARDPOINT', 'location': [*loc, round(PIVOT_Z[sid], 2)], 'angle': angle,
                    'arc': arc, 'size': size, 'type': typ, 'weapon': weapon, 'objects': f'Weapon {sid} ...',
                    'muzzle': f'Weapon {sid} muzzle', 'trains': 'fixed'})
    for sid, loc, angle, arc, size, typ, weapon in DECORATIVE:
        out.append({'id': sid, 'mount': 'HARDPOINT', 'location': list(loc), 'angle': angle, 'arc': arc, 'size': size,
                    'type': typ, 'weapon': weapon, 'objects': 'Fitting command dome sensor mast'})
    return out
