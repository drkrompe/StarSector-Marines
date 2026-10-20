"""Wings: the wing body's exposed deck, the wing armour plates, the leading-edge
glacis bands (on the launcher bay objects) and the wing-tip gun nacelles.

The wing body is one object across both sides, mostly roofed by the core: its
tile is the exposed outboard deck (|Y| > 24), port authored, starboard mirrored.
The pods sheet also carries the bow head deck (bow.py has the horns). The armour,
bands and nacelles are port-authored with starboard mirrors.
"""
from math import sqrt
from parts_common import UP_Z, DOWN_X, BOW, PORT
from groups._mirror import SEAMS, box, pair

GLACIS = (2/sqrt(5), 0.0, 1/sqrt(5))   # the bands' forward rake (normal +X, half up)

PARTS = {
    'head-top': dict(members=[('Hull bow head', {'box': box()}), ('Hull bow crossbar', {'box': box()}),
                              ('Hull neck', {'box': box()})], dir=UP_Z, up=DOWN_X, cone=.6, seam_walls=SEAMS,
                     what='the bow head deck round the bow turret ring, the crossbar and the neck from above'),
}
pair(PARTS, 'wing-top', [('Hull wing body', box(y=(24, 75)))], UP_Z, DOWN_X, .6,
     'wing body exposed outboard deck under the open framework')
pair(PARTS, 'wing-armour', [('Hull wing {s} armour plate 1', box())], UP_Z, DOWN_X, .5,
     'wing armour plate from above: the bolted course behind the glacis band')
pair(PARTS, 'band', [('Hull {s} launcher bay', box())], GLACIS, UP_Z, .75,
     'leading-edge glacis band seen from ahead and above, the Harpoon bay in its back')
pair(PARTS, 'pod-top', [('Hull {s} turret pod', box())], UP_Z, DOWN_X, .6,
     'gun nacelle deck from above: the armour coaming round the turret deck')
pair(PARTS, 'pod-out', [('Hull {s} turret pod', box())], PORT, UP_Z, .6,
     'gun nacelle outboard wall, red armour in a bolted course')
pair(PARTS, 'pod-nose', [('Hull {s} turret pod', box())], BOW, UP_Z, .6,
     'gun nacelle blunt chamfered nose from ahead')

SHEETS = {
    'wings': ['wing-top-port', 'wing-armour-port', 'band-port', 'core-flank-aft-port'],
    'pods': ['pod-top-port', 'pod-out-port', 'pod-nose-port', 'head-top'],
    # Retry: the first generation drew dark seam lines over the guide's thin blue plate gaps
    # (IoU 0.956-0.969); listed after the first, its split overwrites only these tiles.
    'wings-v2': ['wing-armour-port', 'band-port'],
    # Retry: the first generation drew dark seam lines over the guide's thin blue plate gaps
    # (IoU 0.956-0.969); listed after the first, its split overwrites only these tiles.
    'pods-v2': ['pod-out-port'],
}
