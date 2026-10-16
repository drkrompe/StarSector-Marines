"""RCS attitude thrusters: quad-style clusters of small bell nozzles.

Eight clusters, each a gunmetal housing block carrying two to four small
faceted bells that point along different ship axes (+X bow, +Y port, +Z up):

  bow-port, bow-stbd     on the raked bow cheeks above the belt (x ~53,
                         |y| ~19): lateral, fore, up. The belt plate below
                         the cheek leaves no clear line for a down bell.
  pod-port, pod-stbd     on the forward outboard corner of each drive pod's
                         cowl (x ~-4.5, |y| ~37.5, the widest station):
                         up, down, lateral, fore.
  stern-port, stern-stbd bolted to the outboard wall of each thrust housing
                         (x ~-28.5, |y| ~39): lateral, up, down.
  dorsal                 on the stern cross girder, centreline (x ~-28.4):
                         up-1 (port of centre), up-2 (starboard).
  ventral                under the flat keel forward (x ~44): down-1, down-2.

The housings are corner blocks: on the bow cheek and the pod cowl the wall is
raked, so an axis-aligned block sunk into it shows a fore face and an
outboard face and every bell fires square to a ship axis.

How the ship steers. Axis names are the exhaust direction; the push on the
ship is the opposite way.
- Roll (about X; a bank to starboard drops the starboard side): bank to
  starboard fires pod-port down and pod-stbd up, a pure couple at the widest
  lateral stations (|y| ~38, about 25 m apart). Bank to port fires pod-port up
  and pod-stbd down. The C1 10 degree starboard bank is pod-port down plus
  pod-stbd up, trimmed by the bow up bells if the nose wants to rise.
- Yaw (about Z): bow to port fires bow-stbd stbd (pushes the bow to port)
  with stern-port port (pushes the stern to starboard), arms of about +53 and
  -28 along X. Bow to starboard fires bow-port port with stern-stbd stbd. The
  pod fore bells fired on one side only add a weaker yaw (pod-stbd fore turns
  the bow to starboard) and double as the brakes.
- Pitch (about Y): nose up fires ventral down-1/down-2 (lifts the bow), dorsal
  up-1/up-2 and stern-port/stbd up (push the stern down). Nose down fires
  bow-port/stbd up and stern-port/stbd down.
- Translation: all lateral bells on one side, all fore bells, or ventral with
  the stern down bells for heave.

Seating: every cluster is placed from a ray cast against the clean ship at
build time (a side cast for the cheek, cowl and housing wall, a downward cast
onto the girder, an upward cast onto the keel), and each housing and bell is
then checked with a BVH overlap test against the nearby clean parts; any
clash is printed as 'RCS clash'.

Names (identifiers for damage, surfacing and VFX):
  'RCS <cluster> housing'          the block, its dark nozzle ports included
  'RCS <cluster> <axis> nozzle'    one bell with its flange and bolts
  'RCS <cluster> <axis> anchor'    empty at the bell mouth, local +X along the
                                   exhaust; properties cluster, axis,
                                   radius_m and working (True as built).
Base materials only: the housing in a new gunmetal, the bells in a darker
heat-tinted steel, their insides kit.dark.
"""
import math
import bpy
from mathutils import Vector, Matrix
from mathutils.bvhtree import BVHTree
import sw_kit
from sw_hull import crisp

V = Vector
MPU = 0.65  # metres per unit (build_reference.METRES_PER_UNIT)
SIDES = 12

AXIS_NAME = {(1,0,0): 'fore', (-1,0,0): 'aft', (0,1,0): 'port', (0,-1,0): 'stbd',
             (0,0,1): 'up', (0,0,-1): 'down'}


def u(metres):
    return metres/MPU


def axis_name(a):
    return AXIS_NAME[tuple(int(round(c)) for c in a)]


class Geo:
    """Polygon soup with a material index per face."""
    def __init__(self):
        self.v, self.f, self.m = [], [], []

    def add(self, verts, faces, mat=0):
        o = len(self.v)
        self.v += [tuple(p) for p in verts]
        self.f += [tuple(i+o for i in face) for face in faces]
        self.m += [mat]*len(faces)

    def obox(self, c, half, mat=0):
        c = V(c)
        hx, hy, hz = half
        pts = [c+V((sx*hx, sy*hy, sz*hz)) for sz in (-1,1)
               for sx,sy in ((-1,-1),(1,-1),(1,1),(-1,1))]
        self.add(pts, [(3,2,1,0),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)], mat)

    def lathe(self, base, axis, profile, mats, sides=SIDES, turn=.5):
        """Surface of revolution about axis from base. profile: (d, radius)
        points, first and last on the axis (radius 0). mats[i] is the material
        of the band between profile points i and i+1."""
        base, axis = V(base), V(axis).normalized()
        e1, e2 = basis(axis)
        def ring(d, r):
            return [base+axis*d+(e1*math.cos(math.tau*(k+turn)/sides)+e2*math.sin(math.tau*(k+turn)/sides))*r
                    for k in range(sides)]
        for i, ((d0, r0), (d1, r1)) in enumerate(zip(profile, profile[1:])):
            if r0 == 0 and r1 == 0:
                continue
            if r0 == 0:
                ring1 = ring(d1, r1)
                self.add([base+axis*d0]+ring1, [(0, 1+(k+1) % sides, 1+k) for k in range(sides)], mats[i])
            elif r1 == 0:
                ring0 = ring(d0, r0)
                self.add(ring0+[base+axis*d1], [(k, (k+1) % sides, sides) for k in range(sides)], mats[i])
            else:
                ring0, ring1 = ring(d0, r0), ring(d1, r1)
                self.add(ring0+ring1, [(k, (k+1) % sides, sides+(k+1) % sides, sides+k)
                                       for k in range(sides)], mats[i])

    def disc(self, c, axis, r, h, mat=0, sides=6):
        """Short prism standing on c along axis (a bolt head, a port)."""
        self.lathe(c, axis, [(0,0), (0,r), (h,r), (h,0)], [mat]*3, sides, 0)


def basis(a):
    ref = V((0,0,1)) if abs(a.z) < .9 else V((1,0,0))
    e1 = a.cross(ref).normalized()
    return e1, a.cross(e1).normalized()


def finish(kit, name, geo, mats, edge):
    ob = kit.mesh(name, geo.v, geo.f, mats[0])
    for m in mats[1:]:
        ob.data.materials.append(m)
    for poly, i in zip(ob.data.polygons, geo.m):
        poly.material_index = i
    ob.data.validate()
    ob.data.update()
    if edge:
        crisp(ob, edge)
    return ob


# ---- the bell ---------------------------------------------------------------
def bell_profile(r):
    """Flange, valve neck, a narrow throat and a three-facet contoured bell
    ending in a thin flat lip; dark inside. r is the mouth radius."""
    tf, neck, t = .16*r, .26*r, .07*r
    d1 = tf+neck
    L = 1.6*r
    end = d1+L
    out = [(0,0), (0,.92*r), (tf,.92*r), (tf,.46*r), (d1,.46*r), (d1,.3*r+t),
           (d1+.3*L,.6*r+t), (d1+.65*L,.86*r+t), (end,r+t), (end,r)]
    inner = [(d1+.65*L,.86*r), (d1+.3*L,.6*r), (d1+.06*r,.3*r), (d1+.06*r,0)]
    mats = [0]*9+[1]*4
    return out+inner, mats, end, tf


def nozzle(kit, mats, cluster, axis_tag, base, a, r):
    """One bell on a housing face at base, exhaust along a. Returns
    (nozzle object, anchor empty)."""
    a = V(a).normalized()
    profile, pm, end, tf = bell_profile(r)
    geo = Geo()
    geo.lathe(base, a, profile, pm)
    e1, e2 = basis(a)
    for k in range(6):
        t = math.tau*(k+.5)/6
        geo.disc(V(base)+a*tf+(e1*math.cos(t)+e2*math.sin(t))*.72*r, a, .085*r, .09*r, 0, 6)
    ob = finish(kit, f'RCS {cluster} {axis_tag} nozzle', geo, mats, .012)
    mouth = V(base)+a*end
    anchor = bpy.data.objects.new(f'RCS {cluster} {axis_tag} anchor', None)
    bpy.context.collection.objects.link(anchor)
    anchor.empty_display_type = 'SINGLE_ARROW'
    anchor.empty_display_size = r*2
    ref = V((0,0,1)) if abs(a.z) < .9 else V((1,0,0))
    z = (ref-a*ref.dot(a)).normalized()
    y = z.cross(a)
    rot = Matrix((a, y, z)).transposed().to_4x4()
    anchor.matrix_world = Matrix.Translation(mouth) @ rot
    anchor['cluster'] = cluster
    anchor['axis'] = axis_tag
    anchor['radius_m'] = round(r*MPU, 3)
    anchor['working'] = True
    return ob, anchor


# ---- seating ------------------------------------------------------------------
def cast(origin, direction):
    """First hit on the ship (no RCS part) from origin along direction."""
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    o, d = V(origin), V(direction).normalized()
    for _ in range(30):
        ok, p, n, _, ob, _ = bpy.context.scene.ray_cast(dg, o, d)
        if not ok:
            return None
        if not ob.name.startswith('RCS '):
            return p, (n if n.dot(d) < 0 else -n), ob
        o = p+d*.02
    return None


def world_bvh(ob, dg):
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    mw = ob.matrix_world
    verts = [mw @ v.co for v in me.vertices]
    polys = [tuple(p.vertices) for p in me.polygons]
    ev.to_mesh_clear()
    return BVHTree.FromPolygons(verts, polys) if polys else None


def box_of(ob):
    pts = [ob.matrix_world @ V(c) for c in ob.bound_box]
    return (V([min(q[k] for q in pts) for k in range(3)]), V([max(q[k] for q in pts) for k in range(3)]))


def check(objs, hosts):
    """BVH overlap of new RCS parts against nearby clean parts. Housings may
    sink into their host; bells may touch only their own housing."""
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    clashes = []
    others = [o for o in bpy.data.objects if o.type == 'MESH' and not o.name.startswith('RCS ')
              and not o.hide_render]
    for ob in objs:
        lo, hi = box_of(ob)
        mine = world_bvh(ob, dg)
        if mine is None:
            continue
        for o in others:
            if ob.name.endswith('housing') and o.name in hosts:
                continue
            a, b = box_of(o)
            if any(a[i] > hi[i]+.05 or b[i] < lo[i]-.05 for i in range(3)):
                continue
            t = world_bvh(o, dg)
            if t and mine.overlap(t):
                clashes.append((ob.name, o.name))
        if not ob.name.endswith('housing'):
            for o in objs:
                if o is ob or o.name.endswith('housing'):
                    continue
                t = world_bvh(o, dg)
                if t and mine.overlap(t):
                    clashes.append((ob.name, o.name))
    for c in clashes:
        print('RCS clash:', *c)
    return clashes


# ---- clusters -------------------------------------------------------------------
class Cluster:
    def __init__(self, name, centre, half, r):
        self.name, self.c, self.half, self.r = name, V(centre), V(half), r
        self.housing = Geo()
        self.housing.obox(self.c, self.half)
        self.bells = []   # (axis tag, base point, direction)

    def face(self, a, offsets=(0,0,0)):
        """Point on the housing face whose outward normal is a, shifted by
        offsets (fractions of the half extents along the other two axes)."""
        a = V(a)
        k = [i for i in range(3) if abs(a[i]) > .5][0]
        p = self.c.copy()
        for i in range(3):
            p[i] += a[i]*self.half[i] if i == k else offsets[i]*self.half[i]
        return p

    def bell(self, a, offsets=(0,0,0), tag=None):
        base = self.face(a, offsets)
        tag = tag or axis_name(a)
        boss = .1
        self.housing.disc(base-V(a)*.02, a, 1.08*self.r, boss+.02, 0, SIDES)
        base = base+V(a)*boss
        self.bells.append((tag, base, V(a)))
        # Dark port under the flange: what shows if the bell is taken.
        self.housing.disc(base-V(a)*.01, a, .6*self.r, .03, 1, SIDES)

    def band(self, dz, grow=.05, h=.09):
        """A proud joint band round the block: a two-part machined housing."""
        self.housing.obox(self.c+V((0, 0, dz)), (self.half.x+grow, self.half.y+grow, h))

    def build(self, kit, mats, edge=.1):
        hmats = [mats['housing'], mats['dark']]
        housing = finish(kit, f'RCS {self.name} housing', self.housing, hmats, edge)
        objs, anchors = [housing], []
        for tag, base, a in self.bells:
            ob, anchor = nozzle(kit, [mats['bell'], mats['dark']], self.name, tag, base, a, self.r)
            objs.append(ob)
            anchors.append(anchor)
        return objs, anchors


def flange_plate(cl, wall, depth, margin, rim):
    """A bolted mounting plate between a block and the wall: wall is the
    outward wall normal (a ship axis), depth the plate thickness, margin its
    overhang past the block per axis; a row of bolt heads runs along each
    rim across axis `rim` (where the overhang is wide enough)."""
    w = V(wall)
    k = [i for i in range(3) if abs(w[i]) > .5][0]
    c = cl.c.copy()
    c[k] = cl.c[k]-w[k]*(cl.half[k]+depth/2)
    half = [h+m for h, m in zip(cl.half, margin)]
    half[k] = depth/2
    cl.housing.obox(c, half)
    j = [i for i in range(3) if i not in (k, rim)][0]
    for si in (-1, 1):
        for f in (-.6, 0, .6):
            p = c.copy()
            p[k] += w[k]*depth/2
            p[rim] += si*(cl.half[rim]+margin[rim]/2)
            p[j] += f*cl.half[j]
            cl.housing.disc(p, w, .07, .06, 0, 6)
    return c, half


def build(kit):
    mats = {'housing': sw_kit.material('RCS housing gunmetal', (.12,.13,.14), .65, .45),
            'bell': sw_kit.material('RCS bell heat-tinted steel', (.15,.13,.12), .85, .35),
            'dark': kit.dark}
    made, hosts, anchors = [], set(), []

    def seat_side(x, z, s, host):
        hit = cast((x, s*80, z), (0, -s, 0))
        assert hit and hit[2].name.startswith(host), (x, z, s, hit and hit[2].name)
        hosts.add(hit[2].name)
        return hit

    for s, side in ((1, 'port'), (-1, 'stbd')):
        lat = V((0, s, 0))
        # Bow cheek: the raked armour face above the belt plate. The block is
        # sunk corner-first into the cheek, so its fore and outboard faces and
        # its top stand clear.
        p, n, _ = seat_side(55.0, 4.0, s, 'Forebody armour module')
        n.z = 0
        n.normalize()
        cl = Cluster(f'bow-{side}', p+n*.7, (1.1, 1.1, 1.05), u(.3))
        cl.bell(lat, (.3, 0, -.05))
        cl.bell(V((1,0,0)), (0, s*.32, -.05))
        cl.bell(V((0,0,1)), (.35, s*.35, 0))
        cl.band(.72)
        made.append(cl)
        # Pod cowl, forward outboard corner, the widest station of the ship.
        p, n, _ = seat_side(-5.55, 6.2, s, 'Main hull')
        n.z = 0
        n.normalize()
        cl = Cluster(f'pod-{side}', p+n*.45, (.95, 1.0, 1.3), u(.3))
        cl.bell(V((0,0,1)), (.3, s*.38, 0))
        cl.bell(V((0,0,-1)), (.3, s*.38, 0))
        cl.bell(lat, (.15, 0, 0))
        cl.bell(V((1,0,0)), (0, s*.3, 0))
        cl.band(.98)
        made.append(cl)
        # Thrust housing outboard wall, between the barrel band and the frame
        # rib, above the seam strap: a block on a bolted mounting plate.
        p, n, _ = seat_side(-28.5, 3.6, s, 'Thrust housing')
        plate = .12
        hy = .62
        cl = Cluster(f'stern-{side}', (p.x, p.y+s*(plate+hy), p.z), (.6, hy, 1.35), u(.27))
        flange_plate(cl, lat, plate, (.08, 0, .26), 2)
        cl.bell(lat, (0, 0, 0))
        cl.bell(V((0,0,1)), (0, s*.15, 0))
        cl.bell(V((0,0,-1)), (0, s*.15, 0))
        cl.band(1.0)
        made.append(cl)

    # Dorsal: on the stern cross girder, raised on two feet clear of its
    # centre bolt head.
    tops = []
    for y in (-1.15, 1.15):
        hit = cast((-28.4, y, 60), (0, 0, -1))
        assert hit and 'girder' in hit[2].name, hit and hit[2].name
        hosts.add(hit[2].name)
        tops.append(hit[0].z)
    top = max(tops)
    feet = .38
    cl = Cluster('dorsal', (-28.4, 0, top+feet+.55), (.72, 1.55, .55), u(.3))
    for y in (-1.15, 1.15):
        cl.housing.obox((-28.4, y, top+feet/2-.03), (.6, .24, feet/2+.03))
    cl.bell(V((0,0,1)), (0, .52, 0), 'up-1')
    cl.bell(V((0,0,1)), (0, -.52, 0), 'up-2')
    made.append(cl)

    # Ventral: under the flat keel forward, a shallow block on a plate.
    lows = []
    for x in (43.3, 45.1):
        for y in (-1.6, 1.6):
            hit = cast((x, y, -60), (0, 0, 1))
            assert hit, (x, y)
            hosts.add(hit[2].name)
            lows.append(hit[0].z)
    keel = min(lows)
    cl = Cluster('ventral', (44.2, 0, keel-.12-.5), (.8, 1.5, .5), u(.3))
    flange_plate(cl, V((0,0,-1)), .12, (.26, .1, 0), 0)
    cl.bell(V((0,0,-1)), (0, .5, 0), 'down-1')
    cl.bell(V((0,0,-1)), (0, -.5, 0), 'down-2')
    made.append(cl)

    objs = []
    for cl in made:
        o, a = cl.build(kit, mats)
        objs += o
        anchors += a
    check(objs, hosts)
    return anchors
