"""Aft drive pods, the centre body between them and the stern machinery.

Each aft "arm" of the Cerberus planform is a drive unit bolted to the ship,
not hull with nozzles poking out of it. A pod is built as engine casing in
distinct sections along X, each its own block with a dark seam between:
a steel intake cap, the armoured forward casing that carries the turret
seat, a steel reinforcing band, a heat-sink section with fin banks, a
second band, and a low steel thrust housing whose whole aft end is the
exhaust assembly, the two bells recessed into it. A dark core behind the
sections shows through every seam. Feed lines cross the neck valley from
the centre body. Sections use vertical walls and single chamfers only.
"""
import math
import bpy
import bmesh
import sw_kit
from sw_hull import armor_patch, prism, union

# Vanilla warhound.ship / Cerberus engineSlots: exact XY and width ratios.
# Four fixed nozzles, two lateral pairs, all at Z=0 and pointed aft (-X).
ENGINES = [(-41,31,12),(-39,24,8),(-39,-24,8),(-41,-31,12)]
# Solid bell mouth radius per slot width: 3:2, the pair just clearing each
# other in stern view because the outer bell sits further aft.
BELL_R = {12:3.9, 8:2.6}
FACE_GAP = .6  # bell lip stands this far aft of the housing face

# The low pylon tying each pod to the centre body (sw_aft_deck).
BLOCKS = [
    # The pylon stays below the waist deck: a dark valley separates every
    # pod from the centre body in plan, bridged only by feed lines.
    ('Drive pod pylon', True, [(1,-6.5),(0,-5),(0,6.8),(.8,7.6)],
     [(-26,10.5),(0,10.5),(0,13),(-5,16.2),(-26,16.2)]),
]

# Pod sections, port side, fore to aft. Plans CCW; profiles (inset, z).
CAP_PLAN = [(-2.4,21.5),(1.6,22.3),(3.2,24.5),(3.2,34.5),(1.6,36.7),(-2.4,37.5)]
CAP_PROFILE = [(1,-5.5),(0,-4.5),(0,10.5),(1,11.5)]
# Forward casing: the tallest block, its inboard face swept back clear of
# the bridge's aft access trunk. The armoured upper cowl is part of the hull
# so the turret seats on it; it overhangs a steel lower casing at a seam.
CASING_PLAN = [(-17,15.5),(-7.5,15.5),(-3,20),(-3,35.5),(-5.5,38.5),(-17,38.5)]
COWL_PROFILE = [(0,3),(0,13.5),(2.5,16)]
LOWER_CASING_PROFILE = [(2,-7.8),(.6,-6.2),(.6,2.6)]
# Sections running along X: (y0, y1, z0, z1, chamfer) in the YZ plane.
# Reinforcing bands stand proud of the casing all round: (x0, x1).
# The aft band only clears the heat sink, so the pod steps down toward
# the stern: cowl, band, sink, band, thrust bays.
BANDS = [(-18.6,-17,16.5),(-27,-25.4,14.6)]
BAND_SEC = (15,39,-8.3,2.5)
# Heat-sink section between the bands, inset so casing and bands overhang it.
SINK = (-25.4,-18.6)
SINK_SEC = (16.7,37.3,-7.3,14,2.5)
FIN_X = [-19.3-k*.95 for k in range(7)]
# Thrust housing: one bay per bell, a dark seam between them. The outer bay
# is taller and longer, so the aft face steps like the sprite's two blocks.
BAYS = [(-40.4,27.1,37.8,-7.6,8.5,2.2),(-38.4,20,26.7,-5,6.5,1.6)]
HOUSING_X = -27.6
CORE = ([(-27.8,21),(-.4,21),(-.4,36.5),(-27.8,36.5)], [(0,-4.5),(0,11)])
# Turret 1/2 seat: an octagonal platform cantilevered over the inboard edge.
SEAT = (-11,20,5.0,17.0)


def mirror(plan, side):
    return plan if side > 0 else [(x,-y) for x,y in reversed(plan)]


def bevel(obj, width):
    b = obj.modifiers.new('Manufactured edge radii', 'BEVEL')
    b.width, b.segments = width, 2
    return obj


def block(kit, name, plan, profile, mat, side, edge=.15):
    return bevel(prism(kit, name, mirror(plan, side), profile, mat), edge)


def slab(kit, name, x0, x1, sec, mat, side, edge=.15):
    # Prism along X from a chamfered rectangle in the YZ plane: the chamfers
    # run lengthwise only, so a thin band keeps square fore and aft faces.
    y0,y1,z0,z1,c = sec
    yz = [(y0,z0+c),(y0+c,z0),(y1-c,z0),(y1,z0+c),(y1,z1-c),(y1-c,z1),(y0+c,z1),(y0,z1-c)]
    n = len(yz)
    v = [(x,side*y,z) for x in (x0,x1) for y,z in yz]
    f = [tuple(range(n)), tuple(range(2*n-1,n-1,-1))]
    f += [(i,n+i,n+(i+1)%n,(i+1)%n) for i in range(n)]
    ob = kit.mesh(name, v, f, mat)
    bm = bmesh.new(); bm.from_mesh(ob.data)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces); bm.to_mesh(ob.data); bm.free()
    return bevel(ob, edge)


def cut(obj, cutter):
    bpy.context.view_layer.objects.active = obj
    mod = obj.modifiers.new('Recess', 'BOOLEAN')
    mod.operation, mod.object, mod.solver = 'DIFFERENCE', cutter, 'MANIFOLD'
    bpy.ops.object.modifier_apply(modifier=mod.name)
    bpy.data.objects.remove(cutter, do_unlink=True)


def octagon(cx, cy, r):
    return [(cx+r*math.cos((k+.5)*math.pi/4), cy+r*math.sin((k+.5)*math.pi/4)) for k in range(8)]


def bell_face(x):
    # Housing face for an engine whose bell mouth sits at x.
    return x+FACE_GAP


def build_pods(kit):
    for name, mirrored, profile, plan in BLOCKS:
        for side in ((1,-1) if mirrored else (1,)):
            union(kit.hull, prism(kit, name, mirror(plan, side), profile))
    for s in (1,-1):
        # Casing and turret seat join the hull: the turret seats by ray-cast.
        union(kit.hull, prism(kit, 'Drive casing armoured cowl', mirror(CASING_PLAN, s), COWL_PROFILE))
        block(kit, 'Drive lower casing', CASING_PLAN, LOWER_CASING_PROFILE, kit.steel, s)
        x,y,r,top = SEAT
        union(kit.hull, prism(kit, 'Turret mount platform', octagon(x,s*y,r), [(0,13.5),(0,top-.5),(.5,top)]))
        # Steel skin over the seat so it reads as a bolted mount, not hull.
        prism(kit, 'Turret mount platform skin', octagon(x,s*y,r+.12), [(0,13.6),(0,top-.4),(.4,top+.03)], kit.steel)
        prism(kit, 'Drive pod dark core', mirror(CORE[0], s), CORE[1], kit.dark)
        block(kit, 'Drive intake cap', CAP_PLAN, CAP_PROFILE, kit.steel, s)
        for x0,x1,top in BANDS:
            y0,y1,z0,c = BAND_SEC
            slab(kit, 'Drive reinforcing band', x0, x1, (y0,y1,z0,top,c), kit.steel, s, .2)
        slab(kit, 'Heat-sink section', *SINK, SINK_SEC, kit.dark, s, .1)
        # Thrust bays, each with its bell well sunk into the aft face.
        for (xa,y0,y1,z0,z1,c),(ex,ey,w) in zip(BAYS, ENGINES[:2]):
            bay = slab(kit, 'Thrust bay', xa, HOUSING_X, (y0,y1,z0,z1,c), kit.steel, s, .2)
            well = kit.cylinder('Temporary bell well', (bell_face(ex),s*ey,0), BELL_R[w]+.45, 7, kit.dark, 'X', 32, 0)
            cut(bay, well)
        kit.box('Thrust bay seam backing', (-33,s*26.9,0), (10,.8,11), kit.dark, 0)
        # Dark flange gasket on the aft band: a shadow line around both bays.
        kit.box('Thrust bay flange gasket', (-27.25,s*28.85,.6), (.5,19.3,17.4), kit.dark, 0)


def build_casing(kit, s, red):
    # Deck armour on the casing, clear of the turret seat.
    for x0,x1,y0,y1,mat in [(-14.3,-9.4,26.3,30.9,kit.orange),(-14.3,-9.4,31.6,35.7,kit.cream),
                            (-8.6,-6.1,26.3,34.9,kit.cream)]:
        armor_patch(kit, 'Drive casing armour plate', x0, x1, s*y0, s*y1, mat)
    # Outboard access panels: dark frame, cream panel, steel grab strap.
    for xc,length in ((-8.6,5.6),(-14.3,4.6)):
        kit.box('Casing access panel frame', (xc,s*38.55,8.3), (length,.3,8.2), kit.dark, .05)
        kit.box('Casing access panel', (xc,s*38.75,8.3), (length-.8,.4,7.4), kit.cream, .12)
        kit.box('Panel grab strap', (xc,s*39,11), (length-2.4,.3,.5), kit.steel, .08)
    # Lower casing: drain slots and bolted stiffeners on the machinery skin.
    for x in (-6,-9.5,-13,-16):
        kit.box('Lower casing stiffener', (x,s*38,-1), (.6,.5,6), kit.steel, .1)
    for x in (-7.8,-11.3,-14.5):
        kit.box('Lower casing drain slot', (x,s*37.95,0), (1.6,.2,3.2), kit.dark, .03)
    # Inboard: a feed manifold under the cantilevered seat.
    kit.box('Casing feed manifold', (-9,s*15.3,4), (9,.8,3), kit.steel, .2)
    # Intake: a dark louvred mouth recessed into the cap's forward face.
    kit.box('Intake mouth', (3.0,s*29.5,3), (.6,7.6,8.4), kit.dark, .05)
    for z in (0,1.5,3,4.5,6):
        kit.box('Intake louver', (3.3,s*29.5,z), (.4,7.2,.35), kit.steel, .04)
    kit.box('Intake splitter', (3.4,s*29.5,3), (.5,.5,8.4), kit.steel, .06)


def build_sink(kit, s):
    x0,x1 = SINK
    y0,y1 = SINK_SEC[:2]
    # Fin banks: transverse plates across the deck and down the outboard flank.
    for x in FIN_X:
        kit.box('Heat-sink deck fin', (x,s*(y0+y1)/2,15), (.35,y1-y0-4,2.2), kit.steel, .03)
        kit.box('Heat-sink flank fin', (x,s*(y1+.75),4), (.35,1.6,15), kit.steel, .03)
    for z in (-3.6,11.6):
        kit.box('Fin bank rail', ((x0+x1)/2,s*(y1+1.2),z), (x1-x0-.3,.4,.5), kit.steel, .05)
    for y in (19,27,35):
        kit.box('Fin bank tie bar', ((x0+x1)/2,s*y,15.7), (x1-x0-.3,.5,.4), kit.steel, .05)


def build_housing(kit, s, red):
    # Pump machinery on the housing deck: the grey blocks of the sprite.
    kit.box('Turbopump housing', (-33.5,s*32,9.4), (8.5,6.5,2.2), kit.steel, .3)
    kit.box('Turbopump vent slot', (-33.5,s*32,10.55), (6.5,.6,.1), kit.dark, 0)
    for x in (-31,-36):
        kit.cylinder('Pump drive drum', (x,s*32,11.2), 1.4, 5.5, kit.steel, 'Y', 16, .1)
    kit.cylinder('Inner pump drum', (-33,s*23.4,7.9), 1.5, 8.4, kit.steel, 'X', 16, .1)
    for x in (-30,-36):
        kit.box('Pump strap', (x,s*23.4,7.9), (.6,3.4,3.4), kit.dark, .1)
    # Hot-section lights on the aft deck corners.
    for x,y,z in ((-39.6,36.4,8.6),(-37.6,21.4,6.6)):
        kit.box('Drive warning light', (x,s*y,z), (.6,.6,.5), red, .05)
    # Bulkhead on the aft band above the housing: hatch and vent louvres.
    kit.box('Aft band hatch', (-27.15,s*31,11.8), (.3,5,3.4), kit.dark, .05)
    for z in (10.8,11.8,12.8):
        kit.box('Aft band louver', (-27.3,s*31,z), (.2,4.4,.3), kit.steel, .03)
    kit.box('Housing flank seam strap', (-34,s*37.95,1), (12,.3,.6), kit.dark, 0)
    for x in (-30.6,-35.4):
        kit.box('Thrust bay frame rib', (x,s*38,0), (.9,.6,11.5), kit.steel, .15)


def build_feeds(kit, s):
    # Coolant and fuel lines cross the neck valley from the centre body
    # into the heat-sink section, flanged at each wall.
    for x in (-20.4,-21.9,-23.4):
        kit.cylinder('Coolant feed line', (x,s*14,9.4), .45, 5.6, kit.steel, 'Y', 16, .05)
        for y in (11.6,16.4):
            kit.cylinder('Feed line flange', (x,s*y,9.4), .75, .35, kit.steel, 'Y', 16, .03)
    kit.box('Centre body feed manifold', (-21.9,s*11.5,9.4), (4.4,.6,1.8), kit.steel, .15)
    # Fuel trunks run along the valley floor into the forward casing.
    for y in (12.0,13.2):
        kit.cylinder('Fuel feed trunk', (-11,s*y,8.1), .5, 22, kit.steel, 'X', 16, .05)


def build_details(kit):
    red = sw_kit.material('Drive warning light', (.6,.05,.03), emission=(1,.08,.04))
    for s in (1,-1):
        build_casing(kit, s, red)
        build_sink(kit, s)
        build_housing(kit, s, red)
        build_feeds(kit, s)


def ring(kit, name, x0, x1, y, r_in, r_out, mat, n=32):
    # Annulus along X: octagonal outer collar plate around a round bore.
    def pt(r, k, oct_):
        a = k*math.tau/n
        if oct_:
            r = r*math.cos(math.pi/8)/math.cos((a % (math.pi/4))-math.pi/8)
        return (y+r*math.cos(a), r*math.sin(a))
    v = []
    for x in (x0,x1):
        v += [(x,*pt(r_out,k,True)) for k in range(n)] + [(x,*pt(r_in,k,False)) for k in range(n)]
    f = []
    for k in range(n):
        j = (k+1) % n
        f += [(k,j,2*n+j,2*n+k), (n+j,n+k,3*n+k,3*n+j), (j,k,n+k,n+j), (2*n+k,2*n+j,3*n+j,3*n+k)]
    return kit.mesh(name, v, f, mat, bevel=.06)


def build_nozzles(kit):
    for i,(x,y,w) in enumerate(ENGINES,1):
        r = BELL_R[w]
        face = bell_face(x)
        # Octagonal shroud collar on the housing face, round bore through it.
        ring(kit, f'Engine {i} shroud collar', face+.2, face-.6, y, r+.45, r+1.35, kit.steel)
        # Open bell: wide mouth at the vanilla engine point narrowing to a
        # dark throat deep in the well, so the casing reads as the exhaust.
        ev = []
        for ex,rr in ((x,r),(x+3.4,r*.55)):
            ev += [(ex,y+rr*math.cos(t*math.tau/48),rr*math.sin(t*math.tau/48)) for t in range(48)]
        ef = [(t,(t+1)%48,48+(t+1)%48,48+t) for t in range(48)]
        kit.mesh(f'Engine {i} aft-facing bell', ev, ef, kit.dark)
        kit.cylinder(f'Engine {i} dark throat', (x+3.4,y,0), r*.55, .1, kit.dark, 'X', 32, 0)
        kit.cylinder(f'Engine {i} throat ring', (x+2.6,y,0), r*.72, .3, kit.steel, 'X', 32, .03)
        bpy.ops.mesh.primitive_torus_add(major_radius=r, minor_radius=.3, major_segments=48,
                                         minor_segments=8, location=(x,y,0), rotation=(0,math.pi/2,0))
        kit.finish(bpy.context.object, f'Engine {i} mouth lip', kit.steel)
