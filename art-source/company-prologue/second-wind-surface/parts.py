"""Port-flank surfacing parts: what is skinned, from where, and which sheet it shares.

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

Object names are the clean build's stable keys (second-wind-pipeline.md);
duplicates carry Blender's creation-order suffixes (.001, ...), which are
deterministic for one build script.

`skin_from` reuses another part's generation: the member is projected with its
own camera fitted to its own face group, so its UVs land on the same
normalised tile as the source part (identical / near-identical fittings).
"""
from math import sqrt

HULL = 'Main hull - level datum Z0'
PORT = (0.0, 1.0, 0.0)
UP_Z = (0.0, 0.0, 1.0)
DOWN_X = (1.0, 0.0, 0.0)   # screen-up for top views: bow at the top
BOW_QUARTER = (sqrt(.5), sqrt(.5), 0.0)
CHEEK = (0.50, 0.36, 0.79)  # sw_hull 'Port bow cheek': (.81, .58) at 38 deg below horizontal

# id: dict(members=[(object, seed)], dir, up, cone, material notes)
PARTS = {
    # Sheet A: the forebody and waist flank
    'hull-waist-port': dict(members=[(HULL, (8, 23.2, 7))], dir=PORT, up=UP_Z, cone=.6,
                            what='main hull port waist wall around the open bay'),
    'bow-port-flank': dict(members=[('Forebody armour module.002', (52, 20, 2))], dir=BOW_QUARTER, up=UP_Z, cone=.6,
                           what='bow shield module, port flank and forward rake'),
    'fore-module-1': dict(members=[('Forebody armour module', (32, 31.5, 2))], dir=PORT, up=UP_Z, cone=.6,
                          what='aft forebody armour module, port face (behind the grapple sponson)'),
    'fore-module-2': dict(members=[('Forebody armour module.001', (42, 31.5, 2))], dir=PORT, up=UP_Z, cone=.6,
                          what='forward forebody armour module, port face (behind the grapple sponson)'),
    # Sheet B: the port engine pod's major casing, from port
    'pod-cowl-port': dict(members=[(HULL, (-10, 38.5, 9))], dir=PORT, up=UP_Z, cone=.6,
                          what='port pod armoured cowl, port face (part of the main hull object)'),
    'pod-lower-casing-port': dict(members=[('Drive lower casing', (-10, 38, -2))], dir=PORT, up=UP_Z, cone=.6,
                                  what='port pod steel lower casing'),
    'pod-intake-cap-port': dict(members=[('Drive intake cap', (0, 36.5, 3))], dir=PORT, up=UP_Z, cone=.6,
                                what='port pod intake cap'),
    'pod-thrust-housing-port': dict(members=[('Thrust housing', (-30, 38, 2))], dir=PORT, up=UP_Z, cone=.6,
                                    what='port pod thrust housing'),
    # Sheet C: the grapple sponson boxes, the pod bands and the bow belt
    'sponson-lower': dict(members=[('Grapple sponson aft lower body', (33, 34.3, 1))], dir=PORT, up=UP_Z, cone=.6,
                          what='grapple sponson lower body; the forward body reuses it'),
    'sponson-lower-fwd': dict(members=[('Grapple sponson forward lower body', (43, 34.3, 1))], dir=PORT, up=UP_Z, cone=.6,
                              skin_from='sponson-lower', what='forward sponson lower body (shares sponson-lower)'),
    'sponson-cowl': dict(members=[('Grapple sponson aft cowl', (33, 34.3, 8))], dir=PORT, up=UP_Z, cone=.6,
                         what='grapple sponson cowl; the forward cowl reuses it'),
    'sponson-cowl-fwd': dict(members=[('Grapple sponson forward cowl', (43, 34.3, 8))], dir=PORT, up=UP_Z, cone=.6,
                             skin_from='sponson-cowl', what='forward sponson cowl (shares sponson-cowl)'),
    'pod-bands-port': dict(members=[('Drive reinforcing band', (-17.8, 39, 4)), ('Drive reinforcing band.001', (-26.2, 39, 4))],
                           dir=PORT, up=UP_Z, cone=.6, what='port pod barrel bands, both in one tile'),
    'belt-bow-port': dict(members=[('Forebody armour belt plate.002', (50, 24, -1))], dir=BOW_QUARTER, up=UP_Z, cone=.5,
                          what='steel belt plate on the bow module, port side'),
    # Sheet D: tops and strips
    'pod-cowl-top': dict(members=[(HULL, (-12, 33, 16))], dir=UP_Z, up=DOWN_X, cone=.6,
                         what='port pod cowl top and turret seat (main hull object)'),
    'sponson-cowl-tops': dict(members=[('Grapple sponson aft cowl', (33, 29, 12)), ('Grapple sponson forward cowl', (43, 29, 12))],
                              dir=UP_Z, up=DOWN_X, cone=.6, what='both sponson cowl roofs in one tile'),
    'bow-cheek-port': dict(members=[('Forebody armour module.002', (49, 18, 13))], dir=CHEEK, up=DOWN_X, cone=.85,
                           what='raked port bow cheek'),
    'deck-edge-lip-port': dict(members=[('Deck edge lip', (20, 28.5, 10)), ('Deck edge lip', (32, 32, 10)), ('Deck edge lip', (42, 30, 10))], dir=PORT, up=UP_Z, cone=.5,
                               what='overhanging deck-edge lip, port run'),
}

# One Codex generation per sheet; reading order = tile order (2x2).
SHEETS = {
    'flank': ['hull-waist-port', 'bow-port-flank', 'fore-module-1', 'fore-module-2'],
    'pod': ['pod-cowl-port', 'pod-lower-casing-port', 'pod-intake-cap-port', 'pod-thrust-housing-port'],
    'fittings': ['sponson-lower', 'sponson-cowl', 'pod-bands-port', 'belt-bow-port'],
    'tops': ['pod-cowl-top', 'sponson-cowl-tops', 'bow-cheek-port', 'deck-edge-lip-port'],
}

SHEET, TILE, GX, GY = (1920, 1080), (912, 513), 32, 18
MARGIN = 0.07           # share of each tile left clear round the part, so segmentation sees background
BACKGROUND = (214, 214, 214)
# Base materials -> palette class. Anything unlisted is not graded.
MATERIAL_CLASS = {
    'Chalked cream armor': 'cream',
    'Oxide orange armor': 'oxide',
    'Engine collars': 'gunmetal',
    'Recess and machinery': 'dark',
}
CLASS_ID = {'cream': (255, 0, 0), 'oxide': (0, 255, 0), 'gunmetal': (0, 0, 255), 'dark': (255, 255, 0), 'other': (255, 0, 255)}


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
        polys = [list(p.vertices) for p in me.polygons]
        bvh = BVHTree.FromPolygons(verts, polys)
        # Visibility is sampled at triangle centroids, not the polygon centre:
        # an L-shaped n-gon's centre can fall in its own hole.
        me.calc_loop_triangles()
        seen, total = {}, {}
        for t in me.loop_triangles:
            c = sum((verts[v] for v in t.vertices), Vector()) / 3
            total[t.polygon_index] = total.get(t.polygon_index, 0) + t.area
            hit = bvh.ray_cast(c + d * 300, -d)
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


def all_selections(objects=None):
    """Every part's faces with overlaps resolved toward the squarer view."""
    import bpy
    from mathutils import Vector
    sel = {p: select_faces(p, objects) for p in PARTS}
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
    return (point.dot(x) - cu) / width + .5, (point.dot(y) - cv) / height + .5
