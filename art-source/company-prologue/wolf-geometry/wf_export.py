"""Package the open scene as the Wolf (P)'s appendable cinematic asset.

Used by build_wolf.py on the untextured build and by export_wolf.py on the
textured one (../wolf-surface's skinned build), so both have one contract:
one collection `WOLF_P`, the root empty `WOLF_P | root` (at the hull origin,
carrying metres_per_unit) parenting every part, and every empty in the child
collection `WOLF_P | anchors`. Cameras, lamps and the review studio are
removed; images are packed when asked (the textured export), so the .blend
travels alone.
"""
import bpy

COLL = 'WOLF_P'
ROOT = 'WOLF_P | root'


def package(path, metres_per_unit, pack_images=False, extra=None):
    scene = bpy.context.scene
    for ob in [o for o in bpy.data.objects if o.type in ('CAMERA', 'LIGHT')]:
        bpy.data.objects.remove(ob, do_unlink=True)
    coll = bpy.data.collections.new(COLL)
    scene.collection.children.link(coll)
    anchors = bpy.data.collections.new(f'{COLL} | anchors')
    coll.children.link(anchors)
    root = bpy.data.objects.new(ROOT, None)
    root.empty_display_type, root.empty_display_size = 'ARROWS', 12
    coll.objects.link(root)
    root['metres_per_unit'] = metres_per_unit
    root['axes'] = '+X bow, +Y port, +Z dorsal; hull units'
    root['source'] = 'art-source/company-prologue/wolf-geometry/build_wolf.py'
    for k, v in (extra or {}).items():
        root[k] = v
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
    if pack_images:
        bpy.ops.file.pack_all()
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(path), compress=True)
    return coll
