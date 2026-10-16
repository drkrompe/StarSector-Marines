"""Port forward grapple arm: the one asymmetric fitting on the hull.

Reading of the Cerberus sprite's port-bow protrusion. The grey body hugging
the port flank (x 28..48) is the machinery sponson of a folded boarding /
cargo grapple arm, its work lights along the outer edge. The pale band that
runs from the module up the forebody edge to the nose is the arm itself,
stowed forward in two straight segments: a boom along the citadel edge and a
forearm angled down the glacis, its grapple head parked against the nose
block. A small davit over the bay mouth (x 17) is the aft fitting with the
green light. Port only; no starboard twin.
"""
import bmesh
from mathutils import Vector
import sw_kit
from sw_hull import prism

# Arm joints (x, y): shoulder pin on the citadel edge, elbow at the bow
# shoulder, wrist beside the nose block. Z comes from hull raycasts.
SHOULDER, ELBOW, WRIST = (32.5, 20.4), (47.4, 16.6), (50.4, 9.2)
BOOM_W, BOOM_H = 3.6, 2.1
CLEAR = .8  # gap over hull plating (armour patches stand .7 proud)


def solid(kit, name, verts, faces, mat, bevel=.12):
    ob = kit.mesh(name, verts, faces, mat, bevel=bevel)
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(ob.data)
    bm.free()
    return ob


def beam(kit, name, p0, p1, w, h, mat, ch=.45, bevel=.1):
    # Box-section member with chamfered top corners between two points.
    p0, p1 = Vector(p0), Vector(p1)
    u = (p1-p0).normalized()
    side = Vector((0, 0, 1)).cross(u).normalized()
    up = u.cross(side)
    sec = [(-w/2,-h/2),(w/2,-h/2),(w/2,h/2-ch),(w/2-ch,h/2),(-w/2+ch,h/2),(-w/2,h/2-ch)]
    verts = [tuple(p+side*s+up*t) for p in (p0, p1) for s, t in sec]
    n = len(sec)
    faces = [tuple(range(n)), tuple(range(2*n-1, n-1, -1))]
    faces += [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
    return solid(kit, name, verts, faces, mat, bevel)


def ground(kit, x, y, default):
    hit = kit.surface(x, y)
    return hit[0].z if hit else default


def lay(kit, a, za, b, h):
    # Lowest far-end height for a member from (a, za) to b that still clears
    # the plating everywhere past its first quarter (the joint covers that).
    need = []
    for k in range(3, 13):
        t = k/12
        g = ground(kit, a[0]+(b[0]-a[0])*t, a[1]+(b[1]-a[1])*t, -99)
        need.append((g+CLEAR+h/2-za*(1-t))/t)
    return max(need)


def build(kit):
    pale = sw_kit.material('Grapple arm pale grey', (.40, .40, .38), .45)
    amber = sw_kit.material('Port arm work light amber', (.9, .6, .2), emission=(1, .55, .12))
    green = sw_kit.material('Davit position light green', (.2, .7, .3), emission=(.25, 1, .35))
    for m, e in ((amber, 5), (green, 5)):
        m.node_tree.nodes['Principled BSDF'].inputs['Emission Strength'].default_value = e

    # Machinery sponson: lower body hangs on the flank wall and overhangs it;
    # a dark seam, then the upper cowl on the shoulder ledge. Both tiers are
    # split fore/aft at one dark joint. Inboard face buried against the citadel.
    plan = [(28.5,24),(46,24),(48,26),(48,31),(45.4,34.3),(30,34.3),(28.5,32.8)]
    for name, x0, x1 in (('aft', 28.5, 37.5), ('forward', 38.3, 48)):
        part = [(max(x0, min(x1, x)), y) for x, y in plan]
        part = [p for i, p in enumerate(part) if p != part[i-1]]
        prism(kit, f'Grapple sponson {name} lower body', part, [(1.6,-3.2),(0,-1.6),(0,4.6)], kit.steel)
        prism(kit, f'Grapple sponson {name} cowl', part, [(0,5.6),(0,10.6),(1.3,12)], kit.steel)
    prism(kit, 'Grapple sponson dark core', [(29,24.2),(47.4,24.2),(47.4,31),(45,33.7),(29,33.7)],
          [(0,-2.4),(0,11.6)], kit.dark)

    # Knee gussets carry the overhang into the flank wall.
    for x in (31, 36, 41.5):
        solid(kit, 'Sponson knee gusset',
              [(x+dx, y, z) for dx in (-.6, .6) for y, z in ((29,-3.0),(33.2,-3.0),(29,-6.2))],
              [(0,1,2),(5,4,3),(0,3,4,1),(1,4,5,2),(2,5,3,0)], kit.steel, .08)
    # Outboard face: intake grille on the forward lower body, cable hose run.
    kit.box('Sponson intake recess', (42.6, 34.32, 1.2), (6.4, .1, 3.6), kit.dark, 0)
    for k in range(5):
        kit.box('Sponson intake bar', (40.2+k*1.2, 34.45, 1.2), (.35, .3, 3.4), kit.steel, .04)
    for x in (30.4, 34.2):
        kit.box('Sponson service hatch', (x, 34.32, 1.0), (2.4, .1, 3.4), kit.dark, 0)

    # Aft cowl: louvred machinery vent over the slew and luff gear.
    kit.box('Sponson vent recess', (33, 29.2, 12.02), (7, 5.2, .1), kit.dark, 0)
    for k in range(6):
        kit.box('Sponson vent louvre', (30.2+k*1.12, 29.2, 12.15), (.42, 5, .3), kit.steel, .04)

    # Forward cowl: the grapple cable winch in an open cradle.
    kit.cylinder('Grapple winch drum', (41.6, 27.4, 14.1), 2.0, 5.2, pale, 'X', 12, .12)
    for x in (38.8, 44.4):
        kit.cylinder('Winch drum flange', (x, 27.4, 14.1), 2.45, .4, kit.steel, 'X', 12, .08)
        kit.box('Winch cradle cheek', (x+(-.55 if x < 40 else .55), 27.4, 13.2), (.6, 4.4, 2.6), kit.steel, .1)
    kit.box('Winch fairlead', (46.6, 27.4, 12.6), (1.6, 2.2, 1.2), kit.dark, .1)

    # Front face: access hatch and a work light at the outer corner.
    kit.box('Sponson forward hatch', (48.02, 28.6, 2.6), (.2, 3.0, 4.0), kit.dark, .05)
    kit.box('Sponson forward hatch frame', (48.1, 28.6, 5.0), (.3, 3.6, .4), kit.steel, .04)

    # Work lights along the outer edge (sprite: yellow at x 45, 35, 30).
    for x in (30.5, 35, 45):
        kit.box('Grapple sponson lamp hood', (x, 33.55, 11.25), (1.6, 1.1, .7), kit.steel, .06)
        kit.box('Grapple sponson work light', (x, 34.12, 11.15), (1.2, .1, .4), amber, 0)
    kit.box('Sponson bow work light', (48.12, 25.3, 9.4), (.1, 1.2, .5), amber, 0)

    # Shoulder: slew housing climbing the citadel wall from the cowl, then
    # a pedestal and clevis on the citadel edge carrying the boom root.
    sz = ground(kit, *SHOULDER, 14.5)
    kit.box('Arm slew gear casing', (31.6, 24.4, 13.4), (4.6, 4.6, 4.0), kit.steel, .25)
    kit.cylinder('Arm shoulder pedestal', (SHOULDER[0], SHOULDER[1], sz+.9), 2.3, 2.6, kit.steel, 'Z', 8, .15)
    kz = sz+3.1
    for dy in (-1.6, 1.6):
        kit.box('Shoulder clevis plate', (SHOULDER[0], SHOULDER[1]+dy, kz-.5), (3.0, .5, 3.4), kit.steel, .1)
    kit.cylinder('Shoulder pivot pin', (SHOULDER[0], SHOULDER[1], kz), .75, 4.2, kit.dark, 'Y', 12, .05)

    # Boom and forearm, folded flat along the hull, each clear of plating.
    ez = lay(kit, SHOULDER, kz, ELBOW, BOOM_H)
    wz = lay(kit, ELBOW, ez, WRIST, BOOM_H*.85)
    print(f'port arm joints z: shoulder {kz:.2f} elbow {ez:.2f} wrist {wz:.2f}')
    shoulder, elbow, wrist = Vector((*SHOULDER, kz)), Vector((*ELBOW, ez)), Vector((*WRIST, wz))
    beam(kit, 'Grapple arm boom', shoulder, elbow, BOOM_W, BOOM_H, pale)
    beam(kit, 'Grapple arm forearm', elbow, wrist, BOOM_W*.85, BOOM_H*.85, pale)
    # Dark web seam along both sides splits each member into two flanges.
    beam(kit, 'Boom web seam', shoulder, elbow, BOOM_W+.12, .35, kit.dark, 0, 0)
    beam(kit, 'Forearm web seam', elbow, wrist, BOOM_W*.85+.12, .3, kit.dark, 0, 0)
    # Hydraulic luff ram and its dark sleeve along the boom's outboard side.
    off = Vector((0, 2.2, -.2))
    beam(kit, 'Boom luff ram', shoulder+off+Vector((1, .6, -.6)), shoulder.lerp(elbow, .55)+off, .7, .7, kit.steel, .15, .05)
    beam(kit, 'Boom luff ram rod', shoulder.lerp(elbow, .5)+off, shoulder.lerp(elbow, .78)+off, .4, .4, kit.dark, .1, 0)
    kit.cylinder('Arm elbow knuckle', tuple(elbow), 2.1, BOOM_H+.8, kit.steel, 'Z', 8, .12)
    kit.cylinder('Elbow knuckle cap', (elbow.x, elbow.y, elbow.z+BOOM_H/2+.45), 1.3, .2, kit.dark, 'Z', 8, 0)
    # Stowage saddles: the arm rests on the hull, it does not float.
    for a, b, t, h in ((shoulder, elbow, .62, BOOM_H), (elbow, wrist, .5, BOOM_H*.85)):
        p = a.lerp(b, t)
        g = ground(kit, p.x, p.y, p.z-3)
        top = p.z-h/2
        kit.box('Arm stowage saddle', (p.x, p.y, (g-.4+top)/2), (1.2, 2.8, top-g+.4), kit.steel, .08)

    # Grapple head parked against the nose block: wrist yoke, clamp pad and
    # two jaws reaching forward over the glacis.
    kit.cylinder('Grapple wrist yoke', tuple(wrist), 1.6, BOOM_H+.4, kit.steel, 'Z', 8, .1)
    hx, hy = wrist.x+2.0, wrist.y-.2
    kit.box('Grapple head body', (hx, hy, wz), (2.8, 3.0, 2.3), kit.steel, .2)
    kit.box('Grapple clamp pad', (hx+1.5, hy, wz), (.3, 2.4, 1.7), kit.dark, .05)
    for dz in (-.62, .62):
        kit.box('Grapple jaw', (hx+2.3, hy, wz+dz), (1.8, 2.0, .45), kit.dark, .06)

    # Bay davit: a short cantilevered derrick head over the bay mouth with a
    # sheave hanging off its tip and the green position light.
    dz0 = ground(kit, 17, 23, 10.5)
    prism(kit, 'Bay davit head', [(14.9,21.5),(19.3,21.5),(19.3,28.4),(18.4,29.2),(15.8,29.2),(14.9,28.4)],
          [(0,dz0-2.8),(0,dz0-.2),(.6,dz0+.4)], kit.steel)
    kit.box('Davit seam', (17.1, 25.4, dz0-1.5), (4.6, .3, 1.5), kit.dark, 0)
    kit.cylinder('Davit sheave', (17.1, 28.3, dz0-3.6), .85, 1.4, kit.dark, 'X', 12, .05)
    kit.box('Davit sheave cheek', (17.1, 28.3, dz0-3.2), (2.0, .9, 1.6), kit.steel, .06)
    kit.box('Davit position light', (17.1, 29.25, dz0-.6), (1.1, .12, .45), green, 0)
