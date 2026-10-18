"""Shepherd bow: the bridge cab, the crew habitat dome behind it, the forward
sensor boom off the spine and the starboard sensor head.

Read off drone_tender.png: the tan body ends forward in a rounded cab
(X 26..33) ringed by a dark band at X ~27 - the bridge glazing; behind it
the pale blister on the starboard half of the body (X 5..25, Y -8..-1) is
the habitat shell the description's "crew comfort" asks for; a thin boom
runs forward off the spine to X 44 (Y ~9.5); a grey sensor cluster stands
at X 31..39, Y -6..-11.

Names: 'Bridge ...' and 'Mast ...'.
"""
from sh_kit import prism, section, pod

CAB_PLAN = [(25, -6), (31.5, -6), (35, -3), (35, 3), (31.5, 6), (25, 6)]


def build(kit):
    deck = kit.surface(28, 0)[0].z
    cab = prism(kit, 'Bridge cab', CAB_PLAN, section(deck-1.0, deck+4.0, .6, 1.6), kit.tan)
    kit.hull.append(cab)
    # Glazing band round the cab's forward faces (blockout: one dark band).
    prism(kit, 'Bridge glazing band', [(31.0, -5.6), (31.7, -5.6), (35.4, -2.6), (35.4, 2.6),
                                         (31.7, 5.6), (31.0, 5.6)], section(deck+1.2, deck+2.6, 0, 0), kit.glass, 0)

    # Habitat shell: a low faceted blister along the starboard half of the deck.
    hz = kit.surface(15, -4.5)[0].z
    kit.hull.append(pod(kit, 'Bridge habitat shell', [(4, 1.0, .8), (7, 3.6, 3.6), (14, 4.4, 4.6),
                                                       (21, 4.0, 4.2), (25.5, 2.2, 2.4)],
                        -4.5, hz, kit.pale, 12))

    # Forward sensor boom: a thin box girder off the spine's forward end.
    sz = kit.surface(35, 8)[0].z
    kit.box('Mast forward sensor boom', (40.5, 9.5, sz+.6), (8, 1.1, 1.1), kit.steel, .1)
    kit.box('Mast forward sensor boom tip', (44.6, 9.5, sz+.6), (.6, .5, .5), kit.cream, .05)

    # Starboard sensor head on its pedestal (X 31..39, Y -6..-11).
    pz = kit.surface(30.5, -9)[0].z
    kit.box('Mast starboard sensor pedestal', (30.5, -9, pz+1.5), (3, 3, 3.5), kit.steel, .15)
    kit.box('Mast starboard sensor head', (35, -8.5, pz+3.5), (8, 4.5, 2.2), kit.grey, .2)
