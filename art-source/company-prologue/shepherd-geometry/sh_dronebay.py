"""Shepherd drone bay: the port hangar pods, their dorsal launch openings,
and the port outrigger frame.

Read off drone_tender.png: the port third of the hull (Y 11..27) is two
dark capsules end to end - a forward one X 8..33 and an aft one X -13..6 -
with a cyan glow along their inboard edge and amber lamps at the joins. A
dark service block caps the forward end (X 26..34). A blue ring on the
forward capsule's back (X ~18) is a hatch. The bay is the hidden
LAUNCH_BAY slot WS 003, which the game gives three launch locations at
(-3, 19), (12, 19) and (26, 19), facing 0 deg: those are the three dorsal
openings, one Borer drone each out of the wing of four (the fourth stows
below). The opening sizes come from the Borer, not the sprite (see
build_shepherd.DRONE_BAY).

Names: 'Drone bay ...' and 'Frame port ...'. Each opening is
'Drone bay WS 003 opening <i> ...' with a 'Drone bay WS 003 opening <i>
mouth' empty on the rim (normal +Z) and a 'Drone bay WS 003 launch point
<i>' empty on the well floor; geometry.json exports both.
"""
from sh_kit import pod, boolean

SLOT = {'id': 'WS 003', 'locations': [(-3, 19), (12, 19), (26, 19)], 'angle': 0, 'arc': 5,
        'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'LAUNCH_BAY'}
POD_Y, POD_Z, POD_R = 18.5, 0.0, 7.5
OPENING_R = 4.2      # clear radius of each dorsal opening, units
WELL_FLOOR = 1.0     # well floor height, units (pod crown is about +7.3)
OPENINGS = []        # filled by build(): one record per opening for geometry.json


def build(kit):
    aft = pod(kit, 'Drone bay aft hangar pod', [(-13.5, 5.0, 5.0), (-11.5, POD_R, POD_R), (4.5, POD_R, POD_R),
                                                 (7.0, 6.2, 6.2)], POD_Y, POD_Z, kit.steel, 12)
    fore = pod(kit, 'Drone bay forward hangar pod', [(6.0, 6.2, 6.2), (8.5, POD_R, POD_R), (27.5, POD_R, POD_R)],
               POD_Y, POD_Z, kit.steel, 12)
    block = kit.box('Drone bay forward service block', (30.75, 18.75, 0), (7.5, 15.5, 10.5), kit.dark, .3)
    kit.hull += [aft, fore, block]
    # Inboard glow strip (the sprite's cyan edge) and amber join lamps.
    kit.box('Drone bay inboard glow strip', (10, 11.0, 1.5), (40, .4, 1.2), kit.bay_glow, 0)
    for i, (x, y) in enumerate(((7.5, 23.5), (7.5, 12.5), (27, 25), (27, 12.5)), 1):
        kit.box(f'Drone bay join lamp {i}', (x, y, 5.5), (.8, .8, .5), kit.cream, 0)

    OPENINGS.clear()
    for i, (x, y) in enumerate(SLOT['locations'], 1):
        host = block if x > 25 else (fore if x > 6.5 else aft)
        top = kit.surface(x, y)[0].z
        cutter = kit.cylinder(f'Temporary opening {i} cutter', (x, y, (WELL_FLOOR+top+2)/2), OPENING_R,
                              top+2-WELL_FLOOR, kit.dark, 'Z', 20, 0)
        boolean(host, cutter)
        kit.cylinder(f'Drone bay WS 003 opening {i} well floor', (x, y, WELL_FLOOR+.1), OPENING_R+.05, .2,
                     kit.dark, 'Z', 20, 0)
        ring = kit.cylinder(f'Drone bay WS 003 opening {i} coaming', (x, y, top+.25), OPENING_R+.9, .7,
                            kit.grey, 'Z', 20, .1)
        hole = kit.cylinder(f'Temporary coaming {i} cutter', (x, y, top+.25), OPENING_R, 2, kit.dark, 'Z', 20, 0)
        boolean(ring, hole)
        mouth = kit.empty(f'Drone bay WS 003 opening {i} mouth', (x, y, top+.6), SLOT['angle'])
        launch = kit.empty(f'Drone bay WS 003 launch point {i}', (x, y, WELL_FLOOR+.2), SLOT['angle'])
        OPENINGS.append({'index': i, 'slot_location': [x, y], 'mouth_anchor': mouth.name,
                         'launch_point_anchor': launch.name, 'rim_z': round(top+.6, 3),
                         'floor_z': round(WELL_FLOOR+.2, 3), 'clear_radius': OPENING_R, 'host': host.name})

    # Port outrigger: an upper and a lower rail on posts, outboard of the pods.
    kit.box('Frame port outrigger upper rail', (3, 28.5, 2.5), (56, 5.0, 2.0), kit.tan, .2)
    kit.box('Frame port outrigger lower rail', (2.5, 29.5, -3.5), (50, 2.5, 1.6), kit.steel, .15)
    for i, x in enumerate((-22, -8, 6, 20, 29), 1):
        kit.box(f'Frame port outrigger post {i}', (x, 28.5, -.5), (1.4, 1.4, 5.5), kit.steel, .1)
        kit.box(f'Frame port outrigger strut {i}', (x, 26.5, 0), (1.2, 4.5, 1.2), kit.steel, .1)
    kit.cylinder('Frame port bow fender', (22, 31.2, 0), 2.4, 4, kit.dark, 'Z', 12, .15)
