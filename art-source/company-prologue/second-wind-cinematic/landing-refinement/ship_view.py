"""Starboard camera and immutable, mesh-attached guide UV finish for landing."""
from pathlib import Path
import json
import bpy
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view

OUT = Path(__file__).resolve().parent
CAMERA_NAME = 'LANDING REFINEMENT | starboard bow-right'
GUIDE_FRAME = 744


def configure_ship_view(scene):
    """Fixed starboard camera; source flight/support actions stay untouched."""
    root = bpy.data.objects['SECOND WIND | flight root']
    scene.frame_set(GUIDE_FRAME)
    cam = bpy.data.objects.get(CAMERA_NAME)
    if cam is None:
        cam = bpy.data.objects.new(CAMERA_NAME, bpy.data.cameras.new(CAMERA_NAME))
        scene.collection.objects.link(cam)
    cam.animation_data_clear()
    cam.location = (20, -235, 67)
    target = Vector((0, 0, 32))
    cam.rotation_euler = (target-cam.location).to_track_quat('-Z', 'Y').to_euler()
    cam.data.type = 'PERSP'
    cam.data.lens = 32
    cam.data.sensor_width = 36
    cam.data.clip_start = .5
    cam.data.clip_end = 20000
    scene.camera = cam
    for marker in scene.timeline_markers:
        if marker.camera:
            marker.camera = cam
    scene.render.resolution_x = 1920
    scene.render.resolution_y = 1080
    scene.render.resolution_percentage = 100
    scene['landing_view_contract'] = 'Starboard (-Y), bow +X projects right; static camera; source flight and contact retained.'
    bpy.context.view_layer.update()
    return cam


def apply_starboard_surface(scene, imagepath, registration_path=None):
    """Overlay RGB on facing visible starboard polygons with stored local UVs."""
    scene.frame_set(GUIDE_FRAME)
    cam = configure_ship_view(scene)
    root = bpy.data.objects['SECOND WIND | flight root']
    image = bpy.data.images.load(str(Path(imagepath).resolve()), check_existing=True)
    image.pack()
    registration_file = Path(registration_path) if registration_path else OUT/'guides/starboard-image-registration.json'
    registration = json.loads(registration_file.read_text()) if registration_file.exists() else None
    if registration and registration['image'] != Path(imagepath).name:
        raise ValueError('Registration master does not match requested image')
    depsgraph = bpy.context.evaluated_depsgraph_get()
    cache = {}
    report = {'image': str(imagepath), 'native_dimensions': list(image.size), 'registration': registration or {'scale':[1,1], 'offset':[0,0]}, 'covered_objects':[]}

    def material(original):
        if original in cache:
            return cache[original]
        result = original.copy()
        result.name = original.name + ' | starboard immutable guide UV'
        nodes = result.node_tree.nodes
        links = result.node_tree.links
        bsdf = nodes.get('Principled BSDF')
        if bsdf is None:
            cache[original] = original
            return original
        color = bsdf.inputs['Base Color']
        previous = color.links[0].from_socket if color.links else None
        uv = nodes.new('ShaderNodeUVMap'); uv.uv_map = 'StarboardGuideUV'
        tex = nodes.new('ShaderNodeTexImage'); tex.image = image; tex.extension = 'CLIP'
        links.new(uv.outputs[0], tex.inputs['Vector'])
        attr = nodes.new('ShaderNodeAttribute'); attr.attribute_name = 'StarboardGuideCoverage'
        weight = nodes.new('ShaderNodeMath'); weight.operation = 'MULTIPLY'
        links.new(attr.outputs['Fac'], weight.inputs[0]); links.new(tex.outputs['Alpha'], weight.inputs[1])
        mix = nodes.new('ShaderNodeMixRGB')
        links.new(weight.outputs[0], mix.inputs[0]); links.new(tex.outputs['Color'], mix.inputs[2])
        if previous: links.new(previous, mix.inputs[1])
        else: mix.inputs[1].default_value = color.default_value
        links.new(mix.outputs[0], color)
        cache[original] = result
        return result

    for ob in root.children_recursive:
        if ob.type != 'MESH' or not ob.data.materials or ob.name.startswith('LIFT VECTOR |'):
            continue
        mesh = ob.data
        uv = mesh.uv_layers.get('StarboardGuideUV') or mesh.uv_layers.new(name='StarboardGuideUV')
        attr = mesh.attributes.get('StarboardGuideCoverage') or mesh.attributes.new('StarboardGuideCoverage','FLOAT','CORNER')
        normal_matrix = ob.matrix_world.to_3x3().inverted().transposed()
        count = 0
        for polygon in mesh.polygons:
            center = ob.matrix_world @ polygon.center
            normal = (normal_matrix @ polygon.normal).normalized()
            projection = world_to_camera_view(scene, cam, center)
            facing = normal.dot((cam.location-center).normalized()) > .35
            eligible = facing and projection.z > 0 and 0 < projection.x < 1 and 0 < projection.y < 1
            visible = False
            if eligible:
                for sample in [center] + [(ob.matrix_world@mesh.vertices[i].co)*.92+center*.08 for i in polygon.vertices]:
                    ray = sample-cam.location
                    hit, point, _, _, hitob, _ = scene.ray_cast(depsgraph, cam.location, ray.normalized(), distance=ray.length+.3)
                    if hit and hitob.original == ob.original and abs((point-cam.location).length-ray.length) < .3:
                        visible = True
                        break
            if visible: count += 1
            for li in polygon.loop_indices:
                v = ob.matrix_world @ mesh.vertices[mesh.loops[li].vertex_index].co
                projected = world_to_camera_view(scene,cam,v)
                if registration:
                    gx = projected.x * registration['guide_dimensions'][0]
                    gy = (1-projected.y) * registration['guide_dimensions'][1]
                    a,b = registration['affine_top_left_pixel_rows']
                    u = (a[0]*gx+a[1]*gy+a[2])/registration['native_dimensions'][0]
                    v = 1-(b[0]*gx+b[1]*gy+b[2])/registration['native_dimensions'][1]
                    uv.data[li].uv = (u,v)
                else:
                    uv.data[li].uv = (projected.x,projected.y)
                attr.data[li].value = float(visible)
        if count:
            for i,m in enumerate(mesh.materials):
                if m: mesh.materials[i] = material(m)
            report['covered_objects'].append({'name':ob.name,'polygons':count})
    report['camera_matrix_world'] = [list(row) for row in cam.matrix_world]
    report['camera_matrix_ship_local'] = [list(row) for row in root.matrix_world.inverted()@cam.matrix_world]
    report['limits'] = 'Single starboard aspect. Polygon sampled visibility; generated internal registration requires review. Retains port finish and neutral hidden-face fallback.'
    (OUT/'guides/starboard-surface-registration.json').write_text(json.dumps(report,indent=2)+'\n', encoding='utf-8', newline='\n')
    return report


def export_guide(scene, render=True):
    cam = configure_ship_view(scene)
    root = bpy.data.objects['SECOND WIND | flight root']
    keep = set(root.children_recursive) | {root, cam}
    for ob in scene.objects:
        if ob.type != 'LIGHT' and ob not in keep:
            ob.hide_render = True
        if ob.name.startswith('LIFT VECTOR |'):
            ob.hide_render = True
    # Keep the accepted neutral fallback; old port UV RGB is never painted across this view.
    for ob in root.children_recursive:
        if ob.type == 'MESH':
            old = ob.data.attributes.get('SideGuideCoverage')
            if old:
                for value in old.data: value.value = 0
    scene.world.color = (.22,.22,.22)
    if scene.world.use_nodes:
        bg = scene.world.node_tree.nodes.get('Background')
        bg.inputs[0].default_value = (.32,.34,.36,1)
        bg.inputs[1].default_value = .8
    data=bpy.data.lights.new('Starboard guide softbox','AREA');data.energy=220000;data.shape='DISK';data.size=160
    lamp=bpy.data.objects.new('Starboard guide softbox',data);scene.collection.objects.link(lamp)
    lamp.location=(20,-130,140);lamp.rotation_euler=(Vector((0,0,25))-lamp.location).to_track_quat('-Z','Y').to_euler()
    scene.render.engine='CYCLES';scene.cycles.samples=24;scene.cycles.use_denoising=True
    scene.render.image_settings.file_format='PNG';scene.render.film_transparent=False
    scene.render.filepath=str(OUT/'guides/starboard-contact-guide.png')
    scene.view_settings.exposure=.3
    if render:
        bpy.ops.render.render(write_still=True)
    metadata={'frame':GUIDE_FRAME,'camera':cam.name,'position':list(cam.location),'rotation_euler':list(cam.rotation_euler),'lens_mm':cam.data.lens,'sensor_width_mm':cam.data.sensor_width,'resolution':[1920,1080],'matrix_world':[list(r) for r in cam.matrix_world],'axes':'+X bow, +Y port, +Z up. View from -Y; no mirroring. Horizontal pod remains port side.'}
    (OUT/'guides/starboard-camera.json').write_text(json.dumps(metadata,indent=2)+'\n', encoding='utf-8', newline='\n')

if __name__ == '__main__':
    export_guide(bpy.context.scene)
