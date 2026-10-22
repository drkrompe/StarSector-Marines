"""Hulks derived from accepted cinematic hull builds (Blender): an old, stripped, long-dead wreck of
a recognisable class, at that class's own settled scale.

From the build's appendable collection:
1. **Stripped**: turrets, weapons, launchers, drive bells, lamps, anchors and effects go; so does
   a share of the loose fittings, armour plates and (on a freighter) cargo modules, the way a
   salvage crew would have left her. What remains is joined into one mesh in metres.
2. **Recoloured**: every face keeps its hull's own painted colour as `wk_base` (read off its
   material, or the base material a skin was made from) and goes to one of the kit's four
   materials by what it was: plating, structure, interior (glass, seams, recesses, throats) or
   bare metal. The kit's materials bleach and pit it; nothing glows.
3. **Broken**: torn off at a station along her length. The geometry near the tear is cut into a
   fine grid first, so the tear follows a ragged noise surface rather than whole faces. Ring
   frames are set just inside the skin behind the tear and stringers run on past it, bent: the
   structure shows where the hull ends.
4. **Holed**: plating torn out of her sides in ragged patches, with the frames behind each hole.

Damage follows the project's conventions (second-wind-pipeline.md, derelict): torn and missing
plates over dark backing, exposed frames, no glow, no fire: these hulls died long ago."""
import json
import math
import random
import re
from pathlib import Path

import bmesh
import bpy
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree

import wk_catalogue as C
from wk_geom import BARE, INTERIOR, PLATING, STRUCTURE, Mesh, Noise

DROP = re.compile(r'^(Weapon|Turret|Launcher|Anchor|SWFX|Light |Lamp|Engine \d+|Lift thruster|Missile|Running|'
                  r'.*\| (root|anchors))', re.I)
# share of each kind of part a salvage crew took
STRIP = [(re.compile(r'^Cargo (port|starboard)'), .55), (re.compile(r'^Armour '), .22), (re.compile(r'^Detail '), .3),
         (re.compile(r'^Fitting '), .45), (re.compile(r'^Wing '), .12), (re.compile(r'^Flight deck'), .15),
         (re.compile(r'^Bridge (mast|masthead|sensor|dome)'), .7), (re.compile(r'^Ventral (lamp|boat|grapple)'), .8)]
INTERIOR_WORDS = ('glass', 'glazing', 'lens', 'lamp', 'light', 'throat', 'liner', 'groove', 'seam', 'recess', 'shadow',
                  'lattice', 'dark', 'chamber', 'bay')
STRUCTURE_WORDS = ('machinery', 'gunmetal', 'pipe', 'conduit', 'rod', 'actuator', 'heat shield', 'heat-shield', 'nozzle',
                   'structure', 'frame', 'radiator', 'rail', 'tank steel')
STEEL = (.30, .29, .27)


def source_path(sources, key):
    s = C.SOURCES[key]
    commit = s['drive'].rstrip('/').split('/')[-1]
    return sources / key / commit


# ----------------------------------------------------------------------------- materials

_img_mean = {}


def _colour(mat):
    """The colour a material was painted: its Principled base colour, or the mean of the image
    feeding it, or the base material a skin copy names after ' | '."""
    if mat is None:
        return (.4, .4, .4)
    if mat.use_nodes:
        for nd in mat.node_tree.nodes:
            if nd.type == 'BSDF_PRINCIPLED':
                sock = nd.inputs['Base Color']
                if not sock.is_linked:
                    return tuple(sock.default_value[:3])
                src = sock.links[0].from_node
                img = getattr(src, 'image', None)
                if img is not None:
                    return _image_mean(img)
                break
    if ' | ' in mat.name:
        base = bpy.data.materials.get(mat.name.split(' | ')[-1])
        if base is not None and base is not mat:
            return _colour(base)
    return tuple(mat.diffuse_color[:3])


def _image_mean(img):
    if img.name not in _img_mean:
        try:
            px = img.pixels[:]
            n = len(px) // 4
            step = max(1, n // 4096)
            r = g = b = 0.0
            k = 0
            for i in range(0, n, step):
                if px[4 * i + 3] > .5:
                    r += px[4 * i]
                    g += px[4 * i + 1]
                    b += px[4 * i + 2]
                    k += 1
            _img_mean[img.name] = (r / k, g / k, b / k) if k else (.4, .4, .4)
        except Exception:
            _img_mean[img.name] = (.4, .4, .4)
    return _img_mean[img.name]


def _slot(mat):
    n = (mat.name if mat else '').lower()
    if mat is not None and mat.use_nodes:
        for nd in mat.node_tree.nodes:
            if nd.type == 'EMISSION':
                return INTERIOR
            if nd.type == 'BSDF_PRINCIPLED' and nd.inputs['Emission Strength'].default_value > 0 \
                    and any(nd.inputs['Emission Color'].default_value[:3]):
                return INTERIOR
    if any(w in n for w in INTERIOR_WORDS):
        return INTERIOR
    if any(w in n for w in STRUCTURE_WORDS):
        return STRUCTURE
    return PLATING


# ---------------------------------------------------------------------------- assembly

def _group(name):
    """Parts strip together by their first four words: a cargo module goes whole, door and all."""
    return ' '.join(name.split()[:4])


def _strip_and_join(coll, mpu, rnd, mats, bounds):
    """Keeps what a salvage crew left, evaluated and joined into one new mesh in metres, with
    wk_base / wk_tone per face and the kit's materials."""
    dg = bpy.context.evaluated_depsgraph_get()
    scale = Matrix.Scale(mpu, 4)
    co, loops, starts, totals, fslot, fbase, ftone = [], [], [], [], [], [], []
    kept, dropped = 0, 0
    cache = {}
    lo, hi = (Vector(b) for b in bounds)
    pad = (hi - lo) * .03
    lo, hi = lo - pad, hi + pad
    taken = {}
    for o in sorted(coll.all_objects, key=lambda o: o.name):
        if o.type != 'MESH' or DROP.match(o.name):
            continue
        cen = o.matrix_world @ (sum((Vector(c) for c in o.bound_box), Vector()) / 8)
        if any(cen[k] < lo[k] or cen[k] > hi[k] for k in range(3)):
            continue                     # plumes and effect volumes outside the hull's own bounds
        g = _group(o.name)
        if g not in taken:
            taken[g] = any(rx.match(o.name) and rnd.random() < p for rx, p in STRIP)
        if taken[g]:
            dropped += 1
            continue
        ev = o.evaluated_get(dg)
        me = ev.to_mesh()
        mw = scale @ o.matrix_world
        info = []
        for ms in o.material_slots:
            m = ms.material
            if m not in cache:
                slot = _slot(m)
                if slot != INTERIOR and any(w in o.name.lower() for w in ('frame', 'longeron', 'girder', 'truss')):
                    slot = STRUCTURE
                cache[m] = (slot, _colour(m))
            info.append(cache[m])
        if not info:
            info = [(PLATING, (.4, .4, .4))]
        base = len(co)
        co.extend(tuple(mw @ v.co) for v in me.vertices)
        tone = rnd.gauss(0, .35)
        for p in me.polygons:
            starts.append(len(loops))
            totals.append(p.loop_total)
            loops.extend(base + me.loops[li].vertex_index for li in range(p.loop_start, p.loop_start + p.loop_total))
            s, c = info[min(p.material_index, len(info) - 1)]
            fslot.append(s)
            fbase.append(c)
            ftone.append(tone)
        ev.to_mesh_clear()
        kept += 1
    me = bpy.data.meshes.new('hulk')
    me.vertices.add(len(co))
    me.vertices.foreach_set('co', [c for v in co for c in v])
    me.loops.add(len(loops))
    me.loops.foreach_set('vertex_index', loops)
    me.polygons.add(len(starts))
    me.polygons.foreach_set('loop_start', starts)
    me.update(calc_edges=True)
    me.validate(clean_customdata=False)
    for m in mats:
        me.materials.append(m)
    me.polygons.foreach_set('material_index', fslot)
    a = me.attributes.new('wk_base', 'FLOAT_COLOR', 'FACE')
    a.data.foreach_set('color', [x for c in fbase for x in (*c, 1.0)])
    t = me.attributes.new('wk_tone', 'FLOAT', 'FACE')
    t.data.foreach_set('value', ftone)
    return me, kept, dropped


def _bisect_zone(bm, faces, planes):
    """Cuts the given faces (and the faces cut from them) by every plane (co, no)."""
    zone = set(faces)
    for co, no in planes:
        n0 = len(bm.faces)
        geom = list(zone) + list({e for f in zone for e in f.edges}) + list({v for f in zone for v in f.verts})
        bmesh.ops.bisect_plane(bm, geom=geom, plane_co=co, plane_no=no, dist=1e-4)
        bm.faces.ensure_lookup_table()
        zone.update(bm.faces[i] for i in range(n0, len(bm.faces)))
        zone = {f for f in zone if f.is_valid}
    return zone


def _tear(bm, x_cut, keep_sign, amp, noise, step=2.0):
    """Tears the hull at x_cut: grids the faces within amp of it, then deletes every face whose
    centre lies past the ragged surface x_cut + noise on the side away from keep_sign."""
    lo, hi = x_cut - amp * 1.2, x_cut + amp * 1.2
    zone = [f for f in bm.faces if any(lo <= v.co.x <= hi for v in f.verts)]
    ys = [v.co.y for f in zone for v in f.verts] or [0]
    zs = [v.co.z for f in zone for v in f.verts] or [0]
    planes = [((x, 0, 0), (1, 0, 0)) for x in frange(lo, hi, step)]
    planes += [((0, y, 0), (0, 1, 0)) for y in frange(min(ys), max(ys), step * 1.5)]
    planes += [((0, 0, z), (0, 0, 1)) for z in frange(min(zs), max(zs), step * 1.5)]
    _bisect_zone(bm, zone, planes)

    def surface(y, z):
        return x_cut + amp * (.75 * noise(y / 14.0, z / 14.0) + .25 * noise(y / 4.0, z / 4.0, 7.0))
    dead = [f for f in bm.faces if (f.calc_center_median().x - surface(f.calc_center_median().y,
                                                                          f.calc_center_median().z)) * keep_sign < 0]
    bmesh.ops.delete(bm, geom=dead, context='FACES')
    loose = [v for v in bm.verts if not v.link_faces]
    bmesh.ops.delete(bm, geom=loose, context='VERTS')
    _ragged_lip(bm, lo, hi, keep_sign, step)


def _ragged_lip(bm, lo, hi, keep_sign, step):
    """Breaks the grid's stair-steps along a tear: every vertex on the torn edge is pushed a
    random way along the tear and bent outward a little, so the edge reads as torn plate."""
    rnd = random.Random(len(bm.verts))
    bm.verts.index_update()
    edge = sorted({v for e in bm.edges if e.is_boundary for v in e.verts if lo <= v.co.x <= hi}, key=lambda v: v.index)
    ys = [v.co.y for v in bm.verts]
    zs = [v.co.z for v in bm.verts]
    axis = Vector((0, (min(ys) + max(ys)) / 2, (min(zs) + max(zs)) / 2))
    for v in edge:
        out = Vector((0, v.co.y - axis.y, v.co.z - axis.z))
        out = out.normalized() if out.length > 1e-6 else Vector((0, 0, 1))
        v.co.x -= keep_sign * rnd.uniform(0, step * .9)
        v.co += out * rnd.uniform(0, step * .6)
        v.co.y += rnd.uniform(-.35, .35) * step
        v.co.z += rnd.uniform(-.35, .35) * step


def frange(a, b, s):
    n = max(1, int((b - a) / s))
    return [a + (b - a) * k / n for k in range(n + 1)]


def _outline(bvh, x, yc, zc, reach, n=72):
    """The hull's outer outline at station x: rays from far outside toward the axis, first hits."""
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n
        d = Vector((0, math.cos(a), math.sin(a)))
        o = Vector((x, yc, zc)) + d * reach
        hit = bvh.ray_cast(o, -d, reach)
        pts.append(None if hit[0] is None else (hit[0], d))
    return pts


def _tear_structure(m, bvh, x_cut, keep_sign, yc, zc, reach, rnd):
    """Frames just inside the skin behind the tear, and stringers running past it, bent."""
    for k, dx in enumerate((1.5, 5.5, 10.0)):
        x = x_cut + keep_sign * dx
        pts = _outline(bvh, x, yc, zc, reach)
        ring = [None if p is None else tuple(p[0] - p[1] * 1.3) for p in pts]
        for i in range(len(ring)):
            a, b = ring[i], ring[(i + 1) % len(ring)]
            if a is None or b is None or math.dist(a, b) > 12 or rnd.random() < (.2 + .15 * k):
                continue
            radial = tuple((Vector((x, yc, zc)) - Vector(a)).normalized())
            m.member(a, b, radial, .5, 1.2, 'I', base=STEEL)
    pts = _outline(bvh, x_cut + keep_sign * 8, yc, zc, reach, n=36)
    for p in pts:
        if p is None or rnd.random() < .45:
            continue
        a = Vector(p[0]) - p[1] * 1.0
        reach_out = rnd.uniform(4, 14)
        mid = a + Vector((-keep_sign * (8 + reach_out * .5), 0, 0))
        tip = mid + Vector((-keep_sign * reach_out * .5, rnd.uniform(-1, 1) * reach_out * .4,
                            rnd.uniform(-1, 1) * reach_out * .4))
        for u, v in ((a, mid), (mid, tip)):
            m.member(tuple(u), tuple(v), tuple(-p[1]), .4, .8, 'L', base=STEEL)


def _holes(bm, bvh, rnd, count, x_lo, x_hi, mpu_len, m):
    """Plating torn out of her sides in ragged patches, frames behind each."""
    bm.faces.ensure_lookup_table()
    cands = [f for f in bm.faces if f.material_index == PLATING and x_lo < f.calc_center_median().x < x_hi
             and f.calc_area() > .5]
    if not cands:
        return 0
    weights = [f.calc_area() for f in cands]
    noise = Noise(rnd.randrange(1 << 30))
    made = 0
    for h in range(count):
        f = rnd.choices(cands, weights)[0]
        if not f.is_valid:
            continue
        c, n = f.calc_center_median().copy(), f.normal.copy()
        if abs(n.x) > .8:
            continue
        r = rnd.uniform(.025, .06) * mpu_len
        tl = (Vector((1, 0, 0)) - n * n.x).normalized()       # along the hull
        tr = n.cross(tl).normalized()                          # round the hull
        near = [g for g in bm.faces if (g.calc_center_median() - c).length < r * 1.8 and g.normal.dot(n) > .15]
        planes = [(tuple(c + tl * s), tuple(tl)) for s in frange(-r * 1.4, r * 1.4, 2.0)]
        planes += [(tuple(c + tr * s), tuple(tr)) for s in frange(-r * 1.4, r * 1.4, 2.0)]
        zone = _bisect_zone(bm, near, planes)
        dead = []
        for g in zone:
            d = g.calc_center_median() - c
            if g.normal.dot(n) < .15 or abs(d.dot(n)) > 8:
                continue
            u, v = d.dot(tl), d.dot(tr)
            ang = math.atan2(v, u)
            rr = r * (1 + .3 * noise(math.cos(ang) * 2, math.sin(ang) * 2, h) + .15 * noise(math.cos(ang) * 7, math.sin(ang) * 7, h))
            if math.hypot(u * .8, v) < rr:
                dead.append(g)
        if not dead:
            continue
        bmesh.ops.delete(bm, geom=dead, context='FACES')
        made += 1
        # frames behind the hole: ribs round the hull at a few stations, a stringer along it
        for s in frange(-r * .8, r * .8, max(3.0, r * .45)):
            pts = []
            for t in frange(-r * 1.1, r * 1.1, 1.5):
                o = c + tl * s + tr * t + n * 30
                hit = bvh.ray_cast(o, -n, 60)
                pts.append(None if hit[0] is None else tuple(hit[0] - n * 1.0))
            for p0, p1 in zip(pts, pts[1:]):
                if p0 and p1:
                    m.member(p0, p1, tuple(-n), .4, 1.0, 'I', base=STEEL)
        for t in (-r * .4, r * .35):
            o0, o1 = c + tr * t - tl * r * 1.1 + n * 30, c + tr * t + tl * r * 1.1 + n * 30
            h0, h1 = bvh.ray_cast(o0, -n, 60), bvh.ray_cast(o1, -n, 60)
            if h0[0] is not None and h1[0] is not None:
                m.member(tuple(h0[0] - n * 1.6), tuple(h1[0] - n * 1.6), tuple(-n), .35, .7, 'L', base=STEEL)
    return made


def _append(bm, m):
    """Adds a wk_geom Mesh (members) to the bmesh, with its slots, colours and tones."""
    lb = bm.faces.layers.float_color.get('wk_base')
    lt = bm.faces.layers.float.get('wk_tone')
    vs = [bm.verts.new(v) for v in m.verts]
    new = []
    for f, mat, tone, base in zip(m.faces, m.mats, m.tones, m.bases):
        try:
            face = bm.faces.new([vs[i] for i in f])
        except ValueError:
            continue
        face.material_index = mat
        face[lb] = (*base, 1.0)
        face[lt] = tone
        new.append(face)
    bmesh.ops.recalc_face_normals(bm, faces=new)


def derive(name, key, span, torn, seed, sources, mats):
    """(object, provenance) for one hulk row of wk_catalogue.HULKS."""
    src = C.SOURCES[key]
    folder = source_path(sources, key)
    blend = folder / src['file']
    man = json.loads((folder / 'manifest.json').read_text())
    mpu = man['metres_per_unit']
    rnd = random.Random(seed)
    with bpy.data.libraries.load(str(blend), link=False) as (df, dt):
        dt.collections = [src['collection']]
    coll = dt.collections[0]
    bpy.context.scene.collection.children.link(coll)
    bounds = man.get('bounds_units_rest') or man.get('bounds_units')
    me, kept, dropped = _strip_and_join(coll, mpu, rnd, mats, bounds)
    bpy.context.scene.collection.children.unlink(coll)
    for o in list(coll.all_objects):
        bpy.data.objects.remove(o, do_unlink=True)
    for c in list(coll.children_recursive) + [coll]:
        bpy.data.collections.remove(c)
    bm = bmesh.new()
    bm.from_mesh(me)
    xs = [v.co.x for v in bm.verts]
    x0, x1 = min(xs), max(xs)
    length = x1 - x0
    ys = [v.co.y for v in bm.verts]
    zs = [v.co.z for v in bm.verts]
    yc, zc = (min(ys) + max(ys)) / 2, (min(zs) + max(zs)) / 2
    reach = max(max(ys) - min(ys), max(zs) - min(zs)) * 1.5
    bvh = BVHTree.FromBMesh(bm)
    members = Mesh(STEEL)
    noise = Noise(seed)
    amp = .05 * length
    lo, hi = x0 + span[0] * length, x0 + span[1] * length
    if 'aft' in torn and span[0] > 0:
        _tear(bm, lo, +1, amp, noise)
        _tear_structure(members, bvh, lo, +1, yc, zc, reach, rnd)
    if 'fore' in torn and span[1] < 1:
        _tear(bm, hi, -1, amp, Noise(seed + 1))
        _tear_structure(members, bvh, hi, -1, yc, zc, reach, rnd)
    bvh = BVHTree.FromBMesh(bm)
    holes = _holes(bm, bvh, rnd, max(3, round((span[1] - span[0]) * length / 40)), lo + amp * 2, hi - amp * 2,
                   length, members)
    _append(bm, members)
    out = bpy.data.meshes.new('Wreck ' + name)
    bm.to_mesh(out)
    bm.free()
    for mt in mats:
        out.materials.append(mt)
    bpy.data.meshes.remove(me)
    ob = bpy.data.objects.new('Wreck ' + name, out)
    bpy.context.scene.collection.objects.link(ob)
    info = dict(build=src['drive'], file=src['file'], class_length_m=src['length_m'], metres_per_unit=mpu,
                span=list(span), torn=list(torn), parts_kept=kept, parts_stripped=dropped, holes=holes,
                measured_length_m=round(length, 1))
    return ob, info
