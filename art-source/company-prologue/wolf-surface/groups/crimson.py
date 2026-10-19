"""Crimson: the painted armour (the head's cheeks aside, every crimson part).

Two sheets. crimson-tops: the neck deck with its port flank, the main body's
port deck with its wing root, the centre hump aft of the turret, and the aft
body's port deck with the port armour dome. crimson-sides: the main body's,
neck's and aft body's port flanks, and the whole main-body underside.
Starboard parts are the port tiles mirrored; the pirate paint's asymmetries
(the sprite's red patches) are paint the texture keeps, not new geometry.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_Z, DOWN_X

PARTS = {
    'neck-top-port': dict(members=[('Hull neck', (24.0, 8.0, 2.4)), ('Hull neck port flank', (23.5, 9.4, 3.6))],
                          dir=UP_Z, up=DOWN_X, cone=.5, what='neck deck, port half, with its rounded flank'),
    'main-top-port': dict(members=[('Hull main body', (10.0, 13.0, 2.6)), ('Hull wing port root', (0.4, 17.8, 2.5))],
                          dir=UP_Z, up=DOWN_X, cone=.5, what='main body deck, port half, with the wing root'),
    'centre-hump': dict(members=[('Hull centre hump', (-1.6, 0.0, 4.7))], dir=UP_Z, up=DOWN_X, cone=.45,
                        what='the rounded hump aft of the turret'),
    'aft-top-port': dict(members=[('Hull aft body', (-8.0, 8.0, 3.0)), ('Hull port armour dome', (-13.6, 12.4, 5.5))],
                         dir=UP_Z, up=DOWN_X, cone=.5, what='aft body deck, port half, with the armour dome'),
    'main-side-port': dict(members=[('Hull main body', (0.5, 23.4, -2.0))], dir=PORT, up=UP_Z, cone=.6,
                           what='main body and wing root port flank'),
    'neck-side-port': dict(members=[('Hull neck', (24.0, 14.9, -1.5))], dir=PORT, up=UP_Z, cone=.6,
                           what='neck port flank'),
    'aft-side-port': dict(members=[('Hull aft body', (-14.0, 15.6, -1.5))], dir=PORT, up=UP_Z, cone=.6,
                          what='aft body port flank'),
    'main-under': dict(members=[('Hull main body', (5.0, 6.0, -6.8))], dir=DOWN_Z, up=DOWN_X, cone=.6,
                       what='main body underside, both sides'),
}
for p in ('neck-top', 'main-top', 'aft-top', 'main-side', 'neck-side', 'aft-side'):
    src = PARTS[p+'-port']
    members = [(n.replace('port', 'starboard'), (x, -y, z)) for n, (x, y, z) in src['members']]
    PARTS[p+'-stbd'] = dict(members=members, dir=STARBOARD if src['dir'] == PORT else src['dir'], up=src['up'],
                            cone=src['cone'], skin_from=p+'-port', mirror_u=True, what=src['what'].replace('port', 'starboard')+' (mirror)')

SHEETS = {
    'crimson-tops': ['neck-top-port', 'main-top-port', 'centre-hump', 'aft-top-port'],
    'crimson-sides': ['main-side-port', 'neck-side-port', 'aft-side-port', 'main-under'],
}
