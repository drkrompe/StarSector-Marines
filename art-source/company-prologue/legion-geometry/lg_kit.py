"""Legion (XIV) battlecarrier: scale and palette over the shared ship detail kit.

The primitives (materials, box/cylinder/mesh, chamfered prisms, lofts,
booleans, plane cuts, the downward ray-cast) live in the shared
`ship-detail-kit/ship_detail_kit.py`, which was factored out of this file and
the Colossus's; this module keeps what is the Legion's own: its metres per
unit, PERSON and the XIV palette. Coordinates are hull-local, +X bow, +Y port,
+Z dorsal, one sprite pixel per unit about the .ship centre.
"""

# Real-world scale, from the shared sizing basis (ship_scale.py; owner's choice,
# 2026-10-18: option C). One sprite pixel is one unit. Person-scale fittings
# (doors, hatches, rails, ladders, catwalks) were first drawn at 0.61 m per unit;
# PERSON scales those authored sizes so they stay true metres at this scale.
METRES_PER_UNIT = 0.99
PERSON = 0.61/METRES_PER_UNIT

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'ship-detail-kit'))
import ship_detail_kit as sdk
# The shared primitives (ship-detail-kit), under the names the Legion modules import.
from ship_detail_kit import (material, inset, section, prism, mirror_y, loft_x, octagon, boolean, plane_cut,
                             facet_normal, strip_plan, ccw, clip_rect, _left)


class Kit(sdk.BaseKit):
    def __init__(self):
        # XIV Battlegroup livery read off legion_xiv.png: bright Hegemony
        # orange armour belts, warm grey-tan structural plating, the bone
        # white command dome and spine, gunmetal machinery, dark recesses,
        # and the red-lit hangar interiors of the four flight decks.
        self.orange = material('XIV orange armour', (.62, .19, .055), .25, .55)
        self.plate = material('XIV grey hull plating', (.30, .27, .25), .35, .6)
        self.bone = material('XIV bone white dome', (.62, .60, .56), .2, .5)
        self.steel = material('Gunmetal machinery', (.16, .16, .17), .7, .5)
        self.dark = material('Recess and machinery', (.035, .035, .04), .5)
        self.deck = material('Flight deck', (.11, .10, .10), .3, .7)
        self.bay_light = material('Hangar red light', (.08, .01, .01), .0, .5, emission=(1.0, .12, .04), strength=6.0)
        self.glass = material('Bridge glazing', (.04, .06, .07), .2, .15, emission=(.9, .75, .45), strength=3.0)
        self.hull = []  # objects the downward ray-cast seats parts on
