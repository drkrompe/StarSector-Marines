"""Forebody pressure hull, bow facets, keel, flank bays and forebody armor.

Owns the forward planform, its faceted bow and the stepped deck heights; the
aft engine pods are sw_engines and the dorsal spine is sw_bridge. Locked features
(engines, port pod, the four mount XY positions and both bay openings) are
placed elsewhere and must stay supported by this shell.
"""
import math
import bpy
from mathutils import Vector

# The hull is a union of chamfered prismatic blocks, not one lofted skin: each
# block keeps vertical walls, flat decks and fixed-width chines, so the outline
# reads as plate construction. Cerberus planform: broad forebody, narrow waist,
# two wider aft engine rooms and a central stern notch. Plans are CCW; blocks
# flagged True are port-side plans mirrored onto starboard.
KEEL, WAIST_DECK, MAIN_DECK = -10, 10.5, 14.5

# Section profiles: (inset from plan outline, z), keel to deck.
# A knuckle at the datum breaks the flank into two flats instead of one slab.
def side_profile(deck, wall_top, proud=0):
    return [(d-proud,z) for d,z in
            [(4,KEEL),(1.5,-8.5),(0,-5.5),(0,1),(.8,wall_top),(2.2,deck-1.6),(3.8,deck)]]

LOWER = side_profile(WAIST_DECK, 6.5)
def citadel(base):
    return [(0,base),(0,12.6),(1,13.8),(2.2,MAIN_DECK)]

BLOCKS = [
    # Forebody lower hull: carries the waist deck aft and the bow shoulders.
    ('Forebody lower hull', False, LOWER,
     [(0,-22.5),(30,-28.5),(46,-26),(55,-17),(58,-8),(58,8),(55,17),(46,26),(30,28.5),(0,22.5)]),
    # Raised forward citadel: the main deck, stepped in from the flank ledge.
    ('Forward citadel', False, citadel(8.5),
     [(16,-19.5),(28,-23.5),(44,-22.5),(50,-17),(50,17),(44,22.5),(28,23.5),(16,19.5)]),
    # Forecastle ridge carries the main deck out to an armoured nose that
    # overhangs a recessed bow face above a projecting chin; the bow quarters
    # stay a deck lower.
    ('Forecastle and armoured nose', False, [(0,5.5),(0,12.4),(1.2,13.6),(2.4,MAIN_DECK)],
     [(44,-9),(60,-9),(63.5,-5.5),(63.5,5.5),(60,9),(44,9)]),
    ('Bow chin', False, [(2.2,-12.4),(0,-11),(0,-8),(1,-6.5)],
     [(42,-11),(57,-11),(61,-7),(61,7),(57,11),(42,11)]),
    ('Ventral keel', False, [(1.6,-13),(0,-11.8),(0,-9)],
     [(-25,-6.5),(44,-6.5),(44,6.5),(-25,6.5)]),
]
# Deck plateaus aft to fore, for the manifest: (x0, x1, deck z).
THICKNESS_STATIONS = [(-36,-32,8),(-32,-1,MAIN_DECK),
                      (0,18,WAIST_DECK),(18,50,MAIN_DECK),(50,58,WAIST_DECK),(44,63.5,MAIN_DECK)]


def inset(poly, d):
    # Mitred inward offset of a CCW polygon: fixed-width chines on every edge.
    out = []
    n = len(poly)
    for i in range(n):
        (ax,ay),(bx,by),(cx,cy) = poly[i-1],poly[i],poly[(i+1)%n]
        n0 = _left(bx-ax,by-ay)
        n1 = _left(cx-bx,cy-by)
        k = d/(1+n0[0]*n1[0]+n0[1]*n1[1])
        out.append((bx+(n0[0]+n1[0])*k, by+(n0[1]+n1[1])*k))
    return out


def _left(x, y):
    length = (x*x+y*y)**.5
    return (-y/length, x/length)


def prism(kit, name, plan, profile):
    n = len(plan)
    verts = [(x,y,z) for d,z in profile for x,y in inset(plan,d)]
    faces = [tuple(reversed(range(n)))]
    for r in range(len(profile)-1):
        faces += [(r*n+i, r*n+(i+1)%n, (r+1)*n+(i+1)%n, (r+1)*n+i) for i in range(n)]
    top = (len(profile)-1)*n
    faces.append(tuple(range(top, top+n)))
    return kit.mesh(name, verts, faces, kit.orange)


def union(hull, other):
    bpy.context.view_layer.objects.active = hull
    mod = hull.modifiers.new('Hull block union', 'BOOLEAN')
    mod.operation = 'UNION'
    mod.object = other
    mod.solver = 'MANIFOLD'  # EXACT collapsed on the aft citadel's near-coincident faces
    bpy.ops.object.modifier_apply(modifier=mod.name)
    bpy.data.objects.remove(other, do_unlink=True)


def facet(name, point, down, angle, span=None):
    # A flat armour face through `point` that falls away along plan direction
    # `down` at `angle` degrees below horizontal: (name, point, outward
    # normal, optional X span of the cut).
    a = math.radians(angle)
    dx, dy = down
    length = (dx*dx+dy*dy)**.5
    normal = Vector((dx/length*math.sin(a), dy/length*math.sin(a), math.cos(a)))
    return name, Vector(point), normal, span


# The Cerberus forebody is a faceted shield, not a flat-topped box: the deck
# falls to the nose in a glacis, the bow shoulders are cut back by raked
# cheek faces, and the chin rakes up under the nose. Each is one plane
# sliced through the hull, so the faces stay flat and crisp.
# Cheeks hold clear of turrets 3 and 4 and stay above the port pod.
BOW_FACETS = [
    facet('Bow glacis', (46,0,MAIN_DECK), (1,0), 24),
    facet('Bow glacis lower course', (56,0,WAIST_DECK+.6), (1,0), 40),
    facet('Nose rake', (62,0,4), (1,0), 62),
    facet('Port bow cheek', (48,17.5,MAIN_DECK), (.81,.58), 38),
    facet('Starboard bow cheek', (48,-17.5,MAIN_DECK), (.81,-.58), 38),
    facet('Chin rake', (50,0,KEEL-1), (1,0), 147),
    # Forebody citadel flanks slope outboard like the sprite's armoured
    # shoulders; limited to the forebody so the engine pods keep their walls.
    facet('Port forebody shoulder', (0,21.5,MAIN_DECK), (0,1), 48, (-2,64)),
    facet('Starboard forebody shoulder', (0,-21.5,MAIN_DECK), (0,-1), 48, (-2,64)),
]


def plane_cut(hull, name, point, normal, span=None):
    # Remove everything on the normal side of the plane with a large box.
    # `span` (x0, x1) limits the cut along X; only for normals with no X part,
    # whose cutter keeps its local X on world X.
    normal = normal.normalized()
    centre = point+normal*200
    bpy.ops.mesh.primitive_cube_add(size=1, location=centre)
    cutter = bpy.context.object
    cutter.name = 'Temporary '+name+' cutter'
    cutter.scale = ((span[1]-span[0]) if span else 400, 400, 400)
    if span:
        cutter.location.x = (span[0]+span[1])/2
    cutter.rotation_euler = normal.to_track_quat('Z','Y').to_euler()
    bpy.context.view_layer.objects.active = hull
    mod = hull.modifiers.new(name, 'BOOLEAN')
    mod.operation = 'DIFFERENCE'
    mod.object = cutter
    mod.solver = 'MANIFOLD'
    bpy.ops.object.modifier_apply(modifier=mod.name)
    bpy.data.objects.remove(cutter, do_unlink=True)


def build_shell(kit):
    hull = None
    for name, mirror, profile, plan in BLOCKS:
        plans = [plan] + ([[(x,-y) for x,y in reversed(plan)]] if mirror else [])
        for p in plans:
            block = prism(kit, name, p, profile)
            if hull is None:
                hull = block
            else:
                union(hull, block)
    for name, point, normal, span in BOW_FACETS:
        plane_cut(hull, name, point, normal, span)
    hull.name = 'Main hull - level datum Z0'
    kit.hull = hull
    return hull


def build_bays(kit):
    # Two distinct bays, not a through tunnel. Port open, starboard sealed.
    hull=kit.hull
    for side in (1,-1):
        cutter=kit.box('Temporary bay cutter',(14,side*25,-.5),(23,20,9),kit.dark,0)
        bpy.context.view_layer.objects.active=hull
        mod=hull.modifiers.new('Lateral shuttle aperture','BOOLEAN')
        mod.operation='DIFFERENCE'
        mod.object=cutter
        bpy.ops.object.modifier_apply(modifier=mod.name)
        bpy.data.objects.remove(cutter,do_unlink=True)
        kit.box(('Port' if side==1 else 'Starboard')+' bay interior',(14,side*15.3,-.5),(22,.5,8.6),kit.dark,.1)
        kit.box('Bay level floor',(14,side*20,-5),(23,10,.5),kit.steel,.1)
        kit.box('Bay ceiling',(14,side*20,4.1),(23,10,.5),kit.dark,.1)
        for x in (2,26):
            kit.box('Bay jamb',(x,side*23,-.5),(1.2,3,10),kit.cream,.2)
        for z in (-5.5,4.6):
            kit.box('Bay coaming',(14,side*23,z),(25,3,1),kit.cream,.2)
        if side==-1:
            kit.box('Starboard bay sealed sectional door',(14,-23,-.4),(22,.8,8.5),kit.steel,.15)
            for x in (6,10,14,18,22):
                kit.box('Closed door stiffener',(x,-23.6,-.4),(.25,.3,8),kit.orange,.05)
        else:
            for x in (6,22):
                kit.box('Port bay interior lamp',(x,16,3.2),(2,.4,.5),kit.lightmat,.05)
                kit.box('Empty maintenance cradle',(x,19,-4.4),(1.2,6,.7),kit.steel,.1)
    # Flat shading: smooth normals across a chine read as an inflated skin.
    for poly in hull.data.polygons:
        poly.use_smooth=False
    b=hull.modifiers.new('Hull edge radii','BEVEL')
    b.width=.3
    b.segments=2


def armor_patch(kit,name,x0,x1,y0,y1,mat,offset=.22):
    y0,y1=sorted((y0,y1))
    pv=[]
    count=5
    for i in range(count):
        for j in range(count):
            x=x0+(x1-x0)*i/(count-1)
            y=y0+(y1-y0)*j/(count-1)
            hit=kit.surface(x,y)
            if hit is None:
                return None
            p,normal=hit
            pv.append(tuple(p+normal*offset))
    pf=[]
    for i in range(count-1):
        for j in range(count-1):
            a=i*count+j
            pf.append((a,a+count,a+count+1,a+1))
    me=bpy.data.meshes.new(name)
    me.from_pydata(pv,[],pf)
    me.update()
    ob=bpy.data.objects.new(name,me)
    bpy.context.collection.objects.link(ob)
    ob.data.materials.append(mat)
    sol=ob.modifiers.new('Armor plate thickness','SOLIDIFY')
    sol.thickness=.45
    sol.offset=1
    return ob


def build_armor(kit):
    # Layered plates sit on the flat decks, clear of the bridge strip |y|<7.
    for x in (21,29,37):
        for y in (7.5,14):
            for side in (1,-1):
                armor_patch(kit,'Forebody replaceable armor',x,x+7,side*y,side*(y+5.5),kit.cream if (x+y*side)%3>1 else kit.orange)
    for x in (28.1,36.1):
        for side in (-1,1):
            armor_patch(kit,'Forebody reinforcing band',x,x+1.1,side*7.5,side*20,kit.steel,.7)

    for side in (-1,1):
        # Keep these reinforcements clear of the existing bay aperture and pod.
        for x in (1,27):
            kit.box('Bay structural buttress',(x,side*23,-.5),(2,3.5,14),kit.steel,.5)
        kit.cylinder('Protected waist service line',(14,side*24.2,7.2),.7,21,kit.steel,'X')


def build_bow(kit):
    # Bow-quarter shoulder decks carry louvered intakes; the nose gets a cap.
    for s in (1,-1):
        kit.box('Bow shoulder intake',(51.5,s*13,WAIST_DECK+.3),(6,4.4,.8),kit.dark,.2)
        for dx in (-2,-1,0,1,2):
            kit.box('Intake louver',(51.5+dx,s*13,WAIST_DECK+.8),(.3,4,.25),kit.steel,.05)


def build_details(kit):
    build_armor(kit)
    build_bow(kit)
