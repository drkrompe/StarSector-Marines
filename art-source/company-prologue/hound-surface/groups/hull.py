"""Hull: the cargo body's large orange armour.

One sheet of four sources: the port side wall (the starboard wall is its
mirror, flipped-mask IoU 0.9999), the dorsal deck with both armour straps and
the four spar armour plates on it, the stern face, and the underside.

The cargo body is a core wrapped by other objects: against the whole scene,
square-on, the side walls are 4-7% visible (the engine pods stand in front),
the deck 23% (deck seam underlay, deck plates, deckhouse), the stern 26%
(skids) and the underside 13% (keel seam underlay and keel plates). The engine
spar is not skinned: it lies entirely inside the cargo body and the engine
armour collars, so no face of it can be seen. The deck and underside are
single polygons 38.5 x 28 units, which caps them at 13.7 texels per unit.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_Z, STERN

BODY = 'Hull cargo body'
# The bolted plates on the deck and keel carry most of the orange actually seen
# there; their outlines lie inside the deck and underside frames, so they take
# those tiles' art at no extra generation.
DECK_PLATES = [('Hull deck plate forward sill', (5.35, 0.0, 6.4)), ('Hull deck plate aft port', (-12.1, 4.5, 6.4)), ('Hull deck plate turret port fore', (1.35, 4.2, 6.4)), ('Hull deck plate turret starboard fore', (0.05, -1.95, 6.4)), ('Hull deck plate turret starboard aft', (-6.2, -1.95, 6.4)), ('Hull deck plate aft starboard', (-12.1, -11.8, 6.4)), ('Hull deck plate turret port aft', (-8.6, 4.2, 6.4))]
KEEL_PLATES = [('Hull keel plate ventral hatch starboard', (4.65, -8.3, -5.4)), ('Hull keel plate ventral hatch port', (4.65, 8.3, -5.4)), ('Hull keel plate 4', (-1.5, 8.45, -5.4)), ('Hull keel plate 13', (-1.5, -8.45, -5.4)), ('Hull keel plate 3', (-1.5, 3.7, -5.4)), ('Hull keel plate 12', (-1.5, -3.7, -5.4)), ('Hull keel plate 6', (10.25, 8.45, -5.4)), ('Hull keel plate 15', (10.25, -8.45, -5.4)), ('Hull keel plate 5', (10.25, 3.7, -5.4)), ('Hull keel plate 14', (10.25, -3.7, -5.4)), ('Hull keel plate 8', (14.9, 8.45, -5.4)), ('Hull keel plate 17', (14.9, -8.45, -5.4)), ('Hull keel plate 7', (14.9, 3.7, -5.4)), ('Hull keel plate 16', (14.9, -3.7, -5.4)), ('Hull keel plate 2', (-6.25, 8.45, -5.4)), ('Hull keel plate 11', (-6.25, -8.45, -5.4)), ('Hull keel plate 10', (-6.25, -3.7, -5.4)), ('Hull keel plate 1', (-6.25, 3.7, -5.4)), ('Hull keel plate 9', (-9.15, 8.45, -5.4)), ('Hull keel plate 18', (-9.15, -8.45, -5.4))]

PARTS = {
    'hull-side-port': dict(members=[(BODY, (10.8, 13, -0.3)), (BODY, (-11.4, 14, -0.9)), (BODY, (-7.8, 14, 2.3))],
                           dir=PORT, up=UP_Z, cone=.6, what='cargo body port side wall'),
    'hull-side-stbd': dict(members=[(BODY, (10.8, -13, -0.1)), (BODY, (-10.6, -14, 0.8)), (BODY, (-7.6, -14, 3.7))],
                           dir=STARBOARD, up=UP_Z, cone=.6, skin_from='hull-side-port', mirror_u=True,
                           what='cargo body starboard side wall (mirror of port)'),
    'hull-top': dict(members=[(BODY, (2.9, 0, 6.0)), ('Hull forward armour strap', (4, 0, 6.6)), ('Hull aft armour strap', (-15.6, 0, 6.6)),
                              ('Hull spar armour plate port aft', (-5.7, 10.2, 6.4)), ('Hull spar armour plate port fore', (-0.1, 10, 6.4)),
                              ('Hull spar armour plate starboard aft', (-5.8, -8.9, 6.4)), ('Hull spar armour plate starboard fore', (0.4, -8.4, 6.4))] + DECK_PLATES,
                     dir=UP_Z, up=PORT, cone=.6, what='cargo body dorsal deck with the armour straps, spar plates and deck plates'),
    'hull-stern': dict(members=[(BODY, (-18, -4.6, 0.8))], dir=STERN, up=UP_Z, cone=.6, what='cargo body stern face'),
    'hull-under': dict(members=[(BODY, (-5.2, 0, -5.0))] + KEEL_PLATES, dir=DOWN_Z, up=PORT, cone=.6, what='cargo body underside and its keel plates'),
}

SHEETS = {
    'hull': ['hull-side-port', 'hull-top', 'hull-stern', 'hull-under'],
}
