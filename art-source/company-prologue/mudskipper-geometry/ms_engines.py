"""Mudskipper drive units: the main drive, two pod drives and two jets.

Every engine is a whole unit bolted to the aft hull, never a nozzle poking
out of the plating. Engine numbers follow the order of mudskipper.ship's
engineSlots, and each nozzle mouth sits exactly on its slot:
  1 main drive        (-36,  0) angle 180, width 24
  2 port pod drive    (-36, 25) angle 180, width 8
  3 starboard pod     (-36,-23) angle 180, width 8
  4 port jet          ( 19, 29) angle 150, width 8  (maneuvering jets)
  5 starboard jet     ( 19,-28) angle 210, width 8
The main drive is the sprite's banded centre drum; the pods are the boxy
machinery either side of it, cut back into the sprite's two stern notches.
Solid bell mouths use 0.31 x slot width (the Second Wind ratio), except the
maneuvering jets, which the sprite shows as 3 px bumps: 0.2 x width.
"""
import math
import bmesh
import bpy
from mathutils import Matrix, Vector
from ms_kit import prism, section, crisp
from ms_hull import AFT_FACE

ENGINES = [(-36,0,180,24),(-36,25,180,8),(-36,-23,180,8),(19,29,150,8),(19,-28,210,8)]
MAIN_R = 8.0          # drum circumradius: the sprite's +-8 centre column
POD_Z = (-7.5, 8.5)


def bell_radius(i, width):
    return width*(.2 if i >= 4 else .31)


def ngon(cy, cz, r, n=12):
    return [(cy+r*math.cos(math.pi/n*(2*k+1)), cz+r*math.sin(math.pi/n*(2*k+1))) for k in range(n)]


def drum(kit, name, rings, mat, y=0, z=0, n=12):
    """Faceted solid of revolution about an X-axis line: rings are (x, r)."""
    v = [(x,py,pz) for x,r in rings for py,pz in ngon(y,z,r,n)]
    f = [tuple(reversed(range(n))), tuple(range(len(v)-n,len(v)))]
    for k in range(len(rings)-1):
        f += [(k*n+i,k*n+(i+1)%n,(k+1)*n+(i+1)%n,(k+1)*n+i) for i in range(n)]
    ob = kit.mesh(name, v, f, mat)
    bpy.context.view_layer.objects.active = ob
    bm = bmesh.new(); bm.from_mesh(ob.data)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces); bm.to_mesh(ob.data); bm.free()
    return crisp(ob, .12)


def nozzle(kit, i, mouth, angle, radius, depth):
    """Open bell (mouth -> narrower throat), dark throat plug and mouth rim,
    built along -X and turned to the slot's exhaust angle."""
    objs = set(bpy.data.objects)
    throat = radius*.66
    seg = 32
    v = []
    for x, r in ((0, radius), (depth, throat)):
        v += [(x, r*math.cos(t*math.tau/seg), r*math.sin(t*math.tau/seg)) for t in range(seg)]
    f = [(t,(t+1)%seg,seg+(t+1)%seg,seg+t) for t in range(seg)]
    bell = kit.mesh(f'Engine {i} bell', v, f, kit.steel)
    sol = bell.modifiers.new('Bell wall', 'SOLIDIFY')
    sol.thickness = .25
    kit.cylinder(f'Engine {i} dark throat', (depth-.05,0,0), throat, .1, kit.dark, 'X', seg, 0)
    bpy.ops.mesh.primitive_torus_add(major_radius=radius, minor_radius=max(.12, radius*.06),
                                     major_segments=seg, minor_segments=8, location=(0,0,0),
                                     rotation=(0,math.pi/2,0))
    kit.finish(bpy.context.object, f'Engine {i} mouth rim', kit.steel)
    # Bell axis points forward (+X) from the mouth; exhaust leaves along the
    # slot angle, so turn +X onto the reverse of the exhaust direction.
    turn = Matrix.Translation(mouth) @ Matrix.Rotation(math.radians(angle+180), 4, 'Z')
    for ob in set(bpy.data.objects)-objs:
        ob.matrix_world = turn @ ob.matrix_world


def build_main(kit):
    x0 = AFT_FACE+.5
    # Main drive drum: casing, three proud barrel bands and a tapered can.
    drum(kit, 'Engine 1 main drive casing', [(x0,MAIN_R-.6),(x0-1.5,MAIN_R),(-33,MAIN_R),(-35.2,MAIN_R-.5)], kit.steel)
    for k, x in enumerate((-19.5,-24.5,-29.5),1):
        drum(kit, f'Engine 1 barrel band {k}', [(x+.7,MAIN_R+.55),(x-.7,MAIN_R+.55)], kit.plate)
    # Dark machinery core the drive units bolt to: what the seams between
    # the drum and the pods open onto.
    # It stops short of the stern notch so the notch stays open.
    prism(kit, 'Drive bay core', [(AFT_FACE+1,-13),(-29,-13),(-29,13),(AFT_FACE+1,13)],
          [(0,-5),(0,6.5)], kit.dark)


def build_pods(kit):
    for s, label, i in ((1,'port',2),(-1,'starboard',3)):
        ex, ey = ENGINES[i-1][:2]
        plan = [(AFT_FACE-.5,12.5),(AFT_FACE-.5,28.5),(-35,28.5),(-35,20.5),(-29.5,12.5)]
        pod = prism(kit, f'Engine {i} {label} pod drive casing', [(x,s*y) for x,y in plan],
                    section(POD_Z[0], 4.5, POD_Z[1], 2, 2), kit.steel)
        crisp(pod)
        # Raised machinery deck on the pod, and the tan nozzle housing (the
        # sprite's tan blocks over each pod nozzle).
        kit.box(f'Engine {i} pod machinery deck', (-21.5,s*20.5,POD_Z[1]+.6), (10,11,1.2), kit.sponson, .2)
        drum(kit, f'Engine {i} nozzle housing', [(-27,3.4),(-34.4,3.4),(-35.4,3.0)], kit.tan, y=ey)


def build_jets(kit):
    for i in (4,5):
        x, y, angle, w = ENGINES[i-1]
        s = 1 if y > 0 else -1
        # A short housing set into the forebody's aft outer corner, its
        # axis along the exhaust direction.
        d = Vector((math.cos(math.radians(angle)), math.sin(math.radians(angle)), 0))
        c = Vector((x,y,2)) - d*2.6
        h = kit.box(f'Engine {i} {"port" if s > 0 else "starboard"} maneuvering jet housing', c, (4.4,3.2,3.6), kit.steel, .15)
        h.rotation_euler[2] = math.radians(angle)


def build_nozzles(kit):
    for i, (x, y, angle, w) in enumerate(ENGINES, 1):
        z = 2 if i >= 4 else 0
        depth = .7 if i == 1 else (.5 if i <= 3 else .3)
        nozzle(kit, i, Vector((x,y,z)), angle, bell_radius(i, w), depth)


def build(kit):
    build_main(kit)
    build_pods(kit)
    build_jets(kit)
    build_nozzles(kit)
