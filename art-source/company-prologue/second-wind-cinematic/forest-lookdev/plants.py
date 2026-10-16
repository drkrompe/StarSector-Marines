"""Instancable Jangala plant prototypes. Importing this module does no work."""

import math
import random


def build_plants(bpy, atlas_path):
    """Return three joined woody/foliage prototypes, rooted at local (0, 0, 0).

    Atlas layout, viewed as an image: broad cluster / narrow frond on the top
    row; radiating fan / feathered branchlets on the bottom row. Cards grow
    from image bottom centre to image top centre and are visible on both sides.
    """
    from mathutils import Vector

    bark = bpy.data.materials.new("Jangala | living grey-green bark")
    bark.use_nodes = True
    nodes = bark.node_tree.nodes
    links = bark.node_tree.links
    shader = nodes.get("Principled BSDF")
    shader.inputs["Roughness"].default_value = 0.91
    noise = nodes.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = 5.5
    noise.inputs["Detail"].default_value = 3.0
    texcoord = nodes.new("ShaderNodeTexCoord")
    mapping = nodes.new("ShaderNodeVectorMath")
    mapping.operation = "MULTIPLY"
    mapping.inputs[1].default_value = (5.0, 5.0, 0.7)
    links.new(texcoord.outputs["Generated"], mapping.inputs[0])
    links.new(mapping.outputs["Vector"], noise.inputs["Vector"])
    ramp = nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].color = (0.065, 0.08, 0.052, 1)
    ramp.color_ramp.elements[1].color = (0.255, 0.26, 0.17, 1)
    links.new(noise.outputs["Fac"], ramp.inputs[0])
    links.new(ramp.outputs["Color"], shader.inputs["Base Color"])
    bump = nodes.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = 0.24
    bump.inputs["Distance"].default_value = 0.10
    links.new(noise.outputs["Fac"], bump.inputs["Height"])
    links.new(bump.outputs["Normal"], shader.inputs["Normal"])

    leaf = bpy.data.materials.new("Jangala | atlas foliage cutouts")
    leaf.use_nodes = True
    leaf.use_backface_culling = False
    if hasattr(leaf, "surface_render_method"):
        leaf.surface_render_method = "DITHERED"
    if hasattr(leaf, "use_transparent_shadow"):
        leaf.use_transparent_shadow = True
    if hasattr(leaf, "blend_method"):
        leaf.blend_method = "HASHED"
    if hasattr(leaf, "shadow_method"):
        leaf.shadow_method = "HASHED"
    nodes, links = leaf.node_tree.nodes, leaf.node_tree.links
    shader = nodes.get("Principled BSDF")
    shader.inputs["Roughness"].default_value = 0.75
    for name in ("Subsurface Weight", "Subsurface"):
        if name in shader.inputs:
            shader.inputs[name].default_value = 0.05
            break
    texture = nodes.new("ShaderNodeTexImage")
    texture.image = bpy.data.images.load(str(atlas_path), check_existing=True)
    texture.extension = "CLIP"
    # A hard cutout avoids grey, noisy semi-transparent card rectangles.
    alpha = nodes.new("ShaderNodeMath")
    alpha.operation = "GREATER_THAN"
    alpha.inputs[1].default_value = 0.35
    links.new(texture.outputs["Alpha"], alpha.inputs[0])
    links.new(alpha.outputs[0], shader.inputs["Alpha"])
    instance = nodes.new("ShaderNodeObjectInfo")
    tint = nodes.new("ShaderNodeValToRGB")
    tint.color_ramp.elements[0].color = (0.69, 0.80, 0.58, 1)
    tint.color_ramp.elements[1].color = (0.96, 1.0, 0.83, 1)
    links.new(instance.outputs["Random"], tint.inputs[0])
    multiply = nodes.new("ShaderNodeMixRGB")
    multiply.blend_type = "MULTIPLY"
    multiply.inputs[0].default_value = 1.0
    links.new(texture.outputs["Color"], multiply.inputs[1])
    links.new(tint.outputs["Color"], multiply.inputs[2])
    links.new(multiply.outputs[0], shader.inputs["Base Color"])

    prototypes = []
    species = [("Jangala broadcrown", 28.0, 12.0),
               ("Jangala ribfan emergent", 38.0, 10.0),
               ("Jangala arching frondtree", 14.0, 8.0)]
    for kind, (name, nominal_height, crown_radius) in enumerate(species):
        rng = random.Random(7901 + kind)
        vertices, faces, material_ids, face_uvs = [], [], [], []

        def face(indices, material_id, uvs=None):
            faces.append(indices)
            material_ids.append(material_id)
            face_uvs.append(uvs or [(0, 0)] * len(indices))

        def tube(points, radii, sides=8):
            """Closed tapered wood with a transported, stable ring basis."""
            start = len(vertices)
            points = [Vector(p) for p in points]
            for j, point in enumerate(points):
                tangent = (points[min(j + 1, len(points) - 1)] -
                           points[max(j - 1, 0)]).normalized()
                ref = Vector((0, 1, 0))
                if abs(tangent.dot(ref)) > 0.95:
                    ref = Vector((1, 0, 0))
                side = tangent.cross(ref).normalized()
                other = tangent.cross(side).normalized()
                for k in range(sides):
                    angle = k * math.tau / sides
                    vertices.append(tuple(point + radii[j] * (
                        math.cos(angle) * side + math.sin(angle) * other)))
            face(tuple(start + k for k in reversed(range(sides))), 0)
            for j in range(len(points) - 1):
                for k in range(sides):
                    n = (k + 1) % sides
                    a, b = start + j * sides, start + (j + 1) * sides
                    face((a + k, a + n, b + n, b + k), 0)
            end = start + (len(points) - 1) * sides
            face(tuple(end + k for k in range(sides)), 0)

        def card(root, angle, length, width, lift, arch, quadrant):
            """Three strips across, four along: a cupped, arched physical leaf."""
            root = Vector(root)
            forward = Vector((math.cos(angle), math.sin(angle), lift)).normalized()
            side = Vector((math.sin(angle), -math.cos(angle), 0))
            start = len(vertices)
            # Blender UV origin is bottom left; image top row is v=.5..1.
            xoff = 0.5 if quadrant in (1, 3) else 0.0
            yoff = 0.5 if quadrant in (0, 1) else 0.0
            for j in range(5):
                t = j / 4
                centre = root + forward * length * t
                centre.z += arch * math.sin(math.pi * t) - length * 0.13 * t * t
                twist = 0.15 * math.sin(angle * 3 + t * 2)
                for i in range(3):
                    s = i - 1
                    point = centre + side * s * width * 0.5
                    point.z += width * (0.065 * abs(s) + twist * s) * math.sin(math.pi * t)
                    vertices.append(tuple(point))
            for j in range(4):
                for i in range(2):
                    a = start + j * 3 + i
                    # side cross forward points upward: front faces catch light.
                    indices = (a, a + 1, a + 4, a + 3)
                    uv = []
                    for u, v in ((i / 2, j / 4), ((i + 1) / 2, j / 4),
                                 ((i + 1) / 2, (j + 1) / 4), (i / 2, (j + 1) / 4)):
                        uv.append((xoff + 0.001 + u * 0.498,
                                   yoff + 0.001 + v * 0.498))
                    face(indices, 1, uv)

        trunk_top = [22, 33, 9][kind]
        trunk_radius = [0.72, 0.56, 0.58][kind]
        trunk = [(0, 0, 0), (0.15, -0.12, trunk_top * 0.22),
                 (-0.3, 0.18, trunk_top * 0.52),
                 (0.5, 0.4, trunk_top * 0.78), (0.35, 0.1, trunk_top)]
        tube(trunk, [trunk_radius, trunk_radius * .85, trunk_radius * .62,
                     trunk_radius * .38, .08], 10)
        # Buttress roots ground the silhouette from every viewing direction.
        for j in range(5):
            a = j * math.tau / 5
            tube([(math.cos(a) * 1.8, math.sin(a) * 1.8, 0.03),
                  (math.cos(a) * .6, math.sin(a) * .6, .5), (0, 0, 2.8)],
                 [.09, .3, .23], 6)

        if kind < 2:
            layers = 4 if kind == 0 else 5
            branches_per_layer = 6 if kind == 0 else 5
            for layer in range(layers):
                for j in range(branches_per_layer):
                    # Golden-angle phyllotaxis with height/reach variation
                    # removes the repeated radial shelves of a conifer.
                    branch_index = layer * branches_per_layer + j
                    a = branch_index * 2.399963 + rng.uniform(-.30, .30)
                    z = ((8.3 + layer * 3.7 + rng.uniform(-2.0, 2.0))
                         if kind == 0 else (17.5 + layer * 3.1 + rng.uniform(-2.3, 2.3)))
                    reach = crown_radius * (1 - layer * .055) * rng.uniform(.76, 1.08)
                    direction = Vector((math.cos(a), math.sin(a), 0))
                    lateral = Vector((-direction.y, direction.x, 0))
                    base = Vector((.2 + .025 * z, .1, z))
                    tip = (base + direction * reach * .73 + lateral * rng.uniform(-1.2, 1.2)
                           + Vector((0, 0, rng.uniform(2.5, 4.8))))
                    tube([base, base + direction * reach * .33 + Vector((0, 0, 1.5)), tip],
                         [.23 if kind == 0 else .16, .13, .025], 7)
                    for cluster in range(5):
                        fraction = .24 + cluster * .175 + rng.uniform(-.035, .035)
                        root = base.lerp(tip, fraction)
                        root += lateral * rng.uniform(-.55, .55)
                        root.z += rng.uniform(-.65, .7)
                        # Alternate lateral sprays fill between branch spokes;
                        # the last cluster follows the woody tip outwards.
                        spread = (-1 if cluster % 2 else 1) * rng.uniform(.4, 1.05)
                        card(root, a + (spread if cluster < 4 else rng.uniform(-.25, .25)),
                             rng.uniform(3.9, 5.6) if kind == 0 else rng.uniform(3.3, 4.9),
                             rng.uniform(3.3, 4.7) if kind == 0 else rng.uniform(2.5, 3.7),
                             rng.uniform(-.16, .55), rng.uniform(.35, .85),
                             0 if kind == 0 else (2 if cluster % 2 else 1))
            # Upright young shoots break the canopy's horizontal silhouette.
            for j in range(8):
                card((rng.uniform(-1.6, 1.6), rng.uniform(-1.6, 1.6),
                      trunk_top - rng.uniform(.5, 2.8)), j * 2.399963,
                     4.5 if kind == 0 else 5.2, 2.5, 1.6, .4, 3 if kind == 0 else 2)
        else:
            for j in range(16):
                a = j * 2.399963 + rng.uniform(-.16, .16)
                reach = rng.uniform(5.8, 7.7)
                base = Vector((.35, .1, rng.uniform(6.2, 9.5)))
                direction = Vector((math.cos(a), math.sin(a), 0))
                mid = base + direction * reach * .45 + Vector((0, 0, rng.uniform(2.2, 3.7)))
                tip = base + direction * reach + Vector((0, 0, rng.uniform(-1.0, .7)))
                tube([base, mid, tip], [.11, .07, .012], 6)
                for k in range(5):
                    t = k / 5
                    centre = (1 - t) ** 2 * base + 2 * (1 - t) * t * mid + t * t * tip
                    card(centre, a + (-.25 if k % 2 else .25), 2.5,
                         2.5 - .17 * k, .2 - t * .6, .35, 1 if k % 2 else 3)

        # Preserve the requested metre envelope without object transforms, so
        # linked mesh copies and geometry instances share predictable bounds.
        z_scale = nominal_height / max(v[2] for v in vertices)
        radial_scale = crown_radius / max(math.hypot(v[0], v[1]) for v in vertices)
        vertices = [(x * radial_scale, y * radial_scale, z * z_scale)
                    for x, y, z in vertices]
        data = bpy.data.meshes.new(name + " mesh")
        data.from_pydata(vertices, [], faces)
        data.materials.append(bark)
        data.materials.append(leaf)
        uv_layer = data.uv_layers.new(name="Foliage atlas quadrants")
        for polygon, material_id, uv in zip(data.polygons, material_ids, face_uvs):
            polygon.material_index = material_id
            polygon.use_smooth = material_id == 0
            for loop_index, coordinate in zip(polygon.loop_indices, uv):
                uv_layer.data[loop_index].uv = coordinate
        data.update()
        obj = bpy.data.objects.new(name, data)
        bpy.context.collection.objects.link(obj)
        actual_height = max(v[2] for v in vertices)
        actual_radius = max(math.hypot(v[0], v[1]) for v in vertices)
        obj["nominal_height_m"] = nominal_height
        obj["foliage_cards"] = sum(m == 1 for m in material_ids) // 8
        prototypes.append({"name": name, "object": obj, "height": actual_height,
                           "crown_radius": actual_radius})
    return prototypes
