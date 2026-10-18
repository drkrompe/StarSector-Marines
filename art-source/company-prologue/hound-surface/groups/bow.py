"""Bow group: the caged bridge capsule on the port bow and the gun fork on the
starboard bow (hd_bridge, hd_fork).

What is visible decides the tiles, not the object list:
- The capsule hull (orange core) is entirely covered: the dark seam liner
  (r 5.3 over the core's 5.2, X 20.6-37.3), the four armour sleeves and the
  aft collar sit on it. A tile there would never be seen, so it stays swatch.
- The capsule nose is the bridge's most visible cream surface. Its glazing
  bands, canopy frames, brow and chin are separate objects over it; the tile
  paints the nose shell under them.
- The cage rings are four identical elliptical frames: one fore-face tile
  serves every ring's fore face (same picture) and, mirrored, every aft face.
  Rims stay on the gunmetal swatch.
- The fork tines are one prism profile at two Y positions: one tile of the
  starboard tine's outboard face serves both tines' starboard-facing walls
  and, mirrored, both port-facing walls.
- The fork base's top is almost all under its bolted armour plates, the gun
  trough and the tines, so its tile is the starboard side wall (aft of the
  tine), the base's one large exposed face.
Chin armour, cradle and tine tops are narrow or hidden: class swatches.
"""
from parts_common import PORT, STARBOARD, UP_Z, BOW, STERN

VERIFY = False   # True: reuse parts render as their own source (mirror / identity check)


def _reuse(src, mirror=False):
    if VERIFY:
        return {}
    return dict(skin_from=src, mirror_u=True) if mirror else dict(skin_from=src)


_RINGS = {1: 21.5, 2: 26.0, 3: 30.0, 4: 34.0}
_RING_SEED_YZ = (9.2, 1.5 + 6.2)   # on the annulus above the capsule axis (CY, CZ + between inner 5.4 and outer 7.1)

PARTS = {
    'bow-fork-tine-side': dict(members=[('Fork starboard tine', (32, -16, 0))], dir=STARBOARD, up=UP_Z, cone=.6,
                               what='starboard gun-fork tine, outboard (starboard) wall: weathered brown plate'),
    'bow-fork-tine-side-port-stbd': dict(members=[('Fork port tine', (32, -3, 0))], dir=STARBOARD, up=UP_Z, cone=.6,
                                         what='port tine, starboard-facing wall (into the gun gap)',
                                         **_reuse('bow-fork-tine-side')),
    'bow-fork-tine-side-stbd-port': dict(members=[('Fork starboard tine', (32, -9, 0))], dir=PORT, up=UP_Z, cone=.6,
                                         what='starboard tine, port-facing wall (into the gun gap)',
                                         **_reuse('bow-fork-tine-side', mirror=True)),
    'bow-fork-tine-side-port-port': dict(members=[('Fork port tine', (32, 3, 0))], dir=PORT, up=UP_Z, cone=.6,
                                         what='port tine, port wall (behind the capsule cage)',
                                         **_reuse('bow-fork-tine-side', mirror=True)),
    'bow-capsule-nose': dict(members=[('Bridge capsule nose', (42.6, 9.2, 1.5))], dir=BOW, up=UP_Z, cone=.3,
                             what='bridge capsule nose shell, cream, seen from ahead'),
    'bow-cage-ring-fore': dict(members=[('Bridge cage ring 4', (_RINGS[4] + .7,) + _RING_SEED_YZ)], dir=BOW, up=UP_Z, cone=.6,
                               what='crash-cage ring 4, forward face: gunmetal elliptical frame'),
    'bow-fork-base-side': dict(members=[('Fork base', (22.5, -16, 0))], dir=STARBOARD, up=UP_Z, cone=.6,
                               what='gun-fork base, starboard side wall aft of the tine: orange armour'),
}
for _k, _x in _RINGS.items():
    if _k != 4:
        PARTS[f'bow-cage-ring-{_k}-fore'] = dict(
            members=[(f'Bridge cage ring {_k}', (_x + .7,) + _RING_SEED_YZ)], dir=BOW, up=UP_Z, cone=.6,
            what=f'crash-cage ring {_k}, forward face', **_reuse('bow-cage-ring-fore'))
    PARTS[f'bow-cage-ring-{_k}-aft'] = dict(
        members=[(f'Bridge cage ring {_k}', (_x - .7,) + _RING_SEED_YZ)], dir=STERN, up=UP_Z, cone=.6,
        what=f'crash-cage ring {_k}, aft face', **_reuse('bow-cage-ring-fore', mirror=True))

SHEETS = {
    'bow': ['bow-fork-tine-side', 'bow-capsule-nose', 'bow-cage-ring-fore', 'bow-fork-base-side'],
}
