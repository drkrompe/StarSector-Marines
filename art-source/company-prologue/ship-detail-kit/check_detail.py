"""check_detail.py: does a built hull meet the ship detail standard?

Runs inside Blender on a built hull, either an opened .blend or chained after
a build in the same process, and reports what `ship-detail-guide.md` says a
close view must not show:

  curves     round parts with fewer segments than the segment rule asks for
             (ship_detail_kit.segments), from caps (regular n-gons) and from
             runs of shallow parallel creases (lofts and revolves without caps);
  hard       convex edges sharper than 60 deg with no bevel, on faces wider
             than 15 cm (thin bars and rails are exempt);
  emissive   emissive faces outside the lights layer: with --lights the
             sprite's lights layer decides; without one, an emissive object
             must say what it is in its name (lamp, light, lit, lens, beacon,
             glow, strip); engines are exempt;
  glazing    glass or emissive panes with no frame, mullion, rim, surround,
             brow or visor object round them (the "glowing rectangle");
  bare       flat patches bigger than --bare-m2 with nothing on them: no seam,
             step, plate, bolt, vent or fitting within 0.35 m in front of the
             surface (sampled on a 0.5 m grid);
  primitive  bare primitive boxes (>= 0.6 m thick, >= 1.5 m long) and
             cylinders (>= 0.4 m radius) with nothing fixed to them.

Usage:
  blender -b hull.blend --python check_detail.py -- --mpu 0.5 --label Colossus --out report.json
  SDK_CHECK="--mpu 0.5 --label Colossus --out report.json"       blender -b --python build_x.py --python check_detail.py -- <build args>
  (chained after a build, the flags come from $SDK_CHECK: the build's own argparse
  owns the command line). --manifest geometry.json reads metres_per_unit instead of
  --mpu; --lights <sprite_lights.png> with --ship <hull>.ship tests emissives against
  the lights layer; --bare-m2 sets the bare-patch threshold (default 100 m2);
  --exclude '<regex>' leaves objects out (the table in ship-detail-guide.md leaves out
  turrets, which turret-kit owns: --exclude '^(Weapon|Turret) ').

It reports; it never edits the scene. Numbers are in metres at true scale.
"""
import argparse
import json
import math
import os
import re
import shlex
import sys
from collections import defaultdict
from pathlib import Path

import bpy
import numpy as np
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ship_detail_kit import segments, sag, VISIBLE_SAG_M  # noqa: E402

SKIP = re.compile(r'^(Temporary|Studio|Camera|Figure|Scale figure|C2 )', re.I)
LIGHT_NAME = re.compile(r'(?<![a-z])(lamps?|lights?|lit|lens(es)?|beacons?|glow|strips?)(?![a-z])', re.I)
ENGINE_NAME = re.compile(r'^(Engine|Lift thruster|Thruster)\b|exhaust|throat|plume', re.I)
GLASS_NAME = re.compile(r'(glass|glazing|windows?|panes?|windscreen|viewports?|skylights?)', re.I)
FRAME_NAME = re.compile(r'(frames?|mullions?|surrounds?|rims?|brow|visor|eyebrows?|bezel|sill|coamings?|plates|guards?|housings?)', re.I)

SOFT = (2.0, 32.0)        # degrees: a crease this shallow is a facet of a curve, not a designed facet
HARD = 60.0               # degrees: a convex edge this sharp wants a bevel
PLATE_W = .5              # metres: ... when both faces are at least this wide (members under it may stay crisp)
TOL = .8                  # a curve passes within 20% of the segment rule
BOLT_R = .15              # metres: hexagonal bolt and nut heads up to this radius are exempt
HULL_R = 5.0              # metres: rounds bigger than this may be built as armour facets (reported, not failed)
ROUND = 75.0              # degrees: a crease run turning this far is a rounded form
GRID = .5                 # metres: bare-patch sampling pitch
TOUCH = .35               # metres: detail this close in front of a surface counts
DARK = .06                # base colour (linear, brightest channel) under which a bevel highlight cannot read


def parse():
    """Flags from SDK_CHECK (chained after a build, whose own argparse owns the command line)
    or from after `--` (an opened .blend)."""
    env = os.environ.get('SDK_CHECK')
    argv = shlex.split(env, posix=True) if env else (sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
    p = argparse.ArgumentParser(prog='check_detail')
    p.add_argument('--mpu', type=float)
    p.add_argument('--manifest')
    p.add_argument('--label')
    p.add_argument('--out', dest='outp')
    p.add_argument('--lights')
    p.add_argument('--ship')
    p.add_argument('--bare-m2', type=float, dest='bare', default=100.0)
    p.add_argument('--exclude', help='regex of object names to leave out (e.g. turrets owned by turret-kit)')
    a, _ = p.parse_known_args(argv)
    if a.mpu is None and a.manifest:
        a.mpu = json.loads(Path(a.manifest).read_text(encoding='utf-8'))['metres_per_unit']
    if a.mpu is None:
        sys.exit('check_detail: give --mpu or --manifest (metres per unit)')
    a.label = a.label or Path(bpy.data.filepath).stem or 'hull'
    return a


# --- scene to arrays --------------------------------------------------------------------

class Part:
    """One evaluated mesh object in world metres."""

    def __init__(self, ob, dg, mpu):
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        me.transform(Matrix.Scale(mpu, 4) @ ob.matrix_world)
        nv, npl, nl, ne = len(me.vertices), len(me.polygons), len(me.loops), len(me.edges)
        self.name = ob.name
        self.v = np.empty(nv*3)
        me.vertices.foreach_get('co', self.v)
        self.v.shape = (nv, 3)
        self.n = np.empty(npl*3)
        me.polygons.foreach_get('normal', self.n)
        self.n.shape = (npl, 3)
        self.c = np.empty(npl*3)
        me.polygons.foreach_get('center', self.c)
        self.c.shape = (npl, 3)
        self.area = np.empty(npl)
        me.polygons.foreach_get('area', self.area)
        self.ls = np.empty(npl, dtype=np.int64)
        me.polygons.foreach_get('loop_start', self.ls)
        self.lt = np.empty(npl, dtype=np.int64)
        me.polygons.foreach_get('loop_total', self.lt)
        self.smooth = np.empty(npl, dtype=bool)
        me.polygons.foreach_get('use_smooth', self.smooth)
        self.mi = np.empty(npl, dtype=np.int64)
        me.polygons.foreach_get('material_index', self.mi)
        self.lv = np.empty(nl, dtype=np.int64)
        me.loops.foreach_get('vertex_index', self.lv)
        self.le = np.empty(nl, dtype=np.int64)
        me.loops.foreach_get('edge_index', self.le)
        self.ev = np.empty(ne*2, dtype=np.int64)
        me.edges.foreach_get('vertices', self.ev)
        self.ev.shape = (ne, 2)
        self.mats = [ms.material for ms in ob.material_slots]
        ev.to_mesh_clear()
        self.polys = [self.lv[s:s+t] for s, t in zip(self.ls, self.lt)]
        self.lp = np.repeat(np.arange(npl), self.lt)
        # Manifold edges: their two faces.
        order = np.argsort(self.le, kind='stable')
        le, lp = self.le[order], self.lp[order]
        cnt = np.bincount(le, minlength=ne)
        first = np.concatenate(([0], np.cumsum(cnt)[:-1]))
        two = np.nonzero(cnt == 2)[0]
        self.e2 = two
        self.f1 = lp[first[two]]
        self.f2 = lp[first[two]+1]
        d = np.einsum('ij,ij->i', self.n[self.f1], self.n[self.f2]).clip(-1, 1)
        self.ang = np.degrees(np.arccos(d))


def base_value(mat):
    """Brightest channel of a material's base colour (1.0 when unknown, so it is checked)."""
    if mat is None:
        return 1.0
    col = mat.diffuse_color
    if mat.node_tree:
        for nd in mat.node_tree.nodes:
            if nd.type == 'BSDF_PRINCIPLED':
                if nd.inputs['Base Color'].is_linked:
                    return 1.0
                col = nd.inputs['Base Color'].default_value
                break
    return max(col[0], col[1], col[2])


def emissive(mat):
    """Emission strength x colour brightness of a material (0 when it does not glow)."""
    if mat is None or not mat.node_tree:
        return 0.0
    best = 0.0
    for nd in mat.node_tree.nodes:
        if nd.type == 'BSDF_PRINCIPLED':
            col = nd.inputs['Emission Color'].default_value
            s = nd.inputs['Emission Strength'].default_value
            if nd.inputs['Emission Strength'].is_linked or nd.inputs['Emission Color'].is_linked:
                s = max(s, 1.0)
            best = max(best, s*max(col[0], col[1], col[2]))
        elif nd.type == 'EMISSION':
            col = nd.inputs['Color'].default_value
            best = max(best, nd.inputs['Strength'].default_value*max(col[0], col[1], col[2]))
    return best


# --- 1. faceted curves ----------------------------------------------------------------

def check_curves(i, p, world):
    found = []
    soft = np.nonzero((p.ang >= SOFT[0]) & (p.ang <= SOFT[1]))[0]
    by_face = defaultdict(list)
    for k in soft:
        by_face[p.f1[k]].append(k)
        by_face[p.f2[k]].append(k)
    flagged = {}
    for f, ks in by_face.items():
        if len(ks) < 2:
            continue
        for i in range(len(ks)):
            for j in range(i+1, len(ks)):
                a, b = ks[i], ks[j]
                ea, eb = p.ev[p.e2[a]], p.ev[p.e2[b]]
                da = p.v[ea[1]]-p.v[ea[0]]
                db = p.v[eb[1]]-p.v[eb[0]]
                la, lb = np.linalg.norm(da), np.linalg.norm(db)
                if la < 1e-6 or lb < 1e-6 or abs(np.dot(da, db))/(la*lb) < .97:
                    continue
                aa, ab = p.ang[a], p.ang[b]
                if abs(aa-ab) > .3*max(aa, ab):
                    continue
                mid = (p.v[eb[0]]+p.v[eb[1]])/2-(p.v[ea[0]]+p.v[ea[1]])/2
                width = np.linalg.norm(np.cross(mid, da/la))
                alpha = math.radians((aa+ab)/2)
                r = width/(2*math.sin(alpha/2))
                n_eff = 360.0/math.degrees(alpha)
                need = segments(r)
                if n_eff < need*TOL:
                    for k in (a, b):
                        flagged[k] = (r, n_eff, need, la if k == a else lb, bool(p.smooth[f]))
    # caps: near-regular n-gons
    for f, poly in enumerate(p.polys):
        k = len(poly)
        if k < 5:
            continue
        pts = p.v[poly]
        rad = np.linalg.norm(pts-p.c[f], axis=1)
        if rad.mean() < 1e-4 or rad.std()/rad.mean() > .03:
            continue
        edges = np.linalg.norm(pts-np.roll(pts, 1, axis=0), axis=1)
        if edges.std()/edges.mean() > .1:
            continue
        r = rad.mean()
        if (k == 6 and r <= BOLT_R) or (k == 8 and r < .05):
            continue       # hexagon bolt heads and thin rods are exempt by the rule
        need = segments(r)
        if k < need*TOL:
            found.append({'kind': 'cap', 'radius_m': round(r, 3), 'segments': k, 'needs': need,
                          'sag_mm': round(1000*sag(r, k), 1), 'visible': bool(sag(r, k) > VISIBLE_SAG_M)})
    # Group the flagged creases into runs (faces joined across them). A run whose faces turn
    # through at least ROUND deg is a rounded form drawn with too few segments; a gentler run
    # is a planform or plate break along a long body, which is a design choice, not a curve.
    parent = {}

    def find(x):
        parent.setdefault(x, x)
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x
    for k in flagged:
        a, b = find(p.f1[k]), find(p.f2[k])
        if a != b:
            parent[a] = b
    runs = defaultdict(list)
    for k in flagged:
        runs[find(p.f1[k])].append(k)
    for ks in runs.values():
        faces = list({int(p.f1[k]) for k in ks} | {int(p.f2[k]) for k in ks})
        nn = p.n[faces]
        a0 = np.degrees(np.arccos((nn @ nn[0]).clip(-1, 1)))
        far = nn[int(a0.argmax())]
        spread = float(np.degrees(np.arccos((nn @ far).clip(-1, 1))).max())
        length = sum(flagged[k][3] for k in ks)
        if spread < ROUND or length < 1.0:
            continue
        sample = faces[::max(1, len(faces)//40)]
        if sum(world.covered(p.c[f], p.n[f]) for f in sample) >= .7*len(sample):
            continue        # plated over: the facets are a frame under separate plates (the Legion dome)
        worst = min((flagged[k] for k in ks), key=lambda t: t[1]/t[2])
        found.append({'kind': 'crease run' if worst[0] <= HULL_R else 'hull facets', 'edges': len(ks), 'length_m': round(length, 1),
                      'turn_deg': round(spread), 'radius_m': round(worst[0], 3), 'segments': round(worst[1]),
                      'needs': worst[2], 'smooth_shaded': sum(flagged[k][4] for k in ks),
                      'sag_mm': round(1000*sag(worst[0], worst[1]), 1), 'visible': bool(sag(worst[0], worst[1]) > VISIBLE_SAG_M)})
    return found


# --- 2. hard edges ------------------------------------------------------------------------

def check_hard(p):
    hard = np.nonzero(p.ang >= HARD)[0]
    if not len(hard):
        return 0.0
    f1, f2 = p.f1[hard], p.f2[hard]
    convex = np.einsum('ij,ij->i', p.c[f2]-p.c[f1], p.n[f1]) < -1e-5
    ev = p.ev[p.e2[hard]]
    length = np.linalg.norm(p.v[ev[:, 1]]-p.v[ev[:, 0]], axis=1)
    w1 = p.area[f1]/np.maximum(length, 1e-6)
    w2 = p.area[f2]/np.maximum(length, 1e-6)
    keep = convex & (length >= PLATE_W) & (w1 >= PLATE_W) & (w2 >= PLATE_W)
    dark = np.array([base_value(m) < DARK for m in p.mats] or [False])
    mi = p.mi.clip(0, len(dark)-1)
    keep &= ~(dark[mi[f1]] & dark[mi[f2]])         # a chamfer on near-black recess catches no highlight
    return float(length[keep].sum())


# --- 3. emissives and glazing -------------------------------------------------------------

class LightsLayer:
    """A sprite lights layer mapped to hull units through the .ship centre."""

    def __init__(self, png, ship):
        img = bpy.data.images.load(png)
        self.w, self.h = img.size
        px = np.empty(self.w*self.h*4, dtype=np.float32)
        img.pixels.foreach_get(px)
        alpha = px.reshape(self.h, self.w, 4)[::-1, :, 3]   # row 0 at the top
        lit = alpha > .05
        grown = lit.copy()                                   # 2 px of slack
        for dy in (-2, -1, 0, 1, 2):
            for dx in (-2, -1, 0, 1, 2):
                grown |= np.roll(np.roll(lit, dy, 0), dx, 1)
        self.mask = grown
        centre = json.loads(Path(ship).read_text(encoding='utf-8'))['center']
        self.cx, self.cy = centre

    def lit(self, x, y):
        """Model X (bow) / Y (port) in hull units -> is the layer lit there?"""
        r = int(math.floor((self.h-self.cy)-x))
        c = int(math.floor(self.cx-y))
        return 0 <= r < self.h and 0 <= c < self.w and bool(self.mask[r, c])


def check_emissive(p, layer, mpu):
    out = []
    glow = {i for i, m in enumerate(p.mats) if emissive(m) > 0}
    if not glow:
        return out
    faces = np.nonzero(np.isin(p.mi, list(glow)))[0]
    area = float(p.area[faces].sum())
    if ENGINE_NAME.search(p.name):
        return out
    if layer:
        on = sum(p.area[f] for f in faces if layer.lit(p.c[f][0]/mpu, p.c[f][1]/mpu))
        if on < .5*area:
            out.append({'area_m2': round(area, 2), 'on_layer_share': round(on/area if area else 0, 2)})
    elif not LIGHT_NAME.search(p.name):
        out.append({'area_m2': round(area, 2), 'on_layer_share': None})
    return out


def bbox(p):
    return p.v.min(axis=0), p.v.max(axis=0)


def check_glazing(parts, every):
    out = []
    framers = [(q.name, *bbox(q)) for q in every if FRAME_NAME.search(q.name)]
    for p in parts:
        is_glass = GLASS_NAME.search(p.name) or any(m and GLASS_NAME.search(m.name) for m in p.mats)
        glows = any(emissive(m) > 0 for m in p.mats)
        if not is_glass or ENGINE_NAME.search(p.name) or (glows and LIGHT_NAME.search(p.name) and
                                                          not GLASS_NAME.search(p.name)):
            continue
        lo, hi = bbox(p)
        lo, hi = lo-.3, hi+.3
        framed = any(n != p.name and (flo <= hi).all() and (fhi >= lo).all() for n, flo, fhi in framers)
        if not framed:
            out.append({'object': p.name, 'emissive': glows})
    return out


# --- 4. bare patches and 5. bare primitives -----------------------------------------------

class World:
    """One BVH over every checked part, with face -> (part, face) lookup."""

    def __init__(self, parts):
        verts, polys, owner = [], [], []
        off = 0
        for i, p in enumerate(parts):
            verts.append(p.v)
            polys += [tuple(int(x)+off for x in poly) for poly in p.polys]
            owner += [(i, f) for f in range(len(p.polys))]
            off += len(p.v)
        allv = np.concatenate(verts) if verts else np.zeros((0, 3))
        self.tree = BVHTree.FromPolygons([Vector(v) for v in allv], polys)
        self.owner = owner

    def touched(self, point, normal, mine):
        """Anything not in `mine` (set of (part, face)) within TOUCH in front of point, or
        the point covered within OVERHANG (hidden under a cover, inside a bay)?"""
        q = Vector(point)+Vector(normal)*.03
        for co, _, idx, _ in self.tree.find_nearest_range(q, TOUCH):
            if self.owner[idx] in mine:
                continue
            if (co-Vector(point)).dot(Vector(normal)) > -.01:
                return True
        # A surface that cannot see open space along its normal is covered, overhung, inside a
        # bay or embedded in another solid: whatever it is, it is not a bare face on show.
        return self.tree.ray_cast(q, Vector(normal))[0] is not None

    def covered(self, point, normal, reach=.6):
        """Is the surface directly overlaid within `reach` (a plate laid over a frame)?"""
        return self.tree.ray_cast(Vector(point)+Vector(normal)*.02, Vector(normal), reach)[0] is not None


def _regions(p, min_area):
    """Coplanar connected face groups of at least min_area m2."""
    parent = list(range(len(p.polys)))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a
    flat = np.nonzero(p.ang < .5)[0]
    off = np.abs(np.einsum('ij,ij->i', p.c[p.f2[flat]]-p.c[p.f1[flat]], p.n[p.f1[flat]]))
    flat = flat[(off < .01) & (p.area[p.f1[flat]] > 1e-6) & (p.area[p.f2[flat]] > 1e-6)]   # same plane, no slivers
    for k in flat:
        a, b = find(p.f1[k]), find(p.f2[k])
        if a != b:
            parent[a] = b
    groups = defaultdict(list)
    for f in range(len(p.polys)):
        groups[find(f)].append(f)
    return [g for g in groups.values() if p.area[g].sum() >= min_area]


def check_bare(i, p, world, min_area):
    """Largest bare patch (m2) per coplanar region bigger than min_area."""
    patches = []
    for g in _regions(p, min_area):
        nrm = p.n[g[0]]
        u = np.cross(nrm, [0, 0, 1] if abs(nrm[2]) < .9 else [1, 0, 0])
        u /= np.linalg.norm(u)
        w = np.cross(nrm, u)
        mine = {(i, f) for f in g}
        cells = {}
        for f in g:
            pts = p.v[p.polys[f]]
            uv = np.stack([pts @ u, pts @ w], axis=1)
            for t in range(1, len(pts)-1):             # fan triangles, rasterised on the grid
                tri = uv[[0, t, t+1]]
                lo = np.floor(tri.min(axis=0)/GRID).astype(int)
                hi = np.ceil(tri.max(axis=0)/GRID).astype(int)
                (x0, y0), (x1, y1), (x2, y2) = tri
                den = (y1-y2)*(x0-x2)+(x2-x1)*(y0-y2)
                if abs(den) < 1e-9:
                    continue
                for a in range(lo[0], hi[0]+1):
                    for b in range(lo[1], hi[1]+1):
                        if (a, b) in cells:
                            continue
                        x, y = a*GRID, b*GRID
                        l0 = ((y1-y2)*(x-x2)+(x2-x1)*(y-y2))/den
                        l1 = ((y2-y0)*(x-x2)+(x0-x2)*(y-y2))/den
                        if l0 >= -1e-6 and l1 >= -1e-6 and 1-l0-l1 >= -1e-6:
                            pt = pts[0]*l0+pts[t]*l1+pts[t+1]*(1-l0-l1)
                            cells[(a, b)] = not world.touched(pt, nrm, mine)
        seen, best, where = set(), 0, None
        for c, empty in cells.items():          # largest 4-connected cluster of bare samples
            if not empty or c in seen:
                continue
            stack, size, members = [c], 0, []
            seen.add(c)
            while stack:
                a, b = stack.pop()
                size += 1
                members.append((a, b))
                for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nb = (a+d[0], b+d[1])
                    if nb not in seen and cells.get(nb):
                        seen.add(nb)
                        stack.append(nb)
            if size > best:
                best, where = size, members
        area = best*GRID*GRID
        if area >= min_area:
            ua, wa = (sum(m[0] for m in where)/len(where)*GRID, sum(m[1] for m in where)/len(where)*GRID)
            off = float(np.dot(p.c[g[0]], nrm))
            centre = u*ua+w*wa+nrm*off
            patches.append({'area_m2': round(area, 1), 'centre_m': [round(float(x), 1) for x in centre],
                            'normal': [round(float(x), 2) for x in nrm]})
    return patches


def check_primitives(i, p, world, ob, mpu):
    """Bare boxes and cylinders among the object's own (unevaluated) connected parts."""
    out = []
    me = ob.data
    if p.mats and all(base_value(m) < DARK for m in p.mats):
        return out                                  # a bare box in near-black recess reads as shadow
    nv = len(me.vertices)
    if nv == 0 or nv > 200000:
        return out
    parent = list(range(nv))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a
    for e in me.edges:
        a, b = find(e.vertices[0]), find(e.vertices[1])
        if a != b:
            parent[a] = b
    comp_faces = defaultdict(list)
    for poly in me.polygons:
        comp_faces[find(poly.vertices[0])].append(poly)
    mw = Matrix.Scale(mpu, 4) @ ob.matrix_world
    for root, polys in comp_faces.items():
        k = len(polys)
        if k == 6 and all(len(q.vertices) == 4 for q in polys):
            vs = {vi for q in polys for vi in q.vertices}
            if len(vs) != 8:
                continue
            nrm = [(mw.to_3x3() @ q.normal).normalized() for q in polys]
            if any(abs(a.dot(b)) > .02 and abs(abs(a.dot(b))-1) > .02 for a in nrm for b in nrm):
                continue
            pts = [mw @ me.vertices[v].co for v in vs]
            dims = sorted(_extent(pts, nrm))
            if dims[0] < .6 or dims[2] < 1.5:
                continue
            kind = 'box'
        elif k >= 7:
            caps = [q for q in polys if len(q.vertices) >= 5]
            if len(caps) != 2 or len(polys) != 2+len(caps[0].vertices):
                continue
            pts = [mw @ me.vertices[v].co for v in caps[0].vertices]
            c = sum(pts, Vector())/len(pts)
            r = sum((q-c).length for q in pts)/len(pts)
            if r < .4:
                continue
            kind = 'cylinder'
            nrm = None
        else:
            continue
        own = BVHTree.FromPolygons([mw @ me.vertices[v].co for v in range(nv)], [tuple(q.vertices) for q in polys])
        touched = 0
        for q in polys:
            cw = mw @ q.center
            nw = (mw.to_3x3() @ q.normal).normalized()
            if _touched_face(world, own, cw, nw):
                touched += 1
        if touched <= 1:
            out.append(kind)
    return out


def _touched_face(world, own, point, normal):
    """Something other than this component within TOUCH in front of a face centre."""
    q = point+normal*.03
    for co, _, idx, _ in world.tree.find_nearest_range(q, TOUCH):
        near = own.find_nearest(co, 1e-3)
        if near[0] is not None:
            continue
        if (co-point).dot(normal) > -.01:
            return True
    return False


def _extent(pts, nrm):
    axes = []
    for a in nrm:
        if not any(abs(abs(a.dot(b))-1) < .02 for b in axes):
            axes.append(a)
    return [max(p.dot(a) for p in pts)-min(p.dot(a) for p in pts) for a in axes[:3]]


# --- driver -------------------------------------------------------------------------------

def run(args):
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    obs = [ob for ob in bpy.context.scene.objects
           if ob.type == 'MESH' and not ob.hide_render and not SKIP.match(ob.name) and ob.visible_get()]
    every = [Part(ob, dg, args.mpu) for ob in obs]
    world = World(every)              # excluded parts still cover, touch and shade what is checked
    keep = [k for k, ob in enumerate(obs) if not (args.exclude and re.search(args.exclude, ob.name))]
    obs, parts = [obs[k] for k in keep], [every[k] for k in keep]
    layer = LightsLayer(args.lights, args.ship) if args.lights and args.ship else None
    report = {'label': args.label, 'metres_per_unit': args.mpu, 'objects': len(parts), 'excluded': args.exclude,
              'faces': int(sum(len(p.polys) for p in parts)), 'lights_layer': bool(layer),
              'surface_m2': round(float(sum(p.area.sum() for p in parts))),
              'rules': {'segments': 'chord sag <= 5 mm, multiple of 4, 12-96 (8 under 5 cm, hex bolts 6)',
                        'visible_sag_mm': 1000*VISIBLE_SAG_M, 'dark_base': DARK,
                        'hard_edge_deg': HARD, 'plate_edge_m': PLATE_W, 'tolerance': TOL, 'bare_patch_m2': args.bare, 'touch_m': TOUCH, 'grid_m': GRID},
              'curves': {}, 'hard_m': {}, 'emissive': {}, 'bare': {}, 'primitives': {}}
    for i, ob, p in zip(keep, obs, parts):          # i indexes the world's parts
        c = check_curves(i, p, world)
        if c:
            report['curves'][p.name] = c
        h = check_hard(p)
        if h >= 1.0:
            report['hard_m'][p.name] = round(h, 1)
        e = check_emissive(p, layer, args.mpu)
        if e:
            report['emissive'][p.name] = e[0]
        b = check_bare(i, p, world, args.bare)
        if b:
            report['bare'][p.name] = b
        pr = check_primitives(i, p, world, ob, args.mpu)
        if pr:
            report['primitives'][p.name] = pr
    report['glazing'] = check_glazing(parts, every)
    s = summarise(report)
    report['summary'] = s
    print('CHECK_DETAIL', json.dumps({'label': args.label, **s}))
    if args.outp:
        Path(args.outp).write_text(json.dumps(report, indent=1)+'\n', encoding='utf-8')
    return report


def summarise(r):
    curves = r['curves']
    caps = sum(1 for v in curves.values() for c in v if c['kind'] == 'cap')
    runs = sum(1 for v in curves.values() for c in v if c['kind'] == 'crease run')
    hull = sum(1 for v in curves.values() for c in v if c['kind'] == 'hull facets')
    faceted = sum(1 for v in curves.values() if any(c['kind'] != 'hull facets' and c['visible'] for c in v))
    below = sum(1 for v in curves.values() if any(c['kind'] != 'hull facets' for c in v))
    bare = [a['area_m2'] for v in r['bare'].values() for a in v]
    prim = [k for v in r['primitives'].values() for k in v]
    return {
        'objects': r['objects'], 'faces': r['faces'],
        'faceted_objects': faceted, 'below_rule_objects': below, 'faceted_caps': caps, 'faceted_runs': runs, 'hull_facet_runs': hull,
        'unbevelled_objects': len(r['hard_m']), 'unbevelled_m': round(sum(r['hard_m'].values()), 1),
        'unbevelled_m_per_100m2': round(100*sum(r['hard_m'].values())/max(r['surface_m2'], 1), 2),
        'emissive_outside': len(r['emissive']),
        'emissive_outside_m2': round(sum(v['area_m2'] for v in r['emissive'].values()), 1),
        'unframed_glazing': len(r['glazing']),
        'bare_patches': len(bare), 'largest_bare_m2': max(bare) if bare else 0,
        'bare_boxes': prim.count('box'), 'bare_cylinders': prim.count('cylinder'),
    }


if __name__ == '__main__':
    run(parse())
