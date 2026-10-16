"""Export exact retained cabin geometry without changing the source scene."""
from pathlib import Path
import bpy
HERE = Path(__file__).resolve().parent
bpy.ops.wm.open_mainfile(filepath=str(HERE.parent/'parallax-window/jangala-parallax-atlas-lift.blend'))
scene=bpy.context.scene
scene.frame_set(144)
root=bpy.data.objects['SECOND WIND | flight root']
ship=set(root.children_recursive)
for marker in scene.timeline_markers:
    marker.camera=None
for ob in scene.objects:
    if ob.type != 'MESH':
        continue
    if ob not in ship:
        ob.hide_render=True
        continue
    mat=bpy.data.materials.new('GUIDE | neutral cabin')
    mat.diffuse_color=(.32,.38,.40,1)
    mat.use_nodes=True
    shader=mat.node_tree.nodes.get('Principled BSDF')
    shader.inputs['Base Color'].default_value=(.32,.38,.40,1)
    shader.inputs['Roughness'].default_value=.8
    ob.data=ob.data.copy()
    ob.data.materials.clear()
    ob.data.materials.append(mat)
    for face in ob.data.polygons:
        face.material_index=0
scene.camera=bpy.data.objects['PAINT | ship-local cabin projector']
scene.render.resolution_x=1920
scene.render.resolution_y=1080
scene.render.resolution_percentage=100
scene.render.filepath=str(HERE/'guides/cabin-geometry.png')
bpy.ops.render.render(write_still=True)
