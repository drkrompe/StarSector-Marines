"""Centre hull: the cargo body, its shielded-hold deckhouse, the engine spar
and the two stern skids either side of the flare notch.

Planforms are measured off hound_hegemony.png (62x72, centre 31,27): pixel
column c, row r covers X 44-r..45-r, Y 30-c..31-c. The body is the box
between the pods (cols 18-43, rows 25-62), the spar is the transverse beam
(rows 41-52) that carries the pods, and the skids are the two aft legs
(cols 18-24 and 37-43, rows 63-68) framing the notch where the hidden
flare launcher sits. Heights are proposed art: the sprite gives none.
"""
from hd_kit import boolean, facet_normal, mirror_y, plane_cut, prism, section

KEEL, DECK = -5.0, 6.0
# Aft of the spar the body is +-14 (rows 52-62), forward of it +-13.
BODY_PLAN = [(-18, -14), (-2, -14), (-2, -13), (19, -13), (20.5, -11.5), (20.5, 11.5), (19, 13), (-2, 13), (-2, 14), (-18, 14)]
BODY = section(KEEL, DECK, 2.0, 1.2)

# Shielded cargo holds: a raised armoured deckhouse over the forward hold
# (the sprite's ribbed block, rows 27-39), with hatch ribs along it.
HOUSE_PLAN = [(6, -10.5), (17, -10.5), (18.5, -9), (18.5, 9.5), (17, 11), (6, 11)]
HOUSE = [(0, DECK-.6), (0, 8.8), (.9, 9.7)]
HATCH_RIBS = [-8, -4.5, -1, 2.5, 6, 9.5]

# Engine spar: the transverse beam the pods hang on, reaching into each pod.
SPAR_PLAN = [(-7, -16.5), (4, -16.5), (4, 16.5), (-7, 16.5)]
SPAR = section(-3.6, 3.2, 1.0, 1.2)

# Stern skids, port side; mirrored to starboard. Each is a U opening forward
# in the sprite (rows 63-68): an outer cheek and inner rail joined by a foot.
SKID_PLAN = [(-24, 6), (-17, 6), (-17, 14), (-24, 14)]
SKID_SLOT = ((-22.2, -18.4), (8.2, 9.6))
SKID = section(KEEL+.5, 2.2, 1.5, 1.0)


def build(kit):
    body = prism(kit, 'Hull cargo body', BODY_PLAN, BODY, kit.orange)
    # Forward face of the body sits behind the bridge capsule and gun fork;
    # a raked chin keeps the keel from reading as a brick in side view.
    plane_cut(body, 'Hull chin rake', (21, 0, KEEL+3), facet_normal((1, 0), 140))
    house = prism(kit, 'Hull shielded hold deckhouse', HOUSE_PLAN, HOUSE, kit.rust)
    plane_cut(house, 'Deckhouse forward rake', (18.5, 0, 7.8), facet_normal((1, 0), 35))
    for i, y in enumerate(HATCH_RIBS, 1):
        kit.box(f'Hull hold hatch rib {i}', (11.6, y, 9.85), (10.2, .9, .5), kit.steel, .08)
    spar = prism(kit, 'Hull engine spar', SPAR_PLAN, SPAR, kit.orange)
    for side, plan in (('port', SKID_PLAN), ('starboard', mirror_y(SKID_PLAN))):
        skid = prism(kit, f'Hull stern skid {side}', plan, SKID, kit.rust)
        plane_cut(skid, f'Skid {side} aft rake', (-24, 0, -1), facet_normal((-1, 0), 130))
        (x0, x1), (y0, y1) = SKID_SLOT
        sy = 1 if side == 'port' else -1
        cut = kit.box('Temporary skid slot', ((x0+x1)/2, sy*(y0+y1)/2, 0), (x1-x0, y1-y0, 20), kit.dark, 0)
        boolean(skid, cut)
    # Dark aft bulkhead band across the notch (the sprite's dark stern line).
    kit.box('Hull aft bulkhead band', (-18.1, 0, 0), (.6, 12.4, 7), kit.dark, 0)
    kit.hull = [body, house, spar]
    return body
