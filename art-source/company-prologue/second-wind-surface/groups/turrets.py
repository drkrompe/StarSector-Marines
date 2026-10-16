"""Turrets: the four naval gun turrets (rollout group, README Pipeline).

Turrets 1, 2 and 3 are one design at three yaws: 1 trains to port (barrel +Y),
2 to starboard (-Y, turret 1 turned 180 deg), 3 to the bow quarter (yaw 30 deg,
0.3 lower, on a taller barbette). Turret 4 is the larger twin-gun mount at yaw
-30 deg. So one sheet carries four sources -- the small turret's top and side,
turret 4's top and side -- and everything else reuses them: the other small
turrets through a camera turned with the turret (`up` follows the barrel, so
the reuse is the same picture, not a mirror), and each turret's far side
through `mirror_u`, since the gun house is symmetric about its barrel.

Members are restricted to what the three small turrets share exactly, so the
reuses' cameras fit the same extent: turret 3's tall barbette sides and the
port/starboard mount platforms (turrets 1 and 2 only) stay on the swatch.
Top tiles include the collar and barbette tops: per-object visibility keeps
the faces the gun house hides, and the visible ring round it takes their art.
"""
from math import cos, sin, radians
from parts_common import UP_Z

# Small-turret design, written in turret 1's frame (centre (-11, 20), barrel +Y).
_C1 = (-11.0, 20.0)
# turret: (centre x, y, yaw from turret 1's frame (deg), z offset)
_SMALL = {1: (-11.0, 20.0, 0.0, 0.0), 2: (-11.0, -20.0, 180.0, 0.0), 3: (41.0, 11.0, -60.0, -0.3)}


def _xf(t, p):
    cx, cy, yaw, dz = _SMALL[t]
    a = radians(yaw)
    dx, dy = p[0] - _C1[0], p[1] - _C1[1]
    return (round(cx + dx * cos(a) - dy * sin(a), 3), round(cy + dx * sin(a) + dy * cos(a), 3), round(p[2] + dz, 3))


def _rot(t, v):
    a = radians(_SMALL[t][2])
    return (round(v[0] * cos(a) - v[1] * sin(a), 6), round(v[0] * sin(a) + v[1] * cos(a), 6), v[2])


# Turret 1 seeds (object suffix, seed point).
_TOP = [('gun house', (-11, 19.9, 20.3)), ('rear bustle', (-11, 16.9, 20.0)), ('roof hatch', (-10.3, 18.8, 20.4)),
        ('optics blister', (-12.35, 19.8, 20.9)), ('mantlet drum', (-11, 22.4, 20.0)), ('mantlet face', (-11, 22.8, 19.8)),
        ('gun recoil sleeve', (-11, 23.85, 19.8)), ('gun breech band', (-11, 24.5, 19.9)),
        ('gun barrel chase', (-11, 26, 19.6)), ('gun barrel tube', (-11, 28.45, 19.5)), ('gun muzzle brake', (-11, 30.1, 19.6)),
        ('armoured collar', (-11, 20, 18.1)), ('barbette foot', (-11, 20, 17.3))]
# Left side (looking aft along the barrel's left, i.e. -X for turret 1).
_LEFT = [('gun house', (-13.2, 19.4, 19.3)), ('rear bustle', (-12.9, 17.2, 19.2)), ('armoured collar', (-13.9, 20.0, 17.6)),
         ('traverse gap', (-13.7, 20.0, 18.2)), ('mantlet drum', (-11.9, 22.3, 19.2)), ('mantlet face', (-11.8, 22.8, 19.2)),
         ('gun recoil sleeve', (-11.6, 23.85, 19.2)), ('gun breech band', (-11.7, 24.5, 19.2)),
         ('gun barrel chase', (-11.4, 26, 19.2)), ('gun barrel tube', (-11.3, 28.45, 19.2)), ('gun muzzle brake', (-11.5, 30.1, 19.2))]


def _mirror_x(p):  # turret 1's left seed -> its right seed (mirror about the barrel axis x = -11)
    return (2 * _C1[0] - p[0], p[1], p[2])


def _small_members(t, rows, right=False):
    return [(f'Turret {t} {o}', _xf(t, _mirror_x(p) if right else p)) for o, p in rows]


PARTS = {}
for _t in (1, 2, 3):
    _b = _rot(_t, (0.0, 1.0, 0.0))                 # barrel direction
    _up_top = (-_b[1], _b[0], 0.0)                 # screen-right = barrel in the top view
    _left = (-_b[1], _b[0], 0.0)
    _right = (_b[1], -_b[0], 0.0)
    _top = dict(members=_small_members(_t, _TOP), dir=UP_Z, up=_up_top, cone=.6, square=.62,
                what=f'turret {_t} from above: gun house roof and glacis, bustle, hatch, optics, mantlet, barrel, collar and barbette tops')
    _side_l = dict(members=_small_members(_t, _LEFT), dir=_left, up=UP_Z, cone=.6,
                   what=f'turret {_t} left side: gun house, bustle, collar, traverse gap, mantlet and barrel')
    _side_r = dict(members=_small_members(_t, _LEFT, right=True), dir=_right, up=UP_Z, cone=.6, mirror_u=True,
                   what=f'turret {_t} right side (mirror of the left)')
    if _t == 1:
        PARTS['turret-small-top'] = _top
        PARTS['turret-small-side'] = _side_l
    else:
        PARTS[f'turret-{_t}-top'] = dict(_top, skin_from='turret-small-top')
        PARTS[f'turret-{_t}-left'] = dict(_side_l, skin_from='turret-small-side')
    PARTS[f'turret-{_t}-right'] = dict(_side_r, skin_from='turret-small-side')

# Turret 4: twin guns, barrel direction (cos -30, sin -30).
_B4 = (cos(radians(-30)), sin(radians(-30)), 0.0)
_L4 = (-_B4[1], _B4[0], 0.0)                         # its left (port-facing) side


def _off(p, k):
    return (round(p[0] + _L4[0] * k, 3), round(p[1] + _L4[1] * k, 3), p[2])


_GUN = [('gun recoil sleeve', {'port': (40.89, -17.55), 'starboard': (39.78, -19.46)}),
        ('gun breech band', {'port': (41.62, -17.97), 'starboard': (40.51, -19.88)}),
        ('gun barrel chase', {'port': (43.67, -19.15), 'starboard': (42.57, -21.07)}),
        ('gun barrel tube', {'port': (47.12, -21.14), 'starboard': (46.02, -23.06)}),
        ('gun muzzle brake', {'port': (49.3, -22.4), 'starboard': (48.2, -24.32)})]
_T4_TOP = [('gun house', (35.8, -15.9, 18.7)), ('rear bustle', (32.1, -13.7, 18.3)), ('roof hatch', (34.19, -16.01, 18.9)),
           ('optics blister', (37.04, -13.67, 19.5)), ('starboard optics blister', (34.51, -18.07, 19.5)),
           ('rangefinder arm', (35.32, -15.61, 19.2)), ('mantlet drum', (38.7, -17.6, 18.4)), ('mantlet face', (39.1, -17.8, 18.1)),
           ('armoured collar', (36.0, -16.0, 15.9)), ('barbette foot', (36.0, -16.0, 14.8))]
_T4_TOP += [(f'{s} {o}', (xy[0], xy[1], 17.9)) for o, d in _GUN for s, xy in d.items()]
_T4_LEFT = [('gun house', (36.7, -13.1, 17.4)), ('rear bustle', (33.7, -11.9, 17.3)), ('armoured collar', (37.9, -12.7, 15.3)),
            ('barbette foot', (38.2, -12.2, 14.4)), ('traverse gap', (37.6, -12.6, 16.0)), ('mantlet drum', (39.7, -15.6, 17.4)),
            ('mantlet face', (40.1, -16.0, 17.4))]
_T4_LEFT += [(f'port {o}', _off((d['port'][0], d['port'][1], 17.36), .35)) for o, d in _GUN]
_T4_RIGHT = [('gun house', (33.8, -18.1, 17.4)), ('rear bustle', (31.3, -16.0, 17.3)), ('armoured collar', (34.1, -19.3, 15.3)),
             ('barbette foot', (33.8, -19.8, 14.4)), ('traverse gap', (34.4, -19.4, 16.0)), ('mantlet drum', (37.5, -19.4, 17.4)),
             ('mantlet face', (38.1, -19.5, 17.4))]
_T4_RIGHT += [(f'starboard {o}', _off((d['starboard'][0], d['starboard'][1], 17.36), -.35)) for o, d in _GUN]

PARTS['turret-4-top'] = dict(members=[(f'Turret 4 {o}', p) for o, p in _T4_TOP], dir=UP_Z, up=(-_B4[1], _B4[0], 0.0),
                             cone=.6, square=.62, what='turret 4 from above: twin-gun house, bustle, rangefinder, optics, guns, collar, barbette')
PARTS['turret-4-side'] = dict(members=[(f'Turret 4 {o}', p) for o, p in _T4_LEFT], dir=_L4, up=UP_Z, cone=.6,
                              what='turret 4 left (port-facing) side: house, bustle, collar, barbette, mantlet and port gun')
PARTS['turret-4-right'] = dict(members=[(f'Turret 4 {o}', p) for o, p in _T4_RIGHT], dir=(-_L4[0], -_L4[1], 0.0), up=UP_Z,
                               cone=.6, skin_from='turret-4-side', mirror_u=True, what='turret 4 right side (mirror of the left)')

SHEETS = {
    'turrets-a': ['turret-small-top', 'turret-4-top', 'turret-small-side', 'turret-4-side'],
}
