"""Centre aft body between the engine pods and the stern-notch machinery.

The sprite's centre aft region, read across all four paint schemes (pale
lines and dark recesses hold in every scheme; the orange does not):
behind the command capsule a transverse frame ties two pale fore-aft rails
(|y| ~7, x -8..-27) that splay outboard at their fore ends; between them an
armoured lid (x -8..-14), then a dark recess holding a grey module
(x -15..-22) and a dark slot, and a bolted cross girder at the deck's aft
edge (x -28). Below it a grey face and a dark "[" frame (x -31..-37, arms at
|y| ~11) close the stern notch, flanked by short machinery prongs (|y| 12..17)
that stop short of the inner thrust bays.

In-world: the deck is the engine-room roof. The lid is the reactor plant's
armoured lift-out hatch, the rails carry a hoist bridge parked over the
open plant-access well, and the stern frame is a docking/tow cradle worked
by winches on the prongs. The command-deck housing (sw_bridge) runs onto
this deck from forward; the pods (sw_engines) join it through their pylons.
"""
import math
from sw_hull import LOWER, citadel, crisp, prism, subtract, union

DECK = 14.5
BLOCKS = [
    ('Central aft body', False, LOWER,
     [(-28,-12),(4,-12),(4,12),(-28,12)]),
    # Flat deck plateau at main-deck height for |y|<9 between the pods.
    ('Aft centreline deck', False, citadel(7.5),
     [(-28,-11.2),(-1,-11.2),(-1,11.2),(-28,11.2)]),
]
RAIL_Y = 6.8                 # hoist rails, the sprite's pale fore-aft lines
LID = (-16.8, -12.0, 5.6)     # reactor lift-out hatch: x0, x1, half-width
WELL = (-25.4, -17.4, 5.0)   # open plant-access well
WELL_FLOOR = 12.3


def rect(x0, x1, y0, y1):
    return [(x0,y0),(x1,y0),(x1,y1),(x0,y1)]


def plate(kit, name, x0, x1, y0, y1, z0, z1, mat, c=.25):
    # Flat plate, top edges chamfered all round: a facet, not a radius.
    return crisp(prism(kit, name, rect(x0,x1,*sorted((y0,y1))), [(0,z0),(0,z1-c),(c,z1)], mat), .06)


def build_body(kit):
    for name, _, profile, plan in BLOCKS:
        union(kit.hull, prism(kit, name, plan, profile))
    # The lid is hull, so the bridge's engine-room hatch seats on it.
    x0,x1,h = LID
    lid = [(x0+.8,-h),(x1-.5,-h),(x1,-h+.5),(x1,h-.5),(x1-.5,h),(x0+.8,h),(x0,h-.8),(x0,-h+.8)]
    union(kit.hull, prism(kit, 'Reactor lift-out hatch', lid, [(0,DECK-.2),(0,DECK+.9),(.6,DECK+1.4)]))


def build_deck(kit):
    s0 = DECK+.08
    # Dark deck skin: every gap between the armour plates below opens onto it.
    skin = kit.box('Aft deck seam skin', (-16.8,0,DECK+.04), (21.2,22.3,.08), kit.dark, 0)
    for ob in (kit.hull, skin):
        subtract(ob, kit.box('Temporary well cutter', ((WELL[0]+WELL[1])/2,0,WELL_FLOOR+3),
                             (WELL[1]-WELL[0],2*WELL[2],6), kit.dark, 0))
    x0,x1,h = WELL
    kit.box('Plant well floor', ((x0+x1)/2,0,WELL_FLOOR-.2), (x1-x0,2*h,.5), kit.dark, 0)
    for s in (1,-1):
        kit.box('Plant well side lining', ((x0+x1)/2,s*(h-.05),(WELL_FLOOR+DECK)/2), (x1-x0,.12,DECK-WELL_FLOOR+.1), kit.dark, 0)
    for x in (x0+.05,x1-.05):
        kit.box('Plant well end lining', (x,0,(WELL_FLOOR+DECK)/2), (.12,2*h,DECK-WELL_FLOOR+.1), kit.dark, 0)
    # Armour plates: three bays per side outboard of the rails, a strip
    # inboard of them, a plate aft of the well. Seams fall on common frames.
    frames = [(-12.7,-6.2),(-19.7,-13.3),(-27.0,-20.3)]
    for s in (1,-1):
        for k,(a,b) in enumerate(frames):
            if k != 1:
                plate(kit, 'Aft deck armour plate', a, b, s*7.6, s*10.85, s0, s0+.5, kit.orange)
                continue
            # Middle bay: engine-room ventilation grille, louvres in a frame.
            plate(kit, 'Engine room vent frame', a, b, s*7.6, s*10.85, s0, s0+.35, kit.steel, .1)
            kit.box('Vent grille well', ((a+b)/2,s*9.22,s0+.3), (b-a-1.0,2.45,.2), kit.dark, 0)
            for i in range(9):
                kit.box('Vent louvre', (a+.75+i*(b-a-1.5)/8,s*9.22,s0+.3), (.22,2.3,.24), kit.steel, .02)
        plate(kit, 'Well coaming plate', -27.0, WELL[1]+.2, s*5.35, s*6.2, s0, s0+.5, kit.orange, .15)
    plate(kit, 'Aft well plate', -27.0, WELL[0]-.4, -5.35, 5.35, s0, s0+.5, kit.orange)
    # Fore plate behind the rail-head frame: the bridge's round engine-room
    # hatch stands on it, ahead of the lid.
    fore = plate(kit, 'Fore deck plate', LID[1]+.3, -7.7, -6.2, 6.2, s0, s0+.5, kit.orange)
    subtract(fore, kit.cylinder('Temporary hatch cutter', (-9.6,0,s0), 2.6, 3, kit.dark, 'Z', 8, 0))
    # Deck-edge lip overhanging the side wall: a hard shadow line above the
    # pod valley (the engines' feed manifold sits under it at y 11.5).
    for s in (1,-1):
        kit.box('Aft deck edge lip', (-16.8,s*11.25,DECK-.15), (21.2,.9,.9), kit.steel, .1)
        # Valley wall: a steel strake under the lip and frame stiffeners,
        # placed clear of the pod feed lines (x -20..-24, z < 10.4).
        kit.box('Valley wall strake', (-16.8,s*11.3,12.65), (21.2,.3,.8), kit.steel, .06)
        for x in (-9.5,-13.0,-16.5,-26.2):
            kit.box('Valley wall stiffener', (x,s*11.35,10.0), (.6,.4,4.6), kit.steel, .06)


def build_lid(kit):
    x0,x1,h = LID
    top = DECK+1.4
    # Two leaves split on the centreline, hinged outboard on heavy knuckles.
    kit.box('Hatch leaf split', ((x0+x1)/2,0,top), (x1-x0-.8,.35,.12), kit.dark, 0)
    for s in (1,-1):
        for x in (x0+1.4,x1-1.6):
            kit.box('Hatch hinge knuckle', (x,s*(h-.35),top-.2), (1.4,.8,.8), kit.steel, .12)
        kit.box('Hatch dog bar', (x0+.8,s*2.8,top+.05), (.5,2.6,.25), kit.steel, .05)
        # Lifting eyes either side: the sprite's pale studs at (-11,+-9).
        kit.box('Lid lifting eye base', (-10.2,s*9.25,DECK+.8), (1.4,1.2,.5), kit.steel, .1)
        kit.cylinder('Lid lifting eye', (-10.2,s*9.25,DECK+1.55), .55, .9, kit.steel, 'Y', 12, .06)


def build_rails(kit):
    z = DECK+.08
    for s in (1,-1):
        # Rail: steel web with a pale running cap, splayed outboard at the
        # fore end toward the aft access trunks (the sprite's chevron).
        y = s*RAIL_Y
        plate(kit, 'Hoist rail web', -27.6, -8.6, y-.45, y+.45, z, z+.95, kit.steel, .1)
        plate(kit, 'Hoist rail cap', -27.4, -8.8, y-.3, y+.3, z+.95, z+1.15, kit.cream, .06)
        a,b = (-8.6,RAIL_Y), (-6.3,9.9)
        dx,dy = b[0]-a[0], b[1]-a[1]
        n = (-dy/math.hypot(dx,dy)*.45, dx/math.hypot(dx,dy)*.45)
        lead = [(a[0]-n[0],a[1]-n[1]),(b[0]-n[0],b[1]-n[1]),(b[0]+n[0],b[1]+n[1]),(a[0]+n[0],a[1]+n[1])]
        lead = [(px,s*py) for px,py in (lead if s > 0 else reversed(lead))]
        crisp(prism(kit, 'Hoist rail splay', lead, [(0,z),(0,z+.85),(.1,z+.95)], kit.steel), .06)
        for x in (-12.9,-19.9,-27.2):
            kit.box('Rail end stop', (x,y,z+1.1), (.5,1.1,.5), kit.dark, .05)
    # Transverse frame behind the capsule: ties the rail heads.
    plate(kit, 'Rail head cross frame', -7.4, -6.4, -RAIL_Y-.4, RAIL_Y+.4, DECK+.08, DECK+1.0, kit.steel, .12)
    plate(kit, 'Cross frame cap', -7.3, -6.5, -RAIL_Y, RAIL_Y, DECK+1.0, DECK+1.2, kit.cream, .06)


def build_well(kit):
    x0,x1,h = WELL
    # Louvred intake grille across the fore end of the well.
    for k in range(4):
        kit.box('Well grille louvre', (x1-.5-k*.45,0,DECK-.5), (.2,2*h-.3,.9), kit.steel, .02)
    # Plant heads standing in the well floor.
    for y in (-2.6,0,2.6):
        kit.cylinder('Plant head cap', (-24.3,y,WELL_FLOOR+.6), .9, 1.2, kit.steel, 'Z', 8, .08)
    kit.box('Plant head manifold', (-24.3,0,WELL_FLOOR+.25), (1.0,7.0,.6), kit.steel, .08)
    # Hoist bridge parked over the well: two end trucks riding the rails,
    # a crossbeam and a hoist housing with its cable drum; the grey module.
    top = DECK+1.3
    for x in (-19.0,-23.0):
        kit.box('Hoist bridge truck', (x,0,top+.4), (.9,2*RAIL_Y+1.4,.8), kit.steel, .12)
        for s in (1,-1):
            kit.box('Truck wheel box', (x,s*RAIL_Y,top+.1), (1.6,1.0,.6), kit.dark, .08)
    kit.box('Hoist housing', (-21,0,top+1.0), (3.4,5.6,1.2), kit.steel, .2)
    kit.box('Hoist housing roof plate', (-21,0,top+1.65), (2.6,4.6,.15), kit.cream, .05)
    kit.cylinder('Hoist cable drum', (-21,0,top+.15), .55, 5.0, kit.dark, 'Y', 12, .05)
    for y in (-1.2,1.2):
        kit.cylinder('Hoist fall', (-21,y,(WELL_FLOOR+top)/2), .12, top-WELL_FLOOR, kit.dark, 'Z', 12, 0)
    kit.box('Hoist hook block', (-21,0,WELL_FLOOR+1.6), (1.0,3.2,.7), kit.steel, .08)


def build_stern(kit):
    # Bolted cross girder on the deck's aft edge, overhanging the plant face.
    kit.box('Stern cross girder', (-28.4,0,DECK-.6), (2.2,22.6,2.2), kit.steel, .15)
    for y in (-8.5,-4.2,0,4.2,8.5):
        kit.cylinder('Girder bolt head', (-28.4,y,DECK+.55), .23, .25, kit.steel, 'Z', 6, .03)   # 0.15 m hex
    # Engine-room aft bulkhead under the girder: a deep plinth, an upper
    # face stepped in above it, stiffeners and a door onto the cradle beam.
    kit.box('Plant aft plinth', (-29.4,0,-1.2), (2.8,19.2,6.6), kit.steel, .2)
    kit.box('Plinth shadow seam', (-30.75,0,2.1), (.2,18.6,.25), kit.dark, 0)
    # The plinth's aft face below the cradle beam: the bulkhead stiffeners carried
    # down to the keel and two bolted service covers between them.
    for y in (-6.6,6.6):
        kit.box('Plinth stiffener', (-30.95,y,-1.3), (.5,.8,6.2), kit.steel, .08)
    for y in (-3.3,3.3):
        kit.box('Plinth service cover', (-30.88,y,-1.9), (.25,3.2,2.6), kit.steel, .06)
        for dy in (-1.3,1.3):
            for dz in (-1.0,1.0):
                kit.cylinder('Plinth cover bolt', (-31.05,y+dy,-1.9+dz), .12, .12, kit.steel, 'X', 6, 0)
    kit.box('Plant aft bulkhead', (-29.1,0,7.3), (2.2,18.4,10.4), kit.steel, .2)
    kit.box('Bulkhead door recess', (-30.15,0,8.6), (.2,4.6,4.6), kit.dark, 0)
    kit.box('Bulkhead door', (-30.2,0,8.4), (.25,3.8,4.0), kit.steel, .08)
    kit.box('Door lintel', (-30.35,0,11.15), (.5,5.4,.5), kit.cream, .08)
    for y in (-2.6,2.6):
        kit.box('Door jamb', (-30.35,y,8.6), (.5,.6,4.6), kit.cream, .08)
    for y in (-6.6,6.6):
        kit.box('Bulkhead stiffener', (-30.4,y,6.2), (.7,.8,12), kit.steel, .1)
        kit.box('Bulkhead vent', (-30.2,y*1.25,8.8), (.2,1.6,3.4), kit.dark, 0)
    # Docking/tow cradle: the sprite's dark "[" closing the notch. A cross
    # beam and two arms running aft, each ending in an inward clamp jaw.
    kit.box('Cradle cross beam', (-31.6,0,3.5), (2.0,25,4.6), kit.steel, .15)
    kit.box('Cradle beam shadow web', (-31.0,0,6.0), (1,23,.6), kit.dark, 0)
    for s in (1,-1):
        kit.box('Cradle arm', (-35.1,s*11.4,3.5), (5.0,2.0,4.6), kit.steel, .15)
        kit.box('Cradle clamp jaw', (-37.0,s*10.2,3.5), (1.6,2.6,5.8), kit.steel, .15)
        kit.box('Clamp jaw pad', (-37.0,s*8.85,3.5), (1.2,.3,4.4), kit.dark, 0)
        kit.box('Cradle arm seam', (-32.6,s*11.4,3.5), (.25,2.1,4.7), kit.dark, 0)
        # Machinery prongs outboard of the arms: an armoured fore block, a
        # dark seam, then the winch bay that works the cradle.
        plate(kit, 'Prong armour block', -32.0, -26.0, s*12.5, s*17.6, -2.5, 8.6, kit.orange, .8)
        kit.box('Prong seam', (-32.25,s*15.05,3), (.5,5,11), kit.dark, 0)
        plate(kit, 'Winch bay', -36.8, -32.5, s*12.5, s*17.6, -2.5, 6.2, kit.steel, .5)
        kit.cylinder('Cradle winch drum', (-34.6,s*15.0,7.3), 1.5, 4.0, kit.steel, 'Y', 12, .1)
        for y in (13.0,17.0):
            kit.box('Winch cheek plate', (-34.6,s*y,6.9), (3.6,.35,2.8), kit.dark, .05)
        kit.box('Winch bay louvre recess', (-36.85,s*15.05,2.0), (.2,3.6,4.0), kit.dark, 0)
        kit.cylinder('Cradle hydraulic line', (-30.8,s*9.9,8.2), .4, 1.6, kit.steel, 'X', 12, .03)


def build_details(kit):
    build_deck(kit)
    build_lid(kit)
    build_rails(kit)
    build_well(kit)
    build_stern(kit)
