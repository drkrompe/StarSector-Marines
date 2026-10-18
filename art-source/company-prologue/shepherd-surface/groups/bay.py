"""Bay: the open port drone work bay.

The inboard back wall and its armour panels face +Y into the bay; the rim
wall's aft end faces +X on the crossbeam's face; the two hangar pods' exterior
shows below the kerb outboard, under the outrigger; the forward service block
(the workshop) closes the bay's forward end. One sheet: back wall from the
bay, aft end from ahead, pod exterior from port, service block outboard face.
"""
from parts_common import PORT, UP_Z, BOW

WALL, RIM = 'Drone bay inboard back wall', 'Drone bay rim wall'
AFT, FORE, BLOCK = 'Drone bay aft hangar pod', 'Drone bay forward hangar pod', 'Drone bay forward service block'

PARTS = {
    'bay-back-wall': dict(members=[(WALL, (10.0, 12.4, 0.0))],
                          dir=PORT, up=UP_Z, cone=.6, ray=12,
                          what='drone bay inboard back wall seen from inside the bay (its armour panels keep their swatch)'),
    'bay-aft-end': dict(members=[(RIM, (-8.7, 19.0, 0.0))],
                        dir=BOW, up=UP_Z, cone=.6, ray=30,
                        what='drone bay aft end on the crossbeam face, seen from ahead inside the bay'),
    'bay-pod-port': dict(members=[(AFT, (-3.0, 25.7, -6.0)), (FORE, (16.0, 25.7, -6.0))],
                         dir=PORT, up=UP_Z, cone=.6, what='hangar pods exterior below the kerb, from port'),
    'bay-block-port': dict(members=[(BLOCK, (31.0, 26.5, 0.0))], dir=PORT, up=UP_Z, cone=.6,
                           what='forward service block outboard face'),
}

SHEETS = {
    'bay': ['bay-back-wall', 'bay-aft-end', 'bay-pod-port', 'bay-block-port'],
}
