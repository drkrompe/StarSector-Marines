"""Material-class map of the whole clean ship from the finish reference's camera.

  blender --background --factory-startup --python render_reference_classes.py

The finish reference (reference/finish-reference.png, the projection spike's
port-side reskin) was made from build_reference.py's own 'port-side' camera.
This renders that camera's view of build/geom with every base material
replaced by its flat CLASS_ID colour, so palette.py can read the reference's
cream / oxide / gunmetal / dark paint off the plates the geometry says carry it.
Writes build/reference-classes.png (1920x1080).
"""
from pathlib import Path
import sys
import bpy

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import parts as P

bpy.ops.wm.open_mainfile(filepath=str(HERE / 'build' / 'geom' / 'second-wind-geometry.blend'))
scene = bpy.context.scene
for ob in [o for o in scene.objects if o.type == 'LIGHT']:
    bpy.data.objects.remove(ob)
mats = {}
for c, rgb in P.CLASS_ID.items():
    m = bpy.data.materials.new('SURF class ' + c)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    e, o = nt.nodes.new('ShaderNodeEmission'), nt.nodes.new('ShaderNodeOutputMaterial')
    e.inputs['Color'].default_value = (*[v / 255 for v in rgb], 1)
    nt.links.new(e.outputs[0], o.inputs['Surface'])
    mats[c] = m
for ob in scene.objects:
    if ob.type != 'MESH':
        continue
    for slot in ob.material_slots:
        cls = P.MATERIAL_CLASS.get(slot.material.name.split('.')[0], 'other') if slot.material else 'other'
        slot.link = 'OBJECT'
        slot.material = mats[cls]
scene.camera = bpy.data.objects['Camera port-side']
scene.render.engine = 'CYCLES'
scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
scene.render.resolution_percentage = 100
scene.render.film_transparent = True
scene.world.node_tree.nodes['Background'].inputs[1].default_value = 0
scene.view_settings.view_transform = 'Standard'
scene.cycles.samples = 1
scene.cycles.use_denoising = False
scene.cycles.filter_width = .01
scene.render.image_settings.color_mode = 'RGB'
scene.render.filepath = str(HERE / 'build' / 'reference-classes.png')
bpy.ops.render.render(write_still=True)
