"""Shepherd starboard side: the repair gantry, the turret deck aft of it and
the starboard outrigger frame.

Read off drone_tender.png: the starboard third (Y -11..-27, X 7..33) is
open grey machinery in a lattice - the built-in repair_gantry - flanked by
burnt-orange rails along its outboard edge (Y -24..-27). Aft of it a grey
lattice deck (X -14..7) carries the round mount of turret WS 007 at
(-3, -23). The hardpoint WS 008 at (27, -19) faces forward out of the
gantry's forward end.

Names: 'Gantry ...' and 'Frame starboard ...'.
"""
from sh_kit import prism, section

X0, X1, Y0, Y1 = 8.0, 32.5, -12.0, -26.0   # gantry frame extents (column centres)
Z0, Z1 = -6.0, 9.5                         # bottom and top girder heights


def build(kit):
    core = kit.box('Gantry machinery core', (20.5, -18.5, 0), (24, 12, 9), kit.grey, .3)
    base = prism(kit, 'Gantry base deck', [(7, -29.5), (33, -29.5), (33, -11), (7, -11)],
                 section(Z0-1.2, Z0+.6, .4, .3), kit.steel)
    kit.hull += [core, base]
    kit.box('Gantry forward hardpoint bed', (28.5, -19, 2.5), (7, 7, 6), kit.steel, .2)
    for i, (x, y) in enumerate(((X0, Y0), (X1, Y0), (X1, Y1), (X0, Y1)), 1):
        kit.box(f'Gantry column {i}', (x, y, (Z0+Z1)/2), (1.6, 1.6, Z1-Z0), kit.steel, .1)
    for z, tag in ((Z1, 'top'), (Z0, 'bottom')):
        for y, side in ((Y0, 'inboard'), (Y1, 'outboard')):
            kit.box(f'Gantry {tag} {side} girder', ((X0+X1)/2, y, z), (X1-X0+1.6, 1.6, 1.6), kit.steel, .1)
    for i, x in enumerate((X0, (X0+X1)/2, X1), 1):
        kit.box(f'Gantry top cross girder {i}', (x, (Y0+Y1)/2, Z1), (1.6, Y0-Y1, 1.6), kit.steel, .1)
    kit.box('Gantry crane trolley', (22, (Y0+Y1)/2, Z1+.6), (5, Y0-Y1+1.2, 2.2), kit.orange, .2)
    kit.box('Gantry crane hoist', (22, -19, Z1-3.2), (2.4, 2.4, 4.2), kit.dark, .1)
    # Burnt-orange mounting rails along the gantry's outboard edge.
    kit.box('Gantry outboard mounting rail', (20, -26.2, 1), (27, 1.6, 7), kit.orange, .2)

    # Turret deck: a lattice-framed platform aft of the gantry.
    deck = prism(kit, 'Frame starboard turret deck', [(-14, -30), (-10, -32.5), (6, -32.5), (7, -30), (7, -11), (-14, -11)],
                 section(-3, 1, .8, .6), kit.steel)
    kit.hull.append(deck)
    # Starboard outrigger rails, outboard of the gantry and turret deck.
    kit.box('Frame starboard outrigger upper rail', (8.5, -29.25, 2.5), (45, 4.0, 2.0), kit.tan, .2)
    kit.box('Frame starboard outrigger lower rail', (8, -29.5, -3.5), (40, 2.5, 1.6), kit.steel, .15)
    for i, x in enumerate((-12, 0, 14, 28), 1):
        kit.box(f'Frame starboard outrigger post {i}', (x, -28.5, -.5), (1.4, 1.4, 5.5), kit.steel, .1)
    kit.cylinder('Frame starboard bow fender', (22, -31.2, 0), 2.4, 4, kit.dark, 'Z', 12, .15)
