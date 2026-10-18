"""Black silhouettes of a built Onslaught from the angles the formation shot can see.

  sh ../../blender.sh --background onslaught-geometry.blend --python silhouettes.py -- OUTDIR
Writes OUTDIR/<view>.png (640x360, black on white, orthographic): side, front,
stern, quarter, bowq, high, low, above-bow. At 100-250 px the cinematic reads
the hull as a silhouette first; judge shape changes on these. Destructive to
the open file's materials, so never save after running it."""
import bpy, math, sys
from mathutils import Vector
out = sys.argv[sys.argv.index('--')+1]
sc = bpy.context.scene
sc.render.engine = 'BLENDER_EEVEE' if 'BLENDER_EEVEE' in [e.identifier for e in bpy.types.RenderSettings.bl_rna.properties['engine'].enum_items] else 'CYCLES'
sc.render.resolution_x, sc.render.resolution_y = 640, 360
sc.render.film_transparent = False
w = bpy.data.worlds.new('W'); sc.world = w; w.use_nodes = True
w.node_tree.nodes['Background'].inputs[0].default_value = (1, 1, 1, 1)
w.node_tree.nodes['Background'].inputs[1].default_value = 1
black = bpy.data.materials.new('B'); black.use_nodes = True
n = black.node_tree.nodes; n.clear()
o = n.new('ShaderNodeOutputMaterial'); e = n.new('ShaderNodeEmission'); e.inputs[0].default_value = (0, 0, 0, 1)
black.node_tree.links.new(e.outputs[0], o.inputs[0])
for ob in sc.objects:
    if ob.type == 'MESH':
        ob.data.materials.clear(); ob.data.materials.append(black)
        for s in ob.material_slots: s.material = black
    if ob.type == 'LIGHT':
        ob.hide_render = True
cam = bpy.data.objects.new('SilCam', bpy.data.cameras.new('SilCam')); sc.collection.objects.link(cam)
cam.data.type = 'ORTHO'
mid = Vector((50, 0, 0))
views = [('side', 0, -90, 0), ('front', 0, 0, 0), ('stern', 0, 180, 0), ('quarter', 20, -40, 0), ('bowq', 15, -140, 0),
         ('high', 40, 30, 0), ('low', -20, -60, 0), ('above-bow', 30, -160, 0)]
for name, el, az, _ in views:
    a, e2 = math.radians(az), math.radians(el)
    d = Vector((math.cos(e2)*math.cos(a), math.cos(e2)*math.sin(a), math.sin(e2)))
    cam.location = mid+d*600
    cam.rotation_euler = (-d).to_track_quat('-Z', 'Y').to_euler()
    cam.data.ortho_scale = 480 if el >= 30 else 420   # high views take the arms' full spread
    sc.camera = cam
    sc.render.filepath = f'{out}/{name}.png'
    bpy.ops.render.render(write_still=True)
