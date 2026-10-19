"""Hold: the oxide-red central cargo hold, its hatch cover and its bow face.

`Hull central cargo hold` is one block X -19..90.5, keel Z -50 to deck Z 18;
its sides lie behind the longerons and its open deck mostly under `Hull hold
hatch cover` (X 13..79, top Z 20.5). The hull is Y-symmetric about Y = +1.5,
so every face set that splits cleanly on the centreline is a port source and
a starboard mirror reuse (mirror_u). Boxes cut the multi-plate faces into
tiles; 'pad' keeps a plate that spans the centreline (or the tile boundary)
out of a half tile, so it keeps its swatch rather than stretching the tile.

- Hatch cover: the aft leaves' twelve panels split on the centre seam (port
  source, starboard mirror); the fore leaf and the hinge leaf are single
  polygons across the whole beam, one own tile (single-polygon width caps it
  near 12 texels per unit).
- Bow: the two cargo-door leaves and the armour plates round them, port half
  seen from ahead with the tile turned (outboard up, keel to the right) so the
  tall half-face fills it; starboard mirrored. The thin centre strips above and
  below the door span the centreline and keep the swatch, as does the hold's
  own bow face, which only shows as the seams between the plates.
- Keel: the Z -50 plating with its seam grooves, port half in two tiles along
  X, starboard mirrored. The lift-thruster wells (recessed above Z -49), the
  full-beam bow end plate and the cross seams spanning the beam keep their
  swatch; tiles, skids and runners are other objects.
- Aft face below the waist: one 40-vertex plate across the whole beam with
  its inset panels and grooves; it cannot be cut, so it takes a low density.
- Deck aft of the hatch cover: the two plates at X -19..-4. The big deck
  polygon round the cover is mostly hidden under it and keeps its swatch.

Left to swatch: every chamfer (cones 0.9), the hold sides behind the
longerons, the cover's coaming chamfers and hinge gaps' walls, the bow and aft
keel slopes, the lift wells, and the deck polygon round the cover.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_Z, BOW, STERN

HOLD, COVER = 'Hull central cargo hold', 'Hull hold hatch cover'
DOOR, ARMOUR = 'Hull bow cargo door leaves', 'Hull bow armour plates'

CL = 1.5                       # the centreline; a seam face centred on it belongs to neither half (swatch)
PORT_Y, STBD_Y = (CL + 0.05, 60.0), (-60.0, CL - 0.05)
PAD = 0.5


def _box(x, y, z, pad=PAD):
    return {'box': ((x[0], y[0], z[0]), (x[1], y[1], z[1])), 'pad': pad}


def _pair(name, members_of, d, up, what, ys=(PORT_Y, STBD_Y), **kw):
    """Port source and starboard mirror reuse; members_of(y range) -> members."""
    port = dict(members=members_of(ys[0]), dir=d, up=up, cone=.9, what=f'{what} (port half)', **kw)
    sup = (up[0], -up[1], up[2])
    stbd = dict(members=members_of(ys[1]), dir=d, up=sup, cone=.9, skin_from=f'{name}-port', mirror_u=True,
                what=f'{what} (starboard half, mirror of port)', **kw)
    return {f'{name}-port': port, f'{name}-stbd': stbd}


COVER_Z = (19.9, 21.0)
KEEL_Z = (-50.3, -49.2)        # the plating and its seam grooves; the wells sit higher and stay swatched

PARTS = {}
# The aft leaves' port half is cut once more on the panel seam at Y 18.55 (mirror -15.55): two rows of
# three panels each. The seam gaps and the cross-beam hinge gaps span both and keep the swatch.
PARTS |= _pair('cover-aft-in', lambda y: [(COVER, _box((12.0, 46.05), y, COVER_Z, pad=0.2))], UP_Z, PORT,
               'hatch cover aft leaves from above, outboard up: the inboard row of three panels',
               ys=((CL + 0.05, 18.55), (-15.55, CL - 0.05)))
PARTS |= _pair('cover-aft-out', lambda y: [(COVER, _box((12.0, 46.05), y, COVER_Z, pad=0.2))], UP_Z, PORT,
               'hatch cover aft leaves from above, outboard up: the outboard row of three panels',
               ys=((18.55, 60.0), (-60.0, -15.55)))
PARTS['cover-fore'] = dict(members=[(COVER, _box((46.05, 80.0), (-60.0, 60.0), COVER_Z, pad=None))], dir=UP_Z, up=BOW,
                           cone=.9, what='hatch cover fore leaf and hinge leaf across the whole beam, from above, bow up')
PARTS |= _pair('bow', lambda y: [(DOOR, _box((89.0, 92.0), y, (-60.0, 30.0))),
                                 (ARMOUR, _box((89.0, 92.0), y, (-60.0, 30.0)))], BOW, PORT,
               'bow face from ahead, turned: cargo-door leaf and the armour plates outboard of and above it')
PARTS |= _pair('keel-aft', lambda y: [(HOLD, _box((-20.0, 35.5), y, KEEL_Z, pad=0.2))], DOWN_Z, PORT,
               'keel plating from below, aft half (X -11..35), outboard up')
PARTS |= _pair('keel-fore', lambda y: [(HOLD, _box((35.6, 78.75), y, KEEL_Z, pad=0.2))], DOWN_Z, PORT,
               'keel plating from below, forward half (X 36..79), outboard up')
PARTS['aft-face'] = dict(members=[(HOLD, _box((-19.2, -17.5), (-60.0, 60.0), (-45.0, 16.0), pad=None))], dir=STERN,
                         up=UP_Z, cone=.9, what='hold aft face below the waist, from astern, with its inset panels')
PARTS['deck-aft'] = dict(members=[(HOLD, _box((-20.0, -3.5), (-60.0, 60.0), (16.0, 18.5)))], dir=UP_Z, up=BOW,
                         cone=.9, what='hold deck plates aft of the hatch cover, from above, bow up')

SHEETS = {
    'hold-deck': ['cover-aft-in-port', 'cover-aft-out-port', 'cover-fore', 'bow-port'],
    'hold-keel': ['keel-aft-port', 'keel-fore-port', 'aft-face', 'deck-aft'],
}
