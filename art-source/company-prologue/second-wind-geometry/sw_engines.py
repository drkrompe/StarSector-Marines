"""Aft drive pods: the two engine units bolted either side of the stern notch.

Each aft "arm" of the Cerberus planform is a drive unit bolted to the ship,
not hull with nozzles poking out of it. A pod is engine casing in distinct
sections along its thrust axis, each its own solid with a dark seam between:
a tapered intake cap, an armoured cowl over a steel lower casing (carrying
the turret seat), a reinforcing barrel band, a finned heat-sink section, a
second band, a stepped-down thrust housing, and two faceted engine cans
whose aft faces are the exhausts, the bells sunk into them. A dark core
shows through every seam; feed lines cross the valley from the centre body.

Sections are lofted along X from faceted cross-sections: a rounded rectangle
whose corners are cut by two or three flat facets, so the casing reads as
machined barrel plate rather than a box, and stays flat-shaded and crisp
rather than inflated. Tapering stations make the transitions: the cap
narrows forward, the cowl face is raked, the housing steps down aft.
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
FACE_GAP = .6  # bell lip stands this far aft of the can face

# The low pylon tying each pod to the centre body (sw_aft_deck). It stays
# below the waist deck: a dark valley bridged only by feed lines.
PYLON = ([(1,-6.5),(0,-5),(0,6.8),(.8,7.6)],
         [(-26,10.5),(0,10.5),(0,13),(-5,16.2),(-26,16.2)])

# Cross-sections, port side: (y0, y1, z0, z1, (inboard, outboard) top
# corner radius, bottom corner radius). Top corners take three facets so
# the deck reads curved; bottom corners two.
def S(y0, y1, z0, z1, rt, rb):
    return (y0, y1, z0, z1, rt if isinstance(rt, tuple) else (rt, rt), rb)

CASE = S(15.5,38.5,-7.8,16,(7.5,6.5),5.5)
# Intake cap: narrows forward to a raked intake ring.
CAP = [(-2.4,S(21.5,36.5,-5.5,11.5,(6,5.5),4.5)),(0,S(21.5,36.5,-5.5,11.5,(6,5.5),4.5)),
       (3.2,S(23.6,34.4,-3.6,9.6,(4.6,4.2),3.4))]
# Cowl over the casing: raked forward face, inboard face swept back clear of
# the bridge's aft access trunk. Upper part is armour (joins the hull so the
# turret seats on it); lower casing is steel, inset under a seam.
SPLIT = 3
CASING_X = [(-3,S(20,35.5,-6,14,(5.5,4.5),4)),(-6.5,CASE),(-17,CASE)]
# Reinforcing barrel bands (x0, x1, section): proud of everything around
# them; the aft one lower, so the pod steps down toward the stern.
BANDS = [(-17,-18.6,S(15,39,-8.3,16.5,(8,7),6)),(-25.4,-27,S(15,39,-8.3,14.8,(7.2,6.5),6))]
SINK = (-18.6,-25.4,S(16.7,37.3,-7.3,13.4,(6.5,5.5),4.8))
FIN = S(15.7,38.3,-8,14.4,(7.2,6.2),5.5)
FIN_X = [-19.3-k*.95 for k in range(7)]
# Thrust housing: the step down from the bands to the cans, aft face raked.
HOUSING = [(-27.6,S(19.6,38,-6.8,9.8,(5,4.5),4.5)),(-31.4,S(19.6,38,-6.8,9.8,(5,4.5),4.5)),
           (-33.4,S(21.6,36,-5,7.4,(4,4),3.4))]
# Engine cans: 12-sided shrouds (x, circumradius) per engine, with a proud
# band and a taper to the face. The pair shares a split line with a dark
# seam: outer can inboard of it is cut away, inner can outboard of it.
CAN = {12:[(-29,5.8),(-34.6,5.8),(-34.6,6.25),(-35.8,6.25),(-35.8,5.8),(-36.4,5.8)],
       8:[(-29,4.4),(-34.1,4.4),(-34.1,4.8),(-35.1,4.8),(-35.1,4.4),(-35.6,4.4)]}
CAN_FACE_R = {12:5.6, 8:4.2}
SPLIT_Y = {12:27.1, 8:26.7}
CORE = [(-.4,S(22.5,35.5,-4.5,10.5,(3.5,3),2.5)),(-3,S(21,34.5,-5,13,(3.5,3),2.5)),
        (-6.5,S(17,37,-6.5,13.5,(6.5,5.5),4.5)),(-27.8,S(17,37,-6.5,13.5,(6.5,5.5),4.5))]
# Turret 1/2 seat: an octagonal platform cantilevered over the inboard shoulder.
SEAT = (-11,20,5.0,17.0)


def rr(sec, grow=0, top=True, bottom=True):
    """Faceted rounded rectangle in YZ, CCW. top/bottom False = square edge."""
    y0,y1,z0,z1,(ri,ro),rb = sec
    y0,y1,z0,z1,ri,ro,rb = y0-grow,y1+grow,z0-grow,z1+grow,ri+grow,ro+grow,rb+grow
    pts = []
    for cy,cz,r,a0,n,on in [(y0,z0,rb,180,2,bottom),(y1,z0,rb,270,2,bottom),
                            (y1,z1,ro,0,3,top),(y0,z1,ri,90,3,top)]:
        if not on:
            pts.append((cy,cz)); continue
        oy = cy+(r if cy == y0 else -r)
        oz = cz+(r if cz == z0 else -r)
        pts += [(oy+r*math.cos(math.radians(a0+90*k/n)), oz+r*math.sin(math.radians(a0+90*k/n))) for k in range(n+1)]
    return pts


def loft(kit, name, rings, mat, side, edge=.15):
    """Closed solid through YZ rings at successive X stations, mirrored by side."""
    n = len(rings[0][1])
    v = [(x,side*y,z) for x,pts in rings for y,z in pts]
    f = [tuple(range(n)), tuple(range(len(v)-n,len(v)))]
    for r in range(len(rings)-1):
        f += [(r*n+i,r*n+(i+1)%n,(r+1)*n+(i+1)%n,(r+1)*n+i) for i in range(n)]
    ob = kit.mesh(name, v, f, mat)
    bm = bmesh.new(); bm.from_mesh(ob.data)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces); bm.to_mesh(ob.data); bm.free()
    return bevel(ob, edge) if edge else ob


def ngon(cy, cz, r, n=12):
    # Flats at top, bottom and sides.
    return [(cy+r*math.cos(math.pi/n*(2*k+1)), cz+r*math.sin(math.pi/n*(2*k+1))) for k in range(n)]


def bevel(obj, width):
    b = obj.modifiers.new('Manufactured edge radii', 'BEVEL')
    b.width, b.segments = width, 2
    return obj


def cut(obj, cutter):
    bpy.context.view_layer.objects.active = obj
    mod = obj.modifiers.new('Recess', 'BOOLEAN')
    mod.operation, mod.object, mod.solver = 'DIFFERENCE', cutter, 'MANIFOLD'
    bpy.ops.object.modifier_apply(modifier=mod.name)
    bpy.data.objects.remove(cutter, do_unlink=True)


def octagon(cx, cy, r):
    return [(cx+r*math.cos((k+.5)*math.pi/4), cy+r*math.sin((k+.5)*math.pi/4)) for k in range(8)]


def bell_face(x):
    # Can face for an engine whose bell mouth sits at x.
    return x+FACE_GAP


def build_cans(kit, s):
    for ex,ey,w in ENGINES[:2]:
        face = bell_face(ex)
        rings = [(x,ngon(ey,0,r)) for x,r in CAN[w]] + [(face,ngon(ey,0,CAN_FACE_R[w]))]
        can = loft(kit, 'Thrust bay engine can', rings, kit.steel, s, 0)
        # Trim the shared side flat; the dark seam backing fills the gap.
        sy = SPLIT_Y[w]
        cut(can, kit.box('Temporary can trim', (-34,s*(sy+(-6 if w == 12 else 6)),0), (16,12,16), kit.dark, 0))
        cut(can, kit.cylinder('Temporary bell well', (face,s*ey,0), BELL_R[w]+.45, 7, kit.dark, 'X', 32, 0))
        bevel(can, .15)
    kit.box('Thrust bay seam backing', (-33.5,s*26.9,0), (9,.5,8.6), kit.dark, 0)


def build_pods(kit):
    for s in (1,-1):
        plan = PYLON[1] if s > 0 else [(x,-y) for x,y in reversed(PYLON[1])]
        union(kit.hull, prism(kit, 'Drive pod pylon', plan, PYLON[0]))
        # Armoured cowl and turret seat join the hull: the turret seats by ray-cast.
        cowl = [(x,rr(sec,bottom=False)) for x,sec in CASING_X]
        cowl = [(x,[(y,max(z,SPLIT)) for y,z in pts]) for x,pts in cowl]
        union(kit.hull, loft(kit, 'Drive casing armoured cowl', cowl, kit.orange, s, 0))
        lower = [(x,[(y,min(z,SPLIT-.4)) for y,z in rr(sec,-.6,top=False)]) for x,sec in CASING_X]
        loft(kit, 'Drive lower casing', lower, kit.steel, s)
        x,y,r,top = SEAT
        union(kit.hull, prism(kit, 'Turret mount platform', octagon(x,s*y,r), [(0,13.5),(0,top-.5),(.5,top)]))
        # Steel skin over the seat so it reads as a bolted mount, not hull.
        prism(kit, 'Turret mount platform skin', octagon(x,s*y,r+.12), [(0,13.6),(0,top-.4),(.4,top+.03)], kit.steel)
        loft(kit, 'Drive pod dark core', [(x,rr(sec)) for x,sec in CORE], kit.dark, s, 0)
        loft(kit, 'Drive intake cap', [(x,rr(sec)) for x,sec in CAP], kit.steel, s)
        for x0,x1,sec in BANDS:
            # Barrel band with chamfered fore and aft edges.
            loft(kit, 'Drive reinforcing band', [(x0,rr(sec,-.35)),(x0-.35,rr(sec)),(x1+.35,rr(sec)),(x1,rr(sec,-.35))], kit.steel, s, .1)
        x0,x1,sec = SINK
        loft(kit, 'Heat-sink section', [(x0,rr(sec)),(x1,rr(sec))], kit.dark, s, 0)
        loft(kit, 'Thrust housing', [(x,rr(sec)) for x,sec in HOUSING], kit.steel, s, .2)
        build_cans(kit, s)


def build_casing(kit, s, red):
    # Deck armour on the cowl's flat top, clear of the turret seat.
    for x0,x1,y0,y1,mat in [(-14.3,-9.4,26.3,30.9,kit.orange),(-14.3,-9.4,31.3,32.6,kit.cream),
                            (-8.6,-6.8,26.3,32.6,kit.cream)]:
        armor_patch(kit, 'Drive casing armour plate', x0, x1, s*y0, s*y1, mat)
    # Outboard access panels on the cowl wall: dark frame, cream panel, strap.
    for xc,length in ((-8.9,4.6),(-14.2,4.6)):
        kit.box('Casing access panel frame', (xc,s*38.55,6.6), (length,.3,6.2), kit.dark, .05)
        kit.box('Casing access panel', (xc,s*38.75,6.6), (length-.8,.4,5.4), kit.cream, .12)
        kit.box('Panel grab strap', (xc,s*39,8.7), (length-2.4,.3,.5), kit.steel, .08)
    # Lower casing: drain slots and bolted stiffeners on the machinery skin.
    for x in (-6.4,-9.5,-13,-16):
        kit.box('Lower casing stiffener', (x,s*38,0), (.6,.5,4), kit.steel, .1)
    for x in (-7.9,-11.3,-14.5):
        kit.box('Lower casing drain slot', (x,s*37.95,0), (1.6,.2,3.2), kit.dark, .03)
    # Inboard: a feed manifold under the cantilevered seat.
    kit.box('Casing feed manifold', (-9,s*15.3,4), (9,.8,3), kit.steel, .2)
    # Intake: a dark faceted mouth on the cap's raked face, louvred across.
    kit.mesh('Intake mouth', [(x,s*y,z) for x in (3.1,3.35) for y,z in rr(S(25,34,-1.6,7.6,2.6,2.2))],
             [tuple(range(14)), tuple(range(27,13,-1))] + [(i,(i+1)%14,14+(i+1)%14,14+i) for i in range(14)], kit.dark)
    for z in (0,1.5,3,4.5,6):
        kit.box('Intake louver', (3.4,s*29.5,z), (.3,7.4,.35), kit.steel, .04)
    kit.box('Intake splitter', (3.45,s*29.5,3), (.4,.5,9.2), kit.steel, .06)


def build_sink(kit, s):
    x0,x1,sec = SINK
    # Cooling fins: thin faceted rings around the barrel, dark sink between.
    for x in FIN_X:
        loft(kit, 'Heat-sink fin', [(x+.17,rr(FIN)),(x-.17,rr(FIN))], kit.steel, s, .03)
    # Straps tying the fin bank, on the deck and the outboard flank.
    for y,z in ((25,FIN[3]+.15),(31,FIN[3]+.15),(FIN[1]+.15,-1),(FIN[1]+.15,7)):
        dy,dz = ((.5,.35) if z > 10 else (.35,.5))
        kit.box('Fin bank tie bar', ((x0+x1)/2,s*y,z), (x0-x1-.3,dy,dz), kit.steel, .05)


def build_housing(kit, s, red):
    # Pump machinery on the housing deck: the grey blocks of the sprite.
    kit.box('Turbopump housing', (-30,s*32,10.5), (4.4,6,1.6), kit.steel, .3)
    kit.box('Turbopump vent slot', (-30,s*32,11.35), (3.4,.6,.1), kit.dark, 0)
    for y in (29.6,34.4):
        kit.cylinder('Pump drive drum', (-30.8,s*y,12.1), 1.1, 3.4, kit.steel, 'X', 12, .08)
    kit.cylinder('Inner pump drum', (-30,s*23.6,10.9), 1.3, 4.8, kit.steel, 'X', 12, .08)
    for x in (-28.6,-31.4):
        kit.box('Pump strap', (x,s*23.6,10.9), (.5,3,2.9), kit.dark, .08)
    # Feed pipes down the raked aft face into each can.
    for y,z1 in ((32,5.8),(23.6,4.4)):
        kit.cylinder('Can feed pipe', (-33.2,s*y,(z1+8.6)/2), .45, 8.6-z1, kit.steel, 'Z', 12, .04)
    # Hot-section lights on the housing's aft corners.
    for y in (36.2,21.4):
        kit.box('Drive warning light', (-32.8,s*y,7.9), (.6,.6,.5), red, .05)
    # Bulkhead on the aft band above the housing: hatch and vent louvres.
    kit.box('Aft band hatch', (-27.15,s*31,12), (.3,5,3), kit.dark, .05)
    for z in (11.1,12,12.9):
        kit.box('Aft band louver', (-27.3,s*31,z), (.2,4.4,.3), kit.steel, .03)
    kit.box('Housing flank seam strap', (-30,s*38.05,1.5), (5.5,.3,.6), kit.dark, 0)
    kit.box('Housing frame rib', (-29.8,s*38.1,1.5), (.9,.6,8.5), kit.steel, .15)


def build_feeds(kit, s):
    # Coolant and fuel lines cross the neck valley from the centre body
    # into the heat-sink fin bank, flanged at the centre-body wall.
    for x in (-20.7,-21.65,-22.6):
        kit.cylinder('Coolant feed line', (x,s*13.8,9.4), .4, 4.6, kit.steel, 'Y', 12, .05)
        kit.cylinder('Feed line flange', (x,s*11.6,9.4), .7, .35, kit.steel, 'Y', 12, .03)
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


def ring(kit, name, x0, x1, y, r_in, r_out, mat, n=48):
    # Annulus along X: 12-sided outer collar plate around a round bore.
    def pt(r, k, flat):
        a = k*math.tau/n
        if flat:
            r = r/math.cos(((a+math.pi/12) % (math.pi/6))-math.pi/12)
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
        # Faceted shroud collar on the can face, round bore through it.
        ring(kit, f'Engine {i} shroud collar', face+.2, face-.6, y, r+.45, CAN_FACE_R[w]*math.cos(math.pi/12)-.15, kit.steel)
        # Open bell: wide mouth at the vanilla engine point narrowing to a
        # dark throat deep in the well, so the can reads as the exhaust.
        ev = []
        for ex,rr_ in ((x,r),(x+3.4,r*.55)):
            ev += [(ex,y+rr_*math.cos(t*math.tau/48),rr_*math.sin(t*math.tau/48)) for t in range(48)]
        ef = [(t,(t+1)%48,48+(t+1)%48,48+t) for t in range(48)]
        kit.mesh(f'Engine {i} aft-facing bell', ev, ef, kit.dark)
        kit.cylinder(f'Engine {i} dark throat', (x+3.4,y,0), r*.55, .1, kit.dark, 'X', 32, 0)
        kit.cylinder(f'Engine {i} throat ring', (x+2.6,y,0), r*.72, .3, kit.steel, 'X', 32, .03)
        bpy.ops.mesh.primitive_torus_add(major_radius=r, minor_radius=.3, major_segments=48,
                                         minor_segments=8, location=(x,y,0), rotation=(0,math.pi/2,0))
        kit.finish(bpy.context.object, f'Engine {i} mouth lip', kit.steel)
