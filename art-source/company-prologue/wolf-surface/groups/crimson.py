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
    'neck-top-port': dict(members=[('Hull neck', (24.0, 4.0, 7.5))],
                          dir=UP_Z, up=DOWN_X, cone=.5, what='neck deck, port half, '),
    'main-top-port': dict(members=[('Hull main body', (8.0, 6.0, 8.8))],
                          dir=UP_Z, up=DOWN_X, cone=.5, what='main body deck, port half, '),
    'aft-top-port': dict(members=[('Hull aft body', (-10.0, 5.0, 7.6)), ('Hull port armour dome', (-13.6, 12.4, 7.0))],
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
# The lofted top is one connected crowned surface: the port top tiles span both halves.
for p in ('main-side', 'neck-side', 'aft-side'):
    src = PARTS[p+'-port']
    members = [(n.replace('port', 'starboard'), (x, -y, z)) for n, (x, y, z) in src['members']]
    PARTS[p+'-stbd'] = dict(members=members, dir=STARBOARD if src['dir'] == PORT else src['dir'], up=src['up'],
                            cone=src['cone'], skin_from=p+'-port', mirror_u=True, what=src['what'].replace('port', 'starboard')+' (mirror)')

SHEETS = {
    'crimson-tops': ['neck-top-port', 'main-top-port', 'aft-top-port', 'main-under'],
    'crimson-sides': ['main-side-port', 'neck-side-port', 'aft-side-port'],
}

# Round 8 (the accepted underside): the four landing wedges, stowed flat on the
# lower hull sides (seen from below and outboard), and the undersides of the
# aft body and neck. Starboard wedges mirror the port tiles.
LOWER_PORT = (0.0, 0.8, -0.6)
LOWER_STBD = (0.0, -0.8, -0.6)
PARTS.update({
    'wedge-fore-port': dict(members=[('Hull port fore wedge', (19.5, 17.0, -6.5))], dir=LOWER_PORT, up=UP_Z, cone=.5,
                            what='the port fore landing wedge stowed on the lower hull side, its outer face'),
    'wedge-aft-port': dict(members=[('Hull port aft wedge', (-13.0, 17.8, -7.5))], dir=LOWER_PORT, up=UP_Z, cone=.5,
                           what='the port aft landing wedge stowed on the lower hull side, its outer face'),
    'aft-under': dict(members=[('Hull aft body', (-12.0, 6.0, -8.0))], dir=DOWN_Z, up=DOWN_X, cone=.6,
                      what='aft body underside, both sides'),
    'neck-under': dict(members=[('Hull neck', (24.0, 6.0, -8.0))], dir=DOWN_Z, up=DOWN_X, cone=.6,
                       what='neck underside, both sides'),
})
for p in ('wedge-fore', 'wedge-aft'):
    src = PARTS[p+'-port']
    PARTS[p+'-stbd'] = dict(members=[(n.replace('port', 'starboard'), (x, -y, z)) for n, (x, y, z) in src['members']],
                            dir=LOWER_STBD, up=src['up'], cone=src['cone'], skin_from=p+'-port', mirror_u=True,
                            what=src['what'].replace('port', 'starboard')+' (mirror)')
SHEETS['crimson-belly'] = ['wedge-fore-port', 'wedge-aft-port', 'aft-under', 'neck-under']
