"""Phaeton hull: the long red tank hull, its three spherical cargo tanks, the
port gallery block, the turret sponsons and the aft machinery spine.

Read off tanker_medium.png (bow right, port up in model XY):
- the red hull runs X -34..128, Y -29..+30, its bow rounded in plan;
- three spherical tanks sit on the centreline at X 0, 44 and 88, each about
  30 units across: a Moss-type layout, the spheres standing half proud of
  the deck in their skirts;
- the raised port block X 55..82 reaching Y +37 carries an amber-lit strip
  along its crown: the crew gallery's skylight;
- the round grey housings at the turret slots stand outboard of the hull on
  sponsons (WS 002 port, WS 003 starboard, WS 004 on the port bow);
- aft of the hull, a narrow machinery spine (X -64..-30) carries the
  silvery reactor dome at X -50 and joins the hull to the drive block.
Heights are proposed art: keel -20, deck +6, tank centres on the deck.
"""
import math
import bpy
from ph_kit import prism, section, plane_cut, facet_normal, sphere, octagon, loft_x

KEEL, DECK = -20.0, 6.0
# (name, x, radius): tank centres sit on the deck line.
TANKS = [('Tank 1 aft sphere', 0.0, 15.0), ('Tank 2 mid sphere', 44.0, 14.5), ('Tank 3 fore sphere', 88.0, 15.5)]
PLAN = [(-36, -22), (-28, -30), (100, -30), (112, -31), (124, -31), (130, -26), (132, -18), (129.5, -6), (129, 2),
        (126, 10), (125, 20), (121, 28), (114, 34), (104, 35), (96, 33), (86, 30), (56, 30), (52, 31), (-28, 31), (-36, 23)]
# Grey machinery strakes along both flanks, below the red deck: pump rooms and
# pipe galleries out to the sprite's edge (Y +-38), stepping the section.
STRAKES = {
    'port': [(-38, 24), (-30, 26), (100, 26), (104, 31), (100, 36), (86, 38.5), (56, 38.5), (52, 39), (22, 39),
             (16, 40.5), (-10, 40.5), (-14, 38.5), (-26, 38.5), (-38, 32)],
    'starboard': [(-38, -32), (-28, -37.5), (-8, -37.5), (-4, -35.5), (26, -35.5), (56, -36.5), (60, -37), (98, -37), (104, -42),
                  (118, -42), (124, -36), (118, -28), (-30, -26), (-38, -24)],
}
SPONSONS = {  # slot id: (centre x, y, radius, inboard reach)
    'WS 002': (38, 38, 11.0), 'WS 003': (36, -34, 10.0), 'WS 004': (126, 13, 10.5),
}


def build(kit):
    body = prism(kit, 'Hull main body', PLAN, section(KEEL, DECK, 10, 3), kit.red)
    # Bow rake: a raked forefoot under the nose and a shoulder facet over it.
    plane_cut(body, 'bow forefoot', (124, 0, -8), (.5, 0, -.87))
    plane_cut(body, 'bow shoulder', (129, 0, 3), (.6, 0, .8))
    kit.hull.append(body)

    for name, x, r in TANKS:
        dome = sphere(kit, name, (x, 0, DECK), r, kit.tank)
        kit.hull.append(dome)
        # The skirt: a low cylindrical collar the sphere stands in, proud of the deck.
        kit.cylinder(name.replace('sphere', 'skirt'), (x, 0, DECK+1.2), r+1.2, 2.4, kit.red, 'Z', 32, .2)

    for side, plan in STRAKES.items():
        kit.hull.append(prism(kit, f'Hull {side} machinery strake', plan, section(-13, -1, 3, 1.5), kit.steel))
    # Aft transverse frame: where the hull meets the spine and the drive housings.
    kit.hull.append(prism(kit, 'Hull aft transverse frame', [(-42, -34), (-30, -34), (-30, 34), (-42, 34)],
                          section(-13, DECK-3, 3, 2), kit.steel))

    # Port crew gallery block, its amber skylight on the crown.
    gal = prism(kit, 'Hull port gallery block', [(54, 22), (84, 22), (84, 33), (80, 37), (58, 37), (54, 33)],
                section(-12, DECK+6, 3, 2.5), kit.red)
    kit.hull.append(gal)
    kit.box('Hull gallery skylight', (69, 34.6, DECK+6.05), (20, 1.6, .25), kit.lamp, 0)

    # Turret sponsons: octagonal drums reaching in to the hull side.
    for sid, (x, y, r) in SPONSONS.items():
        s = 1 if y > 0 else -1
        ring = [(x+r*math.cos(math.pi/8+i*math.pi/4), y+r*math.sin(math.pi/8+i*math.pi/4)) for i in range(8)]
        ob = prism(kit, f'Hull sponson {sid}', ring, section(-8, DECK+1, 3, 1.2), kit.grey)
        kit.hull.append(ob)
        if sid != 'WS 004':
            kit.box(f'Hull sponson {sid} web', (x, y-s*(r+1)/2, -2), (2*r-2, r+2, 12), kit.steel, .3)

    # Aft machinery spine and the reactor dome on it.
    spine = prism(kit, 'Hull aft machinery spine', [(-66, -16), (-28, -16), (-28, 16), (-66, 16)],
                  section(-14, DECK+2, 4, 2.5), kit.steel)
    kit.hull.append(spine)
    dome = sphere(kit, 'Hull aft reactor dome', (-50, 0, DECK+1), 8.5, kit.grey)
    kit.hull.append(dome)
