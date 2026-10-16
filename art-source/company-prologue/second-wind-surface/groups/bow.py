"""Bow: the cream armoured nose block and the bow shield's centreline nose rake.

Facet directions come from sw_hull.BOW_FACETS and the nose block's own cuts:
the nose rake falls 58 deg below horizontal along +X, normal (0.85, 0, 0.53),
on both the shield module and the block standing proud of it, so one tile
covers both (the block's cream rake and the oxide rake framing it). The
glacis and the shield's stem face stay with the port pilot's cheek / flank
parts, which claimed them first and whose tile cameras depend on them.
"""
from math import sqrt
from parts_common import PORT, STARBOARD, UP_Z, DOWN_X, BOW

NOSE = 'Armoured nose block'
SHIELD = 'Forebody armour module.002'
RAKE = (0.848 / sqrt(0.848 ** 2 + 0.53 ** 2), 0.0, 0.53 / sqrt(0.848 ** 2 + 0.53 ** 2))

PARTS = {
    'bow-nose-rake': dict(members=[(NOSE, (56.5, 0, 5.7)), (SHIELD, (54.6, 14, 6.3))], dir=RAKE, up=UP_Z, cone=.9,
                          what='nose rake: the cream nose block rake standing proud of the oxide shield rake, centreline'),
    'bow-nose-side-port': dict(members=[(NOSE, (53.3, 8, 2.2))], dir=PORT, up=UP_Z, cone=.9,
                               what='cream nose block, port side face'),
    'bow-nose-side-stbd': dict(members=[(NOSE, (53.3, -8, 2.2))], dir=STARBOARD, up=UP_Z, cone=.9,
                               skin_from='bow-nose-side-port', mirror_u=True,
                               what='cream nose block, starboard side face (mirror of the port side)'),
    'bow-nose-front': dict(members=[(NOSE, (59.6, 0, -0.9))], dir=BOW, up=UP_Z, cone=.65,
                           what='cream nose block stem face and its corner chamfers'),
    'bow-nose-top': dict(members=[(NOSE, (50.6, 0, 12.4))], dir=UP_Z, up=DOWN_X, cone=.9,
                         what='cream nose block crown, flat top behind the rake'),
}

SHEETS = {
    'bow-nose': ['bow-nose-rake', 'bow-nose-side-port', 'bow-nose-front', 'bow-nose-top'],
}
