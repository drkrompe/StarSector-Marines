"""Make the appendable Falcon (P) asset from a build or a skinned build.

Inside Blender, on the open file (build_falcon.py calls make_asset() after
saving its review scene; the surfacing's skinned.blend is exported the same
way):
  blender --background <file>.blend --python export_falcon.py -- --out DIR

The asset is one collection `FALCON (P)` holding the root empty
`FALCON (P) | root` (metres_per_unit, axes, source), which parents every
part; anchors (every empty) live in the child collection
`FALCON (P) | anchors`. Cameras, lamps and the review figure are removed,
every modifier is baked, images are packed, and the file is saved as
falcon-p.blend in DIR.
"""
from pathlib import Path
import json
import sys
import bpy

COLL = 'FALCON (P)'
ROOT = 'FALCON (P) | root'
SOURCE = 'art-source/company-prologue/falcon-geometry/build_falcon.py'
HERE = Path(__file__).resolve().parent
LAUNCHERS = ('swarmer', 'annihilatorpod', 'typhoon')


def mounts():
    """The manifest's weapon slots (hardpoints and turrets) in mount order (the VFX profile's MOUNTS order)."""
    return json.loads((HERE/'geometry.json').read_text(encoding='utf-8'))['weapon_slots']


def kit_muzzles():
    """The shared VFX kit's declared muzzles: `Anchor turret <i> barrel|tube <k> muzzle`,
    one per `Weapon <id> muzzle anchor <k>`, parented to it with no offset, so a
    trained or elevated mount carries the kit's anchor with it. i is the mount's
    order in the manifest (vfx_hull.MOUNTS)."""
    for i, t in enumerate(mounts(), 1):
        tag = 'tube' if t['weapon'] in LAUNCHERS else 'barrel'
        for k, name in enumerate(t['muzzle_anchors'], 1):
            src = bpy.data.objects[name]
            alias = f'Anchor turret {i} {tag} {k} muzzle'
            if alias in bpy.data.objects:
                continue
            ob = bpy.data.objects.new(alias, None)
            ob.empty_display_type, ob.empty_display_size = 'SINGLE_ARROW', .8
            for c in src.users_collection:
                c.objects.link(ob)
            ob.parent = src
            ob['weapon_slot'] = t['id']


def bake():
    dg = bpy.context.evaluated_depsgraph_get()
    for ob in [o for o in bpy.data.objects if o.type == 'MESH' and o.modifiers]:
        ev = ob.evaluated_get(dg)
        me = bpy.data.meshes.new_from_object(ev)
        old = ob.data
        ob.modifiers.clear()
        ob.data = me
        me.name = old.name
        if old.users == 0:
            bpy.data.meshes.remove(old)


def make_asset(path, mpu, pack=False):
    for ob in [o for o in bpy.data.objects if o.type in ('CAMERA', 'LIGHT') or o.name.startswith('Review ')]:
        bpy.data.objects.remove(ob, do_unlink=True)
    bake()
    kit_muzzles()
    scene = bpy.context.scene
    for c in list(bpy.data.collections):
        if c.name in (COLL, f'{COLL} | anchors'):
            bpy.data.collections.remove(c)
    coll = bpy.data.collections.new(COLL)
    scene.collection.children.link(coll)
    anchors = bpy.data.collections.new(f'{COLL} | anchors')
    coll.children.link(anchors)
    old_root = bpy.data.objects.get(ROOT)
    if old_root:
        bpy.data.objects.remove(old_root, do_unlink=True)
    root = bpy.data.objects.new(ROOT, None)
    root.empty_display_type, root.empty_display_size = 'ARROWS', 20
    coll.objects.link(root)
    root['metres_per_unit'] = mpu
    root['axes'] = '+X bow, +Y port, +Z dorsal; hull units'
    root['source'] = SOURCE
    for ob in [o for o in bpy.data.objects if o is not root]:
        for c in list(ob.users_collection):
            c.objects.unlink(ob)
        (anchors if ob.type == 'EMPTY' else coll).objects.link(ob)
        if ob.parent is None:
            ob.parent = root  # root sits at the hull origin: no inverse needed
    bpy.context.view_layer.update()
    orphans = [o.name for o in coll.all_objects if o is not root and o.parent is None]
    assert not orphans, orphans
    for c in [c for c in bpy.data.collections if c.name not in (COLL, f'{COLL} | anchors')]:
        bpy.data.collections.remove(c)
    bpy.ops.outliner.orphans_purge(do_recursive=True)
    if pack:
        bpy.ops.file.pack_all()
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(path), compress=True)
    print('exported', path)


if __name__ == '__main__' and '--out' in sys.argv:
    argv = sys.argv[sys.argv.index('--') + 1:]
    out = Path(argv[argv.index('--out') + 1]).resolve()
    out.mkdir(parents=True, exist_ok=True)
    here = Path(__file__).resolve().parent
    mpu = json.loads((here/'geometry.json').read_text(encoding='utf-8'))['metres_per_unit']
    make_asset(out/'falcon-p.blend', mpu, pack=True)
