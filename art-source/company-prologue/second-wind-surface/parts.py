"""Surfacing parts: what is skinned, from where, and which sheet it shares.

Pure data at the top (importable by plain Python); the Blender helpers below
it import bpy lazily, so sheets.py / grade.py can read PARTS without Blender.

A part is one or more MEMBERS. A member is (object name, seed point): the
part's faces on that object are the connected face group, grown from the face
nearest the seed, of faces whose normal lies within the part's cone about its
view direction and which the view sees first (a ray from the camera hits that
face before any other face of the part's objects). Every boundary of such a
group is therefore a crease (the neighbouring face turns out of the cone), an
object edge, or an occlusion edge, never a box cut through a plate. Faces two
parts both qualify for go to the part whose direction they face more squarely.
A seed of None takes every qualifying face of the object instead of one
connected group, and a seed {'box': ((x0, y0, z0), (x1, y1, z1))} every
qualifying face whose centre lies in that world-space box: for an object built
as many separate pieces (a mesh of plates), cut into tiles of a useful size.

Object names are the clean build's stable keys (second-wind-pipeline.md);
duplicates carry Blender's creation-order suffixes (.001, ...), which are
deterministic for one build script.

`skin_from` reuses another part's generation: the member is projected with its
own camera fitted to its own face group, so its UVs land on the same
normalised tile as the source part (identical / near-identical fittings).
`mirror_u: True` with it flips the tile horizontally: a starboard face seen
from starboard is the port face's mirror image, and the starboard camera's
screen-right points the other way along the hull, so a mirrored reuse lines
the port outline up with the starboard one.

Optional per-part keys:
  ray       visibility rays start this far out along the view direction
            (default 300, i.e. outside the ship). A bay interior seen
            through its opening sets it short enough to start inside.
  square    |normal . dir| at and above which the skin is used unblended
            (default SQUARE). Below it the material blends to the graded
            swatch of the face's class, fully by SQUARE - SQUARE_BAND: the
            chamfers and the bevel modifier's strips a square-on camera
            would streak (pilot change 1).

Per-part tone offsets come from variation.json (variation.py), applied in
the part's material, so even two parts sharing one generation differ
(pilot change 2).
"""
import importlib
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))
from parts_common import *  # noqa: E402,F401,F403  direction constants
import hull as H  # noqa: E402  which hull: its folder, part tables and constants (hull.py)

# Each group owns one module, groups/<group>.py in the hull's folder, defining PARTS and SHEETS.
# The rollout fans out one session per group, so a group never edits another
# group's rows; the aggregate below is what every script reads. A group that
# fails to import (mid-edit by its owner) is skipped with a warning rather
# than breaking every other group's run.
GROUPS = H.GROUPS
PARTS, SHEETS, PART_GROUP, SHEET_GROUP = {}, {}, {}, {}
for _g in GROUPS:
    try:
        _m = importlib.import_module(f'groups.{_g}')
    except ModuleNotFoundError as _e:
        if _e.name == f'groups.{_g}':
            continue
        print(f'SURF warning: group {_g} skipped: {_e!r}')
        continue
    except Exception as _e:  # a group mid-edit must not stop the others
        print(f'SURF warning: group {_g} skipped: {_e!r}')
        continue
    for _p, _spec in _m.PARTS.items():
        if _p in PARTS:
            raise SystemExit(f'part {_p} defined by both {PART_GROUP[_p]} and {_g}')
        PARTS[_p] = _spec
        PART_GROUP[_p] = _g
    for _s, _ps in _m.SHEETS.items():
        if _s in SHEETS:
            raise SystemExit(f'sheet {_s} defined by both {SHEET_GROUP[_s]} and {_g}')
        SHEETS[_s] = _ps
        SHEET_GROUP[_s] = _g


def group_arg(argv):
    """`--group a,b` from a script's arguments (None = every group)."""
    return argv[argv.index('--group') + 1].split(',') if '--group' in argv else None


def parts_in(groups):
    return [p for p in PARTS if groups is None or PART_GROUP[p] in groups]


def sheets_in(groups):
    return [s for s in SHEETS if groups is None or SHEET_GROUP[s] in groups]


def tiles_dir(part):
    """build/<group>/tiles: each group's scratch output is its own."""
    d = H.DIR / 'build' / PART_GROUP[part] / 'tiles'
    d.mkdir(parents=True, exist_ok=True)
    return d


SHEET, TILE, GX, GY = (1920, 1080), (912, 513), 32, 18
MARGIN = 0.07           # share of each tile left clear round the part, so segmentation sees background
# Tile background. It must sit far from every paint class: a light plate within
# ~22 of it is segmented as background (imgops.segment), so a near-white hull
# sets its own (the Mudskipper's is mid blue).
BACKGROUND = tuple(H.get('BACKGROUND', (214, 214, 214)))
SQUARE, SQUARE_BAND = 0.86, 0.10   # see `square` above: 30 deg off square-on is pure skin, 45 deg pure swatch
SWATCH_UNITS = H.SWATCH_UNITS              # hull units one swatch image spans (per hull: surface_hull.py)
# Base materials -> palette class. Anything unlisted is not graded.
MATERIAL_CLASS = H.MATERIAL_CLASS
CLASS_ID = H.CLASS_ID



def id_linear(rgb):
    """Emission colour a Standard (sRGB) view writes back as `rgb` in the flat
    class passes: an id component of 128 would otherwise come out 188 and be
    read as another class. 0 and 255 are unchanged. (Blender 5.2 offers no Raw
    view transform, so those passes run Standard.)"""
    return [(c / 255 / 12.92) if c / 255 <= 0.04045 else ((c / 255 + 0.055) / 1.055) ** 2.4 for c in rgb]


def sheet_of(part):
    for s, ps in SHEETS.items():
        if part in ps:
            return s, ps.index(part)
    return None, None


def tile_box(k):
    x = GX + (k % 2) * (TILE[0] + GX)
    y = GY + (k // 2) * (TILE[1] + GY)
    return x, y, x + TILE[0], y + TILE[1]


def source_part(part):
    return PARTS[part].get('skin_from', part)


# ---------------------------------------------------------------- Blender side
def basis(spec):
    from mathutils import Vector
    z = Vector(spec['dir']).normalized()
    up = Vector(spec['up'])
    y = (up - up.dot(z) * z).normalized()
    x = y.cross(z)
    return x, y, z


def select_faces(part, objects=None):
    """{object name: [base polygon index]} for one part's own members, on the
    unmodified base meshes (modifiers stay live for the damage phase)."""
    import bpy
    from mathutils import Vector
    from mathutils.bvhtree import BVHTree
    spec = PARTS[part]
    d = Vector(spec['dir']).normalized()
    out = {}
    for name, seed in spec['members']:
        ob = (objects or bpy.data.objects)[name]
        me, mw = ob.data, ob.matrix_world
        n3 = mw.to_3x3().inverted().transposed()
        verts = [mw @ v.co for v in me.vertices]
        # Visibility is sampled at triangle centroids, not the polygon centre:
        # an L-shaped n-gon's centre can fall in its own hole.
        me.calc_loop_triangles()
        # A hull bent per vertex after it was built (SHEARED_FACES: the
        # fighters' folds) has faces out of plane. The tree then takes the
        # sampled triangles themselves: triangulated along the other diagonal,
        # the surface a ray hits lies off the centroid and a whole wing reads
        # as hidden. Planar hulls keep the polygon tree (byte-identical).
        tris = me.loop_triangles if H.SHEARED_FACES else me.polygons
        bvh = BVHTree.FromPolygons(verts, [list(t.vertices) for t in tris])
        seen, total = {}, {}
        for t in me.loop_triangles:
            c = sum((verts[v] for v in t.vertices), Vector()) / 3
            total[t.polygon_index] = total.get(t.polygon_index, 0) + t.area
            hit = bvh.ray_cast(c + d * spec.get('ray', 300), -d)
            if hit[0] is not None and abs((hit[0] - c).dot(d)) < 0.02:
                seen[t.polygon_index] = seen.get(t.polygon_index, 0) + t.area
        ok = set()
        centres = [mw @ p.center for p in me.polygons]
        for p in me.polygons:
            n = (n3 @ p.normal).normalized()
            if abs(n.dot(d)) >= spec['cone'] and seen.get(p.index, 0) >= 0.5 * total.get(p.index, 1):
                ok.add(p.index)
        if not ok:
            raise SystemExit(f'part {part}: no faces on {name}')
        if seed is None or isinstance(seed, dict):
            # A whole-object member, or the part of it inside an axis-aligned box: every
            # qualifying face (whose centre lies in the box). For a mesh of many separate
            # pieces in one object (the Onslaught's detail plating: dozens of plates per
            # mesh), where one seed would claim a single plate.
            lo, hi = (seed or {}).get('box', ((-1e9,) * 3, (1e9,) * 3))
            out.setdefault(name, set()).update(
                i for i in ok if all(lo[k] <= centres[i][k] <= hi[k] for k in range(3)))
            continue
        seed_v = Vector(seed)
        start = min(ok, key=lambda i: (centres[i] - seed_v).length)
        group, todo = {start}, [start]
        adjacent = _adjacency(me, verts, ok)
        while todo:
            i = todo.pop()
            for j in adjacent[i]:
                if j not in group:
                    group.add(j)
                    todo.append(j)
        out.setdefault(name, set()).update(group)
    return {k: sorted(v) for k, v in out.items()}


def _adjacency(me, verts, faces, eps=1e-3):
    """Faces touching along an edge, including boolean T-junctions where one
    face's corner lies on the other's edge without a shared vertex."""
    adj = {i: set() for i in faces}
    edge_faces = {}
    for i in faces:
        for ek in me.polygons[i].edge_keys:
            edge_faces.setdefault(ek, []).append(i)
    for fs in edge_faces.values():
        for i in fs:
            adj[i].update(f for f in fs if f != i)
    loops = {i: [verts[v] for v in me.polygons[i].vertices] for i in faces}
    boxes = {i: ([min(p[k] for p in loops[i]) - eps for k in range(3)], [max(p[k] for p in loops[i]) + eps for k in range(3)]) for i in faces}

    def on_edges(points, ring):
        for p in points:
            for a, b in zip(ring, ring[1:] + ring[:1]):
                ab = b - a
                t = max(0.0, min(1.0, (p - a).dot(ab) / max(ab.length_squared, 1e-12)))
                if (a + ab * t - p).length < eps:
                    return True
        return False
    fl = list(faces)
    for k, i in enumerate(fl):
        lo, hi = boxes[i]
        for j in fl[k + 1:]:
            if j in adj[i]:
                continue
            lo2, hi2 = boxes[j]
            if any(lo[c] > hi2[c] or lo2[c] > hi[c] for c in range(3)):
                continue
            if on_edges(loops[i], loops[j]) or on_edges(loops[j], loops[i]):
                adj[i].add(j)
                adj[j].add(i)
    return adj


def all_selections(objects=None, strict=None):
    """Every part's faces with overlaps resolved toward the squarer view.

    `strict` is the set of parts whose selection failure stops the run (default
    every part). Another group's part that fails (a seed being authored by its
    owner right now) is dropped with a warning, so it cannot stop this group;
    the integrated run passes strict=None and fails on any of them."""
    import bpy
    from mathutils import Vector
    sel = {}
    for p in PARTS:
        try:
            sel[p] = select_faces(p, objects)
        except (SystemExit, KeyError) as e:
            if strict is None or p in strict:
                raise
            print(f'SURF warning: {p} ({PART_GROUP[p]}) skipped: {e}')
    claims = {}
    for p, per in sel.items():
        d = Vector(PARTS[p]['dir']).normalized()
        for name, idx in per.items():
            ob = (objects or bpy.data.objects)[name]
            n3 = ob.matrix_world.to_3x3().inverted().transposed()
            for i in idx:
                c = abs((n3 @ ob.data.polygons[i].normal).normalized().dot(d))
                key = (name, i)
                if key not in claims or c > claims[key][1]:
                    claims[key] = (p, c)
    return {p: {name: [i for i in idx if claims[(name, i)][0] == p] for name, idx in per.items()} for p, per in sel.items()}


def group_extent(part, faces, objects=None):
    """(centre, width, height, depth range) of a part's faces in its view basis."""
    import bpy
    x, y, z = basis(PARTS[part])
    pts = []
    for name, idx in faces.items():
        ob = (objects or bpy.data.objects)[name]
        mw = ob.matrix_world
        for i in idx:
            pts += [mw @ ob.data.vertices[v].co for v in ob.data.polygons[i].vertices]
    us, vs, ws = [p.dot(x) for p in pts], [p.dot(y) for p in pts], [p.dot(z) for p in pts]
    cu, cv = (min(us) + max(us)) / 2, (min(vs) + max(vs)) / 2
    return dict(cu=cu, cv=cv, w=max(us) - min(us), h=max(vs) - min(vs), wmin=min(ws), wmax=max(ws))


def tile_camera(part, extent):
    """Ortho frame for a part tile: (centre u, v, ortho width) fitting the extent with MARGIN."""
    aspect = TILE[0] / TILE[1]
    width = max(extent['w'], extent['h'] * aspect) / (1 - 2 * MARGIN)
    return extent['cu'], extent['cv'], width


def project_uv(part, cam, point):
    """Tile UV (0..1, v up) of a world point under a part's tile camera."""
    x, y, _ = basis(PARTS[part])
    cu, cv, width = cam
    height = width * TILE[1] / TILE[0]
    u, v = (point.dot(x) - cu) / width + .5, (point.dot(y) - cv) / height + .5
    return (1 - u if PARTS[part].get('mirror_u') else u), v
