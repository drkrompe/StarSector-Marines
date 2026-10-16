"""Aft engine pods, the centre body between them and the stern machinery.

The two aft "arms" of the Cerberus planform are engine pods. Their blocks
are unioned into the main hull after the forebody facets are cut, so bow
planes never reach them. The four nozzles themselves are locked in
build_reference.py; the pods must enclose and support them.
"""
from sw_hull import LOWER, armor_patch, citadel, prism, side_profile, union

BLOCKS = [
    ('Engine room ventral strake', True, [(1.6,-12.4),(0,-11.4),(0,-9)],
     [(-32,19),(-2,19),(-2,28),(-32,28)]),
    # Central aft body between the engine rooms stops well short of the
    # sponson sterns: the Cerberus stern notch, open from deck to keel.
    ('Central aft body', False, LOWER,
     [(-28,-12),(4,-12),(4,12),(-28,12)]),
    # Engine rooms repeat the forebody's language: a lower hull with an
    # outboard ledge, a raised engine-room citadel and a low thrust step aft.
    ('Engine room lower hull', True, LOWER,
     [(-30,10),(2,10),(2,34),(0,36),(-30,36)]),
    # The bridge deck on the centreline ends at the notch apex; each engine
    # room keeps its own citadel past it, split from the bridge deck by a
    # deck-height groove so the two sponsons read separately from above.
    ('Aft centreline deck', False, citadel(7.5),
     [(-28,-11.2),(-1,-11.2),(-1,11.2),(-28,11.2)]),
    ('Engine room citadel', True, citadel(7.5),
     [(-32,13),(-1,13),(-1,29.5),(-3,31.5),(-32,31.5)]),
    # The thrust step stands slightly proud of the sponson: a stern collar.
    ('Engine room thrust collar', True, side_profile(8, 5, .4),
     [(-36,10),(-26,10),(-26,36),(-33,36),(-36,33)]),
]


def build_pods(kit):
    for name, mirror, profile, plan in BLOCKS:
        plans = [plan] + ([[(x,-y) for x,y in reversed(plan)]] if mirror else [])
        for p in plans:
            union(kit.hull, prism(kit, name, p, profile))


def build_armor(kit):
    for x in (-27,-19.5,-10.5):
        for y in (15.5,22.3):
            for side in (1,-1):
                armor_patch(kit,'Engine room armored shoulder',x,x+6.3,side*y,side*(y+6.3),kit.cream if x==-19 else kit.orange)
    for x in (-24.5,-16.5):
        for side in (-1,1):
            armor_patch(kit,'Engine room reinforcing band',x,x+1.1,side*13.5,side*36,kit.steel,.7)
    for side in (-1,1):
        for x in (-25,-15,-5):
            kit.box('Aft flank access housing',(x,side*35.9,0),(6,1.4,6),kit.cream,.5)


def build_stern(kit):
    # Machinery architecture at the stern: thrust frames boxing each nozzle
    # pair, radiator grilles on the stepped engine-room bulkheads and the
    # exposed plant at the apex of the open stern notch.
    for s in (1,-1):
        kit.box('Thrust bulkhead recess',(-36.55,s*27.5,0),(.5,15,10),kit.dark,.1)
        for z in (-5.7,5.7):
            kit.box('Thrust frame rail',(-37.1,s*27.6,z),(1.6,17.2,1.4),kit.steel,.3)
        for y in (19.5,35.6):
            kit.box('Thrust frame post',(-37.1,s*y,0),(1.6,1.4,12.8),kit.steel,.3)
        kit.box('Engine room radiator grille',(-32.3,s*22,11.2),(.8,15,3.6),kit.dark,.15)
        for y in range(16,30,2):
            kit.box('Radiator louver',(-32.75,s*y,11.2),(.3,.35,3.4),kit.steel,.05)
    kit.box('Notch plant housing',(-29.2,0,.5),(2.4,13,14),kit.steel,.6)
    kit.box('Plant access recess',(-30.5,0,0),(.4,9,9.5),kit.dark,.1)
    for y in (-3,0,3):
        kit.box('Plant housing rib',(-30.8,y,0),(.8,.7,11),kit.steel,.15)


def build_details(kit):
    build_armor(kit)
    build_stern(kit)
