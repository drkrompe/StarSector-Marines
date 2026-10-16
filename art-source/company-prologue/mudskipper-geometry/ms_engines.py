"""Mudskipper drive units: the main drive, two pod drives and two jets.

Every engine is a whole unit bolted to the aft hull, never a nozzle poking
out of the plating. Engine numbers follow the order of mudskipper.ship's
engineSlots, and each nozzle mouth sits exactly on its slot:
  1 main drive        (-36,  0) angle 180, width 24
  2 port pod drive    (-36, 25) angle 180, width 8
  3 starboard pod     (-36,-23) angle 180, width 8
  4 port jet          ( 19, 29) angle 150, width 8  (maneuvering jets)
  5 starboard jet     ( 19,-28) angle 210, width 8
Solid bell mouths use 0.31 x slot width (the Second Wind ratio), except the
maneuvering jets, which the sprite shows as 3 px bumps: 0.2 x width.

Main drive (the sprite's banded centre drum, y +-8): a 12-sided barrel
casing on a thrust-frame flange at the aft hull face, three proud burnished
barrel bands (the sprite's pink highlights), a dark gimbal waist (the
sprite's dark ring at x -32..-33) and a thrust can with the bell sunk into
it. Gimbal actuator housings stand either side of the waist (the sprite's
dark columns at y +-8..10.5, x -29..-34) and short rams reach the can.
Coolant lines run aft along the barrel's shoulders, clamped by the bands,
and drop into the waist.

Pod drives (y 12.5..28.5, with the inboard diagonal into the stern notch):
faceted lofts in sections along the thrust axis with a dark core showing
through every seam. A tall forward casing carries the sprite's dark
machinery top: a pump/valve housing (red on the port pod, pale on the
starboard one, as drawn), a tan access hatch and an outboard radiator fin
bay. A tan thrust collar rakes down aft (the sprite's tan band at x
-26..-29) onto a low thrust housing that cradles the tan nozzle can.
Feed trunks and cross pipes run through the valley to the core.

Maneuvering jets: faceted RCS blocks buried in the forebody's aft outer
corners, with a mounting collar at the sponson, the main jet on the slot
and dorsal/ventral puff nozzles.
"""
import math
import bmesh
import bpy
from mathutils import Matrix, Vector
from ms_kit import prism, crisp, material
from ms_hull import AFT_FACE

ENGINES = [(-36,0,180,24),(-36,25,180,8),(-36,-23,180,8),(19,29,150,8),(19,-28,210,8)]
MAIN_R = 8.0          # drum circumradius: the sprite's +-8 centre column
POD_Z = (-7.5, 8.5)
POD_Y = (12.5, 28.5)
NOTCH = (-29.5, -35.0, 20.5)   # inboard diagonal: (x start, x end, y at end)


def bell_radius(i, width):
    return width*(.2 if i >= 4 else .31)


def materials(kit):
    if not hasattr(kit, 'eng_band'):
        kit.eng_band = material('Drive band burnished plate', (.55,.45,.43), .45, .4)
        kit.eng_red = material('Drive pump housing red', (.44,.09,.04), .3)
        kit.eng_bell = material('Drive bell heat-darkened steel', (.075,.07,.068), .8, .35)
        kit.eng_deck = material('Drive pod deck plate', (.27,.28,.28), .5)
        kit.eng_warn = material('Drive warning light', (.6,.05,.03), emission=(1,.08,.04))


def ngon(cy, cz, r, n=12):
    return [(cy+r*math.cos(math.pi/n*(2*k+1)), cz+r*math.sin(math.pi/n*(2*k+1))) for k in range(n)]


def _closed(kit, name, v, n, mat):
    f = [tuple(reversed(range(n))), tuple(range(len(v)-n,len(v)))]
    for k in range(len(v)//n-1):
        f += [(k*n+i,k*n+(i+1)%n,(k+1)*n+(i+1)%n,(k+1)*n+i) for i in range(n)]
    ob = kit.mesh(name, v, f, mat)
    bm = bmesh.new(); bm.from_mesh(ob.data)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces); bm.to_mesh(ob.data); bm.free()
    return ob


def drum(kit, name, rings, mat, y=0, z=0, n=12, edge=.12):
    """Faceted solid of revolution about an X-axis line: rings are (x, r)."""
    ob = _closed(kit, name, [(x,py,pz) for x,r in rings for py,pz in ngon(y,z,r,n)], n, mat)
    return crisp(ob, edge) if edge else ob


def S(y0, y1, z0, z1, rt, rb):
    """Cross-section: (y0, y1, z0, z1, (inboard, outboard) top radius, bottom radius)."""
    return (y0, y1, z0, z1, rt if isinstance(rt, tuple) else (rt, rt), rb)


def rr(sec, grow=0):
    """Faceted rounded rectangle in YZ (port coords): two facets per bottom
    corner, three per top corner, so the casing reads as machined barrel
    plate rather than a box. (Idiom copied from the Second Wind drives.)"""
    y0,y1,z0,z1,(ri,ro),rb = sec
    y0,y1,z0,z1,ri,ro,rb = y0-grow,y1+grow,z0-grow,z1+grow,ri+grow,ro+grow,rb+grow
    pts = []
    for cy,cz,r,a0,n in [(y0,z0,rb,180,2),(y1,z0,rb,270,2),(y1,z1,ro,0,3),(y0,z1,ri,90,3)]:
        oy = cy+(r if cy == y0 else -r)
        oz = cz+(r if cz == z0 else -r)
        pts += [(oy+r*math.cos(math.radians(a0+90*k/n)), oz+r*math.sin(math.radians(a0+90*k/n))) for k in range(n+1)]
    return pts


def loft(kit, name, rings, mat, side, edge=.12):
    """Closed solid through YZ rings at successive X stations, mirrored by side."""
    n = len(rings[0][1])
    ob = _closed(kit, name, [(x,side*y,z) for x,pts in rings for y,z in pts], n, mat)
    return crisp(ob, edge) if edge else ob


def ring(kit, name, x0, x1, y, r_in, r_out, mat, n=48, edge=.05):
    """Annulus along X: a 12-sided collar plate around a round bore."""
    def pt(r, k, flat):
        a = k*math.tau/n
        if flat:
            r = r*math.cos(math.pi/12)/math.cos(((a+math.pi/12) % (math.pi/6))-math.pi/12)
        return (y+r*math.cos(a), r*math.sin(a))
    v = []
    for x in (x0,x1):
        v += [(x,*pt(r_out,k,True)) for k in range(n)] + [(x,*pt(r_in,k,False)) for k in range(n)]
    f = []
    for k in range(n):
        j = (k+1) % n
        f += [(k,j,2*n+j,2*n+k), (n+j,n+k,3*n+k,3*n+j), (j,k,n+k,n+j), (2*n+k,2*n+j,3*n+j,3*n+k)]
    ob = kit.mesh(name, v, f, mat)
    bm = bmesh.new(); bm.from_mesh(ob.data)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces); bm.to_mesh(ob.data); bm.free()
    return crisp(ob, edge) if edge else ob


def cut(obj, cutter):
    """Boolean recess, applied ahead of the chamfer modifier."""
    bpy.context.view_layer.objects.active = obj
    mod = obj.modifiers.new('Recess', 'BOOLEAN')
    mod.operation, mod.object, mod.solver = 'DIFFERENCE', cutter, 'MANIFOLD'
    bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)
    bpy.ops.object.modifier_apply(modifier=mod.name)
    bpy.data.objects.remove(cutter, do_unlink=True)


def pipe(kit, name, a, b, r, mat, n=12):
    a, b = Vector(a), Vector(b)
    d = b-a
    ob = kit.cylinder(name, (a+b)/2, r, d.length, mat, 'Z', n, 0)
    ob.rotation_euler = d.to_track_quat('Z','Y').to_euler()
    return ob


def notch_y(x):
    """Inboard edge of a pod (port coords) at station x."""
    x0, x1, y1 = NOTCH
    return POD_Y[0] if x >= x0 else POD_Y[0]+(x0-x)*(y1-POD_Y[0])/(x0-x1)


def nozzle(kit, i, mouth, angle, radius, depth):
    """Open bell (mouth -> narrower throat), dark throat plug, throat ring
    and mouth rim, built along -X and turned to the slot's exhaust angle."""
    objs = set(bpy.data.objects)
    throat = radius*.66
    seg = 32
    v = []
    for x, r in ((0, radius), (depth, throat)):
        v += [(x, r*math.cos(t*math.tau/seg), r*math.sin(t*math.tau/seg)) for t in range(seg)]
    f = [(t,(t+1)%seg,seg+(t+1)%seg,seg+t) for t in range(seg)]
    bell = kit.mesh(f'Engine {i} bell', v, f, kit.eng_bell)
    sol = bell.modifiers.new('Bell wall', 'SOLIDIFY')
    sol.thickness = min(.25, radius*.08)
    kit.cylinder(f'Engine {i} dark throat', (depth-.05,0,0), throat, .1, kit.dark, 'X', seg, 0)
    if depth > 1:
        # Stiffening ring inside the bell, two thirds of the way to the throat.
        rx = depth*.66
        bpy.ops.mesh.primitive_torus_add(major_radius=radius+(throat-radius)*.66, minor_radius=max(.1, radius*.03),
                                         major_segments=seg, minor_segments=6, location=(rx,0,0),
                                         rotation=(0,math.pi/2,0))
        kit.finish(bpy.context.object, f'Engine {i} throat ring', kit.steel)
    bpy.ops.mesh.primitive_torus_add(major_radius=radius, minor_radius=max(.12, radius*.06),
                                     major_segments=seg, minor_segments=8, location=(0,0,0),
                                     rotation=(0,math.pi/2,0))
    kit.finish(bpy.context.object, f'Engine {i} mouth rim', kit.steel)
    # Bell axis points forward (+X) from the mouth; exhaust leaves along the
    # slot angle, so turn +X onto the reverse of the exhaust direction.
    turn = Matrix.Translation(mouth) @ Matrix.Rotation(math.radians(angle+180), 4, 'Z')
    for ob in set(bpy.data.objects)-objs:
        ob.matrix_world = turn @ ob.matrix_world


# ---------------------------------------------------------------- main drive

BANDS = (-19.5,-24.5,-29.5)
WAIST = (-30.6,-33.2,7.0)
CAN_X = (-33.0,-35.6)


def build_main(kit):
    x0 = AFT_FACE+.5
    # Thrust-frame flange at the aft hull face, bolted round its rim.
    drum(kit, 'Engine 1 thrust frame flange', [(x0-.1,8.6),(-16.6,8.6),(-17.0,8.1)], kit.steel)
    for k in range(12):
        a = math.tau*k/12
        kit.cylinder('Engine 1 flange bolt', (-16.75,8.25*math.cos(a),8.25*math.sin(a)), .28, .3, kit.steel, 'X', 6, 0)
    # Barrel casing and three proud burnished barrel bands with chamfered edges.
    drum(kit, 'Engine 1 main drive casing', [(x0,7.5),(-17,MAIN_R),(-30.4,MAIN_R),(-30.8,7.3)], kit.steel)
    for k, x in enumerate(BANDS,1):
        drum(kit, f'Engine 1 barrel band {k}', [(x+.7,MAIN_R+.25),(x+.45,MAIN_R+.55),(x-.45,MAIN_R+.55),(x-.7,MAIN_R+.25)], kit.eng_band, edge=.06)
    # Dark gimbal waist between the barrel and the thrust can, a steel
    # gimbal ring round it.
    xa, xb, rw = WAIST
    drum(kit, 'Engine 1 gimbal waist', [(xa,rw),(xb,rw)], kit.dark, edge=0)
    drum(kit, 'Engine 1 gimbal ring', [(-31.5,rw+.45),(-32.3,rw+.45)], kit.steel, edge=.05)
    # Thrust can: the bell is sunk into it, its burnished lip band proud.
    can = drum(kit, 'Engine 1 thrust can', [(CAN_X[0],7.4),(CAN_X[0]-.4,MAIN_R),(CAN_X[1]+.3,MAIN_R),(CAN_X[1],7.75)], kit.steel, edge=0)
    cut(can, kit.cylinder('Temporary bell well', (CAN_X[1],0,0), 7.6, 6.4, kit.dark, 'X', 48, 0))
    crisp(can, .1)
    ring(kit, 'Engine 1 can band', -33.8, -34.9, 0, 7.7, MAIN_R+.5, kit.eng_band)
    # Inspection covers on the barrel's top and side flats between the bands.
    ap = MAIN_R*math.cos(math.pi/12)
    for xc in (-22,-27):
        for a in (90,0,180):
            r = ap+.06
            c = (xc, r*math.cos(math.radians(a)), r*math.sin(math.radians(a)))
            frame = kit.box('Engine 1 casing cover frame', c, (3.0,3.3,.16), kit.dark, 0)
            cover = kit.box('Engine 1 casing inspection cover', c, (2.6,2.9,.3), kit.steel, .06)
            for ob in (frame, cover):
                ob.rotation_euler = (math.radians(a-90),0,0)
    # Coolant lines along the barrel shoulders (60 and 120 degree flats),
    # passing under the bands that clamp them, then dropping into the waist.
    for a in (60,120):
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        r = 8.15
        pipe(kit, 'Engine 1 coolant line', (-15.4,r*ca,r*sa), (-31.0,r*ca,r*sa), .32, kit.steel)
        pipe(kit, 'Engine 1 coolant line', (-31.0,r*ca,r*sa), (-31.0,6.6*ca,6.6*sa), .32, kit.steel)
        kit.box('Engine 1 coolant elbow', (-31.0,r*ca,r*sa), (.8,.8,.8), kit.steel, .1).rotation_euler = (math.radians(a-90),0,0)
        for x in BANDS[:2]:
            kit.box('Engine 1 coolant line clamp', (x,(r+.1)*ca,(r+.1)*sa), (.9,1.0,.5), kit.steel, .06).rotation_euler = (math.radians(a-90),0,0)
    # Gimbal actuators: housings either side of the waist, cantilevered off
    # the core's aft face, and a dorsal one over the waist; rams to the can.
    for s in (1,-1):
        kit.box('Engine 1 gimbal actuator housing', (-30.7,s*9.0,0), (3.0,3.0,4.6), kit.steel, .2)
        kit.box('Engine 1 gimbal actuator cap', (-30.7,s*9.0,2.45), (2.2,2.2,.3), kit.dark, .05)
        pipe(kit, 'Engine 1 gimbal actuator ram', (-32.1,s*8.9,0), (-34.1,s*8.9,0), .4, kit.eng_band)
        kit.box('Engine 1 gimbal actuator lug', (-34.3,s*8.4,0), (1.0,1.4,1.6), kit.steel, .1)
    kit.box('Engine 1 dorsal actuator housing', (-29.4,0,8.55), (1.4,2.2,1.2), kit.steel, .12)
    pipe(kit, 'Engine 1 gimbal actuator ram', (-30.1,0,8.4), (-33.5,0,8.4), .35, kit.eng_band)
    kit.box('Engine 1 gimbal actuator lug', (-33.6,0,8.25), (.9,1.4,.9), kit.steel, .08)
    # Dark machinery core the drive units bolt to: what the seams between
    # the drum and the pods open onto. It stops short of the stern notch so
    # the notch stays open.
    prism(kit, 'Drive bay core', [(AFT_FACE+1,-13),(-29,-13),(-29,13),(AFT_FACE+1,13)],
          [(0,-5),(0,6.5)], kit.dark)
    # Valley plumbing on the core: feed manifolds at the hull face (tan on
    # the port side, pale on the starboard, as the sprite draws them), and
    # two feed trunks along the valley floor into each pod's valve block.
    for s, mat in ((1,kit.tan),(-1,kit.plate)):
        kit.box('Engine 1 feed manifold', (-17.3,s*10.3,7.4), (2.8,3.6,2.0), mat, .15)
        for y in (9.2,10.8):
            pipe(kit, 'Engine 1 feed trunk', (-17.0,s*y,7.0), (-28.4,s*y,7.0), .38, kit.steel)
        kit.box('Engine 1 feed trunk bracket', (-26.5,s*10.0,6.7), (.5,2.6,.6), kit.steel, .05)


# ---------------------------------------------------------------- pod drives

FWD = S(12.5,28.5,POD_Z[0],POD_Z[1],(2.2,2.0),2.0)
COLLAR = S(12.3,28.7,-7.7,8.2,(2.3,2.1),2.1)
COLLAR_AFT = S(12.3,28.7,-7.7,4.4,(1.6,1.6),2.1)
AFT_TOP = 2.0


def aft_sec(x):
    return S(notch_y(x),POD_Y[1],-6.8,AFT_TOP,(1.0,1.2),1.6)


def build_pods(kit):
    for s, label, i in ((1,'port',2),(-1,'starboard',3)):
        ey = abs(ENGINES[i-1][1])
        # Forward casing: the tall machinery section, chamfered fore and aft.
        loft(kit, f'Engine {i} {label} pod drive casing',
             [(-16.5,rr(FWD,-.4)),(-16.9,rr(FWD)),(-25.3,rr(FWD)),(-25.7,rr(FWD,-.4))], kit.steel, s)
        # Dark core inside every section: shows through each seam.
        core = [(-15.8,S(13.1,27.9,-6.9,7.9,1.6,1.4)),(-26.3,S(13.1,27.9,-6.9,7.9,1.6,1.4)),
                (-27.3,S(13.1,27.9,-6.9,3.4,1.0,1.4)),(-28.9,S(13.1,27.9,-6.9,3.4,1.0,1.4)),
                (-29.4,S(13.1,27.9,-6.2,AFT_TOP-.6,.6,1.0)),(-34.6,S(notch_y(-34.6)+.6,27.9,-6.2,AFT_TOP-.6,.6,1.0))]
        loft(kit, f'Engine {i} pod dark core', [(x,rr(sec)) for x,sec in core], kit.dark, s, 0)
        # Tan thrust collar: a proud band raking down aft onto the housing.
        loft(kit, f'Engine {i} pod thrust collar',
             [(-25.85,rr(COLLAR,-.3)),(-26.15,rr(COLLAR)),(-27.4,rr(COLLAR)),(-29.0,rr(COLLAR_AFT))], kit.tan, s)
        # Low thrust housing following the notch diagonal, cradling the can.
        loft(kit, f'Engine {i} pod thrust housing',
             [(-28.8,rr(aft_sec(-28.8))),(-29.5,rr(aft_sec(-29.5))),(-34.6,rr(aft_sec(-34.6))),
              (-35.0,rr(aft_sec(-35.0),-.35))], kit.steel, s)
        # Tan nozzle can, banded, with the bell sunk into its face.
        can = drum(kit, f'Engine {i} nozzle housing',
                   [(-28.6,3.2),(-29.0,3.6),(-35.25,3.6),(-35.6,3.3)], kit.tan, y=s*ey, edge=0)
        cut(can, kit.cylinder('Temporary bell well', (-35.6,s*ey,0), bell_radius(i,8)+.2, 4.2, kit.dark, 'X', 32, 0))
        crisp(can, .1)
        ring(kit, f'Engine {i} nozzle can band', -32.7, -33.9, s*ey, 3.3, 3.85, kit.steel)
        # Tan coaming along the notch edge of the housing top.
        coaming = [(-29.2,notch_y(-29.2)+.8),(-34.7,notch_y(-34.7)+.8),(-34.7,notch_y(-34.7)+2.0),(-29.2,notch_y(-29.2)+2.0)]
        crisp(prism(kit, f'Engine {i} pod notch coaming', [(x,s*y) for x,y in coaming], [(0,AFT_TOP-.2),(0,AFT_TOP+.35)], kit.tan), .05)
        build_pod_top(kit, s, i)
        build_pod_flank(kit, s, i)


def build_pod_top(kit, s, i):
    top = POD_Z[1]
    # Machinery deck and a narrow strake beside it: the sprite's seams at
    # y 19 and 21.5 run between them.
    kit.box(f'Engine {i} pod machinery deck', (-21.1,s*16.0,top+.15), (7.8,6.0,.4), kit.eng_deck, .1)
    kit.box(f'Engine {i} pod deck strake', (-21.1,s*20.3,top+.15), (7.8,1.9,.4), kit.eng_deck, .1)
    # Pump/valve housing with its drive motor drum and a handwheel (port red,
    # starboard pale, as the sprite draws them).
    mat = kit.eng_red if s > 0 else kit.plate
    kit.box(f'Engine {i} pump valve housing', (-21.3,s*17.0,top+1.25), (2.6,2.8,1.9), mat, .15)
    kit.box(f'Engine {i} pump vent slot', (-21.3,s*17.0,top+2.22), (1.8,.5,.06), kit.dark, 0)
    kit.cylinder(f'Engine {i} pump drive motor', (-23.4,s*17.0,top+1.15), .95, 1.8, kit.tan, 'X', 12, .06)
    kit.cylinder(f'Engine {i} pump valve spindle', (-20.6,s*15.3,top+1.4), .12, 1.4, kit.steel, 'Z', 8, 0)
    bpy.ops.mesh.primitive_torus_add(major_radius=.55, minor_radius=.1, major_segments=12, minor_segments=6,
                                     location=(-20.6,s*15.3,top+2.1))
    kit.finish(bpy.context.object, f'Engine {i} pump valve handwheel', kit.eng_red if s > 0 else kit.steel)
    pipe(kit, f'Engine {i} pump discharge pipe', (-21.3,s*15.5,top+.9), (-21.3,s*13.6,top+.9), .3, kit.steel)
    pipe(kit, f'Engine {i} pump discharge pipe', (-21.3,s*13.6,top+.9), (-21.3,s*12.0,top-1.0), .3, kit.steel)
    # Valley feed valve block where the core's trunks reach the pod wall
    # (port red, starboard pale).
    kit.box(f'Engine {i} feed valve block', (-22.0,s*11.4,7.25), (4.6,2.2,1.6), mat, .12)
    # Tan bolted access hatch on the forward outboard top.
    kit.box(f'Engine {i} pod access hatch frame', (-18.8,s*23.0,top+.06), (3.4,3.2,.2), kit.dark, 0)
    kit.box(f'Engine {i} pod access hatch', (-18.8,s*23.0,top+.18), (3.0,2.8,.3), kit.tan, .08)
    kit.box(f'Engine {i} hatch grab bar', (-17.9,s*23.0,top+.45), (.25,1.6,.2), kit.steel, .03)
    # Radiator bay: a recess open to the outboard flank, transverse fins in it.
    cas = bpy.data.objects[f'Engine {i} {"port" if s > 0 else "starboard"} pod drive casing']
    cut(cas, kit.box('Temporary radiator bay', (-22.4,s*27.2,top+.9), (5.6,5.6,2.6), kit.dark, 0))
    kit.box(f'Engine {i} radiator bay floor', (-22.4,s*26.6,top-1.0), (5.5,3.6,.2), kit.dark, 0)
    for k in range(7):
        kit.box(f'Engine {i} radiator fin', (-19.95-k*.8,s*26.45,top-.3), (.2,3.7,1.3), kit.steel, .03)
    kit.box(f'Engine {i} radiator tie bar', (-22.4,s*27.9,top+.32), (5.4,.3,.3), kit.steel, .04)


def build_pod_flank(kit, s, i):
    y = POD_Y[1]
    # Outboard flank: two bolted access panels, stiffener ribs, drain slots.
    for xc in (-18.2,-22.6):
        kit.box(f'Engine {i} pod flank panel frame', (xc,s*(y+.05),.3), (3.6,.3,6.0), kit.dark, 0)
        kit.box(f'Engine {i} pod flank panel', (xc,s*(y+.15),.3), (3.0,.3,5.4), kit.eng_deck, .08)
    for x in (-20.4,-24.8):
        kit.box(f'Engine {i} pod flank rib', (x,s*(y+.12),.3), (.6,.4,9.0), kit.steel, .08)
    for z in (-3.6,-2.4):
        kit.box(f'Engine {i} pod drain slot', (-24.8,s*(y+.05),z), (.25,.2,.6), kit.dark, 0)
    # Thrust housing flank strap and a hot-section warning light.
    kit.box(f'Engine {i} pod housing strap', (-32.0,s*(y+.05),-2.4), (5.0,.25,.6), kit.dark, 0)
    kit.box(f'Engine {i} drive warning light', (-34.6,s*27.4,AFT_TOP+.2), (.5,.5,.4), kit.eng_warn, .05)
    # Cross pipes over the valley from the pod's inboard wall to the drum.
    for x in (-27.0,):
        pipe(kit, f'Engine {i} coolant cross pipe', (x,s*12.6,5.0), (x,s*6.4,5.0), .32, kit.steel)
        kit.cylinder(f'Engine {i} cross pipe flange', (x,s*12.6,5.0), .55, .3, kit.steel, 'Y', 12, .03)


# ---------------------------------------------------------- maneuvering jets

def build_jets(kit):
    for i in (4,5):
        x, y, angle, w = ENGINES[i-1]
        side = 'port' if y > 0 else 'starboard'
        objs = set(bpy.data.objects)
        # Built along +X from the mouth (which sits on the slot), then turned
        # so +X runs against the exhaust, into the forebody corner.
        house = drum(kit, f'Engine {i} {side} maneuvering jet housing',
                     [(.2,1.75),(.55,2.0),(3.9,2.0),(4.8,1.6)], kit.steel, n=8, edge=0)
        cut(house, kit.cylinder('Temporary jet well', (0,0,0), bell_radius(i,w)+.12, 1.4, kit.dark, 'X', 24, 0))
        crisp(house, .08)
        drum(kit, f'Engine {i} jet mounting collar', [(3.0,2.0),(3.2,2.3),(3.7,2.3),(3.9,2.0)], kit.sponson, n=8, edge=.05)
        drum(kit, f'Engine {i} jet face band', [(.6,2.0),(.8,2.18),(1.3,2.18),(1.5,2.0)], kit.tan, n=8, edge=.04)
        ap = 2.0*math.cos(math.pi/8)
        for zs, tag in ((1,'dorsal'),(-1,'ventral')):
            kit.cylinder(f'Engine {i} {tag} puff nozzle', (2.2,0,zs*(ap+.25)), .5, .55, kit.steel, 'Z', 12, .03)
            kit.cylinder(f'Engine {i} {tag} puff throat', (2.2,0,zs*(ap+.53)), .32, .04, kit.dark, 'Z', 12, 0)
        kit.box(f'Engine {i} jet status lamp', (1.0,0,ap+.12), (.35,.35,.25), kit.lamp, .03)
        turn = Matrix.Translation((x,y,2)) @ Matrix.Rotation(math.radians(angle+180), 4, 'Z')
        for ob in set(bpy.data.objects)-objs:
            ob.matrix_world = turn @ ob.matrix_world


def build_nozzles(kit):
    for i, (x, y, angle, w) in enumerate(ENGINES, 1):
        z = 2 if i >= 4 else 0
        depth = 2.75 if i == 1 else (2.0 if i <= 3 else .6)
        nozzle(kit, i, Vector((x,y,z)), angle, bell_radius(i, w), depth)


def build(kit):
    materials(kit)
    build_main(kit)
    build_pods(kit)
    build_jets(kit)
    build_nozzles(kit)
