"""Tarsus weapon mounts: four small ballistic turrets at tarsus.ship's slots.

WS 001 (-41, 37) 135 and WS 002 (-41, -37) 225 stand on the aft drums;
WS 003 (101, 21) 45 and WS 004 (101, -20) -45 on the arms' bow drums. Each
rests at its slot angle: only the parts above the traverse gap are rotated,
T(x,y) @ Rz(angle) @ T(-x,-y), about the slot centre. The barbette seats
itself on the surface below it."""
import math
from ts_kit import Parts

SLOTS = [
    dict(id='WS 001', location=(-41, 37), angle=135, arc=250, mount='TURRET', size='SMALL', type='BALLISTIC'),
    dict(id='WS 002', location=(-41, -37), angle=225, arc=250, mount='TURRET', size='SMALL', type='BALLISTIC'),
    dict(id='WS 003', location=(101, 21), angle=45, arc=250, mount='TURRET', size='SMALL', type='BALLISTIC'),
    dict(id='WS 004', location=(101, -20), angle=-45, arc=250, mount='TURRET', size='SMALL', type='BALLISTIC'),
]
for i, s in enumerate(SLOTS, 1):
    s['prefix'] = f"Turret {i} {s['id']}"
ANCHOR_Z = {}
MUZZLES = {}

RING_R, BARBETTE_H = 3.6, 1.4


def _rot(p, cx, cy, a):
    x, y, z = p
    c, s = math.cos(a), math.sin(a)
    dx, dy = x-cx, y-cy
    return (cx+c*dx-s*dy, cy+s*dx+c*dy, z)


def _turned(parts, cx, cy, a):
    parts.v = [_rot(p, cx, cy, a) for p in parts.v]
    return parts


def build(kit):
    for s in SLOTS:
        cx, cy = s['location']
        p, _ = kit.surface(cx, cy)
        z0 = p.z
        pre = s['prefix']
        base = Parts().cyl((cx, cy, z0-.6), RING_R, BARBETTE_H+.6, 'Z', 32)
        base.build(kit, f'{pre} barbette', kit.steel, .1)
        zt = z0+BARBETTE_H
        a = math.radians(s['angle'])
        # Above the traverse gap: race, house, mantlet and twin barrels, built facing +X then turned.
        race = Parts().cyl((cx, cy, zt+.15), RING_R-.4, .35, 'Z', 32)
        _turned(race, cx, cy, a).build(kit, f'{pre} traverse race', kit.dark)
        house = Parts().box(cx-2.6, cx+2.2, cy-2.1, cy+2.1, zt+.5, zt+2.6)
        _turned(house, cx, cy, a).build(kit, f'{pre} gun house', kit.grey, .15)
        mant = Parts().box(cx+2.2, cx+2.9, cy-1.5, cy+1.5, zt+.9, zt+2.3)
        _turned(mant, cx, cy, a).build(kit, f'{pre} mantlet', kit.steel, .08)
        bar = Parts()
        for dy in (-.75, .75):
            bar.cyl((cx+2.9, cy+dy, zt+1.6), .38, 6.5, 'X', 12)
        _turned(bar, cx, cy, a).build(kit, f'{pre} barrels', kit.steel)
        piv = kit.empty(f'{pre} pivot', (cx, cy, zt), s['angle'])
        mz = _rot((cx+9.4, cy, zt+1.6), cx, cy, a)
        kit.empty(f'{pre} muzzle', mz, s['angle'])
        ANCHOR_Z[s['id']] = zt
        MUZZLES[s['id']] = tuple(round(v, 2) for v in mz)
