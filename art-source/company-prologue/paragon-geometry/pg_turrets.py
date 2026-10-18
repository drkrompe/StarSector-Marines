"""Weapon mounts of the Paragon: 21 turrets on pivots and the two bow hardpoints.

Every slot is read from paragon.ship and placed at its location, resting at its
slot angle; the build fails if the installed file disagrees. A turret is a
fixed barbette (`Weapon <slot> barbette`) and a pivot empty (`Weapon <slot>
pivot`) at the traverse gap, parenting the trainable parts (housing, emitters)
and a `Weapon <slot> muzzle` empty per barrel. Rotate the pivot about its
local Z to train. Hardpoints are fixed: `Weapon <slot> hardpoint` (anchor),
housing, emitter and muzzle.

Tri-Tachyon mounts are energy weapons in smooth domed housings, the emitters
long thin rods with a bright collar at the tip; the four small missile slots
carry squat launcher pods. Barbette radii are read off the sprite's turret
wells (large 14, medium 10.5, small 6.5 units).
"""
import math
from mathutils import Vector
import pg_kit as K
import pg_hull

SLOTS = [  # id, location, angle, arc, size, type, mount
    ('WS 001', (163, 16), 0, 5, 'LARGE', 'ENERGY', 'HARDPOINT'),
    ('WS 002', (163, -16), 0, 5, 'LARGE', 'ENERGY', 'HARDPOINT'),
    ('WS 003', (3, 99), 80, 220, 'LARGE', 'ENERGY', 'TURRET'),
    ('WS 004', (3, -99), -80, 220, 'LARGE', 'ENERGY', 'TURRET'),
    ('WS 005', (126, 40), 20, 210, 'MEDIUM', 'ENERGY', 'TURRET'),
    ('WS 006', (126, -40), -20, 210, 'MEDIUM', 'ENERGY', 'TURRET'),
    ('WS 007', (51, 90), 70, 210, 'MEDIUM', 'UNIVERSAL', 'TURRET'),
    ('WS 008', (51, -90), -70, 210, 'MEDIUM', 'UNIVERSAL', 'TURRET'),
    ('WS 009', (-143, 62), 170, 150, 'MEDIUM', 'ENERGY', 'TURRET'),
    ('WS 010', (-143, -62), 190, 150, 'MEDIUM', 'ENERGY', 'TURRET'),
    ('WS 011', (106, 69), 50, 210, 'SMALL', 'ENERGY', 'TURRET'),
    ('WS 012', (78, 55), 50, 360, 'SMALL', 'MISSILE', 'TURRET'),
    ('WS 013', (-40, 112), 100, 210, 'SMALL', 'ENERGY', 'TURRET'),
    ('WS 014', (-52, 81), 130, 360, 'SMALL', 'MISSILE', 'TURRET'),
    ('WS 015', (-92, 104), 90, 150, 'SMALL', 'ENERGY', 'TURRET'),
    ('WS 016', (-145, 121), 120, 250, 'SMALL', 'ENERGY', 'TURRET'),
    ('WS 017', (106, -69), -40, 210, 'SMALL', 'ENERGY', 'TURRET'),
    ('WS 018', (78, -55), -50, 360, 'SMALL', 'MISSILE', 'TURRET'),
    ('WS 019', (-40, -112), 260, 210, 'SMALL', 'ENERGY', 'TURRET'),
    ('WS 020', (-52, -81), 230, 360, 'SMALL', 'MISSILE', 'TURRET'),
    ('WS 021', (-92, -104), -90, 150, 'SMALL', 'ENERGY', 'TURRET'),
    ('WS 022', (-145, -121), 240, 250, 'SMALL', 'ENERGY', 'TURRET'),
    ('WS 023', (-150, 0), 180, 150, 'SMALL', 'ENERGY', 'TURRET'),
]
# Fittings of the Elite variant, for the record (the model shows the mount, not the weapon).
ELITE = {'WS 001': 'autopulse', 'WS 002': 'autopulse', 'WS 003': 'tachyonlance', 'WS 004': 'tachyonlance',
         'WS 005': 'gravitonbeam', 'WS 006': 'gravitonbeam', 'WS 009': 'gravitonbeam', 'WS 010': 'gravitonbeam',
         'WS 012': 'reaper', 'WS 014': 'reaper', 'WS 018': 'reaper', 'WS 020': 'reaper',
         'WS 013': 'taclaser', 'WS 015': 'taclaser', 'WS 019': 'taclaser', 'WS 021': 'taclaser'}

# size: barbette radius, collar height, housing radius, housing height, emitter length, emitter radius, emitters
SPEC = {
    'LARGE':  dict(r=14.0, collar=2.6, hr=10.5, hh=6.0, length=34.0, er=1.5, n=1),
    'MEDIUM': dict(r=10.5, collar=2.2, hr=7.6, hh=4.6, length=22.0, er=1.1, n=2),
    'SMALL':  dict(r=6.5, collar=1.6, hr=4.6, hh=3.0, length=12.0, er=.7, n=1),
}
RECORDS = []


def housing_profile(hr, hh):
    """A low smooth dome: flat foot, rounded shoulder, flattened crown."""
    prof = [(0, 0)]
    for k in range(9):
        a = math.radians(90*k/8)
        prof.append((hr*(.62+.38*math.cos(a)) if k else hr, hh*math.sin(a)*.92 if k else 0))
    prof.append((0, hh))
    return prof


def barbette(kit, sid, x, y, spec):
    hit = kit.surface(x, y)
    z0 = hit[0].z if hit else 0.0
    r, c = spec['r'], spec['collar']
    prof = [(0, -3.0), (r, -3.0), (r, c*.55), (r*.93, c), (0, c)]
    b = K.revolve(kit, f'Weapon {sid} barbette', prof, kit.slate, center=(x, y, z0), segments=48)
    return b, z0+c


def emitter(kit, name, start, length, radius, mat_tip):
    """A Tri-Tachyon emitter rod along local +X from start: a tapered sleeve, the rod, a bright tip collar."""
    sx, sy, sz = start
    sleeve = kit.cylinder(name+' sleeve', (sx+length*.18, sy, sz), radius*1.9, length*.36, kit.pale, axis='X',
                          vertices=24, bevel=0)
    rod = kit.cylinder(name+' rod', (sx+length*.6, sy, sz), radius, length*.8, kit.steel, axis='X',
                       vertices=16, bevel=0)
    tip = kit.cylinder(name+' tip collar', (sx+length*.97, sy, sz), radius*1.45, length*.06, mat_tip, axis='X',
                       vertices=16, bevel=0)
    return [sleeve, rod, tip], (sx+length, sy, sz)


def parent(objs, pivot):
    for o in objs:
        mw = o.matrix_world.copy()
        o.parent = pivot
        o.matrix_parent_inverse = pivot.matrix_world.inverted()
        o.matrix_world = mw


def turret(kit, sid, loc, angle, arc, size, typ):
    spec = SPEC[size]
    x, y = loc
    base, ztop = barbette(kit, sid, x, y, spec)
    # The pivot sits at the traverse gap on the barbette's top face; trainable
    # parts are built facing +X about the slot centre, then turned to the slot
    # angle with T(x,y) Rz(angle) T(-x,-y) by parenting them to the rotated pivot.
    pivot = kit.empty(f'Weapon {sid} pivot', (x, y, ztop), 0, 'ARROWS', spec['r'])
    pivot['slot'] = sid
    pivot['angle_deg'] = angle
    pivot['arc_deg'] = arc
    pivot['size'] = size
    pivot['type'] = typ
    hr, hh = spec['hr'], spec['hh']
    parts = []
    muzzles = []
    if typ == 'MISSILE':
        house = K.revolve(kit, f'Weapon {sid} housing', housing_profile(hr, hh*.7), kit.pale,
                          center=(x, y, ztop+.05), segments=32)
        pod = kit.box(f'Weapon {sid} launcher pod', (x+.6, y, ztop+hh*.7+1.6), (hr*1.6, hr*1.5, 3.2), kit.skin, .35)
        face = kit.box(f'Weapon {sid} launcher face', (x+.6+hr*.8, y, ztop+hh*.7+1.6), (.3, hr*1.3, 2.6), kit.tech, 0)
        parts += [house, pod, face]
        for j in (-1, 1):
            muzzles.append((x+.6+hr*.8+.2, y+j*hr*.35, ztop+hh*.7+1.6))
    else:
        house = K.revolve(kit, f'Weapon {sid} housing', housing_profile(hr, hh), kit.pale,
                          center=(x, y, ztop+.05), segments=40)
        parts.append(house)
        n = spec['n']
        gap = spec['er']*4.2
        for j in range(n):
            off = (j-(n-1)/2)*gap
            objs, tip = emitter(kit, f'Weapon {sid} emitter {j+1}', (x+hr*.35, y+off, ztop+hh*.55),
                                spec['length'], spec['er'], kit.glow_white)
            parts += objs
            muzzles.append(tip)
    mz = []
    for j, p in enumerate(muzzles):
        e = kit.empty(f'Weapon {sid} muzzle {j+1}' if len(muzzles) > 1 else f'Weapon {sid} muzzle', p, 0, 'SINGLE_ARROW', 3)
        mz.append(e)
    parent(parts+mz, pivot)
    pivot.rotation_euler = (0, 0, math.radians(angle))
    RECORDS.append({'id': sid, 'mount': 'TURRET', 'size': size, 'type': typ, 'location': [x, y],
                    'angle_deg': angle, 'arc_deg': arc, 'pivot': pivot.name, 'pivot_z': round(ztop, 2),
                    'barbette': base.name, 'muzzles': [e.name for e in mz], 'elite_variant': ELITE.get(sid)})
    return pivot


def hardpoint(kit, sid, loc, angle, arc, size, typ):
    """A fixed bow hardpoint: a slot in the prow face with the emitter running out of it."""
    x, y = loc
    z = pg_hull.BAY['z']  # recessed in its bay in the prow (pg_hull.hardpoint_bays)
    anchor = kit.empty(f'Weapon {sid} hardpoint', (x, y, z), angle, 'ARROWS', 6)
    anchor['slot'] = sid
    anchor['angle_deg'] = angle
    anchor['arc_deg'] = arc
    # The capital mount fills its bay: a breech block at the back, a heavy
    # cradle, and a thick emitter whose tip stops just inside the prow face.
    house = kit.box(f'Weapon {sid} housing', (x-23, y, z), (24, 17.0, 17.0), kit.slate, 1.2)
    cradle = kit.box(f'Weapon {sid} cradle', (x-8, y, z), (10, 13.0, 13.0), kit.steel, .8)
    objs, tip = emitter(kit, f'Weapon {sid} emitter', (x-12, y, z), 19.0, 3.2, kit.glow_white)
    objs.append(cradle)
    mz = kit.empty(f'Weapon {sid} muzzle', tip, angle, 'SINGLE_ARROW', 3)
    parent([house]+objs+[mz], anchor)
    RECORDS.append({'id': sid, 'mount': 'HARDPOINT', 'size': size, 'type': typ, 'location': [x, y],
                    'angle_deg': angle, 'arc_deg': arc, 'anchor': anchor.name, 'anchor_z': round(z, 2),
                    'muzzles': [mz.name], 'elite_variant': ELITE.get(sid)})
    return anchor


def build(kit):
    RECORDS.clear()
    for sid, loc, angle, arc, size, typ, mount in SLOTS:
        if mount == 'HARDPOINT':
            hardpoint(kit, sid, loc, angle, arc, size, typ)
        else:
            turret(kit, sid, loc, angle, arc, size, typ)


def records():
    return list(RECORDS)
