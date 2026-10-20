"""Hull: the core (one object from the gun step to the engine block, its combat
armour unioned in), the engine block's aft body, the stern and the belly, and
the bridge's command tier with the dome.

`Hull core` is plated in bolted courses (mt_kit.armour_plates / hull_plating):
each plate is lifted on a seam ring whose walls turn out of any cone, so every
seed is a box (all qualifying faces whose centre lies in it), never a grown
group, which would stop at the first seam. The core is symmetric about Y = 0;
its top is three tiles along X for texel density (about 11 a unit).
"""
from math import sqrt
from parts_common import UP_Z, DOWN_Z, DOWN_X, STERN, PORT
from groups._mirror import SEAMS, box, pair

CORE, AFT, WING, TIER, DOME = 'Hull core', 'Hull aft body', 'Hull wing body', 'Bridge command tier', 'Bridge command dome'
FLANK = (0.0, sqrt(.5), -sqrt(.5))      # the core's lower chamfer, outboard and down

PARTS = {
    'core-top-1': dict(members=[(CORE, {'box': box(x=(18, 50))})], dir=UP_Z, up=DOWN_X, cone=.6, seam_walls=SEAMS,
                       what='hull core deck and shoulders forward of X 18: the gun step round the main turret ring'),
    'core-top-2': dict(members=[(CORE, {'box': box(x=(-16, 18))})], dir=UP_Z, up=DOWN_X, cone=.6, seam_walls=SEAMS,
                       what='hull core deck and shoulders X -16..18, in front of and beside the bridge plinth'),
    'core-top-3': dict(members=[(CORE, {'box': box(x=(-60, -16))})], dir=UP_Z, up=DOWN_X, cone=.6, seam_walls=SEAMS,
                       what='hull core deck and shoulders aft of X -16, behind the bridge'),
    'core-stern': dict(members=[(CORE, {'box': box(x=(-60, -50))}), (AFT, {'box': box(x=(-60, -45))})], dir=STERN,
                       up=UP_Z, cone=.7, what='the stern faces of the core and the engine block, behind the drives'),
    'belly': dict(members=[(CORE, {'box': box()}), (WING, {'box': box()}), (AFT, {'box': box()})], dir=DOWN_Z,
                  up=PORT, cone=.8, what='the whole underside from below: core, wing body and engine block bottoms'),
    'tier-top': dict(members=[(TIER, {'box': box()}), (DOME, {'box': box()})], dir=UP_Z, up=DOWN_X, cone=.6, seam_walls=SEAMS,
                     what='bridge command tier roof and the pale command dome from above'),
}
pair(PARTS, 'aft-top', [(AFT, box(y=(0, 60)))], UP_Z, DOWN_X, .6,
     'engine block deck and its raked shoulder between the drive cans')
pair(PARTS, 'core-flank-fore', [(CORE, box(x=(-5, 50), y=(0, 30), z=(-30, 4)))], FLANK, UP_Z, .75,
     'hull core lower chamfer forward of X -5, the armoured flank under the wing root')
pair(PARTS, 'core-flank-aft', [(CORE, box(x=(-60, -5), y=(0, 30), z=(-30, 4)))], FLANK, UP_Z, .75,
     'hull core lower chamfer aft of X -5, under the engine block shoulder')

SHEETS = {
    'core': ['core-top-1', 'core-top-2', 'core-top-3', 'tier-top'],
    'aft': ['aft-top-port', 'core-stern', 'belly', 'core-flank-fore-port'],
}
