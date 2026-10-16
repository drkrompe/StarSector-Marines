"""All-face relightable Second Wind materials on the retained damaged mesh.

Background Blender only. Existing ship geometry is immutable. Neutral generated
material tiles feed explicit UVs; all large wear masks live on mesh attributes.
No guide-camera RGB, world projector, or light/shadow photograph is retained.
"""
from pathlib import Path
import hashlib
import json
import math
import random
import shutil
import struct

import bpy
from mathutils import Matrix, Vector

BASE = Path(__file__).resolve().parent
SOURCE = BASE.parent / 'landing-wreck-pass/landing-wreck-pass.blend'
TILES = BASE.parent / 'second-wind-surface-proof/assets'
ROOT = 'SECOND WIND | flight root'
UV = 'SurfaceTileUV'
REST = 'SurfaceRestPosition'
VARIATION = 'SurfacePlateVariation'
PATINA = 'SurfacePatina'
SEAMS = 'SurfacePanelGrid'
EXCLUDED = ('LANDING FX', 'LIFT VECTOR', 'Cloud breakup', 'ENTRY CLOUD', 'ENTRY WARM')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def mesh_signature(obj):
    h = hashlib.sha256()
    for v in obj.data.vertices:
        h.update(struct.pack('<3f', *v.co))
    for p in obj.data.polygons:
        h.update(struct.pack('<I', len(p.vertices)))
        h.update(struct.pack('<' + 'I' * len(p.vertices), *p.vertices))
    return h.hexdigest()


def number(text):
    return int(hashlib.sha256(text.encode()).hexdigest()[:8], 16) / 0xffffffff


def role_for(obj, original):
    name = original.name.lower() if original else ''
    object_name = obj.name.lower()
    if 'lamp' in object_name:
        return 'lamp'
    if 'clear proxy' in object_name:
        return 'glass'
    if 'dark throat' in object_name or 'lift mouth' in object_name:
        return 'throat'
    if 'recessed sealed backing' in object_name:
        return 'impact'
    if 'sheared raw' in name or 'exposed weld' in name or 'welded salvage' in name:
        return 'weld'
    if 'backing' in name or 'seam inlay' in object_name:
        return 'backing'
    if 'cream' in name:
        return 'cream'
    if 'oxide' in name:
        return 'oxide'
    return 'gunmetal'


def install_coordinates(obj, root):
    """Tiled UVs plus explicit undeformed rest coordinates for every mesh corner.

    All materials use the same physical tile scale, including formerly hidden
    faces. Three planar mappings meet along manufactured corners. Images repeat;
    this is intentional tile UV reuse, not a packed unique lightmap.
    """
    mesh = obj.data
    for layer in list(mesh.uv_layers):
        mesh.uv_layers.remove(layer)
    for attr in list(mesh.attributes):
        if any(x in attr.name.lower() for x in ('guide', 'coverage', 'surface')):
            mesh.attributes.remove(attr)
    uv = mesh.uv_layers.new(name=UV)
    attrs = {name: mesh.attributes.new(name, kind, 'CORNER') for name, kind in
             [(REST, 'FLOAT_VECTOR'), (VARIATION, 'FLOAT'), (PATINA, 'FLOAT'), (SEAMS, 'FLOAT')]}
    transform = root.matrix_world.inverted() @ obj.matrix_world
    normal_transform = transform.to_3x3().inverted().transposed()
    # Connected islands give a plate one consistent tint, instead of random
    # colours per triangle. Physical armor plates are disconnected mesh islands.
    parent = list(range(len(mesh.vertices)))
    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i
    for edge in mesh.edges:
        a, b = map(find, edge.vertices)
        parent[a] = b
    island_noise = {}
    for poly in mesh.polygons:
        n = (normal_transform @ poly.normal).normalized()
        axis = max(range(3), key=lambda i: abs(n[i]))
        island = find(poly.vertices[0])
        tint = island_noise.setdefault(island, number(obj.name + ':' + str(island)))
        grid = float(obj.name.startswith(('Main hull', 'Forebody replaceable',
                                          'Engine room armored', 'Fore pressure casing',
                                          'Aft machinery casing', 'PORT SERVICE POD')))
        for loop_index in poly.loop_indices:
            p = transform @ mesh.vertices[mesh.loops[loop_index].vertex_index].co
            # Nine provisional metres per native 627px tile, matching the
            # approved materials' old physical frequency without a camera map.
            if axis == 0:
                co = (p.y / 6, p.z / 6)
            elif axis == 1:
                co = (p.x / 6, p.z / 6)
            else:
                co = (p.x / 6, p.y / 6)
            uv.data[loop_index].uv = co
            attrs[REST].data[loop_index].vector = p
            attrs[VARIATION].data[loop_index].value = tint
            # Old exposed underside, engine-room heat and damage-near patina.
            belly = max(0, min(1, (-p.z - 1) / 10)) * .24
            aft = max(0, min(1, (-p.x - 16) / 28)) * .15
            damage = 0
            if p.y < -15:
                for x, z, rx, rz in [(42, 5, 10, 8), (-17, 6, 12, 9), (32, -4, 7, 6)]:
                    damage = max(damage, math.exp(-((p.x-x)/rx)**2 - ((p.z-z)/rz)**2) * .28)
            attrs[PATINA].data[loop_index].value = min(.55, belly + aft + damage)
            attrs[SEAMS].data[loop_index].value = grid
    mesh.uv_layers.active = uv
    return {'uv': UV, 'corners': len(mesh.loops), 'islands': len(island_noise),
            'repeat_hull_units': 6, 'mapping': 'fixed per-corner material UV + rest attributes'}


def material(role, images):
    mat = bpy.data.materials.new('FULL SURFACE | ' + role)
    mat.use_nodes = True
    n, l = mat.node_tree.nodes, mat.node_tree.links
    n.clear()
    def node(kind, name):
        value = n.new(kind)
        value.label = value.name = name
        return value
    def connect(a, socket, b, target):
        l.new(a.outputs[socket], b.inputs[target])
    def math_node(operation, a=None, b=None, name=''):
        t = node('ShaderNodeMath', name or operation)
        t.operation = operation
        for i, v in enumerate((a, b)):
            if v is not None:
                if isinstance(v, (float, int)):
                    t.inputs[i].default_value = v
                else:
                    l.new(v, t.inputs[i])
        return t
    out = node('ShaderNodeOutputMaterial', 'Surface output')
    bsdf = node('ShaderNodeBsdfPrincipled', 'Relightable coating and metal')
    connect(bsdf, 0, out, 'Surface')
    if role == 'glass':
        bsdf.inputs['Base Color'].default_value = (.06, .075, .08, 1)
        bsdf.inputs['Roughness'].default_value = .12
        bsdf.inputs['Transmission Weight'].default_value = .92
        return mat
    if role == 'lamp':
        bsdf.inputs['Base Color'].default_value = (.34, .29, .19, 1)
        bsdf.inputs['Emission Color'].default_value = (.8, .54, .24, 1)
        bsdf.inputs['Emission Strength'].default_value = .5
        return mat
    tile = role if role in images else ('backing' if role in ('impact', 'throat') else 'gunmetal')
    tex = node('ShaderNodeTexImage', 'Neutral albedo tile: ' + tile)
    tex.image = images[tile]
    tex.extension = 'REPEAT'
    tex.interpolation = 'Linear'
    uv = node('ShaderNodeUVMap', 'All-face fixed tile coordinates')
    uv.uv_map = UV
    connect(uv, 'UV', tex, 'Vector')
    rest = node('ShaderNodeAttribute', 'Stored hull rest position')
    rest.attribute_name = REST
    variation = node('ShaderNodeAttribute', 'Per-plate paint batch')
    variation.attribute_name = VARIATION
    patina = node('ShaderNodeAttribute', 'Authored wear placement')
    patina.attribute_name = PATINA
    grid_attr = node('ShaderNodeAttribute', 'Panel-joint eligible shell')
    grid_attr.attribute_name = SEAMS
    noise = node('ShaderNodeTexNoise', 'Fine abrasion - stable in mesh')
    noise.inputs['Scale'].default_value = 5
    noise.inputs['Detail'].default_value = 3
    noise.inputs['Roughness'].default_value = .65
    connect(rest, 'Vector', noise, 'Vector')
    large = node('ShaderNodeTexNoise', 'Broad coating erosion')
    large.inputs['Scale'].default_value = .42
    large.inputs['Detail'].default_value = 4
    connect(rest, 'Vector', large, 'Vector')
    saturation = node('ShaderNodeHueSaturation', 'Restrained industrial pigment')
    saturation.inputs['Saturation'].default_value = .95
    connect(tex, 'Color', saturation, 'Color')
    tint = node('ShaderNodeMapRange', 'Plate paint batch range')
    tint.inputs['To Min'].default_value = .76
    tint.inputs['To Max'].default_value = 1.08
    connect(variation, 'Fac', tint, 'Value')
    pigment = node('ShaderNodeMixRGB', 'Paint batch brightness')
    pigment.blend_type = 'MULTIPLY'
    pigment.inputs[0].default_value = 1
    connect(saturation, 0, pigment, 1)
    connect(tint, 'Result', pigment, 2)
    metal_region = None
    if role == 'oxide':
        # Broad unplated shell faces get repair-panel paint batches too. This
        # finishes the rear/belly without pretending that paint adds geometry.
        uv_scale = node('ShaderNodeVectorMath', 'Repair panel cell pitch')
        uv_scale.operation = 'MULTIPLY'
        uv_scale.inputs[1].default_value = (1.15, 1.8, 1)
        connect(uv, 'UV', uv_scale, 0)
        uv_floor = node('ShaderNodeVectorMath', 'One batch per service panel')
        uv_floor.operation = 'FLOOR'
        connect(uv_scale, 'Vector', uv_floor, 0)
        cell = node('ShaderNodeTexWhiteNoise', 'Salvage panel selection')
        connect(uv_floor, 'Vector', cell, 'Vector')
        cream_cell = math_node('LESS_THAN', cell.outputs['Value'], .13)
        cream_cell = math_node('MULTIPLY', cream_cell.outputs[0], grid_attr.outputs['Fac'])
        cream_tex = node('ShaderNodeTexImage', 'Neutral replacement cream')
        cream_tex.image = images['cream']
        cream_tex.extension = 'REPEAT'
        connect(uv, 'UV', cream_tex, 'Vector')
        patch = node('ShaderNodeMixRGB', 'Mismatched service-panel repaint')
        connect(cream_cell, 0, patch, 0)
        connect(pigment, 0, patch, 1)
        connect(cream_tex, 'Color', patch, 2)
        metal_cell = math_node('GREATER_THAN', cell.outputs['Value'], .95)
        xyz = node('ShaderNodeSeparateXYZ', 'Hull-axis service regions')
        connect(rest, 'Vector', xyz, 'Vector')
        abs_y = math_node('ABSOLUTE', xyz.outputs['Y'])
        belly = math_node('LESS_THAN', xyz.outputs['Z'], -6)
        keel = math_node('LESS_THAN', abs_y.outputs[0], 5)
        keel = math_node('MULTIPLY', keel.outputs[0], belly.outputs[0])
        rear = math_node('LESS_THAN', xyz.outputs['X'], -33)
        core = math_node('LESS_THAN', abs_y.outputs[0], 21)
        core = math_node('MULTIPLY', core.outputs[0], rear.outputs[0])
        metal_region = math_node('MAXIMUM', metal_cell.outputs[0], keel.outputs[0])
        metal_region = math_node('MAXIMUM', metal_region.outputs[0], core.outputs[0])
        metal_region = math_node('MULTIPLY', metal_region.outputs[0], grid_attr.outputs['Fac'])
        metal_tex = node('ShaderNodeTexImage', 'Neutral keel and machinery panels')
        metal_tex.image = images['gunmetal']
        metal_tex.extension = 'REPEAT'
        connect(uv, 'UV', metal_tex, 'Vector')
        metal_patch = node('ShaderNodeMixRGB', 'Exposed keel and stern service spine')
        connect(metal_region, 0, metal_patch, 0)
        connect(patch, 0, metal_patch, 1)
        connect(metal_tex, 'Color', metal_patch, 2)
        pigment = metal_patch
    wear = math_node('MULTIPLY', patina.outputs['Fac'], large.outputs['Fac'], 'Localized wear')
    grime = node('ShaderNodeMixRGB', 'Oxidized grime in worn regions')
    connect(wear, 0, grime, 0)
    connect(pigment, 0, grime, 1)
    grime.inputs[2].default_value = (.045, .032, .023, 1)
    # Recessed service joints are material relief only on otherwise broad shell
    # surfaces. Existing separately modeled armor retains its own silhouette.
    separate = node('ShaderNodeSeparateXYZ', 'Panel UV axes')
    connect(uv, 'UV', separate, 'Vector')
    joints = []
    for axis, factor in [('X', 1.15), ('Y', 1.8)]:
        scaled = math_node('MULTIPLY', separate.outputs[axis], factor, 'Panel pitch ' + axis)
        frac = math_node('FRACT', scaled.outputs[0])
        low = math_node('LESS_THAN', frac.outputs[0], .008)
        high = math_node('GREATER_THAN', frac.outputs[0], .992)
        joints.append(math_node('MAXIMUM', low.outputs[0], high.outputs[0]).outputs[0])
    joint = math_node('MAXIMUM', *joints)
    joint = math_node('MULTIPLY', joint.outputs[0], grid_attr.outputs['Fac'], 'Shell seam coverage')
    colour = node('ShaderNodeMixRGB', 'Recessed panel joints')
    connect(joint, 0, colour, 0)
    connect(grime, 0, colour, 1)
    colour.inputs[2].default_value = (.022, .019, .016, 1)
    if role in ('impact', 'throat', 'weld'):
        special = node('ShaderNodeMixRGB', 'Role-specific oxidized metal')
        special.blend_type = 'MULTIPLY'
        special.inputs[0].default_value = 1
        connect(colour, 0, special, 1)
        value = {'impact': (.4, .32, .25, 1), 'throat': (.085, .09, .095, 1),
                 'weld': (1.8, 1.65, 1.4, 1)}[role]
        special.inputs[2].default_value = value
        colour = special
    connect(colour, 0, bsdf, 'Base Color')
    rough = node('ShaderNodeMapRange', 'Variable surface roughness')
    rough.inputs['To Min'].default_value = .34 if role in ('gunmetal', 'weld') else .48
    rough.inputs['To Max'].default_value = .66 if role in ('gunmetal', 'weld') else .78
    connect(noise, 'Fac', rough, 'Value')
    connect(rough, 'Result', bsdf, 'Roughness')
    bsdf.inputs['Metallic'].default_value = .7 if role in ('gunmetal', 'weld', 'backing', 'impact', 'throat') else .08
    # Texture chips reveal oxidized metal; physically separate from paint colour.
    if role in ('cream', 'oxide'):
        bw = node('ShaderNodeRGBToBW', 'Exposed chip colour')
        connect(tex, 'Color', bw, 'Color')
        chip = math_node('LESS_THAN', bw.outputs[0], .075 if role == 'oxide' else .15)
        if metal_region is not None:
            chip = math_node('MAXIMUM', chip.outputs[0], metal_region.outputs[0])
        metal = node('ShaderNodeMapRange', 'Paint to exposed metal')
        metal.inputs['To Min'].default_value = .04
        metal.inputs['To Max'].default_value = .62
        connect(chip, 0, metal, 'Value')
        connect(metal, 'Result', bsdf, 'Metallic')
    inverse_joint = math_node('SUBTRACT', 1, joint.outputs[0])
    height = math_node('MULTIPLY', noise.outputs['Fac'], .14)
    height = math_node('ADD', height.outputs[0], inverse_joint.outputs[0])
    bump = node('ShaderNodeBump', 'Fine coating and recessed seam relief')
    bump.inputs['Strength'].default_value = .28
    bump.inputs['Distance'].default_value = .035
    connect(height, 0, bump, 'Height')
    bevel = node('ShaderNodeBevel', 'Manufactured edge light response')
    bevel.inputs['Radius'].default_value = .035
    bevel.samples = 3
    connect(bevel, 'Normal', bump, 'Normal')
    connect(bump, 'Normal', bsdf, 'Normal')
    mat['surface_role'] = role
    mat['mapping'] = 'SurfaceTileUV + stored mesh-rest attributes; no camera/world-space colour'
    return mat


def main():
    if not bpy.app.background:
        raise RuntimeError('Background Blender only')
    source_hash = sha(SOURCE)
    bpy.ops.wm.open_mainfile(filepath=str(SOURCE))
    scene = bpy.context.scene
    scene.frame_set(744)
    root = scene.objects[ROOT]
    keep = {root, *root.children_recursive}
    signatures = {o.name: mesh_signature(o) for o in keep if o.type == 'MESH'}
    # Asset contains only the existing ship, not any yard/camera/light/effects.
    removed = []
    for obj in list(scene.objects):
        if obj not in keep or obj.name.startswith(EXCLUDED) or obj.type in ('CAMERA', 'LIGHT'):
            removed.append(obj.name)
            bpy.data.objects.remove(obj, do_unlink=True)
    root.animation_data_clear()
    root.location = (0, 0, 0)
    root.rotation_euler = (0, 0, 0)
    for obj in root.children_recursive:
        obj.animation_data_clear()
        if obj.type in ('CAMERA', 'LIGHT'):
            obj.hide_render = True
    bpy.context.view_layer.update()
    # One appendable collection carries the complete hierarchy and packed art.
    asset_collection = bpy.data.collections.new('SECOND WIND | full surface asset')
    scene.collection.children.link(asset_collection)
    for obj in [root, *root.children_recursive]:
        asset_collection.objects.link(obj)
        for old_collection in list(obj.users_collection):
            if old_collection != asset_collection:
                old_collection.objects.unlink(obj)
    for old_collection in list(bpy.data.collections):
        if old_collection != asset_collection:
            bpy.data.collections.remove(old_collection)
    assets = BASE / 'assets'
    assets.mkdir(exist_ok=True)
    images = {}
    source_images = []
    for role in ('cream', 'oxide', 'gunmetal', 'backing'):
        src = TILES / (role + '.png')
        dst = assets / src.name
        shutil.copy2(src, dst)
        im = bpy.data.images.load(str(dst), check_existing=False)
        im.name = 'FULL SURFACE | ' + role
        im.pack()
        images[role] = im
        source_images.append({'path': 'assets/' + src.name, 'source': '../second-wind-surface-proof/assets/' + src.name,
                              'sha256': sha(dst), 'dimensions': list(im.size)})
    cache = {}
    objects = []
    for obj in sorted(root.children_recursive, key=lambda o: o.name):
        if obj.type != 'MESH':
            continue
        mapping = install_coordinates(obj, root)
        original = [m for m in obj.data.materials]
        if not original:
            original = [None]
            obj.data.materials.append(None)
        roles = []
        for i, old in enumerate(original):
            role = role_for(obj, old)
            if role not in cache:
                cache[role] = material(role, images)
            obj.data.materials[i] = cache[role]
            roles.append(role)
        assert mesh_signature(obj) == signatures[obj.name], obj.name
        objects.append({'name': obj.name, 'geometry_sha256': signatures[obj.name],
                        'roles': roles, 'polygons': len(obj.data.polygons),
                        'visible': not obj.hide_render, **mapping})
    # Purge orphan projected images/materials so the reusable file cannot carry
    # megabytes of obsolete angle-specific colour or source-yard textures.
    bpy.ops.outliner.orphans_purge(do_recursive=True)
    scene.camera = None
    scene.timeline_markers.clear()
    scene.frame_start = scene.frame_end = 1
    scene.frame_set(1)
    scene['asset_scope'] = 'Complete all-face material pass on retained damaged hull; no camera-colour projections.'
    root['axes'] = '+X bow; +Y port; +Z up'
    root['scale_status'] = 'Inherited provisional 1.5 metres per hull unit'
    root['surface_version'] = 'full-surface-v1'
    target = BASE / 'second-wind-full-surface.blend'
    bpy.ops.wm.save_as_mainfile(filepath=str(target), compress=True)
    assert sha(SOURCE) == source_hash
    manifest = {'source': '../landing-wreck-pass/landing-wreck-pass.blend', 'source_sha256': source_hash,
                'append_collection': asset_collection.name,
                'asset': target.name, 'asset_sha256': sha(target), 'build_script_sha256': sha(Path(__file__)),
                'mesh_count': len(objects), 'polygons': sum(o['polygons'] for o in objects),
                'all_retained_geometry_unchanged': True, 'objects': objects, 'materials': list(cache),
                'images': source_images, 'removed_scene_object_count': len(removed),
                'surface_contract': 'All mesh corners have fixed tiled UV and stored rest coordinates; all physical material slots rebuilt. No source camera sheets used.',
                'limits': 'Relightable Blender material asset, not a unique packed PBR atlas. Neutral ImageGen tile RGB approximates albedo. Roughness/metal/normal are authored, not measured. Bow/stern/belly retain simpler source geometry; deep modeled craters remain starboard. No new ship design or gameplay dimensions.'}
    (BASE / 'asset-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8', newline='\n')
    print('FULL_SURFACE_ASSET', target, 'meshes', len(objects))


if __name__ == '__main__':
    main()
