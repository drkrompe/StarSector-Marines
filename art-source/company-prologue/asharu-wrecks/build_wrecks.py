"""Builds the Asharu wreck kit: every variant in wk_catalogue as one mesh object in the collection
`ASHARU WRECKS`, centred on its bounding box, in metres, long axis X, middle Y, short Z.

  sh ../../blender.sh --background --factory-startup --python build_wrecks.py -- [--only NAME,...]
      [--no-hulks] [--sources DIR] [--out DIR] [--no-save]

Writes, under --out (default build/asharu-wrecks/kit, ignored):
  asharu-wrecks.blend   the kit
  manifest.json         the publish manifest (asset, sha256, collection, variants)
  validation.json       the checks below, and whether they passed
and rewrites catalogue.json beside this script (committed: the contract wreck_kit.pick reads).

Checks: every variant is present, centred and the size the catalogue says; no emissive
material, no light; no loose vertex outside the bounds; every mesh carries wk_base and wk_tone.

Hulks are derived from the accepted builds in wk_catalogue.SOURCES, copied local under --sources
(default build/asharu-wrecks/src/<key>/<commit>/; fetch_sources.py copies them off Drive)."""
import argparse
import hashlib
import json
import math
import sys
import time
from pathlib import Path

import bmesh
import bpy

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import wk_catalogue as C  # noqa: E402
import wk_families as F  # noqa: E402
import wk_mat  # noqa: E402

REPO = HERE.parents[2]
argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
ap = argparse.ArgumentParser()
ap.add_argument('--only', default='')
ap.add_argument('--no-hulks', action='store_true')
ap.add_argument('--sources', default=str(REPO / 'build' / 'asharu-wrecks' / 'src'))
ap.add_argument('--out', default=str(REPO / 'build' / 'asharu-wrecks' / 'kit'))
ap.add_argument('--no-save', action='store_true')
a = ap.parse_args(argv)
only = set(filter(None, a.only.split(',')))
OUT = Path(a.out)
OUT.mkdir(parents=True, exist_ok=True)
COLL = 'ASHARU WRECKS'
BEVEL_M = .08


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def to_object(name, m, mats):
    """A wk_geom.Mesh as a Blender object: materials by slot, wk_base / wk_tone per face, an
    angle-limited bevel applied, normals recalculated outward per part."""
    me = bpy.data.meshes.new(name)
    me.from_pydata(m.verts, [], m.faces)
    me.validate(clean_customdata=False)
    for mat in mats:
        me.materials.append(mat)
    me.polygons.foreach_set('material_index', m.mats)
    base = me.attributes.new('wk_base', 'FLOAT_COLOR', 'FACE')
    base.data.foreach_set('color', [c for col in m.bases for c in (*col, 1.0)])
    tone = me.attributes.new('wk_tone', 'FLOAT', 'FACE')
    tone.data.foreach_set('value', m.tones)
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=.002)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    bev = ob.modifiers.new('bevel', 'BEVEL')
    bev.width, bev.segments, bev.limit_method = BEVEL_M, 1, 'ANGLE'
    bev.angle_limit = math.radians(35)
    bev.harden_normals = False
    apply_modifiers(ob)
    return ob


def apply_modifiers(ob):
    dg = bpy.context.evaluated_depsgraph_get()
    ev = ob.evaluated_get(dg)
    me = bpy.data.meshes.new_from_object(ev, preserve_all_data_layers=True, depsgraph=dg)
    old = ob.data
    ob.modifiers.clear()
    ob.data = me
    me.name = old.name
    bpy.data.meshes.remove(old)


def recentre(ob):
    """Moves the mesh so its bounds are centred on the origin; returns (half extents)."""
    me = ob.data
    n = len(me.vertices)
    co = [0.0] * (3 * n)
    me.vertices.foreach_get('co', co)
    lo = [min(co[k::3]) for k in range(3)]
    hi = [max(co[k::3]) for k in range(3)]
    c = [(lo[k] + hi[k]) / 2 for k in range(3)]
    for k in range(3):
        co[k::3] = [v - c[k] for v in co[k::3]]
    me.vertices.foreach_set('co', co)
    me.update()
    return tuple(round((hi[k] - lo[k]) / 2, 3) for k in range(3))


def sort_axes(ob):
    """Turns the mesh (a proper rotation) so its long axis is X, middle Y, short Z."""
    me = ob.data
    n = len(me.vertices)
    co = [0.0] * (3 * n)
    me.vertices.foreach_get('co', co)
    ext = [max(co[k::3]) - min(co[k::3]) for k in range(3)]
    order = sorted(range(3), key=lambda k: -ext[k])
    perm = [[1.0 if order[r] == c else 0.0 for c in range(3)] for r in range(3)]
    det = (perm[0][0] * (perm[1][1] * perm[2][2] - perm[1][2] * perm[2][1])
           - perm[0][1] * (perm[1][0] * perm[2][2] - perm[1][2] * perm[2][0])
           + perm[0][2] * (perm[1][0] * perm[2][1] - perm[1][1] * perm[2][0]))
    if det < 0:
        perm[2] = [-v for v in perm[2]]
    from mathutils import Matrix
    me.transform(Matrix(perm).to_4x4())
    me.update()


def main():
    t_all = time.time()
    reset()
    mats = wk_mat.materials()
    coll = bpy.data.collections.new(COLL)
    bpy.context.scene.collection.children.link(coll)
    rows = []
    for name, fam, size, seed, liv in C.PROCEDURAL:
        if only and name not in only:
            continue
        t = time.time()
        m = F.BUILDERS[fam](size, seed, F.LIVERIES[liv])
        ob = to_object('Wreck ' + name, m, mats)
        sort_axes(ob)
        half = recentre(ob)
        rows.append(dict(name=name, object=ob.name, family=fam, half=half, seed=seed, livery=liv,
                         source=None, verts=len(ob.data.vertices), faces=len(ob.data.polygons)))
        print('WRECK %-22s %-9s half %-24s verts %7d  %.1fs' % (name, fam, half, rows[-1]['verts'], time.time() - t),
              flush=True)
    if not a.no_hulks:
        import wk_hulks
        for name, key, span, torn, seed in C.HULKS:
            if only and name not in only:
                continue
            t = time.time()
            ob, info = wk_hulks.derive(name, key, span, torn, seed, Path(a.sources), mats)
            sort_axes(ob)
            half = recentre(ob)
            rows.append(dict(name=name, object=ob.name, family='hulk', half=half, seed=seed, livery=None,
                             source=info, verts=len(ob.data.vertices), faces=len(ob.data.polygons)))
            print('WRECK %-22s %-9s half %-24s verts %7d  %.1fs' % (name, 'hulk', half, rows[-1]['verts'],
                                                                     time.time() - t), flush=True)
    for r in rows:
        ob = bpy.data.objects[r['object']]
        for c in list(ob.users_collection):
            c.objects.unlink(ob)
        coll.objects.link(ob)
        ob['wk_family'], ob['wk_name'] = r['family'], r['name']
        ob['wk_half'] = list(r['half'])
    # lay the kit out on a line for anyone opening the file; the matrix is never part of the contract
    x = 0.0
    for r in rows:
        ob = bpy.data.objects[r['object']]
        x += r['half'][0] + 20
        ob.location = (x, 0, 0)
        x += r['half'][0]
    checks = validate(rows)
    if only:
        print('ONLY', sorted(only), '- catalogue.json is not written for a partial build')
        print('VALID', checks['passed'], json.dumps(checks['failures'])[:2000])
        if not a.no_save:
            bpy.ops.wm.save_as_mainfile(filepath=str(OUT / 'asharu-wrecks-partial.blend'))
        return
    cat = dict(written_by='build_wrecks.py', units='metres', axes='long X, middle Y, short Z; centred on bounds',
               collection=COLL, variants=[{k: r[k] for k in ('name', 'object', 'family', 'half', 'source')} for r in rows])
    (HERE / 'catalogue.json').write_text(json.dumps(cat, indent=1) + '\n')
    (OUT / 'validation.json').write_text(json.dumps(checks, indent=1) + '\n')
    print('VALID', checks['passed'], json.dumps(checks['failures'])[:2000])
    if a.no_save:
        return
    blend = OUT / 'asharu-wrecks.blend'
    bpy.ops.file.pack_all()
    bpy.ops.wm.save_as_mainfile(filepath=str(blend), compress=True)
    man = dict(asset='asharu-wrecks.blend', asset_sha256=hashlib.sha256(blend.read_bytes()).hexdigest(),
               append_collection=COLL, units='metres', catalogue='catalogue.json',
               contract='wreck_kit.pick(half, seed) -> object name and the 3x3 that replaces Diagonal(half)',
               materials=['WK %s' % s.lower() for s in wk_mat.SLOTS],
               variants=[dict(name=r['name'], object=r['object'], family=r['family'], half=r['half'],
                              verts=r['verts'], faces=r['faces'], source=r['source']) for r in rows])
    (OUT / 'manifest.json').write_text(json.dumps(man, indent=1) + '\n')
    print('SAVED', blend, '%.0fs' % (time.time() - t_all))


def validate(rows):
    fails = []
    for r in rows:
        ob = bpy.data.objects[r['object']]
        me = ob.data
        if 'wk_base' not in me.attributes or 'wk_tone' not in me.attributes:
            fails.append('%s: missing wk_base/wk_tone' % r['name'])
        if len(me.materials) != 4:
            fails.append('%s: %d material slots' % (r['name'], len(me.materials)))
        co = [0.0] * (3 * len(me.vertices))
        me.vertices.foreach_get('co', co)
        for k in range(3):
            lo, hi = min(co[k::3]), max(co[k::3])
            if abs(lo + hi) > .01 or abs((hi - lo) / 2 - r['half'][k]) > .01:
                fails.append('%s: not centred on axis %d' % (r['name'], k))
        if not (r['half'][0] >= r['half'][1] >= r['half'][2]):
            fails.append('%s: axes not sorted %s' % (r['name'], r['half']))
    for m in bpy.data.materials:
        if m.use_nodes:
            for nd in m.node_tree.nodes:
                if nd.type == 'EMISSION' or (nd.type == 'BSDF_PRINCIPLED' and nd.inputs['Emission Strength'].default_value > 0
                                             and any(nd.inputs['Emission Color'].default_value[:3])):
                    if any(m.name == s.name for r in rows for s in bpy.data.objects[r['object']].data.materials):
                        fails.append('emissive material %s' % m.name)
    if any(o.type == 'LIGHT' for o in bpy.data.objects):
        fails.append('lights in the kit')
    return dict(passed=not fails, failures=fails, variants=len(rows))


main()
