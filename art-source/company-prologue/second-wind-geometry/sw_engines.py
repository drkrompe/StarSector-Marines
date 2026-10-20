"""Aft drive pods: the two engine units bolted either side of the stern notch.

Each aft "arm" of the Cerberus planform is a drive unit bolted to the ship,
not hull with nozzles poking out of it. A pod is engine casing in distinct
sections along its thrust axis, each its own solid with a dark seam between:
a tapered intake cap, an armoured cowl over a steel lower casing (carrying
the turret seat), a reinforcing barrel band, a finned heat-sink section, a
second band, a stepped-down thrust housing, and two faceted engine cans
whose aft faces are the exhausts, the bells sunk into them. A dark core
shows through every seam; feed lines cross the valley from the centre body.

Sections are lofted along X from rounded-rectangle cross-sections, so the
casing reads as machined barrel plate rather than a box. The corner radii
follow the shared segment rule (ship-detail-guide.md, Edge treatment) and
shade smooth; the flats stay flat and every station step, raked face and
band chamfer stays a crisp edge, so the pods read machined, not inflated.
Tapering stations make the transitions: the cap narrows forward, the cowl
face is raked, the housing steps down aft. The engine cans are round
shrouds by the same rule.
"""
import math
import bpy
import bmesh
import sw_kit
from mathutils import Vector
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
# corner radius, bottom corner radius). Every corner of every section takes
# the same number of segments (corner_segments), so stations loft
# vertex-for-vertex, enough for the largest corner under the segment rule.
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
# Engine cans: round shrouds (x, radius) per engine, with a proud
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


# The largest corner radius any pod section carries (the bands' top, 8 units).
CORNER_MAX = 8.4


def corner_segments():
    """Segments per quarter round on every pod section corner: the segment rule
    for the largest corner, so the smaller ones are finer than they need."""
    return sw_kit.sides(CORNER_MAX)//4


def rr(sec, grow=0, top=True, bottom=True):
    """Rounded rectangle in YZ, CCW, its corners by the segment rule.
    top/bottom False = square edge."""
    y0,y1,z0,z1,(ri,ro),rb = sec
    y0,y1,z0,z1,ri,ro,rb = y0-grow,y1+grow,z0-grow,z1+grow,ri+grow,ro+grow,rb+grow
    pts = []
    q = corner_segments()
    for cy,cz,r,a0,n,on in [(y0,z0,rb,180,q,bottom),(y1,z0,rb,270,q,bottom),
                            (y1,z1,ro,0,q,top),(y0,z1,ri,90,q,top)]:
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
    # Corner rounds shade smooth; station steps, raked faces and chamfers stay sharp.
    sw_kit.smooth(ob, 20)
    return bevel(ob, edge) if edge else ob


def ngon(cy, cz, r, n=12):
    # n sides, flats at top, bottom and sides (n a multiple of 4).
    return [(cy+r*math.cos(math.pi/n*(2*k+1)), cz+r*math.sin(math.pi/n*(2*k+1))) for k in range(n)]


def bevel(obj, width):
    # One segment, angle-limited above the ~30-deg station tapers: a chamfer that
    # splits an edge into shallow creases reads as a coarse curve, and the rounds'
    # own segment edges are left alone. Hardened normals keep the flats flat.
    b = obj.modifiers.new('Manufactured edge radii', 'BEVEL')
    b.width, b.segments = width, 1
    b.limit_method, b.angle_limit, b.harden_normals = 'ANGLE', math.radians(40), True
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
        n = sw_kit.sides(max(r for _,r in CAN[w]))
        rings = [(x,ngon(ey,0,r,n)) for x,r in CAN[w]] + [(face,ngon(ey,0,CAN_FACE_R[w],n))]
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
    mouth = rr(S(25,34,-1.6,7.6,2.6,2.2))
    m = len(mouth)
    kit.mesh('Intake mouth', [(x,s*y,z) for x in (3.1,3.35) for y,z in mouth],
             [tuple(range(m)), tuple(range(2*m-1,m-1,-1))] + [(i,(i+1)%m,m+(i+1)%m,m+i) for i in range(m)], kit.dark)
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


def ring(kit, name, x0, x1, y, r_in, r_out, mat, n=48, flat_outer=True):
    # Annulus along X: 12-sided outer collar plate (or a round one) around a round bore.
    if not flat_outer:
        n = sw_kit.sides(r_out, n)
    def pt(r, k, flat):
        a = k*math.tau/n
        if flat:
            r = r/math.cos(((a+math.pi/12) % (math.pi/6))-math.pi/12)
        return (y+r*math.cos(a), r*math.sin(a))
    v = []
    for x in (x0,x1):
        v += [(x,*pt(r_out,k,flat_outer)) for k in range(n)] + [(x,*pt(r_in,k,False)) for k in range(n)]
    f = []
    for k in range(n):
        j = (k+1) % n
        f += [(k,j,2*n+j,2*n+k), (n+j,n+k,3*n+k,3*n+j), (j,k,n+k,n+j), (2*n+k,2*n+j,3*n+j,3*n+k)]
    return kit.mesh(name, v, f, mat, bevel=.06)


# Nozzle proportions, in fractions of the mouth radius r (the bell is the
# same drive scaled for both slot widths). A short contoured bell: a Rao-like
# curve leaving the throat at 33 deg and reaching the mouth at 11 deg, shallow
# enough that the throat still shows from C1's stern quarter.
THROAT_R = .55      # throat radius
BELL_L = .7         # mouth plane to throat
CHAMBER = .1        # throat to injector face (the dark throat)
WALL = .045         # bell wall thickness
BANDS_N = 6         # film-cooling bands lining the bell
STEP = .03          # backward-facing step at each band joint
# Along X behind the mouth plane, in units scaled by r/3.9: heat-shield band,
# gimbal ring, then the fixed shroud collar on the can face (x+0.6).
COLLAR_AFT = .56
RING_T = .34
# Gimbal actuators, at angles in (away-from-partner, up): one on top and one
# low on the far side, both on can flats; two non-collinear rams steer pitch
# and yaw about the gimbal trunnions.
RAM_ANGLES = (90, -60)


def bell_curve(r):
    """Interior radius as a function of depth a behind the mouth."""
    rt, L = THROAT_R*r, BELL_L*r
    m1, m2 = math.tan(math.radians(33)), math.tan(math.radians(11))
    bc = (r-rt-m2*L)/(m1-m2)
    pts = []
    for k in range(65):
        u = k/64
        b = 2*u*(1-u)*bc+u*u*L
        rho = (1-u)**2*rt+2*u*(1-u)*(rt+m1*bc)+u*u*r
        pts.append((L-b, rho))
    pts.sort()
    def rho(a):
        for (a0,r0),(a1,r1) in zip(pts, pts[1:]):
            if a <= a1:
                return r0+(r1-r0)*(a-a0)/max(a1-a0, 1e-9)
        return pts[-1][1]
    return rho


def revolve(kit, name, prof, y, mat, n=36, closed=False, inward=False, bevel=0):
    """Surface of revolution about the engine axis (parallel to X through
    (y, 0)) through an (x, radius) profile; closed profiles make solids.
    Segmented by the shared segment rule at least, and shaded smooth with
    its profile's steps kept sharp."""
    n = sw_kit.sides(max(r_ for _,r_ in prof), n)
    v = []
    for x,r_ in prof:
        v += [(x, y+r_*math.cos(t*math.tau/n), r_*math.sin(t*math.tau/n)) for t in range(n)]
    m = len(prof)
    rows = m if closed else m-1
    f = [(k*n+t, k*n+(t+1)%n, ((k+1)%m)*n+(t+1)%n, ((k+1)%m)*n+t)
         for k in range(rows) for t in range(n)]
    ob = kit.mesh(name, v, f, mat, bevel=bevel)
    bm = bmesh.new(); bm.from_mesh(ob.data)
    if closed:
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    else:
        # Open sheet: face the axis (interior) or away from it.
        out = 0
        for fc in bm.faces:
            c = fc.calc_center_median()
            out += fc.normal.dot(Vector((0, c.y-y, c.z)))*fc.calc_area()
        if (out > 0) == inward:
            bmesh.ops.reverse_faces(bm, faces=bm.faces)
    bm.to_mesh(ob.data); bm.free()
    sw_kit.smooth(ob, 20)
    return ob


def radial_box(kit, name, x, y, u, ang, r0, r1, length, width, mat, bevel=.04):
    """Box standing radially off the engine axis from r0 to r1 at angle ang
    (degrees, in the (away-from-partner u, up) plane)."""
    a = math.radians(ang)
    rc = (r0+r1)/2
    ob = kit.box(name, (x, y+u*rc*math.cos(a), rc*math.sin(a)), (length, width, r1-r0), mat, bevel)
    ob.rotation_euler[0] = math.atan2(-u*math.cos(a), math.sin(a))
    return ob


def build_nozzles(kit):
    liner = sw_kit.material('Drive bell liner', (.085,.09,.095), .75, .42)
    rod = sw_kit.material('Actuator rod chrome', (.5,.52,.53), .9, .22)
    for i,(x,y,w) in enumerate(ENGINES,1):
        r = BELL_R[w]
        k = r/3.9
        rho = bell_curve(r)
        L, rt, t = BELL_L*r, THROAT_R*r, WALL*r
        xi = x+L+CHAMBER*r  # injector face
        u = (1 if y > 0 else -1)*(1 if w == 12 else -1)  # away from the pair partner
        # Bell interior: film-cooling bands, each joint a backward-facing step
        # (the downstream band starts proud of the upstream one), faceted.
        joints = [L*j/BANDS_N for j in range(BANDS_N+1)]
        prof = []
        for j,(a0,a1) in enumerate(zip(joints, joints[1:])):
            for q in range(4):
                a = a0+(a1-a0)*q/3
                inset = 0 if j == 0 else STEP*r*(a1-a)/(a1-a0)
                prof.append((x+a, rho(a)-inset))
        # Throat, then the short chamber widening to the injector face.
        prof += [(x+L+CHAMBER*r*.5, rt*1.04), (xi, rt*1.12)]
        revolve(kit, f'Engine {i} aft-facing bell', prof, y, liner, inward=True)
        # Injector face at the bottom: the cinematic swaps this for glow.
        kit.cylinder(f'Engine {i} dark throat', (xi,y,0), rt*1.14, .06*k, kit.dark, 'X', 36, 0)
        kit.cylinder(f'Engine {i} injector pintle', (xi-.08*k,y,0), .07*r, .16*k, kit.steel, 'X', 12, .02)
        revolve(kit, f'Engine {i} injector ring', [(xi-.05*k,rt*.55),(xi-.05*k,rt*.6),(xi-.01,rt*.6),(xi-.01,rt*.55)],
                y, kit.steel, 24, True)
        # Throat ring: the hard insert at the narrowest section, its bore open.
        revolve(kit, f'Engine {i} throat ring', [(x+L-.06*r,rho(L-.06*r)-.01),(x+L-.02*r,rt*.95),(x+L+.05*r,rt*.95),
                (x+L+.05*r,rt*1.06),(x+L-.06*r,rt*1.12)], y, kit.steel, 36, True)
        # Bell wall: outer skin one wall thickness off the liner.
        skin = [(x+a, rho(a)+t) for a in [L*q/12 for q in range(13)]]
        revolve(kit, f'Engine {i} bell outer skin', skin, y, kit.steel, 36)
        bpy.ops.mesh.primitive_torus_add(major_radius=r+t/2, minor_radius=t/2+.06*k, major_segments=48,
                                         minor_segments=8, location=(x,y,0), rotation=(0,math.pi/2,0))
        kit.finish(bpy.context.object, f'Engine {i} mouth lip', kit.steel)
        # Exposed exterior between lip and gimbal ring: a heat-shield band.
        ring_aft = x+(COLLAR_AFT-RING_T)*k
        e0, e1 = rho(.1*k)+t, rho(ring_aft-x)+t
        hb = [(x+.1*k, e0), (x+.14*k, e0+.08*k), (ring_aft-.04*k, e1+.08*k), (ring_aft-.02*k, e1)]
        revolve(kit, f'Engine {i} heat-shield band', hb, y, kit.dark, 36, True)
        # Gimbal ring hugging the bell behind the mouth, pinned to the collar
        # at two trunnions; lever ears carry the actuator rod ends.
        ring_out = r+.85*k
        ring(kit, f'Engine {i} gimbal ring', x+COLLAR_AFT*k, ring_aft, y, rho(.4*k)+t+.03, ring_out, kit.steel)
        ring(kit, f'Engine {i} shroud collar', bell_face(x)+.2, x+COLLAR_AFT*k, y, r+.45,
             CAN_FACE_R[w]*math.cos(math.pi/12)-.15, kit.dark, flat_outer=False)
        rmid = x+(COLLAR_AFT-RING_T/2)*k
        for ang in (0,180):
            radial_box(kit, f'Engine {i} gimbal trunnion', rmid, y, u, ang, ring_out-.15*k, ring_out+.42*k,
                       RING_T*k+.08, .7*k, kit.steel, .05)
            a = math.radians(ang)
            kit.cylinder(f'Engine {i} trunnion pin cap', (ring_aft-.05*k, y+u*(ring_out+.13*k)*math.cos(a), 0),
                         .2*k, .1*k, rod, 'X', 12, .02)
        # Actuator rams lying along the can flats, from clevis brackets bolted
        # against the can band forward to the gimbal ring's lever ears.
        rr_ = .1*r
        band_aft = CAN[w][3][0]
        apo = CAN[w][4][1]*math.cos(math.pi/12)
        R = apo+.18+rr_
        xb = band_aft-.35
        for ang in RAM_ANGLES:
            a = math.radians(ang)
            py, pz = y+u*R*math.cos(a), R*math.sin(a)
            # The ear runs forward to the can face so no light shows under it.
            radial_box(kit, f'Engine {i} gimbal lever ear', (ring_aft+bell_face(x)+.04)/2, y, u, ang, ring_out-.2*k, R+rr_*.7,
                       bell_face(x)+.04-ring_aft, 2.2*rr_, kit.steel, .04)
            radial_box(kit, f'Engine {i} actuator clevis bracket', xb, y, u, ang, apo-.1, R+rr_*.8,
                       .7, 2.6*rr_, kit.steel, .05)
            x_body = rmid+(xb-rmid)*.42
            kit.cylinder(f'Engine {i} gimbal actuator', ((x_body+xb-.3)/2, py, pz), rr_, xb-.3-x_body, kit.dark, 'X', 16, .04)
            kit.cylinder(f'Engine {i} actuator gland', (x_body-.1*k, py, pz), rr_*1.2, .3*k, kit.steel, 'X', 16, .03)
            kit.cylinder(f'Engine {i} actuator rod', ((rmid+x_body)/2, py, pz), rr_*.45, x_body-rmid, rod, 'X', 12, .02)
            # Hydraulic line along the cylinder's flank, into the bracket.
            hu = R*math.cos(a)-(rr_+.07*k)*math.sin(a)
            hz = R*math.sin(a)+(rr_+.07*k)*math.cos(a)
            kit.cylinder(f'Engine {i} actuator hydraulic line', ((x_body+xb)/2, y+u*hu, hz),
                         .07*k, xb-x_body, kit.steel, 'X', 8, 0)
        # Exhaust anchor: mouth-plane centre, local +X down the exhaust (-X).
        an = bpy.data.objects.new(f'Engine {i} exhaust anchor', None)
        an.empty_display_type = 'ARROWS'
        an.empty_display_size = r
        an.location = (x, y, 0)
        an.rotation_euler = (0, 0, math.pi)
        bpy.context.collection.objects.link(an)
        an['engine'] = i
        an['radius'] = r
        an['radius_m'] = r*.65
        an['throat_x'] = xi
        an['throat_radius'] = rt*1.14
