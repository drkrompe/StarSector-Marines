"""Pods: the two tug drive pods (X -125..-30), port authored, starboard mirrored.

The port pod is an octagonal-section maroon casing (Y 41.5..85.5, Z -16..16)
segmented by dark seam grooves, with the pale shoulder band wrapping it
diagonally at X -72..-45, the maroon dorsal drive housing (X -113..-88, up to
Z 27) on its aft top, the worn red aft deck plate sloping down behind it, and
the maroon drive keel and pale propellant tanks below. The hull is Y-symmetric
about Y = +1.5, so every starboard part is the port part mirrored (skin_from +
mirror_u) with its box reflected (y -> 3 - y).

Eight sources, cut by box members along X at the casing's plate joints:
  top    (from above, bow at the top of the tile): five slices of the casing
         top and its inboard upper chamfer, with whatever sits on them: the aft
         deck plate, the drive housing (top, aft and fore slopes), its top
         cover, tail hatch, aft flank cover and pale band, the top service
         cover, the shoulder band's top and the fore access hatch. The outboard
         upper chamfers go to the flank tiles (top box Y < 72).
  flank  (from port, bow to the left): two slices of the outboard flank and
         its upper chamfer (box Z >= -10), with the wall service cover, the aft
         deck plate's outboard edge and the shoulder band's outboard face.
  belly  (from below, bow to the left): the flat casing bottom in one tile
         (cone .9, so the lower chamfers keep their swatch). Its 95-unit run
         caps it at about 7 texels per unit; it is seen only from below.
Chamfers face 45 deg off both the top and flank views; `square` .7 keeps them
on the painted art (1.4x stretch) rather than blending them to the swatch.

Left on swatches / own materials: the bells, liners, thrust frame, turbopumps,
radiators, coaming, handrails, ladders, lamps, vents, RCS quads, rubbing
strakes, cheek armour, fore collar, push knees, couplings, feed lines, tank
straps and pole fittings, the drive keel and its plates and stiffeners, the
propellant tanks (cylinders: one view would cover a third of each), the
casing's inboard flank (two units off the hull wall) and its lower chamfers,
the casing's fore and aft end faces and the drive housing's end and side faces
(its outboard side sits behind the radiator) and its outboard flank cover.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_Z, BOW

SQ = .7
C = 'Drive pod {} casing'
DECK = 'Drive pod {} aft deck plate'
HOUS = 'Drive pod {} drive housing'
HTOP = 'Drive pod {} drive housing top cover'
HTAIL = 'Drive pod {} drive housing tail hatch'
HFC1 = 'Drive pod {} drive housing flank cover 1'
HBAND = 'Drive pod {} drive housing band'
SVC = 'Drive pod {} top service cover'
WALL = 'Drive pod {} wall service cover'
BAND = 'Drive pod {} shoulder band'
FWD = 'Drive pod {} fore access hatch'

# The top's Y < 72 hands every outboard upper chamfer (centres Y 73..83, 45 deg to both
# views) to the flank, so a tie can never split port and starboard differently.
TOP_Y, TOP_Z = (38, 72), (0, 40)       # Z >= 0 leaves out the drive bay floor at the stern
FLK_Y, FLK_Z = (70, 90), (-10, 20)     # Z >= -10 leaves out the lower chamfers
# (part, view, X range, (Y range, Z range), objects); port coordinates. Cuts fall on the
# casing's plates, clear of every face centre (a groove floor centred on a cut would go
# either way): grooves at X -102, -88, -74; plate joints at -116.5, -103.8, -67, -60, -55, -40.3, -33.
# pad (flank only): every face must also lie within the box grown by that much, so the
# long seam grooves running the flank's length (centred in one slice) keep their dark
# swatch rather than stretching the tile. The top's plates span the box's Y bound, so
# the top slices take whole faces by centre.
PORT_PARTS = [
    ('pod-top-1', 'top', (-127, -101), None, (TOP_Y, TOP_Z), [C, DECK, HOUS, HTAIL, HFC1]),
    ('pod-top-2', 'top', (-101, -87), None, (TOP_Y, TOP_Z), [C, HOUS, HTOP, HBAND]),
    ('pod-top-3', 'top', (-87, -66), None, (TOP_Y, TOP_Z), [C, SVC, BAND]),
    ('pod-top-4', 'top', (-66, -50), None, (TOP_Y, TOP_Z), [C, BAND]),
    ('pod-top-5', 'top', (-50, -28), None, (TOP_Y, TOP_Z), [C, FWD]),
    ('pod-flank-1', 'flank', (-112, -77), 3, (FLK_Y, FLK_Z), [C, DECK, WALL]),
    ('pod-flank-2', 'flank', (-77, -28), 3, (FLK_Y, FLK_Z), [C, BAND]),
    ('pod-belly-1', 'belly', (-127, -28), None, ((38, 90), (-20, -5)), [C]),
]
VIEW = {
    # dir, up, cone, square, starboard dir, starboard up (the Y mirror must flip the tile horizontally)
    'top': (UP_Z, BOW, .6, SQ, UP_Z, BOW),
    'flank': (PORT, UP_Z, .7, SQ, STARBOARD, UP_Z),
    'belly': (DOWN_Z, PORT, .9, None, DOWN_Z, STARBOARD),
}
WHAT = {'top': '{s} drive pod top, slice {i} of 5 (X {a}..{b}): casing top and inboard chamfer{extra}',
        'flank': '{s} drive pod outboard flank and upper chamfer, slice {i} of 2 (X {a}..{b}){extra}',
        'belly': '{s} drive pod casing bottom (X {a}..{b})'}
EXTRA = {'pod-top-1': ', aft deck plate, drive housing aft slope, tail hatch and flank covers',
         'pod-top-2': ', drive housing top, fore slope, top cover and band',
         'pod-top-3': ', top service cover', 'pod-top-4': ', shoulder band top', 'pod-top-5': ', fore access hatch',
         'pod-flank-1': ', wall service cover', 'pod-flank-2': ', shoulder band outboard face'}

PARTS = {}
for part, view, (x0, x1), pad, ((y0, y1), (z0, z1)), objs in PORT_PARTS:
    d, up, cone, square, sdir, sup = VIEW[view]
    i = part.rsplit('-', 1)[1]
    extra = EXTRA.get(part, '')
    spec = dict(dir=d, up=up, cone=cone)
    if square is not None:
        spec['square'] = square
    box = ((x0, y0, z0), (x1, y1, z1))
    sbox = ((x0, 3 - y1, z0), (x1, 3 - y0, z1))
    seed = (lambda b: {'box': b}) if pad is None else (lambda b: {'box': b, 'pad': pad})
    PARTS[part + '-port'] = dict(spec, members=[(o.format('port'), seed(box)) for o in objs],
                                 what=WHAT[view].format(s='port', i=i, a=x0, b=x1, extra=extra))
    PARTS[part + '-stbd'] = dict(spec, dir=sdir, up=sup, members=[(o.format('starboard'), seed(sbox)) for o in objs],
                                 skin_from=part + '-port', mirror_u=True,
                                 what=WHAT[view].format(s='starboard', i=i, a=x0, b=x1, extra=extra) + ' (mirror of port)')

SHEETS = {
    'pods-top': ['pod-top-1-port', 'pod-top-2-port', 'pod-top-3-port', 'pod-top-5-port'],
    'pods-side': ['pod-flank-1-port', 'pod-flank-2-port', 'pod-top-4-port', 'pod-belly-1-port'],
    # The one retry: pods-side painted flank 1's worn aft deck plate edge pale grey (5% of
    # that red_w region in family: a colour-layout swap), so flank 1 alone was regenerated
    # asking for it as oxide red. Listed after pods-side, its split overwrites that tile;
    # pods-side's other three tiles stand.
    'pods-flank1-v2': ['pod-flank-1-port'],
}
