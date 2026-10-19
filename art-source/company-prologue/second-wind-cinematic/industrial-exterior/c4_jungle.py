"""The jungle around Greywater's port, seen from above: east of Kilo Nine's fence and north of the apron.

Called from `c4x_scene.build()` once the port is built. It takes away the blockout's ball trees
(`Jungle canopy`, `Port tree line`) and grows layered rainforest in their place, keeping clear of
the fence line, its service track and the burnt clearings beside it.

The canopy is a few hundred thousand faces of low domes, one per crown, each wearing one cell of
the painted crown sheet (`jungle/raw/crowns-v1.png`, from `jungle/crown_guide.py` and
`jungle/crowns.txt`) projected straight down and cut out by its alpha. The domes give real sun
shading and real shadows on the layers below; the paint gives leaves. Layers, low to high:

- understory: ferns, palms and young broadleaf where the canopy opens and along the edge,
- the clearing edge: a dense band of palms and ferns stepping up into the forest,
- the canopy: overlapping broadleaf crowns 22-34 m up, with palms through it and gaps,
- emergents: the odd big tree standing clear above the rest.

A slow gust runs across the crowns through the materials' displacement, so the canopy sways a
little over the shot without moving any geometry.
"""
import math
from pathlib import Path

import bpy
import numpy as np

HERE = Path(__file__).resolve().parent
SHEET = HERE / 'jungle' / 'raw' / 'crowns-v1.png'
GRID = 4                                   # the sheet is 4 x 4 crowns; rows below
BROADLEAF, EMERGENT, PALM, FERN = 0, 1, 2, 3
CUT = 0.43                                 # a dome's rim in the sheet, as a fraction of its cell
FPS = 24.0


# ---------------------------------------------------------------- where the jungle is

class Ground:
    """Where trees may stand: east of the fence and north of the apron, never in a clearing."""

    def __init__(self, fence_x, north_y, burns, seed=3):
        self.fence_x, self.north_y = fence_x, north_y
        rng = np.random.default_rng(seed)
        self.waves = [(rng.uniform(0.004, 0.03), rng.uniform(0, 2 * np.pi), rng.uniform(3, 9)) for _ in range(4)]
        self.burns = burns                 # convex polygons (n, 2), already grown by a margin

    def wobble(self, s):
        return sum(a * np.sin(f * s + p) for f, p, a in self.waves)

    def edge_distance(self, x, y):
        """Metres into the jungle from its edge (negative outside)."""
        east = x - (self.fence_x + 11.0 + self.wobble(y))
        north = y - (self.north_y + self.wobble(x * 1.3 + 50))
        d = np.where(x > self.fence_x + 4, east, north)
        d = np.where((x > self.fence_x + 4) & (y > self.north_y + 30), np.maximum(east, north), d)
        return d

    def in_burn(self, x, y):
        out = np.zeros(x.shape, bool)
        for poly in self.burns:
            inside = np.ones(x.shape, bool)
            for (ax, ay), (bx, by) in zip(poly, np.roll(poly, -1, axis=0)):
                inside &= (bx - ax) * (y - ay) - (by - ay) * (x - ax) >= 0
            out |= inside
        return out


def burn_polygons(margin):
    ob = bpy.data.objects.get('Burn patches')
    if ob is None:
        return []
    polys = []
    for f in ob.data.polygons:
        if f.normal.z < 0.9:
            continue
        pts = np.array([(ob.matrix_world @ ob.data.vertices[i].co).xy for i in f.vertices])
        c = pts.mean(axis=0)
        pts = c + (pts - c) * (1 + margin / max(1.0, np.linalg.norm(pts - c, axis=1).mean()))
        area = np.sum(pts[:, 0] * np.roll(pts[:, 1], -1) - np.roll(pts[:, 0], -1) * pts[:, 1])
        polys.append(pts if area > 0 else pts[::-1])
    return polys


def noise2(x, y, scale, seed):
    """Cheap smooth value noise: a sum of a few rotated sine waves, in about -1..1."""
    rng = np.random.default_rng(seed)
    v = np.zeros_like(x)
    for _ in range(5):
        a = rng.uniform(0, np.pi)
        f = scale * rng.uniform(0.6, 1.6)
        v += np.sin((x * np.cos(a) + y * np.sin(a)) * f + rng.uniform(0, 2 * np.pi))
    return v / np.sqrt(5 / 2)


def scatter(rng, box, spacing, jitter):
    x0, x1, y0, y1 = box
    gx, gy = np.meshgrid(np.arange(x0, x1, spacing), np.arange(y0, y1, spacing * 0.866))
    gx = gx + (np.arange(gx.shape[0])[:, None] % 2) * spacing / 2
    x = gx.ravel() + rng.uniform(-jitter, jitter, gx.size)
    y = gy.ravel() + rng.uniform(-jitter, jitter, gy.size)
    return x, y


# ---------------------------------------------------------------- crowns

def dome(rings, segs):
    """A unit dome: centre vertex, then rings out to radius 1. Returns (xy, r, faces)."""
    pts, rr = [(0.0, 0.0)], [0.0]
    for i in range(1, rings + 1):
        r = i / rings
        for j in range(segs):
            a = 2 * math.pi * (j + 0.5 * (i % 2)) / segs
            pts.append((r * math.cos(a), r * math.sin(a)))
            rr.append(r)
    faces = [(0, 1 + j, 1 + (j + 1) % segs) for j in range(segs)]
    for i in range(1, rings):
        a0, b0 = 1 + (i - 1) * segs, 1 + i * segs
        for j in range(segs):
            j1 = (j + 1) % segs
            faces.append((a0 + j, b0 + j, b0 + j1, a0 + j1))
    return np.array(pts), np.array(rr), faces


def crowns_mesh(name, crowns, material, rings=4, segs=12):
    """One mesh holding every crown in `crowns`: dicts of arrays x, y, z (top), r, h (dome
    height), rot, cell (sheet index 0..15), tint (n, 3)."""
    xy, rr, faces = dome(rings, segs)
    n, nv = len(crowns['x']), len(xy)
    c, s = np.cos(crowns['rot'])[:, None], np.sin(crowns['rot'])[:, None]
    lx, ly = xy[None, :, 0], xy[None, :, 1]
    jit = np.random.default_rng(len(name)).uniform(0.85, 1.12, (n, nv))        # lumpy, not lathe-turned
    jit[:, 0] = 1.0
    vx = crowns['x'][:, None] + crowns['r'][:, None] * (lx * c - ly * s) * np.where(rr > 0, jit, 1)
    vy = crowns['y'][:, None] + crowns['r'][:, None] * (lx * s + ly * c) * np.where(rr > 0, jit, 1)
    prof = (1 - rr ** 2) ** 0.7
    vz = crowns['z'][:, None] - crowns['h'][:, None] * (1 - prof[None, :]) * jit
    verts = np.stack([vx, vy, vz], axis=-1).reshape(-1, 3)

    # The sheet's cell, in unrotated crown space, so the painting turns with the crown.
    col, row = crowns['cell'] % GRID, crowns['cell'] // GRID
    u = (col[:, None] + 0.5 + lx * CUT) / GRID
    v = 1 - (row[:, None] + 0.5 - ly * CUT) / GRID
    uv = np.stack([u, v], axis=-1).reshape(-1, 2)

    tri = [f for f in faces if len(f) == 3]
    quad = [f for f in faces if len(f) == 4]
    loops = np.concatenate([np.array(tri).ravel(), np.array(quad).ravel()])
    sizes = np.array([3] * len(tri) + [4] * len(quad))
    all_loops = (loops[None, :] + nv * np.arange(n)[:, None]).ravel()
    all_sizes = np.tile(sizes, n)
    starts = np.concatenate([[0], np.cumsum(all_sizes)[:-1]])

    me = bpy.data.meshes.new(name)
    me.vertices.add(n * nv)
    me.vertices.foreach_set('co', verts.astype(np.float32).ravel())
    me.loops.add(len(all_loops))
    me.loops.foreach_set('vertex_index', all_loops.astype(np.int32))
    me.polygons.add(len(all_sizes))
    me.polygons.foreach_set('loop_start', starts.astype(np.int32))
    uvl = me.uv_layers.new(name='UVMap')
    uvl.data.foreach_set('uv', uv[all_loops].astype(np.float32).ravel())
    tint = me.color_attributes.new('tint', 'FLOAT_COLOR', 'POINT')
    rgba = np.concatenate([np.repeat(crowns['tint'], nv, axis=0), np.ones((n * nv, 1))], axis=1)
    tint.data.foreach_set('color', rgba.astype(np.float32).ravel())
    me.update(calc_edges=True)
    me.shade_smooth()
    me.validate(clean_customdata=False)
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    ob.data.materials.append(material)
    return ob


def crown_material(name, image, sway):
    """The sheet through each crown's own cell, tinted per crown, cut out by its alpha, with a
    gust running across it."""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    m.surface_render_method = 'DITHERED'
    m.diffuse_color = (0.07, 0.16, 0.06, 1)     # what the flat overhead guide shows
    try:
        m.use_transparent_shadow = True
    except AttributeError:
        pass
    nt = m.node_tree
    pr = nt.nodes['Principled BSDF']
    pr.inputs['Roughness'].default_value = 0.75
    pr.inputs['Specular IOR Level'].default_value = 0.35
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = image
    tex.interpolation = 'Linear'
    tex.extension = 'CLIP'
    attr = nt.nodes.new('ShaderNodeAttribute')
    attr.attribute_name = 'tint'
    mul = nt.nodes.new('ShaderNodeMix')
    mul.data_type = 'RGBA'
    mul.blend_type = 'MULTIPLY'
    mul.inputs['Factor'].default_value = 1.0
    nt.links.new(tex.outputs['Color'], mul.inputs['A'])
    nt.links.new(attr.outputs['Color'], mul.inputs['B'])
    nt.links.new(mul.outputs['Result'], pr.inputs['Base Color'])
    clip = nt.nodes.new('ShaderNodeMath')               # a hard cut: leaves, not a soft halo
    clip.operation = 'GREATER_THAN'
    clip.inputs[1].default_value = 0.45
    nt.links.new(tex.outputs['Alpha'], clip.inputs[0])
    nt.links.new(clip.outputs[0], pr.inputs['Alpha'])
    if sway:
        add_sway(m, sway)
    return m


def add_sway(m, amp):
    """Displace each crown sideways by a slow travelling gust; the top moves, the rim less."""
    m.displacement_method = 'DISPLACEMENT'
    nt = m.node_tree
    out = next(n for n in nt.nodes if n.type == 'OUTPUT_MATERIAL')
    t = nt.nodes.new('ShaderNodeValue')
    t.label = 'seconds'
    fc = t.outputs[0].driver_add('default_value')
    fc.driver.expression = 'frame / %g' % FPS
    geo = nt.nodes.new('ShaderNodeNewGeometry')
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    nt.links.new(geo.outputs['Position'], sep.inputs[0])

    def math_(op, a, b):
        n = nt.nodes.new('ShaderNodeMath')
        n.operation = op
        for i, v in enumerate((a, b)):
            if isinstance(v, (int, float)):
                n.inputs[i].default_value = v
            else:
                nt.links.new(v, n.inputs[i])
        return n.outputs[0]

    # gust phase: along the wind (from the south-west), drifting with time; plus a quick flutter
    along = math_('ADD', math_('MULTIPLY', sep.outputs['X'], 0.012), math_('MULTIPLY', sep.outputs['Y'], 0.016))
    gust = math_('SINE', math_('SUBTRACT', along, math_('MULTIPLY', t.outputs[0], 0.9)), 0)
    flutter = math_('SINE', math_('ADD', math_('MULTIPLY', sep.outputs['X'], 0.37),
                                  math_('MULTIPLY', t.outputs[0], 2.7)), 0)
    push = math_('ADD', math_('MULTIPLY', gust, amp), math_('MULTIPLY', flutter, amp * 0.25))
    vec = nt.nodes.new('ShaderNodeCombineXYZ')
    nt.links.new(math_('MULTIPLY', push, 0.6), vec.inputs['X'])
    nt.links.new(math_('MULTIPLY', push, 0.8), vec.inputs['Y'])
    disp = nt.nodes.new('ShaderNodeVectorDisplacement')
    disp.space = 'WORLD'
    nt.links.new(vec.outputs[0], disp.inputs['Vector'])
    nt.links.new(disp.outputs[0], out.inputs['Displacement'])


# ---------------------------------------------------------------- the forest

def tints(rng, n, base, spread):
    """Per-crown colour: a shared base, a little hue and value scatter."""
    v = rng.uniform(1 - spread, 1 + spread, (n, 1))
    hue = rng.normal(0, spread * 0.5, (n, 3)) * np.array([[1.0, 0.4, 1.2]])
    return np.clip(np.array(base)[None, :] * v + hue * 0.3, 0.2, 1.6)


def grow(fence_x, north_y, west_x=-1100.0, far=1450.0, south_y=345.0, seed=11):
    rng = np.random.default_rng(seed)
    ground = Ground(fence_x, north_y, burn_polygons(3.0))
    box = (west_x, far, south_y - 130, far)

    def keep(x, y, inset):
        d = ground.edge_distance(x, y)
        # South of the port road the forest rounds the belt's corner, east of the container yard,
        # its edge as ragged as a clearing that is cut back now and then.
        k = np.clip((x - 660) / 200, 0, 1)
        corner = south_y - 125 * k * k * (3 - 2 * k)
        ragged = 2.5 * ground.wobble(x * 0.8 + 200) + 15 * noise2(x, y, 0.035, 7) + 6 * noise2(x, y, 0.13, 8)
        d = np.where(x > fence_x + 4, np.minimum(d, y - (corner + 10 + ragged)), d)
        ok = (d > inset) & ~ground.in_burn(x, y)
        ok &= ~((x < fence_x + 4) & (y < north_y - 2))
        ok &= ~((x < 625) & (y < 364))                              # the loading strip stays clear
        return ok, d

    gaps = noise2  # the canopy opens where low-frequency noise peaks, onto the understory

    # The canopy: overlapping broadleaf crowns, rising from the edge into full height.
    x, y = scatter(rng, box, 10.5, 3.6)
    ok, d = keep(x, y, 4.0)
    open_ = gaps(x, y, 0.03, 1) + 0.45 * gaps(x, y, 0.11, 2)
    ok &= open_ < 0.85
    x, y, d = x[ok], y[ok], d[ok]
    n = len(x)
    rise = np.clip(d / 45.0, 0, 1) ** 0.7                         # an edge profile, not a cliff
    r = rng.uniform(5.8, 9.5, n) * (0.7 + 0.3 * rise)
    is_palm = rng.random(n) < 0.06 + 0.25 * (1 - rise)
    leaf = rng.choice(4, n, p=(0.36, 0.34, 0.08, 0.22))         # the lime crown sparingly
    cell = np.where(is_palm, PALM * GRID + rng.integers(0, 4, n), BROADLEAF * GRID + leaf)
    canopy = dict(x=x, y=y, r=np.where(is_palm, r * 0.85, r),
                  z=(9 + 19 * rise) + rng.uniform(-2.5, 4.5, n) + 0.4 * r,
                  h=np.where(is_palm, r * 0.35, r * rng.uniform(0.45, 0.65, n)),
                  rot=rng.uniform(0, 2 * np.pi, n), cell=cell,
                  tint=tints(rng, n, (0.66, 0.70, 0.58), 0.18))
    # Some of the canopy sits in shade of its neighbours: darken a few, more toward the gaps.
    canopy['tint'] *= np.clip(1.05 - 0.25 * np.clip(open_[ok], 0, 1.2), 0.7, 1.05)[:, None]

    # Emergents: big multi-clump crowns standing over the canopy.
    x, y = scatter(rng, box, 44.0, 16.0)
    ok, d = keep(x, y, 30.0)
    ok &= rng.random(len(x)) < 0.55
    x, y = x[ok], y[ok]
    n = len(x)
    r = rng.uniform(11, 17, n)
    emergent = dict(x=x, y=y, r=r, z=rng.uniform(38, 47, n), h=r * rng.uniform(0.3, 0.45, n),
                    rot=rng.uniform(0, 2 * np.pi, n), cell=EMERGENT * GRID + rng.integers(0, 4, n),
                    tint=tints(rng, n, (0.72, 0.76, 0.70), 0.12))

    # Understory: under the gaps and along the edge, ferns, palms and young trees.
    x, y = scatter(rng, box, 6.0, 2.6)
    ok, d = keep(x, y, -1.0)
    under_open = gaps(x, y, 0.03, 1) + 0.45 * gaps(x, y, 0.11, 2)
    ok &= (under_open > 0.75) | (d < 30.0)
    x, y, d = x[ok], y[ok], d[ok]
    n = len(x)
    kind = rng.random(n)
    cell = np.where(kind < 0.45, FERN * GRID + rng.integers(0, 4, n),
                    np.where(kind < 0.75, PALM * GRID + rng.integers(0, 4, n), BROADLEAF * GRID + rng.integers(0, 4, n)))
    r = np.where(kind < 0.45, rng.uniform(2.2, 3.8, n), rng.uniform(3.0, 5.5, n))
    edge = np.clip(1 - d / 30.0, 0, 1)
    under = dict(x=x, y=y, r=r, z=rng.uniform(2.5, 6.5, n) + 7 * (1 - edge) + 0.3 * r,
                 h=r * 0.3, rot=rng.uniform(0, 2 * np.pi, n), cell=cell,
                 tint=tints(rng, n, (0.50, 0.56, 0.48), 0.14))

    # The clearing edge itself: a ragged band of ferns and small palms on the cleared side of
    # the trees, overhanging the track and the burnt patches but never the fence.
    x, y = scatter(rng, box, 3.6, 1.6)
    ok, d = keep(x, y, -7.0)
    ok &= d < 6.0
    ok &= rng.random(len(x)) < 0.7
    ok &= ~((x < fence_x + 3.5) & (x > fence_x - 30))             # the fence line and track stay clear
    ok &= ~((y < north_y + 1) & (y > north_y - 30) & (x < fence_x + 4))
    x, y = x[ok], y[ok]
    n = len(x)
    palm = rng.random(n) < 0.35
    r = np.where(palm, rng.uniform(2.5, 4.2, n), rng.uniform(1.4, 2.6, n))
    fringe = dict(x=x, y=y, r=r, z=rng.uniform(0.9, 3.0, n) + np.where(palm, 2.5, 0), h=r * 0.3,
                  rot=rng.uniform(0, 2 * np.pi, n),
                  cell=np.where(palm, PALM * GRID, FERN * GRID) + rng.integers(0, 4, n),
                  tint=tints(rng, n, (0.8, 0.84, 0.72), 0.15))
    return dict(canopy=canopy, emergent=emergent, under=under, fringe=fringe)


def apply(fence_x, north_y):
    """Replace the blockout's ball trees with the layered canopy."""
    for name in ('Jungle canopy', 'Port tree line'):
        ob = bpy.data.objects.get(name)
        if ob is not None:
            bpy.data.objects.remove(ob)
    image = bpy.data.images.load(str(SHEET), check_existing=True)
    image.alpha_mode = 'STRAIGHT'
    layers = grow(fence_x, north_y)
    mats = {'canopy': crown_material('Jungle crowns', image, 0.22),
            'emergent': crown_material('Jungle emergents', image, 0.3),
            'under': crown_material('Jungle understory', image, 0.0),
            'fringe': crown_material('Jungle fringe', image, 0.12)}
    floor = bpy.data.materials.get('Jungle floor')         # the floor between: deep shade, not lawn
    if floor is not None:
        floor.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = (0.025, 0.05, 0.02, 1)
    counts = {}
    for key, crowns in layers.items():
        rings, segs = (4, 12) if key in ('canopy', 'emergent') else (2, 9)
        crowns_mesh('Jungle ' + key, crowns, mats[key], rings, segs)
        counts[key] = len(crowns['x'])
    print('C4_JUNGLE', counts, flush=True)
    return counts

