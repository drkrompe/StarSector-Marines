"""Centre aft body between the engine pods and the stern-notch machinery.

The command-deck housing (sw_bridge) runs onto this deck from forward; the
pods (sw_engines) join it through their pylons.
"""
from sw_hull import LOWER, citadel, prism, union

BLOCKS = [
    ('Central aft body', False, LOWER,
     [(-28,-12),(4,-12),(4,12),(-28,12)]),
    # Flat deck plateau at main-deck height for |y|<9 between the pods.
    ('Aft centreline deck', False, citadel(7.5),
     [(-28,-11.2),(-1,-11.2),(-1,11.2),(-28,11.2)]),
]


def build_body(kit):
    for name, _, profile, plan in BLOCKS:
        union(kit.hull, prism(kit, name, plan, profile))


def build_stern(kit):
    # The stern notch carries a dark machinery frame, low between the pods:
    # a cross girder at the notch mouth, side rails short of each housing
    # and the plant housing at the apex.
    kit.box('Notch plant housing',(-29.2,0,1),(2.4,13,13),kit.steel,.6)
    kit.box('Plant access recess',(-30.5,0,0),(.4,9,9.5),kit.dark,.1)
    for y in (-3,0,3):
        kit.box('Plant housing rib',(-30.8,y,0),(.8,.7,11),kit.steel,.15)
    kit.box('Notch cross girder',(-35.5,0,1.5),(1.6,36,2.2),kit.steel,.3)
    for s in (1,-1):
        kit.box('Notch side rail',(-32.5,s*16.5,1.5),(7,1.4,2.2),kit.steel,.3)
        kit.box('Notch machinery bay',(-33,s*12.8,0),(4.6,6,7),kit.dark,.2)
        kit.cylinder('Notch feed line',(-31.5,s*9.5,3.4),.6,7,kit.steel,'X',16,.05)


def build_details(kit):
    build_stern(kit)
