"""Top: the dorsal surfaces of the central body.

The two orange glacis bands that run diagonally out from the prow collar to the
waist, the dark grey glacis bands inboard of them with their blue-steel and
light-grey panels, the orange prow shoulders, the stepped prow gallery deck,
the brown core deck round the citadel, the command dome (citadel), the aft dome
on the spine and the spine forward of the citadel.

Every port part is the source and its starboard twin reuses it mirrored
(mirror_u): the hull is built Y-symmetric. Centreline objects (gallery, domes,
spine, core) are cut at Y = 0 by box so each half is a mirror of the other; the
spine forward of the citadel is a single centred tile. The only asymmetry is the
turret aprons' plate tones (blue steel and dark brown change sides), small
plates that take the mirrored art.

A tile spans at most about 45 x 25.6 units of surface at 15 texels per unit, so
the long diagonal bands are cut by box along X, each seen square-on to its own
slope with the band running along the tile. The grey band's sloped face and its
flat top are seen from their bisector, so both stay inside the pure-skin cone.

Left to the class swatches: the waist trench machinery strip, the gallery's
forward ramp faces and cheeks, the citadel drum walls and glazing frames, the
neck vents, fittings, pipe bundles and every chamfer.
"""
import re
from math import sqrt

from parts_common import PORT, STARBOARD, UP_Z, BOW


def _n(v):
    s = sqrt(sum(c * c for c in v))
    return tuple(c / s for c in v)


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _slope(normal, along):
    """(dir, up) seeing a sloped band square-on with `along` running across the tile."""
    d = _n(normal)
    t = _n(along)
    return d, _n(_cross(t, d))


def _stbd(v):
    return (v[0], -v[1], v[2])


# Orange glacis band: faces (0.25, 0.65, 0.7), running aft-outboard (-80, +33).
GLACIS_DIR, GLACIS_UP = _slope((0.25, 0.65, 0.7), (0.925, -0.38, 0.0))
# Grey glacis band: bisector of its slope (0.3, 0.65, 0.7) and its flat top.
GREY_DIR, GREY_UP = _slope((0.16, 0.35, 0.92), (0.925, -0.42, 0.0))
# Prow shoulders: faces (0.1, 0.35, 0.93), running forward-inboard (94, -30).
PROW_DIR, PROW_UP = _slope((0.1, 0.35, 0.93), (0.953, -0.304, 0.0))

PORT_Y, STBD_Y = (0.0, 200.0), (-200.0, 0.0)


def _box(x0, x1, y=PORT_Y, z=(-100.0, 200.0)):
    return {'box': ((x0, y[0], z[0]), (x1, y[1], z[1]))}


def _members(names, box):
    return [(n, box) for n in names]


def _mirror_names(names):
    return [re.sub(r'\bport\b', 'starboard', n) for n in names]


GLACIS_ORANGE = ['Detail port glacis band panels orange', 'Detail port glacis band panels dirty orange',
                 'Detail port glacis band seams', 'Detail port glacis bolt rows', 'Detail glacis access panels']
GLACIS_GREY = ['Armour port glacis grey band', 'Detail port glacis grey panels blue steel', 'Detail port glacis grey panels grey',
               'Detail port glacis grey panels light grey', 'Detail port glacis grey seams', 'Detail port glacis grey bolt rows',
               'Detail glacis hatches', 'Detail glacis hatch handles']
PROW = ['Armour port prow shoulder 1', 'Armour port prow shoulder 2', 'Armour port prow shoulder 3',
        'Detail port prow shoulder panels orange', 'Detail port prow shoulder panels dirty orange', 'Detail port prow shoulder seams',
        'Detail port prow bolt rows', 'Detail prow vents', 'Detail port prow nose panels bone brown',
        'Detail port prow nose panels dark brown', 'Detail port prow nose seams', 'Detail prow nose panels blue steel',
        'Detail prow nose seams']
GALLERY = ['Detail port prow gallery deck panels brown', 'Detail port prow gallery deck panels bone brown',
           'Detail port prow gallery deck panels dark brown', 'Detail port prow gallery deck seams', 'Detail prow deck straps',
           'Detail prow fittings', 'Detail ws009 apron panels dark brown', 'Detail ws009 apron seams',
           'Detail ws019 apron panels blue steel', 'Detail ws019 apron panels dark brown', 'Detail ws019 apron seams']
CORE = ['Detail port core deck panels brown', 'Detail port core deck panels bone brown', 'Detail port core deck panels dark brown',
        'Detail port core deck seams', 'Detail port core shoulder panels brown', 'Detail port core shoulder panels bone brown',
        'Detail port core shoulder panels dark brown', 'Detail port core shoulder seams', 'Detail core vents',
        'Detail ws012 apron panels dark brown', 'Detail ws012 apron seams', 'Detail ws014 apron panels blue steel',
        'Detail ws014 apron panels dark brown', 'Detail ws014 apron seams', 'Detail ws022 apron panels blue steel',
        'Detail ws022 apron panels dark brown', 'Detail ws022 apron seams']
DOME = ['Bridge command dome', 'Bridge command dome ribs', 'Detail citadel dome panels bone', 'Detail citadel dome panels bone grey',
        'Detail citadel dome seams', 'Detail citadel comm boxes', 'Bridge citadel riser band', 'Bridge citadel cheek port',
        'Bridge citadel sensor crown', 'Bridge citadel sensor blister port', 'Bridge command dome hatch rim',
        'Bridge command brow visor', 'Bridge command citadel bustle']
AFT_DOME = ['Bridge aft dome', 'Bridge aft dome ribs', 'Bridge aft dome ledge hatch 1', 'Bridge aft dome ledge hatch 2',
            'Bridge aft dome ledge hatch 3', 'Bridge aft dome ledge hatch 4', 'Bridge aft dome ledge hatch 5']
SPINE_FORE = ['Bridge spine', 'Bridge spine chevron 1', 'Bridge spine chevron 2', 'Bridge spine chevron 3', 'Bridge spine arch',
              'Bridge spine side step port fore', 'Bridge spine side step starboard fore', 'Bridge forward pod',
              'Bridge forward pod recess']


def _pair(name, names, x0, x1, d, up, what, mirror_objects=True, y=PORT_Y, z=(-100.0, 200.0)):
    """A port source part and its starboard mirror reuse."""
    port = dict(members=_members(names, _box(x0, x1, y, z)), dir=d, up=up, cone=.6, what=f'{what} (port)')
    sy = (-y[1], -y[0])
    snames = _mirror_names(names) if mirror_objects else names
    stbd = dict(members=_members(snames, _box(x0, x1, sy, z)), dir=_stbd(d), up=_stbd(up), cone=.6,
                skin_from=f'{name}-port', mirror_u=True, what=f'{what} (starboard, mirror of port)')
    return {f'{name}-port': port, f'{name}-stbd': stbd}


PARTS = {}
PARTS |= _pair('glacis-orange-aft', GLACIS_ORANGE, -5, 40, GLACIS_DIR, GLACIS_UP, 'orange glacis band, aft third, with its orange and weathered-orange plates')
PARTS |= _pair('glacis-orange-mid', GLACIS_ORANGE, 40, 72, GLACIS_DIR, GLACIS_UP, 'orange glacis band, middle third')
PARTS |= _pair('glacis-orange-fore', GLACIS_ORANGE, 72, 112, GLACIS_DIR, GLACIS_UP, 'orange glacis band, forward third')
PARTS |= _pair('glacis-grey-aft', GLACIS_GREY, 15, 50, GREY_DIR, GREY_UP, 'dark grey glacis band, aft third, with blue-steel and light grey panels and hatches')
PARTS |= _pair('glacis-grey-mid', GLACIS_GREY, 50, 80, GREY_DIR, GREY_UP, 'dark grey glacis band, middle third')
PARTS |= _pair('glacis-grey-fore', GLACIS_GREY, 80, 112, GREY_DIR, GREY_UP, 'dark grey glacis band, forward third and its flat top')
PARTS |= _pair('prow-shoulder-aft', PROW, 105, 145, PROW_DIR, PROW_UP, 'orange prow shoulder armour, aft third')
PARTS |= _pair('prow-shoulder-mid', PROW, 145, 180, PROW_DIR, PROW_UP, 'orange prow shoulder armour, middle third')
PARTS |= _pair('prow-shoulder-fore', PROW, 180, 203, PROW_DIR, PROW_UP, 'orange prow shoulder armour, forward third')
PARTS |= _pair('gallery-aft', GALLERY, 85, 133, UP_Z, PORT, 'prow gallery deck plates, aft half')
PARTS |= _pair('core-mid', CORE, 8, 30, UP_Z, BOW, 'brown core deck plates and turret aprons beside the citadel', y=(0.0, 52.0))
PARTS |= _pair('dome', DOME, 0, 48, UP_Z, PORT, 'bone command dome (citadel) top with its panels and ribs', y=(3.0, 60.0))
PARTS |= _pair('aft-dome', AFT_DOME, -70, -12, UP_Z, PORT, 'bone aft dome with its ribs and ledge hatches', y=(1.0, 60.0))
# The aft dome's ledge hatches are numbered round the dome: 1-5 port, 10-6 their starboard twins.
PARTS['aft-dome-stbd']['members'] = [(n if 'ledge hatch' not in n else f"Bridge aft dome ledge hatch {11 - int(n.split()[-1])}", b)
                                     for n, b in PARTS['aft-dome-stbd']['members']]
# Not mirrors: the starboard core deck and the starboard grey band's aft panels are laid out differently
# from port (flipped-mask IoU 0.57 and 0.84), so each takes its own tile.
for _p in ('core-mid-stbd', 'glacis-grey-aft-stbd'):
    del PARTS[_p]['skin_from'], PARTS[_p]['mirror_u']
    PARTS[_p]['what'] = PARTS[_p]['what'].replace(', mirror of port', ', own tile')
PARTS['spine-fore'] = dict(members=_members(SPINE_FORE, _box(58.5, 93, (-30.0, 30.0))), dir=UP_Z, up=PORT, cone=.6,
                           what='bone spine forward of the citadel with its chevrons, forward pod and side steps')

SHEETS = {
    # v2 of both sheets: v1 painted the grey bands' pale-steel inset panels a warm off-white (L 65 against the
    # palette's 51), which grade.py read as bone, a colour-layout swap; the v2 prompts pin them to a neutral mid grey.
    'top-glacis-v2': ['glacis-orange-aft-port', 'glacis-orange-mid-port', 'glacis-orange-fore-port', 'glacis-grey-aft-port'],
    'top-prow-v2': ['glacis-grey-mid-port', 'glacis-grey-fore-port', 'prow-shoulder-aft-port', 'prow-shoulder-mid-port'],
    'top-core': ['prow-shoulder-fore-port', 'gallery-aft-port', 'core-mid-port', 'core-mid-stbd'],
    'top-bridge': ['dome-port', 'aft-dome-port', 'spine-fore', 'glacis-grey-aft-stbd'],
}
