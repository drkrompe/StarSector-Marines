"""Detail and cohesion pass: the joins that make the Lasher one built ship.

Owner review 2026-10-19 (independent livery r1): "Lasher hull needs detail,
sections seem 'disconnected'". The light pass 2 was built for 1.5-3 km and
left the gun pods, missile pods and engine nacelles as blocks set against
the spine and wing, with plain flats between them. This module runs after
the structure, pods and drives exist and adds, without renaming or moving
any of them:

- Join fairings (`Hull <side> ... fairing`): armour wedges that carry the
  spine's plating over onto the gun pods, ramp the pods' aft faces and the
  missile pods' aft faces down onto the wing, fair the nacelles' forward
  walls into the wing, and fill the spine-nacelle groove.
- Frames (`Hull frame <k> ...`): transverse gunmetal ribs cut from the hull
  itself (a thin slab of each host, grown outward), so one frame runs
  unbroken across spine, fairing, pod and wing at the same station.
- Buttresses, barbette collars, a bridge frame between each gun pod and its
  missile pod, conduit runs with brackets in the spine-nacelle grooves.
- Surface detail at the scale of a 90 m hull: armour panels draped on the
  wing, nose and walls (their gaps are the panel seams), hatches with dark
  rims, vent grilles, nacelle cooling fins, drive bezels.

Every part is seated by ray-casts against the frozen hull (`Field`), never
by hard-coded Z, and none is added to kit.hull, so the turret barbettes and
lamps seat exactly where they did. None of these parts is an enclosed
volume (ship_scale.SOLIDS is unchanged), and the planform stays inside the
sprite (IoU checked on every build).
"""
import math
import bmesh
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from ls_kit import boolean, revolve_x


class Field:
    """The hull frozen into one BVH, for seating details by ray-casts."""

    def __init__(self, objects):
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        verts, polys = [], []
        for ob in objects:
            ev = ob.evaluated_get(dg)
            me = ev.to_mesh()
            base = len(verts)
            verts += [ob.matrix_world @ v.co for v in me.vertices]
            polys += [tuple(base+i for i in p.vertices) for p in me.polygons]
            ev.to_mesh_clear()
        self.tree = BVHTree.FromPolygons(verts, polys)

    def down(self, x, y, fallback=0.0):
        p = self.tree.ray_cast(Vector((x, y, 80)), Vector((0, 0, -1)))[0]
        return p.z if p is not None else fallback

    def aft(self, y, z, fallback=0.0):
        """Hindmost X of the hull at (y, z), seen from astern."""
        p = self.tree.ray_cast(Vector((-80, y, z)), Vector((1, 0, 0)))[0]
        return p.x if p is not None else fallback

    def side(self, x, z, s, fallback=0.0):
        """Outermost |Y| of the hull wall at (x, z) on side s (+1 port)."""
        p = self.tree.ray_cast(Vector((x, s*80, z)), Vector((0, -s, 0)))[0]
        return p.y if p is not None else fallback


def _solid(kit, name, top, outer, base, mat):
    """A plate solid from three rings of equal length: inset top, full-size shoulder, base."""
    n = len(top)
    verts = top+outer+base
    faces = [tuple(range(n)), tuple(reversed(range(2*n, 3*n)))]
    for r in range(2):
        faces += [(r*n+i, r*n+(i+1) % n, (r+1)*n+(i+1) % n, (r+1)*n+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat)


def _ring(x0, x1, y0, y1, nx, ny):
    """Perimeter samples of a rectangle, counter-clockwise from (x0, y0)."""
    pts = [(x0+(x1-x0)*i/nx, y0) for i in range(nx)]
    pts += [(x1, y0+(y1-y0)*j/ny) for j in range(ny)]
    pts += [(x1-(x1-x0)*i/nx, y1) for i in range(nx)]
    pts += [(x0, y1-(y1-y0)*j/ny) for j in range(ny)]
    return pts


def plate(kit, field, name, x0, x1, y0, y1, thick, mat, chamfer=.12, step=2.0, lift=0.0):
    """An armour panel draped on the deck: its top follows the surface `thick` above it
    (plus `lift`, for a part standing on another panel)."""
    nx = max(2, math.ceil((x1-x0)/step))
    ny = max(2, math.ceil((y1-y0)/step))
    c = min(chamfer, (x1-x0)/4, (y1-y0)/4)
    top, outer, base = [], [], []
    for (xo, yo), (xi, yi) in zip(_ring(x0, x1, y0, y1, nx, ny), _ring(x0+c, x1-c, y0+c, y1-c, nx, ny)):
        top.append((xi, yi, field.down(xi, yi)+lift+thick))
        z = field.down(xo, yo)+lift
        outer.append((xo, yo, z+thick-c))
        base.append((xo, yo, z-.3))
    return _solid(kit, name, top, outer, base, mat)


def side_plate(kit, field, name, s, x0, x1, z0, z1, thick, mat, chamfer=.1, step=2.0):
    """A panel on the hull wall of side s, its face `thick` proud of the wall."""
    nx = max(2, math.ceil((x1-x0)/step))
    nz = max(2, math.ceil((z1-z0)/step))
    c = min(chamfer, (x1-x0)/4, (z1-z0)/4)
    top, outer, base = [], [], []
    for (xo, zo), (xi, zi) in zip(_ring(x0, x1, z0, z1, nx, nz), _ring(x0+c, x1-c, z0+c, z1-c, nx, nz)):
        top.append((xi, field.side(xi, zi, s)+s*thick, zi))
        y = field.side(xo, zo, s)
        outer.append((xo, y+s*(thick-c), zo))
        base.append((xo, y-s*.3, zo))
    return _solid(kit, name, top, outer, base, mat)


def aft_plate(kit, field, name, y0, y1, z0, z1, thick, mat, chamfer=.1, step=1.5):
    """A panel on an aft-facing wall, its face `thick` proud of it."""
    ny = max(2, math.ceil((y1-y0)/step))
    nz = max(2, math.ceil((z1-z0)/step))
    c = min(chamfer, (y1-y0)/4, (z1-z0)/4)
    top, outer, base = [], [], []
    for (yo, zo), (yi, zi) in zip(_ring(y0, y1, z0, z1, ny, nz), _ring(y0+c, y1-c, z0+c, z1-c, ny, nz)):
        top.append((field.aft(yi, zi)-thick, yi, zi))
        x = field.aft(yo, zo)
        outer.append((x-thick+c, yo, zo))
        base.append((x+.3, yo, zo))
    return _solid(kit, name, top, outer, base, mat)


def hatch(kit, field, name, x, y, sx, sy, base=0.0):
    """A square access hatch: a dark rim with the steel cover inside it."""
    plate(kit, field, name+' rim', x-sx/2-.25, x+sx/2+.25, y-sy/2-.25, y+sy/2+.25, .06, kit.dark, .03, lift=base)
    plate(kit, field, name, x-sx/2, x+sx/2, y-sy/2, y+sy/2, .16, kit.steel, .06, lift=base)


def grille(kit, field, name, x0, x1, y0, y1, n, along='x'):
    """A vent: a dark well with n slats across it."""
    plate(kit, field, name+' well', x0, x1, y0, y1, .05, kit.dark, .03)
    for k in range(n):
        if along == 'x':  # slats run along X, spaced across Y
            y = y0+(y1-y0)*(k+.5)/n
            plate(kit, field, f'{name} slat {k+1}', x0+.2, x1-.2, y-.12, y+.12, .2, kit.steel, .04, 4)
        else:
            x = x0+(x1-x0)*(k+.5)/n
            plate(kit, field, f'{name} slat {k+1}', x-.12, x+.12, y0+.2, y1-.2, .2, kit.steel, .04, 4)


def wedge(kit, field, name, pts, top_in, run, top_z, mat, lift=.06, depth=1.6):
    """A fairing along a plan polyline: its crest lies `top_in` to the right of the
    line, `top_z` above the host's surface there, its toe `run` to the left, seated
    on the surface there."""
    secs = []
    for i, (x, y) in enumerate(pts):
        ax, ay = pts[max(i-1, 0)]
        bx, by = pts[min(i+1, len(pts)-1)]
        dx, dy = bx-ax, by-ay
        length = math.hypot(dx, dy)
        nx, ny = -dy/length, dx/length  # left of travel
        cx, cy = x-nx*top_in, y-ny*top_in
        tx, ty = x+nx*run, y+ny*run
        zc = field.down(cx, cy)+top_z  # the crest follows the host it rises from
        zt = field.down(tx, ty)+lift
        zb = min(zc, zt)-depth
        secs.append([(cx, cy, zc), (tx, ty, zt), (tx, ty, zt-depth*.5), (cx, cy, zb)])
    n = len(secs)
    verts = [v for sec in secs for v in sec]
    faces = [(0, 1, 2, 3), tuple(4*(n-1)+i for i in (3, 2, 1, 0))]
    for s in range(n-1):
        a, b = 4*s, 4*(s+1)
        faces += [(a+i, a+(i+1) % 4, b+(i+1) % 4, b+i) for i in range(4)]
    return kit.mesh(name, verts, faces, mat, .08)


def frame(kit, name, hosts, x0, x1, grow, mat):
    """A transverse frame: the slab x0..x1 of each host, grown `grow` out of its skin."""
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    parts = []
    for host in hosts:
        me = bpy.data.meshes.new_from_object(host.evaluated_get(dg))
        me.transform(host.matrix_world)
        ob = bpy.data.objects.new(f'{name} {host.name}', me)
        bpy.context.collection.objects.link(ob)
        lo = [min(v.co[k] for v in me.vertices) for k in range(3)]
        hi = [max(v.co[k] for v in me.vertices) for k in range(3)]
        if x1 < lo[0] or x0 > hi[0]:
            bpy.data.objects.remove(ob, do_unlink=True)
            continue
        bpy.ops.mesh.primitive_cube_add(size=1, location=((x0+x1)/2, (lo[1]+hi[1])/2, (lo[2]+hi[2])/2))
        slab = bpy.context.object
        slab.name = f'Temporary {name} slab'
        slab.scale = (x1-x0, hi[1]-lo[1]+2, hi[2]-lo[2]+2)
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        boolean(ob, slab, 'INTERSECT')
        if not ob.data.polygons:
            bpy.data.objects.remove(ob, do_unlink=True)
            continue
        _grow_sideways(ob.data, grow)
        ob.data.materials.clear()
        ob.data.materials.append(mat)
        ob.data.name = ob.name
        parts.append(ob)
    return parts


def _grow_sideways(me, grow):
    """Push the skin of a slab outward by `grow`, leaving its two cut faces in place."""
    bm = bmesh.new()
    bm.from_mesh(me)
    bm.normal_update()
    moves = []
    for v in bm.verts:
        normals = [f.normal for f in v.link_faces if abs(f.normal.x) < .9]
        if not normals:
            moves.append(None)
            continue
        n = Vector((0.0, 0.0, 0.0))
        for f in normals:
            n += f
        n.x = 0
        if n.length < 1e-6:
            moves.append(None)
            continue
        n.normalize()
        d = min(max(n.dot(Vector((0, f.y, f.z)).normalized()), .5) for f in normals if Vector((0, f.y, f.z)).length > 1e-6)
        moves.append(n*grow/d)
    for v, m in zip(bm.verts, moves):
        if m is not None:
            v.co += m
    bm.to_mesh(me)
    bm.free()
    me.update()


def pipe(kit, name, x0, x1, y, z, r, brackets=()):
    kit.cylinder(name, ((x0+x1)/2, y, z), r, x1-x0, kit.steel, 'X', 8, 0)
    for k, x in enumerate(brackets, 1):
        kit.cylinder(f'{name} bracket {k}', (x, y, z), r+.14, .35, kit.dark, 'X', 8, 0)


def build(kit):
    O = bpy.data.objects
    spine, wing, head, nose = O['Hull spine'], O['Hull wing body'], O['Hull bow head'], O['Hull nose cap']
    field = Field(kit.hull+[O['Hull ventral keel']])
    sides = (('port', 1), ('starboard', -1))
    gun = {sd: O[f'Pod {sd} gun body'] for sd, _ in sides}
    mis = {sd: O[f'Pod {sd} missile body'] for sd, _ in sides}
    nac = {sd: O[f'Hull {sd} engine nacelle'] for sd, _ in sides}
    fair = {}

    for sd, s in sides:
        # Gun pod root fairing: the spine's plating carried over the groove onto the pod.
        fair[sd] = wedge(kit, field, f'Hull {sd} gun pod root fairing',
                         [(x, s*9.5) for x in ((27.4, 17.4) if s > 0 else (17.4, 27.4))],
                         1.7, 2.2, .14, kit.orange, lift=.04)
        # Gun pod aft fairing: the pod's aft wall ramped down onto the wing.
        ys = [s*y for y in (10.4, 13, 16, 19, 20.6)]
        wedge(kit, field, f'Hull {sd} gun pod aft fairing', [(17.0, y) for y in (ys if s > 0 else ys[::-1])],
              .7, 2.2, -.12, kit.orange)
        # Missile pod aft glacis onto the wing (it laps onto the armour strake).
        ys = [s*y for y in (20.9, 24, 27, 30, 31.9)]
        wedge(kit, field, f'Hull {sd} missile pod aft fairing', [(5.0, y) for y in (ys if s > 0 else ys[::-1])],
              .7, 2.5, -.12, kit.orange)
        # Missile pod inboard fillet down to the wing.
        xs = (13.4, 10, 7, 5.4)
        wedge(kit, field, f'Hull {sd} missile pod inboard fairing',
              [(x, s*20.5) for x in (xs if s > 0 else xs[::-1])], .6, 1.6, -.12, kit.orange)
        # Nacelle forward glacis: the drive block faired into the wing ahead of it.
        edge = [(-6.0, 9.0), (-6.25, 12.5), (-6.5, 16), (-6.8, 19.6)]  # the inner edge; the strake covers the rest
        edge = [(x, s*y) for x, y in edge]
        wedge(kit, field, f'Hull {sd} nacelle forward fairing', edge if s < 0 else edge[::-1],
              1.9, 2.0, -.05, kit.orange)
        # Spine-nacelle groove: a saddle with a conduit run along it.
        groove = [(x, s*9.0) for x in ((-8, -26) if s > 0 else (-26, -8))]
        wedge(kit, field, f'Hull {sd} spine nacelle saddle', groove, 1.4, 2.2, .15, kit.steel, lift=.05)
        zc, zt = field.down(-16, s*10.4)+.15, field.down(-16, s*6.8)+.05  # the saddle's crest and toe
        for k, y in enumerate((8.3, 9.2), 1):  # riding the saddle's slope
            z = zc+(10.4-y)/3.6*(zt-zc)+.26
            pipe(kit, f'Hull {sd} conduit {k}', -25.5, -8.5, s*y, z, .32, (-11.5, -16.5, -21.5))
        # Bridge frame tying the gun pod to the missile pod across the wing's leading edge.
        z0 = field.down(15.6, s*20.4)
        kit.box(f'Hull {sd} pod bridge frame', (15.6, s*20.4, z0-2.6), (3.6, 2.2, 5.6), kit.steel, .2)
        # Buttresses from the spine wall down to the wing.
        for k, x in enumerate((14.2, 11.0, -1.8), 1):
            verts = [(x-.3, s*3.4, 7.1), (x-.3, s*3.4, 4.6), (x-.3, s*6.6, 4.6), (x+.3, s*3.4, 7.1),
                     (x+.3, s*3.4, 4.6), (x+.3, s*6.6, 4.6)]
            kit.mesh(f'Hull spine {sd} buttress {k}', verts, [(0, 1, 2), (3, 5, 4), (0, 3, 4, 1), (1, 4, 5, 2),
                                                             (2, 5, 3, 0)], kit.steel, .05)
        kit.box(f'Hull spine {sd} wall rail', (5.5, s*4.62, 6.4), (17.5, .4, .45), kit.steel, .08)

    # Barbette collars: the turrets seated in the armour rather than set on it.
    for i, (x, y) in enumerate(((4, 10), (4, -10), (37, 0)), 1):
        z = field.down(x, y)
        kit.cylinder(f'Hull turret {i} barbette collar', (x, y, z-.3), 5.3, 1.2, kit.orange, vertices=16, bevel=.2)

    # Frames: one rib per station across everything that stands there.
    every = [spine, wing, head, nose, *gun.values(), *mis.values(), *nac.values(), *fair.values()]
    for k, (x0, x1, hosts) in enumerate((
            (48.8, 49.7, [nose, head]),
            (53.3, 54.0, [nose]),
            (28.3, 29.1, [spine]),
            (17.7, 18.5, every),
            (23.1, 23.9, every),
            (5.5, 6.2, list(mis.values())),
            (-3.6, -2.7, [spine, wing]),
            (-9.6, -8.8, [spine, *nac.values()]),
            (-23.7, -22.9, [spine, *nac.values()])), 1):
        frame(kit, f'Hull frame {k}', hosts, x0, x1, .16, kit.steel)

    # Armour panels on the wing (gaps are the seams), clear of turrets, pods and nacelles.
    for sd, s in sides:
        for k, (x0, x1, y0, y1) in enumerate(((10.4, 14.4, 5.0, 11.7), (10.4, 14.4, 12.3, 19.6),
                                              (6.6, 9.9, 15.9, 19.6), (.4, 6.1, 16.1, 19.6),
                                              (-2.4, -.4, 15.0, 19.6)), 1):
            ya, yb = sorted((s*y0, s*y1))
            plate(kit, field, f'Hull wing {sd} panel {k}', x0, x1, ya, yb, .22, kit.orange, .16)
        hatch(kit, field, f'Hull wing {sd} hatch 1', 12.6, s*8.2, 1.8, 1.8, .22)
        hatch(kit, field, f'Hull wing {sd} hatch 2', 8.2, s*17.7, 1.4, 1.4, .22)
        # Head walls, nose rake, pod and nacelle walls.
        for k, (x0, x1) in enumerate(((34.6, 41.2), (41.8, 48.2)), 1):
            side_plate(kit, field, f'Hull bow head {sd} side panel {k}', s, x0, x1, -2.6, 3.2, .12, kit.orange)
        side_plate(kit, field, f'Hull bow head {sd} belt', s, 33.5, 48.6, -3.6, -3.0, .22, kit.steel)
        ya, yb = sorted((s*.7, s*5.6))
        plate(kit, field, f'Hull nose {sd} rake panel', 50.4, 52.9, ya, yb, .12, kit.steel, step=1.0)
        side_plate(kit, field, f'Hull nose {sd} cheek panel', s, 50.6, 54.6, -2.4, 2.0, .14, kit.steel, step=1.0)
        # Nacelle outer shoulders: plates and a grille on the slope.
        for k, (x0, x1) in enumerate(((-20.3, -15.6), (-14.9, -10.2)), 1):
            ya, yb = sorted((s*21.8, s*24.4))
            plate(kit, field, f'Hull {sd} nacelle shoulder panel {k}', x0, x1, ya, yb, .18, kit.steel, step=1.0)
        ya, yb = sorted((s*8.9, s*11.0))
        aft_plate(kit, field, f'Hull {sd} nacelle aft panel', ya, yb, -3.6, .6, .18, kit.steel)
        side_plate(kit, field, f'Hull {sd} nacelle belt', s, -27.0, -20.6, -2.7, -2.0, .2, kit.steel, .05, 1.0)
        for k, x in enumerate((7.6, 10.6), 1):
            side_plate(kit, field, f'Pod {sd} missile side rib {k}', s, x-.3, x+.3, -2.8, 4.3, .22, kit.steel, .05)
        for k, (x0, x1) in enumerate(((17.8, 22.7), (24.1, 27.2)), 1):
            side_plate(kit, field, f'Pod {sd} gun side panel {k}', s, x0, x1, -2.2, 4.0, .12, kit.steel)
        side_plate(kit, field, f'Pod {sd} missile side panel', s, 6.6, 12.8, -2.0, 3.6, .12, kit.steel)
        # Hatches and grilles on the pods, spine and nacelles.
        hatch(kit, field, f'Pod {sd} gun hatch', 26.8, s*15.0, 1.5, 1.5)
        hatch(kit, field, f'Hull spine {sd} hatch 1', 20.8, s*3.6, 1.6, 1.6)
        hatch(kit, field, f'Hull spine {sd} hatch 2', -7.0, s*3.1, 1.6, 1.6)
        ya, yb = sorted((s*10.6, s*16.4))
        grille(kit, field, f'Hull {sd} nacelle aft grille', -27.4, -24.6, ya, yb, 5, along='y')
        ya, yb = sorted((s*22.6, s*28.8))
        grille(kit, field, f'Pod {sd} missile grille', 11.2, 13.2, ya, yb, 4)
        ya, yb = sorted((s*14.6, s*18.4))
        for k, x in enumerate((-20.5, -9.0), 1):
            plate(kit, field, f'Hull {sd} nacelle cooling fin {k}', x-.25, x+.25, ya, yb, .7, kit.steel, .05)

    # Drive bezels round the bells on the aft faces, and the main drive's collar.
    import ls_engines
    for i, (x, y, w, _) in enumerate(ls_engines.ENGINES[1:], 2):
        r = ls_engines.mouth_r(w)+ls_engines.WALL*w+.05
        xf = field.aft(y, ls_engines.Z+r+.3, x)
        revolve_x(kit, f'Hull drive {i} bezel', [(xf+.5, r), (xf-.3, r), (xf-.3, r+.4), (xf+.5, r+.5)],
                  y, ls_engines.Z, kit.steel, 12)
    # The main bell stands out of the spine's tail: a collar where it enters.
    revolve_x(kit, 'Hull main drive collar', [(-31.9, 5.2), (-33.0, 5.2), (-33.0, 6.2), (-31.9, 6.4)],
              0, ls_engines.Z, kit.steel, 16)
