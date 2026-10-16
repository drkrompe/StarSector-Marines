"""Aft engine pods, the centre body between them and the stern machinery.

The two aft "arms" of the Cerberus planform are big armoured engine nacelles,
not deck blocks: each is its own faceted octagonal body, taller than the main
deck, standing off the centre body across a recessed neck and ending in a
stepped engine housing that presents the nozzle mouths. Pod blocks are cut by
their own facets before they join the hull, so neither bow nor pod planes
reach the wrong part. The four nozzles are locked in build_reference.py.
"""
from sw_hull import LOWER, MAIN_DECK, armor_patch, citadel, facet, plane_cut, prism, union

POD_DECK = 16  # nacelles stand proud of the main deck: the bulkiest masses

# Centre aft body (mirror False) and the neck tying each pod to it (True).
BLOCKS = [
    ('Central aft body', False, LOWER,
     [(-28,-12),(4,-12),(4,12),(-28,12)]),
    # Flat deck plateau at main-deck height for |y|<9 between the pods.
    ('Aft centreline deck', False, citadel(7.5),
     [(-28,-11.2),(-1,-11.2),(-1,11.2),(-28,11.2)]),
    # The neck is a low pylon: its top sits under the waist deck, so a dark
    # valley separates every pod from the centre body in plan.
    ('Engine pod neck pylon', True, [(1,-6.5),(0,-5),(0,6.8),(.8,7.6)],
     [(-24,10.5),(1,10.5),(1,14),(-19,14),(-21,16),(-24,16)]),
]

# Nacelle: octagonal plan with heavy chamfered corners, wide outboard of the
# waist (to y 39) and waisted inboard aft where the engine section clears the
# stern notch. Section is octagonal too: chamfered keel and deck chines.
NACELLE_PLAN = [(-31,18.5),(-23,18.5),(-19,13),(-5,13),(2,20),(2,33),
                (-4,39),(-21,39),(-25,37),(-31,37)]
NACELLE_PROFILE = [(4,-9),(1.2,-7),(0,-4),(0,11.5),(1.2,14),(2.6,POD_DECK)]
# Armour belt: a chamfered band proud of the nacelle wall at the nozzle line,
# so the flank breaks into belt and cowl rather than one slab.
BELT_PLAN = [(-31,18.5),(-23,18.5),(-19,13),(-5,13),(1.6,19.6),(1.6,33.4),
             (-4,39),(-21,39),(-25,37),(-31,37)]
BELT_PROFILE = [(0,-4.5),(-1.4,-3),(-1.4,4),(0,5.5)]
# Engine housing: stepped aft face, the big outer pair proud of the inner.
HOUSING_PLAN = [(-36,20),(-29,20),(-29,37),(-37.5,37),(-39.2,35.3),
                (-39.2,27),(-37.2,27),(-37.2,21.2)]
HOUSING_PROFILE = [(1.6,-6.8),(0,-5.2),(0,5.2),(1.6,6.8)]
# Nacelle planes: a nose glacis and chin, and a raked tail falling to the
# housing above and below. Turret 1/2's deck (x -15..-7) stays flat.
NACELLE_FACETS = [
    facet('Nacelle nose glacis', (-3,0,POD_DECK), (1,0), 32),
    facet('Nacelle chin rake', (-1,0,-6), (1,0), 145),
    facet('Nacelle tail rake', (-24,0,POD_DECK), (-1,0), 40),
    facet('Nacelle tail keel rake', (-27,0,-6), (-1,0), 135),
]
# The outboard shoulder falls away in one broad armour face, port side;
# mirrored for starboard.
SHOULDER = (-10,32.5,POD_DECK), 33


def mirror(plan, side):
    return plan if side > 0 else [(x,-y) for x,y in reversed(plan)]


def build_pods(kit):
    for name, mirrored, profile, plan in BLOCKS:
        for side in ((1,-1) if mirrored else (1,)):
            union(kit.hull, prism(kit, name, mirror(plan, side), profile))
    for side in (1,-1):
        pod = prism(kit, 'Engine nacelle', mirror(NACELLE_PLAN, side), NACELLE_PROFILE)
        (x,y,z), angle = SHOULDER
        shoulder = facet('Nacelle outboard shoulder', (x,side*y,z), (0,side), angle)
        for name, point, normal, span in NACELLE_FACETS+[shoulder]:
            plane_cut(pod, name, point, normal, span)
        union(pod, prism(kit, 'Nacelle armour belt', mirror(BELT_PLAN, side), BELT_PROFILE))
        union(pod, prism(kit, 'Engine housing', mirror(HOUSING_PLAN, side), HOUSING_PROFILE))
        union(kit.hull, pod)


def build_armor(kit):
    # Plates on the nacelle deck, clear of the barbette (x -15..-7, y 16..24).
    for x0,x1,y0,y1 in [(-23.5,-17,16,23),(-23.5,-17,24.5,30.5),(-16,-6,25.5,31),
                        (-23.5,-17,31.5,36.5),(-16,-6,32,36.5),(-5.5,-3.5,16,36)]:
        for s in (1,-1):
            armor_patch(kit,'Nacelle armour plate',x0,x1,s*y0,s*y1,
                        kit.cream if y0>31 or x0==-16 else kit.orange)
    for s in (1,-1):
        # Steel cladding over each engine housing: the machinery block aft.
        for x0,y0,y1 in ((-36.4,21.4,26.6),(-38.4,27.6,35.8)):
            armor_patch(kit,'Engine housing cladding',x0,-31.4,s*y0,s*y1,kit.steel,.25)
        armor_patch(kit,'Nacelle reinforcing band',-16.6,-15.6,s*15.5,s*36.5,kit.steel,.7)
        # Outboard flank: access housings and a rubbing strake on the wall.
        for x in (-22,-12):
            kit.box('Nacelle flank access hatch',(x,s*39.1,9),(4,.5,3),kit.cream,.3)
        kit.box('Nacelle cowl strake',(-12,s*39.15,10.4),(24,.6,.8),kit.steel,.2)
        # Nose intake: a louvred scoop in the nacelle's forward face.
        kit.box('Nacelle nose intake',(2.05,s*25,8.2),(.5,11,3),kit.dark,.15)
        for z in (7.3,8.2,9.1):
            kit.box('Intake louver',(2.3,s*25,z),(.3,10.6,.3),kit.steel,.04)
        # Dark machinery bulkhead closing the neck slot, seen through the notch.
        kit.box('Neck aft machinery bulkhead',(-24.3,s*15.2,1.5),(.6,6.8,12),kit.dark,.1)
        # Conduits run along the neck valley between nacelle and centre body.
        for y in (12.0,13.2):
            kit.cylinder('Neck valley conduit',(-10,s*y,8.1),.5,24,kit.steel,'X',16,.05)


def build_stern(kit):
    for s in (1,-1):
        # Thrust frame boxing each housing's aft face, a raked radiator on the
        # nacelle tail and an exhaust baffle plate between the nozzle pairs.
        for z in (-5.6,5.6):
            kit.box('Thrust frame rail',(-38.3,s*28.6,z),(1.6,17.6,1.2),kit.steel,.3)
        for y in (20.4,27.3,36.5):
            kit.box('Thrust frame post',(-38.3 if y>27 else -37.4,s*y,0),(1.4,1.2,12.2),kit.steel,.3)
        # Laid on the 40-degree tail rake, inboard of the shoulder facet.
        g = kit.box('Nacelle tail radiator',(-28.36,s*25.5,12.67),(5.6,11,.5),kit.dark,.1)
        g.rotation_euler[1] = -0.70
        for y in range(21,31,2):
            r = kit.box('Radiator louver',(-28.52,s*y,12.86),(5.2,.35,.35),kit.steel,.05)
            r.rotation_euler[1] = -0.70
    # The stern notch carries a dark machinery frame, low between the pods:
    # a cross girder at the notch mouth, side rails into each neck and the
    # plant housing at the apex.
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
    build_armor(kit)
    build_stern(kit)
