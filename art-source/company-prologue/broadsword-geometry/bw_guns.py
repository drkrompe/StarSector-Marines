"""Broadsword weapons: the two light machine guns in the pod housings and
the anchors for every weapon slot in broadsword.ship.

All five slots are HIDDEN mounts, so the game draws no turret; the sprite
draws the guns. Their barrels run forward out of the housings on the
sprite's barrel column (Y 9..10, centre 9.5) to X 9. The .ship slots are
integer points about a centre line that falls between two pixel columns,
which is why port and starboard read 11 / -10 and 7 / -6: the model is
symmetric at their mean (10.5, 6.5). geometry.json records both.

The variant (broadsword_Fighter) fits lightmg in WS 001/002 and leaves
the missile slots WS 003/004 empty, so those get anchors and no geometry.
"""
from bw_wings import POD_Y, POD
from bw_kit import at

SLOTS = [  # exactly as in broadsword.ship
    {'id': 'WS 001', 'location': (-5, 11), 'angle': 0, 'arc': 5, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 002', 'location': (-5, -10), 'angle': 0, 'arc': 5, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 003', 'location': (-4, 7), 'angle': 0, 'arc': 5, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'MISSILE'},
    {'id': 'WS 004', 'location': (-4, -6), 'angle': 0, 'arc': 5, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'MISSILE'},
    {'id': 'WS 005', 'location': (-6, 0), 'angle': 180, 'arc': 5, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
]
FITTED = {'WS 001': 'lightmg', 'WS 002': 'lightmg'}
GUN_Z = .35
MUZZLE_X = 9.1
ANCHOR_Z = {}
ANCHOR_XY = {}


def symmetric_y(y):
    # 11 / -10 -> +-10.5: the slots sit half a pixel to port of a symmetric pair.
    return y - .5 if y else 0.0


def build(kit):
    for slot in SLOTS[:2]:
        s = 1 if slot['location'][1] > 0 else -1
        n = 'port' if s > 0 else 'starboard'
        sid = slot['id']
        y = s*9.4  # the sprite's barrel column (Y 9..10), on the housing
        top = at(POD, 4.4)[3]
        kit.cylinder(f'Weapon {sid} barrel casing', ((3.8+7.4)/2, y, GUN_Z), .74, 7.4-3.8, kit.steel, 'X', 16)
        kit.cylinder(f'Weapon {sid} barrel', ((7.3+MUZZLE_X-.3)/2, y, GUN_Z), .38, MUZZLE_X-.3-7.3, kit.bright, 'X', 12)
        kit.cylinder(f'Weapon {sid} muzzle brake', (MUZZLE_X-.4, y, GUN_Z), .55, .8, kit.steel, 'X', 12)
        kit.cylinder(f'Weapon {sid} muzzle bore', (MUZZLE_X-.02, y, GUN_Z), .22, .1, kit.dark, 'X', 12, 0)
        kit.box(f'Weapon {sid} breech block', (3.4, y, top+.12), (2.2, 2.3, .55), kit.steel, .1)
        if kit.detail:
            for i, x in enumerate((5.0, 6.4), 1):
                kit.cylinder(f'Weapon {sid} casing band {i}', (x, y, GUN_Z), .86, .3, kit.bright, 'X', 16, .03)
            for i in range(4):
                kit.box(f'Weapon {sid} breech grille slat {i+1}', (2.8+i*.4, y, top+.42), (.16, 1.6, .1), kit.dark, .02)
            kit.box(f'Weapon {sid} breech feed cover', (3.4, y+s*1.2, top+.05), (1.6, .3, .45), kit.dark, .03)
        ANCHOR_Z[sid] = GUN_Z
        ANCHOR_XY[sid] = (slot['location'][0], symmetric_y(slot['location'][1]))
        kit.empty(f'Weapon {sid} anchor', (*ANCHOR_XY[sid], GUN_Z), slot['angle'])
        kit.empty(f'Weapon {sid} muzzle', (MUZZLE_X, y, GUN_Z), slot['angle'], 1.0)
    for slot in SLOTS[2:]:
        sid = slot['id']
        x, y = slot['location']
        ys = symmetric_y(y) if sid != 'WS 005' else 0.0
        z = kit.seat_z(x, ys) - (.4 if sid != 'WS 005' else -.25)
        ANCHOR_Z[sid] = z
        ANCHOR_XY[sid] = (x, ys)
        kit.empty(f'Weapon {sid} anchor', (x, ys, z), slot['angle'])
