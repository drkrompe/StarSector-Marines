"""Shared Blender helpers for the fighter blockouts (fighters-geometry).

A standalone copy of the Broadsword kit (itself copied from the Hound):
materials, primitives, chamfered prisms, lofts, booleans, a downward
ray-cast, station tables interpolated along X and armour strips, plus the
engine unit and slot anchors every fighter shares so their names agree.
It imports no other hull package. Coordinates are hull-local, +X nose,
+Y port, +Z dorsal, one sprite pixel per unit about the .ship centre.
"""
import math
import bmesh
import bpy
from mathutils import Matrix, Vector


def material(name, color, metallic=0.0, roughness=.65, emission=None, strength=2.0):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*color, 1)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get('Principled BSDF')
    bsdf.inputs['Base Color'].default_value = (*color, 1)
    bsdf.inputs['Metallic'].default_value = metallic
    bsdf.inputs['Roughness'].default_value = roughness
    if emission:
        bsdf.inputs['Emission Color'].default_value = (*emission, 1)
        bsdf.inputs['Emission Strength'].default_value = strength
    return m


class Kit:
    def __init__(self, armour=(.47, .40, .24), armour_name='Armour', trim=None, glass=(.30, .62, .70)):
        # One armour colour per fighter, read off its sprite; the rest is the
        # shared low-detail palette of a blockout.
        self.armour = material(armour_name, armour, .15, .6)
        self.khaki = self.armour  # Broadsword-kit alias
        self.trim = material('Trim', trim, .2, .55) if trim else self.armour
        self.steel = material('Gunmetal structure', (.11, .115, .12), .6, .5)
        self.bright = material('Worn bright steel', (.42, .42, .42), .85, .35)
        self.dark = material('Recess and machinery', (.025, .027, .03), .4, .6)
        self.glass = material('Canopy glass', glass, .0, .08)
        self.detail = False
        self.m = 1.0    # metres per unit: the driver sets the fighter's own
        self.hull = []  # objects the downward ray-cast seats parts on
        self.anchors = []

    def material(self, name, color, metallic=.15, roughness=.6):
        return material(name, color, metallic, roughness)

    def engine(self, i, x_mouth, y, z, lip_r, bell_r=None, depth=1.2, lip_len=.5, sides=24):
        """One exhaust, named as on the Second Wind: Engine {i} mouth lip,
        aft-facing bell and dark throat, plus Engine {i} <side> exhaust
        anchor at the mouth plane (+X aft). The bell opens aft (-X) at x_mouth
        and runs forward `depth` into whatever drive unit the module built;
        cut that unit's well first (kit.well) so the bell is visible."""
        side = 'port' if y > 1e-6 else ('starboard' if y < -1e-6 else 'centre')
        bell_r = bell_r or lip_r*.82
        lip = self.cylinder(f'Engine {i} mouth lip', (x_mouth+lip_len/2, y, z), lip_r, lip_len, self.bright, 'X', sides, .03)
        boolean(lip, self.cylinder('Temporary bell well', (x_mouth, y, z), bell_r+.05, lip_len*3, self.dark, 'X', sides, 0))
        _cone(self, f'Engine {i} aft-facing bell', x_mouth+depth, x_mouth+.03, bell_r*.62, bell_r, y, z, self.dark, sides)
        self.cylinder(f'Engine {i} dark throat', (x_mouth+depth-.05, y, z), bell_r*.66, .08, self.dark, 'X', sides, 0)
        return self.empty(f'Engine {i} {side} exhaust anchor', (x_mouth, y, z), 180)

    def well(self, target, x_mouth, y, z, bell_r, depth=1.2, sides=24):
        """Cut the bell's well into a drive unit before calling engine()."""
        boolean(target, self.cylinder('Temporary drive well', (x_mouth, y, z), bell_r+.06, 2*(depth+.1), self.dark, 'X', sides, 0))

    def slot_anchor(self, slot, loc, muzzle=None):
        """Weapon {id} anchor at loc (x, y, z), +X along the slot angle; optional Weapon {id} muzzle."""
        a = self.empty(f"Weapon {slot['id']} anchor", loc, slot['angle'])
        if muzzle:
            self.empty(f"Weapon {slot['id']} muzzle", muzzle, slot['angle'], 1.0)
        return a

    def finish(self, obj, name, mat, bevel=0):
        obj.name = name
        obj.data.name = name
        obj.data.materials.append(mat)
        if bevel:
            b = obj.modifiers.new('Plate edge chamfer', 'BEVEL')
            b.width, b.segments, b.limit_method = bevel, 1, 'ANGLE'
        return obj

    def box(self, name, loc, dims, mat, bevel=.06, rot=None):
        bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
        obj = bpy.context.object
        obj.dimensions = dims
        if rot:
            obj.rotation_euler = rot
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        return self.finish(obj, name, mat, bevel)

    def cylinder(self, name, loc, radius, depth, mat, axis='Z', vertices=16, bevel=.04):
        bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=depth, location=loc)
        obj = bpy.context.object
        if axis == 'X':
            obj.rotation_euler[1] = math.pi / 2
        elif axis == 'Y':
            obj.rotation_euler[0] = math.pi / 2
        return self.finish(obj, name, mat, bevel)

    def mesh(self, name, verts, faces, mat, bevel=0):
        """Flat-shaded polygon solid with outward normals."""
        me = bpy.data.meshes.new(name)
        me.from_pydata(verts, [], faces)
        bm = bmesh.new()
        bm.from_mesh(me)
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        bm.to_mesh(me)
        bm.free()
        me.update()
        ob = bpy.data.objects.new(name, me)
        bpy.context.collection.objects.link(ob)
        return self.finish(ob, name, mat, bevel)

    def empty(self, name, loc, angle_deg, size=1.5):
        """An arrows anchor: its +X points along the slot angle (0 = nose, 180 = aft)."""
        ob = bpy.data.objects.new(name, None)
        ob.empty_display_type = 'ARROWS'
        ob.empty_display_size = size
        ob.location = loc
        ob.rotation_euler = (0, 0, math.radians(angle_deg))
        bpy.context.collection.objects.link(ob)
        self.anchors.append(ob)
        return ob

    def surface(self, x, y, top=40):
        """Highest (point, normal) where a ray dropped at (x, y) meets the hull parts."""
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        best = None
        for ob in self.hull:
            ev = ob.evaluated_get(dg)
            inv = ev.matrix_world.inverted()
            o = inv @ Vector((x, y, top))
            d = (inv.to_3x3() @ Vector((0, 0, -1))).normalized()
            hit, p, n, _ = ev.ray_cast(o, d)
            if hit:
                p = ev.matrix_world @ p
                if best is None or p.z > best[0].z:
                    best = (p, (ev.matrix_world.to_3x3() @ n).normalized())
        return best

    def seat_z(self, x, y):
        hit = self.surface(x, y)
        if hit is None:
            raise RuntimeError(f'nothing to seat on at ({x}, {y})')
        return hit[0].z

    def bolt(self, name, x, y, r=.13):
        """A bolt head seated on whatever hull part is highest at (x, y)."""
        p, n = self.surface(x, y)
        ob = self.cylinder(name, p+n*.03, r, .12, self.bright, 'Z', 8, 0)
        ob.rotation_euler = n.to_track_quat('Z', 'Y').to_euler()
        return ob

    # --- Pass 2: plausible detail. ---
    # Positions stay in hull units; sizes are given in metres and go through
    # u(), so a panel line or a grille slat is the same real size on every
    # fighter whatever its metres_per_unit. Engraving and skin layers work
    # from offsets of a part's own finished surface, so they follow chamfers
    # and lofts without tracing them: a panel line is a constant-depth
    # groove and a skin plate a constant-thickness layer wherever the slab
    # that bounds it lands on the part.

    def u(self, metres):
        return metres/self.m

    def mark_detail(self):
        """Called first thing in a module's detail(): everything built from here on is pass-2 detail
        (the driver tags it, and check_clashes.py reads the tag)."""
        self.pass1 = {o.name for o in bpy.data.objects}

    def offset_copy(self, ob, d, name, mat=None):
        """A copy of ob's finished surface moved d units along its normals (negative: shrunk)."""
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        me = bpy.data.meshes.new_from_object(ob.evaluated_get(dg))
        bm = bmesh.new()
        bm.from_mesh(me)
        bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
        bm.normal_update()
        moves = [(v, v.normal*d*min(v.calc_shell_factor(), 2.0)) for v in bm.verts]
        for v, m in moves:
            v.co += m
        bm.to_mesh(me)
        bm.free()
        me.materials.clear()
        me.materials.append(mat or self.dark)
        for poly in me.polygons:
            poly.material_index = 0
        me.name = name
        out = bpy.data.objects.new(name, me)
        bpy.context.collection.objects.link(out)
        out.matrix_world = ob.matrix_world.copy()
        return out

    def slab(self, name, pts, z0, z1, mat=None, axis='Z'):
        """A prism over a convex polygon pts, extruded z0..z1 along axis
        ('Z': pts in XY; 'Y': pts in XZ; 'X': pts in YZ)."""
        pts = ccw(list(pts))
        n = len(pts)
        place = {'Z': lambda a, b, c: (a, b, c), 'Y': lambda a, b, c: (a, c, b), 'X': lambda a, b, c: (c, a, b)}[axis]
        verts = [place(a, b, z) for z in (z0, z1) for a, b in pts]
        faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))]
        faces += [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
        return self.mesh(name, verts, faces, mat or self.dark, 0)

    def line_slab(self, p0, p1, w_m, z0=-60, z1=60, axis='Z'):
        """A slab w_m wide along the straight line p0 -> p1, in the plan of `axis`."""
        (ax, ay), (bx, by) = p0, p1
        L = math.hypot(bx-ax, by-ay)
        h = self.u(w_m)/2
        nx, ny = -(by-ay)/L*h, (bx-ax)/L*h
        return self.slab('Temporary line', [(ax+nx, ay+ny), (ax-nx, ay-ny), (bx-nx, by-ny), (bx+nx, by+ny)],
                         z0, z1, axis=axis)

    def lines(self, pts, w_m, z0=-60, z1=60, closed=False, axis='Z'):
        """Line slabs along a polyline, each run on by half a width so the corners meet."""
        pairs = list(zip(pts, pts[1:])) + ([(pts[-1], pts[0])] if closed else [])
        out = []
        for (ax, ay), (bx, by) in pairs:
            e = self.u(w_m)/2/math.hypot(bx-ax, by-ay)
            out.append(self.line_slab((ax-(bx-ax)*e, ay-(by-ay)*e), (bx+(bx-ax)*e, by+(by-ay)*e), w_m, z0, z1, axis))
        return out

    def outline(self, cx, cy, L_m, W_m, w_m=.04, z0=-60, z1=60, axis='Z', corner_m=None):
        """A rectangular hatch outline with cut corners, centred on (cx, cy) in the plan of `axis`."""
        L, W = self.u(L_m)/2, self.u(W_m)/2
        c = self.u(corner_m) if corner_m is not None else min(L, W)*.3
        pts = [(cx+L-c, cy+W), (cx-L+c, cy+W), (cx-L, cy+W-c), (cx-L, cy-W+c), (cx-L+c, cy-W),
               (cx+L-c, cy-W), (cx+L, cy-W+c), (cx+L, cy+W-c)]
        return self.lines(pts, w_m, z0, z1, closed=True, axis=axis)

    def band_slab(self, x, w_m, y0=-60, y1=60, z0=-60, z1=60):
        """An axis-aligned slab w_m long in X at station x: a ring groove or a collar rib."""
        h = self.u(w_m)/2
        return self.slab('Temporary band', [(x-h, y0), (x+h, y0), (x+h, y1), (x-h, y1)], z0, z1)

    def region(self, x0, x1, y0, y1, z0=-60, z1=60):
        return self.slab('Temporary region', [(x0, y0), (x1, y0), (x1, y1), (x0, y1)], z0, z1)

    def engrave(self, target, slabs, depth_m=.03):
        """Panel lines: cut every slab's footprint depth_m into target's skin and no deeper.
        The groove walls and floor take the slabs' dark material."""
        slabs = list(slabs) if isinstance(slabs, (list, tuple)) else [slabs]
        if not slabs:
            return target
        bake(target)
        cutter = slabs[0]
        for s in slabs[1:]:
            boolean(cutter, s, 'UNION')
        boolean(cutter, self.offset_copy(target, -self.u(depth_m), 'Temporary core'))
        boolean(target, cutter)
        return target

    def skin(self, target, name, region, t_m, mat, chamfer=True):
        """A layer t_m thick over target's surface wherever the region solid covers it: a raised
        armour plate, a collar rib round a drum, a canopy frame round the glass."""
        shell = self.offset_copy(target, self.u(t_m), name, mat)
        boolean(shell, region, 'INTERSECT')
        boolean(shell, self.offset_copy(target, -self.u(.002), 'Temporary inner'))
        if chamfer:
            b = shell.modifiers.new('Plate edge chamfer', 'BEVEL')
            b.width, b.segments, b.limit_method = min(self.u(t_m)*.35, .04), 1, 'ANGLE'
        return shell

    def frame_at(self, x, y, yaw_deg=0.0, lift=0.0):
        """World matrix on the hull under (x, y): +Z along the surface normal, +X toward yaw (0 = nose)."""
        hit = self.surface(x, y)
        if hit is None:
            raise RuntimeError(f'nothing to seat on at ({x}, {y})')
        p, n = hit
        a = math.radians(yaw_deg)
        xa = Vector((math.cos(a), math.sin(a), 0))
        xa = (xa-n*xa.dot(n)).normalized()
        rot = Matrix((xa, n.cross(xa), n)).transposed().to_4x4()
        return Matrix.Translation(p+n*lift) @ rot

    def seated(self, name, x, y, dims_m, mat, yaw_deg=0.0, sink_m=.01, bevel_m=.01):
        """A box laid on the hull at (x, y), tilted to the surface and bedded sink_m into it."""
        L, W, T = (self.u(v) for v in dims_m)
        ob = self.box(name, (0, 0, 0), (L, W, T), mat, self.u(bevel_m) if bevel_m else 0)
        ob.matrix_world = self.frame_at(x, y, yaw_deg, T/2-self.u(sink_m))
        return ob

    def grille(self, name, x, y, L_m, W_m, slats, yaw_deg=0.0, slat_mat=None):
        """A dark grille well with slats across it, laid on the hull: intakes and vents."""
        well = self.seated(f'{name} well', x, y, (L_m, W_m, .04), self.dark, yaw_deg, .025, .008)
        pitch = L_m/(slats+.5)
        a = math.radians(yaw_deg)
        for k in range(slats):
            off = self.u(-L_m/2+pitch*(k+.75))
            self.seated(f'{name} slat {k+1}', x+math.cos(a)*off, y+math.sin(a)*off,
                        (pitch*.42, W_m*.84, .06), slat_mat or self.steel, yaw_deg, .015, .006)
        return well

    def fairing(self, name, x, y, L_m, W_m, H_m, mat, yaw_deg=0.0, nose=.35, tail=.3):
        """A faceted blister (sensor or antenna fairing) laid on the hull, long axis toward yaw."""
        L, W, H = self.u(L_m), self.u(W_m), self.u(H_m)
        sink = self.u(.04)
        rows = [(L/2, .3, .35), (L/2-L*nose, 1, 1), (-L/2+L*tail, 1, 1), (-L/2, .45, .5)]
        st = [(px, octagon(-W/2*k, W/2*k, -sink, H*h, min(W/2*k, H*h)*.55, 0)) for px, k, h in rows]
        ob = loft_x(self, name, st, mat, self.u(.01))
        ob.matrix_world = self.frame_at(x, y, yaw_deg)
        return ob

    def blade(self, name, x, y, H_m, L_m, mat, yaw_deg=0.0, t_m=.03, sweep=.5):
        """A swept blade antenna standing on the hull."""
        H, L, t, sink = self.u(H_m), self.u(L_m), self.u(t_m), self.u(.03)
        plan = [(-L/2, -sink), (L/2, -sink), (L/2-L*sweep*.4, H), (L/2-L*sweep*.4-L*.35, H)]
        ob = self.slab(name, plan, -t/2, t/2, mat, axis='Y')
        ob.matrix_world = self.frame_at(x, y, yaw_deg)
        return ob

    def xcyl(self, name, x0, x1, y, z, r_m, mat, sides=16, bevel_m=.008):
        """A cylinder along X from x0 to x1, radius in metres."""
        return self.cylinder(name, ((x0+x1)/2, y, z), self.u(r_m), abs(x1-x0), mat, 'X', sides, self.u(bevel_m))

    def rod(self, name, p0, p1, r_m, mat, sides=10):
        """A round rod between two points: struts, actuators, hinge pins."""
        a, b = Vector(p0), Vector(p1)
        ob = self.cylinder(name, (0, 0, 0), self.u(r_m), (b-a).length, mat, 'Z', sides, 0)
        ob.matrix_world = Matrix.Translation((a+b)/2) @ (b-a).to_track_quat('Z', 'Y').to_matrix().to_4x4()
        return ob

    def drive_core(self, i, x_mouth, y, z, bell_r, depth=1.2, vanes=8):
        """Inside the bell: a centre body on the throat and a ring of stator vanes round it."""
        r = bell_r*.62
        xt = x_mouth+depth-.08
        st = [(xt, octagon(y-r*.42, y+r*.42, z-r*.42, z+r*.42, r*.12)),
              (xt-depth*.45, octagon(y-r*.3, y+r*.3, z-r*.3, z+r*.3, r*.09)),
              (xt-depth*.7, octagon(y-r*.06, y+r*.06, z-r*.06, z+r*.06, r*.02))]
        loft_x(self, f'Engine {i} centre body', st, self.steel, .02)
        for k in range(vanes):
            a = 2*math.pi*(k+.5)/vanes
            p0 = (xt-.04, y+math.cos(a)*r*.38, z+math.sin(a)*r*.38)
            p1 = (xt-.04, y+math.cos(a)*r*1.02, z+math.sin(a)*r*1.02)
            self.rod(f'Engine {i} stator vane {k+1}', p0, p1, .035, self.steel, 4)


# --- Planform prisms: one keel chamfer, one wall, one shoulder chamfer. ---

def _left(x, y):
    length = (x*x+y*y)**.5
    return (-y/length, x/length)


def ccw(poly):
    area = sum(poly[i-1][0]*poly[i][1]-poly[i][0]*poly[i-1][1] for i in range(len(poly)))
    return poly if area > 0 else list(reversed(poly))


def inset(poly, d):
    """Mitred inward offset of a CCW polygon."""
    out = []
    n = len(poly)
    for i in range(n):
        (ax, ay), (bx, by), (cx, cy) = poly[i-1], poly[i], poly[(i+1) % n]
        n0 = _left(bx-ax, by-ay)
        n1 = _left(cx-bx, cy-by)
        k = d/(1+n0[0]*n1[0]+n0[1]*n1[1])
        out.append((bx+(n0[0]+n1[0])*k, by+(n0[1]+n1[1])*k))
    return out


def section(keel, deck, keel_c, shoulder_c):
    """Profile (inset, z) keel to deck: keel chamfer, vertical wall, shoulder chamfer."""
    return [(keel_c, keel), (0, keel+keel_c), (0, deck-shoulder_c), (shoulder_c, deck)]


def prism(kit, name, plan, profile, mat, bevel=.06):
    plan = ccw(plan)
    n = len(plan)
    verts = [(x, y, z) for d, z in profile for x, y in inset(plan, d)]
    faces = [tuple(reversed(range(n)))]
    for r in range(len(profile)-1):
        faces += [(r*n+i, r*n+(i+1) % n, (r+1)*n+(i+1) % n, (r+1)*n+i) for i in range(n)]
    top = (len(profile)-1)*n
    faces.append(tuple(range(top, top+n)))
    return kit.mesh(name, verts, faces, mat, bevel)


def mirror_y(plan):
    """A port-side plan reflected to starboard."""
    return [(x, -y) for x, y in reversed(plan)]


def loft_x(kit, name, stations, mat, bevel=.06):
    """Solid lofted along X between equal-count YZ sections [(x, [(y, z), ...]), ...]."""
    n = len(stations[0][1])
    verts = [(x, y, z) for x, sec in stations for y, z in sec]
    faces = [tuple(range(n)), tuple(range((len(stations)-1)*n, len(stations)*n))]
    for s in range(len(stations)-1):
        faces += [(s*n+i, s*n+(i+1) % n, (s+1)*n+(i+1) % n, (s+1)*n+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat, bevel)


def octagon(y0, y1, z0, z1, ct, cb=None):
    """Chamfered rectangle in YZ: top corners cut by ct, bottom by cb."""
    cb = ct if cb is None else cb
    return [(y0, z0+cb), (y0+cb, z0), (y1-cb, z0), (y1, z0+cb),
            (y1, z1-ct), (y1-ct, z1), (y0+ct, z1), (y0, z1-ct)]


# --- Station tables: (x, half width, keel z, deck z, top chamfer, keel chamfer). ---

def at(table, x):
    """Linear interpolation of a station table (sorted nose to tail) at x."""
    rows = sorted(table, key=lambda r: -r[0])
    if x >= rows[0][0]:
        return rows[0]
    for a, b in zip(rows, rows[1:]):
        if b[0] <= x <= a[0]:
            t = (a[0]-x)/(a[0]-b[0]) if a[0] != b[0] else 0
            return tuple(av+(bv-av)*t for av, bv in zip(a, b))
    return rows[-1]


def section_loft(kit, name, table, mat, cy=0.0, bevel=.06, grow=0.0, xs=None):
    """Loft an octagonal body through a station table, centred on Y = cy."""
    xs = xs or [r[0] for r in table]
    st = []
    for x in xs:
        _, w, z0, z1, ct, cb = at(table, x)
        st.append((x, octagon(cy-w-grow, cy+w+grow, z0-grow, z1+grow, ct, cb)))
    return loft_x(kit, name, st, mat, bevel)


def half_profile(w, z0, z1, ct, cb, cy=0.0, side=1):
    """One side of an octagon section from the top centre line down to the keel centre line."""
    pts = [(0, z1), (w-ct, z1), (w, z1-ct), (w, z0+cb), (w-cb, z0), (0, z0)]
    return [(cy+side*y, z) for y, z in pts]


def _walk(pts, s):
    acc = 0.0
    for (ay, az), (by, bz) in zip(pts, pts[1:]):
        seg = math.hypot(by-ay, bz-az)
        if acc+seg >= s or (by, bz) == pts[-1]:
            t = 0 if seg == 0 else min(1, max(0, (s-acc)/seg))
            ny, nz = -(bz-az)/seg, (by-ay)/seg  # outward: the walk runs across the top, then down
            return (ay+(by-ay)*t, az+(bz-az)*t), (ny, nz)
        acc += seg
    return pts[-1], (0, -1)


def corner_s(w, ct, k):
    """Arc length from the top centre line to corner k of half_profile (1: shoulder start, 2: wall top)."""
    return [0, w-ct, w-ct+ct*2**.5, None][k]


def strip(kit, name, table, x0, x1, s0, s1, mat, t=.14, cy=0.0, side=1, n=9, bevel=.04, stations=4):
    """An armour plate of thickness t laid on a section loft between arc lengths s0..s1.
    s0/s1 may be callables of (w, ct) so the plate follows a narrowing body."""
    st = []
    for i in range(stations):
        x = x0+(x1-x0)*i/(stations-1)
        _, w, z0, z1, ct, cb = at(table, x)
        prof = half_profile(w, z0, z1, ct, cb)
        a = s0(w, ct) if callable(s0) else s0
        b = s1(w, ct) if callable(s1) else s1
        inner, outer = [], []
        for j in range(n):
            (py, pz), (ny, nz) = _walk(prof, a+(b-a)*j/(n-1))
            inner.append((py-ny*.05, pz-nz*.05))
            outer.append((py+ny*t, pz+nz*t))
        sec = [(cy+side*y, z) for y, z in inner+list(reversed(outer))]
        st.append((x, sec))
    return loft_x(kit, name, st, mat, bevel)


def _cone(kit, name, x0, x1, r0, r1, cy, z, mat, n=24):
    """Open-ended cone shell along X from x0 (radius r0) to x1 (radius r1), wall .1."""
    verts, faces = [], []
    for x, r in ((x0, r0), (x1, r1), (x1, r1+.1), (x0, r0+.1)):
        verts += [(x, cy+r*math.cos(2*math.pi*k/n), z+r*math.sin(2*math.pi*k/n)) for k in range(n)]
    for ring in range(4):
        a, b = ring*n, ((ring+1) % 4)*n
        faces += [(a+k, a+(k+1) % n, b+(k+1) % n, b+k) for k in range(n)]
    return kit.mesh(name, verts, faces, mat, 0)


def bake(ob):
    """Apply every modifier, so a later cut or offset sees the finished (chamfered) surface."""
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    me = bpy.data.meshes.new_from_object(ob.evaluated_get(dg))
    old = ob.data
    ob.modifiers.clear()
    ob.data = me
    me.name = ob.name
    if old.users == 0:
        bpy.data.meshes.remove(old)
    return ob


def boolean(target, cutter, op='DIFFERENCE', keep=False):
    bpy.context.view_layer.objects.active = target
    mod = target.modifiers.new('Hull boolean', 'BOOLEAN')
    mod.operation = op
    mod.object = cutter
    mod.solver = 'MANIFOLD'
    bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)
    bpy.ops.object.modifier_apply(modifier=mod.name)
    if not keep:
        bpy.data.objects.remove(cutter, do_unlink=True)
