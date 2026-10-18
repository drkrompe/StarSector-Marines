"""Second Wind VFX kit: the one call a shot makes.

    import sys; sys.path.insert(0, '<repo>/art-source/company-prologue/second-wind-vfx')
    import swfx
    ctx = swfx.apply(state='patched', preset='jangala-approach')

appends anchors, thrusters, smoke/atmosphere, gun effects and the ship's own
lights onto whatever Second Wind build is in the open file (built by
second-wind-geometry/build_reference.py, or appended from its .blend), plus
one lighting rig preset. Re-running replaces the kit's own objects only.
Animate through the knobs on the 'SWFX controls' empty (see README.md).
"""
import importlib
import bpy

import swfx_core
import swfx_anchors

GROUPS = ('thrusters', 'smoke', 'guns', 'lighting')


def module(group):
    mod = importlib.import_module(f'swfx_{group}')
    return importlib.reload(mod)


def clear():
    """Remove everything this kit added (its collections, objects, materials)."""
    root = bpy.data.collections.get(swfx_core.ROOT)
    if root is None:
        return
    for coll in [root, *root.children_recursive]:
        for ob in list(coll.objects):
            data = ob.data
            bpy.data.objects.remove(ob, do_unlink=True)
            if data is not None and data.users == 0:
                for store in (bpy.data.meshes, bpy.data.lights, bpy.data.cameras, bpy.data.curves):
                    if data.name in store and store[data.name] == data:
                        store.remove(data)
                        break
    for coll in [*root.children_recursive, root]:
        bpy.data.collections.remove(coll)
    for store in (bpy.data.materials, bpy.data.node_groups, bpy.data.meshes, bpy.data.lights):
        for block in list(store):
            if block.name.startswith('SWFX') and block.users == 0:
                store.remove(block)


def apply(state='patched', groups=GROUPS, preset=None, scene=None, **options):
    """Build the kit for one condition state. options go to each group's
    build(ctx, **options) where it accepts them (e.g. preset for lighting)."""
    scene = scene or bpy.context.scene
    clear()
    importlib.reload(swfx_core)
    importlib.reload(swfx_anchors)
    # An exported asset carries its own scale on its root (the Second Wind's
    # equals geometry.json's); a bare build keeps geometry.json's.
    root = swfx_anchors.export_root()
    if root is not None:
        swfx_core.METRES_PER_UNIT = float(root['metres_per_unit'])
    anchors = swfx_anchors.find(scene)
    ctx = swfx_core.Context(scene, state, anchors)
    ctx.records['anchors'] = anchors.record()
    for group in groups:
        mod = module(group)
        kwargs = dict(options)
        if group == 'lighting' and preset:
            kwargs['preset'] = preset
        ctx.records[group] = mod.build(ctx, **kwargs)
    swfx_core.refresh_drivers(scene)
    return ctx
