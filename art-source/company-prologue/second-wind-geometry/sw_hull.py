"""Forebody pressure hull, bow facets, keel, flank bays and forebody armor.

Owns the forward planform, its faceted bow and the stepped deck heights; the
aft engine pods are sw_engines and the dorsal spine is sw_bridge. Forward of
the bays the flank is not the hull's own skin: square-ended armour modules
with dark seam gaps between them, a steel belt, keel plate and overhanging
deck-edge lip stand on a set-back core, so the side reads in strata. Locked features
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
KEEL, WAIST_DECK, MAIN_DECK = -7.5, 10.5, 14.5

# Section profiles: (inset from plan outline, z), keel to deck. One keel
# chamfer, one vertical wall, one shoulder chamfer: a stack of chamfers and
# knuckles read as a rounded pebble in side view. Horizontal lines come from
# the separate armour belt instead.
def side_profile(deck, wall_top, proud=0):
    return [(d-proud,z) for d,z in [(3,KEEL),(0,KEEL+3),(0,wall_top),(3,deck)]]

LOWER = side_profile(WAIST_DECK, 7.5)
# A plate belt standing proud of the flank wall along the datum.
BELT = [(0,-3.6),(-1.2,-3.6),(-1.2,.8),(0,1.8)]
def citadel(base):
    # A terrace step: vertical wall, one narrow lip chamfer at the deck edge.
    return [(0,base),(0,MAIN_DECK-.8),(.8,MAIN_DECK)]

# Forebody planform, measured off the vanilla sprite: widest (+-31.5) over
# x 30..40, then three big flats close the shield to a +-8 nose at x 57 (a
# many-vertex bow plus edge radii read as a rounded pebble).
PLAN = [(0,-22.5),(14,-24),(22,-28),(30,-31.5),(40,-31.5),(50,-23.5),(57,-8),
        (57,8),(50,23.5),(40,31.5),(30,31.5),(22,28),(14,24),(0,22.5)]
# Flank strata, keel to deck: steel keel plate, armour modules (orange) with a
# steel belt proud at the datum, and a steel deck-edge lip overhanging them.
MODULE_TOP, LIP = 9.2, (9.2, 10.8)
# Armour module stations forward of the bay: dark seam gaps fall between them.
STATIONS = [26.6, 37.6, 47.2, 60]
SEAM = .9
DECK_JOINTS = STATIONS[:2]
# Waist hull (bay section) keeps a plain wall straight up to the waist deck.
WAIST_WALL = [(3,KEEL),(0,KEEL+3),(0,WAIST_DECK)]

BLOCKS = [
    ('Forebody waist hull', False, WAIST_WALL, (0,27.5)),
    # Forward of the bay the pressure hull is a core set back behind the
    # armour modules, so seams between them open onto a recess.
    ('Forebody armoured core', False, [(4,KEEL),(1.4,KEEL+2.6),(1.4,WAIST_DECK)], (26,60)),
    # Raised forward citadel: the main deck, stepped in from the flank ledge.
    # Its deck plate overhangs the wall, a hard shadow line in side view.
    ('Forward citadel', False, [(0,8.5),(0,MAIN_DECK-1.2),(-.7,MAIN_DECK-1.2),(-.7,MAIN_DECK-.3),(-.4,MAIN_DECK)],
     [(16,-19.5),(26,-24),(42,-24),(50,-17),(50,17),(42,24),(26,24),(16,19.5)]),
    ('Bow chin', False, [(2,KEEL-2.5),(0,KEEL-1),(0,KEEL+1)],
     [(42,-11),(54,-11),(57,-7),(57,7),(54,11),(42,11)]),
    ('Ventral keel', False, [(1.2,KEEL-2.5),(0,KEEL-1.3),(0,KEEL+1)],
     [(-2,-6.5),(44,-6.5),(44,6.5),(-2,6.5)]),
]
# Deck plateaus aft to fore, for the manifest: (x0, x1, deck z).
THICKNESS_STATIONS = [(-36,-32,8),(-32,-1,MAIN_DECK),
                      (0,18,WAIST_DECK),(18,50,MAIN_DECK),(47.6,57,MAIN_DECK),(49,59.6,12.4)]


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


def prism(kit, name, plan, profile, mat=None):
    n = len(plan)
    verts = [(x,y,z) for d,z in profile for x,y in inset(plan,d)]
    faces = [tuple(reversed(range(n)))]
    for r in range(len(profile)-1):
        faces += [(r*n+i, r*n+(i+1)%n, (r+1)*n+(i+1)%n, (r+1)*n+i) for i in range(n)]
    top = (len(profile)-1)*n
    faces.append(tuple(range(top, top+n)))
    return kit.mesh(name, verts, faces, mat or kit.orange)


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
    facet('Bow glacis', (45,0,MAIN_DECK), (1,0), 27),
    facet('Bow glacis lower course', (53,0,WAIST_DECK+.6), (1,0), 40),
    facet('Nose rake', (57.5,0,1), (1,0), 58),
    facet('Port bow cheek', (48,17.5,MAIN_DECK), (.81,.58), 38),
    facet('Starboard bow cheek', (48,-17.5,MAIN_DECK), (.81,-.58), 38),
    facet('Chin rake', (50,0,KEEL-1), (1,0), 147),
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


def clip(poly, a, b, c):
    # Keep the part of a plan polygon where a*x+b*y <= c (Sutherland-Hodgman).
    out = []
    for i in range(len(poly)):
        p, q = poly[i-1], poly[i]
        fp, fq = a*p[0]+b*p[1]-c, a*q[0]+b*q[1]-c
        if fq <= 0:
            if fp > 0:
                t = fp/(fp-fq)
                out.append((p[0]+(q[0]-p[0])*t, p[1]+(q[1]-p[1])*t))
            out.append(q)
        elif fp <= 0:
            t = fp/(fp-fq)
            out.append((p[0]+(q[0]-p[0])*t, p[1]+(q[1]-p[1])*t))
    return [p for i,p in enumerate(out) if abs(p[0]-out[i-1][0])+abs(p[1]-out[i-1][1]) > 1e-6]


def band(x0, x1, plan=PLAN):
    return clip(clip(plan, 1, 0, x1), -1, 0, -x0)


def subtract(obj, cutter):
    bpy.context.view_layer.objects.active = obj
    mod = obj.modifiers.new('Recess', 'BOOLEAN')
    mod.operation = 'DIFFERENCE'
    mod.object = cutter
    mod.solver = 'MANIFOLD'
    bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)  # ahead of the chamfer
    bpy.ops.object.modifier_apply(modifier=mod.name)
    bpy.data.objects.remove(cutter, do_unlink=True)


def crisp(obj, width=.18):
    # One-segment chamfer on hard edges only: a highlight line, not a radius.
    b = obj.modifiers.new('Plate edge chamfer', 'BEVEL')
    b.width, b.segments, b.limit_method = width, 1, 'ANGLE'
    return obj


def faceted(kit, name, plan, profile, mat, facets=None, xs=None, proud=0):
    # A separate plate solid cut by the bow planes shifted `proud` outward,
    # so it skins the shield faces without sharing a plane with the hull;
    # `xs` trims it to square-ended sections between two frame stations.
    ob = prism(kit, name, plan, profile, mat)
    cuts = [(n, p+v*proud, v, s) for n,p,v,s in (BOW_FACETS if facets is None else facets)]
    if xs:
        cuts += [('Aft frame', Vector((xs[0],0,0)), Vector((-1,0,0)), None),
                 ('Fore frame', Vector((xs[1],0,0)), Vector((1,0,0)), None)]
    for fname, point, normal, span in cuts:
        plane_cut(ob, fname, point, normal, span)
    return crisp(ob)


def build_shell(kit):
    hull = None
    for name, mirror, profile, plan in BLOCKS:
        if isinstance(plan, tuple):
            plan = band(*plan)
        plans = [plan] + ([[(x,-y) for x,y in reversed(plan)]] if mirror else [])
        for p in plans:
            block = prism(kit, name, p, profile)
            if hull is None:
                hull = block
            else:
                union(hull, block)
    for name, point, normal, span in BOW_FACETS:
        plane_cut(hull, name, point, normal, span)
    # Frame joints carry on across the flank deck as open grooves, so each
    # armour module reads as a separate section from above too.
    for x in DECK_JOINTS:
        for s in (1,-1):
            subtract(hull, groove(kit, x, s))
    hull.name = 'Main hull - level datum Z0'
    kit.hull = hull
    return hull


def groove(kit, x, s):
    return kit.box('Temporary frame groove', (x,s*29.2,10.2), (SEAM,8.6,2.4), kit.dark, 0)


def build_flank(kit):
    # Dark recess skin behind the armour modules: what a seam gap opens onto.
    faceted(kit, 'Forebody seam recess', band(26,60),
            [(3.6,KEEL+.4),(1.2,KEEL+2.8),(1.2,MODULE_TOP)], kit.dark, proud=.1)
    modules = []
    for i in range(len(STATIONS)-1):
        xs = (STATIONS[i]+(SEAM/2 if i else 0), STATIONS[i+1]-SEAM/2)
        # Each module steps in above the belt: a lit ledge, a second stratum.
        # The last module is the whole bow shield: it rises to the main deck
        # and the bow planes cut its cheeks, glacis and rake in one block.
        top = MAIN_DECK if i == len(STATIONS)-2 else MODULE_TOP
        modules.append(faceted(kit, 'Forebody armour module', PLAN,
            [(2.5,KEEL),(0,KEEL+2.5),(0,5.6),(.7,5.6),(.7,top)], kit.orange, xs=xs, proud=.35))
        # Belt plates ride on each module; the port flank aft of x 47 stays
        # plain for the port service module, so only starboard is belted there.
        belt = PLAN if xs[0] > 46 else clip(PLAN, 0, 1, 0)
        faceted(kit, 'Forebody armour belt plate', belt, BELT, kit.steel, xs=xs, proud=.6)
    # Armoured nose block on the centreline (the sprite's cream nose): it
    # stands proud of the glacis and ahead of the nose rake.
    faceted(kit, 'Armoured nose block',
            [(49,-8),(57.6,-8),(59.6,-6),(59.6,6),(57.6,8),(49,8)],
            [(0,KEEL+1.6),(0,12.4)], kit.cream,
            [facet('Nose block brow', (56,0,12.4), (1,0), 40),
             facet('Nose block rake', (59.4,0,1), (1,0), 58),
             facet('Nose block chin', (52,0,KEEL), (1,0), 147)])
    # Keel plate: a steel strake under the keel chamfer, the bottom stratum.
    faceted(kit, 'Keel plate', band(1,50), [(5.5,KEEL-1.7),(3.6,KEEL+.3)], kit.steel, proud=.3)
    # Deck-edge lip: an overhanging ring whose underside throws a hard shadow
    # line along the flank. It ends at the last frame: the bow shield ahead
    # is all raked faces.
    lip = faceted(kit, 'Deck edge lip', PLAN,
                  [(-.8,LIP[0]),(-.8,LIP[1]-.5),(-.2,LIP[1])], kit.steel,
                  xs=(1, STATIONS[2]-SEAM/2))
    inner = [(x if x > 5 else -10, y) for x,y in inset(PLAN, 2.4)]
    subtract(lip, prism(kit, 'Temporary lip cutter', inner, [(0,LIP[0]-1),(0,LIP[1]+1)]))
    for x in DECK_JOINTS:
        for s in (1,-1):
            subtract(lip, groove(kit, x, s))
            kit.box('Frame groove floor', (x,s*29.2,9.15), (SEAM-.05,8.6,.3), kit.dark, 0)
    # Starboard intake bank on the straight flank of the first module (the
    # port flank there carries the service module).
    m = modules[0]
    subtract(m, kit.box('Temporary vent cutter', (33.5,-30.8,7.3), (5.6,1.4,2.6), kit.dark, 0))
    kit.box('Intake plenum', (33.5,-30.3,7.3), (5.6,.2,2.6), kit.dark, 0)
    for z in (6.4,7.0,7.6,8.2):
        kit.box('Intake louvre', (33.5,-30.5,z), (5.4,.35,.22), kit.steel, .04)
    return modules


# Back wall inner face |y| of each bay. The port bay is deepened so the
# company's Hermes (5.7 m beam) stands inside a door on the starboard bay's
# line (y 22.6) with about 0.4 m either side (owner, 2026-10-18).
BAY_WALL = {1: 12.4, -1: 15.55}


def build_bays(kit):
    # Two distinct bays, not a through tunnel. Port open, starboard sealed.
    hull=kit.hull
    for side in (1,-1):
        wall=BAY_WALL[side]
        cutter=kit.box('Temporary bay cutter',(14,side*(wall-.55+35)/2,-.5),(23,35-wall+.55,9),kit.dark,0)
        bpy.context.view_layer.objects.active=hull
        mod=hull.modifiers.new('Lateral shuttle aperture','BOOLEAN')
        mod.operation='DIFFERENCE'
        mod.object=cutter
        bpy.ops.object.modifier_apply(modifier=mod.name)
        bpy.data.objects.remove(cutter,do_unlink=True)
        kit.box(('Port' if side==1 else 'Starboard')+' bay interior',(14,side*(wall-.25),-.5),(22,.5,8.6),kit.dark,.1)
        kit.box('Bay level floor',(14,side*(wall-.55+25)/2,-5),(23,25-wall+.55,.5),kit.steel,.1)
        kit.box('Bay ceiling',(14,side*(wall-.55+25)/2,4.1),(23,25-wall+.55,.5),kit.dark,.1)
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
                kit.box('Port bay interior lamp',(x,wall+.45,3.2),(2,.4,.5),kit.lightmat,.05)
                kit.box('Empty maintenance cradle',(x,19,-4.4),(1.2,6,.7),kit.steel,.1)
    # Flat shading: smooth normals across a chine read as an inflated skin.
    for poly in hull.data.polygons:
        poly.use_smooth=False
    crisp(hull,.25)


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


def build_details(kit):
    build_flank(kit)
    build_armor(kit)
