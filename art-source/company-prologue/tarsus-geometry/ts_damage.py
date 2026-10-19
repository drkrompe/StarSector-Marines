"""The Tarsus 'boarded' condition state: the Asharu battle's prize.

The pirates shot her drives out to stop her, then put Kites (P) on her two
flank collars and cut their way in; the Colossus Mk.III carries her off by the
four dorsal grapple receivers. She is disabled, not destroyed: the drive
section is cratered and scorched and its lamps are dead, the rest of her is
whole, and nothing breaches the holds, the command module or the tanks.

The contract is second-wind-pipeline.md's (Condition states, Layers):
- a base object is never renamed, replaced or deleted. A scar Boolean-cuts it
  (faces the cut opens take 'Scar burnt metal') or overrides its look with an
  object-level material slot, so the mesh and its skin keep their base material;
- everything added is named 'Scar <id> ...' and takes 'Scar ...' materials;
- every site is resolved by a ray cast against the clean ship before any cut,
  so a scar id lands in the same place however many scars are built.

apply(state) works on whatever file is open, by object name: build_tarsus.py
calls it as its last modelling phase (--state boarded), and damage_blend.py
runs it on the skinned build, so the cuts open the livery like any face.

Pass 7 keys its effects off the anchors: 'Scar <id> smoke anchor' for smoke
and 'Scar <id> spark anchor' (the shared kit's conduit-spark name, sparking =
True) for sparks, +X out of the hull like the docking anchors, sized in
radius_m.
"""
import math
import random
import bmesh
import bpy
from mathutils import Matrix, Vector

STATES = ('boarded',)
SCAR_STATUSES = ('open', 'gone')

# Nothing a cut may reach into: the mounts, the docking hardware the boarders and the
# Mk.III use, the command module, the tanks, the lamps (overridden, never cut) and scars.
UNCUT = ('Scar', 'Turret', 'Clamp receiver', 'Kite collar', 'Bridge', 'Tank', 'Pipe', 'Review')
NO_CUT_WORDS = ('lens', 'lamp')


class Scar:
    """One damage site. kind: crater (cut, scorch, torn plating, anchor), scorch
    (decal only), ring (a cutting-torch ring decal round an anchor), dead (object
    material override). status: one per state in STATES."""
    def __init__(self, id, kind, status, note='', **params):
        status = status.split() if isinstance(status, str) else list(status)
        assert len(status) == len(STATES) and all(s in SCAR_STATUSES for s in status), (id, status)
        self.id, self.kind, self.note, self.params = id, kind, note, params
        self.states = dict(zip(STATES, status))


# Crater rows: aim from 'eye' at 'at' (hull units); r is the hole radius, depth how far
# the cut reaches in; emit is what pass 7 puts on the anchor.
SCARS = [
    Scar('D1', 'crater', 'open', 'port main drive bell holed through its upper outboard quarter',
         eye=(-112, 44, 30), at=(-76, 25, 8), r=3.6, depth=3.2, petals=5, emit='smoke+spark', seed=11),
    Scar('D2', 'crater', 'open', 'starboard main drive bell lip blown away low outboard',
         eye=(-118, -48, -20), at=(-77, -25, -4), r=4.4, depth=3.6, petals=6, emit='smoke+spark', seed=12),
    Scar('D3', 'crater', 'open', 'stern service housing between the mains, burned through',
         eye=(-130, 1, 6), at=(-72, 1, 1), r=3.2, depth=4.0, petals=4, emit='smoke+spark', seed=13),
    Scar('D4', 'crater', 'open', 'strafing run along the drive deck, aft hit',
         eye=(-96, 12, 40), at=(-68, 3, 8), r=2.0, depth=2.0, petals=3, emit='smoke', seed=14),
    Scar('D5', 'crater', 'open', 'strafing run along the drive deck, middle hit',
         eye=(-90, 16, 40), at=(-62, 12, 7), r=2.4, depth=2.2, petals=4, emit='smoke', seed=15),
    Scar('D6', 'crater', 'open', 'strafing run along the drive deck, forward hit on the castle foot',
         eye=(-84, 20, 40), at=(-55, 18, 9), r=1.9, depth=1.8, petals=3, emit='', seed=16),
    Scar('D7', 'crater', 'open', 'inboard port auxiliary bell shot through',
         eye=(-116, 22, -14), at=(-75, 13, -10), r=2.2, depth=2.4, petals=3, emit='smoke', seed=17),
    Scar('D8', 'crater', 'open', 'inboard starboard auxiliary bell shot through',
         eye=(-116, -20, -16), at=(-75, -9, -11), r=2.0, depth=2.4, petals=3, emit='', seed=18),
    Scar('D9', 'crater', 'open', 'drive skirt keel holed from below',
         eye=(-80, -6, -60), at=(-66, -6, -14), r=2.8, depth=2.6, petals=4, emit='smoke', seed=19),
    Scar('D10', 'crater', 'open', 'starboard aft drum casting hit on its aft face',
         eye=(-96, -62, 4), at=(-53, -40, 4), r=2.4, depth=2.0, petals=4, emit='', seed=20),
    Scar('D11', 'crater', 'open', 'port drive block flank hit beside the radiators',
         eye=(-70, 90, 0), at=(-62, 38, -2), r=2.2, depth=2.0, petals=3, emit='smoke', seed=21),
    Scar('S1', 'scorch', 'open', 'the stern face scorched by the hits round the mains',
         eye=(-130, 1, 2), at=(-74, 1, 1), r=14.0, seed=31),
    Scar('S2', 'scorch', 'open', 'the drive deck blackened along the strafing run',
         eye=(-88, 14, 45), at=(-62, 10, 7), r=9.0, seed=32),
    Scar('K1', 'ring', 'open', 'cutting-torch ring where the boarders burned the starboard collar hatch',
         anchor='Kite collar starboard anchor', r0=2.4, r1=3.6, seed=41),
    Scar('K2', 'ring', 'open', 'cutting-torch ring where the boarders burned the port collar hatch',
         anchor='Kite collar port anchor', r0=2.4, r1=3.6, seed=42),
    Scar('L1', 'dead', 'open', 'drive section power lost: stern and deck marker lamps dark',
         objects=('Drive stern lamp port lens', 'Drive stern lamp starboard lens', 'Drive stern lamp white lens',
                  'Drive skirt stern lamp lens', 'Drive deck marker lamp lens')),
]


# --- materials ---------------------------------------------------------------

def _material(name, colour, metallic, roughness, alpha_attr=None):
    m = bpy.data.materials.get(name)
    if m:
        return m
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*colour, 1)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes['Principled BSDF']
    bsdf.inputs['Base Color'].default_value = (*colour, 1)
    bsdf.inputs['Metallic'].default_value = metallic
    bsdf.inputs['Roughness'].default_value = roughness
    if alpha_attr:
        # A decal fades to nothing at its rim: vertex alpha mixes the scorch over the hull.
        out = nt.nodes['Material Output']
        attr = nt.nodes.new('ShaderNodeAttribute')
        attr.attribute_type, attr.attribute_name = 'GEOMETRY', alpha_attr
        clear = nt.nodes.new('ShaderNodeBsdfTransparent')
        mix = nt.nodes.new('ShaderNodeMixShader')
        nt.links.new(attr.outputs['Fac'], mix.inputs['Fac'])
        nt.links.new(clear.outputs[0], mix.inputs[1])
        nt.links.new(bsdf.outputs[0], mix.inputs[2])
        nt.links.new(mix.outputs[0], out.inputs['Surface'])
        if hasattr(m, 'surface_render_method'):
            m.surface_render_method = 'BLENDED'
    return m


def materials():
    return {
        'burnt': _material('Scar burnt metal', (.035, .028, .024), .55, .8),
        'torn': _material('Scar torn plating', (.34, .31, .27), .8, .42),
        'scorch': _material('Scar scorch', (.018, .015, .013), 0, .95, alpha_attr='Scar alpha'),
        'cut': _material('Scar torch-cut scorch', (.03, .022, .016), .3, .7, alpha_attr='Scar alpha'),
        'dead': _material('Scar dead lamp lens', (.06, .055, .05), 0, .35),
    }


# --- sites -------------------------------------------------------------------

def _frame(n):
    ref = Vector((0, 0, 1)) if abs(n.z) < .9 else Vector((1, 0, 0))
    u = n.cross(ref).normalized()
    return u, n.cross(u).normalized()


def _cuttable(ob):
    return (ob.type == 'MESH' and not ob.name.startswith(UNCUT) and not ob.hide_render
            and not any(w in ob.name.lower() for w in NO_CUT_WORDS))


def _cast(origin, direction, skip_scars=True):
    """First hit on the ship along a ray: (point, normal, object), stepping past scars."""
    dg = bpy.context.evaluated_depsgraph_get()
    o = Vector(origin)
    d = Vector(direction).normalized()
    for _ in range(8):
        hit, p, n, _, ob, _ = bpy.context.scene.ray_cast(dg, o, d)
        if not hit:
            return None
        if skip_scars and (ob.name.startswith('Scar') or ob.name.startswith('Review')):
            o = p + d*1e-3
            continue
        if n.dot(d) > 0:
            n = -n
        return p, n.normalized(), ob
    return None


def resolve(scar):
    """Point, outward normal and the object hit, on the clean ship."""
    p = scar.params
    eye, at = Vector(p['eye']), Vector(p['at'])
    hit = _cast(eye, at - eye)
    if hit is None:
        raise RuntimeError(f'Scar {scar.id}: ray from {p["eye"]} to {p["at"]} missed the ship')
    return hit


# --- geometry ----------------------------------------------------------------

def _mesh_object(name, verts, faces, mat):
    me = bpy.data.meshes.new(name)
    me.from_pydata([tuple(v) for v in verts], [], faces)
    me.update()
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    me.materials.append(mat)
    return ob


def _cutter(scar, p, n, mats):
    """An irregular faceted blast cavity: a jittered icosphere, flattened along the normal."""
    rng = random.Random(scar.params['seed'])
    r, depth = scar.params['r'], scar.params['depth']
    u, v = _frame(n)
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=2, radius=1.0)
    frame = Matrix((u, v, n)).transposed()
    for vert in bm.verts:
        k = rng.uniform(.78, 1.15)
        c = vert.co * k
        vert.co = p + frame @ Vector((c.x*r, c.y*r, c.z*depth))
    me = bpy.data.meshes.new(f'Scar {scar.id} cutter')
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(f'Scar {scar.id} cutter', me)
    bpy.context.scene.collection.objects.link(ob)
    me.materials.append(mats['burnt'])
    return ob


def _overlaps(ob, centre, radius):
    w = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
    lo = Vector([min(q[i] for q in w) for i in range(3)])
    hi = Vector([max(q[i] for q in w) for i in range(3)])
    nearest = Vector([min(max(centre[i], lo[i]), hi[i]) for i in range(3)])
    return (nearest - centre).length <= radius


def _extent(me):
    if not me.vertices:
        return None
    xs = [v.co for v in me.vertices]
    return (Vector([min(c[i] for c in xs) for i in range(3)]), Vector([max(c[i] for c in xs) for i in range(3)]))


def _closed(me):
    bm = bmesh.new()
    bm.from_mesh(me)
    ok = all(e.is_manifold for e in bm.edges)
    bm.free()
    return ok


def _cut(target, cutter, mat):
    """Boolean difference; the opened faces take the cutter's material. A closed mesh
    cuts with MANIFOLD; an open one (a bell, a liner, a batch of plates) with
    hole-tolerant EXACT. A difference can only remove, so a result that grows past the
    mesh's own extent has kept the cutter's skin, and is put back: the scorch and
    torn plating still mark the site."""
    if target.data.users > 1:
        target.data = target.data.copy()
    if mat.name not in [m.name for m in target.data.materials if m]:
        target.data.materials.append(mat)
    keep = target.data.copy()
    before, box = len(target.data.polygons), _extent(target.data)
    closed = _closed(target.data)
    mod = target.modifiers.new('Scar cut', 'BOOLEAN')
    mod.operation, mod.object = 'DIFFERENCE', cutter
    mod.solver = 'MANIFOLD' if closed else 'EXACT'
    if not closed:
        mod.use_hole_tolerant = True
    if hasattr(mod, 'material_mode'):
        mod.material_mode = 'TRANSFER'
    with bpy.context.temp_override(object=target, active_object=target):
        bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)
        bpy.ops.object.modifier_apply(modifier=mod.name)
    after = _extent(target.data)
    grew = after is None or box is None or any(after[0][i] < box[0][i] - .02 or after[1][i] > box[1][i] + .02
                                               for i in range(3))
    if grew or len(target.data.polygons) == before:
        spent = target.data
        target.data = keep
        bpy.data.meshes.remove(spent)
        return False
    bpy.data.meshes.remove(keep)
    return True


def _decal(name, centre, n, r_in, r_out, alpha, mat, seed, step=.6, segs=None, cliff=1.0):
    """A thin fan projected onto whatever is under it, in rings about `step` apart.
    alpha(t) gives the opacity at t = 0 (r_in) .. 1 (r_out), jittered; a face whose
    corners land more than `cliff` apart along the normal would bridge a step in the
    hull and sink inside it, so it is left out."""
    rng = random.Random(seed)
    u, v = _frame(n)
    reach = r_out + 2.0
    count = max(3, int(math.ceil((r_out - r_in)/step)) + 1)
    radii = [r_in + (r_out - r_in)*i/(count-1) for i in range(count)]
    segs = segs or max(24, min(72, int(math.tau*r_out/step)))
    edge = [rng.uniform(.8, 1.12) for _ in range(segs)]
    rings = []
    for i, rho in enumerate(radii):
        t = i/(count-1)
        ring = []
        for k in range(segs):
            a = math.tau*k/segs
            j = rho*(1 + (edge[k]-1)*t)
            o = centre + n*reach + (u*math.cos(a) + v*math.sin(a))*j
            hit = _cast(o, -n)
            if hit is None or (hit[0]-o).length > 2*reach:
                ring.append(None)
                continue
            ring.append((hit[0] + hit[1]*.05, (hit[0]-centre).dot(n), max(0.0, min(1.0, alpha(t)*rng.uniform(.82, 1.0)))))
        rings.append(ring)
    verts, cols, faces, index = [], [], [], {}
    def idx(i, k):
        key = (i, k % segs)
        if key not in index:
            q = rings[i][k % segs]
            if q is None:
                return None
            index[key] = len(verts)
            verts.append(q[0])
            cols.append(q[2])
        return index[key]
    for i in range(count-1):
        for k in range(segs):
            corners = [(i, k), (i, k+1), (i+1, k+1), (i+1, k)]
            qs = [rings[a][b % segs] for a, b in corners]
            if None in qs or max(q[1] for q in qs) - min(q[1] for q in qs) > cliff:
                continue
            faces.append([idx(a, b) for a, b in corners])
    if not faces:
        return None
    ob = _mesh_object(name, verts, faces, mat)
    attr = ob.data.color_attributes.new('Scar alpha', 'FLOAT_COLOR', 'POINT')
    for i, a in enumerate(cols):
        attr.data[i].color = (a, a, a, 1.0)
    return ob


def _petals(scar, p, n, mats):
    """Torn plating curled outward round the rim of a crater."""
    rng = random.Random(scar.params['seed'] + 1000)
    r = scar.params['r']
    u, v = _frame(n)
    verts, faces = [], []
    count = scar.params.get('petals', 4)
    for k in range(count):
        a = math.tau*k/count + rng.uniform(-.35, .35)
        d = u*math.cos(a) + v*math.sin(a)
        t = n.cross(d)
        w = r*rng.uniform(.3, .5)
        length = r*rng.uniform(.35, .65)
        q = p + d*r*.92
        lift = rng.uniform(.35, .8)
        pts = [q - t*w/2, q + t*w/2,
               q + d*length*.45 + n*length*lift*.4 + t*w*.3, q + d*length*.45 + n*length*lift*.4 - t*w*.35,
               q + d*length*.8 + n*length*lift + t*w*rng.uniform(-.15, .15)]
        o = len(verts)
        verts += pts
        faces += [[o, o+1, o+2, o+3], [o+3, o+2, o+4]]
    ob = _mesh_object(f'Scar {scar.id} torn plating', verts, faces, mats['torn'])
    sol = ob.modifiers.new('Scar plate thickness', 'SOLIDIFY')
    sol.thickness, sol.offset = .08, 0
    return ob


def _anchor(scar, p, n, kind, metres_per_unit):
    """'Scar <id> smoke anchor' or 'Scar <id> spark anchor' (the shared VFX kit's
    name for broken live conduit): +X out of the hull, its size in metres."""
    e = bpy.data.objects.new(f'Scar {scar.id} {kind} anchor', None)
    e.empty_display_type, e.empty_display_size = 'SINGLE_ARROW', scar.params['r']
    x = n
    y = Vector((0, 0, 1)).cross(x)
    y = (Vector((0, 1, 0)) if y.length < 1e-3 else y).normalized()
    z = x.cross(y)
    lift = .3 if kind == 'smoke' else .1
    e.matrix_world = Matrix.Translation(p + n*lift) @ Matrix((x, y, z)).transposed().to_4x4()
    e['scar_id'], e['emit'] = scar.id, kind
    e['radius_units'] = scar.params['r']
    e['radius_m'] = round(scar.params['r']*metres_per_unit*(1.0 if kind == 'smoke' else .35), 3)
    if kind == 'spark':
        e['sparking'] = True
    bpy.context.scene.collection.objects.link(e)
    return e


# --- the state ---------------------------------------------------------------

def apply(state='boarded', metres_per_unit=.5):
    """Build every scar open in this state on the open file; return the record."""
    assert state in STATES, state
    mats = materials()
    live = [s for s in SCARS if s.states[state] == 'open']
    bpy.context.view_layer.update()
    # 1. Every site on the clean ship, before anything changes it.
    sites = {}
    for s in live:
        if s.kind in ('crater', 'scorch'):
            sites[s.id] = resolve(s)
        elif s.kind == 'ring':
            e = bpy.data.objects[s.params['anchor']]
            sites[s.id] = (e.matrix_world.translation.copy(), e.matrix_world.to_3x3().col[0].normalized(), None)
    record = []
    # 2. Cuts.
    cut_objects = {}
    for s in live:
        if s.kind != 'crater':
            continue
        p, n, hit = sites[s.id]
        cutter = _cutter(s, p, n, mats)
        reach = 1.2*max(s.params['r'], s.params['depth'])
        names = []
        for ob in [o for o in bpy.context.scene.objects if _cuttable(o) and _overlaps(o, p, reach)]:
            if _cut(ob, cutter, mats['burnt']):
                names.append(ob.name)
        bpy.data.objects.remove(cutter, do_unlink=True)
        if not names:
            raise RuntimeError(f'Scar {s.id}: the cut opened nothing (hit {hit.name})')
        cut_objects[s.id] = names
    bpy.context.view_layer.update()
    # 3. Scorch decals, laid on the cut ship; then torn plating and anchors.
    for s in live:
        if s.kind == 'crater':
            p, n, hit = sites[s.id]
            r = s.params['r']
            _decal(f'Scar {s.id} scorch', p, n, r*.85, r*3.2, lambda t: 1.0 - t**1.7, mats['scorch'],
                   s.params['seed'], step=min(.6, r/4))
        elif s.kind == 'scorch':
            p, n, hit = sites[s.id]
            _decal(f'Scar {s.id} scorch', p, n, 0.0, s.params['r'], lambda t: .8*(1.0 - t**1.4), mats['scorch'],
                   s.params['seed'])
        elif s.kind == 'ring':
            p, n, _ = sites[s.id]
            r0, r1 = s.params['r0'], s.params['r1']
            _decal(f'Scar {s.id} torch-cut scorch', p, n, r0 - .5, r1 + 1.0,
                   lambda t: max(0.0, 1.0 - abs(t - .4)/.5)**.8, mats['cut'], s.params['seed'], step=.25, segs=48)
    for s in live:
        entry = {'id': s.id, 'kind': s.kind, 'status': s.states[state], 'note': s.note}
        if s.kind == 'crater':
            p, n, hit = sites[s.id]
            _petals(s, p, n, mats)
            kinds = [k for k in ('smoke', 'spark') if k in s.params['emit']]
            for k in kinds:
                _anchor(s, p, n, k, metres_per_unit)
            if kinds:
                entry['anchors'] = [f'Scar {s.id} {k} anchor' for k in kinds]
                entry['emit'] = s.params['emit']
            entry.update(location=[round(c, 2) for c in p], outward=[round(c, 3) for c in n],
                         radius_units=s.params['r'], depth_units=s.params['depth'],
                         hit=hit.name, cut=cut_objects[s.id])
        elif s.kind in ('scorch', 'ring'):
            p, n, _ = sites[s.id]
            entry.update(location=[round(c, 2) for c in p], outward=[round(c, 3) for c in n])
        elif s.kind == 'dead':
            done = []
            for name in s.params['objects']:
                ob = bpy.data.objects.get(name)
                if ob is None:
                    continue
                for slot in ob.material_slots:
                    slot.link = 'OBJECT'
                    slot.material = mats['dead']
                done.append(name)
            entry['objects'] = done
        record.append(entry)
    return record
