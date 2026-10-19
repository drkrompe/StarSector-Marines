"""Body: everything between and behind the prongs.

The central body with the dorsal spine machinery and the bridge dome on it;
the waist decks either side with their bare-metal tanks and pipework; the two
drive blocks with their banded drives, exposed cores, fins and patch plates;
the outrigger pods; the bow fork jaws ahead of the prongs. S10 looks down on
the port waist and drive block and along the spine; the stern quarter sees
the drive blocks' outboard walls.

The hull is built Y-symmetric (fc_hull mirror_y), so every port part is the
source and its starboard twin reuses it mirrored. The central body is one
centred tile; the dome is cut at Y 0 and mirrored.

Left to the class swatches: the drive cans, bells and bands (fc_engines),
the turret kit and launchers, the keel and chines, the dome's steep sides
and every chamfer.
"""
import re

from parts_common import PORT, UP_Z


def _box(x0, x1, y0, y1, z=(-40.0, 60.0)):
    return {'box': ((x0, y0, z[0]), (x1, y1, z[1]))}


def _stbd_member(m):
    o, s = m
    o = re.sub(r'\bport\b', 'starboard', o)
    if s is not None:
        (x0, y0, z0), (x1, y1, z1) = s['box']
        s = {'box': ((x0, -y1, z0), (x1, -y0, z1))}
    return o, s


def _pair(name, members, d, up, what, cone=.6):
    port = dict(members=members, dir=d, up=up, cone=cone, what=f'{what} (port)')
    stbd = dict(members=[_stbd_member(m) for m in members], dir=(d[0], -d[1], d[2]), up=(up[0], -up[1], up[2]),
                cone=cone, skin_from=f'{name}-port', mirror_u=True, what=f'{what} (starboard, mirror of port)')
    return {f'{name}-port': port, f'{name}-starboard': stbd}


DRIVE = ['Hull port drive block', 'Hull port drive block cable bundle', 'Hull port drive block core cradle',
         'Hull port drive block core rings', 'Hull port drive block patch plates', 'Hull port drive block radiator fins',
         'Hull port drive block thrust collars']
WAIST = ['Hull port waist deck', 'Hull port waist tank', 'Hull port waist tank bands', 'Hull port waist tank saddles',
         'Hull port waist pipework']
POD = ['Hull port outrigger pod', 'Hull port outrigger pod plating']
FORK = ['Hull port bow fork inner jaw', 'Hull port bow fork outer jaw', 'Hull port bow fork probes']

PARTS = {}
PARTS['spine'] = dict(
    members=[(o, _box(-50.0, 19.0, -16.0, 16.0)) for o in
             ('Hull central body', 'Hull spine machinery housing', 'Hull spine pipe runs', 'Hull spine radiator fins',
              'Hull stern airlock frame', 'Hull stern airlock door')],
    dir=UP_Z, up=PORT, cone=.6,
    what='grey central body top with the dorsal spine machinery housing, radiator fins and pipe runs, stern airlock')
PARTS |= _pair('dome', [(o, _box(-12.0, 20.0, 0.0, 16.0)) for o in ('Bridge command dome', 'Bridge dome bands', 'Bridge crown mast')],
               UP_Z, PORT, 'grey bridge dome crown with its gunmetal bands and mast')
PARTS |= _pair('waist', [(o, _box(-55.0, 10.0, 8.0, 46.0)) for o in WAIST], UP_Z, PORT,
               'grey waist deck with the bare-metal tank on its saddles, gunmetal tank bands and pipework')
PARTS |= _pair('drive-top', [(o, _box(-82.0, -38.0, 6.0, 50.0)) for o in DRIVE], UP_Z, PORT,
               'gunmetal drive block top: the exposed bare engine core in its grey cradle, red patch plates, fins and cable bundle')
PARTS |= _pair('drive-side', [(o, _box(-82.0, -38.0, 36.0, 60.0)) for o in DRIVE], PORT, UP_Z,
               'gunmetal drive block outboard wall with its thrust collars', cone=.7)
PARTS |= _pair('pod-top', [(o, _box(-42.0, -6.0, 56.0, 82.0)) for o in POD], UP_Z, PORT,
               'gunmetal outrigger pod top under grey plating')
PARTS |= _pair('pod-side', [(o, _box(-42.0, -6.0, 70.0, 90.0)) for o in POD], PORT, UP_Z,
               'gunmetal outrigger pod outboard face with grey plating', cone=.7)
PARTS |= _pair('fork', [(o, _box(100.0, 140.0, 0.0, 80.0)) for o in FORK], UP_Z, PORT,
               'khaki bow fork jaws ahead of the prong with gunmetal probes')

SHEETS = {
    'body-centre': ['spine', 'dome-port', 'waist-port', 'fork-port'],
    'body-aft': ['drive-top-port', 'drive-side-port', 'pod-top-port', 'pod-side-port'],
}
